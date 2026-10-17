import 'dotenv/config'
import { Server } from '@hocuspocus/server'
import * as Y from 'yjs'
import { applyInitialAccess } from './access-context.js'
import { authorize, updateMaterializedSnapshot } from './authorization.js'
import { createInitialDocument, documentToHtml } from './initial-document.js'
import { redisExtensions } from './redis.js'
import { createPool, CrdtStore } from './store.js'
import { validateUpdateSize } from './crdt-state.js'
import { startAdminServer } from './admin-server.js'
import { createDocumentEvictor } from './document-eviction.js'
import { createAccessRevalidator } from './access-revalidation.js'
import { replaceLiveContent, restoreLiveCheckpoint } from './restore-document.js'

// Hocuspocus 负责连接生命周期和 Yjs 合并，Java 服务仍是身份与文档权限的唯一权威。
const maxUpdateBytes = Number(process.env.CRDT_MAX_UPDATE_BYTES || 1024 * 1024)
const pool = createPool()
const store = new CrdtStore(pool)
let adminServer
let authorizationTimer
let materializationTimer
let materializationRunning
const restoringDocuments = new Set()
const internalRestore = Symbol('internalRestore')
const authorizationInterval = Math.max(1000, Math.min(60_000, Number(process.env.CRDT_AUTH_REVALIDATE_MS) || 30_000))
const accessRevalidator = createAccessRevalidator(authorize, { interval: authorizationInterval })

function materializePending() {
  if (materializationRunning) return materializationRunning
  materializationRunning = store.materializePending(async (name, payload, editorUserId, generation) => {
    const document = new Y.Doc()
    try {
      Y.applyUpdate(document, payload)
      await updateMaterializedSnapshot(name, documentToHtml(document), editorUserId, generation)
    } finally { document.destroy() }
  }).catch(() => {
    console.warn('Materialized snapshot queue processing failed')
  }).finally(() => { materializationRunning = null })
  return materializationRunning
}

export const server = new Server({
  name: process.env.CRDT_SERVER_NAME || `docflow-collab-${process.pid}`,
  port: Number(process.env.CRDT_PORT || 1234),
  // 生产只监听回环地址，不暴露 0.0.0.0；需要对外时用反代转发。
  address: process.env.CRDT_HOST || '127.0.0.1',
  debounce: Number(process.env.CRDT_STORE_DEBOUNCE_MS || 3000),
  maxDebounce: Number(process.env.CRDT_STORE_MAX_DEBOUNCE_MS || 10000),
  extensions: redisExtensions(),

  async onAuthenticate(data) {
    if (restoringDocuments.has(data.documentName)) throw new Error('Document restore in progress')
    const access = await authorize(data.documentName, data.token)
    if (restoringDocuments.has(data.documentName)) throw new Error('Document restore in progress')
    return applyInitialAccess(data, access)
  },

  async onTokenSync(data) {
    const access = await authorize(data.documentName, data.token)
    if (access.userId !== data.context?.user?.id) throw new Error('Not authorized')
    const changed = data.connection.readOnly !== !access.writable
    data.connection.readOnly = !access.writable
    const context = { ...data.context, token: data.token, readOnly: !access.writable }
    // Hocuspocus updates hook context, but Connection.context is also used for periodic checks.
    data.connection.context = context
    accessRevalidator.markAuthorized(data.connection)
    if (changed) data.connection.close({ code: 4403, reason: 'Document permissions changed' })
    return context
  },

  async beforeHandleMessage({ update, connection, documentName }) {
    validateUpdateSize(update, maxUpdateBytes)
    if (restoringDocuments.has(documentName)) throw new Error('Document restore in progress')
    await accessRevalidator.refresh(documentName, connection)
  },

  beforeHandleAwareness({ states, context }) {
    // 覆盖客户端自报身份，避免伪造其他用户的光标和在线状态。
    if (!context?.user) return
    for (const state of states.values()) state.user = context.user
  },

  async onLoadDocument({ documentName, context }) {
    if (restoringDocuments.has(documentName) && !context?.[internalRestore]) throw new Error('Document restore in progress')
    let document = await store.load(documentName)
    if (document) return document
    const initial = createInitialDocument(context)
    await store.initialize(documentName, Y.encodeStateAsUpdate(initial))
    document = await store.load(documentName)
    return document || initial
  },

  async onChange({ documentName, update, context }) {
    if (!update?.byteLength) return
    validateUpdateSize(update, maxUpdateBytes)
    await store.appendUpdate(documentName, update, context?.clientId)
  },

  async onStoreDocument({ documentName, document, lastContext }) {
    // checkpoint 成功后更新 Java 侧 HTML 快照，供列表摘要、搜索和非协作接口读取。
    const payload = await store.checkpoint(documentName, document, null, 'AUTO', lastContext?.user?.id)
    // Broadcast durable updates discovered from another node to this node's connected clients.
    Y.applyUpdate(document, payload, 'docflow-checkpoint-merge')
    void materializePending()
  },

  async onDestroy() {
    clearInterval(authorizationTimer)
    clearInterval(materializationTimer)
    if (materializationRunning) await materializationRunning
    if (adminServer) await new Promise(resolve => adminServer.close(resolve))
    await pool.end()
  }
})

async function start() {
  await store.verify()
  // 文档表与卸载方法属于 Hocuspocus 实例（server.hocuspocus），不是外层 Server 包装对象。
  const evictDocument = createDocumentEvictor(server.hocuspocus, active => store.checkpoint(active.name, active))
  adminServer = await startAdminServer(store, evictDocument, async (documentId, html, contentFormat = 'HTML') => {
    // 历史版本回滚在同一 Y.Doc 谱系内替换根片段，在线客户端会收到正常 CRDT 更新。
    const name = String(documentId)
    const replacement = createInitialDocument({ initialContent: html, initialContentFormat: contentFormat })
    try {
      return await replaceLiveContent(server.hocuspocus, store, name, replacement)
    } finally {
      replacement.destroy()
    }
  }, async (documentId, historyId) => {
    const name = String(documentId)
    if (restoringDocuments.has(name)) throw new Error('Document restore in progress')
    restoringDocuments.add(name)
    try {
      return await restoreLiveCheckpoint(server.hocuspocus, store, documentId, historyId, { [internalRestore]: true })
    } finally { restoringDocuments.delete(name) }
  })
  authorizationTimer = setInterval(() => { void accessRevalidator.refreshAll(server.hocuspocus.documents) }, Math.min(1000, authorizationInterval))
  materializationTimer = setInterval(() => { void materializePending() }, 5000)
  authorizationTimer.unref()
  materializationTimer.unref()
  void materializePending()
  await server.listen()
  console.log(`DocFlow CRDT collaboration server ready on port ${process.env.CRDT_PORT || 1234}`)
}

if (process.env.NODE_ENV !== 'test') {
  start().catch(async error => {
    console.error(`DocFlow CRDT collaboration server failed to start: ${error.message}`)
    await server.destroy()
    process.exitCode = 1
  })
}
