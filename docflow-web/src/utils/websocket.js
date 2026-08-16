export class CollabWebSocket {
  constructor(docId, token) {
    this.docId = docId
    this.token = token
    this.ws = null
    this.handlers = new Map()
    this.reconnectTimer = null
    this.reconnectAttempts = 0
    this.maxReconnectAttempts = 5
    this.manuallyClosed = false
  }

  connect() {
    clearTimeout(this.reconnectTimer)
    this.manuallyClosed = false
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const url = `${protocol}//${window.location.host}/ws/doc/${this.docId}?token=${encodeURIComponent(this.token)}`
    this.ws = new WebSocket(url)

    this.ws.onopen = () => {
      this.reconnectAttempts = 0
      this.emit('connected')
    }
    this.ws.onmessage = event => {
      try {
        this.handleMessage(JSON.parse(event.data))
      } catch (error) {
        this.emit('error', { message: '协作消息格式错误', cause: error })
      }
    }
    this.ws.onclose = event => {
      this.emit('disconnected', { code: event.code, reason: event.reason })
      if (!this.manuallyClosed) this.scheduleReconnect()
    }
    this.ws.onerror = error => this.emit('error', error)
  }

  scheduleReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      this.emit('offline')
      return
    }
    const delay = Math.min(1000 * (2 ** this.reconnectAttempts), 30000)
    this.reconnectTimer = setTimeout(() => {
      this.reconnectAttempts += 1
      this.connect()
    }, delay)
  }

  handleMessage(message) {
    const events = {
      DOCUMENT_UPDATE: 'documentUpdate',
      CURSOR_MOVE: 'cursorMove',
      SELECTION_CHANGE: 'selectionChange',
      USER_JOIN: 'userJoin',
      USER_LEAVE: 'userLeave',
      ONLINE_USERS: 'onlineUsers'
    }
    if (message.type === 'ERROR') {
      this.emit('collaborationError', message)
      if (message.data?.code === 409) this.emit('revisionConflict', message)
      return
    }
    const event = events[message.type]
    if (event) this.emit(event, message)
  }

  sendDocumentUpdate(content, revision, title) {
    return this.send({
      type: 'DOCUMENT_UPDATE',
      data: { content, revision, ...(title ? { title } : {}) }
    })
  }

  sendCursorMove(line, ch) {
    return this.send({ type: 'CURSOR_MOVE', data: { line, ch } })
  }

  sendSelectionChange(from, to) {
    return this.send({ type: 'SELECTION_CHANGE', data: { from, to } })
  }

  send(message) {
    if (this.ws?.readyState !== WebSocket.OPEN) return false
    this.ws.send(JSON.stringify(message))
    return true
  }

  on(event, handler) {
    const handlers = this.handlers.get(event) || []
    handlers.push(handler)
    this.handlers.set(event, handlers)
    return () => this.off(event, handler)
  }

  off(event, handler) {
    this.handlers.set(event, (this.handlers.get(event) || []).filter(item => item !== handler))
  }

  emit(event, data) {
    ;(this.handlers.get(event) || []).forEach(handler => handler(data))
  }

  disconnect() {
    this.manuallyClosed = true
    clearTimeout(this.reconnectTimer)
    this.ws?.close()
    this.ws = null
  }
}
