import mysql from 'mysql2/promise'
import { encodeCheckpoint, PROTOCOL_VERSION, restoreDocument, updateId } from './crdt-state.js'

export function createPool() {
  return mysql.createPool({
    host: process.env.DB_HOST || '127.0.0.1',
    port: Number(process.env.DB_PORT || 3306),
    database: process.env.DB_NAME || 'docflow',
    user: process.env.DB_USERNAME || 'root',
    password: process.env.DB_PASSWORD || '',
    connectionLimit: Number(process.env.DB_POOL_SIZE || 10),
    enableKeepAlive: true,
    timezone: '+08:00'
  })
}

export class CrdtStore {
  constructor(pool) {
    this.pool = pool
  }

  async verify() {
    await this.pool.execute('SELECT 1 FROM crdt_checkpoint LIMIT 1')
    await this.pool.execute('SELECT 1 FROM crdt_update LIMIT 1')
    await this.pool.execute('SELECT 1 FROM crdt_checkpoint_history LIMIT 1')
  }

  async appendUpdate(documentId, payload, clientId) {
    const id = updateId(documentId, payload)
    await this.pool.execute(
      `INSERT IGNORE INTO crdt_update
       (doc_id, protocol_version, client_id, update_id, payload)
       VALUES (?, ?, ?, ?, ?)`,
      [documentId, PROTOCOL_VERSION, clientId || null, id, Buffer.from(payload)]
    )
    return id
  }

  async initialize(documentId, payload) {
    await this.pool.execute(
      `INSERT IGNORE INTO crdt_checkpoint
       (doc_id, protocol_version, checkpoint_seq, payload)
       VALUES (?, ?, 0, ?)`,
      [documentId, PROTOCOL_VERSION, Buffer.from(payload)]
    )
  }

  async load(documentId) {
    const loaded = await this.loadWithMetadata(documentId)
    return loaded?.document || null
  }

  async loadWithMetadata(documentId) {
    const [checkpointRows] = await this.pool.execute(
      'SELECT checkpoint_seq, payload FROM crdt_checkpoint WHERE doc_id = ?', [documentId]
    )
    const checkpoint = checkpointRows[0]
    const sequence = checkpoint?.checkpoint_seq || 0
    const [updates] = await this.pool.execute(
      'SELECT id, payload FROM crdt_update WHERE doc_id = ? AND id > ? ORDER BY id ASC',
      [documentId, sequence]
    )
    if (!checkpoint && updates.length === 0) return null
    return {
      document: restoreDocument(checkpoint?.payload, updates),
      checkpointSequence: Number(sequence),
      updateHighWater: updates.length ? Number(updates[updates.length - 1].id) : Number(sequence),
      updateCount: updates.length
    }
  }

  async checkpoint(documentId, document, sequenceOverride = null, reason = 'AUTO') {
    const connection = await this.pool.getConnection()
    try {
      const [rows] = await connection.execute(
        'SELECT COALESCE(MAX(id), 0) AS high_water FROM crdt_update WHERE doc_id = ?',
        [documentId]
      )
      const highWater = sequenceOverride == null ? Number(rows[0].high_water || 0) : Number(sequenceOverride)
      const payload = Buffer.from(encodeCheckpoint(document))
      await connection.beginTransaction()
      const [existingRows] = await connection.execute(
        'SELECT protocol_version, checkpoint_seq, payload FROM crdt_checkpoint WHERE doc_id = ? FOR UPDATE',
        [documentId]
      )
      const existing = existingRows[0]
      if (existing?.payload?.length) {
        await connection.execute(
          `INSERT INTO crdt_checkpoint_history (doc_id, protocol_version, checkpoint_seq, payload, reason)
           VALUES (?, ?, ?, ?, ?)`,
          [documentId, existing.protocol_version, existing.checkpoint_seq, existing.payload, reason]
        )
      }
      await connection.execute(
        `INSERT INTO crdt_checkpoint (doc_id, protocol_version, checkpoint_seq, payload)
         VALUES (?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE protocol_version = VALUES(protocol_version),
           checkpoint_seq = VALUES(checkpoint_seq), payload = VALUES(payload), updated_at = CURRENT_TIMESTAMP(3)`,
        [documentId, PROTOCOL_VERSION, highWater, payload]
      )
      await connection.execute(
        'DELETE FROM crdt_update WHERE doc_id = ? AND id <= ?', [documentId, highWater]
      )
      await this.trimHistory(connection, documentId)
      await connection.commit()
    } catch (error) {
      await connection.rollback()
      throw error
    } finally {
      connection.release()
    }
  }

  async status(documentId) {
    const [rows] = await this.pool.execute(
      `SELECT c.checkpoint_seq, MAX(OCTET_LENGTH(c.payload)) AS checkpoint_bytes, c.updated_at,
              COUNT(u.id) AS pending_updates, COALESCE(SUM(OCTET_LENGTH(u.payload)), 0) AS pending_bytes,
              MAX(u.created_at) AS last_update_at
         FROM crdt_checkpoint c
         LEFT JOIN crdt_update u ON u.doc_id = c.doc_id AND u.id > c.checkpoint_seq
        WHERE c.doc_id = ?
        GROUP BY c.doc_id, c.checkpoint_seq, c.updated_at`,
      [documentId]
    )
    if (!rows.length) return { exists: false, documentId: Number(documentId), pendingUpdates: 0, pendingBytes: 0 }
    const row = rows[0]
    return {
      exists: true,
      documentId: Number(documentId),
      checkpointSequence: Number(row.checkpoint_seq || 0),
      checkpointBytes: Number(row.checkpoint_bytes || 0),
      checkpointUpdatedAt: row.updated_at,
      pendingUpdates: Number(row.pending_updates || 0),
      pendingBytes: Number(row.pending_bytes || 0),
      lastUpdateAt: row.last_update_at
    }
  }

  async listHistory(documentId) {
    const limit = Math.max(2, Math.min(100, Number(process.env.CRDT_CHECKPOINT_HISTORY_LIMIT || 20)))
    const [rows] = await this.pool.execute(
      `SELECT id, checkpoint_seq, protocol_version, reason, created_at, OCTET_LENGTH(payload) AS snapshot_bytes
         FROM crdt_checkpoint_history
        WHERE doc_id = ?
        ORDER BY id DESC
        LIMIT ${limit}`,
      [documentId]
    )
    return rows.map(row => ({
      id: Number(row.id), checkpointSequence: Number(row.checkpoint_seq), protocolVersion: Number(row.protocol_version),
      reason: row.reason, createdAt: row.created_at, snapshotBytes: Number(row.snapshot_bytes || 0)
    }))
  }

  async restoreCheckpoint(documentId, historyId) {
    const connection = await this.pool.getConnection()
    try {
      await connection.beginTransaction()
      const [historyRows] = await connection.execute(
        `SELECT protocol_version, checkpoint_seq, payload
           FROM crdt_checkpoint_history WHERE id = ? AND doc_id = ? FOR UPDATE`,
        [historyId, documentId]
      )
      const selected = historyRows[0]
      if (!selected) throw new Error('Checkpoint history entry does not exist')
      const [currentRows] = await connection.execute(
        'SELECT protocol_version, checkpoint_seq, payload FROM crdt_checkpoint WHERE doc_id = ? FOR UPDATE',
        [documentId]
      )
      const current = currentRows[0]
      if (current?.payload?.length) {
        await connection.execute(
          `INSERT INTO crdt_checkpoint_history (doc_id, protocol_version, checkpoint_seq, payload, reason)
           VALUES (?, ?, ?, ?, 'PRE_RESTORE')`,
          [documentId, current.protocol_version, current.checkpoint_seq, current.payload]
        )
      }
      await connection.execute(
        `INSERT INTO crdt_checkpoint (doc_id, protocol_version, checkpoint_seq, payload)
         VALUES (?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE protocol_version = VALUES(protocol_version), checkpoint_seq = VALUES(checkpoint_seq),
           payload = VALUES(payload), updated_at = CURRENT_TIMESTAMP(3)`,
        [documentId, selected.protocol_version, selected.checkpoint_seq, selected.payload]
      )
      await connection.execute('DELETE FROM crdt_update WHERE doc_id = ?', [documentId])
      await this.trimHistory(connection, documentId)
      await connection.commit()
      return this.status(documentId)
    } catch (error) {
      await connection.rollback()
      throw error
    } finally {
      connection.release()
    }
  }

  async trimHistory(connection, documentId) {
    const limit = Math.max(2, Math.min(100, Number(process.env.CRDT_CHECKPOINT_HISTORY_LIMIT || 20)))
    const [rows] = await connection.execute(
      'SELECT id FROM crdt_checkpoint_history WHERE doc_id = ? ORDER BY id DESC LIMIT ?', [documentId, limit]
    )
    if (rows.length < limit) return
    const ids = rows.map(row => row.id)
    await connection.execute(
      `DELETE FROM crdt_checkpoint_history WHERE doc_id = ? AND id NOT IN (${ids.map(() => '?').join(',')})`,
      [documentId, ...ids]
    )
  }

  async forceCheckpoint(documentId) {
    const loaded = await this.loadWithMetadata(documentId)
    if (!loaded) throw new Error('CRDT document state does not exist')
    await this.checkpoint(documentId, loaded.document, loaded.updateHighWater, 'MANUAL')
    loaded.document.destroy()
    return this.status(documentId)
  }
}
