const SUPPORTED_ACTIONS = new Set(['created', 'updated', 'deleted'])

export function createCommentEvent(action, commentId, senderId, createdAt = Date.now(), idFactory) {
  const createId = idFactory || (() => globalThis.crypto?.randomUUID?.() || `${createdAt}-${Math.random().toString(36).slice(2)}`)
  return { id: createId(), action, commentId: Number(commentId), senderId: String(senderId), createdAt }
}

export function collectCommentEvents(states, seen, now = Date.now(), ttl = 30_000) {
  // Awareness 事件只用于提示其他客户端刷新，批注正文仍以 REST 查询结果为准。
  const values = states instanceof Map ? [...states.values()] : Array.from(states || [])
  const result = []
  for (const state of values) {
    const event = state?.commentEvent
    if (!event?.id || !SUPPORTED_ACTIONS.has(event.action) || !Number.isFinite(Number(event.commentId))) continue
    if (!Number.isFinite(Number(event.createdAt)) || now - Number(event.createdAt) > ttl || Number(event.createdAt) > now + 5_000) continue
    if (seen.has(event.id)) continue
    seen.add(event.id)
    result.push(event)
  }
  // 限制去重集合大小，避免长时间打开编辑器时持续增长。
  while (seen.size > 500) seen.delete(seen.values().next().value)
  return result
}
