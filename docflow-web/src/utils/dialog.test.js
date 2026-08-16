import { describe, expect, it } from 'vitest'
import { confirmDialog, promptDialog } from './dialog'

describe('dialog', () => {
  it('确认窗口在页面中央显示并返回确认结果', async () => {
    const result = confirmDialog('确定删除？', { title: '删除文档', danger: true })
    expect(document.querySelector('.app-dialog h2')?.textContent).toBe('删除文档')
    document.querySelector('.app-dialog footer button:last-child').click()
    await expect(result).resolves.toBe(true)
    expect(document.querySelector('.app-dialog')).toBeNull()
  })

  it('输入窗口返回用户输入', async () => {
    const result = promptDialog('模板名称', '项目模板')
    const input = document.querySelector('.app-dialog-input')
    input.value = '新模板'
    document.querySelector('.app-dialog footer button:last-child').click()
    await expect(result).resolves.toBe('新模板')
  })
})
