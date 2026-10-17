import test from 'node:test'
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { spawn } from 'node:child_process'
import mysql from 'mysql2/promise'
import * as Y from 'yjs'
import { CrdtStore } from '../src/store.js'
import { createInitialDocument, documentToHtml, replaceDocumentContent } from '../src/initial-document.js'

const enabled = process.env.DOCFLOW_TEST_MYSQL_ISOLATED === 'true'

function clone(document) {
  const result = new Y.Doc()
  Y.applyUpdate(result, Y.encodeStateAsUpdate(document))
  return result
}

function change(document, action) {
  let update
  document.once('update', value => { update = value })
  action()
  return update
}

async function startLegacyServer(options, database) {
  const child = spawn(process.execPath, ['src/server.js'], {
    cwd: new URL('..', import.meta.url),
    env: {
      ...process.env, NODE_ENV: 'development', DB_HOST: options.host, DB_PORT: String(options.port),
      DB_NAME: database, DB_USERNAME: options.user, DB_PASSWORD: options.password,
      CRDT_REDIS_ENABLED: 'false', CRDT_HOST: '127.0.0.1', CRDT_PORT: '0',
      CRDT_ADMIN_HOST: '127.0.0.1', CRDT_ADMIN_PORT: '0',
      CRDT_ADMIN_SECRET: 'isolated-startup-fixture-secret', DOCFLOW_API_URL: 'http://127.0.0.1:1'
    },
    stdio: ['ignore', 'pipe', 'pipe']
  })
  const closed = new Promise(resolve => child.once('close', resolve))
  let output = ''
  let timeout
  try {
    await new Promise((resolve, reject) => {
      const collect = chunk => {
        output += chunk.toString()
        if (output.includes('DocFlow CRDT collaboration server ready')) resolve()
      }
      child.stdout.on('data', collect)
      child.stderr.on('data', collect)
      child.once('error', reject)
      child.once('exit', code => reject(new Error(`Collaboration startup exited (${code}): ${output}`)))
      timeout = setTimeout(() => reject(new Error(`Collaboration startup timed out: ${output}`)), 15000)
    })
  } finally {
    clearTimeout(timeout)
    child.kill()
    await closed
  }
}

test('isolated MySQL persistence, concurrent compaction and outbox', { skip: !enabled }, async t => {
  const port = Number(process.env.DOCFLOW_TEST_MYSQL_PORT)
  assert.ok(Number.isInteger(port) && port >= 20000 && port <= 60000)
  const options = { host: '127.0.0.1', port, user: 'root', password: '', multipleStatements: true }
  const admin = await mysql.createConnection(options)
  const database = `docflow_audit_crdt_${process.pid}_${Date.now()}`
  let created = false
  let pool
  try {
    const [[server]] = await admin.query('SELECT @@datadir AS directory')
    assert.match(server.directory.replaceAll('\\', '/'), /\/\.tmp\/audit-mysql-/i)
    assert.match(database, /^docflow_audit_crdt_[0-9_]+$/)
    await admin.query(`CREATE DATABASE ${database} CHARACTER SET utf8mb4`)
    created = true
    pool = mysql.createPool({ ...options, database, connectionLimit: 8 })
    const baseline = new URL('../../docflow/docflow-server/src/main/resources/db/migration/V1__baseline.sql', import.meta.url)
    const outbox = new URL('../../docflow/docflow-server/src/main/resources/db/migration/V4__crdt_materialization.sql', import.meta.url)
    await pool.query((await readFile(baseline, 'utf8')).replace(/^\uFEFF/, ''))
    const store = new CrdtStore(pool)

    await t.test('legacy schema starts without an outbox and existing collaboration data survives restart', async () => {
      const oldDocument = new Y.Doc(); oldDocument.getText('content').insert(0, 'existing document')
      try {
        await store.initialize(91, Y.encodeStateAsUpdate(oldDocument))
        // Exercise the actual npm-start entry point against an old database, not just a mocked pool.
        await startLegacyServer(options, database)
        await store.verify()
        await store.checkpoint(91, oldDocument)
        const [[queued]] = await pool.query('SELECT generation FROM crdt_materialization WHERE doc_id=91')
        assert.ok(queued.generation > 0)
        await new CrdtStore(pool).verify()
        const [[retained]] = await pool.query('SELECT generation FROM crdt_materialization WHERE doc_id=91')
        assert.equal(retained.generation, queued.generation)
        const restored = await store.load(91)
        try { assert.equal(restored.getText('content').toString(), 'existing document') } finally { restored.destroy() }
      } finally { oldDocument.destroy() }
    })

    // Applying the normal backend V4 after the startup bootstrap must remain valid.
    await pool.query((await readFile(outbox, 'utf8')).replace(/^\uFEFF/, ''))
    await store.verify()

    await t.test('checkpoint includes another node durable update before deleting increments', async () => {
      const seed = new Y.Doc(); seed.getText('content').insert(0, 'AB')
      const staleNode = clone(seed); const otherNode = clone(seed)
      try {
        await store.initialize(1, Y.encodeStateAsUpdate(seed))
        const update = change(otherNode, () => otherNode.getText('content').insert(2, '-other-node'))
        await store.appendUpdate(1, update, 'other')
        await store.checkpoint(1, staleNode)
        const loaded = await store.load(1)
        try { assert.equal(loaded.getText('content').toString(), 'AB-other-node') } finally { loaded.destroy() }
        const [[pending]] = await pool.query('SELECT COUNT(*) AS count FROM crdt_update WHERE doc_id=1')
        assert.equal(pending.count, 0)
      } finally { seed.destroy(); staleNode.destroy(); otherNode.destroy() }
    })

    await t.test('concurrent increments loads and stale checkpoints do not lose updates', async () => {
      const seed = new Y.Doc(); seed.getText('content').insert(0, 'base:')
      const clients = Array.from({ length: 10 }, () => clone(seed))
      try {
        await store.initialize(2, Y.encodeStateAsUpdate(seed))
        await Promise.all(clients.map(async (client, index) => {
          const update = change(client, () => client.getText('content').insert(5, String(index)))
          await store.appendUpdate(2, update, String(index))
          await store.checkpoint(2, seed)
          const loaded = await store.load(2); loaded.destroy()
        }))
        const loaded = await store.load(2)
        try {
          const text = loaded.getText('content').toString()
          assert.equal(text.length, 15)
          for (let i = 0; i < 10; i++) assert.ok(text.includes(String(i)))
        } finally { loaded.destroy() }
      } finally { seed.destroy(); clients.forEach(client => client.destroy()) }
    })

    await t.test('in-flight delivery holds no row lock and cannot acknowledge a newer generation', async () => {
      const document = new Y.Doc(); document.getText('content').insert(0, 'before')
      try {
        await pool.query('DELETE FROM crdt_materialization')
        await store.initialize(3, Y.encodeStateAsUpdate(document))
        await store.checkpoint(3, document)
        const [[old]] = await pool.query('SELECT generation FROM crdt_materialization WHERE doc_id=3')
        let start, finish
        const started = new Promise(resolve => { start = resolve })
        const gate = new Promise(resolve => { finish = resolve })
        const delivery = store.materializePending(async (id, payload, userId, generation) => {
          assert.equal(id, '3'); assert.equal(generation, old.generation)
          start(); await gate
        })
        let timer
        try {
          await started
          document.getText('content').insert(6, '-after')
          await Promise.race([
            store.checkpoint(3, document),
            new Promise((resolve, reject) => { timer = setTimeout(() => reject(new Error('Checkpoint blocked behind HTTP delivery')), 5000) })
          ])
          const [[newer]] = await pool.query('SELECT generation FROM crdt_materialization WHERE doc_id=3')
          assert.ok(newer.generation > old.generation)
        } finally { clearTimeout(timer); finish(); await delivery }
        const [[retained]] = await pool.query('SELECT generation FROM crdt_materialization WHERE doc_id=3')
        assert.ok(retained.generation > old.generation)
        await store.materializePending(async () => {})
        await store.checkpoint(3, document)
        const [[requeued]] = await pool.query('SELECT generation FROM crdt_materialization WHERE doc_id=3')
        assert.ok(requeued.generation > retained.generation)
      } finally { document.destroy() }
    })

    await t.test('history restore survives replay from a client with the pre-restore state', async () => {
      const initial = createInitialDocument({ initialContent: '<p>old</p>', initialContentFormat: 'HTML' })
      const cache = clone(initial)
      const replacement = createInitialDocument({ initialContent: '<p>new</p>', initialContentFormat: 'HTML' })
      try {
        await store.initialize(4, Y.encodeStateAsUpdate(initial))
        await store.checkpoint(4, initial)
        const update = change(cache, () => replaceDocumentContent(cache, replacement))
        await store.appendUpdate(4, update, 'cached-client')
        await store.checkpoint(4, cache)
        const [history] = await pool.query('SELECT id FROM crdt_checkpoint_history WHERE doc_id=4 ORDER BY id DESC LIMIT 1')
        await store.restoreCheckpoint(4, history[0].id)
        const restored = await store.load(4)
        try {
          Y.applyUpdate(restored, Y.encodeStateAsUpdate(cache))
          assert.equal(documentToHtml(restored), '<p>old</p>')
        } finally { restored.destroy() }
      } finally { initial.destroy(); cache.destroy(); replacement.destroy() }
    })
  } finally {
    if (pool) await pool.end()
    if (created) await admin.query(`DROP DATABASE ${database}`)
    await admin.end()
  }
})
