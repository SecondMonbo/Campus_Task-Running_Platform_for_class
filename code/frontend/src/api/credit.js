import { ApiError } from './client.js'
import { isCreditReceipt, isCreditRecord, isCreditReviewRequest, isCreditSummary } from '../domain/credit.js'

const id = (value) => encodeURIComponent(String(value))
const invalid = () => new ApiError('信用服务返回的信息不完整，请刷新后核对结果。', 0, 'INVALID_RESPONSE')

export function createCreditApi(client) {
  async function summary(path, signal, expectedUserId) {
    const value = await client.request(path, { signal })
    if (!isCreditSummary(value) || (expectedUserId && value.userId !== expectedUserId)) throw invalid()
    return value
  }
  async function page(path, query, signal, validate) {
    const value = await client.request(path, { query, signal })
    if (!value || !Array.isArray(value.items) || !value.items.every(validate) ||
      !Number.isSafeInteger(value.total) || value.total < 0 ||
      !Number.isInteger(value.page) || value.page < 0 || ![10, 20, 50].includes(value.size) ||
      value.page !== (query?.page ?? 0) || value.size !== (query?.size ?? 10)) throw invalid()
    return value
  }
  async function operation(path, body, key, userId, identifiers = {}) {
    const value = await client.request(path, { method: 'POST', body, headers: { 'Idempotency-Key': key } })
    if (!isCreditReceipt(value, userId) || Object.entries(identifiers).some(([field, expected]) => value[field] !== expected))
      throw invalid()
    if ((body.adjustment !== undefined && value.requestedAdjustment !== body.adjustment) ||
      (body.result === 'UPHELD' && value.requestedAdjustment !== 0)) throw invalid()
    return value
  }
  return {
    myCredit: (signal, userId) => summary('/users/me/credit', signal, userId),
    adminCredits: (query, signal) => page('/admin/credits', query, signal, isCreditSummary),
    adminCredit: (userId, signal) => summary(`/admin/credits/${id(userId)}`, signal, userId),
    adminCreditRecords: (userId, query, signal) => page(`/admin/credits/${id(userId)}/records`, query, signal,
      (value) => isCreditRecord(value) && value.userId === userId),
    adjustCredit: (userId, body, key) => operation(`/admin/credits/${id(userId)}/adjustments`, body, key, userId),
    reviewComplaintCredit: (complaintId, userId, body, key) => operation(
      `/admin/complaints/${id(complaintId)}/credit-reviews`, body, key, userId, { complaintId }),
    decideCreditReview: (reviewId, userId, body, key) => operation(
      `/admin/credit-reviews/${id(reviewId)}/decision`, body, key, userId, { reviewId }),
    async requestCreditReview(body, key, userId) {
      const value = await client.request('/users/me/credit-reviews', {
        method: 'POST', body, headers: { 'Idempotency-Key': key },
      })
      if (!isCreditReviewRequest(value, userId) || value.status !== 'PENDING') throw invalid()
      return value
    },
  }
}
