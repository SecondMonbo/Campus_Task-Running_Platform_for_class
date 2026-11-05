import test from 'node:test'
import assert from 'node:assert/strict'
import { effectScope, nextTick, ref } from 'vue'
import { usePaged } from '../src/composables/state.js'
import { validateComplaintDecision } from '../src/domain/admin.js'

const settle = async () => {
  await nextTick()
  await new Promise((resolve) => setImmediate(resolve))
  await nextTick()
}

test('投诉处理只接受已开放的留痕方式，并校验说明', () => {
  assert.equal(validateComplaintDecision({ actions: ['LOG_ONLY', 'WARNING'], decisionNote: '处理依据' }), '')
  for (const body of [
    { actions: [], decisionNote: '说明' },
    { actions: ['LOG_ONLY'], decisionNote: '   ' },
    { actions: ['LOG_ONLY'], decisionNote: '字'.repeat(1001) },
    { actions: ['LOG_ONLY', 'LOG_ONLY'], decisionNote: '说明' },
    ...['CREDIT_ADJUSTMENT', 'FINANCIAL_ADJUSTMENT', 'FUNCTION_RESTRICTION', 'ACCOUNT_FREEZE'].map(
      (action) => ({ actions: [action], decisionNote: '说明' }),
    ),
  ]) assert.ok(validateComplaintDecision(body))
})

test('管理列表分页从 0/10 开始，切换筛选和每页条数返回首页', async () => {
  const calls = []
  const filter = ref('PENDING')
  const scope = effectScope()
  const list = scope.run(() => usePaged(async (query) => {
    calls.push({ ...query, status: filter.value })
    return { ...query, items: [{}], total: 100 }
  }, [filter]))
  try {
    await settle()
    assert.deepEqual(calls.at(-1), { page: 0, size: 10, status: 'PENDING' })
    list.page.value = 2
    await settle()
    filter.value = 'PROCESSED'
    await settle()
    assert.deepEqual(calls.at(-1), { page: 0, size: 10, status: 'PROCESSED' })
    list.page.value = 1
    await settle()
    list.size.value = 50
    await settle()
    assert.deepEqual(calls.at(-1), { page: 0, size: 50, status: 'PROCESSED' })
  } finally { scope.stop() }
})

test('处理后末页消失时请求最后有效页，空列表保持成功', async () => {
  let total = 11
  const calls = []
  const scope = effectScope()
  const list = scope.run(() => usePaged(async (query) => {
    calls.push(query.page)
    const remaining = Math.max(0, Math.min(query.size, total - query.page * query.size))
    return { ...query, items: Array.from({ length: remaining }, () => ({})), total }
  }))
  try {
    await settle()
    list.page.value = 1
    await settle()
    assert.equal(list.items.value.length, 1)
    total = 10
    list.reload()
    await settle()
    assert.equal(list.page.value, 0)
    assert.equal(calls.at(-1), 0)
    assert.equal(list.items.value.length, 10)
    total = 0
    list.reload()
    await settle()
    assert.deepEqual(list.items.value, [])
    assert.equal(list.error.value, null)
  } finally { scope.stop() }
})

test('切换管理查询会取消旧请求，延迟响应不能覆盖新筛选', async () => {
  const filter = ref('first')
  const pending = []
  const scope = effectScope()
  const list = scope.run(() => usePaged((query, signal) => new Promise((resolve) => {
    pending.push({ query, signal, resolve })
  }), [filter]))
  try {
    await nextTick()
    filter.value = 'second'
    await nextTick()
    assert.equal(pending[0].signal.aborted, true)
    pending[1].resolve({ page: 0, size: 10, items: [{ id: 'current' }], total: 1 })
    await settle()
    pending[0].resolve({ page: 0, size: 10, items: [{ id: 'stale' }], total: 1 })
    await settle()
    assert.equal(list.items.value[0].id, 'current')
  } finally { scope.stop() }
})
