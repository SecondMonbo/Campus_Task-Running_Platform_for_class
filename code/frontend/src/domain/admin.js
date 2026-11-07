export const complaintActions = {
  WARNING: '记录提醒与警告',
  CREDIT_ADJUSTMENT: '扣除信用分',
  FINANCIAL_ADJUSTMENT: '账务处理',
  FUNCTION_RESTRICTION: '限制功能',
  ACCOUNT_FREEZE: '冻结账号',
  LOG_ONLY: '记入处理记录',
  OFFLINE_REFERRAL: '记录线下移交',
}

export const recordOnlyActions = ['WARNING', 'LOG_ONLY', 'OFFLINE_REFERRAL']

export function validateComplaintDecision({ actions, decisionNote }) {
  if (!Array.isArray(actions) || !actions.length) return '请至少选择一种处理方式。'
  if (actions.some((action) => !recordOnlyActions.includes(action)))
    return '当前仅支持投诉处理留痕，处罚功能暂未开放。'
  if (new Set(actions).size !== actions.length) return '处理方式不能重复。'
  if (!decisionNote?.trim()) return '请填写处理说明。'
  if (decisionNote.length > 1000) return '处理说明不能超过 1000 字。'
  return ''
}
