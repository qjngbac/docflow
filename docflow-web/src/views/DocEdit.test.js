import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { createApp, nextTick, reactive } from 'vue'
import DocEdit from './DocEdit.vue'

const mocks = vi.hoisted(() => ({
  route: null, leave: null, update: vi.fn(), detail: vi.fn(), access: vi.fn(), push: vi.fn(),
  rememberUpdatedDoc: vi.fn(), sockets: [], confirm: vi.fn(), routeUpdate: null, comments: vi.fn(), loadFolders: vi.fn(), loadDocs: vi.fn(),
  setTags: vi.fn(), rollback: vi.fn(), prompt: vi.fn(), createTemplate: vi.fn(), uploadAttachment: vi.fn(), listAttachments: vi.fn(), removeAttachment: vi.fn(),
  createShare: vi.fn(), createComment: vi.fn(), removeComment: vi.fn(), resolveComments: vi.fn(), addCollaborator: vi.fn(), removeCollaborator: vi.fn(),
  createCheckpoint: vi.fn(), restoreCheckpoint: vi.fn(), deleteVersions: vi.fn(), saveVersion: vi.fn(), reconnectCrdt: vi.fn(), clearCrdtLocal: vi.fn(async () => {})
}))
vi.mock('vue-router', () => ({
  useRoute: () => mocks.route, useRouter: () => ({ push: mocks.push }),
  onBeforeRouteLeave: handler => { mocks.leave = handler },
  onBeforeRouteUpdate: handler => { mocks.routeUpdate = handler }
}))
vi.mock('../api', () => ({
  docApi: { detail: mocks.detail, update: mocks.update }, crdtApi: { access: mocks.access, createCheckpoint: mocks.createCheckpoint, restoreCheckpoint: mocks.restoreCheckpoint },
  attachmentApi: { upload: mocks.uploadAttachment, list: mocks.listAttachments, remove: mocks.removeAttachment },
  commentApi: { list: mocks.comments, create: mocks.createComment, remove: mocks.removeComment, resolveBatch: mocks.resolveComments }, fileApi: {},
  permissionApi: { add: mocks.addCollaborator, remove: mocks.removeCollaborator }, shareApi: { create: mocks.createShare },
  tagApi: { setForDocument: mocks.setTags }, templateApi: { create: mocks.createTemplate }, versionApi: { rollback: mocks.rollback, deleteAll: mocks.deleteVersions, save: mocks.saveVersion },
  getRealtimeAccessToken: async () => 'test'
}))
vi.mock('../store', () => ({ useDocStore: () => ({ rememberUpdatedDoc: mocks.rememberUpdatedDoc, loadFolders: mocks.loadFolders, loadDocs: mocks.loadDocs }) }))
vi.mock('../utils/dialog', () => ({ confirmDialog: mocks.confirm, promptDialog: mocks.prompt }))
vi.mock('../utils/toast', () => ({ error: vi.fn(), success: vi.fn(), warning: vi.fn() }))
vi.mock('md-editor-v3', () => ({ MdEditor: { template: '<div />' }, MdPreview: { template: '<div />' } }))
vi.mock('../components/CrdtRichTextEditor.vue', () => ({ __esModule: true, default: {
  setup(_props, { expose }) { expose({ reconnect: mocks.reconnectCrdt, clearLocalState: mocks.clearCrdtLocal }); return () => null }
} }))
vi.mock('../components/RichTextEditor.vue', () => ({ __esModule: true, default: { template: '<div />' } }))
vi.mock('../utils/websocket', () => ({ CollabWebSocket: class {
  constructor(id) { this.id = id; this.handlers = {}; mocks.sockets.push(this) }
  on(event, handler) { this.handlers[event] = handler }
  connect() {} disconnect() {} sendDocumentUpdate() { return true }
} }))

const deferred = () => { let resolve, reject; const promise = new Promise((done, fail) => { resolve = done; reject = fail }); return { promise, resolve, reject } }
const document = (id = 1, mode = 'LEGACY', fields = {}) => ({ id, title: 'Original', content: 'Original body', contentFormat: 'MARKDOWN', collabMode: mode, revision: 1, ...fields })
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); await nextTick() }
let app, state, host
async function mount() {
  host = window.document.createElement('div'); window.document.body.append(host)
  app = createApp(DocEdit); app.mount(host); state = app._instance.setupState
  await flush()
  if (state.isCrdt && state.crdtAccess) await vi.waitFor(() => expect(state.crdtEditor).not.toBeNull())
}
beforeEach(() => {
  vi.clearAllMocks(); localStorage.clear(); mocks.sockets = []
  mocks.route = reactive({ params: { id: '1' } }); mocks.routeUpdate = null
  mocks.detail.mockResolvedValue({ data: document() })
  mocks.access.mockResolvedValue({ data: { writable: true, userId: 1 } })
  mocks.confirm.mockResolvedValue(false)
  mocks.loadFolders.mockResolvedValue(); mocks.loadDocs.mockResolvedValue()
  mocks.setTags.mockResolvedValue({}); mocks.listAttachments.mockResolvedValue({ data: [] })
})
afterEach(() => { app?.unmount(); host?.remove(); vi.useRealTimers() })

describe('document editor asynchronous state', () => {
  it('checks a closed CRDT session once and bounds automatic reconnection for that editor', async () => {
    mocks.detail.mockResolvedValue({ data: document(1, 'CRDT') }); await mount(); mocks.access.mockClear()
    const editor = state.crdtEditor
    const access = deferred(); mocks.access.mockReturnValueOnce(access.promise)
    state.handleCrdtStatus({ status: 'session-closed' }); state.handleCrdtStatus({ status: 'session-closed' })
    await flush()
    expect(mocks.access).toHaveBeenCalledOnce(); expect(editor.reconnect).not.toHaveBeenCalled()
    access.resolve({ data: { writable: true, userId: 1 } }); await flush()
    expect(editor.reconnect).toHaveBeenCalledOnce()
    state.handleCrdtStatus({ status: 'session-closed' }); await flush()
    expect(mocks.access).toHaveBeenCalledTimes(2); expect(editor.reconnect).toHaveBeenCalledOnce()
    expect(state.connected).toBe(false); expect(state.saveStatus).toBe('离线')
  })

  it.each([false, 'revoked'])('clears a closed CRDT branch before recovering %s access', async permission => {
    mocks.detail.mockResolvedValue({ data: document(1, 'CRDT') }); await mount(); mocks.access.mockClear()
    const editor = state.crdtEditor
    const generation = state.crdtEditorGeneration
    if (permission === 'revoked') mocks.access.mockRejectedValueOnce({ code: 403 })
    else mocks.access.mockResolvedValueOnce({ data: { writable: false, userId: 1 } })
    state.handleCrdtStatus({ status: 'session-closed' }); await flush()
    expect(editor.clearLocalState).toHaveBeenCalledOnce(); expect(editor.reconnect).not.toHaveBeenCalled()
    expect(state.crdtEditorGeneration).toBe(generation + 1)
    if (permission === 'revoked') expect(state.crdtAccess).toBeNull()
    else expect(state.crdtAccess.writable).toBe(false)
  })

  it('preserves a closed local branch after a temporary access failure and allows explicit resync', async () => {
    mocks.detail.mockResolvedValue({ data: document(1, 'CRDT') }); await mount(); mocks.access.mockClear()
    const editor = state.crdtEditor
    mocks.access.mockRejectedValueOnce(new Error('Network unavailable'))
    state.handleCrdtStatus({ status: 'session-closed' }); await flush()
    expect(mocks.access).toHaveBeenCalledOnce(); expect(editor.reconnect).not.toHaveBeenCalled()
    expect(editor.clearLocalState).not.toHaveBeenCalled(); expect(state.crdtAccess.writable).toBe(true)
    await state.resyncDocument()
    expect(editor.reconnect).toHaveBeenCalledOnce()
  })

  it('never reconnects a new document after an old session access check completes', async () => {
    mocks.detail.mockResolvedValue({ data: document(1, 'CRDT') }); await mount()
    const editor = state.crdtEditor
    const access = deferred(); mocks.access.mockReturnValueOnce(access.promise)
    state.handleCrdtStatus({ status: 'session-closed' }); await flush()
    mocks.detail.mockResolvedValue({ data: document(2, 'CRDT') }); mocks.route.params.id = '2'; await flush()
    const newEditor = state.crdtEditor
    access.resolve({ data: { writable: true, userId: 1 } }); await flush()
    expect(editor.reconnect).not.toHaveBeenCalled(); expect(newEditor.reconnect).not.toHaveBeenCalled()
  })

  it.each(['LEGACY', 'CRDT'])('preserves a newer title and draft while a %s save is pending', async mode => {
    mocks.detail.mockResolvedValue({ data: document(1, mode) }); await mount()
    state.docTitle = 'Saved title'; if (mode === 'LEGACY') state.content = 'Saved body'
    const save = deferred(); mocks.update.mockReturnValueOnce(save.promise)
    const saving = state.handleSave()
    state.docTitle = 'Newer title'; state.content = 'Newer body'; state.markLocalDraft()
    save.resolve({ data: document(1, mode, { title: 'Saved title', content: 'Saved body', revision: 2 }) })
    await saving
    expect(state.docTitle).toBe('Newer title')
    expect(state.content).toBe('Newer body')
    expect(state.acceptedTitle).toBe('Saved title')
    expect(state.hasUnsaved).toBe(true)
    if (mode === 'LEGACY') expect(JSON.parse(localStorage.getItem('docflow:draft:1')).content).toBe('Newer body')
  })

  it('does not apply an old save to a different document', async () => {
    await mount(); state.docTitle = 'Saving old document'
    const save = deferred(); mocks.update.mockReturnValueOnce(save.promise)
    const saving = state.handleSave()
    mocks.detail.mockResolvedValue({ data: document(2, 'LEGACY', { title: 'Second' }) })
    mocks.route.params.id = '2'; await flush()
    save.resolve({ data: document(1, 'LEGACY', { title: 'Saving old document', revision: 2 }) }); await saving
    expect(state.docTitle).toBe('Second'); expect(state.docData.id).toBe(2)
  })

  it('ignores a slower detail response after document params change', async () => {
    const old = deferred(); mocks.detail.mockReturnValueOnce(old.promise); await mount()
    mocks.detail.mockResolvedValueOnce({ data: document(2, 'LEGACY', { title: 'Second' }) })
    mocks.route.params.id = '2'; await flush()
    old.resolve({ data: document(1) }); await flush()
    expect(state.docData.id).toBe(2); expect(state.docTitle).toBe('Second')
  })

  it('asks before reusing the editor route with an unsaved draft', async () => {
    await mount(); state.content = 'Unsaved'
    expect(mocks.routeUpdate).toBeTypeOf('function')
    expect(await mocks.routeUpdate({ params: { id: '2' } }, mocks.route)).toBe(false)
    expect(mocks.confirm).toHaveBeenCalledOnce()
  })

  it('preserves newer typing when the websocket acknowledges an older update', async () => {
    await mount(); state.content = 'First edit'; state.handleContentChange()
    // The debounce has sent the first edit; a second edit remains local.
    vi.useFakeTimers(); state.connected = true; state.handleContentChange(); await vi.advanceTimersByTimeAsync(650)
    state.content = 'Second edit'; state.handleContentChange()
    mocks.sockets[0].handlers.documentUpdate({ data: { title: 'Original', content: 'First edit', revision: 2 } })
    expect(state.content).toBe('Second edit'); expect(state.acceptedContent).toBe('First edit')
    expect(state.hasUnsaved).toBe(true); vi.useRealTimers()
  })

  it('does not acknowledge an unsaved title in a body-only websocket update', async () => {
    await mount(); vi.useFakeTimers(); state.connected = true
    state.docTitle = 'Unsaved title'; state.content = 'First edit'; state.handleContentChange()
    await vi.advanceTimersByTimeAsync(650)
    mocks.sockets[0].handlers.documentUpdate({ data: { title: 'Original', content: 'First edit', revision: 2 } })
    expect(state.docTitle).toBe('Unsaved title'); expect(state.hasUnsaved).toBe(true)
  })

  it('ignores a stale access response after switching to another CRDT document', async () => {
    mocks.detail.mockResolvedValue({ data: document(1, 'CRDT') }); await mount()
    const access = deferred(); mocks.access.mockReturnValueOnce(access.promise)
    const refreshing = state.refreshCrdtAccess()
    mocks.detail.mockResolvedValue({ data: document(2, 'CRDT', { title: 'Second' }) })
    mocks.access.mockResolvedValue({ data: { writable: true, userId: 2 } })
    mocks.route.params.id = '2'; await flush()
    access.resolve({ data: { writable: false, userId: 1 } }); await refreshing
    expect(state.crdtAccess).toEqual({ writable: true, userId: 2 })
    expect(state.docTitle).toBe('Second')
  })

  it('ignores sidepanel data requested for the previous document', async () => {
    await mount(); const comments = deferred(); mocks.comments.mockReturnValueOnce(comments.promise)
    const loading = state.loadComments()
    mocks.detail.mockResolvedValue({ data: document(2) }); mocks.route.params.id = '2'; await flush()
    comments.resolve({ data: [{ id: 99, content: 'Old document comment' }] }); await loading
    expect(state.comments).toEqual([])
  })

  it('does not navigate away from a new document after an old access denial finishes refreshing the workspace', async () => {
    mocks.detail.mockResolvedValue({ data: document(1, 'CRDT') }); await mount()
    const workspace = deferred(); mocks.loadFolders.mockReturnValueOnce(workspace.promise)
    mocks.access.mockRejectedValueOnce({ code: 403 })
    const refreshing = state.refreshCrdtAccess(); await flush()
    mocks.detail.mockResolvedValue({ data: document(2, 'CRDT') }); mocks.route.params.id = '2'; await flush()
    workspace.resolve(); await refreshing
    expect(mocks.push).not.toHaveBeenCalled()
  })

  it('refreshes CRDT access only on reconnection, not edit acknowledgements', async () => {
    mocks.detail.mockResolvedValue({ data: document(1, 'CRDT') }); await mount(); mocks.access.mockClear()
    state.handleCrdtStatus({ status: 'connected', pending: 0 })
    state.handleCrdtStatus({ status: 'synced', pending: 0 })
    for (let i = 0; i < 10; i++) state.handleCrdtStatus({ status: 'connected', pending: i + 1 })
    await flush(); expect(mocks.access).not.toHaveBeenCalled()
    state.handleCrdtStatus({ status: 'disconnected' }); state.handleCrdtStatus({ status: 'connected' }); await flush()
    expect(mocks.access).toHaveBeenCalledOnce()
  })

  it('labels the checkpoint reason values returned by the server', async () => {
    await mount()
    expect(state.checkpointReason('MANUAL')).toBe('手动创建')
    expect(state.checkpointReason('AUTO')).toBe('系统自动保存')
    expect(state.checkpointReason('PRE_RESTORE')).toBe('恢复前备份')
  })

  it('keeps metadata requests bound to their original document and submitted tags', async () => {
    await mount(); state.metadata.category = 'A category'; state.metadata.tags = 'A one, A two'
    const request = deferred(); mocks.update.mockReturnValueOnce(request.promise)
    const saving = state.saveMetadata()
    mocks.detail.mockResolvedValue({ data: document(2, 'LEGACY', { category: 'B category' }) }); mocks.route.params.id = '2'; await flush()
    state.metadata.tags = 'B tag'
    request.resolve({ data: document(1, 'LEGACY', { category: 'A category' }) }); await saving
    expect(mocks.setTags).toHaveBeenCalledWith(1, ['A one', 'A two'])
    expect(state.docData.id).toBe(2); expect(state.metadata.category).toBe('B category')
  })

  it.each(['changePageFormat', 'savePageSettings'])('ignores a %s response after changing documents', async method => {
    await mount(); const request = deferred(); mocks.update.mockReturnValueOnce(request.promise)
    const saving = state[method](method === 'changePageFormat' ? 'LETTER' : { pageFormat: 'LETTER', marginTop: 30 })
    mocks.detail.mockResolvedValue({ data: document(2, 'LEGACY', { pageFormat: 'A4', marginTop: 12 }) }); mocks.route.params.id = '2'; await flush()
    request.resolve({ data: document(1, 'LEGACY', { pageFormat: 'LETTER', marginTop: 30 }) }); await saving
    expect(state.docData.id).toBe(2); expect(state.docData.pageFormat).toBe('A4'); expect(state.pageSettings.marginTop).toBe(12)
  })

  it.each(['changePageFormat', 'savePageSettings'])('does not revert the new document when an old %s request fails', async method => {
    await mount(); const request = deferred(); mocks.update.mockReturnValueOnce(request.promise)
    const saving = state[method](method === 'changePageFormat' ? 'LETTER' : { pageFormat: 'LETTER', marginTop: 30 })
    mocks.detail.mockResolvedValue({ data: document(2, 'LEGACY', { pageFormat: 'LETTER', marginTop: 12 }) }); mocks.route.params.id = '2'; await flush()
    request.reject(new Error('Old request failed')); await saving
    expect(state.pageSettings.pageFormat).toBe('LETTER'); expect(state.pageSettings.marginTop).toBe(12)
  })

  it('ignores rollback results from a previous document', async () => {
    await mount(); mocks.confirm.mockResolvedValueOnce(true); const request = deferred(); mocks.rollback.mockReturnValueOnce(request.promise)
    const restoring = state.rollbackVersion({ versionNum: 3 }); await flush()
    mocks.detail.mockResolvedValue({ data: document(2, 'LEGACY', { title: 'Second' }) }); mocks.route.params.id = '2'; await flush()
    request.resolve({ data: document(1, 'LEGACY', { title: 'Restored' }) }); await restoring
    expect(state.docData.id).toBe(2); expect(state.docTitle).toBe('Second')
  })

  it('cancels a rollback confirmation belonging to a previous document', async () => {
    await mount(); const confirmation = deferred(); mocks.confirm.mockReturnValueOnce(confirmation.promise)
    const restoring = state.rollbackVersion({ versionNum: 3 })
    mocks.detail.mockResolvedValue({ data: document(2) }); mocks.route.params.id = '2'; await flush()
    confirmation.resolve(true); await restoring
    expect(mocks.rollback).not.toHaveBeenCalled()
  })

  it('cancels a template prompt after switching documents', async () => {
    await mount(); const prompt = deferred(); mocks.prompt.mockReturnValueOnce(prompt.promise)
    const saving = state.saveAsTemplate()
    mocks.detail.mockResolvedValue({ data: document(2) }); mocks.route.params.id = '2'; await flush()
    prompt.resolve('Template A'); await saving
    expect(mocks.createTemplate).not.toHaveBeenCalled()
  })

  it('keeps every file in an attachment batch on the captured document', async () => {
    await mount(); const first = deferred(); mocks.uploadAttachment.mockReturnValueOnce(first.promise).mockResolvedValueOnce({})
    const firstFile = new File(['a'], 'a.txt'), secondFile = new File(['b'], 'b.txt')
    const uploading = state.uploadAttachments({ target: { files: [firstFile, secondFile], value: 'selected' } })
    mocks.detail.mockResolvedValue({ data: document(2) }); mocks.route.params.id = '2'; await flush()
    first.resolve({}); await uploading
    expect(mocks.uploadAttachment.mock.calls.map(args => args[0])).toEqual([1, 1])
    expect(mocks.listAttachments).not.toHaveBeenCalled()
  })

  it('does not remove a new document attachment when an old removal completes', async () => {
    await mount(); mocks.confirm.mockResolvedValueOnce(true); const request = deferred(); mocks.removeAttachment.mockReturnValueOnce(request.promise)
    const removing = state.removeAttachment({ id: 3, originalName: 'A.txt' }); await flush()
    mocks.detail.mockResolvedValue({ data: document(2) }); mocks.route.params.id = '2'; await flush()
    state.attachments = [{ id: 3, originalName: 'B.txt' }]
    request.resolve({}); await removing
    expect(state.attachments).toEqual([{ id: 3, originalName: 'B.txt' }])
  })

  it('rechecks access once when authentication is rejected and clears revoked access', async () => {
    mocks.detail.mockResolvedValue({ data: document(1, 'CRDT') }); await mount(); mocks.access.mockClear()
    mocks.access.mockRejectedValueOnce({ code: 403 })
    state.handleCrdtStatus({ status: 'connected' })
    for (let i = 0; i < 5; i++) state.handleCrdtStatus({ status: 'unauthorized' })
    await flush()
    expect(mocks.access).toHaveBeenCalledOnce(); expect(state.crdtAccess).toBeNull()
    expect(localStorage.getItem('docflow:crdt-writable:1')).toBe('0'); expect(mocks.push).toHaveBeenCalledWith('/')
  })

  it.each([
    ['createShare', 'createShare', [], state => {}, state => { expect(state.shareLink).toBe('') }],
    ['submitCommentDialog', 'createComment', [], state => { state.commentDialog.content = 'A comment' }, state => { expect(state.comments).toEqual([{ id: 3, status: 'OPEN' }]) }],
    ['confirmRemoveComment', 'removeComment', [], state => { state.deleteCommentDialog.item = { id: 3 } }, state => { expect(state.comments).toEqual([{ id: 3, status: 'OPEN' }]) }],
    ['resolveSelectedComments', 'resolveComments', [], state => { state.selectedCommentIds = [3] }, state => { expect(state.comments[0].status).toBe('OPEN') }],
    ['addCollaborator', 'addCollaborator', [], state => { state.collaboratorForm.username = 'A user' }, state => { expect(state.collaboratorForm.username).toBe('B user') }],
    ['removeCollaborator', 'removeCollaborator', [{ userId: 3, username: 'A user' }], state => {}, state => { expect(state.collaborators).toEqual([{ userId: 3 }]) }],
    ['createCheckpoint', 'createCheckpoint', [], state => {}, state => { expect(state.checkpointInfo.checkpointSequence).not.toBe(999) }],
    ['handleDeleteVersions', 'deleteVersions', [], state => {}, state => { expect(state.versions).toEqual([{ id: 3 }]) }]
  ])('keeps %s completion from changing a different document', async (method, mock, args, prepare, verify) => {
    await mount(); mocks.confirm.mockResolvedValueOnce(true); prepare(state)
    const request = deferred(); mocks[mock].mockReturnValueOnce(request.promise)
    const operation = state[method](...args); await flush()
    mocks.detail.mockResolvedValue({ data: document(2) }); mocks.route.params.id = '2'; await flush()
    state.comments = [{ id: 3, status: 'OPEN' }]; state.collaborators = [{ userId: 3 }]; state.versions = [{ id: 3 }]; state.collaboratorForm.username = 'B user'
    request.resolve({ data: { id: 9, token: 'A token', checkpointSequence: 999 } }); await operation
    verify(state); expect(state.docData.id).toBe(2)
  })

  it('does not reload the next document after an old checkpoint restore completes', async () => {
    await mount(); mocks.confirm.mockResolvedValueOnce(true); const request = deferred(); mocks.restoreCheckpoint.mockReturnValueOnce(request.promise)
    const restoring = state.restoreCheckpoint({ id: 3 }); await flush()
    mocks.detail.mockResolvedValue({ data: document(2) }); mocks.route.params.id = '2'; await flush(); vi.useFakeTimers()
    request.resolve({}); await restoring
    expect(vi.getTimerCount()).toBe(0)
  })

  it('cancels a version save prompt after switching documents', async () => {
    await mount(); const prompt = deferred(); mocks.prompt.mockReturnValueOnce(prompt.promise)
    const saving = state.handleSaveVersion(); await flush()
    mocks.detail.mockResolvedValue({ data: document(2) }); mocks.route.params.id = '2'; await flush()
    prompt.resolve('A version'); mocks.prompt.mockResolvedValueOnce('A description'); await saving
    expect(mocks.saveVersion).not.toHaveBeenCalled()
  })
})
