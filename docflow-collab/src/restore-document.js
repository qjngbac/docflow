import * as Y from 'yjs'

export async function replaceLiveContent(instance, store, documentId, replacement) {
  const connection = await instance.openDirectConnection(String(documentId), {})
  try {
    const payload = await store.checkpoint(documentId, connection.document, null, 'MANUAL', null, replacement)
    await connection.transact(document => Y.applyUpdate(document, payload))
    return { ...(await store.status(documentId)), replaced: true }
  } finally {
    await connection.disconnect({ unloadImmediately: false })
  }
}

export async function restoreLiveCheckpoint(instance, store, documentId, historyId, context = {}) {
  // A direct connection also loads the room when the admin request reaches an idle node.
  const connection = await instance.openDirectConnection(String(documentId), context)
  let restored
  try {
    const status = await store.restoreCheckpoint(documentId, historyId, connection.document)
    restored = await store.load(documentId)
    // The normal Hocuspocus update path broadcasts to clients and Redis peers.
    await connection.transact(document => Y.applyUpdate(document, Y.encodeStateAsUpdate(restored)))
    return status
  } finally {
    restored?.destroy()
    await connection.disconnect({ unloadImmediately: false })
  }
}
