import { friendlyMessage } from './friendlyMessage'

const CONTAINER_ID = 'docflow-toast-container'
const MAX_VISIBLE = 4

/**
 * 所有提示都放进同一个纵向排列的容器。
 * 之前每条提示都是 position:fixed 到同一个坐标，同时出现两条时会完全重叠在一起。
 */
function toastContainer() {
  let container = document.getElementById(CONTAINER_ID)
  if (!container) {
    container = document.createElement('div')
    container.id = CONTAINER_ID
    container.className = 'toast-container'
    document.body.appendChild(container)
  }
  return container
}

export function showToast(type, message, duration = 3000) {
  const toast = document.createElement('div')
  toast.className = `toast toast-${type}`
  const fallback = type === 'success' ? '操作成功' : type === 'warning' ? '请注意当前操作' : '操作未完成，请稍后重试'
  toast.textContent = friendlyMessage(message, fallback)

  const container = toastContainer()
  // 极端情况下（如连续请求失败）避免提示堆满整屏，只保留最近的几条。
  while (container.childElementCount >= MAX_VISIBLE) container.firstElementChild?.remove()
  container.appendChild(toast)

  setTimeout(() => {
    toast.style.animation = 'slideOut 0.3s ease forwards'
    setTimeout(() => toast.remove(), 300)
  }, duration)
}

export function success(msg) { showToast('success', msg) }
export function error(msg) { showToast('error', msg) }
export function warning(msg) { showToast('warning', msg) }
