export async function authorize(documentName, token) {
  if (!/^\d+$/.test(documentName) || !token) throw new Error('Not authorized')
  const baseUrl = process.env.DOCFLOW_API_URL || 'http://127.0.0.1:8080'
  const response = await fetch(`${baseUrl}/api/v1/docs/${documentName}/crdt/access`, {
    headers: { Authorization: `Bearer ${token}` },
    signal: AbortSignal.timeout(Number(process.env.CRDT_AUTH_TIMEOUT_MS || 5000))
  })
  if (!response.ok) throw new Error('Not authorized')
  const body = await response.json()
  if (body.code !== 200 || !body.data || String(body.data.documentId) !== documentName) {
    throw new Error('Not authorized')
  }
  return body.data
}

export async function updateMaterializedSnapshot(documentName, html, editorUserId) {
  const baseUrl = process.env.DOCFLOW_API_URL || 'http://127.0.0.1:8080'
  const adminSecret = process.env.CRDT_ADMIN_SECRET
  if (!adminSecret) throw new Error('CRDT admin secret is not configured')
  const response = await fetch(`${baseUrl}/api/v1/docs/${documentName}/crdt/snapshot`, {
    method: 'PUT',
    headers: { 'X-CRDT-Admin-Secret': adminSecret, 'Content-Type': 'application/json' },
    body: JSON.stringify({ html, editorUserId }),
    signal: AbortSignal.timeout(Number(process.env.CRDT_AUTH_TIMEOUT_MS || 5000))
  })
  if (!response.ok) throw new Error('Materialized snapshot update failed')
  const body = await response.json()
  if (body.code !== 200) throw new Error('Materialized snapshot update failed')
}
