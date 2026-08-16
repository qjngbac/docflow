const AUTH_KEYS = ['token', 'refreshToken', 'user', 'authMeta', 'lastActivityAt', 'authenticated', 'cookieAuth']

function availableStorages() {
  return [sessionStorage, localStorage]
}

function storageWith(key) {
  return availableStorages().find(storage => storage.getItem(key)) || null
}

export function getAccessToken() {
  return storageWith('token')?.getItem('token') || ''
}

export function getRefreshToken() {
  return storageWith('refreshToken')?.getItem('refreshToken') || ''
}

export function hasAuthSession() {
  return Boolean(getAccessToken() || storageWith('authenticated')?.getItem('authenticated') === 'true')
}

export function isCookieAuth() {
  return storageWith('cookieAuth')?.getItem('cookieAuth') === 'true'
}

export function getStoredUser() {
  try {
    return JSON.parse(storageWith('user')?.getItem('user') || 'null')
  } catch {
    return null
  }
}

export function getAuthMeta() {
  try {
    return JSON.parse(storageWith('authMeta')?.getItem('authMeta') || '{}')
  } catch {
    return {}
  }
}

export function getLastActivityAt() {
  return Number(storageWith('lastActivityAt')?.getItem('lastActivityAt')) || Date.now()
}

export function setLastActivityAt(value = Date.now()) {
  const storage = storageWith('refreshToken') || storageWith('token') || storageWith('authenticated')
  if (storage) storage.setItem('lastActivityAt', String(value))
}

export function saveAuth(authData, rememberMe = Boolean(authData?.rememberMe)) {
  clearStoredAuth()
  const storage = rememberMe ? localStorage : sessionStorage
  const token = authData?.accessToken || authData?.token || ''
  if (token && !authData?.cookieAuth) storage.setItem('token', token)
  if (authData?.refreshToken && !authData?.cookieAuth) storage.setItem('refreshToken', authData.refreshToken)
  if (authData?.user) storage.setItem('user', JSON.stringify(authData.user))
  storage.setItem('authenticated', String(Boolean(authData?.authenticated || token || authData?.cookieAuth)))
  storage.setItem('cookieAuth', String(Boolean(authData?.cookieAuth)))
  storage.setItem('authMeta', JSON.stringify({
    sessionId: authData?.sessionId,
    sessionExpiresAt: authData?.sessionExpiresAt,
    idleTimeoutSeconds: authData?.idleTimeoutSeconds || 1800,
    rememberMe
  }))
  storage.setItem('lastActivityAt', String(Date.now()))
}

export function updateStoredTokens(authData) {
  const storage = storageWith('refreshToken') || storageWith('token') || storageWith('authenticated') || sessionStorage
  if (authData?.accessToken || authData?.token) storage.setItem('token', authData.accessToken || authData.token)
  if (authData?.refreshToken) storage.setItem('refreshToken', authData.refreshToken)
  if (authData?.authenticated || authData?.cookieAuth) storage.setItem('authenticated', 'true')
  if (authData?.cookieAuth != null) storage.setItem('cookieAuth', String(Boolean(authData.cookieAuth)))
  const previous = getAuthMeta()
  storage.setItem('authMeta', JSON.stringify({
    ...previous,
    sessionId: authData?.sessionId ?? previous.sessionId,
    sessionExpiresAt: authData?.sessionExpiresAt ?? previous.sessionExpiresAt,
    idleTimeoutSeconds: authData?.idleTimeoutSeconds ?? previous.idleTimeoutSeconds,
    rememberMe: authData?.rememberMe ?? previous.rememberMe
  }))
}

export function updateStoredUser(user) {
  const storage = storageWith('user') || storageWith('token') || storageWith('authenticated')
  if (storage) storage.setItem('user', JSON.stringify(user))
}

export function clearStoredAuth() {
  for (const storage of availableStorages()) {
    for (const key of AUTH_KEYS) storage.removeItem(key)
  }
}
