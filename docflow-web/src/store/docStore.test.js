import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useDocStore } from './index'

describe('document list state', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('shows a newly created document immediately in the current folder without duplicates', () => {
    const store = useDocStore()
    store.currentFolderId = 7
    store.viewMode = 'documents'
    store.docs = [{ id: 1, title: '已有文档', folderId: 7 }]

    const created = { id: 2, title: '刚创建的文档', folderId: 7, isDeleted: 0 }
    store.rememberCreatedDoc(created)
    store.rememberCreatedDoc({ ...created, title: '刚创建的文档（已同步）' })

    expect(store.docs.map(item => item.id)).toEqual([2, 1])
    expect(store.docs[0].title).toBe('刚创建的文档（已同步）')
  })

  it('does not add a created document to a different folder or the favorites view', () => {
    const store = useDocStore()
    store.currentFolderId = 7
    store.viewMode = 'documents'

    store.rememberCreatedDoc({ id: 2, title: '其他文件夹', folderId: 8, isDeleted: 0 })
    expect(store.docs).toEqual([])

    store.viewMode = 'favorites'
    store.rememberCreatedDoc({ id: 3, title: '新文档', folderId: 7, isDeleted: 0 })
    expect(store.docs).toEqual([])
  })

  it('updates the cached document title after it is saved in the editor', () => {
    const store = useDocStore()
    store.docs = [{ id: 2, title: '旧文件名', folderId: 0, summary: '保留摘要' }]
    store.currentDoc = { id: 2, title: '旧文件名', content: '正文' }

    store.rememberUpdatedDoc({ id: 2, title: '新文件名', updatedAt: '2026-08-12T10:00:00' })

    expect(store.docs[0]).toEqual({
      id: 2,
      title: '新文件名',
      folderId: 0,
      summary: '保留摘要',
      updatedAt: '2026-08-12T10:00:00'
    })
    expect(store.currentDoc).toEqual({
      id: 2,
      title: '新文件名',
      content: '正文',
      updatedAt: '2026-08-12T10:00:00'
    })
  })
})
