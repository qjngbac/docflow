import http from 'node:http'

// 管理接口默认只监听回环地址，并以共享密钥保护 checkpoint 与内容替换操作。
function send(response, status, body) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' })
  response.end(JSON.stringify(body))
}

function readJson(request, maximumBytes) {
  // 在解析前累计原始字节，阻止超大管理请求占满进程内存。
  return new Promise((resolve, reject) => {
    let size = 0
    const chunks = []
    request.on('data', chunk => {
      size += chunk.length
      if (size > maximumBytes) {
        reject(new Error('Request body is too large'))
        request.destroy()
        return
      }
      chunks.push(chunk)
    })
    request.on('end', () => {
      try { resolve(JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}')) } catch { reject(new Error('Invalid JSON body')) }
    })
    request.on('error', reject)
  })
}

/**
 * 按真实失败的操作回不同的失败原因，避免把关闭连接、内容替换等失败
 * 也统一说成“checkpoint 校验或压缩失败”。
 */
function failureMessage(path) {
  if (path.endsWith('/connections/close')) return 'Disconnecting document connections failed'
  if (path.endsWith('/content')) return 'Document content replacement failed'
  if (path.endsWith('/restore')) return 'Checkpoint restore failed'
  if (path.endsWith('/checkpoints')) return 'Checkpoint history lookup failed'
  if (path.endsWith('/checkpoint')) return 'Checkpoint operation failed'
  return 'CRDT administration operation failed'
}

export function startAdminServer(store, evictDocument, replaceDocument) {
  const host = process.env.CRDT_ADMIN_HOST || '127.0.0.1'
  const port = Number(process.env.CRDT_ADMIN_PORT || 1235)
  const secret = process.env.CRDT_ADMIN_SECRET || 'docflow-local-admin'
  const admin = http.createServer(async (request, response) => {
    const path = new URL(request.url, `http://${host}:${port}`).pathname
    if (path === '/health' && request.method === 'GET') {
      return send(response, 200, { status: 'UP', service: 'docflow-collab' })
    }
    if (request.headers.authorization !== `Bearer ${secret}`) return send(response, 401, { message: 'Unauthorized' })
    const checkpointMatch = path.match(/^\/documents\/(\d+)\/checkpoint$/)
    const historyMatch = path.match(/^\/documents\/(\d+)\/checkpoints$/)
    const restoreMatch = path.match(/^\/documents\/(\d+)\/checkpoints\/(\d+)\/restore$/)
    const closeMatch = path.match(/^\/documents\/(\d+)\/connections\/close$/)
    const contentMatch = path.match(/^\/documents\/(\d+)\/content$/)
    if (!checkpointMatch && !historyMatch && !restoreMatch && !closeMatch && !contentMatch) return send(response, 404, { message: 'Not found' })
    const documentId = Number((checkpointMatch || historyMatch || restoreMatch || closeMatch || contentMatch)[1])
    try {
      if (checkpointMatch && request.method === 'GET') return send(response, 200, await store.status(documentId))
      if (checkpointMatch && request.method === 'POST') return send(response, 200, await store.forceCheckpoint(documentId))
      if (historyMatch && request.method === 'GET') return send(response, 200, { items: await store.listHistory(documentId) })
      if (restoreMatch && request.method === 'POST') {
        await evictDocument?.(documentId)
        return send(response, 200, await store.restoreCheckpoint(documentId, Number(restoreMatch[2])))
      }
      if (closeMatch && request.method === 'POST') {
        await evictDocument?.(documentId)
        return send(response, 200, { documentId, disconnected: true })
      }
      if (contentMatch && request.method === 'POST') {
        const body = await readJson(request, Number(process.env.CRDT_ADMIN_MAX_BODY_BYTES || 2 * 1024 * 1024))
        if (typeof body.html !== 'string') return send(response, 400, { message: 'HTML content is required' })
        if (!replaceDocument) return send(response, 503, { message: 'Content replacement is unavailable' })
        return send(response, 200, await replaceDocument(documentId, body.html))
      }
      return send(response, 405, { message: 'Method not allowed' })
    } catch (error) {
      // 记录真实失败的 Admin API（方法 + 路径）与异常原因；不打印共享密钥和请求体。
      const reason = error instanceof Error ? `${error.name}: ${error.message}` : String(error)
      console.warn(`DocFlow CRDT admin ${request.method} ${path} failed for document ${documentId} (${reason})`)
      return send(response, 409, { message: failureMessage(path) })
    }
  })
  return new Promise((resolve, reject) => {
    admin.once('error', reject)
    admin.listen(port, host, () => {
      console.log(`DocFlow CRDT admin ready on http://${host}:${port}`)
      resolve(admin)
    })
  })
}
