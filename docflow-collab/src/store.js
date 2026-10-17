import mysql from 'mysql2/promise'
import * as Y from 'yjs'
import { encodeCheckpoint, PROTOCOL_VERSION, restoreDocument, updateId } from './crdt-state.js'
import { replaceDocumentContent } from './initial-document.js'

// 协作服务持久化 Yjs 二进制；历史恢复只替换约定的编辑器正文片段。
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
    try {
      await this.pool.execute('SELECT 1 FROM crdt_materialization LIMIT 1')
    } catch (error) {
      if (error.code !== 'ER_NO_SUCH_TABLE' && error.errno !== 1146) throw error
      // 旧库可能未开启 Java Flyway；仅补建本服务的新队列表，不重建协同数据。
      // 与 V4 的 CREATE TABLE IF NOT EXISTS 兼容，后端升级仍由 Flyway 管理。
      try {
        await this.pool.execute(`CREATE TABLE IF NOT EXISTS crdt_materialization (
          doc_id BIGINT NOT NULL PRIMARY KEY,
          generation BIGINT NOT NULL AUTO_INCREMENT UNIQUE,
          editor_user_id BIGINT NULL,
          payload LONGBLOB NOT NULL,
          updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Durable pending CRDT HTML materialization'`)
      } catch (cause) {
        throw new Error('初始化 crdt_materialization 失败，请检查数据库连接与 CREATE 权限，或先执行后端 Flyway V4 迁移', { cause })
      }
      await this.pool.execute('SELECT 1 FROM crdt_materialization LIMIT 1')
    }
  }

  async appendUpdate(documentId, payload, clientId) {
    // update_id 具有内容幂等性；INSERT IGNORE 让重复消息不会生成第二条增量。
    const id = updateId(documentId, payload)
    await this.withDocumentLock(documentId, async (connection, checkpoint) => {
      if (!checkpoint) throw new Error('CRDT document state does not exist')
      await connection.execute(
        `INSERT IGNORE INTO crdt_update
         (doc_id, protocol_version, client_id, update_id, payload)
         VALUES (?, ?, ?, ?, ?)`,
        [documentId, PROTOCOL_VERSION, clientId || null, id, Buffer.from(payload)]
      )
    })
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
    return this.withDocumentLock(documentId, (connection, checkpoint) => this.readState(connection, documentId, checkpoint))
  }

  async withDocumentLock(documentId, action) {
    const connection = await this.pool.getConnection()
    try {
      await connection.beginTransaction()
      // 所有增量写入、读取、压缩与回滚都先锁同一文档行，防止跨节点覆盖。
      const [rows] = await connection.execute(
        'SELECT protocol_version, checkpoint_seq, payload FROM crdt_checkpoint WHERE doc_id = ? FOR UPDATE', [documentId]
      )
      const result = await action(connection, rows[0])
      await connection.commit()
      return result
    } catch (error) {
      await connection.rollback()
      throw error
    } finally {
      connection.release()
    }
  }

  async readState(connection, documentId, checkpoint) {
    // 恢复顺序固定为最近 checkpoint，再按自增 ID 应用其后的全部增量。
    const sequence = checkpoint?.checkpoint_seq || 0
    const [updates] = await connection.execute(
      'SELECT id, payload FROM crdt_update WHERE doc_id = ? AND id > ? ORDER BY id ASC FOR UPDATE',
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

  async checkpoint(documentId, document, sequenceOverride = null, reason = 'AUTO', editorUserId = null, replacement = null) {
    // checkpoint、历史留档和已合并增量删除必须处于同一事务中。
    return this.withDocumentLock(documentId, async (connection, existing) => {
      const loaded = await this.readState(connection, documentId, existing)
      if (!loaded && !replacement) throw new Error('CRDT document state does not exist')
      const merged = loaded?.document || new Y.Doc()
      try {
        // 本节点可能尚未收到其他节点已持久化的更新；必须合并后才能删除增量。
        if (document) Y.applyUpdate(merged, encodeCheckpoint(document))
        if (replacement) replaceDocumentContent(merged, replacement)
        // 外部传入的旧序号不能覆盖事务内读取的最新高水位。
        const highWater = loaded?.updateHighWater || 0
        const payload = Buffer.from(encodeCheckpoint(merged))
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
        await this.queueMaterialization(connection, documentId, payload, editorUserId)
        await this.trimHistory(connection, documentId)
        return payload
      } finally {
        merged.destroy()
      }
    })
  }

  async queueMaterialization(connection, documentId, payload, editorUserId = null) {
    await connection.execute(
      `INSERT INTO crdt_materialization (doc_id, payload, editor_user_id) VALUES (?, ?, ?)
       ON DUPLICATE KEY UPDATE payload = VALUES(payload), editor_user_id = COALESCE(VALUES(editor_user_id), editor_user_id),
         generation = VALUES(generation), updated_at = CURRENT_TIMESTAMP(3)`,
      [documentId, payload, editorUserId]
    )
  }

  async materializePending(deliver) {
    const [pending] = await this.pool.execute('SELECT doc_id FROM crdt_materialization ORDER BY updated_at LIMIT 100')
    for (const row of pending) {
      const connection = await this.pool.getConnection()
      const lockName = `docflow-materialization-${row.doc_id}`
      let acquired = false
      try {
        // 只序列化回写工作器；HTTP 期间不持有行锁，避免与 Java 文档事务互相等待。
        const [locks] = await connection.execute('SELECT GET_LOCK(?, 0) AS acquired', [lockName])
        acquired = Number(locks[0]?.acquired) === 1
        if (!acquired) continue
        const [rows] = await connection.execute('SELECT payload, editor_user_id, generation FROM crdt_materialization WHERE doc_id = ?', [row.doc_id])
        if (rows[0]) {
          await deliver(String(row.doc_id), rows[0].payload, rows[0].editor_user_id, rows[0].generation)
          // 只确认已发送的世代；HTTP 期间新入队的快照必须保留。
          await connection.execute('DELETE FROM crdt_materialization WHERE doc_id = ? AND generation = ?', [row.doc_id, rows[0].generation])
        }
      } catch (error) {
        console.warn(`Materialized snapshot retry failed for document ${row.doc_id}`)
      } finally {
        try {
          if (acquired) await connection.execute('SELECT RELEASE_LOCK(?) AS released', [lockName])
        } finally { connection.release() }
      }
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

  async restoreCheckpoint(documentId, historyId, document = null) {
    // 回滚前先保存当前 checkpoint，保留一次反向恢复的机会。
    await this.withDocumentLock(documentId, async (connection, current) => {
      const [historyRows] = await connection.execute(
        `SELECT protocol_version, checkpoint_seq, payload
           FROM crdt_checkpoint_history WHERE id = ? AND doc_id = ? FOR UPDATE`,
        [historyId, documentId]
      )
      const selected = historyRows[0]
      if (!selected) throw new Error('Checkpoint history entry does not exist')
      const loaded = await this.readState(connection, documentId, current)
      if (!loaded) throw new Error('CRDT document state does not exist')
      const historical = restoreDocument(selected.payload, [])
      try {
        if (document) Y.applyUpdate(loaded.document, encodeCheckpoint(document))
        await connection.execute(
          `INSERT INTO crdt_checkpoint_history (doc_id, protocol_version, checkpoint_seq, payload, reason)
           VALUES (?, ?, ?, ?, 'PRE_RESTORE')`,
          [documentId, PROTOCOL_VERSION, loaded.updateHighWater, Buffer.from(encodeCheckpoint(loaded.document))]
        )
        // 使用当前谱系的删除时钟，旧缓存重连时不会重新带回已撤销的内容。
        replaceDocumentContent(loaded.document, historical)
        const payload = Buffer.from(encodeCheckpoint(loaded.document))
        await connection.execute(
          `INSERT INTO crdt_checkpoint (doc_id, protocol_version, checkpoint_seq, payload)
           VALUES (?, ?, ?, ?)
           ON DUPLICATE KEY UPDATE protocol_version = VALUES(protocol_version), checkpoint_seq = VALUES(checkpoint_seq),
             payload = VALUES(payload), updated_at = CURRENT_TIMESTAMP(3)`,
          [documentId, PROTOCOL_VERSION, loaded.updateHighWater, payload]
        )
        await connection.execute('DELETE FROM crdt_update WHERE doc_id = ? AND id <= ?', [documentId, loaded.updateHighWater])
        await this.queueMaterialization(connection, documentId, payload)
        await this.trimHistory(connection, documentId)
      } finally {
        historical.destroy()
        loaded.document.destroy()
      }
    })
    return this.status(documentId)
  }

  async trimHistory(connection, documentId) {
    const limit = Math.max(2, Math.min(100, Number(process.env.CRDT_CHECKPOINT_HISTORY_LIMIT || 20)))
    // LIMIT 不能作为绑定参数下发（MySQL 预处理协议会回 ER_WRONG_ARGUMENTS），这里与 listHistory 保持同样的拼接写法；
    // limit 已由上面的 Math.max/Math.min 收敛为 2~100 的整数。
    const [rows] = await connection.execute(
      `SELECT id FROM crdt_checkpoint_history WHERE doc_id = ? ORDER BY id DESC LIMIT ${limit}`, [documentId]
    )
    if (rows.length < limit) return
    const ids = rows.map(row => row.id)
    await connection.execute(
      `DELETE FROM crdt_checkpoint_history WHERE doc_id = ? AND id NOT IN (${ids.map(() => '?').join(',')})`,
      [documentId, ...ids]
    )
  }

  async forceCheckpoint(documentId) {
    await this.checkpoint(documentId, null, null, 'MANUAL')
    return this.status(documentId)
  }
}
