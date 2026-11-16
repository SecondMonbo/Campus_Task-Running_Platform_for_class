import test from 'node:test'
import assert from 'node:assert/strict'
import { notificationTypes, validateSystemNotification } from '../src/domain/notifications.js'

test('系统通知与任务通知使用不同展示标签', () => {
  assert.equal(notificationTypes.TASK, '任务通知')
  assert.equal(notificationTypes.SYSTEM, '系统通知')
  assert.notEqual(notificationTypes.TASK, notificationTypes.SYSTEM)
})

test('系统通知表单去除首尾空白后校验标题和内容', () => {
  assert.equal(validateSystemNotification({ title: '维护通知', content: '今晚维护' }), '')
  assert.equal(validateSystemNotification({ title: '  维护通知  ', content: ' 正文 ' }), '')
  assert.equal(
    validateSystemNotification({ title: '标'.repeat(100), content: '内'.repeat(500) }),
    '',
  )
  for (const form of [
    {},
    { title: '', content: '正文' },
    { title: '   ', content: '正文' },
    { title: '标题', content: '' },
    { title: '标题', content: '   ' },
    { title: 'x'.repeat(101), content: '正文' },
    { title: '标题', content: 'x'.repeat(501) },
    null,
  ])
    assert.ok(validateSystemNotification(form), JSON.stringify(form))
})
