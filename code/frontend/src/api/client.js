export class ApiError extends Error {
  constructor(message, status = 0, code = 'NETWORK_ERROR') {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
  }
}

const statusMessages = {
  400: '请检查填写的内容。',
  401: '登录已失效，请重新登录。',
  403: '你没有执行此操作的权限。',
  404: '记录不存在，或当前账号无法查看。',
  409: '记录已发生变化，请刷新后重试。',
  500: '服务暂时不可用，请稍后重试。',
}

export function createApiClient({
  baseUrl = '',
  fetchImpl = globalThis.fetch,
  timeout = 15000,
} = {}) {
  let csrfPromise
  let authenticationVersion = 0
  const prefix = baseUrl.replace(/\/$/, '')

  async function send(path, { method = 'GET', query, body, signal, headers = {} } = {}) {
    const startedAt = authenticationVersion
    const params = new URLSearchParams()
    Object.entries(query ?? {}).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') params.set(key, String(value))
    })
    const controller = new AbortController()
    const abort = () => controller.abort(signal?.reason)
    if (signal?.aborted) abort()
    else signal?.addEventListener('abort', abort, { once: true })
    const timer = setTimeout(() => controller.abort(), timeout)
    try {
      const response = await fetchImpl(`${prefix}${path}${params.size ? `?${params}` : ''}`, {
        method,
        credentials: 'include',
        headers: {
          Accept: 'application/json',
          ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
          ...headers,
        },
        body: body === undefined ? undefined : JSON.stringify(body),
        signal: controller.signal,
      })
      let payload
      try {
        payload = await response.json()
      } catch {
        if (response.ok)
          throw new ApiError('服务返回了无法读取的数据。', response.status, 'INVALID_RESPONSE')
      }
      if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
      if (!response.ok) {
        if (startedAt === authenticationVersion && [401, 403].includes(response.status))
          csrfPromise = undefined
        const authenticationProbe = [
          '/api/v1/auth/me',
          '/api/v1/auth/login',
          '/api/v1/auth/register',
        ].includes(path)
        if (
          response.status === 401 &&
          !authenticationProbe &&
          startedAt === authenticationVersion &&
          typeof globalThis.dispatchEvent === 'function'
        ) {
          globalThis.dispatchEvent(new Event('campus:auth-expired'))
        }
        throw new ApiError(
          payload?.message || statusMessages[response.status] || '请求失败，请稍后重试。',
          response.status,
          payload?.code ?? 'HTTP_ERROR',
        )
      }
      return payload
    } catch (error) {
      if (error instanceof ApiError) throw error
      if (signal?.aborted) throw new DOMException('Aborted', 'AbortError')
      throw new ApiError(
        controller.signal.aborted
          ? '请求超时，请核实操作结果后重试。'
          : '连接失败，请检查网络或服务是否启动。',
      )
    } finally {
      clearTimeout(timer)
      signal?.removeEventListener('abort', abort)
    }
  }

  async function getCsrf() {
    if (!csrfPromise) {
      csrfPromise = send('/api/v1/auth/csrf')
        .then((token) => {
          if (token?.headerName !== 'X-XSRF-TOKEN' || !token.token) {
            throw new ApiError('无法获取安全校验信息，请刷新页面。', 0, 'INVALID_CSRF_RESPONSE')
          }
          return token
        })
        .catch((error) => {
          csrfPromise = undefined
          throw error
        })
    }
    return csrfPromise
  }

  return {
    async request(path, options = {}) {
      const headers = { ...options.headers }
      if (options.method && !['GET', 'HEAD'].includes(options.method)) {
        const csrf = await getCsrf()
        headers[csrf.headerName] = csrf.token
      }
      if (options.signal?.aborted) throw new DOMException('Aborted', 'AbortError')
      const payload = await send(`/api/v1${path}`, { ...options, headers })
      if (payload?.code !== 'OK' || !Object.hasOwn(payload, 'data')) {
        throw new ApiError(
          payload?.message || '响应格式与接口约定不一致。',
          0,
          payload?.code ?? 'INVALID_RESPONSE',
        )
      }
      if (
        options.method === 'POST' &&
        ['/auth/login', '/auth/register', '/auth/logout'].includes(path)
      ) {
        // Spring Security rotates/clears CSRF state across authentication changes.
        authenticationVersion += 1
        csrfPromise = undefined
      }
      return payload.data
    },
  }
}

export const client = createApiClient({ baseUrl: import.meta.env?.VITE_API_BASE_URL ?? '' })
