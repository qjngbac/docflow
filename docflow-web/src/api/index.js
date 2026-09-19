import axios from 'axios'
import router from '../router'
import { httpErrorMessage } from '../utils/friendlyMessage'
import { clearStoredAuth, getAccessToken, getRefreshToken, updateStoredTokens } from '../utils/auth'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  withCredentials: true,
  withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN',
  xsrfHeaderName: 'X-XSRF-TOKEN',
  headers: { 'Content-Type': 'application/json' }
})
const refreshClient = axios.create({
  baseURL: '/api/v1', timeout: 15000, withCredentials: true, withXSRFToken: true,
  xsrfCookieName: 'XSRF-TOKEN', xsrfHeaderName: 'X-XSRF-TOKEN', headers: { 'Content-Type': 'application/json' }
})
// 多个请求同时遇到 401 时共用一次刷新，避免刷新令牌被并发轮换后互相失效。
let refreshPromise = null

api.interceptors.request.use(config => {
  const token = getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  if (config.data instanceof FormData) delete config.headers['Content-Type']
  return config
})

function clearAuth() {
  clearStoredAuth()
  window.dispatchEvent(new CustomEvent('docflow:auth-cleared'))
}

function canRefresh(config) {
  const url = config?.url || ''
  return !config?.__authRetry && !/^\/auth\/(login|register|refresh|logout|password-reset)/.test(url)
}

async function refreshAccessToken() {
  const refreshToken = getRefreshToken()
  if (!refreshPromise) {
    refreshPromise = refreshClient.post('/auth/refresh', refreshToken ? { refreshToken } : {}).then(response => {
      const payload = response.data
      if (!payload || payload.code !== 200 || (!payload.data?.accessToken && !payload.data?.cookieAuth)) {
        // refreshClient 没有响应拦截器，业务错误会以 HTTP 200 + 非 200 业务码返回，这里把业务码带上供上层判断。
        const rejection = new Error('Refresh rejected')
        rejection.authCode = payload?.code
        throw rejection
      }
      updateStoredTokens(payload.data)
      return payload.data.accessToken || null
    }).finally(() => { refreshPromise = null })
  }
  return refreshPromise
}

// 只有服务端明确判定会话失效时才结束登录态；限流(429)、5xx 或网络抖动不应把用户踢下线。
function isSessionRejected(exception) {
  if (exception?.response?.status === 401) return true
  return exception?.authCode === 401
}

/** 导出接口用 responseType:'blob'，错误响应体同样是 Blob，直接取 .message 只会得到 undefined。 */
async function readBlobMessage(data) {
  if (typeof Blob === 'undefined' || !(data instanceof Blob)) return ''
  try {
    const parsed = JSON.parse(await data.text())
    return typeof parsed?.message === 'string' ? parsed.message : ''
  } catch {
    return ''
  }
}

async function retryAfterRefresh(config) {
  if (!canRefresh(config)) throw new Error('Refresh is not available')
  config.__authRetry = true
  let accessToken
  try {
    accessToken = await refreshAccessToken()
  } catch (exception) {
    if (isSessionRejected(exception)) expireLogin()
    throw exception
  }
  config.headers = { ...(config.headers || {}) }
  if (accessToken) config.headers.Authorization = `Bearer ${accessToken}`
  else delete config.headers.Authorization
  return api(config)
}

// 所有接口都在此处统一处理业务错误和登录过期，页面组件只接收规范化结果。
function expireLogin() {
  clearAuth()
  if (router.currentRoute.value.name !== 'Login') router.push({ path: '/login', query: { reason: 'expired' } })
}

function rejectBusinessError(payload) {
  if (!payload) return Promise.reject({ code: 500, message: '请求未成功，请稍后重试' })
  return Promise.reject({ ...payload, message: httpErrorMessage(payload.code, payload.message) })
}

api.interceptors.response.use(
  async response => {
    const payload = response.data
    if (typeof Blob !== 'undefined' && payload instanceof Blob) {
      // 导出类接口用 responseType:'blob'。后端失败时会返回「HTTP 200 + 业务错误码 JSON」（项目统一约定），
      // 若直接把它交给下载逻辑，用户拿到的会是内容为 JSON 的假文档，所以这里必须先识别出来。
      const contentType = response.headers?.['content-type'] || payload.type || ''
      if (contentType.includes('application/json')) {
        try {
          const parsed = JSON.parse(await payload.text())
          if (parsed && typeof parsed.code === 'number' && parsed.code !== 200) {
            return rejectBusinessError(parsed)
          }
        } catch { /* 不是合法 JSON，按普通文件返回 */ }
      }
      return payload
    }
    if (payload && typeof payload.code === 'number' && payload.code !== 200) {
      if (payload.code === 401 && canRefresh(response.config)) {
        return retryAfterRefresh(response.config)
      }
      return rejectBusinessError(payload)
    }
    return payload
  },
  async error => {
    const status = error.response?.status
    if (status === 401 && canRefresh(error.config)) {
      return retryAfterRefresh(error.config)
    } else if (status === 401) {
      expireLogin()
    }
    const rawMessage = error.response?.data?.message
    const message = typeof rawMessage === 'string' && rawMessage
      ? rawMessage
      : await readBlobMessage(error.response?.data)
    return Promise.reject({
      code: status || 0,
      message: httpErrorMessage(status, message || error.message),
      cause: error
    })
  }
)

export const userApi = {
  register: data => api.post('/auth/register', data),
  login: data => api.post('/auth/login', data),
  loginSecurity: username => api.get('/auth/login-security', { params: { username: username || '' } }),
  captcha: username => api.get('/auth/captcha', { params: { username: username || '' } }),
  refresh: refreshToken => refreshClient.post('/auth/refresh', { refreshToken }),
  logout: refreshToken => api.post('/auth/logout', refreshToken ? { refreshToken } : {}),
  heartbeat: () => api.post('/auth/heartbeat'),
  session: () => api.get('/auth/session'),
  realtimeToken: () => api.post('/auth/realtime-token'),
  getMe: () => api.get('/user/me'),
  updateProfile: data => api.put('/user/me', data),
  changePassword: data => api.put('/user/me/password', data),
  requestEmailChange: email => api.post('/user/me/email/request', { email }),
  confirmEmailChange: data => api.put('/user/me/email', data),
  sessions: () => api.get('/user/me/sessions'),
  revokeSession: id => api.delete(`/user/me/sessions/${id}`),
  revokeOtherSessions: () => api.delete('/user/me/sessions/others'),
  deleteAvatar: () => api.delete('/user/me/avatar'),
  dismissWelcome: () => api.put('/user/me/welcome-dismissed'),
  requestPasswordReset: email => api.post('/auth/password-reset/request', { email }),
  confirmPasswordReset: data => api.post('/auth/password-reset/confirm', data),
  uploadAvatar(file) {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/user/me/avatar', formData, { headers: {} })
  }
}

export async function getRealtimeAccessToken() {
  // Cookie 登录下普通访问令牌不可被脚本读取，因此另取用途受限的短效协作令牌。
  const stored = getAccessToken()
  if (stored) return stored
  const response = await userApi.realtimeToken()
  return response.data?.token || ''
}

export const docApi = {
  list: params => api.get('/docs', { params }),
  detail: id => api.get(`/docs/${id}`),
  create: data => api.post('/docs', data),
  importWord(file, folderId, category) {
    const formData = new FormData()
    formData.append('file', file)
    formData.append('folderId', folderId || 0)
    if (category) formData.append('category', category)
    return api.post('/docs', formData, { headers: {}, timeout: 60000 })
  },
  update: (id, data) => api.put(`/docs/${id}`, data),
  delete: id => api.delete(`/docs/${id}`),
  restore: id => api.post(`/docs/${id}/restore`),
  purge: id => api.delete(`/docs/${id}/permanent`),
  search: q => api.get('/docs/search', { params: { q } }),
  trashSettings: () => api.get('/docs/trash/settings'),
  togglePin: (id, pinned) => api.put(`/docs/${id}`, { pinned }),
  toggleFavorite: (id, favorite) => api.put(`/docs/${id}`, { favorite }),
  moveToFolder: (id, folderId) => api.put(`/docs/${id}`, { folderId }),
  copy: (id, folderId) => api.post(`/docs/${id}/copy`, null, {
    params: folderId == null ? undefined : { folderId }
  }),
  batchMove: (ids, folderId) => api.post('/docs/batch/move', { ids, folderId }),
  batchTrash: ids => api.post('/docs/batch/trash', { ids }),
  batchRestore: ids => api.post('/docs/batch/restore', { ids }),
  batchPurge: ids => api.post('/docs/batch/permanent', { ids }),
  exportDocx: (id, content) => api.post(`/docs/${id}/export/docx`, { content }, {
    responseType: 'blob',
    timeout: 60000
  }),
  exportPdf: (id, content) => api.post(`/docs/${id}/export/pdf`, { content }, {
    responseType: 'blob',
    timeout: 60000
  })
}

export const globalSearchApi = {
  search: q => api.get('/search', { params: { q } })
}

export const crdtApi = {
  access: docId => api.get(`/docs/${docId}/crdt/access`),
  checkpointStatus: docId => api.get(`/docs/${docId}/crdt/checkpoint`),
  createCheckpoint: docId => api.post(`/docs/${docId}/crdt/checkpoint`),
  checkpointHistory: docId => api.get(`/docs/${docId}/crdt/checkpoint/history`),
  restoreCheckpoint: (docId, checkpointId) => api.post(`/docs/${docId}/crdt/checkpoint/history/${checkpointId}/restore`)
}

export const folderApi = {
  list: () => api.get('/folders'),
  create: data => api.post('/folders', data),
  update: (id, data) => api.put(`/folders/${id}`, data),
  delete: id => api.delete(`/folders/${id}`)
}

export const permissionApi = {
  list: docId => api.get(`/docs/${docId}/permissions`),
  add: (docId, data) => api.post(`/docs/${docId}/permissions`, data),
  update: (docId, userId, data) => api.put(`/docs/${docId}/permissions/${userId}`, data),
  remove: (docId, userId) => api.delete(`/docs/${docId}/permissions/${userId}`)
}

export const shareApi = {
  create: (docId, data) => api.post(`/docs/${docId}/share`, data),
  access: (token, password) => api.post(`/share/${token}/access`, { password: password || null })
}

export const versionApi = {
  list: docId => api.get(`/docs/${docId}/versions`),
  detail: (docId, versionNum) => api.get(`/docs/${docId}/versions/${versionNum}`),
  save: (docId, data) => api.post(`/docs/${docId}/versions`, data),
  rollback: (docId, versionNum) => api.post(`/docs/${docId}/versions/${versionNum}/rollback`),
  deleteAll: docId => api.delete(`/docs/${docId}/versions`)
}

export const commentApi = {
  list: docId => api.get(`/docs/${docId}/comments`),
  create: (docId, data) => api.post(`/docs/${docId}/comments`, data),
  setResolved: (docId, commentId, resolved) => api.put(`/docs/${docId}/comments/${commentId}/status`, { resolved }),
  resolveBatch: (docId, ids, resolved) => api.put(`/docs/${docId}/comments/batch/status`, { ids, resolved }),
  remove: (docId, commentId) => api.delete(`/docs/${docId}/comments/${commentId}`)
}

export const notificationApi = {
  list: (unreadOnly, type) => api.get('/notifications', { params: { unreadOnly: Boolean(unreadOnly), type: type || undefined } }),
  unreadCount: () => api.get('/notifications/unread-count'),
  markRead: id => api.put(`/notifications/${id}/read`),
  markAllRead: () => api.put('/notifications/read-all')
}

export const fileApi = {
  upload(file) {
    const formData = new FormData()
    formData.append('file', file)
    return api.post('/files/upload', formData, {
      headers: {}
    })
  },
  uploadImage(file) {
    return this.upload(file)
  }
}

export const attachmentApi = {
  list: docId => api.get(`/docs/${docId}/attachments`),
  async upload(docId, file) {
    const chunkSize = 5 * 1024 * 1024
    if (file.size > chunkSize) return this.uploadInChunks(docId, file, chunkSize)
    const formData = new FormData(); formData.append('file', file)
    return api.post(`/docs/${docId}/attachments`, formData, { headers: {} })
  },
  async uploadInChunks(docId, file, chunkSize = 5 * 1024 * 1024) {
    const totalChunks = Math.ceil(file.size / chunkSize)
    const digest = await crypto.subtle.digest('SHA-256', await file.arrayBuffer())
    const sha256 = [...new Uint8Array(digest)].map(value => value.toString(16).padStart(2, '0')).join('')
    const started = await api.post(`/docs/${docId}/attachments/uploads`, { fileName: file.name, mimeType: file.type || null, size: file.size, totalChunks, sha256 })
    const uploadId = started.data.id
    let next = 0
    const worker = async () => {
      while (next < totalChunks) {
        const index = next++
        const formData = new FormData()
        formData.append('file', file.slice(index * chunkSize, Math.min(file.size, (index + 1) * chunkSize)), `${file.name}.part${index}`)
        await api.put(`/docs/${docId}/attachments/uploads/${uploadId}/chunks/${index}`, formData, { headers: {}, timeout: 60000 })
      }
    }
    await Promise.all(Array.from({ length: Math.min(3, totalChunks) }, worker))
    return api.post(`/docs/${docId}/attachments/uploads/${uploadId}/complete`, null, { timeout: 60000 })
  },
  remove: (docId, attachmentId) => api.delete(`/docs/${docId}/attachments/${attachmentId}`)
}

export const referenceApi = {
  list: docId => api.get(`/docs/${docId}/references`),
  create: (docId, data) => api.post(`/docs/${docId}/references`, data),
  importDoi: (docId, data) => api.post(`/docs/${docId}/references/doi`, data),
  remove: (docId, referenceId) => api.delete(`/docs/${docId}/references/${referenceId}`)
}

export const tagApi = {
  list: () => api.get('/tags'),
  forDocument: docId => api.get(`/docs/${docId}/tags`),
  setForDocument: (docId, names) => api.put(`/docs/${docId}/tags`, { names })
}

export const templateApi = {
  list: () => api.get('/templates'),
  create: data => api.post('/templates', data),
  remove: id => api.delete(`/templates/${id}`)
}

export const feedbackApi = {
  create({ type, description, docId, images = [] }) {
    const formData = new FormData()
    formData.append('type', type || 'BUG')
    formData.append('description', description || '')
    if (docId) formData.append('docId', docId)
    images.forEach(file => formData.append('images', file))
    return api.post('/feedback', formData, { headers: {}, timeout: 60000 })
  },
  mine: () => api.get('/feedback/mine')
}

export const adminApi = {
  overview: () => api.get('/admin/overview'),
  users: params => api.get('/admin/users', { params }),
  setUserStatus: (id, data) => api.put(`/admin/users/${id}/status`, data),
  setUserRole: (id, role) => api.put(`/admin/users/${id}/role`, { role }),
  revokeSessions: id => api.post(`/admin/users/${id}/revoke-sessions`),
  sendNotification: data => api.post('/admin/notifications', data),
  feedback: params => api.get('/admin/feedback', { params }),
  handleFeedback: (id, data) => api.put(`/admin/feedback/${id}`, data),
  logs: action => api.get('/admin/logs', { params: action ? { action } : undefined }),
  templates: () => api.get('/admin/templates'),
  createTemplate: data => api.post('/admin/templates', data),
  updateTemplate: (id, data) => api.put(`/admin/templates/${id}`, data),
  removeTemplate: id => api.delete(`/admin/templates/${id}`),
  systemStatus: () => api.get('/admin/system-status')
}

export default api
