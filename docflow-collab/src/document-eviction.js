/**
 * 关闭指定文档的全部连接并把它从内存卸载。
 *
 * @hocuspocus/server 4.x 的 `Server` 只是外层包装对象：WebSocket/HTTP 与配置在 `Server` 上，
 * 而文档表与卸载方法在它持有的 Hocuspocus 实例 `server.hocuspocus` 上：
 * `hocuspocus.documents`、`hocuspocus.closeConnections()`、`hocuspocus.unloadDocument()`。
 * 把这段逻辑集中在一处，避免再次误用外层 Server 的同名 API。
 *
 * 文档当前没有任何连接（甚至不在内存中）时视为无事可做，直接返回，
 * 这样“删除文档 / 降级权限后关闭连接”不会因为文档未加载而失败。
 */
export function createDocumentEvictor(hocuspocus) {
  return async function evictDocument(documentId) {
    const name = String(documentId)
    const active = hocuspocus?.documents?.get(name)
    if (!active) return
    hocuspocus.closeConnections(name)
    await hocuspocus.unloadDocument(active)
  }
}
