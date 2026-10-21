import { ApiError, client } from './client.js'

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
  register: (body) => post('/auth/register', body),
  login: (body) => post('/auth/login', body),
  logout: () => post('/auth/logout'),
  me: async () => {
    const user = await get('/auth/me')
    if (!user?.userId || !['USER', 'ADMIN'].includes(user.role))
      throw new ApiError('无法识别当前登录用户，请重新登录。', 0, 'INVALID_RESPONSE')
    return user
  },
  tasks: (query, signal) => page('/tasks', query, signal),
}
