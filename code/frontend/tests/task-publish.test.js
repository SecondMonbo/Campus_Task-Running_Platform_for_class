import test from 'node:test'
import assert from 'node:assert/strict'
import { validateTaskDraft } from '../src/domain/tasks.js'

const now = Date.parse('2026-10-30T10:00:00Z')
const future = '2026-10-31T10:00:00Z'
const past = '2026-10-29T10:00:00Z'

const validDraft = {
  title: '代取图书馆预订资料',
  description: '到图书馆一楼服务台取一份预订资料，送到东区 3 号宿舍。',
  reward: 30,
  deadline: future,
  pickupLocation: '图书馆一楼服务台',
  deliveryLocation: '东区 3 号宿舍',
}

const draftWith = (overrides = {}) => ({ ...validDraft, ...overrides })

test('合法草稿返回空字符串', () => {
  assert.equal(validateTaskDraft(validDraft, now), '')
})

test('标题为空或超长时分别报错', () => {
  assert.equal(validateTaskDraft(draftWith({ title: '' }), now), '请填写任务标题。')
  assert.equal(validateTaskDraft(draftWith({ title: '   ' }), now), '请填写任务标题。')
  assert.equal(validateTaskDraft(draftWith({ title: null }), now), '请填写任务标题。')
  assert.equal(validateTaskDraft(draftWith({ title: 'a'.repeat(100) }), now), '')
  assert.equal(validateTaskDraft(draftWith({ title: 'a'.repeat(101) }), now), '标题不能超过 100 个字符。')
})

test('任务说明为空或超长时分别报错', () => {
  assert.equal(validateTaskDraft(draftWith({ description: '' }), now), '请填写任务说明。')
  assert.equal(validateTaskDraft(draftWith({ description: '   ' }), now), '请填写任务说明。')
  assert.equal(validateTaskDraft(draftWith({ description: 'a'.repeat(2000) }), now), '')
  assert.equal(
    validateTaskDraft(draftWith({ description: 'a'.repeat(2001) }), now),
    '任务说明不能超过 2000 个字符。',
  )
})

test('取件地点为空或超长时分别报错', () => {
  assert.equal(validateTaskDraft(draftWith({ pickupLocation: '' }), now), '请填写取件地点。')
  assert.equal(validateTaskDraft(draftWith({ pickupLocation: '   ' }), now), '请填写取件地点。')
  assert.equal(validateTaskDraft(draftWith({ pickupLocation: 'a'.repeat(200) }), now), '')
  assert.equal(
    validateTaskDraft(draftWith({ pickupLocation: 'a'.repeat(201) }), now),
    '取件地点不能超过 200 个字符。',
  )
})

test('送件地点为空或超长时分别报错', () => {
  assert.equal(validateTaskDraft(draftWith({ deliveryLocation: '' }), now), '请填写送件地点。')
  assert.equal(validateTaskDraft(draftWith({ deliveryLocation: '   ' }), now), '请填写送件地点。')
  assert.equal(validateTaskDraft(draftWith({ deliveryLocation: 'a'.repeat(200) }), now), '')
  assert.equal(
    validateTaskDraft(draftWith({ deliveryLocation: 'a'.repeat(201) }), now),
    '送件地点不能超过 200 个字符。',
  )
})

test('报酬必须是 1 到 1000 之间的整数', () => {
  const message = '报酬必须是 1 到 1000 之间的整数。'
  assert.equal(validateTaskDraft(draftWith({ reward: 0 }), now), message)
  assert.equal(validateTaskDraft(draftWith({ reward: 1001 }), now), message)
  assert.equal(validateTaskDraft(draftWith({ reward: 1.5 }), now), message)
  assert.equal(validateTaskDraft(draftWith({ reward: '30' }), now), message)
  assert.equal(validateTaskDraft(draftWith({ reward: Number.NaN }), now), message)
  assert.equal(validateTaskDraft(draftWith({ reward: undefined }), now), message)
  assert.equal(validateTaskDraft(draftWith({ reward: 1 }), now), '')
  assert.equal(validateTaskDraft(draftWith({ reward: 1000 }), now), '')
})

test('截止时间必须可解析且晚于当前时间', () => {
  assert.equal(validateTaskDraft(draftWith({ deadline: past }), now), '截止时间必须晚于当前时间。')
  assert.equal(validateTaskDraft(draftWith({ deadline: 'not-a-date' }), now), '请选择有效的截止时间。')
  assert.equal(validateTaskDraft(draftWith({ deadline: '' }), now), '请选择有效的截止时间。')
  assert.equal(validateTaskDraft(draftWith({ deadline: null }), now), '请选择有效的截止时间。')
  assert.equal(validateTaskDraft(draftWith({ deadline: future }), now), '')
})
