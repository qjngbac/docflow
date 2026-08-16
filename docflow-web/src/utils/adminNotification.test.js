import { describe, expect, it } from 'vitest'
import { buildAdminNotificationPayload } from './adminNotification'

describe('buildAdminNotificationPayload', () => {
  it('builds a message for all users', () => {
    expect(buildAdminNotificationPayload({ targetType: 'ALL', content: '  系统维护通知  ' })).toEqual({
      targetType: 'ALL',
      username: null,
      registeredFrom: null,
      registeredTo: null,
      content: '系统维护通知'
    })
  })

  it('requires and trims the exact username for one user', () => {
    expect(buildAdminNotificationPayload({ targetType: 'USER', username: ' member ', content: '请查看提示' }).username)
      .toBe('member')
    expect(() => buildAdminNotificationPayload({ targetType: 'USER', username: ' ', content: '请查看提示' }))
      .toThrow('请输入接收人的用户名')
  })

  it('keeps an optional registration boundary and rejects a reversed period', () => {
    expect(buildAdminNotificationPayload({
      targetType: 'REGISTERED_AT',
      registeredFrom: '2026-08-01T00:00',
      registeredTo: '',
      content: '欢迎使用云笺'
    })).toMatchObject({ registeredFrom: '2026-08-01T00:00', registeredTo: null })

    expect(() => buildAdminNotificationPayload({
      targetType: 'REGISTERED_AT',
      registeredFrom: '2026-08-09T00:00',
      registeredTo: '2026-08-01T00:00',
      content: '测试'
    })).toThrow('开始时间不能晚于结束时间')
  })
})
