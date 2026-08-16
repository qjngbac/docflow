import test from 'node:test'
import assert from 'node:assert/strict'
import * as Y from 'yjs'
import { CrdtStore } from '../src/store.js'
import { encodeCheckpoint, restoreDocument, updateId, validateUpdateSize } from '../src/crdt-state.js'

function cloneFrom(document) {
  const clone = new Y.Doc()
  Y.applyUpdate(clone, Y.encodeStateAsUpdate(document))
  return clone
}

function capture(document, action) {
  let update
  document.once('update', value => { update = value })
  action()
  return update
}

test('two users editing concurrently converge', () => {
  const seed = new Y.Doc(); seed.getText('content').insert(0, 'AB')
  const alice = cloneFrom(seed); const bob = cloneFrom(seed)
  const aliceUpdate = capture(alice, () => alice.getText('content').insert(1, 'X'))
  const bobUpdate = capture(bob, () => bob.getText('content').insert(2, 'Y'))
  Y.applyUpdate(alice, bobUpdate); Y.applyUpdate(bob, aliceUpdate)
  assert.equal(alice.getText('content').toString(), bob.getText('content').toString())
})

test('same-position concurrent edits and reverse delivery converge', () => {
  const seed = new Y.Doc(); seed.getText('content').insert(0, 'A')
  const first = cloneFrom(seed); const second = cloneFrom(seed)
  const updateA = capture(first, () => first.getText('content').insert(1, 'left'))
  const updateB = capture(second, () => second.getText('content').insert(1, 'right'))
  const nodeOne = cloneFrom(seed); const nodeTwo = cloneFrom(seed)
  Y.applyUpdate(nodeOne, updateA); Y.applyUpdate(nodeOne, updateB)
  Y.applyUpdate(nodeTwo, updateB); Y.applyUpdate(nodeTwo, updateA)
  assert.equal(nodeOne.getText('content').toString(), nodeTwo.getText('content').toString())
})

test('duplicate updates are idempotent and have a stable update id', () => {
  const source = new Y.Doc()
  const update = capture(source, () => source.getText('content').insert(0, 'once'))
  const target = new Y.Doc(); Y.applyUpdate(target, update); Y.applyUpdate(target, update)
  assert.equal(target.getText('content').toString(), 'once')
  assert.equal(updateId(7, update), updateId(7, update))
})

test('updates from one client may arrive out of order', () => {
  const source = new Y.Doc()
  const first = capture(source, () => source.getText('content').insert(0, 'A'))
  const second = capture(source, () => source.getText('content').insert(1, 'B'))
  const target = new Y.Doc(); Y.applyUpdate(target, second); Y.applyUpdate(target, first)
  assert.equal(target.getText('content').toString(), 'AB')
})

test('reconnect transfers only missing state', () => {
  const online = new Y.Doc(); online.getText('content').insert(0, 'base')
  const offline = cloneFrom(online)
  online.getText('content').insert(4, '-server')
  offline.getText('content').insert(0, 'client-')
  Y.applyUpdate(online, Y.encodeStateAsUpdate(offline, Y.encodeStateVector(online)))
  Y.applyUpdate(offline, Y.encodeStateAsUpdate(online, Y.encodeStateVector(offline)))
  assert.equal(online.getText('content').toString(), offline.getText('content').toString())
})

test('service restart restores checkpoint and remaining increments', () => {
  const beforeCheckpoint = new Y.Doc(); beforeCheckpoint.getText('content').insert(0, 'checkpoint')
  const checkpoint = encodeCheckpoint(beforeCheckpoint)
  const update = capture(beforeCheckpoint, () => beforeCheckpoint.getText('content').insert(10, '+increment'))
  const restored = restoreDocument(checkpoint, [{ payload: update }])
  assert.equal(restored.getText('content').toString(), 'checkpoint+increment')
})

test('two backend nodes converge through update broadcast even without shared memory', () => {
  const nodeA = new Y.Doc(); const nodeB = new Y.Doc()
  const updateA = capture(nodeA, () => nodeA.getText('content').insert(0, 'A'))
  const updateB = capture(nodeB, () => nodeB.getText('content').insert(0, 'B'))
  Y.applyUpdate(nodeA, updateB, 'redis'); Y.applyUpdate(nodeB, updateA, 'redis')
  assert.equal(nodeA.getText('content').toString(), nodeB.getText('content').toString())
})

test('checkpoint failure rolls back and does not delete increments', async () => {
  const calls = []
  const connection = {
    execute: async sql => {
      calls.push(sql)
      if (sql.startsWith('SELECT')) return [[{ high_water: 9 }]]
      if (sql.startsWith('INSERT')) throw new Error('checkpoint unavailable')
      return [{}]
    },
    beginTransaction: async () => calls.push('BEGIN'),
    commit: async () => calls.push('COMMIT'),
    rollback: async () => calls.push('ROLLBACK'),
    release: () => calls.push('RELEASE')
  }
  const store = new CrdtStore({ getConnection: async () => connection })
  await assert.rejects(() => store.checkpoint(1, new Y.Doc()), /checkpoint unavailable/)
  assert.ok(calls.includes('ROLLBACK'))
  assert.ok(!calls.some(call => typeof call === 'string' && call.startsWith('DELETE')))
})

test('oversized binary messages are rejected before application', () => {
  assert.throws(() => validateUpdateSize(new Uint8Array(1025), 1024), /message limit/)
  assert.doesNotThrow(() => validateUpdateSize(new Uint8Array(1024), 1024))
})

test('increment persistence uses a per-document unique update id', async () => {
  let captured
  const pool = { execute: async (sql, values) => { captured = { sql, values }; return [{}] } }
  const store = new CrdtStore(pool)
  const payload = new Uint8Array([1, 2, 3])
  const id = await store.appendUpdate(9, payload, 'client-a')
  assert.match(captured.sql, /INSERT IGNORE INTO crdt_update/)
  assert.equal(captured.values[0], 9)
  assert.equal(captured.values[3], id)
  assert.equal(id, updateId(9, payload))
})

test('startup verifies durable CRDT tables before accepting connections', async () => {
  const calls = []
  const store = new CrdtStore({
    execute: async sql => {
      calls.push(sql)
      return [[]]
    }
  })

  await store.verify()

  assert.deepEqual(calls, [
    'SELECT 1 FROM crdt_checkpoint LIMIT 1',
    'SELECT 1 FROM crdt_update LIMIT 1',
    'SELECT 1 FROM crdt_checkpoint_history LIMIT 1'
  ])
})

test('startup fails fast when CRDT persistence is unavailable', async () => {
  const store = new CrdtStore({
    execute: async () => { throw new Error('Access denied for database user') }
  })

  await assert.rejects(() => store.verify(), /Access denied/)
})
