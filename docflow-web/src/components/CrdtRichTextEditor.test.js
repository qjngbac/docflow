import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick, reactive } from 'vue'
import CrdtRichTextEditor from './CrdtRichTextEditor.vue'

const mocks = vi.hoisted(() => ({ providers: [], persistence: [], editor: null }))
vi.mock('@tiptap/vue-3', async () => {
  const { shallowRef } = await import('vue')
  return { useEditor: () => shallowRef(mocks.editor), EditorContent: { render: () => null } }
})
vi.mock('@hocuspocus/provider', () => ({ HocuspocusProvider: class {
  constructor(options) { this.options = options; this.document = options.document; this.configuration = { websocketProvider: { status: 'connected' } }; this.status = 'connected'; this.destroy = vi.fn(); this.connect = vi.fn(); this.disconnect = vi.fn(); mocks.providers.push(this) }
  setAwarenessField() {}
} }))
vi.mock('y-indexeddb', () => ({ IndexeddbPersistence: class {
  constructor() { this.clearData = vi.fn(async () => {}); this.destroy = vi.fn(async () => {}); mocks.persistence.push(this) }
} }))
vi.mock('../api', () => ({ getRealtimeAccessToken: async () => 'test', referenceApi: {} }))
vi.mock('./MathFieldEditor.vue', () => ({ __esModule: true, default: { render: () => null } }))
let app, state, props, host, statuses, realSockets
beforeEach(() => {
  mocks.providers = []; mocks.persistence = []
  mocks.editor = { setEditable: vi.fn(), destroy: vi.fn(), commands: { setContent: vi.fn() } }
  statuses = []
  realSockets = []
  props = reactive({ docId: 1, user: { id: 1 }, editable: true, onStatus: event => statuses.push(event.status) })
  host = document.createElement('div'); document.body.append(host)
  app = createApp({ ...CrdtRichTextEditor, render: () => null }, props)
  app.mount(host); state = app._instance.setupState
})
afterEach(async () => { app.unmount(); for (const socket of realSockets) socket.destroy(); await nextTick(); host.remove() })

async function useDelayedClosingSocket() {
  const { HocuspocusProviderWebsocket } = await vi.importActual('@hocuspocus/provider')
  const socket = new HocuspocusProviderWebsocket({ url: 'ws://memory.invalid', autoConnect: false, initialDelay: 0 })
  realSockets.push(socket)
  socket.status = 'connected'; socket.shouldConnect = true
  // Model a real asynchronous close handshake; no actual sockets are opened.
  socket.webSocket = { identifier: 'fixture', close() {}, removeEventListener() {} }
  socket.webSocketHandlers.fixture = {}
  socket.createWebSocketConnection = async () => { await socket.onOpen(new Event('open')) }
  const provider = mocks.providers[0]
  provider.configuration.websocketProvider = socket
  provider.disconnect.mockImplementation(() => socket.disconnect())
  provider.connect.mockImplementation(() => socket.connect())
  return socket
}

describe('CRDT permission lifecycle', () => {
  it('waits for the real transport disconnect before reopening a delayed closing socket', async () => {
    const socket = await useDelayedClosingSocket()
    const document = mocks.providers[0].document
    document.getText('pending').insert(0, '保留离线输入')
    state.reconnect(); state.reconnect()
    await new Promise(resolve => setTimeout(resolve, 10))
    expect(socket.status).toBe('connected')
    socket.onClose({ event: new CloseEvent('close') })
    await vi.waitFor(() => expect(socket.status).toBe('connected'), { timeout: 200 })
    expect(socket.shouldConnect).toBe(true)
    expect(document.getText('pending').toString()).toBe('保留离线输入')
    expect(mocks.persistence[0].clearData).not.toHaveBeenCalled()
  })

  it('cancels a pending reconnect when that editor session is disposed', async () => {
    const socket = await useDelayedClosingSocket()
    state.reconnect()
    await state.clearLocalState()
    socket.onClose({ event: new CloseEvent('close') })
    await new Promise(resolve => setTimeout(resolve, 10))
    expect(socket.status).toBe('disconnected')
    expect(socket.shouldConnect).toBe(false)
  })

  it('reports a document-only server close even while its websocket remains connected', async () => {
    const provider = mocks.providers[0]
    expect(provider.options.onClose).toBeTypeOf('function')
    provider.options.onClose({ event: { code: 1000, reason: 'Document permissions changed' } })
    await Promise.resolve()
    expect(statuses).toEqual(['session-closed'])
    expect(provider.connect).not.toHaveBeenCalled()
  })

  it('leaves physical websocket failures to the existing reconnect path', async () => {
    const provider = mocks.providers[0]
    expect(provider.options.onClose).toBeTypeOf('function')
    // Hocuspocus calls this callback before updating websocket status.
    provider.options.onClose({ event: { code: 1006 } })
    provider.configuration.websocketProvider.status = 'disconnected'
    await Promise.resolve()
    expect(statuses).toEqual([])
    expect(provider.connect).not.toHaveBeenCalled()
  })

  it('ignores a document-close callback queued before session disposal', async () => {
    const provider = mocks.providers[0]
    expect(provider.options.onClose).toBeTypeOf('function')
    provider.options.onClose({ event: { code: 1000, reason: 'Not authorized' } })
    await state.clearLocalState()
    expect(statuses).toEqual([])
  })

  it('updates Tiptap editability when write permission changes', async () => {
    app._instance.props.editable = false; await nextTick()
    expect(mocks.editor.setEditable).toHaveBeenLastCalledWith(false)
    app._instance.props.editable = true; await nextTick()
    expect(mocks.editor.setEditable).toHaveBeenLastCalledWith(true)
  })

  it('disposes the in-memory branch and connection before clearing local persistence', async () => {
    const provider = mocks.providers[0], persistence = mocks.persistence[0]
    provider.document.getText('test').insert(0, 'Rejected local branch')
    let stateAtClear
    persistence.clearData.mockImplementation(async () => {
      stateAtClear = {
        providerDestroyed: provider.destroy.mock.calls.length,
        editorDestroyed: mocks.editor.destroy.mock.calls.length,
        documentDestroyed: provider.document.isDestroyed
      }
    })
    await state.clearLocalState()
    expect(persistence.clearData).toHaveBeenCalledOnce()
    expect(provider.destroy).toHaveBeenCalledOnce()
    expect(provider.document.isDestroyed).toBe(true)
    expect(stateAtClear).toEqual({ providerDestroyed: 1, editorDestroyed: 1, documentDestroyed: true })
    // A destroyed branch cannot be reconnected even before the parent remounts.
    state.reconnect(); await new Promise(resolve => setTimeout(resolve, 0))
    expect(provider.connect).not.toHaveBeenCalled()
  })
})
