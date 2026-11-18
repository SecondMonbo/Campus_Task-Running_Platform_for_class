import test from 'node:test'
import assert from 'node:assert/strict'
import { creditTierLabel, creditTiers, formatCreditDelta } from '../src/domain/credit.js'

test('信用档位映射与后端 CreditTier 保持一致', () => {
  assert.deepEqual(creditTiers, {
    EXCELLENT: '优秀',
    GOOD: '良好',
    WARNING: '预警',
    RESTRICTED: '受限',
  })
  assert.equal(creditTierLabel('EXCELLENT'), '优秀')
  assert.equal(creditTierLabel('GOOD'), '良好')
  assert.equal(creditTierLabel('WARNING'), '预警')
  assert.equal(creditTierLabel('RESTRICTED'), '受限')
  assert.equal(creditTierLabel('UNKNOWN'), '未知档位')
})

test('信用分变更值格式化：正数带加号，零分与负数原样展示', () => {
  assert.equal(formatCreditDelta(2), '+2')
  assert.equal(formatCreditDelta(-3), '-3')
  assert.equal(formatCreditDelta(0), '0')
  assert.equal(formatCreditDelta(null), '0')
  assert.equal(formatCreditDelta(Number.NaN), '0')
})
