import test from 'node:test'
import assert from 'node:assert/strict'
import { effectScope, nextTick, ref } from 'vue'
import { allowedComplaintActions, validateComplaintDecision } from '../src/domain/admin.js'
import {
  adjustmentPreview, canReviewComplaint, createSubmissionKeys, creditLevelForScore, creditNotice,
  creditReceiptMessage, isComplaintCreditReceipt, isCreditReceipt, isCreditRecord,
  isCreditSummary, isUserId, parseAdjustment, validateCreditDecision,
} from '../src/domain/credit.js'
import { createCurrentCreditResource } from '../src/composables/credit.js'

const now = '2026-11-25T00:00:00Z'
const summary = (creditScore = 80, extra = {}) => ({ userId: '1', creditScore,
  creditLevel: creditLevelForScore(creditScore), restrictionActive: creditScore < 60,
  canAcceptNormalTasks: creditScore >= 60, requiresPublishReview: creditScore < 60,
  consecutiveCompletedCount: 0, updatedAt: now, ...extra })
const complaint = { status: 'PENDING', targetUserId: '2', allowedActions: ['LOG_ONLY', 'CREDIT_ADJUSTMENT'] }
const receipt = (extra = {}) => ({ eventId: 'event-1', userId: '1', status: 'APPLIED',
  creditRecordId: 'record-1', requestedAdjustment: 5, actualAdjustment: 1, scoreBefore: 99, scoreAfter: 100, ...extra })
const settle = async () => { await nextTick(); await new Promise((resolve) => setImmediate(resolve)); await nextTick() }

test('信用档位覆盖 0、59、60、74、75、89、90、100 的边界，未知分数不默认为 80', () => {
  for (const [value, level] of [[0, 'RESTRICTED'], [59, 'RESTRICTED'], [60, 'WARNING'], [74, 'WARNING'],
    [75, 'GOOD'], [89, 'GOOD'], [90, 'EXCELLENT'], [100, 'EXCELLENT']]) assert.equal(creditLevelForScore(value), level)
  for (const value of [null, undefined, '80', NaN, -1, 101, 59.5]) assert.equal(creditLevelForScore(value), null)
})

test('历史受限用户在 60–74 仍按服务端状态提示，恢复 75 后才显示服务端解除结果', () => {
  const restricted = summary(65, { restrictionActive: true, canAcceptNormalTasks: false, requiresPublishReview: true })
  assert.equal(isCreditSummary(restricted), true)
  assert.equal(creditNotice(restricted).tone, 'restricted')
  assert.equal(creditNotice(summary(60)).tone, 'warning')
  assert.equal(creditNotice(summary(75)), null)
  assert.equal(creditNotice(summary(0)).title, '信用受限')
})

test('信用状态必须同时有有效分数、档位和布尔权限，不能接受 null 分数或伪造正常权限', () => {
  assert.equal(isCreditSummary(summary()), true)
  for (const value of [summary(null), summary(59, { restrictionActive: false }),
    summary(59, { canAcceptNormalTasks: true }), summary(80, { creditLevel: 'EXCELLENT' }),
    summary(80, { canAcceptNormalTasks: 'true' }), summary(80, { adjustmentAllowed: 'true' }),
    summary(80, { pendingReview: { reviewId: 'other-user', userId: '2' } })]) assert.equal(isCreditSummary(value), false)
})

test('用户编号保持字符串，支持 BIGINT 最大值并拒绝超界、零和非整数', () => {
  assert.equal(isUserId('9223372036854775807'), true)
  for (const value of ['9223372036854775808', '0', '-1', '1.1', '01', '1e3', 1, null]) assert.equal(isUserId(value), false)
})

test('投诉扣分必须由服务端开放、投诉成立且有明确被投诉人，三个档位分别校验', () => {
  for (const creditAdjustment of [-2, -5, -10]) assert.equal(validateComplaintDecision({
    actions: ['CREDIT_ADJUSTMENT'], complaintResult: 'SUPPORTED', creditAdjustment, decisionNote: '具体事实与证据',
  }, complaint), '')
  const body = { actions: ['CREDIT_ADJUSTMENT'], complaintResult: 'SUPPORTED', creditAdjustment: -2, decisionNote: '事实依据' }
  for (const record of [{ ...complaint, targetUserId: null }, { ...complaint, status: 'PROCESSED' },
    { ...complaint, allowedActions: undefined }, { ...complaint, allowedActions: 'CREDIT_ADJUSTMENT' }])
    assert.ok(validateComplaintDecision(body, record))
  for (const change of [{ complaintResult: 'REJECTED' }, { complaintResult: undefined }, { creditAdjustment: -3 },
    { creditAdjustment: 2 }, { creditAdjustment: '-2' }, { decisionNote: '   ' }, { decisionNote: '字'.repeat(1001) }])
    assert.ok(validateComplaintDecision({ ...body, ...change }, complaint))
  assert.ok(!allowedComplaintActions({ ...complaint, targetUserId: null }).includes('CREDIT_ADJUSTMENT'))
})

test('旧投诉留痕请求保持兼容，不能夹带扣分值或其他处罚', () => {
  assert.equal(validateComplaintDecision({ actions: ['LOG_ONLY'], decisionNote: '留痕' }), '')
  assert.ok(validateComplaintDecision({ actions: ['LOG_ONLY'], creditAdjustment: -2, decisionNote: '留痕' }, complaint))
  assert.ok(validateComplaintDecision({ actions: ['ACCOUNT_FREEZE'], decisionNote: '冻结' }, complaint))
})

test('信用调整使用明确整数并按 0–100 预览，拒绝空白、小数、科学计数和溢出', () => {
  assert.deepEqual(adjustmentPreview(99, '5'), { before: 99, after: 100, actual: 1 })
  assert.deepEqual(adjustmentPreview(3, '-10'), { before: 3, after: 0, actual: -3 })
  assert.equal(parseAdjustment('-2147483648'), -2147483648)
  for (const value of ['', ' ', '1e2', '0.5', 'Infinity', '2147483648']) assert.equal(Number.isNaN(parseAdjustment(value)), true)
  assert.ok(validateCreditDecision({ adjustment: 0, reason: '依据' }))
  assert.equal(validateCreditDecision({ adjustment: 5, reason: '依据' }), '')
  assert.ok(validateCreditDecision({ adjustment: 5, reason: ' ' }))
})

test('投诉复核只允许已生效且可复核的记录，撤销不得手工按原请求值补回', () => {
  const record = { ...complaint, status: 'PROCESSED', creditStatus: 'APPLIED', creditReviewAllowed: true,
    creditRecordId: 'record-1', creditAdjustment: -10, creditActualAdjustment: -3 }
  assert.equal(canReviewComplaint(record), true)
  for (const change of [{ creditStatus: 'PENDING' }, { targetUserId: null }, { creditReviewAllowed: false },
    { creditRecordId: null }, { creditActualAdjustment: -11 }]) assert.equal(canReviewComplaint({ ...record, ...change }), false)
  assert.equal(validateCreditDecision({ result: 'REVOKED', decisionNote: '原结论被证据推翻' }, 'complaint'), '')
  assert.ok(validateCreditDecision({ result: 'REVOKED', adjustment: 10, decisionNote: '依据' }, 'complaint'))
  assert.ok(validateCreditDecision({ result: 'REVOKED', decisionNote: '依据' }, 'user-review'))
})

test('回执区分排队、失败和零分变动，不把未生效事件显示成扣分成功', () => {
  assert.equal(isCreditReceipt(receipt()), true)
  assert.equal(isCreditReceipt(receipt({ actualAdjustment: 5 })), false)
  assert.equal(isCreditReceipt(receipt(), '2'), false)
  const queued = receipt({ status: 'PENDING', creditRecordId: null, actualAdjustment: null, scoreBefore: null, scoreAfter: null })
  assert.equal(isCreditReceipt(queued), true)
  assert.match(creditReceiptMessage(queued), /等待记分/)
  assert.match(creditReceiptMessage({ ...queued, status: 'FAILED' }), /异常/)
  assert.match(creditReceiptMessage(receipt({ actualAdjustment: 0, scoreBefore: 100 })), /未变动/)
  const handled = { complaintId: 'c1', targetUserId: '2', status: 'PROCESSED', actions: ['CREDIT_ADJUSTMENT'],
    complaintResult: 'SUPPORTED', creditStatus: 'APPLIED', creditAdjustment: -10, creditActualAdjustment: -3, creditRecordId: 'r1' }
  assert.equal(isComplaintCreditReceipt(handled, 'c1', -10, '2'), true)
  assert.equal(isComplaintCreditReceipt({ ...handled, creditStatus: undefined }, 'c1', -10, '2'), false)
})

test('信用变更历史接受初始化和零分记录，校验前后差并保留原始原因', () => {
  const record = { recordId: 'r1', userId: '1', eventType: 'INITIALIZATION', eventId: 'u1', reason: '信用初始化',
    requestedAdjustment: 0, actualAdjustment: 0, scoreBefore: null, scoreAfter: 80, createdAt: now }
  assert.equal(isCreditRecord(record), true)
  assert.equal(isCreditRecord({ ...record, scoreBefore: 80 }), false)
  assert.equal(isCreditRecord({ ...record, eventType: 'NEUTRAL_REVIEW', scoreBefore: 80 }), true)
  assert.equal(isCreditRecord({ ...record, eventType: 'ADMIN_ADJUSTMENT', scoreBefore: 79, actualAdjustment: 0 }), false)
})

test('超时重试使用同一幂等键，更换内容生成新键，回到原内容仍保留原键', () => {
  let count = 0
  const key = createSubmissionKeys(() => `key-${++count}`)
  const body = { adjustment: 5, reason: '依据' }
  assert.equal(key('user:1', body), key('user:1', { ...body }))
  assert.notEqual(key('user:1', body), key('user:1', { ...body, adjustment: 2 }))
  assert.equal(key('user:1', body), 'key-1')
  assert.notEqual(key('user:1', body), key('user:2', body))
})

test('退出和切换账号立刻隐藏旧信用状态，旧请求不能覆盖新用户', async () => {
  const currentSession = ref({ userId: '1' })
  const revision = ref(0)
  const pending = []
  const scope = effectScope()
  const resource = scope.run(() => createCurrentCreditResource((signal, userId) => new Promise((resolve) => {
    pending.push({ signal, userId, resolve })
  }), currentSession, revision))
  try {
    await nextTick()
    currentSession.value = { userId: '2' }
    assert.equal(resource.data.value, null)
    await nextTick()
    assert.equal(pending[0].signal.aborted, true)
    pending[1].resolve(summary(75, { userId: '2' }))
    await settle()
    pending[0].resolve(summary(0))
    await settle()
    assert.equal(resource.data.value.userId, '2')
    currentSession.value = null
    assert.equal(resource.data.value, null)
    assert.equal(resource.lastKnown.value, null)
  } finally { scope.stop() }
})

test('信用接口失败保留未知状态，历史受限状态不会因刷新失败而被误判已解禁', async () => {
  let fail = false
  const scope = effectScope()
  const resource = scope.run(() => createCurrentCreditResource(async () => {
    if (fail) throw new Error('信用接口未接入')
    return summary(59)
  }, ref({ userId: '1' }), ref(0)))
  try {
    await settle()
    fail = true
    resource.reload()
    await settle()
    assert.equal(resource.data.value, null)
    assert.match(resource.error.value.message, /未接入/)
    assert.equal(resource.lastKnown.value.canAcceptNormalTasks, false)
  } finally { scope.stop() }
})
