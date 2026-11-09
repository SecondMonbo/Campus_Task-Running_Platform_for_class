import test from 'node:test'
import assert from 'node:assert/strict'
import {
  taskPermissions,
  validatePasswordChange,
  validateProfileDraft,
} from '../src/domain/tasks.js'

test('个人资料校验：显示名必填且不超过 50 字，手机号可为空但要满足字符与长度规则', () => {
  assert.equal(validateProfileDraft({ displayName: ' 发布者演示 ', phone: '13800001111' }), '')
  assert.equal(validateProfileDraft({ displayName: '发布者演示', phone: '' }), '')
  assert.equal(validateProfileDraft({ displayName: '发布者演示', phone: '+86 138-0000-1111' }), '')

  assert.equal(validateProfileDraft({ displayName: '   ', phone: '13800001111' }), '请填写显示名。')
  assert.equal(validateProfileDraft({ displayName: '', phone: '' }), '请填写显示名。')
  assert.equal(
    validateProfileDraft({ displayName: '名'.repeat(51), phone: '' }),
    '显示名不能超过 50 个字符。',
  )
  assert.equal(
    validateProfileDraft({ displayName: '发布者演示', phone: '1'.repeat(31) }),
    '手机号不能超过 30 个字符。',
  )
  assert.equal(
    validateProfileDraft({ displayName: '发布者演示', phone: '1380000abc' }),
    '手机号只能包含数字、空格、+ 或 -。',
  )
})

test('修改密码校验：当前密码必填、新密码 8-128 位、不得与当前相同、两次输入一致', () => {
  assert.equal(
    validatePasswordChange({ currentPassword: 'User123456', newPassword: 'User654321', confirmPassword: 'User654321' }),
    '',
  )

  assert.equal(
    validatePasswordChange({ currentPassword: '', newPassword: 'User654321', confirmPassword: 'User654321' }),
    '请填写当前密码。',
  )
  assert.equal(
    validatePasswordChange({ currentPassword: 'User123456', newPassword: '', confirmPassword: '' }),
    '请填写新密码。',
  )
  assert.equal(
    validatePasswordChange({ currentPassword: 'User123456', newPassword: 'short', confirmPassword: 'short' }),
    '新密码长度需为 8-128 位。',
  )
  assert.equal(
    validatePasswordChange({
      currentPassword: 'User123456',
      newPassword: 'User123456',
      confirmPassword: 'User123456',
    }),
    '新密码不能与当前密码相同。',
  )
  assert.equal(
    validatePasswordChange({
      currentPassword: 'User123456',
      newPassword: 'User654321',
      confirmPassword: 'User654322',
    }),
    '两次输入的新密码不一致。',
  )
  assert.equal(
    validatePasswordChange({ currentPassword: 'User123456', newPassword: 'a'.repeat(129), confirmPassword: 'a'.repeat(129) }),
    '新密码长度需为 8-128 位。',
  )
})

test('举报权限：任意登录用户都能举报非法任务，未登录不显示入口', () => {
  const task = {
    taskId: 'T1',
    publisherId: '2',
    runnerId: null,
    status: 'WAITING_FOR_TAKER',
    deadline: '2099-01-01T00:00:00Z',
  }
  const now = Date.parse('2026-11-09T00:00:00Z')

  assert.equal(taskPermissions(task, { userId: '2' }, now).report, true)
  assert.equal(taskPermissions(task, { userId: '3' }, now).report, true)
  assert.equal(taskPermissions({ ...task, runnerId: '3', status: 'IN_PROGRESS' }, { userId: '3' }, now).report, true)
  assert.equal(taskPermissions(task, null, now).report, false)
})
