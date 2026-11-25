/** 信用分展示辅助：档位中文名与变更值格式化（与后端 CreditTier / 记录字段对应）。 */
export const creditTiers = {
  EXCELLENT: '优秀',
  GOOD: '良好',
  WARNING: '预警',
  RESTRICTED: '受限',
}

export function creditTierLabel(tier) {
  return creditTiers[tier] || '未知档位'
}

/** 变更值展示：正数带 +，0 表示仅留痕。 */
export function formatCreditDelta(delta) {
  const value = Number(delta)
  if (!Number.isFinite(value)) return '0'
  return value > 0 ? `+${value}` : String(value)
}
