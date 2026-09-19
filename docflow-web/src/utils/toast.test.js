import { beforeEach, describe, expect, it } from 'vitest'
import { showToast } from './toast'

describe('toast stacking', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  it('renders every toast inside one column container so they cannot overlap', () => {
    showToast('error', '登录状态已失效，请重新登录')
    showToast('warning', '登录已过期，请重新登录')

    const container = document.getElementById('docflow-toast-container')
    expect(container).not.toBeNull()
    expect(container.className).toBe('toast-container')
    // 两条提示必须是容器的两个子节点，而不是各自 fixed 到同一个坐标上互相遮挡。
    expect(container.childElementCount).toBe(2)
    expect([...container.children].map(node => node.textContent))
      .toEqual(['登录状态已失效，请重新登录', '登录已过期，请重新登录'])
    expect([...container.children].every(node => node.classList.contains('toast'))).toBe(true)
  })

  it('keeps only the most recent toasts so a burst of failures cannot fill the screen', () => {
    for (let index = 0; index < 6; index += 1) showToast('error', `第${index}条`)

    const container = document.getElementById('docflow-toast-container')
    expect(container.childElementCount).toBe(4)
    expect(container.firstElementChild.textContent).toBe('第2条')
    expect(container.lastElementChild.textContent).toBe('第5条')
  })
})
