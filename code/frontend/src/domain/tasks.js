export const taskStatuses = {
  WAITING_FOR_TAKER: '待接单',
  IN_PROGRESS: '进行中',
  WAITING_FOR_ACCEPTANCE: '待验收',
  COMPLETED: '已完成',
  CANCELLED: '已取消',
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
