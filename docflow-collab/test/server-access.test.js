import assert from 'node:assert/strict'
import test from 'node:test'
import { applyInitialAccess } from '../src/access-context.js'

test('initial authentication applies write permission through connectionConfig', () => {
  const data = {
    connectionConfig: { readOnly: true },
    requestParameters: new URLSearchParams('clientId=browser-1'),
    socketId: 'socket-1',
    token: 'jwt-token'
  }
  const context = applyInitialAccess(data, {
    protocolVersion: 1,
    documentId: 13,
    userId: 4,
    displayName: 'User',
    avatar: null,
    writable: true,
    initialContent: '<p></p>',
    initialContentFormat: 'HTML'
  })

  assert.equal(data.connectionConfig.readOnly, false)
  assert.equal(context.readOnly, false)
  assert.equal(context.clientId, 'browser-1')
  assert.equal(context.documentId, 13)
})

test('initial authentication keeps read-only collaborators read-only', () => {
  const data = {
    connectionConfig: { readOnly: false },
    requestParameters: new URLSearchParams(),
    socketId: 'socket-2',
    token: 'jwt-token'
  }
  const context = applyInitialAccess(data, {
    protocolVersion: 1,
    documentId: 13,
    userId: 5,
    displayName: 'Reader',
    avatar: null,
    writable: false,
    initialContent: '<p></p>',
    initialContentFormat: 'HTML'
  })

  assert.equal(data.connectionConfig.readOnly, true)
  assert.equal(context.readOnly, true)
  assert.equal(context.clientId, 'socket-2')
})
