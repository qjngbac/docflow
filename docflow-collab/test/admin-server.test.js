import assert from 'node:assert/strict'
import http from 'node:http'
import test from 'node:test'
import { startAdminServer } from '../src/admin-server.js'

function request(port, path, authorization) {
  return new Promise((resolve, reject) => {
    const headers = authorization ? { authorization } : {}
    const call = http.get({ host: '127.0.0.1', port, path, headers }, (response) => {
      let body = ''
      response.setEncoding('utf8')
      response.on('data', (chunk) => { body += chunk })
      response.on('end', () => resolve({ status: response.statusCode, body: JSON.parse(body) }))
    })
    call.on('error', reject)
  })
}

test('health endpoint is available without exposing checkpoint administration', async () => {
  const previousPort = process.env.CRDT_ADMIN_PORT
  const previousHost = process.env.CRDT_ADMIN_HOST
  const previousSecret = process.env.CRDT_ADMIN_SECRET
  process.env.CRDT_ADMIN_PORT = '0'
  process.env.CRDT_ADMIN_HOST = '127.0.0.1'
  process.env.CRDT_ADMIN_SECRET = 'test-secret'

  const server = await startAdminServer({}, () => {})
  try {
    const port = server.address().port
    const health = await request(port, '/health')
    assert.equal(health.status, 200)
    assert.equal(health.body.status, 'UP')

    const protectedCall = await request(port, '/documents/1/checkpoint')
    assert.equal(protectedCall.status, 401)
  } finally {
    await new Promise((resolve) => server.close(resolve))
    if (previousPort === undefined) delete process.env.CRDT_ADMIN_PORT
    else process.env.CRDT_ADMIN_PORT = previousPort
    if (previousHost === undefined) delete process.env.CRDT_ADMIN_HOST
    else process.env.CRDT_ADMIN_HOST = previousHost
    if (previousSecret === undefined) delete process.env.CRDT_ADMIN_SECRET
    else process.env.CRDT_ADMIN_SECRET = previousSecret
  }
})
