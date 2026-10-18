import { ApiError, client } from './client.js'

const id = (value) => encodeURIComponent(String(value))
const get = (path, query, signal) => client.request(path, { query, signal })
const post = (path, body) => client.request(path, { method: 'POST', body })
async function page(path, query, signal) {
  const data = await get(path, query, signal)
  if (!Array.isArray(data?.items) || !Number.isSafeInteger(data.total) || data.total < 0) {
    throw new ApiError('列表响应格式不正确，请稍后重试。', 0, 'INVALID_RESPONSE')
  }
  return data
}

export const api = {
  me: async () => {
    const user = await get('/auth/me')
    if (!user?.userId || !['USER', 'ADMIN'].includes(user.role))
      throw new ApiError('无法识别当前登录用户，请重新登录。', 0, 'INVALID_RESPONSE')
    return user
  },
  myTasks: (query, signal) => page('/users/me/tasks', query, signal),
  tasks: (query, signal) => page('/tasks', query, signal),
  task: (taskId, signal) => get(`/tasks/${id(taskId)}`, undefined, signal),
  accept: (taskId) => post(`/tasks/${id(taskId)}/acceptance`),
  deliver: (taskId) => post(`/tasks/${id(taskId)}/delivery`),
  complete: (taskId) => post(`/tasks/${id(taskId)}/completion`),
  wallet: (signal) => get('/wallet', undefined, signal),
  transactions: (query, signal) => page('/wallet/transactions', query, signal),
  notifications: (query, signal) => page('/notifications', query, signal),
  readNotification: (notificationId) => post(`/notifications/${id(notificationId)}/read`),
  cancel: (taskId, body) => post(`/tasks/${id(taskId)}/cancellation-requests`, body),
  respondCancellation: (requestId, body) =>
    post(`/cancellation-requests/${id(requestId)}/response`, body),
  appeal: (taskId, body) => post(`/tasks/${id(taskId)}/appeals`, body),
  complain: (body) => post('/complaints', body),
  adminList: (resource, query, signal) => page(`/admin/${resource}`, query, signal),
  arbitrate: (requestId, body) =>
    post(`/admin/cancellation-requests/${id(requestId)}/arbitration`, body),
  decideAppeal: (appealId, body) => post(`/admin/appeals/${id(appealId)}/decision`, body),
  handleComplaint: (complaintId, body) =>
    post(`/admin/complaints/${id(complaintId)}/actions`, body),
}
