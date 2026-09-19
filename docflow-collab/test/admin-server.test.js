import assert from 'node:assert/strict'
import http from 'node:http'
import test from 'node:test'
import { startAdminServer } from '../src/admin-server.js'

function request(port, path, authorization, method = 'GET', body) {
  return new Promise((resolve, reject) => {
    const payload = body == null ? null : JSON.stringify(body)
    const headers = authorization ? { authorization } : {}
    if (payload) {
      headers['content-type'] = 'application/json'
      headers['content-length'] = Buffer.byteLength(payload)
    }
    const call = http.request({ host: '127.0.0.1', port, path, headers, method }, (response) => {
      let body = ''
      response.setEncoding('utf8')
      response.on('data', (chunk) => { body += chunk })
      response.on('end', () => resolve({ status: response.statusCode, body: JSON.parse(body) }))
    })
    call.on('error', reject)
    if (payload) call.write(payload)
    call.end()
  })
}

// 管理服务只监听随机回环端口，结束后还原环境变量，避免测试之间互相影响。
async function withAdminServer(handlers = {}, run) {
  const previous = {
    port: process.env.CRDT_ADMIN_PORT,
    host: process.env.CRDT_ADMIN_HOST,
    secret: process.env.CRDT_ADMIN_SECRET
  }
  process.env.CRDT_ADMIN_PORT = '0'
  process.env.CRDT_ADMIN_HOST = '127.0.0.1'
  process.env.CRDT_ADMIN_SECRET = 'test-secret'
  const server = await startAdminServer(handlers.store ?? {}, handlers.evictDocument, handlers.replaceDocument)
  try {
    return await run(server.address().port, 'Bearer test-secret')
  } finally {
    await new Promise((resolve) => server.close(resolve))
    for (const [key, value] of Object.entries(previous)) {
      const name = `CRDT_ADMIN_${key.toUpperCase()}`
      if (value === undefined) delete process.env[name]
      else process.env[name] = value
    }
  }
}

test('health endpoint is available without exposing checkpoint administration', async () => {
  await withAdminServer({}, async (port) => {
    const health = await request(port, '/health')
    assert.equal(health.status, 200)
    assert.equal(health.body.status, 'UP')

    const protectedCall = await request(port, '/documents/1/checkpoint')
    assert.equal(protectedCall.status, 401)
  })
})

test('unknown paths are rejected without touching the store', async () => {
  await withAdminServer({}, async (port, authorization) => {
    const missing = await request(port, '/documents/1/unknown', authorization)
    assert.equal(missing.status, 404)

    const wrongMethod = await request(port, '/documents/1/connections/close', authorization, 'GET')
    assert.equal(wrongMethod.status, 405)
  })
})

test('checkpoint endpoints expose status, force checkpoint and history', async () => {
  const calls = []
  const store = {
    status: async id => { calls.push(['status', id]); return { documentId: id, sequence: 3 } },
    forceCheckpoint: async id => { calls.push(['forceCheckpoint', id]); return { documentId: id, sequence: 4 } },
    listHistory: async id => { calls.push(['listHistory', id]); return [{ id: 9, reason: 'MANUAL' }] }
  }
  await withAdminServer({ store }, async (port, authorization) => {
    const status = await request(port, '/documents/11/checkpoint', authorization)
    assert.equal(status.status, 200)
    assert.equal(status.body.sequence, 3)

    const forced = await request(port, '/documents/11/checkpoint', authorization, 'POST')
    assert.equal(forced.status, 200)
    assert.equal(forced.body.sequence, 4)

    const history = await request(port, '/documents/11/checkpoints', authorization)
    assert.equal(history.status, 200)
    assert.deepEqual(history.body.items, [{ id: 9, reason: 'MANUAL' }])

    assert.deepEqual(calls, [['status', 11], ['forceCheckpoint', 11], ['listHistory', 11]])
  })
})

test('restore loads the requested checkpoint and evicts the live document first', async () => {
  const order = []
  const store = {
    restoreCheckpoint: async (id, checkpointId) => {
      order.push(`restore:${id}:${checkpointId}`)
      return { documentId: id, restored: true }
    }
  }
  const evictDocument = async id => { order.push(`evict:${id}`) }
  await withAdminServer({ store, evictDocument }, async (port, authorization) => {
    const response = await request(port, '/documents/11/checkpoints/9/restore', authorization, 'POST')
    assert.equal(response.status, 200)
    assert.equal(response.body.restored, true)
    assert.deepEqual(order, ['evict:11', 'restore:11:9'])
  })
})

test('connections/close answers 200 whether or not the document is loaded', async () => {
  const evicted = []
  const documents = new Map()
  const evictDocument = async documentId => {
    const name = String(documentId)
    const active = documents.get(name)
    if (!active) return
    evicted.push(name)
    documents.delete(name)
  }
  await withAdminServer({ store: {}, evictDocument }, async (port, authorization) => {
    // 文档没有连接、内存里也没有它时，关闭连接必须是安全的空操作。
    const idle = await request(port, '/documents/42/connections/close', authorization, 'POST')
    assert.equal(idle.status, 200)
    assert.deepEqual(idle.body, { documentId: 42, disconnected: true })
    assert.deepEqual(evicted, [])

    // 有活跃文档时返回同样的结果，并真正执行一次卸载。
    documents.set('42', { name: '42' })
    const active = await request(port, '/documents/42/connections/close', authorization, 'POST')
    assert.equal(active.status, 200)
    assert.deepEqual(active.body, { documentId: 42, disconnected: true })
    assert.deepEqual(evicted, ['42'])
  })
})

test('connections/close still answers 200 when no evictor is wired in', async () => {
  await withAdminServer({ store: {} }, async (port, authorization) => {
    const response = await request(port, '/documents/5/connections/close', authorization, 'POST')
    assert.equal(response.status, 200)
    assert.deepEqual(response.body, { documentId: 5, disconnected: true })
  })
})

test('content replacement validates the body and reports an unavailable writer', async () => {
  const replaced = []
  const replaceDocument = async (id, html) => {
    replaced.push({ id, html })
    return { documentId: id, replaced: true }
  }
  await withAdminServer({ store: {}, replaceDocument }, async (port, authorization) => {
    const invalid = await request(port, '/documents/7/content', authorization, 'POST', { text: 'no html' })
    assert.equal(invalid.status, 400)
    assert.deepEqual(replaced, [])

    const response = await request(port, '/documents/7/content', authorization, 'POST', { html: '<p>Earlier</p>' })
    assert.equal(response.status, 200)
    assert.deepEqual(replaced, [{ id: 7, html: '<p>Earlier</p>' }])
  })

  await withAdminServer({ store: {} }, async (port, authorization) => {
    const unavailable = await request(port, '/documents/7/content', authorization, 'POST', { html: '<p>x</p>' })
    assert.equal(unavailable.status, 503)
  })
})

test('failures identify the failing admin operation and never log the shared secret', async () => {
  const warnings = []
  const originalWarn = console.warn
  console.warn = (...args) => { warnings.push(args.join(' ')) }
  try {
    const store = {
      status: async () => { throw new Error('checkpoint store is corrupt') },
      listHistory: async () => { throw new Error('history store is corrupt') }
    }
    const evictDocument = async () => { throw new Error('cannot evict') }
    await withAdminServer({ store, evictDocument }, async (port, authorization) => {
      const checkpointFailure = await request(port, '/documents/3/checkpoint', authorization)
      const closeFailure = await request(port, '/documents/3/connections/close', authorization, 'POST')
      const historyFailure = await request(port, '/documents/3/checkpoints', authorization)

      assert.equal(checkpointFailure.status, 409)
      assert.equal(closeFailure.status, 409)
      assert.equal(historyFailure.status, 409)
      // 不同操作不能用同一句 checkpoint 失败冒充。
      assert.equal(checkpointFailure.body.message, 'Checkpoint operation failed')
      assert.equal(closeFailure.body.message, 'Disconnecting document connections failed')
      assert.equal(historyFailure.body.message, 'Checkpoint history lookup failed')
    })
  } finally {
    console.warn = originalWarn
  }

  const logged = warnings.join('\n')
  assert.match(logged, /GET \/documents\/3\/checkpoint/)
  assert.match(logged, /POST \/documents\/3\/connections\/close/)
  assert.match(logged, /checkpoint store is corrupt/)
  assert.match(logged, /cannot evict/)
  assert.doesNotMatch(logged, /test-secret/)
})
