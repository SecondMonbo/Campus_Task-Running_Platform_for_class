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

export function allowedComplaintActions(record) {
  const allowed = record?.allowedActions
  const basic = Array.isArray(allowed) ? recordOnlyActions.filter((action) => allowed.includes(action)) : recordOnlyActions
  return record?.status === 'PENDING' && record?.targetUserId && Array.isArray(allowed) && allowed.includes('CREDIT_ADJUSTMENT')
    ? [...basic, 'CREDIT_ADJUSTMENT'] : basic
}

export function validateComplaintDecision({ actions, decisionNote, creditAdjustment, complaintResult }, record) {
  if (!Array.isArray(actions) || !actions.length) return '请至少选择一种处理方式。'
  if (actions.some((action) => !allowedComplaintActions(record).includes(action)))
    return '当前记录尚未开放所选处理方式，请刷新后核对。'
  if (new Set(actions).size !== actions.length) return '处理方式不能重复。'
  if (!decisionNote?.trim()) return '请填写处理说明。'
  if (decisionNote.length > 1000) return '处理说明不能超过 1000 字。'
  if (complaintResult !== undefined && !['SUPPORTED', 'REJECTED'].includes(complaintResult)) return '请选择投诉结论。'
  if (actions.includes('CREDIT_ADJUSTMENT')) {
    if (complaintResult !== 'SUPPORTED') return '只有投诉成立时可以扣分。'
    if (![-2, -5, -10].includes(creditAdjustment)) return '请选择轻微、一般或严重扣分档位。'
  } else if (creditAdjustment !== undefined) return '未选择信用扣分时不能提交扣分值。'
  return ''
}
