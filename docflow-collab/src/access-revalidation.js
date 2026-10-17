// Java remains authoritative after the WebSocket handshake, including idle readers.
export function createAccessRevalidator(authorize, { interval = 30_000, now = Date.now } = {}) {
  const checkedAt = new WeakMap()
  const pending = new WeakMap()
  function markAuthorized(connection) { checkedAt.set(connection, now()) }
  async function refresh(documentName, connection) {
    if (pending.has(connection)) return pending.get(connection)
    if (now() - (checkedAt.get(connection) ?? -Infinity) < interval) return
    const check = (async () => {
      try {
        const access = await authorize(documentName, connection.context?.token)
        if (connection.context?.user?.id !== access.userId) throw new Error('Not authorized')
        const changed = connection.readOnly !== !access.writable
        connection.readOnly = !access.writable
        connection.context.readOnly = !access.writable
        markAuthorized(connection)
        // 重新握手使客户端 UI 与服务端权限同时更新。
        if (changed) connection.close({ code: 4403, reason: 'Document permissions changed' })
        else connection.requestToken()
      } catch (error) {
        connection.close({ code: 4401, reason: 'Not authorized' })
        throw error
      }
    })()
    pending.set(connection, check)
    try { await check } finally { pending.delete(connection) }
  }
  async function refreshAll(documents) {
    await Promise.allSettled(Array.from(documents, ([name, document]) =>
      Array.from(document.connections.keys(), connection => refresh(name, connection))).flat())
  }
  return { refresh, refreshAll, markAuthorized }
}
