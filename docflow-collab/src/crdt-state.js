import { createHash } from 'node:crypto'
import * as Y from 'yjs'

export const PROTOCOL_VERSION = Number(process.env.CRDT_PROTOCOL_VERSION || 1)

export function updateId(documentId, payload) {
  // 文档 ID 参与摘要，防止相同二进制更新在不同文档间共享去重键。
  return createHash('sha256')
    .update(String(documentId))
    .update(Buffer.from(payload))
    .digest('hex')
}

export function restoreDocument(checkpoint, updates = []) {
  // Yjs 更新具备交换和幂等性质，因此可安全处理重复或乱序到达的增量。
  const document = new Y.Doc()
  if (checkpoint?.length) Y.applyUpdate(document, new Uint8Array(checkpoint), 'checkpoint')
  for (const update of updates) {
    const payload = update.payload || update
    if (payload?.length) Y.applyUpdate(document, new Uint8Array(payload), 'increment')
  }
  return document
}

export function encodeCheckpoint(document) {
  return Y.encodeStateAsUpdate(document)
}

export function validateUpdateSize(payload, maxBytes) {
  if (!payload?.byteLength) return
  if (payload.byteLength > maxBytes) throw new Error('CRDT update exceeds message limit')
}
