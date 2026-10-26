export function safeDestination(value) {
  if (
    typeof value !== 'string' ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    /[\\\s]/.test(value)
  )
    return '/tasks'
  try {
    const path = decodeURIComponent(value.split(/[?#]/, 1)[0])
    if (path.startsWith('//') || path.includes('\\')) return '/tasks'
    if (['/login', '/register'].includes(path.replace(/\/+$/, '').toLowerCase())) return '/tasks'
  } catch {
    return '/tasks'
  }
  return value
}
