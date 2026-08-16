import http from 'node:http'

function send(response, status, body) {
  response.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' })
  response.end(JSON.stringify(body))
}

export function startAdminServer(store, evictDocument) {
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
    if (!checkpointMatch && !historyMatch && !restoreMatch) return send(response, 404, { message: 'Not found' })
    const documentId = Number((checkpointMatch || historyMatch || restoreMatch)[1])
    try {
      if (checkpointMatch && request.method === 'GET') return send(response, 200, await store.status(documentId))
      if (checkpointMatch && request.method === 'POST') return send(response, 200, await store.forceCheckpoint(documentId))
      if (historyMatch && request.method === 'GET') return send(response, 200, { items: await store.listHistory(documentId) })
      if (restoreMatch && request.method === 'POST') {
        await evictDocument?.(documentId)
        return send(response, 200, await store.restoreCheckpoint(documentId, Number(restoreMatch[2])))
      }
      return send(response, 405, { message: 'Method not allowed' })
    } catch (error) {
      console.warn(`CRDT checkpoint operation failed for document ${documentId}: ${error.message}`)
      return send(response, 409, { message: 'Checkpoint validation, restore, or compaction failed' })
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
