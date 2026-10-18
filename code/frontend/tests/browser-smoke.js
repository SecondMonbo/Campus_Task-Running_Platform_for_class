async function verifyFrontendB(page) {
  await page.unrouteAll({ behavior: 'wait' })
  // Fixed contract fixtures are intercepted only in this browser verification session.
  // Application code always calls the configured API; no fixture fallback is shipped.
  const results = []
  const runtimeErrors = []
  const onRuntimeError = (error) => runtimeErrors.push(error.message)
  page.on('pageerror', onRuntimeError)
  const check = (condition, message) => {
    if (!condition) throw new Error(message)
  }
  const record = (message) => results.push(message)
  const user = { userId: 'u1', username: 'campus-test', displayName: '王同学', role: 'USER' }
  const tomorrow = new Date(Date.now() + 86400000).toISOString()
  const now = new Date().toISOString()
  const makeTask = (taskId, title, status, publisherId, runnerId, reward = 20) => ({
    taskId,
    title,
    description: '请按约定时间送达，取件后通过站内通知联系。',
    reward,
    deadline: tomorrow,
    pickupLocation: '东区快递服务中心',
    deliveryLocation: '学生宿舍 8 栋',
    status,
    publisherId,
    runnerId,
    createdAt: now,
  })
  const tasks = [
    makeTask('t1', '帮取一份快递，送到八号楼', 'IN_PROGRESS', 'u2', 'u1', 15),
    makeTask('t2', '图书馆资料送到教学楼', 'WAITING_FOR_ACCEPTANCE', 'u1', 'u2', 20),
    makeTask('t3', '代取校园卡，送至西区宿舍', 'COMPLETED', 'u2', 'u1', 10),
    makeTask('t4', '教学楼文件送达办公室', 'IN_PROGRESS', 'u1', 'u2', 30),
    makeTask('t5', '食堂午餐送到研究生公寓', 'WAITING_FOR_TAKER', 'u2', null, 12),
    makeTask('t6', '实验室器材代取', 'WAITING_FOR_TAKER', 'u2', null, 16),
    makeTask('t7', '南门代取学习资料', 'COMPLETED', 'u2', 'u1', 8),
    makeTask('t8', '代送毕业材料至行政楼', 'WAITING_FOR_ACCEPTANCE', 'u2', 'u1', 25),
  ]
  const notices = [
    {
      notificationId: 'n1',
      title: '任务报酬已入账',
      content: '代取校园卡任务已完成，报酬已结算。',
      read: false,
      relatedTaskId: 't3',
      createdAt: now,
    },
    {
      notificationId: 'n2',
      title: '对方申请取消任务',
      content: '请核对取消申请编号 c3 后作出应答。',
      read: false,
      relatedTaskId: 't4',
      createdAt: now,
    },
  ]
  const transactions = [
    {
      transactionId: 'tx-original',
      userId: 'u1',
      taskId: 't3',
      type: 'INCOME',
      amount: 10,
      balanceAfter: 400,
      createdAt: now,
    },
  ]
  const originalTransaction = JSON.stringify(transactions[0])
  const cancellations = [
    {
      requestId: 'c1',
      taskId: 't4',
      requesterId: 'u2',
      reason: '双方对取消费用有异议',
      status: 'PENDING_ADMIN',
      createdAt: now,
    },
    {
      requestId: 'c3',
      taskId: 't4',
      requesterId: 'u2',
      reason: '时间冲突，希望协商取消',
      status: 'PENDING_RESPONSE',
      createdAt: now,
    },
  ]
  const appeals = [
    {
      appealId: 'a1',
      taskId: 't3',
      appellantId: 'u1',
      respondentId: 'u2',
      reason: 'FULFILLMENT_ISSUE',
      description: '请复核履约情况',
      status: 'PENDING',
      createdAt: now,
    },
  ]
  const complaints = [
    {
      complaintId: 'p1',
      taskId: 't3',
      category: '物品损坏',
      description: '资料在送达过程中受损',
      status: 'PENDING',
      createdAt: now,
    },
  ]
  const calls = []
  let failList = false
  let expireAuth = false
  let conflict = false
  await page.route('**/api/v1/**', async (route) => {
    const req = route.request()
    const url = new URL(req.url())
    const path = url.pathname.replace('/api/v1', '')
    const method = req.method()
    const body = req.postDataJSON()
    calls.push({
      path,
      method,
      body,
      headers: req.headers(),
      query: Object.fromEntries(url.searchParams),
    })
    const respond = (data, status = 200, code = 'OK', message = 'success') =>
      route.fulfill({
        status,
        contentType: 'application/json',
        body: JSON.stringify({ code, message, data }),
      })
    const paged = (rows) => {
      const pageIndex = Number(url.searchParams.get('page') || 0)
      const size = Number(url.searchParams.get('size') || 10)
      return {
        items: rows.slice(pageIndex * size, (pageIndex + 1) * size),
        page: pageIndex,
        size,
        total: rows.length,
      }
    }
    if (path === '/auth/me')
      return expireAuth
        ? respond(null, 401, 'UNAUTHENTICATED', '登录已失效，请重新登录。')
        : respond(user)
    if (path === '/auth/csrf')
      return route.fulfill({
        contentType: 'application/json',
        body: JSON.stringify({
          headerName: 'X-XSRF-TOKEN',
          parameterName: '_csrf',
          token: 'fixture-csrf',
        }),
      })
    if (method === 'POST' && req.headers()['x-xsrf-token'] !== 'fixture-csrf')
      return respond(null, 403, 'INVALID_CSRF', '缺少安全校验')
    if (path === '/users/me/tasks' || path === '/tasks') {
      if (failList) return respond(null, 500, 'SERVER_ERROR', '任务加载失败，请稍后重试')
      const as = url.searchParams.get('as')
      const rows = tasks.filter(
        (task) =>
          (!as ||
            (as === 'runner' ? task.runnerId === user.userId : task.publisherId === user.userId)) &&
          (!url.searchParams.get('status') || task.status === url.searchParams.get('status')),
      )
      return respond(paged(rows))
    }
    if (path === '/wallet') return respond({ availableBalance: 400, frozenBalance: 50 })
    if (path === '/wallet/transactions')
      return respond(
        paged(
          transactions.filter(
            (item) => !url.searchParams.get('type') || item.type === url.searchParams.get('type'),
          ),
        ),
      )
    if (path === '/notifications')
      return respond(
        paged(
          notices.filter((notice) => url.searchParams.get('unreadOnly') !== 'true' || !notice.read),
        ),
      )
    if (/^\/notifications\/[^/]+\/read$/.test(path)) {
      notices.find((item) => item.notificationId === path.split('/')[2]).read = true
      return respond(null)
    }
    const taskMatch = path.match(
      /^\/tasks\/([^/]+)(?:\/(acceptance|delivery|completion|cancellation-requests|appeals))?$/,
    )
    if (taskMatch) {
      const task = tasks.find((item) => item.taskId === taskMatch[1])
      if (!task) return respond(null, 404, 'TASK_NOT_FOUND', '任务不存在')
      const action = taskMatch[2]
      if (!action) return respond(task)
      if (action === 'acceptance') {
        if (conflict || task.status !== 'WAITING_FOR_TAKER') {
          if (conflict) {
            task.status = 'IN_PROGRESS'
            task.runnerId = 'u3'
          }
          return respond(null, 409, 'TASK_STATE_CONFLICT', '该任务已被他人认领')
        }
        task.status = 'IN_PROGRESS'
        task.runnerId = user.userId
      }
      if (action === 'delivery') task.status = 'WAITING_FOR_ACCEPTANCE'
      if (action === 'completion') {
        await new Promise((resolve) => setTimeout(resolve, 250))
        task.status = 'COMPLETED'
      }
      if (action === 'cancellation-requests') {
        const item = {
          requestId: 'c-new',
          taskId: task.taskId,
          requesterId: user.userId,
          reason: body.reason,
          status: 'PENDING_RESPONSE',
          createdAt: now,
        }
        cancellations.push(item)
        return respond(item, 201)
      }
      if (action === 'appeals') {
        if (appeals.some((item) => item.taskId === task.taskId && item.appellantId === user.userId))
          return respond(null, 409, 'DUPLICATE_ACTION', '该任务已提交过申诉')
        const item = {
          appealId: 'a-new',
          taskId: task.taskId,
          appellantId: user.userId,
          respondentId: task.publisherId,
          ...body,
          status: 'PENDING',
          createdAt: now,
        }
        appeals.push(item)
        return respond(item, 201)
      }
      return respond(task)
    }
    if (/^\/cancellation-requests\/[^/]+\/response$/.test(path)) {
      const item = cancellations.find((entry) => entry.requestId === path.split('/')[2])
      if (!item || item.status !== 'PENDING_RESPONSE')
        return respond(null, 409, 'DUPLICATE_ACTION', '该申请已处理')
      item.status = body.decision === 'REJECT' ? 'PENDING_ADMIN' : 'APPROVED'
      return respond(item)
    }
    if (path === '/complaints' && method === 'POST') {
      const item = { complaintId: 'p-new', ...body, status: 'PENDING', createdAt: now }
      complaints.push(item)
      return respond(item, 201)
    }
    if (path.startsWith('/admin/')) {
      if (user.role !== 'ADMIN') return respond(null, 403, 'FORBIDDEN', '无管理员权限')
      if (method === 'POST') {
        const [, , resource, itemId] = path.split('/')
        const rows =
          resource === 'cancellation-requests'
            ? cancellations
            : resource === 'appeals'
              ? appeals
              : complaints
        const item = rows.find(
          (entry) => (entry.requestId || entry.appealId || entry.complaintId) === itemId,
        )
        Object.assign(item, body, {
          status:
            resource === 'cancellation-requests'
              ? 'ARBITRATED'
              : resource === 'appeals'
                ? 'DECIDED'
                : 'PROCESSED',
        })
        if (resource === 'cancellation-requests' && body.ruling !== 'SUPPORT_CONTINUE')
          tasks.find((task) => task.taskId === item.taskId).status = 'CANCELLED'
        if (resource === 'appeals' && body.transferAmount > 0)
          transactions.push({
            transactionId: 'tx-appeal',
            userId: 'u1',
            taskId: item.taskId,
            appealId: item.appealId,
            type: 'COMPENSATION',
            amount: body.transferAmount,
            balanceAfter: 425,
            createdAt: now,
          })
        return respond(item)
      }
      const resource = path.split('/')[2]
      let rows =
        {
          'cancellation-requests': cancellations,
          appeals,
          complaints,
          users: [user],
          transactions,
          'operation-logs': [
            {
              logId: 'log1',
              actorId: 'u1',
              action: 'TASK_ACCEPTED',
              targetId: 't5',
              createdAt: now,
            },
          ],
        }[resource] || []
      if (url.searchParams.get('status'))
        rows = rows.filter((item) => item.status === url.searchParams.get('status'))
      return respond(paged(rows))
    }
    return respond(null, 404, 'NOT_FOUND', path)
  })
  const go = async (path) => {
    await page.goto(`http://127.0.0.1:5173${path}`)
    await page.getByRole('heading', { level: 1 }).waitFor()
  }
  const settle = async () => {
    await page.locator('dialog').waitFor({ state: 'detached' })
  }
  await page.setViewportSize({ width: 1366, height: 900 })
  await go('/my-tasks')
  await page.getByText('帮取一份快递，送到八号楼', { exact: true }).waitFor()
  check(
    !(await page.getByRole('link', { name: '管理工作台', exact: true }).count()),
    '普通用户不应看到管理入口',
  )
  await page.screenshot({ path: 'output/playwright/my-tasks-desktop.png', fullPage: true })
  record('任务列表、状态筛选、普通用户菜单和桌面布局')

  await page.getByRole('button', { name: '我发布的', exact: true }).click()
  await page.getByRole('button', { name: '待验收', exact: true }).click()
  await page.getByRole('link', { name: '图书馆资料送到教学楼', exact: true }).click()
  await page.getByRole('button', { name: '验收并完成', exact: true }).click()
  await page.getByRole('button', { name: '返回', exact: true }).click()
  check(!calls.some((call) => call.path.endsWith('/completion')), '取消确认框不应发起完成请求')
  await page.getByRole('button', { name: '验收并完成', exact: true }).click()
  const confirm = page.getByRole('button', { name: '确定', exact: true })
  await confirm.click()
  check(
    await page.getByRole('button', { name: '正在提交…', exact: true }).isDisabled(),
    '提交期间按钮应禁用',
  )
  await settle()
  await page.getByText('任务已完成。', { exact: false }).first().waitFor()
  check(
    calls.filter((call) => call.path.endsWith('/completion')).length === 1,
    '完成操作应只提交一次',
  )
  record('发布者验收、取消确认、提交防重和状态刷新')

  await go('/my-tasks/t1')
  check(
    !(await page.getByRole('button', { name: '验收并完成', exact: true }).count()),
    '跑腿员不应显示验收按钮',
  )
  await page.getByRole('button', { name: '确认送达', exact: true }).click()
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await settle()
  check(
    tasks.find((task) => task.taskId === 't1').status === 'WAITING_FOR_ACCEPTANCE',
    '送达应进入待验收',
  )
  record('跑腿员送达及与发布者权限隔离')

  await go('/my-tasks/t5')
  await page.getByRole('button', { name: '接取任务', exact: true }).click()
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await settle()
  check(tasks.find((task) => task.taskId === 't5').runnerId === 'u1', '接单应绑定当前用户')
  check(
    calls.filter((call) => call.path.endsWith('/acceptance')).every((call) => call.body === null),
    '接单不应传入操作者或状态',
  )
  record('接单动作使用会话身份和业务接口')

  conflict = true
  await go('/my-tasks/t6')
  await page.getByRole('button', { name: '接取任务', exact: true }).click()
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await page.locator('.toast.error').getByText('该任务已被他人认领', { exact: false }).waitFor()
  check(
    tasks.find((task) => task.taskId === 't6').runnerId === 'u3',
    '失败不能把任务伪造为本人接单',
  )
  await settle()
  await page.waitForFunction(() => !document.querySelector('.task-actions .button.primary'))
  conflict = false
  record('409 抢单冲突提示，失败不修改页面状态')

  await go('/my-tasks')
  await page.getByRole('button', { name: '已取消', exact: true }).click()
  await page.getByRole('heading', { name: '这个分类下还没有任务' }).waitFor()
  failList = true
  await page.getByRole('button', { name: '刷新', exact: true }).click()
  await page.getByRole('heading', { name: '暂时无法加载' }).waitFor()
  check(
    !(await page.getByRole('heading', { name: '这个分类下还没有任务' }).count()),
    '请求失败不能显示为空列表',
  )
  failList = false
  await page.getByRole('button', { name: '重新加载', exact: true }).click()
  await page.getByRole('heading', { name: '这个分类下还没有任务' }).waitFor()
  await page.getByRole('combobox', { name: '每页条数' }).selectOption('20')
  await page.getByRole('button', { name: '全部', exact: true }).click()
  await page.getByRole('link', { name: '食堂午餐送到研究生公寓', exact: true }).waitFor()
  check(
    calls.some((call) => call.query.size === '20' && call.query.page === '0'),
    '切换每页条数应使用契约分页参数',
  )
  record('空列表、请求失败、重试与分页参数')

  await go('/wallet')
  await page.getByText('#tx-original', { exact: true }).waitFor()
  await page.screenshot({ path: 'output/playwright/wallet-desktop.png', fullPage: true })
  await page.getByLabel('流水类型').selectOption('EXPENSE')
  await page.getByRole('heading', { name: '暂无交易记录' }).waitFor()
  record('钱包和流水类型筛选')
  await go('/notifications')
  await page.getByText('任务报酬已入账', { exact: true }).waitFor()
  await page.screenshot({ path: 'output/playwright/notifications-desktop.png', fullPage: true })
  await page.getByRole('button', { name: '标记已读', exact: true }).first().click()
  await page.waitForFunction(
    () =>
      document.querySelectorAll('.notification-item').length === 2 &&
      document.querySelectorAll('.notification-item .unread-label').length === 1,
  )
  check(notices[0].read, '通知应成功标记已读')
  await page.getByRole('button', { name: '查看任务', exact: true }).first().click()
  await page.getByRole('heading', { name: '代取校园卡，送至西区宿舍', exact: true }).waitFor()
  check(
    calls.filter((call) => call.path === '/notifications/n1/read').length === 1,
    '已读通知跳转不应重复标记',
  )
  record('通知已读和关联任务跳转')

  await go('/my-tasks/t1')
  await page.getByRole('button', { name: '申请取消', exact: true }).click()
  await page.getByLabel('取消原因').fill('时间冲突，需要协商取消')
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await settle()
  check(
    tasks.find((task) => task.taskId === 't1').status === 'WAITING_FOR_ACCEPTANCE',
    '取消申请本身不能改变任务状态',
  )
  await page.locator('.receipt strong').getByText('c-new', { exact: true }).waitFor()
  await go('/disputes?requestId=c3')
  await page.getByLabel('应答结果').selectOption('REJECT')
  await page.getByRole('button', { name: '确认应答', exact: true }).click()
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await settle()
  await page.getByText('应答已记录', { exact: true }).waitFor()
  check(
    cancellations.find((item) => item.requestId === 'c3').status === 'PENDING_ADMIN',
    '拒绝应转管理员处理',
  )
  record('取消申请、取消应答与任务状态分离')

  await go('/my-tasks/t7')
  await page.getByRole('button', { name: '提交申诉', exact: true }).click()
  await page.getByLabel('申诉理由').selectOption('FULFILLMENT_ISSUE')
  await page.getByLabel('申诉说明').fill('申请复核任务履约情况')
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await settle()
  check(
    tasks.find((task) => task.taskId === 't7').status === 'COMPLETED',
    '申诉不能回滚任务完成状态',
  )
  await page.getByRole('button', { name: '提交申诉', exact: true }).click()
  await page.getByLabel('申诉理由').selectOption('OTHER')
  await page.getByLabel('申诉说明').fill('重复提交场景')
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await page.locator('.toast.error').getByText('该任务已提交过申诉', { exact: false }).waitFor()
  await settle()
  await page.getByRole('button', { name: '投诉', exact: true }).click()
  await page.getByLabel('投诉类型').selectOption('其他')
  await page.getByLabel('投诉内容').fill('请管理员核实相关沟通情况')
  await page.getByRole('button', { name: '确定', exact: true }).click()
  await settle()
  record('完成任务申诉、重复申诉拒绝与投诉提交')

  await go('/admin')
  check(new URL(page.url()).pathname === '/my-tasks', '普通用户直达管理端应被拦截')
  check(!calls.some((call) => call.path.startsWith('/admin/')), '普通用户不应发送管理查询')
  user.role = 'ADMIN'
  await go('/admin')
  await page.getByRole('button', { name: '裁决', exact: true }).first().click()
  await page.getByText('教学楼文件送达办公室 · 任务报酬 30 虚拟币', { exact: true }).waitFor()
  await page.screenshot({ path: 'output/playwright/admin-decision-desktop.png', fullPage: true })
  await page.getByLabel('裁决结论').selectOption('CANCEL_PROPORTIONALLY')
  await page.getByLabel('退还发布者').fill('20')
  await page.getByLabel('支付跑腿员').fill('9')
  await page.getByLabel('裁决说明').fill('按实际履约分配冻结报酬')
  await page.getByRole('button', { name: '确认提交', exact: true }).click()
  await page.getByText('退款和支付金额之和必须等于任务冻结报酬。', { exact: true }).waitFor()
  check(!calls.some((call) => call.path.endsWith('/arbitration')), '金额不守恒时不能提交')
  await page.getByLabel('支付跑腿员').fill('10')
  await page.getByRole('button', { name: '确认提交', exact: true }).click()
  await settle()
  check(
    tasks.find((task) => task.taskId === 't4').status === 'CANCELLED',
    '取消裁决应以服务端结果为准',
  )
  record('管理端权限与取消裁决金额守恒')

  await page.getByRole('button', { name: '任务申诉', exact: true }).click()
  await page.getByRole('button', { name: '裁决', exact: true }).first().click()
  await page.getByLabel('裁决结论').selectOption('PARTIALLY_SUPPORTED')
  await page.getByLabel('划转金额（虚拟币）').fill('25')
  await page.getByLabel('裁决说明').fill('根据履约情况部分支持')
  await page.getByRole('button', { name: '确认提交', exact: true }).click()
  await settle()
  check(tasks.find((task) => task.taskId === 't3').status === 'COMPLETED', '申诉裁决后任务仍已完成')
  check(JSON.stringify(transactions[0]) === originalTransaction, '原始结算流水不能被修改')
  check(
    transactions.some((item) => item.appealId === 'a1'),
    '划转流水应关联申诉',
  )
  await page.getByRole('button', { name: '投诉处理', exact: true }).click()
  await page.getByRole('button', { name: '处理', exact: true }).first().click()
  await page.getByLabel('提醒与警告', { exact: true }).check()
  await page.getByLabel('扣除信用分', { exact: true }).check()
  await page.getByRole('combobox', { name: '扣分依据', exact: true }).selectOption('物品损坏')
  await page.getByLabel('处理说明').fill('已核实并提醒当事人注意履约')
  await page.getByRole('button', { name: '确认提交', exact: true }).click()
  await settle()
  check(
    calls
      .find((call) => call.path.endsWith('/p1/actions'))
      .body.decisionNote.includes('扣分依据：物品损坏'),
    '扣分依据必须记录在处理说明中',
  )
  for (const tab of ['用户查询', '任务查询', '交易流水', '操作日志']) {
    await page.getByRole('button', { name: tab, exact: true }).click()
    await page.locator('tbody tr').first().waitFor()
  }
  record('申诉裁决保留原任务和流水、投诉处理与管理查询')

  await page.setViewportSize({ width: 390, height: 844 })
  await go('/my-tasks')
  await page.getByRole('link', { name: '食堂午餐送到研究生公寓', exact: true }).waitFor()
  check(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    '手机页面不能产生整页横向溢出',
  )
  await page.screenshot({ path: 'output/playwright/my-tasks-mobile.png', fullPage: true })
  await page.getByRole('button', { name: '切换导航', exact: true }).click()
  await page.getByRole('button', { name: '关闭导航', exact: true }).waitFor()
  await page.getByRole('button', { name: '关闭导航', exact: true }).click()
  await page.setViewportSize({ width: 320, height: 760 })
  await go('/wallet')
  await page.locator('table').waitFor()
  check(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
    '320px 表格应在容器内滚动',
  )
  record('390px / 320px 手机布局、侧栏开关和表格局部滚动')

  expireAuth = true
  await page.reload()
  await page.getByRole('heading', { name: '请先登录', exact: true }).waitFor()
  check(!(await page.locator('table').count()), '登录失效后不应显示业务记录')
  expireAuth = false
  await page.setViewportSize({ width: 1366, height: 900 })
  await go('/my-tasks')
  await page.getByRole('link', { name: '食堂午餐送到研究生公寓', exact: true }).waitFor()
  record('登录失效错误状态与恢复')
  page.off('pageerror', onRuntimeError)
  check(runtimeErrors.length === 0, `浏览器运行异常：${runtimeErrors.join('; ')}`)
  return {
    passed: results.length,
    results,
    csrfWrites: calls.filter((call) => call.method === 'POST').length,
  }
}
