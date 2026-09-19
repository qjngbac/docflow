import 'dotenv/config'
import { Server } from '@hocuspocus/server'
import * as Y from 'yjs'
import { applyInitialAccess } from './access-context.js'
import { authorize, updateMaterializedSnapshot } from './authorization.js'
import { createInitialDocument, documentToHtml, replaceDocumentContent } from './initial-document.js'
import { redisExtensions } from './redis.js'
import { createPool, CrdtStore } from './store.js'
import { validateUpdateSize } from './crdt-state.js'
import { startAdminServer } from './admin-server.js'
import { createDocumentEvictor } from './document-eviction.js'

// Hocuspocus 负责连接生命周期和 Yjs 合并，Java 服务仍是身份与文档权限的唯一权威。
const maxUpdateBytes = Number(process.env.CRDT_MAX_UPDATE_BYTES || 1024 * 1024)
const pool = createPool()
const store = new CrdtStore(pool)
let adminServer

export const server = new Server({
  name: process.env.CRDT_SERVER_NAME || `docflow-collab-${process.pid}`,
  port: Number(process.env.CRDT_PORT || 1234),
  // 生产只监听回环地址，不暴露 0.0.0.0；需要对外时用反代转发。
  address: process.env.CRDT_HOST || '127.0.0.1',
  debounce: Number(process.env.CRDT_STORE_DEBOUNCE_MS || 3000),
  maxDebounce: Number(process.env.CRDT_STORE_MAX_DEBOUNCE_MS || 10000),
  extensions: redisExtensions(),

  async onAuthenticate(data) {
    const access = await authorize(data.documentName, data.token)
    return applyInitialAccess(data, access)
  },

  async onTokenSync(data) {
    const access = await authorize(data.documentName, data.token)
    data.connection.readOnly = !access.writable
    return { ...data.context, token: data.token, readOnly: !access.writable }
  },

  beforeHandleMessage({ update }) {
    validateUpdateSize(update, maxUpdateBytes)
  },

  beforeHandleAwareness({ states, context }) {
    // 覆盖客户端自报身份，避免伪造其他用户的光标和在线状态。
    if (!context?.user) return
    for (const state of states.values()) state.user = context.user
  },

  async onLoadDocument({ documentName, context }) {
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
    await store.checkpoint(documentName, document)
    if (lastContext?.user?.id) {
      try {
        await updateMaterializedSnapshot(documentName, documentToHtml(document), lastContext.user.id)
      } catch (error) {
        console.warn(`Materialized snapshot update failed for document ${documentName}`)
      }
    }
  },

  async onDestroy() {
    if (adminServer) await new Promise(resolve => adminServer.close(resolve))
    await pool.end()
  }
})

async function start() {
  await store.verify()
  // 文档表与卸载方法属于 Hocuspocus 实例（server.hocuspocus），不是外层 Server 包装对象。
  const evictDocument = createDocumentEvictor(server.hocuspocus)
  adminServer = await startAdminServer(store, evictDocument, async (documentId, html) => {
    // 历史版本回滚在同一 Y.Doc 谱系内替换根片段，在线客户端会收到正常 CRDT 更新。
    const name = String(documentId)
    const replacement = createInitialDocument({ initialContent: html, initialContentFormat: 'HTML' })
    const active = server.hocuspocus.documents.get(name)
    const working = new Y.Doc()
    let persisted
    try {
      persisted = active || await store.load(name)
      if (persisted) Y.applyUpdate(working, Y.encodeStateAsUpdate(persisted))
      replaceDocumentContent(working, replacement)
      await store.checkpoint(name, working, null, 'MANUAL')
      if (active) {
        const update = Y.encodeStateAsUpdate(working, Y.encodeStateVector(active))
        if (update.byteLength) Y.applyUpdate(active, update, 'docflow-version-rollback')
      }
      return { ...(await store.status(name)), replaced: true }
    } finally {
      replacement.destroy()
      working.destroy()
      if (persisted && persisted !== active) persisted.destroy()
    }
  })
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
