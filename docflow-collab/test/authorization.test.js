import test from 'node:test'
import assert from 'node:assert/strict'
import { authorize, updateMaterializedSnapshot } from '../src/authorization.js'

test('invalid document names are rejected before contacting Java', async () => {
  await assert.rejects(() => authorize('../1', 'token'), /Not authorized/)
})

test('Java permission rejection prevents CRDT subscription', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response('', { status: 403 }))
  await assert.rejects(() => authorize('7', 'token'), /Not authorized/)
})

test('authorized Java response supplies the collaboration scope', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({
    code: 200,
    data: { documentId: 7, userId: 2, writable: false, protocolVersion: 1 }
  }), { status: 200, headers: { 'Content-Type': 'application/json' } }))
  const access = await authorize('7', 'token')
  assert.equal(access.userId, 2)
  assert.equal(access.writable, false)
})

test('materialized snapshot uses the internal CRDT secret instead of a user token', async t => {
  const previous = process.env.CRDT_ADMIN_SECRET
  process.env.CRDT_ADMIN_SECRET = 'test-admin-secret'
  t.after(() => {
    if (previous === undefined) delete process.env.CRDT_ADMIN_SECRET
    else process.env.CRDT_ADMIN_SECRET = previous
  })
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    assert.equal(options.headers['X-CRDT-Admin-Secret'], 'test-admin-secret')
    assert.equal(options.headers.Authorization, undefined)
    assert.deepEqual(JSON.parse(options.body), { html: '<p>saved</p>', editorUserId: 7 })
    return new Response(JSON.stringify({ code: 200 }), { status: 200, headers: { 'Content-Type': 'application/json' } })
  })

  await updateMaterializedSnapshot('3', '<p>saved</p>', 7)
})
