import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import { docApi, folderApi } from '../api'
import { clearStoredAuth, getAccessToken, getStoredUser, hasAuthSession, saveAuth, updateStoredUser } from '../utils/auth'

function buildFolderTree(folders) {
  const byId = new Map(folders.map(folder => [folder.id, { ...folder, children: [] }]))
  const roots = []
  byId.forEach(folder => {
    const parent = folder.parentId ? byId.get(folder.parentId) : null
    if (parent) parent.children.push(folder)
    else roots.push(folder)
  })
  const sort = nodes => {
    nodes.sort((a, b) => a.name.localeCompare(b.name, 'zh-CN'))
    nodes.forEach(node => sort(node.children))
  }
  sort(roots)
  return roots
}

export const useUserStore = defineStore('user', () => {
  const token = ref(getAccessToken())
  const authenticated = ref(hasAuthSession())
  const user = ref(getStoredUser())
  const isLoggedIn = computed(() => authenticated.value)

  function setAuth(authData) {
    token.value = authData.accessToken || authData.token || ''
    authenticated.value = Boolean(authData.authenticated || authData.cookieAuth || token.value)
    user.value = authData.user || null
    saveAuth(authData, Boolean(authData.rememberMe))
  }

  function updateUser(userValue) {
    user.value = userValue
    updateStoredUser(userValue)
  }

  function logout() {
    token.value = ''
    authenticated.value = false
    user.value = null
    clearStoredAuth()
  }

  return { token, user, isLoggedIn, setAuth, updateUser, logout }
})

export const useDocStore = defineStore('doc', () => {
  const docs = ref([])
  const currentDoc = ref(null)
  const folders = ref([])
  const folderTree = ref([])
  const currentFolderId = ref(0)
  const viewMode = ref('documents')
  const loading = ref(false)
  const trashRetentionDays = ref(30)

  async function loadDocs(folderId = 0) {
    loading.value = true
    try {
      viewMode.value = 'documents'
      currentFolderId.value = Number(folderId || 0)
      const params = currentFolderId.value ? { folderId: currentFolderId.value } : undefined
      const response = await docApi.list(params)
      docs.value = (response.data || []).filter(doc => !doc.isDeleted)
    } finally {
      loading.value = false
    }
  }

  async function loadScope(scope) {
    loading.value = true
    try {
      viewMode.value = scope
      currentFolderId.value = 0
      const response = await docApi.list({ scope })
      docs.value = response.data || []
    } finally { loading.value = false }
  }

  async function loadTrash() {
    loading.value = true
    try {
      viewMode.value = 'trash'
      currentFolderId.value = 0
      const [documentsResponse, settingsResponse] = await Promise.all([
        docApi.list({ includeDeleted: true }),
        docApi.trashSettings()
      ])
      docs.value = (documentsResponse.data || []).filter(doc => Boolean(doc.isDeleted))
      trashRetentionDays.value = Number(settingsResponse.data?.retentionDays || 30)
    } finally {
      loading.value = false
    }
  }

  async function loadFolders() {
    const response = await folderApi.list()
    folders.value = response.data || []
    folderTree.value = buildFolderTree(folders.value)
  }

  async function createFolder(name, parentId = 0) {
    const response = await folderApi.create({ name, parentId })
    await loadFolders()
    return response.data
  }

  async function updateFolder(folder, changes) {
    const response = await folderApi.update(folder.id, {
      name: changes.name ?? folder.name,
      parentId: changes.parentId ?? folder.parentId ?? 0
    })
    await loadFolders()
    return response.data
  }

  async function deleteFolder(id) {
    await folderApi.delete(id)
    if (currentFolderId.value === id) await loadDocs()
    await loadFolders()
  }

  async function createDoc(title = '无标题文档', folderId = currentFolderId.value) {
    const response = await docApi.create({ title, folderId: Number(folderId || 0) })
    rememberCreatedDoc(response.data)
    return response.data
  }

  function rememberCreatedDoc(doc) {
    if (!doc?.id || viewMode.value !== 'documents' || Boolean(doc.isDeleted)) return
    const docFolderId = Number(doc.folderId || 0)
    if (docFolderId !== Number(currentFolderId.value || 0)) return
    docs.value = [doc, ...docs.value.filter(item => item.id !== doc.id)]
  }

  function rememberUpdatedDoc(doc) {
    if (!doc?.id) return
    const index = docs.value.findIndex(item => item.id === doc.id)
    if (index >= 0) docs.value[index] = { ...docs.value[index], ...doc }
    if (currentDoc.value?.id === doc.id) {
      currentDoc.value = { ...currentDoc.value, ...doc }
    }
  }

  async function deleteDoc(id) {
    await docApi.delete(id)
    docs.value = docs.value.filter(doc => doc.id !== id)
    if (currentDoc.value?.id === id) currentDoc.value = null
  }

  async function restoreDoc(id) {
    await docApi.restore(id)
    docs.value = docs.value.filter(doc => doc.id !== id)
  }

  async function purgeDoc(id) {
    await docApi.purge(id)
    docs.value = docs.value.filter(doc => doc.id !== id)
  }

  async function togglePin(doc) {
    const response = await docApi.togglePin(doc.id, !Boolean(doc.isPinned))
    Object.assign(doc, response.data)
  }

  async function toggleFavorite(doc) {
    const response = await docApi.toggleFavorite(doc.id, !Boolean(doc.isFavorite))
    Object.assign(doc, response.data)
    if (viewMode.value === 'favorites' && !doc.isFavorite) docs.value = docs.value.filter(item => item.id !== doc.id)
  }

  async function batch(action, ids, folderId) {
    if (action === 'move') await docApi.batchMove(ids, folderId)
    if (action === 'trash') await docApi.batchTrash(ids)
    if (action === 'restore') await docApi.batchRestore(ids)
    if (action === 'purge') await docApi.batchPurge(ids)
    docs.value = docs.value.filter(doc => !ids.includes(doc.id))
  }

  async function moveDoc(doc, folderId) {
    const response = await docApi.moveToFolder(doc.id, Number(folderId || 0))
    Object.assign(doc, response.data)
    if (currentFolderId.value && currentFolderId.value !== Number(folderId)) {
      docs.value = docs.value.filter(item => item.id !== doc.id)
    }
  }

  async function copyDoc(doc, folderId = null) {
    const response = await docApi.copy(doc.id, folderId)
    if (!currentFolderId.value || response.data.folderId === currentFolderId.value) {
      docs.value.unshift(response.data)
    }
    return response.data
  }

  async function searchDocs(keyword) {
    loading.value = true
    try {
      viewMode.value = 'search'
      const response = await docApi.search(keyword)
      docs.value = response.data || []
    } finally {
      loading.value = false
    }
  }

  return {
    docs, currentDoc, folders, folderTree, currentFolderId, viewMode, loading, trashRetentionDays,
    loadDocs, loadScope, loadTrash, loadFolders, createFolder, updateFolder, deleteFolder,
    createDoc, rememberCreatedDoc, rememberUpdatedDoc, deleteDoc, restoreDoc, purgeDoc, togglePin, toggleFavorite, batch, moveDoc, copyDoc, searchDocs
  }
})
