import { describe, expect, it } from 'vitest'
import { crdtDocumentKey, deleteLocalCrdtCopy } from './crdtStorage'

describe('crdt local copy storage', () => {
  it('builds the same stable per-document key that the editor persists under', () => {
    // 键名必须与 CrdtRichTextEditor 里的 IndexeddbPersistence 一致，否则清理就清错了库。
    expect(crdtDocumentKey(42)).toBe('docflow:crdt:42')
    expect(crdtDocumentKey('42')).toBe('docflow:crdt:42')
    expect(crdtDocumentKey(42)).not.toBe(crdtDocumentKey(43))
  })

  it('never throws when IndexedDB is unavailable or deletion is blocked', async () => {
    const result = await deleteLocalCrdtCopy(42)
    expect(typeof result).toBe('boolean')
  })
})
