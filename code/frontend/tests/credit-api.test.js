import test from 'node:test'
import assert from 'node:assert/strict'
import { createApiClient } from '../src/api/client.js'
import { createCreditApi } from '../src/api/credit.js'

const now = '2026-11-25T00:00:00Z'
const summary = { userId: '1', creditScore: 80, creditLevel: 'GOOD', restrictionActive: false,
  canAcceptNormalTasks: true, requiresPublishReview: false, consecutiveCompletedCount: 0, updatedAt: now }
const receipt = { eventId: 'e1', userId: '1', status: 'APPLIED', creditRecordId: 'r1', requestedAdjustment: 5,
  actualAdjustment: 1, scoreBefore: 99, scoreAfter: 100 }
const envelope = (data) => Response.json({ code: 'OK', message: 'success', data })
function setup(reply) {
  const calls = []
  const client = createApiClient({ fetchImpl: async (url, options) => {
    calls.push({ url, ...options })
    if (url.endsWith('/auth/csrf')) return Response.json({ headerName: 'X-XSRF-TOKEN', token: 'test-csrf' })
    return reply(url, options)
  } })
  return { api: createCreditApi(client), calls }
}

test('管理员信用查询发送真实分页和字符串用户编号，服务端空列表保持成功', async () => {
  const { api, calls } = setup(() => envelope({ items: [], total: 0, page: 0, size: 20 }))
  const result = await api.adminCredits({ page: 0, size: 20, userId: '9223372036854775807', reviewPending: false })
  assert.deepEqual(result.items, [])
  const url = new URL(calls[0].url, 'http://test.local')
  assert.equal(url.searchParams.get('userId'), '9223372036854775807')
  assert.equal(url.searchParams.get('reviewPending'), 'false')
  assert.equal(url.searchParams.get('page'), '0')
  assert.equal(calls[0].credentials, 'include')
})

test('信用摘要错误或查询到另一用户时显式失败，不能默认为初始分数', async () => {
  const { api } = setup(() => envelope({ ...summary, creditScore: null }))
  await assert.rejects(api.myCredit(undefined, '1'), (error) => error.code === 'INVALID_RESPONSE')
  const other = setup(() => envelope({ ...summary, userId: '2' })).api
  await assert.rejects(other.adminCredit('1'), (error) => error.code === 'INVALID_RESPONSE')
})

test('分页响应的页码、条数、总数和信用记录用户不匹配时不能作为成功显示', async () => {
  for (const response of [{ items: [], total: '0', page: 0, size: 10 },
    { items: [], total: 0, page: 1, size: 10 }, { items: [], total: 0, page: 0, size: 15 },
    { items: [{ ...summary, creditScore: 101 }], total: 1, page: 0, size: 10 }]) {
    await assert.rejects(setup(() => envelope(response)).api.adminCredits({ page: 0, size: 10 }),
      (error) => error.code === 'INVALID_RESPONSE')
  }
  const record = { recordId: 'r1', userId: '2', eventType: 'INITIALIZATION', eventId: 'u2', reason: '初始化',
    requestedAdjustment: 0, actualAdjustment: 0, scoreBefore: null, scoreAfter: 80, createdAt: now }
  await assert.rejects(setup(() => envelope({ items: [record], total: 1, page: 0, size: 10 })).api
    .adminCreditRecords('1', { page: 0, size: 10 }), (error) => error.code === 'INVALID_RESPONSE')
})

test('管理员调整沿用 CSRF 与 Cookie，并传递幂等键，不提交操作者和角色', async () => {
  const { api, calls } = setup(() => envelope(receipt))
  const body = { adjustment: 5, reason: '事实依据' }
  const key = '00000000-0000-4000-8000-000000000001'
  await api.adjustCredit('1', body, key)
  const write = calls.find((call) => call.method === 'POST')
  assert.equal(write.url, '/api/v1/admin/credits/1/adjustments')
  assert.deepEqual(JSON.parse(write.body), body)
  assert.equal(write.headers['X-XSRF-TOKEN'], 'test-csrf')
  assert.equal(write.headers['Idempotency-Key'], key)
  assert.equal(write.credentials, 'include')
})

test('投诉复核确认来源与目标，维持原处理不能收到非零调整的成功回执', async () => {
  const valid = setup(() => envelope({ ...receipt, complaintId: 'c1' })).api
  await valid.reviewComplaintCredit('c1', '1', { result: 'ADJUSTED', adjustment: 5, decisionNote: '依据' }, 'key')
  await assert.rejects(valid.reviewComplaintCredit('c2', '1', { result: 'ADJUSTED', adjustment: 5, decisionNote: '依据' }, 'key'),
    (error) => error.code === 'INVALID_RESPONSE')
  await assert.rejects(valid.reviewComplaintCredit('c1', '1', { result: 'UPHELD', decisionNote: '依据' }, 'key'),
    (error) => error.code === 'INVALID_RESPONSE')
})

test('复核申请与裁决回执必须属于当前用户和申请，错误回执不模拟成功', async () => {
  const { api } = setup(() => envelope({ reviewId: 'review-1', userId: '1', status: 'PENDING', reason: '复核依据', createdAt: now }))
  assert.equal((await api.requestCreditReview({ reason: '复核依据' }, 'key', '1')).status, 'PENDING')
  await assert.rejects(api.requestCreditReview({ reason: '复核依据' }, 'key', '2'), (error) => error.code === 'INVALID_RESPONSE')
  const decision = setup(() => envelope({ ...receipt, reviewId: 'review-1' })).api
  await assert.rejects(decision.decideCreditReview('review-2', '1', { result: 'ADJUSTED', adjustment: 5, decisionNote: '依据' }, 'key'),
    (error) => error.code === 'INVALID_RESPONSE')
})

test('查询失败不转成空列表，写操作 409 或网络失败不自动重试', async () => {
  const { api, calls } = setup(() => Response.json({ code: 'DUPLICATE_ACTION', message: '申请已处理', data: null }, { status: 409 }))
  await assert.rejects(api.adjustCredit('1', { adjustment: 5, reason: '依据' }, 'key'), (error) => error.status === 409)
  assert.equal(calls.filter((call) => call.method === 'POST').length, 1)
  const missing = setup(() => Response.json({ code: 'RESOURCE_NOT_FOUND', message: '信用服务未接入', data: null }, { status: 404 })).api
  await assert.rejects(missing.adminCredits({ page: 0, size: 10 }), (error) => error.status === 404)
})
