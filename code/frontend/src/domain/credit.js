export const creditLevels = {
  EXCELLENT: '优秀',
  GOOD: '良好',
  WARNING: '预警',
  RESTRICTED: '受限',
}

export const creditEvents = {
  INITIALIZATION: '初始化',
  FIVE_STAR_REVIEW: '五星评价',
  NEUTRAL_REVIEW: '中性评价',
  LOW_STAR_REVIEW: '低星评价',
  ON_TIME_COMPLETION: '按时完成',
  TASK_OVERDUE: '任务超时',
  RESPONSIBLE_CANCELLATION: '有责取消',
  COMPLAINT_PENALTY: '投诉扣分',
  CONSECUTIVE_RECOVERY: '连续履约恢复',
  ADMIN_ADJUSTMENT: '管理员复核调整',
  COMPLAINT_REVIEW: '投诉扣分复核',
}

export const creditStatuses = { PENDING: '等待记分', APPLIED: '已生效', FAILED: '记分异常，需核对' }
export const complaintPenalties = {
  '-2': { label: '轻微 · 扣 2 分', basis: '一次、轻微且无实质损失的违规' },
  '-5': { label: '一般 · 扣 5 分', basis: '一般违约、重复不履约或造成可说明的实际影响' },
  '-10': { label: '严重 · 扣 10 分', basis: '有证据支持的故意欺诈、重大财物损毁或人身安全风险' },
}

const text = (value) => typeof value === 'string' && value.trim().length > 0
const date = (value) => text(value) && Number.isFinite(Date.parse(value))
const score = (value) => Number.isInteger(value) && value >= 0 && value <= 100
const integer = (value) => Number.isInteger(value) && value >= -2147483648 && value <= 2147483647

export function isUserId(value) {
  return typeof value === 'string' && /^[1-9]\d{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n
}

export function creditLevelForScore(value) {
  if (!score(value)) return null
  return value >= 90 ? 'EXCELLENT' : value >= 75 ? 'GOOD' : value >= 60 ? 'WARNING' : 'RESTRICTED'
}

export function isCreditReviewRequest(value, userId) {
  return Boolean(value && text(value.reviewId) && isUserId(value.userId) &&
    (!userId || value.userId === userId) && ['PENDING', 'DECIDED'].includes(value.status) &&
    text(value.reason) && date(value.createdAt))
}

export function isCreditSummary(value) {
  if (!value || !isUserId(value.userId) || !score(value.creditScore) ||
    value.creditLevel !== creditLevelForScore(value.creditScore) ||
    !['restrictionActive', 'canAcceptNormalTasks', 'requiresPublishReview'].every((key) => typeof value[key] === 'boolean') ||
    !Number.isInteger(value.consecutiveCompletedCount) || value.consecutiveCompletedCount < 0 ||
    value.consecutiveCompletedCount > 10 || !date(value.updatedAt)) return false
  if (value.creditScore < 60 && !value.restrictionActive) return false
  if (value.restrictionActive && (value.canAcceptNormalTasks || !value.requiresPublishReview)) return false
  if (['adjustmentAllowed', 'reviewRequestAllowed', 'reviewDecisionAllowed'].some(
    (key) => value[key] !== undefined && typeof value[key] !== 'boolean',
  )) return false
  return value.pendingReview == null || isCreditReviewRequest(value.pendingReview, value.userId)
}

export function isCreditRecord(value) {
  if (!value || !text(value.recordId) || !isUserId(value.userId) || !text(value.eventType) ||
    !text(value.eventId) || !text(value.reason) || !integer(value.requestedAdjustment) ||
    !integer(value.actualAdjustment) || !score(value.scoreAfter) || !date(value.createdAt) ||
    (value.administratorId != null && !isUserId(value.administratorId))) return false
  if (value.eventType === 'INITIALIZATION') return value.scoreBefore === null && value.scoreAfter === 80 &&
    value.requestedAdjustment === 0 && value.actualAdjustment === 0
  return score(value.scoreBefore) && value.actualAdjustment === value.scoreAfter - value.scoreBefore
}

export function isCreditReceipt(value, userId) {
  if (!value || !text(value.eventId) || !isUserId(value.userId) ||
    (userId && value.userId !== userId) || !Object.hasOwn(creditStatuses, value.status) ||
    !integer(value.requestedAdjustment)) return false
  if (value.status === 'APPLIED') return text(value.creditRecordId) && score(value.scoreBefore) &&
    score(value.scoreAfter) && integer(value.actualAdjustment) &&
    value.actualAdjustment === value.scoreAfter - value.scoreBefore
  return [value.scoreBefore, value.scoreAfter].every((item) => item === null || score(item)) &&
    (value.actualAdjustment === null || integer(value.actualAdjustment))
}

export function isComplaintCreditReceipt(value, complaintId, adjustment, userId) {
  if (!value || value.complaintId !== complaintId || value.status !== 'PROCESSED' ||
    !isUserId(value.targetUserId) || (userId && value.targetUserId !== userId) ||
    !value.actions?.includes('CREDIT_ADJUSTMENT') || value.complaintResult !== 'SUPPORTED' ||
    !Object.hasOwn(creditStatuses, value.creditStatus) || value.creditAdjustment !== adjustment) return false
  const actual = value.creditActualAdjustment
  return value.creditStatus === 'APPLIED'
    ? text(value.creditRecordId) && Number.isInteger(actual) && actual <= 0 && actual >= adjustment
    : actual === null || (Number.isInteger(actual) && actual <= 0 && actual >= adjustment)
}

export function creditNotice(value) {
  if (!isCreditSummary(value)) return null
  if (value.restrictionActive) return {
    tone: 'restricted', title: '信用受限',
    message: '暂不能接取普通任务；新发布任务需管理员复核后公开。已接取任务仍可继续履约，恢复至 75 分后由系统解除限制。',
  }
  if (value.creditLevel === 'WARNING') return {
    tone: 'warning', title: '履约预警',
    message: '当前处于 60–74 分预警档，请注意履约。当前接单和发布权限以服务端返回的限制状态为准。',
  }
  return null
}

export function parseAdjustment(value) {
  if (typeof value === 'number') return integer(value) ? value : NaN
  if (typeof value !== 'string' || !/^[+-]?\d+$/.test(value.trim())) return NaN
  const parsed = Number(value)
  return integer(parsed) ? parsed : NaN
}

export function validateCreditReason(value) {
  if (!text(value)) return '请填写复核说明与事实依据。'
  if (value.trim().length > 1000) return '说明不能超过 1000 字。'
  return ''
}

export function validateCreditDecision(body, mode = 'adjustment') {
  const reasonError = validateCreditReason(body?.reason ?? body?.decisionNote)
  if (reasonError) return reasonError
  if (mode !== 'adjustment' && !(mode === 'complaint' ? ['UPHELD', 'REVOKED', 'ADJUSTED'] : ['UPHELD', 'ADJUSTED']).includes(body.result))
    return '请选择有效的复核结论。'
  if (mode === 'adjustment' || body.result === 'ADJUSTED') {
    if (!integer(body.adjustment) || body.adjustment === 0) return '调整值必须是非零的 32 位整数。'
  } else if (body.adjustment !== undefined) return '维持或撤销原处理时，不应填写手工调整值。'
  return ''
}

export function adjustmentPreview(currentScore, adjustment) {
  const delta = parseAdjustment(adjustment)
  if (!score(currentScore) || !integer(delta)) return null
  const after = Math.min(100, Math.max(0, currentScore + delta))
  return { before: currentScore, after, actual: after - currentScore }
}

export function canReviewComplaint(record) {
  return Boolean(record?.status === 'PROCESSED' && record.targetUserId &&
    record.creditReviewAllowed === true && record.creditStatus === 'APPLIED' &&
    text(record.creditRecordId) && [-2, -5, -10].includes(record.creditAdjustment) &&
    Number.isInteger(record.creditActualAdjustment) && record.creditActualAdjustment <= 0 &&
    record.creditActualAdjustment >= record.creditAdjustment)
}

export function creditReceiptMessage(receipt) {
  if (receipt.status === 'PENDING') return '处理已提交，信用变更等待记分，请稍后刷新核对。'
  if (receipt.status === 'FAILED') return '处理记录已保存，信用记分异常，请核对记录。'
  return receipt.actualAdjustment === 0 ? '复核记录已保存，实际分值未变动。' : '信用变更已生效，原始记录已保留。'
}

export const signedCredit = (value) => Number.isInteger(value) ? `${value > 0 ? '+' : ''}${value}` : '—'

/** 同一表单内容重试保留键；本地不保存信用、身份或操作者。 */
export function createSubmissionKeys(generate = () => globalThis.crypto.randomUUID()) {
  const keys = new Map()
  return (resource, body) => {
    const signature = JSON.stringify([resource, body])
    if (!keys.has(signature)) keys.set(signature, generate())
    return keys.get(signature)
  }
}
