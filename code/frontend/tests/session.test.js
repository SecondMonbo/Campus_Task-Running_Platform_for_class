import test from 'node:test'
import assert from 'node:assert/strict'
import { createSessionStore } from '../src/composables/session.js'
import { safeDestination } from '../src/domain/auth.js'

const user = { userId: 'u1', username: 'student', displayName: '同学', role: 'USER' }
function deferred() {
  let resolve, reject
  const promise = new Promise((ok, fail) => {
    resolve = ok
    reject = fail
  })
  return { promise, resolve, reject }
}

test('按 A 的约定共享一次 /auth/me 请求，并缓存已登录用户', async () => {
  const request = deferred()
  let count = 0
  const store = createSessionStore(() => {
    count += 1
    return request.promise
  })
  const first = store.loadSession()
  const second = store.loadSession()
  assert.equal(first, second)
  request.resolve(user)
  assert.equal((await first).userId, user.userId)
  assert.equal((await store.loadSession()).userId, user.userId)
  assert.equal(count, 1)
})

test('/auth/me 401 返回 null；网络故障不能伪装成未登录', async () => {
  const anonymous = createSessionStore(async () => {
    throw Object.assign(new Error('未登录'), { status: 401 })
  })
  assert.equal(await anonymous.loadSession(), null)
  assert.equal(anonymous.session.value, null)
  const unavailable = createSessionStore(async () => {
    throw Object.assign(new Error('网络故障'), { status: 500 })
  })
  await assert.rejects(unavailable.loadSession(), /网络故障/)
})

test('旧会话成功响应不能覆盖新登录用户', async () => {
  const request = deferred()
  const store = createSessionStore(() => request.promise)
  const pending = store.loadSession()
  store.setSession({ ...user, userId: 'u2' })
  request.resolve(user)
  assert.equal((await pending).userId, 'u2')
  assert.equal(store.session.value.userId, 'u2')
})

test('退出期间未完成的会话请求不能重新登录原用户', async () => {
  const request = deferred()
  const store = createSessionStore(() => request.promise)
  const pending = store.loadSession()
  store.clearSession()
  request.resolve(user)
  assert.equal(await pending, null)
  assert.equal(store.session.value, null)
})

test('旧 /auth/me 401 和 finally 不能清空新会话请求', async () => {
  const old = deferred()
  const current = deferred()
  let calls = 0
  const store = createSessionStore(() => (++calls === 1 ? old.promise : current.promise))
  const first = store.loadSession()
  await Promise.resolve()
  store.clearSession()
  const second = store.loadSession()
  await Promise.resolve()
  old.reject(Object.assign(new Error('已过期'), { status: 401 }))
  await first
  assert.equal(store.loadSession(), second)
  current.resolve(user)
  assert.equal((await second).userId, 'u1')
  assert.equal(calls, 2)
})

test('登录与注册沿用 A 的站内返回路径，拒绝外站和认证循环', () => {
  assert.equal(safeDestination('/wallet?type=INCOME#history'), '/wallet?type=INCOME#history')
  for (const value of [
    undefined,
    ['/', '/admin'],
    'https://example.com',
    '//example.com',
    '/\\example.com',
    '/%2fexample.com',
    '/login',
    '/LOGIN/',
    '/register?redirect=/wallet',
    '/regist%65r/',
    '/bad%ZZ',
  ]) {
    assert.equal(safeDestination(value), '/tasks')
  }
})
