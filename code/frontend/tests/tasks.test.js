import test from 'node:test'
import assert from 'node:assert/strict'
import {
  taskPermissions,
  validateAppealDecision,
  validateCancellationRuling,
} from '../src/domain/tasks.js'

const user = { userId: 'runner-1', role: 'USER' }
const task = {
  taskId: 'task-1',
  publisherId: 'publisher-1',
  runnerId: null,
  status: 'WAITING_FOR_TAKER',
  deadline: '2026-10-19T10:00:00Z',
}
const now = Date.parse('2026-10-18T10:00:00Z')

test('接单要求尚未绑定跑腿员、未到截止时间且不是本人发布', () => {
  assert.equal(taskPermissions(task, user, now).accept, true)
  assert.equal(taskPermissions(task, { userId: task.publisherId }, now).accept, false)
  assert.equal(taskPermissions({ ...task, runnerId: 'other' }, user, now).accept, false)
  assert.equal(taskPermissions(task, user, Date.parse(task.deadline)).accept, false)
  assert.equal(taskPermissions(task, null, now).accept, false)
})
test('送达仅限实际跑腿员，已过截止时间的在途任务仍可送达', () => {
  const running = { ...task, runnerId: user.userId, status: 'IN_PROGRESS' }
  assert.equal(taskPermissions(running, user, Date.parse(task.deadline) + 1).deliver, true)
  assert.equal(taskPermissions(running, { userId: 'other' }, now).deliver, false)
  assert.equal(
    taskPermissions({ ...running, status: 'WAITING_FOR_ACCEPTANCE' }, user, now).deliver,
    false,
  )
})
test('验收仅限任务发布者，待验收不能显示为已完成', () => {
  const delivered = { ...task, runnerId: user.userId, status: 'WAITING_FOR_ACCEPTANCE' }
  assert.equal(taskPermissions(delivered, { userId: task.publisherId }, now).complete, true)
  assert.equal(taskPermissions(delivered, user, now).complete, false)
  assert.equal(taskPermissions(delivered, user, now).appeal, false)
})
test('取消只适用于进行中或待验收，申诉只适用于已完成且本人参与', () => {
  for (const status of ['WAITING_FOR_TAKER', 'COMPLETED', 'CANCELLED']) {
    assert.equal(
      taskPermissions({ ...task, status, runnerId: user.userId }, user, now).cancel,
      false,
    )
  }
  const completed = { ...task, status: 'COMPLETED', runnerId: user.userId }
  assert.equal(taskPermissions(completed, user, now).appeal, true)
  assert.equal(taskPermissions(completed, { userId: 'other' }, now).appeal, false)
  assert.equal(taskPermissions(task, { userId: task.publisherId }, now).complain, false)
})
test('取消裁决要求分配金额为整数且之和等于冻结报酬', () => {
  const ruling = {
    ruling: 'CANCEL_PROPORTIONALLY',
    refundAmount: 20,
    runnerAmount: 10,
    rulingDetail: '按实际履约分配',
  }
  assert.equal(validateCancellationRuling(ruling, 30), '')
  assert.ok(validateCancellationRuling({ ...ruling, runnerAmount: 9 }, 30))
  assert.ok(validateCancellationRuling({ ...ruling, refundAmount: 20.5 }, 30))
  assert.ok(validateCancellationRuling({ ...ruling, ruling: 'SUPPORT_CONTINUE' }, 30))
  assert.equal(
    validateCancellationRuling(
      { ...ruling, ruling: 'SUPPORT_CONTINUE', refundAmount: 0, runnerAmount: 0 },
      30,
    ),
    '',
  )
  assert.ok(validateCancellationRuling({ ...ruling, rulingDetail: '   ' }, 30))
  assert.ok(validateCancellationRuling({ ...ruling, ruling: 'UNKNOWN' }, 30))
})
test('支持申诉必须填写正整数金额，驳回只能为零', () => {
  const decision = { result: 'SUPPORTED', transferAmount: 10, decisionNote: '支持诉求' }
  assert.equal(validateAppealDecision(decision), '')
  assert.ok(validateAppealDecision({ ...decision, transferAmount: 0 }))
  assert.ok(validateAppealDecision({ ...decision, transferAmount: -1 }))
  assert.ok(validateAppealDecision({ ...decision, transferAmount: 1.5 }))
  assert.ok(validateAppealDecision({ ...decision, result: 'REJECTED' }))
  assert.equal(validateAppealDecision({ ...decision, result: 'REJECTED', transferAmount: 0 }), '')
  assert.ok(validateAppealDecision({ ...decision, decisionNote: ' ' }))
})
