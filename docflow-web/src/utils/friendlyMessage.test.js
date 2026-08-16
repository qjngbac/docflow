import { describe, expect, it } from 'vitest'
import { friendlyMessage, httpErrorMessage } from './friendlyMessage'

describe('friendlyMessage', () => {
  it('将常见后端英文权限错误转换为中文', () => {
    expect(friendlyMessage('No document admin permission')).toBe('你没有管理此文档的权限')
    expect(friendlyMessage('No document write permission')).toBe('你没有编辑此内容的权限')
  })

  it('未知英文技术信息使用易懂的中文后备提示', () => {
    expect(friendlyMessage('Unexpected internal state', '操作失败')).toBe('操作失败')
    expect(httpErrorMessage(503, 'Unexpected upstream state')).toBe('服务正在忙，请稍后重试')
  })

  it('保留已经清晰的中文提示', () => {
    expect(friendlyMessage('文档已删除')).toBe('文档已删除')
  })

  it('将文件安全错误转换为用户可理解的提示', () => {
    expect(friendlyMessage('不允许上传可执行文件')).toBe('该文件可能包含可执行内容，系统已拒绝上传')
    expect(friendlyMessage('Virus signature found')).toBe('文件未通过安全检查，请更换文件后重试')
  })
})
