import test from 'node:test'
import assert from 'node:assert/strict'
import { Connection, Document } from '@hocuspocus/server'

test('idle reader is disconnected when periodic authorization is revoked', async () => {
  const { createAccessRevalidator } = await import('../src/access-revalidation.js')
  const document = new Document('7')
  const connection = new Connection({ readyState: 1, send() {} }, new Request('http://localhost'), document, 'test', { token: 'expired', user: { id: 3 } }, true)
  const documents = new Map([['7', document]])
  const access = createAccessRevalidator(async () => { throw new Error('Not authorized') }, { interval: 10, now: () => 20 })
  try {
    await access.refreshAll(documents)
    assert.equal(document.hasConnection(connection), false)
  } finally { document.destroy() }
})

test('server token sync renews tokens, reconnects on permission changes and rejects identity switches', async t => {
  t.mock.method(console, 'error', () => {})
  const previous = { node: process.env.NODE_ENV, redis: process.env.CRDT_REDIS_ENABLED }
  process.env.NODE_ENV = 'test'
  process.env.CRDT_REDIS_ENABLED = 'false'
  const { server } = await import('../src/server.js')
  const context = { token: 'old', user: { id: 3 }, readOnly: false }
  const document = new Document('7')
  const connection = new Connection({ readyState: 1, send() {} }, new Request('http://localhost'), document, 'test', context, false)
  const closed = []
  connection.onClose((document, event) => { closed.push(event) })
  let userId = 3
  let writable = true
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({ code: 200, data: { documentId: 7, userId, writable } }), { status: 200 }))
  try {
    await server.hocuspocus.hooks('onTokenSync', { documentName: '7', token: 'refreshed', context, connection })
    assert.equal(connection.context.token, 'refreshed')
    assert.equal(document.hasConnection(connection), true)
    writable = false
    await server.hocuspocus.hooks('onTokenSync', { documentName: '7', token: 'refreshed-reader', context, connection })
    assert.equal(connection.readOnly, true)
    assert.equal(document.hasConnection(connection), false, 'token sync permission changes must also reconnect the client UI')
    assert.equal(closed[0].code, 4403)
    userId = 4
    await assert.rejects(server.hocuspocus.hooks('onTokenSync', { documentName: '7', token: 'other-user', context, connection }), /Not authorized/)
  } finally {
    await server.destroy()
    document.destroy()
    if (previous.node === undefined) delete process.env.NODE_ENV; else process.env.NODE_ENV = previous.node
    if (previous.redis === undefined) delete process.env.CRDT_REDIS_ENABLED; else process.env.CRDT_REDIS_ENABLED = previous.redis
  }
})

test('successful periodic checks request fresh tokens so a valid connection outlives its original token', async () => {
  const { createAccessRevalidator } = await import('../src/access-revalidation.js')
  const document = new Document('7')
  const frames = []
  const connection = new Connection({ readyState: 1, send: value => { frames.push(value) } }, new Request('http://localhost'), document, 'test', { token: 'original', user: { id: 3 } }, true)
  let time = 0
  const checkedTokens = []
  const access = createAccessRevalidator(async (_, token) => {
    checkedTokens.push(token)
    if (token === 'original' && time >= 120) throw new Error('Not authorized')
    return { userId: 3, writable: false }
  }, { interval: 30, now: () => time })
  access.markAuthorized(connection)
  try {
    time = 30
    await access.refresh('7', connection)
    assert.equal(frames.length, 1, 'requestToken must emit the Hocuspocus token refresh request')
    // A normal provider answers the request using its token callback.
    connection.context.token = 'renewed'
    time = 150
    await access.refresh('7', connection)
    assert.deepEqual(checkedTokens, ['original', 'renewed'])
    assert.equal(document.hasConnection(connection), true)
    assert.equal(frames.length, 2)
  } finally { document.destroy() }
})

test('ignoring token refresh requests does not bypass periodic expiry checks', async () => {
  const { createAccessRevalidator } = await import('../src/access-revalidation.js')
  const document = new Document('7')
  const frames = []
  const connection = new Connection({ readyState: 1, send: value => { frames.push(value) } }, new Request('http://localhost'), document, 'test', { token: 'original', user: { id: 3 } }, true)
  let time = 0
  const access = createAccessRevalidator(async () => {
    if (time >= 120) throw new Error('Not authorized')
    return { userId: 3, writable: false }
  }, { interval: 30, now: () => time })
  access.markAuthorized(connection)
  try {
    time = 30
    await access.refresh('7', connection)
    assert.equal(frames.length, 1)
    time = 150
    await assert.rejects(access.refresh('7', connection), /Not authorized/)
    assert.equal(document.hasConnection(connection), false)
  } finally { document.destroy() }
})

test('before-message refresh changes effective write permission and shares concurrent checks', async () => {
  const { createAccessRevalidator } = await import('../src/access-revalidation.js')
  let calls = 0
  let time = 0
  const connection = { context: { token: 'original', user: { id: 3 } }, readOnly: false, close() {} }
  const access = createAccessRevalidator(async (_, token) => { calls++; assert.equal(token, 'refreshed'); return { writable: false, userId: 3 } }, { interval: 10, now: () => time })
  access.markAuthorized(connection)
  time = 11
  connection.context.token = 'refreshed'
  await Promise.all([access.refresh('7', connection), access.refresh('7', connection)])
  assert.equal(calls, 1)
  assert.equal(connection.readOnly, true)
})

test('expired authorization rejects a message before processing it', async () => {
  const { createAccessRevalidator } = await import('../src/access-revalidation.js')
  let closed = false
  const connection = { context: { token: 'expired' }, close: () => { closed = true } }
  const access = createAccessRevalidator(async () => { throw new Error('Not authorized') })
  await assert.rejects(access.refresh('7', connection), /Not authorized/)
  assert.ok(closed)
})
