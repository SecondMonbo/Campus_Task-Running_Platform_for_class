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
    cancel: participant && ['IN_PROGRESS', 'WAITING_FOR_ACCEPTANCE'].includes(task.status),
    appeal: participant && task.status === 'COMPLETED',
    complain: participant && task.runnerId != null,
  }
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
