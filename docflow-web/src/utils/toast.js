import { friendlyMessage } from './friendlyMessage'

export function showToast(type, message, duration = 3000) {
  const toast = document.createElement('div')
  toast.className = `toast toast-${type}`
  const fallback = type === 'success' ? '操作成功' : type === 'warning' ? '请注意当前操作' : '操作未完成，请稍后重试'
  toast.textContent = friendlyMessage(message, fallback)
  document.body.appendChild(toast)

  setTimeout(() => {
    toast.style.animation = 'slideOut 0.3s ease forwards'
    setTimeout(() => toast.remove(), 300)
  }, duration)
}

export function success(msg) { showToast('success', msg) }
export function error(msg) { showToast('error', msg) }
export function warning(msg) { showToast('warning', msg) }
