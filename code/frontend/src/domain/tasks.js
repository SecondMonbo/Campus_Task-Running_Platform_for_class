export const taskStatuses = {
  WAITING_FOR_TAKER: '待接单',
  IN_PROGRESS: '进行中',
  WAITING_FOR_ACCEPTANCE: '待验收',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
}

export const transactionTypes = {
  INCOME: '入账',
  EXPENSE: '支出',
  FINE: '罚款',
  COMPENSATION: '补偿',
  FREEZE: '冻结',
  UNFREEZE: '解冻 / 退款',
}

/**
 * 校验发布任务表单，返回错误提示；通过时返回空字符串。
 * 规则与 openapi.yaml 的 CreateTaskRequest 及后端 TaskCreateRequest 保持一致。
 */
export function validateTaskDraft(
  { title, description, reward, deadline, pickupLocation, deliveryLocation },
  now = Date.now(),
) {
  const text = (value) => (typeof value === 'string' ? value.trim() : '')
  if (!text(title)) return '请填写任务标题。'
  if (text(title).length > 100) return '标题不能超过 100 个字符。'
  if (!text(description)) return '请填写任务说明。'
  if (text(description).length > 2000) return '任务说明不能超过 2000 个字符。'
  if (!Number.isSafeInteger(reward) || reward < 1 || reward > 1000)
    return '报酬必须是 1 到 1000 之间的整数。'
  const deadlineTime = typeof deadline === 'string' ? Date.parse(deadline) : NaN
  if (!Number.isFinite(deadlineTime)) return '请选择有效的截止时间。'
  if (deadlineTime <= now) return '截止时间必须晚于当前时间。'
  if (!text(pickupLocation)) return '请填写取件地点。'
  if (text(pickupLocation).length > 200) return '取件地点不能超过 200 个字符。'
  if (!text(deliveryLocation)) return '请填写送件地点。'
  if (text(deliveryLocation).length > 200) return '送件地点不能超过 200 个字符。'
  return ''
}

export function taskPermissions(task, user, now = Date.now()) {
  const publisher = Boolean(user && String(task.publisherId) === String(user.userId))
  const runner = Boolean(
    user && task.runnerId != null && String(task.runnerId) === String(user.userId),
  )
  const participant = publisher || runner
  return {
    accept: Boolean(
      user &&
        !publisher &&
        !task.runnerId &&
        task.status === 'WAITING_FOR_TAKER' &&
        Date.parse(task.deadline) > now,
    ),
    deliver: runner && task.status === 'IN_PROGRESS',
    complete: publisher && task.status === 'WAITING_FOR_ACCEPTANCE',
    edit: publisher && task.status === 'WAITING_FOR_TAKER',
    withdraw: publisher && task.status === 'WAITING_FOR_TAKER',
    review: publisher && task.status === 'COMPLETED',
    cancel: participant && ['IN_PROGRESS', 'WAITING_FOR_ACCEPTANCE'].includes(task.status),
    appeal: participant && task.status === 'COMPLETED',
    complain: participant && task.runnerId != null,
    // 任意登录用户都可以直接举报非法任务（违规内容、虚假信息等），不限于任务参与者。
    report: Boolean(user),
  }
}

/** 校验个人资料编辑表单，规则与 openapi 的 UpdateProfileRequest 保持一致。 */
export function validateProfileDraft({ displayName, phone }) {
  const name = typeof displayName === 'string' ? displayName.trim() : ''
  if (!name) return '请填写显示名。'
  if (name.length > 50) return '显示名不能超过 50 个字符。'
  const phoneValue = typeof phone === 'string' ? phone.trim() : ''
  if (phoneValue.length > 30) return '手机号不能超过 30 个字符。'
  if (phoneValue && !/^[0-9+\-\s]+$/.test(phoneValue))
    return '手机号只能包含数字、空格、+ 或 -。'
  return ''
}

/** 校验修改密码表单，规则与 openapi 的 ChangePasswordRequest 保持一致。 */
export function validatePasswordChange({ currentPassword, newPassword, confirmPassword }) {
  if (!currentPassword) return '请填写当前密码。'
  if (!newPassword) return '请填写新密码。'
  if (newPassword.length < 8 || newPassword.length > 128) return '新密码长度需为 8-128 位。'
  if (newPassword === currentPassword) return '新密码不能与当前密码相同。'
  if (newPassword !== confirmPassword) return '两次输入的新密码不一致。'
  return ''
}

export function validateCancellationRuling(
  { ruling, refundAmount, runnerAmount, rulingDetail },
  reward,
) {
  if (!['SUPPORT_REQUESTER_CANCEL', 'SUPPORT_CONTINUE', 'CANCEL_PROPORTIONALLY'].includes(ruling))
    return '请选择有效的裁决结论。'
  if (!rulingDetail?.trim()) return '请填写裁决说明。'
  if (![refundAmount, runnerAmount].every((amount) => Number.isSafeInteger(amount) && amount >= 0))
    return '分配金额必须为非负整数。'
  if (ruling === 'SUPPORT_CONTINUE')
    return refundAmount === 0 && runnerAmount === 0 ? '' : '继续履约时，两项分配金额必须为 0。'
  if (!Number.isSafeInteger(reward) || refundAmount + runnerAmount !== reward)
    return '退款和支付金额之和必须等于任务冻结报酬。'
  return ''
}

export function validateAppealDecision({ result, transferAmount, decisionNote }) {
  if (!['SUPPORTED', 'PARTIALLY_SUPPORTED', 'REJECTED'].includes(result))
    return '请选择有效的裁决结论。'
  if (!decisionNote?.trim()) return '请填写裁决说明。'
  if (!Number.isSafeInteger(transferAmount) || transferAmount < 0) return '划转金额必须为非负整数。'
  if (result === 'REJECTED') return transferAmount === 0 ? '' : '驳回申诉时，划转金额必须为 0。'
  return transferAmount > 0 ? '' : '支持或部分支持申诉时，请填写正整数划转金额。'
}

export const formatNumber = (value) =>
  Number.isFinite(Number(value)) ? Number(value).toLocaleString('zh-CN') : '—'
export function formatDate(value, short = false) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    month: '2-digit',
    day: '2-digit',
    ...(short ? {} : { year: 'numeric' }),
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(date)
}
