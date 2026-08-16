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

const maxUpdateBytes = Number(process.env.CRDT_MAX_UPDATE_BYTES || 1024 * 1024)
const pool = createPool()
const store = new CrdtStore(pool)
let adminServer

export const server = new Server({
  name: process.env.CRDT_SERVER_NAME || `docflow-collab-${process.pid}`,
  port: Number(process.env.CRDT_PORT || 1234),
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
  adminServer = await startAdminServer(store, async documentId => {
    const name = String(documentId)
    const active = server.documents.get(name)
    if (!active) return
    server.closeConnections(name)
    await server.unloadDocument(active)
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
