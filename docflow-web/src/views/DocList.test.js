import { afterEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick } from 'vue'
import { createPinia } from 'pinia'
import { useDocStore } from '../store'
import DocList from './DocList.vue'
import { docApi } from '../api'

vi.mock('../api', () => ({ docApi: { list: vi.fn() }, folderApi: {}, fileApi: {}, userApi: {} }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('../components/CreateDocumentDialog.vue', () => ({ default: { render: () => null } }))
let app, host
afterEach(() => { app?.unmount(); host?.remove(); vi.resetAllMocks() })

describe('workspace loading results', () => {
  function mount() {
    const pinia = createPinia()
    host = document.createElement('div'); document.body.append(host)
    app = createApp(DocList); app.use(pinia); app.mount(host)
    return useDocStore(pinia)
  }

  it('shows a request failure instead of an empty library, and reloads on retry', async () => {
    const store = mount()
    docApi.list.mockRejectedValueOnce(new Error('服务器处理失败'))
    await expect(store.loadDocs()).rejects.toThrow()
    await nextTick()
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('文档加载失败')
    expect(host.textContent).not.toContain('还没有文档')
    expect(host.textContent).not.toContain('0 篇文档')
    docApi.list.mockResolvedValueOnce({ data: [{ id: 11, title: '原有文档' }] })
    host.querySelector('[role="alert"] button').click()
    await new Promise(resolve => setTimeout(resolve, 0))
    await nextTick()
    expect(host.querySelector('[role="alert"]')).toBeNull()
    expect(host.textContent).toContain('原有文档')
  })
})
