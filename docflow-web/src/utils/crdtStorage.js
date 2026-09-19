/**
 * 协作文档本地离线副本（y-indexeddb）的键名与清理。
 *
 * 编辑器用 IndexeddbPersistence 把 Yjs 文档镜像到浏览器 IndexedDB，供离线编辑使用；
 * 但服务端一旦拒绝写入（权限被降级/移除、文档进回收站），这份本地副本就会变成"分叉"：
 * 用户仍能看到自己的修改，日后恢复写权限时还可能被合并回服务端。所以键名必须唯一可枚举、可清理。
 */
export function crdtDocumentKey(docId) {
  return `docflow:crdt:${docId}`
}

/** 删除某个文档的本地副本（在编辑器挂载前调用；编辑器已挂载时用其 clearLocalState）。 */
export function deleteLocalCrdtCopy(docId) {
  return new Promise(resolve => {
    if (typeof indexedDB === 'undefined') {
      resolve(false)
      return
    }
    try {
      const request = indexedDB.deleteDatabase(crdtDocumentKey(docId))
      request.onsuccess = () => resolve(true)
      request.onerror = () => resolve(false)
      // 仍有连接占用时浏览器会阻塞删除，此时放弃，交由编辑器的 clearData 处理。
      request.onblocked = () => resolve(false)
    } catch {
      resolve(false)
    }
  })
}
