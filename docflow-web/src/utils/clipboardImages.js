export function clipboardImageFiles(clipboardData) {
  if (!clipboardData) return []
  const candidates = []
  for (const item of Array.from(clipboardData.items || [])) {
    if (item.kind !== 'file' || !item.type?.startsWith('image/')) continue
    const file = item.getAsFile?.()
    if (file) candidates.push(file)
  }
  if (!candidates.length) {
    candidates.push(...Array.from(clipboardData.files || []).filter(file => file.type?.startsWith('image/')))
  }
  // 某些浏览器会同时在 items 和 files 中暴露同一图片，需要在上传前去重。
  const unique = new Map()
  for (const file of candidates) {
    const key = `${file.name}:${file.type}:${file.size}:${file.lastModified}`
    if (!unique.has(key)) unique.set(key, file)
  }
  return [...unique.values()]
}
