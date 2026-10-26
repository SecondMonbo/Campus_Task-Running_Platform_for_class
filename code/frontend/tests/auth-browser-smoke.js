async function verifySharedAuthentication(page) {
  const baseUrl = new URL(page.url()).origin
  await page.unrouteAll({ behavior: 'wait' })
  await page.context().clearCookies()
  const results = []
  const requests = []
  const runtimeErrors = []
  const check = (value, message) => {
    if (!value) throw new Error(message)
  }
  const record = (message) => results.push(message)
  const onRequest = (request) => {
    const path = new URL(request.url()).pathname
    if (path.startsWith('/api/v1/'))
      requests.push({ path, method: request.method(), body: request.postDataJSON() })
  }
  const onError = (error) => runtimeErrors.push(error.message)
  page.on('request', onRequest)
  page.on('pageerror', onError)
  const username = `integration-${Date.now()}`
  const password = 'fixture-password-2026'
  let currentUser
  const task = {
    taskId: 'demo-task-1',
    title: '代取图书馆资料',
    description: '从图书馆一楼服务台领取预订资料。',
    reward: 20,
    deadline: new Date(Date.now() + 86400000).toISOString(),
    pickupLocation: '图书馆一楼服务台',
    deliveryLocation: '东区 3 号宿舍',
    status: 'WAITING_FOR_TAKER',
    publisherId: 'demo-publisher',
    runnerId: null,
    createdAt: new Date().toISOString(),
  }
  // Authentication and task hall calls go to A's unchanged stateful demo server.
  // Only B's unimplemented demo endpoints receive contract fixtures here.
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const path = new URL(request.url()).pathname.replace('/api/v1', '')
    if (path.startsWith('/auth/') || path === '/tasks') return route.continue()
    const headers = await request.allHeaders()
    const cookie = headers.cookie ?? ''
    if (!cookie.includes('CAMPUS_DEMO_SESSION=')) return route.fulfill({ status: 401, body: '' })
    const respond = (data) =>
      route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({ code: 'OK', message: 'success', data }),
      })
    if (path === '/wallet') return respond({ availableBalance: 88, frozenBalance: 20 })
    if (['/wallet/transactions', '/users/me/tasks', '/notifications'].includes(path))
      return respond({ items: [], page: 0, size: 10, total: 0 })
    if (path === '/tasks/demo-task-1') return respond(task)
    if (path === '/tasks/demo-task-1/acceptance') {
      const token = cookie
        .split(';')
        .map((part) => part.trim())
        .find((part) => part.startsWith('XSRF-TOKEN='))
        ?.slice('XSRF-TOKEN='.length)
      check(
        headers['x-xsrf-token'] === decodeURIComponent(token ?? ''),
        'B 接单必须携带 A 发放的 CSRF Token',
      )
      check(request.postData() === null, 'B 接单不能提交操作者或目标状态')
      task.status = 'IN_PROGRESS'
      task.runnerId = currentUser.userId
      return respond(task)
    }
    return route.continue()
  })
  const go = (path) => page.goto(`${baseUrl}${path}`)
  const login = async (secret = password) => {
    await page.getByLabel('用户名', { exact: true }).fill(username)
    await page.getByLabel('密码', { exact: true }).fill(secret)
    await page.getByRole('button', { name: '登录', exact: true }).click()
  }
  await page.setViewportSize({ width: 1366, height: 900 })
  await go('/wallet')
  await page.getByRole('heading', { name: '欢迎回来', exact: true }).waitFor()
  check(new URL(page.url()).pathname === '/login', '匿名 B 路由必须进入 A 登录页')
  check(new URL(page.url()).searchParams.get('redirect') === '/wallet', '登录页应保留原 B 路径')
  check(
    !requests.some((request) => request.path === '/api/v1/wallet'),
    '未认证前不能加载 B 的受保护数据',
  )
  check(
    !(await page.getByRole('navigation', { name: '主导航' }).count()),
    '登录页不应嵌入 B 的业务侧栏',
  )
  await page.screenshot({ path: 'output/playwright/shared-login-desktop.png', fullPage: true })
  record('匿名 B 页面转入 A 登录页，401 不会造成空白或业务门禁')

  await page.getByRole('link', { name: '立即注册', exact: true }).click()
  await page.getByRole('heading', { name: '创建你的账号', exact: true }).waitFor()
  check(new URL(page.url()).searchParams.get('redirect') === '/wallet', '切换注册必须保留返回路径')
  await page.getByLabel('用户名', { exact: true }).fill(`  ${username}  `)
  await page.getByLabel('显示名称', { exact: true }).fill('联调同学')
  await page.getByLabel('密码', { exact: true }).fill(password)
  await page.getByLabel('确认密码', { exact: true }).fill(password)
  await page.getByRole('button', { name: '创建账号', exact: true }).click()
  await page.getByRole('heading', { name: /钱包与流水/, level: 1 }).waitFor()
  const meResponse = await page.request.get(`${baseUrl}/api/v1/auth/me`)
  check(meResponse.ok(), 'A 的 Session Cookie 应支持 /auth/me')
  currentUser = (await meResponse.json()).data
  check(
    currentUser.username === username &&
      currentUser.displayName === '联调同学' &&
      currentUser.role === 'USER',
    'A 的用户字段必须与共享会话一致',
  )
  const cookies = await page.context().cookies()
  check(
    cookies.some((cookie) => cookie.name === 'CAMPUS_DEMO_SESSION' && cookie.httpOnly),
    '会话必须使用 A 的 HttpOnly Cookie',
  )
  const registration = requests.find((request) => request.path.endsWith('/auth/register')).body
  check(
    JSON.stringify(Object.keys(registration).sort()) ===
      JSON.stringify(['displayName', 'password', 'username']),
    '注册字段不能新增角色、用户编号或确认密码',
  )
  record('真实注册 201、HttpOnly Cookie、用户字段与返回 B 目标页面')

  const meCallsBefore = requests.filter((request) => request.path.endsWith('/auth/me')).length
  await page.getByRole('link', { name: '任务大厅', exact: true }).click()
  await page.getByRole('link', { name: '代取图书馆资料', exact: true }).waitFor()
  await page.getByRole('link', { name: '代取图书馆资料', exact: true }).click()
  await page.getByRole('heading', { name: '代取图书馆资料', exact: true }).waitFor()
  await page.getByRole('button', { name: '接取任务', exact: true }).click()
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await page.getByRole('button', { name: '确认送达', exact: true }).waitFor()
  await page.getByRole('link', { name: '我的任务', exact: true }).click()
  await page.getByRole('heading', { name: /我的任务/, level: 1 }).waitFor()
  check(
    requests.filter((request) => request.path.endsWith('/auth/me')).length === meCallsBefore,
    'A 与 B 导航应共用已加载的会话',
  )
  record('A 任务大厅进入 B 详情，CSRF 与会话复用，导航不重复探测身份')

  await go('/login?redirect=%2Fwallet')
  await page.getByRole('heading', { name: /钱包与流水/, level: 1 }).waitFor()
  check(new URL(page.url()).pathname === '/wallet', '已登录用户不应滞留登录页')
  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await page.getByRole('heading', { name: '欢迎回来', exact: true }).waitFor()
  check(
    !(await page.getByRole('navigation', { name: '主导航' }).count()),
    '退出后不得保留 B 的用户页面',
  )
  const loggedOut = await page.request.get(`${baseUrl}/api/v1/auth/me`)
  check(loggedOut.status() === 401, '退出后 /auth/me 必须未认证')
  record('已登录认证路由重定向，真实退出清除会话和业务视图')

  await go('/login?redirect=%2Fwallet')
  await page.getByRole('heading', { name: '欢迎回来', exact: true }).waitFor()
  await login('incorrect-fixture-password')
  await page.locator('.form-error').getByText('用户名或密码不正确', { exact: false }).waitFor()
  check(new URL(page.url()).pathname === '/login', '错误凭据应留在 A 的表单')
  check(
    (await page.getByLabel('用户名', { exact: true }).inputValue()) === username,
    '登录失败应保留已填用户名',
  )
  check(
    !(await page.locator('.toast.error').getByText('登录已过期', { exact: false }).count()),
    '错误密码不得误触发业务会话失效事件',
  )
  await login()
  await page.getByRole('heading', { name: /钱包与流水/, level: 1 }).waitFor()
  record('真实登录失败保留表单，正确登录后返回 B 页面')

  await page.context().clearCookies()
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await page.getByRole('heading', { name: '欢迎回来', exact: true }).waitFor()
  check(
    new URL(page.url()).searchParams.get('redirect') === '/wallet',
    '业务空正文 401 应带原路径返回 A 登录页',
  )
  check(!(await page.locator('.balance-card').count()), '会话过期后不能保留旧余额')
  await login()
  await page.getByRole('heading', { name: /钱包与流水/, level: 1 }).waitFor()
  record('业务空正文 401 触发 A 的失效事件，清除旧数据并可重新登录')

  await go('/admin')
  await page.getByRole('heading', { name: /我的任务/, level: 1 }).waitFor()
  check(new URL(page.url()).pathname === '/my-tasks', 'USER 不能进入管理员路由')
  await go('/login?redirect=https%3A%2F%2Fexample.com')
  await page.getByRole('link', { name: '代取图书馆资料', exact: true }).waitFor()
  check(new URL(page.url()).pathname === '/tasks', '不安全返回地址应退回 A 的任务大厅')
  await page.screenshot({ path: 'output/playwright/shared-task-hall-desktop.png', fullPage: true })
  record('B 管理权限与 A 的安全返回路径兼容')

  await page.getByRole('button', { name: '退出登录', exact: true }).click()
  await page.getByRole('heading', { name: '欢迎回来', exact: true }).waitFor()
  await page.setViewportSize({ width: 390, height: 844 })
  check(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    'A 登录页手机布局不能产生整页溢出',
  )
  await page.screenshot({ path: 'output/playwright/shared-login-mobile.png', fullPage: true })
  record('复用 A 登录页的桌面与手机布局')
  page.off('request', onRequest)
  page.off('pageerror', onError)
  check(runtimeErrors.length === 0, `运行异常：${runtimeErrors.join('; ')}`)
  return {
    passed: results.length,
    results,
    authentication: 'Frontend A stateful demo server; B-only browser fixtures',
  }
}
