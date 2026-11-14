export const notificationTypes = {
  TASK: '任务通知',
  SYSTEM: '系统通知',
}

/** 校验管理员发布系统通知的表单，规则与 openapi 的 SystemNotificationRequest 一致。 */
export function validateSystemNotification(form = {}) {
  const heading = typeof form?.title === 'string' ? form.title.trim() : ''
  const body = typeof form?.content === 'string' ? form.content.trim() : ''
  if (!heading) return '请填写通知标题。'
  if (heading.length > 100) return '通知标题不能超过 100 个字符。'
  if (!body) return '请填写通知内容。'
  if (body.length > 500) return '通知内容不能超过 500 个字符。'
  return ''
}
