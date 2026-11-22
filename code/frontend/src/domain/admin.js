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
export const openComplaintActions = [...recordOnlyActions, 'CREDIT_ADJUSTMENT']
export const creditDeductionOptions = [-2, -5, -10]

export function validateComplaintDecision({ actions, creditAdjustment, decisionNote }) {
  if (!Array.isArray(actions) || !actions.length) return '请至少选择一种处理方式。'
  if (actions.some((action) => !openComplaintActions.includes(action)))
    return '当前仅支持处理留痕与信用扣分，其他处罚暂未开放。'
  if (new Set(actions).size !== actions.length) return '处理方式不能重复。'
  const deductCredit = actions.includes('CREDIT_ADJUSTMENT')
  if (deductCredit && !creditDeductionOptions.includes(Number(creditAdjustment)))
    return '选择信用扣分时，请选择扣 2、5 或 10 分。'
  if (!deductCredit && creditAdjustment != null)
    return '未选择信用扣分时不能提交扣分档位。'
  if (!decisionNote?.trim()) return '请填写处理说明。'
  if (decisionNote.length > 1000) return '处理说明不能超过 1000 字。'
  return ''
}
