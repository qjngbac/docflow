import assert from 'node:assert/strict'
import test from 'node:test'
import { Server } from '@hocuspocus/server'
import { createDocumentEvictor } from '../src/document-eviction.js'

test('the document table lives on the Hocuspocus instance, not on the Server wrapper', () => {
  // @hocuspocus/server 4.x 的 Server 只是外层包装：把 documents/closeConnections/unloadDocument
  // 当成 Server 自己的成员会在运行时报“不是函数”，这条断言用来锁住真实依赖结构。
  const server = new Server({ port: 0, quiet: true })
  assert.ok(server.hocuspocus.documents instanceof Map)
  assert.equal(typeof server.hocuspocus.closeConnections, 'function')
  assert.equal(typeof server.hocuspocus.unloadDocument, 'function')
  assert.equal(server.documents, undefined)
  assert.equal(server.closeConnections, undefined)
  assert.equal(server.unloadDocument, undefined)
})

test('evicting a document that is not loaded in memory is a safe no-op', async () => {
  const calls = []
  const hocuspocus = {
    documents: new Map(),
    closeConnections: () => calls.push('closeConnections'),
    unloadDocument: () => calls.push('unloadDocument')
  }
  await createDocumentEvictor(hocuspocus)(7)
  assert.deepEqual(calls, [])
})

test('evicting a loaded document closes its connections before unloading it', async () => {
  const order = []
  const document = { name: '7' }
  const hocuspocus = {
    documents: new Map([['7', document]]),
    closeConnections: (name) => order.push(`closeConnections:${name}`),
    unloadDocument: async (target) => {
      assert.equal(target, document)
      order.push('unloadDocument')
    }
  }
  await createDocumentEvictor(hocuspocus)('7')
  assert.deepEqual(order, ['closeConnections:7', 'unloadDocument'])
})

test('eviction tolerates a Hocuspocus instance without a documents table', async () => {
  // 装配异常或依赖升级时，“关闭连接 / 恢复恢复点”不应因此变成致命错误。
  await createDocumentEvictor({})(1)
  await createDocumentEvictor(undefined)(1)
  await createDocumentEvictor(null)(1)
})
