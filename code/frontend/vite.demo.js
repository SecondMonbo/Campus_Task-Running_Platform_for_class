import { randomBytes, randomUUID, scryptSync, timingSafeEqual } from 'node:crypto'

const PREFIX = '/api/v1'
const SESSION_COOKIE = 'CAMPUS_DEMO_SESSION'

function hashPassword(password, salt = randomBytes(16).toString('hex')) {
  return { salt, hash: scryptSync(password, salt, 32).toString('hex') }
}

function readCookies(request) {
  return Object.fromEntries(
    (request.headers.cookie ?? '')
      .split(';')
      .map((part) => part.trim())
      .filter(Boolean)
      .map((part) => {
        const separator = part.indexOf('=')
        return [part.slice(0, separator), decodeURIComponent(part.slice(separator + 1))]
      }),
  )
}

async function readJson(request) {
  const chunks = []
  let length = 0
  for await (const chunk of request) {
    length += chunk.length
    if (length > 1024 * 1024) throw Object.assign(new Error('请求内容过大。'), { status: 413 })
    chunks.push(chunk)
  }
  if (!length) return {}
  try {
    return JSON.parse(Buffer.concat(chunks).toString('utf8'))
  } catch {
    throw Object.assign(new Error('请求内容不是有效的 JSON。'), { status: 400 })
  }
}

function send(response, status, payload, headers = {}) {
  response.statusCode = status
  response.setHeader('Content-Type', 'application/json; charset=utf-8')
  response.setHeader('Cache-Control', 'no-store')
  for (const [name, value] of Object.entries(headers)) response.setHeader(name, value)
  response.end(JSON.stringify(payload))
}

function success(response, data, status = 200, headers = {}) {
  send(response, status, { code: 'OK', message: 'success', data }, headers)
}

function failure(response, status, code, message) {
  send(response, status, { code, message, data: null })
}

function makePage(items, page, size) {
  const offset = page * size
  return { items: items.slice(offset, offset + size), page, size, total: items.length }
}

export function statefulDemoApi() {
  const users = new Map()
  const sessions = new Map()
  const tasks = new Map()
  const csrfToken = randomBytes(24).toString('hex')
  const now = Date.now()
  const examples = [
    ['demo-task-1', '代取图书馆资料', '从图书馆一楼服务台领取预订资料。', 20, '图书馆一楼服务台', '东区 3 号宿舍', 'WAITING_FOR_TAKER', null],
    ['demo-task-2', '帮忙带一份打印文件', '文件已打印好，请送到实验楼门口。', 15, '文印中心', '实验楼 A 座门口', 'IN_PROGRESS', 'demo-runner-1'],
    ['demo-task-3', '代取快递', '凭取件码领取一个小包裹。', 25, '校园快递站', '学生中心大厅', 'WAITING_FOR_ACCEPTANCE', 'demo-runner-2'],
    ['demo-task-4', '送一份课程资料', '把课程资料送到教学楼自习区。', 18, '东区宿舍 5 栋', '教学楼 B 座', 'COMPLETED', 'demo-runner-3'],
    ['demo-task-5', '帮忙取一杯咖啡', '临时有事无法取餐，任务已取消。', 12, '校园咖啡店', '图书馆门口', 'CANCELLED', null],
  ]
  for (const [taskId, title, description, reward, pickupLocation, deliveryLocation, status, runnerId] of examples) {
    tasks.set(taskId, {
      taskId,
      title,
      description,
      reward,
      deadline: new Date(now + 24 * 60 * 60 * 1000).toISOString(),
      pickupLocation,
      deliveryLocation,
      status,
      publisherId: 'demo-publisher',
      runnerId,
      createdAt: new Date(now - 20 * 60 * 1000).toISOString(),
    })
  }

  function getUser(request) {
    const userId = sessions.get(readCookies(request)[SESSION_COOKIE])
    return userId ? users.get(userId) : undefined
  }

  function publicUser(user) {
    return {
      userId: user.userId,
      username: user.username,
      displayName: user.displayName,
      phone: user.phone ?? null,
      role: 'USER',
    }
  }

  function requireUser(request, response) {
    const user = getUser(request)
    if (!user) failure(response, 401, 'UNAUTHENTICATED', '登录已失效，请重新登录。')
    return user
  }

  async function handle(request, response, next) {
    const url = new URL(request.url ?? '/', 'http://localhost')
    const { pathname, searchParams } = url
    if (!pathname.startsWith(`${PREFIX}/`)) return next()

    const method = request.method ?? 'GET'
    const path = pathname.slice(PREFIX.length)
    const cookies = readCookies(request)
    if (path === '/auth/csrf' && method === 'GET') {
      send(response, 200, { headerName: 'X-XSRF-TOKEN', parameterName: '_csrf', token: csrfToken }, {
        'Set-Cookie': `XSRF-TOKEN=${csrfToken}; Path=/; SameSite=Lax`,
      })
      return
    }

    if (!['GET', 'HEAD', 'OPTIONS'].includes(method) && request.headers['x-xsrf-token'] !== csrfToken) {
      failure(response, 403, 'CSRF_INVALID', '安全校验已失效，请刷新页面后重试。')
      return
    }

    let body = {}
    if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
      try {
        body = await readJson(request)
      } catch (error) {
        failure(response, error.status ?? 400, 'VALIDATION_ERROR', error.message)
        return
      }
    }

    if (path === '/auth/register' && method === 'POST') {
      const username = typeof body.username === 'string' ? body.username.trim() : ''
      const displayName = typeof body.displayName === 'string' ? body.displayName.trim() : ''
      const password = typeof body.password === 'string' ? body.password : ''
      if (username.length < 3 || username.length > 50 || displayName.length < 1 || displayName.length > 50 || password.length < 8 || password.length > 128) {
        failure(response, 400, 'VALIDATION_ERROR', '请检查用户名、显示名称和密码长度。')
        return
      }
      if ([...users.values()].some((user) => user.username.toLowerCase() === username.toLowerCase())) {
        failure(response, 409, 'USERNAME_TAKEN', '该用户名已注册，请直接登录或更换用户名。')
        return
      }
      const credentials = hashPassword(password)
      const user = {
        userId: randomUUID(), username, displayName,
        phone: typeof body.phone === 'string' && body.phone.trim() ? body.phone.trim() : null,
        passwordSalt: credentials.salt, passwordHash: credentials.hash,
      }
      users.set(user.userId, user)
      const sessionId = randomUUID()
      sessions.set(sessionId, user.userId)
      success(response, publicUser(user), 201, {
        'Set-Cookie': `${SESSION_COOKIE}=${sessionId}; Path=/; HttpOnly; SameSite=Lax`,
      })
      return
    }

    if (path === '/auth/login' && method === 'POST') {
      const username = typeof body.username === 'string' ? body.username.trim() : ''
      const user = [...users.values()].find((entry) => entry.username.toLowerCase() === username.toLowerCase())
      const actual = user && typeof body.password === 'string' ? scryptSync(body.password, user.passwordSalt, 32) : null
      const expected = user ? Buffer.from(user.passwordHash, 'hex') : null
      if (!user || !actual || actual.length !== expected.length || !timingSafeEqual(actual, expected)) {
        failure(response, 401, 'INVALID_CREDENTIALS', '用户名或密码不正确。请确认已在此演示环境注册。')
        return
      }
      const sessionId = randomUUID()
      sessions.set(sessionId, user.userId)
      success(response, publicUser(user), 200, {
        'Set-Cookie': `${SESSION_COOKIE}=${sessionId}; Path=/; HttpOnly; SameSite=Lax`,
      })
      return
    }

    if (path === '/auth/logout' && method === 'POST') {
      const sessionId = cookies[SESSION_COOKIE]
      if (!getUser(request)) {
        failure(response, 401, 'UNAUTHENTICATED', '登录已失效，请重新登录。')
        return
      }
      sessions.delete(sessionId)
      success(response, null, 200, {
        'Set-Cookie': `${SESSION_COOKIE}=; Path=/; HttpOnly; SameSite=Lax; Max-Age=0`,
      })
      return
    }

    if (path === '/auth/me' && method === 'GET') {
      const user = requireUser(request, response)
      if (user) success(response, publicUser(user))
      return
    }

    const user = requireUser(request, response)
    if (!user) return

    if (path === '/tasks' && method === 'GET') {
      const page = Number(searchParams.get('page') ?? 0)
      const size = Number(searchParams.get('size') ?? 10)
      if (!Number.isSafeInteger(page) || page < 0 || ![10, 20, 50].includes(size)) {
        failure(response, 400, 'VALIDATION_ERROR', '分页参数无效。')
        return
      }
      const status = searchParams.get('status')
      const keyword = (searchParams.get('keyword') ?? '').trim().toLocaleLowerCase()
      const items = [...tasks.values()]
        .filter((task) => !status || task.status === status)
        .filter((task) => !keyword || [task.title, task.description, task.pickupLocation, task.deliveryLocation].some((value) => value.toLocaleLowerCase().includes(keyword)))
        .sort((left, right) => right.createdAt.localeCompare(left.createdAt))
      success(response, makePage(items, page, size))
      return
    }

    failure(response, 404, 'ENDPOINT_NOT_FOUND', '前端 A 本地演示服务未实现此接口。')
  }

  return {
    name: 'campus-task-frontend-a-demo-api',
    configureServer(server) {
      server.middlewares.use((request, response, next) => {
        void handle(request, response, next).catch((error) => {
          if (!response.headersSent) failure(response, 500, 'DEMO_SERVER_ERROR', '本地演示服务发生错误。')
          else response.end()
          server.config.logger.error(error)
        })
      })
    },
  }
}
