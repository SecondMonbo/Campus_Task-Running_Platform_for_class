import test from 'node:test'
import assert from 'node:assert/strict'
import { ApiError, createApiClient } from '../src/api/client.js'
const ok = (data) => Response.json({ code: 'OK', message: 'success', data })
const csrf = () =>
  Response.json({ headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: 'test-token' })

test('查询保留 page=0 和 false，忽略未使用的筛选参数', async () => {
  let request
  const client = createApiClient({
    baseUrl: 'https://mock.example/',
    fetchImpl: async (url, options) => {
      request = { url, options }
      return ok({ items: [] })
    },
  })
  await client.request('/notifications', {
    query: { page: 0, unreadOnly: false, status: '', size: 10 },
  })
  assert.equal(
    request.url,
    'https://mock.example/api/v1/notifications?page=0&unreadOnly=false&size=10',
  )
  assert.equal(request.options.credentials, 'include')
})
test('并发写操作共享 CSRF 获取；动作不提交操作者或目标状态', async () => {
  const calls = []
  const client = createApiClient({
    fetchImpl: async (url, options) => {
      calls.push({ url, ...options })
      return url.endsWith('/csrf') ? csrf() : ok({ taskId: '1' })
    },
  })
  await Promise.all([
    client.request('/tasks/1/delivery', { method: 'POST' }),
    client.request('/tasks/2/completion', { method: 'POST' }),
  ])
  assert.equal(calls.filter((call) => call.url.endsWith('/csrf')).length, 1)
  const writes = calls.filter((call) => call.method === 'POST')
  assert.equal(writes.length, 2)
  assert.ok(
    writes.every(
      (call) => call.headers['X-XSRF-TOKEN'] === 'test-token' && call.body === undefined,
    ),
  )
})
test('409 保留服务端错误码，且不自动重试写操作', async () => {
  let writes = 0
  const client = createApiClient({
    fetchImpl: async (url) => {
      if (url.endsWith('/csrf')) return csrf()
      writes += 1
      return Response.json(
        { code: 'TASK_STATE_CONFLICT', message: '该任务已被他人认领', data: null },
        { status: 409 },
      )
    },
  })
  await assert.rejects(
    client.request('/tasks/1/acceptance', { method: 'POST' }),
    (error) =>
      error instanceof ApiError && error.status === 409 && error.code === 'TASK_STATE_CONFLICT',
  )
  assert.equal(writes, 1)
})
test('401 返回登录失效错误；错误响应不能当作空列表', async () => {
  const client = createApiClient({
    fetchImpl: async () =>
      Response.json(
        { code: 'UNAUTHENTICATED', message: '请重新登录', data: null },
        { status: 401 },
      ),
  })
  await assert.rejects(
    client.request('/users/me/tasks'),
    (error) => error.status === 401 && error.message === '请重新登录',
  )
})
test('无效成功响应和 HTML 错误页均显式失败', async () => {
  const badEnvelope = createApiClient({ fetchImpl: async () => Response.json({ items: [] }) })
  await assert.rejects(badEnvelope.request('/tasks'), (error) => error.code === 'INVALID_RESPONSE')
  const html = createApiClient({
    fetchImpl: async () => new Response('<html>error</html>', { status: 502 }),
  })
  await assert.rejects(
    html.request('/tasks'),
    (error) => error.code === 'HTTP_ERROR' && error.status === 502,
  )
})
test('网络错误明确提示，不伪造操作成功', async () => {
  const client = createApiClient({
    fetchImpl: async () => {
      throw new TypeError('fetch failed')
    },
  })
  await assert.rejects(client.request('/wallet'), (error) => error.code === 'NETWORK_ERROR')
})
test('已取消的查询不会继续发送', async () => {
  const controller = new AbortController()
  controller.abort()
  let called = false
  const client = createApiClient({
    fetchImpl: async () => {
      called = true
      return ok([])
    },
  })
  await assert.rejects(
    client.request('/tasks', { signal: controller.signal }),
    (error) => error.name === 'AbortError',
  )
  assert.equal(called, false)
})

test('A 的登录、注册、退出均解包统一响应，并为后续写操作重新获取 CSRF', async () => {
  let tokens = 0
  const calls = []
  const user = { userId: 'u1', username: 'student', displayName: '同学', role: 'USER' }
  const client = createApiClient({
    fetchImpl: async (url, options) => {
      calls.push({ url, ...options })
      if (url.endsWith('/csrf'))
        return Response.json({ headerName: 'X-XSRF-TOKEN', token: `token-${++tokens}` })
      if (url.endsWith('/register'))
        return Response.json({ code: 'OK', message: 'success', data: user }, { status: 201 })
      return ok(url.endsWith('/login') ? user : null)
    },
  })
  const registration = { username: 'student', password: 'fixture-password', displayName: '同学' }
  assert.equal(
    (await client.request('/auth/register', { method: 'POST', body: registration })).userId,
    'u1',
  )
  await client.request('/tasks/1/delivery', { method: 'POST' })
  assert.equal(tokens, 2)
  await client.request('/auth/logout', { method: 'POST' })
  await client.request('/auth/login', {
    method: 'POST',
    body: { username: 'student', password: 'fixture-password' },
  })
  await client.request('/tasks/2/completion', { method: 'POST' })
  assert.equal(tokens, 4)
  assert.ok(calls.every((call) => call.credentials === 'include'))
  assert.deepEqual(
    JSON.parse(calls.find((call) => call.url.endsWith('/register')).body),
    registration,
  )
  assert.equal(
    calls.find((call) => call.url.endsWith('/completion')).headers['X-XSRF-TOKEN'],
    'token-4',
  )
})

test('无 JSON 正文的 401 仍通知 A 的统一会话失效处理', async (t) => {
  const original = globalThis.dispatchEvent
  const events = []
  globalThis.dispatchEvent = (event) => {
    events.push(event.type)
    return true
  }
  t.after(() => {
    if (original) globalThis.dispatchEvent = original
    else delete globalThis.dispatchEvent
  })
  const client = createApiClient({ fetchImpl: async () => new Response(null, { status: 401 }) })
  await assert.rejects(client.request('/wallet'), (error) => error.status === 401)
  assert.deepEqual(events, ['campus:auth-expired'])
})

test('登录失败和匿名会话探测不会触发业务会话失效事件', async (t) => {
  const original = globalThis.dispatchEvent
  const events = []
  globalThis.dispatchEvent = (event) => {
    events.push(event.type)
    return true
  }
  t.after(() => {
    if (original) globalThis.dispatchEvent = original
    else delete globalThis.dispatchEvent
  })
  const client = createApiClient({
    fetchImpl: async (url) =>
      url.endsWith('/csrf') ? csrf() : new Response(null, { status: 401 }),
  })
  await assert.rejects(client.request('/auth/me'), (error) => error.status === 401)
  await assert.rejects(
    client.request('/auth/login', {
      method: 'POST',
      body: { username: 'wrong', password: 'fixture-password' },
    }),
    (error) => error.status === 401,
  )
  assert.deepEqual(events, [])
})

test('旧账号请求的延迟 401 不能注销刚完成登录的新账号', async (t) => {
  const original = globalThis.dispatchEvent
  const events = []
  globalThis.dispatchEvent = (event) => {
    events.push(event.type)
    return true
  }
  t.after(() => {
    if (original) globalThis.dispatchEvent = original
    else delete globalThis.dispatchEvent
  })
  let respond
  const old = new Promise((resolve) => {
    respond = resolve
  })
  const client = createApiClient({
    fetchImpl: async (url) => {
      if (url.endsWith('/wallet')) return old
      return url.endsWith('/csrf') ? csrf() : ok({ userId: 'new-user' })
    },
  })
  const pending = client.request('/wallet')
  await client.request('/auth/login', {
    method: 'POST',
    body: { username: 'new-user', password: 'fixture-password' },
  })
  respond(new Response(null, { status: 401 }))
  await assert.rejects(pending, (error) => error.status === 401)
  assert.deepEqual(events, [])
})
