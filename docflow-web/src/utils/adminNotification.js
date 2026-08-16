export function buildAdminNotificationPayload(form) {
  const targetType = String(form?.targetType || '').trim().toUpperCase()
  const content = String(form?.content || '').trim()
  if (!['ALL', 'USER', 'REGISTERED_AT'].includes(targetType)) throw new Error('请选择提示发送范围')
  if (!content) throw new Error('请输入提示内容')
  if (content.length > 500) throw new Error('提示内容不能超过 500 个字')

  const payload = {
    targetType,
    username: null,
    registeredFrom: null,
    registeredTo: null,
    content
  }
  if (targetType === 'USER') {
    payload.username = String(form?.username || '').trim()
    if (!payload.username) throw new Error('请输入接收人的用户名')
  }
  if (targetType === 'REGISTERED_AT') {
    payload.registeredFrom = form?.registeredFrom || null
    payload.registeredTo = form?.registeredTo || null
    if (!payload.registeredFrom && !payload.registeredTo) throw new Error('请选择注册开始时间或结束时间')
    if (payload.registeredFrom && payload.registeredTo && payload.registeredFrom > payload.registeredTo) {
      throw new Error('注册开始时间不能晚于结束时间')
    }
  }
  return payload
}

export function describeAdminNotificationTarget(payload) {
  if (payload.targetType === 'ALL') return '全部用户'
  if (payload.targetType === 'USER') return `用户“${payload.username}”`
  return `注册时间从 ${payload.registeredFrom || '不限'} 到 ${payload.registeredTo || '不限'} 的用户`
}
