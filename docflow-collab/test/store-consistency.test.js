import test from 'node:test'
import assert from 'node:assert/strict'
import * as Y from 'yjs'
import { Server } from '@hocuspocus/server'
import { CrdtStore } from '../src/store.js'
import { createInitialDocument, documentToHtml, replaceDocumentContent } from '../src/initial-document.js'

// SQL adapter exercises persisted Yjs payloads; it is not a MySQL isolation test.
function database(document) {
  const state = { checkpoint: { protocol_version: 1, checkpoint_seq: 0, payload: Buffer.from(Y.encodeStateAsUpdate(document)) }, updates: [], history: [], outbox: null }
  const advisoryLocks = new Set()
  let generation = 0
  const calls = []
  const execute = async (sql, values = [], transaction = false) => {
    calls.push({ sql, transaction })
    if (sql.startsWith('SELECT GET_LOCK')) {
      if (advisoryLocks.has(values[0])) return [[{ acquired: 0 }]]
      advisoryLocks.add(values[0]); return [[{ acquired: 1 }]]
    }
    if (sql.startsWith('SELECT RELEASE_LOCK')) { advisoryLocks.delete(values[0]); return [[{ released: 1 }]] }
    if (sql.includes('MAX(id)')) return [[{ high_water: Math.max(0, ...state.updates.map(row => row.id)) }]]
    if (sql.startsWith('SELECT doc_id FROM crdt_materialization')) return [state.outbox ? [{ doc_id: state.outbox.documentId }] : []]
    if (sql.startsWith('SELECT payload, editor_user_id, generation FROM crdt_materialization')) return [state.outbox ? [{ payload: state.outbox.payload, editor_user_id: state.outbox.editorUserId, generation: state.outbox.generation }] : []]
    if (sql.includes('FROM crdt_checkpoint_history WHERE id')) return [[state.history.find(row => row.id === values[0])].filter(Boolean)]
    if (sql.includes('FROM crdt_checkpoint WHERE')) return [state.checkpoint ? [{ ...state.checkpoint }] : []]
    if (sql.startsWith('SELECT id, payload FROM crdt_update WHERE')) return [state.updates.filter(row => row.id > values[1])]
    if (sql.startsWith('INSERT INTO crdt_checkpoint_history')) {
      state.history.push({ id: state.history.length + 1, protocol_version: values[1], checkpoint_seq: values[2], payload: values[3] })
    } else if (sql.startsWith('INSERT INTO crdt_checkpoint ')) {
      state.checkpoint = { protocol_version: values[1], checkpoint_seq: values[2], payload: values[3] }
    } else if (sql.startsWith('DELETE FROM crdt_update')) {
      state.updates = state.updates.filter(row => row.id > (values[1] ?? Infinity))
    } else if (sql.startsWith('SELECT id FROM crdt_checkpoint_history')) return [state.history.toSorted((a, b) => b.id - a.id).slice(0, Number(sql.match(/LIMIT (\d+)/)[1]))]
    else if (sql.startsWith('DELETE FROM crdt_checkpoint_history')) state.history = state.history.filter(row => values.slice(1).includes(row.id))
    else if (sql.startsWith('INSERT INTO crdt_materialization')) {
      const allocated = ++generation
      const queuedGeneration = state.outbox && !sql.includes('generation = VALUES(generation)') ? state.outbox.generation : allocated
      state.outbox = { documentId: values[0], payload: values[1], editorUserId: values[2], generation: queuedGeneration }
    }
    else if (sql.startsWith('DELETE FROM crdt_materialization')) {
      if (values.length === 1 || state.outbox?.generation === values[1]) state.outbox = null
    }
    return [[]]
  }
  const pool = { execute, getConnection: async () => {
    let transaction = false
    return { execute: (sql, values) => execute(sql, values, transaction), beginTransaction: async () => { transaction = true }, commit: async () => { transaction = false }, rollback: async () => { transaction = false }, release() {} }
  } }
  return { state, calls, pool, store: new CrdtStore(pool) }
}

function clone(document) { const copy = new Y.Doc(); Y.applyUpdate(copy, Y.encodeStateAsUpdate(document)); return copy }

test('checkpoint merges a durable update that has not reached this node', async () => {
  const local = new Y.Doc(); local.getText('content').insert(0, 'base')
  const remote = clone(local)
  let update
  remote.once('update', value => { update = value })
  remote.getText('content').insert(4, '-remote')
  const { store, state } = database(local)
  state.updates.push({ id: 12, payload: update })
  await store.checkpoint(1, local)
  const loaded = await store.load(1)
  assert.equal(loaded.getText('content').toString(), 'base-remote')
  assert.equal(state.checkpoint.checkpoint_seq, 12)
  assert.equal(state.updates.length, 0)
})

test('an older node cannot regress the checkpoint sequence or state', async () => {
  const local = new Y.Doc(); local.getText('content').insert(0, 'base')
  const current = clone(local); current.getText('content').insert(4, '-new')
  const { store, state } = database(current)
  state.checkpoint.checkpoint_seq = 25
  await store.checkpoint(1, local, 0, 'MANUAL')
  assert.equal((await store.load(1)).getText('content').toString(), 'base-new')
  assert.equal(state.checkpoint.checkpoint_seq, 25)
})

test('loading holds a document transaction lock across checkpoint and update reads', async () => {
  const { store, calls } = database(new Y.Doc())
  await store.load(1)
  const reads = calls.filter(call => call.sql.includes('FROM crdt_checkpoint WHERE') || call.sql.includes('FROM crdt_update WHERE'))
  assert.equal(reads.length, 2)
  assert.ok(reads.every(call => call.transaction))
  assert.match(reads[0].sql, /FOR UPDATE/)
})

test('history restore preserves deletion clocks against an old cached client', async () => {
  const current = createInitialDocument({ initialContent: '<p>historical</p>' })
  const historical = Buffer.from(Y.encodeStateAsUpdate(current))
  const changed = createInitialDocument({ initialContent: '<p>later</p>' })
  replaceDocumentContent(current, changed)
  const oldClient = clone(current)
  const { store, state } = database(current)
  state.checkpoint.checkpoint_seq = 8
  state.history.push({ id: 3, protocol_version: 1, checkpoint_seq: 0, payload: historical })
  // status is unrelated SQL aggregation, bypass it in this adapter.
  store.status = async () => ({ exists: true })
  await store.restoreCheckpoint(1, 3)
  const restored = await store.load(1)
  assert.match(documentToHtml(restored), /historical/)
  Y.applyUpdate(restored, Y.encodeStateAsUpdate(oldClient))
  assert.match(documentToHtml(restored), /historical/)
  assert.doesNotMatch(documentToHtml(restored), /later/)
  assert.equal(state.checkpoint.checkpoint_seq, 8)
})

test('checkpoint durably queues the merged snapshot in its transaction', async () => {
  const document = createInitialDocument({ initialContent: '<p>queued</p>' })
  const { store, state, calls } = database(document)
  await store.checkpoint(1, document, null, 'AUTO', 7)
  assert.equal(state.outbox.editorUserId, 7)
  assert.match(documentToHtml((() => { const value = new Y.Doc(); Y.applyUpdate(value, state.outbox.payload); return value })()), /queued/)
  assert.ok(calls.find(call => call.sql.startsWith('INSERT INTO crdt_materialization')).transaction)
})

test('queued writes cannot recreate a document after its checkpoint was purged', async () => {
  const document = createInitialDocument({ initialContent: '<p>deleted document</p>' })
  const { store, state } = database(document)
  state.checkpoint = null
  await assert.rejects(store.appendUpdate(1, Y.encodeStateAsUpdate(document)), /does not exist/)
  await assert.rejects(store.checkpoint(1, document), /does not exist/)
  assert.equal(state.checkpoint, null)
})

test('failed materialization stays durable and a restarted worker retries it', async t => {
  t.mock.method(console, 'warn', () => {})
  const document = createInitialDocument({ initialContent: '<p>retry after restart</p>' })
  const { store, state, pool } = database(document)
  await store.checkpoint(1, document, null, 'AUTO', 7)
  await store.materializePending(async () => { throw new Error('Java unavailable') })
  assert.ok(state.outbox)
  let delivered
  const restarted = new CrdtStore(pool)
  await restarted.materializePending(async (id, payload, userId) => {
    const restored = new Y.Doc(); Y.applyUpdate(restored, payload)
    delivered = { id, userId, html: documentToHtml(restored) }
    restored.destroy()
  })
  assert.deepEqual(delivered, { id: '1', userId: 7, html: '<p>retry after restart</p>' })
  assert.equal(state.outbox, null)
})

test('materialization does not hold a row transaction over HTTP and retains a newer checkpoint', async () => {
  const document = createInitialDocument({ initialContent: '<p>old</p>' })
  const replacement = createInitialDocument({ initialContent: '<p>new</p>' })
  const { store, state, calls } = database(document)
  await store.checkpoint(1, document, null, 'AUTO', 7)
  let releaseDelivery, deliveryStarted
  const gate = new Promise(resolve => { releaseDelivery = resolve })
  const started = new Promise(resolve => { deliveryStarted = resolve })
  let deliveryRead
  const worker = store.materializePending(async () => {
    deliveryRead = calls.at(-1)
    deliveryStarted()
    await gate
  })
  try {
    await started
    await store.checkpoint(1, document, null, 'MANUAL', 7, replacement)
  } finally { releaseDelivery(); await worker }
  assert.equal(deliveryRead.transaction, false, 'HTTP must not hold a DB transaction while Java may hold the document row')
  assert.doesNotMatch(deliveryRead.sql, /FOR UPDATE/)
  assert.ok(state.outbox, 'acknowledging old delivery must retain the newer queued snapshot')
  const queued = new Y.Doc(); Y.applyUpdate(queued, state.outbox.payload)
  assert.match(documentToHtml(queued), /new/)
  document.destroy(); replacement.destroy(); queued.destroy()
})

test('multiple materialization workers serialize by document without locking checkpoint rows', async () => {
  const document = createInitialDocument({ initialContent: '<p>queued</p>' })
  const { store, pool } = database(document)
  await store.checkpoint(1, document, null, 'AUTO', 7)
  let releaseDelivery, deliveryStarted
  const gate = new Promise(resolve => { releaseDelivery = resolve })
  const started = new Promise(resolve => { deliveryStarted = resolve })
  let deliveries = 0
  const first = store.materializePending(async () => { deliveries++; deliveryStarted(); await gate })
  try {
    await started
    await new CrdtStore(pool).materializePending(async () => { deliveries++ })
    assert.equal(deliveries, 1)
  } finally { releaseDelivery(); await first; document.destroy() }
})

test('same-payload requeues allocate a new generation and survive an older acknowledgement', async () => {
  const document = createInitialDocument({ initialContent: '<p>same content</p>' })
  const { store, state } = database(document)
  await store.checkpoint(1, document, null, 'AUTO', 7)
  const firstGeneration = state.outbox.generation
  await store.materializePending(async (id, payload, userId, generation) => {
    assert.equal(generation, firstGeneration)
    await store.checkpoint(1, document, null, 'AUTO', 7)
    assert.ok(state.outbox.generation > firstGeneration)
  })
  assert.ok(state.outbox)
  assert.ok(state.outbox.generation > firstGeneration)
  document.destroy()
})

test('live history restore broadcasts a normal Yjs update and survives an older queued save', async () => {
  const historical = createInitialDocument({ initialContent: '<p>historical</p>' })
  const current = clone(historical)
  const later = createInitialDocument({ initialContent: '<p>later</p>' })
  replaceDocumentContent(current, later)
  const { store, state } = database(current)
  // Selecting the oldest retained entry must not be invalidated by a pre-restore save.
  for (let id = 1; id <= 20; id++) state.history.push({ id, protocol_version: 1, checkpoint_seq: 0, payload: Buffer.from(Y.encodeStateAsUpdate(historical)) })
  store.status = async () => ({ exists: true })
  const server = new Server({ port: 0, quiet: true, debounce: 60_000, maxDebounce: 60_000,
    onLoadDocument: () => store.load(1),
    onStoreDocument: ({ document }) => store.checkpoint(1, document)
  })
  const document = await server.hocuspocus.createDocument('1', new Request('http://localhost'), 'test', { readOnly: false, isAuthenticated: true }, {})
  const connectedClient = clone(document)
  const staleSave = clone(document)
  let broadcasts = 0
  document.on('update', update => { broadcasts++; Y.applyUpdate(connectedClient, update) })
  try {
    const { restoreLiveCheckpoint } = await import('../src/restore-document.js')
    await restoreLiveCheckpoint(server.hocuspocus, store, 1, 1)
    assert.ok(broadcasts > 0)
    assert.match(documentToHtml(connectedClient), /historical/)
    assert.doesNotMatch(documentToHtml(connectedClient), /later/)
    await store.checkpoint(1, staleSave)
    assert.doesNotMatch(documentToHtml(await store.load(1)), /later/)
  } finally {
    await server.hocuspocus.debouncer.executeNow('onStoreDocument-1')
    await server.hocuspocus.unloadDocument(document)
    await server.destroy()
    connectedClient.destroy(); staleSave.destroy(); historical.destroy(); current.destroy(); later.destroy()
  }
})

test('content replacement on an idle node uses the Hocuspocus broadcast path', async () => {
  const original = createInitialDocument({ initialContent: '<p>original</p>' })
  const replacement = createInitialDocument({ initialContent: '**Markdown restored**', initialContentFormat: 'MARKDOWN' })
  const { store } = database(original)
  let broadcasts = 0
  const server = new Server({ port: 0, quiet: true, debounce: 60_000, maxDebounce: 60_000,
    onLoadDocument: () => store.load(1),
    onChange: () => { broadcasts++ },
    onStoreDocument: ({ document }) => store.checkpoint(1, document)
  })
  try {
    const { replaceLiveContent } = await import('../src/restore-document.js')
    await replaceLiveContent(server.hocuspocus, store, 1, replacement)
    assert.ok(broadcasts > 0)
    assert.match(documentToHtml(await store.load(1)), /<strong>Markdown restored<\/strong>/)
  } finally {
    await server.hocuspocus.debouncer.executeNow('onStoreDocument-1')
    const document = server.hocuspocus.documents.get('1')
    if (document) await server.hocuspocus.unloadDocument(document)
    await server.destroy()
    original.destroy(); replacement.destroy()
  }
})
