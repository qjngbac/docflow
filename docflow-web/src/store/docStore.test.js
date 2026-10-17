import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useDocStore } from './index'
import { docApi } from '../api'

vi.mock('../api', () => ({
  docApi: { list: vi.fn(), search: vi.fn(), trashSettings: vi.fn() },
  folderApi: {}
}))

describe('document list state', () => {
  beforeEach(() => { vi.resetAllMocks(); setActivePinia(createPinia()) })

  it.each(['documents', 'recent', 'favorites', 'trash', 'search'])('records a %s load failure without losing cached documents, and can retry', async mode => {
    const store = useDocStore()
    store.docs = [{ id: 2, title: '原有文档' }]
    const request = mode === 'search' ? docApi.search : docApi.list
    request.mockRejectedValueOnce(new Error('服务器处理失败'))
    docApi.trashSettings.mockResolvedValue({ data: { retentionDays: 30 } })
    const load = () => mode === 'documents' ? store.loadDocs(7)
      : mode === 'trash' ? store.loadTrash()
        : mode === 'search' ? store.searchDocs('原有') : store.loadScope(mode)

    await expect(load()).rejects.toThrow('服务器处理失败')
    expect(store.docs).toEqual([{ id: 2, title: '原有文档' }])
    expect(store.loadError).toBeTruthy()
    expect(store.loading).toBe(false)

    request.mockResolvedValueOnce({ data: [] })
    await store.retryLoad()
    expect(store.loadError).toBe('')
    expect(store.docs).toEqual([])
    if (mode === 'search') expect(request).toHaveBeenLastCalledWith('原有')
    if (mode === 'documents') expect(request).toHaveBeenLastCalledWith({ folderId: 7 })
  })

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
