async function verifyAdmin(page) {
  const origin = new URL(page.url()).origin
  await page.unrouteAll({ behavior: 'wait' })
  const results = []
  const errors = []
  const onError = (error) => errors.push(error.message)
  page.on('pageerror', onError)
  const check = (condition, message) => { if (!condition) throw new Error(message) }
  const user = { userId: '3', username: 'test-admin', displayName: '测试管理员', role: 'ADMIN' }
  const taskId = '00000000-0000-0000-0000-000000000100'
  const now = '2026-11-05T04:00:00Z'
  const complaints = Array.from({ length: 11 }, (_, i) => ({
    complaintId: `00000000-0000-0000-0000-${String(11 - i).padStart(12, '0')}`,
    reporterId: '1', targetUserId: '2', taskId, category: '履约问题',
    description: `投诉说明 ${11 - i}`, status: 'PENDING', actions: [], createdAt: now,
  }))
  const transactions = Array.from({ length: 23 }, (_, i) => ({
    transactionId: `10000000-0000-0000-0000-${String(23 - i).padStart(12, '0')}`,
    userId: i < 13 ? '1' : '2', taskId: i < 17 ? taskId : null,
    appealId: i === 0 ? '20000000-0000-0000-0000-000000000001' : null,
    type: i % 2 ? 'EXPENSE' : 'INCOME', amount: i % 2 ? -10 : 10,
    balanceAfter: 100 + i, createdAt: now,
  }))
  const calls = []
  let failTransactions = false
  let conflict = false
  await page.route('**/api/v1/**', async (route) => {
    const req = route.request()
    const url = new URL(req.url())
    const path = url.pathname.replace('/api/v1', '')
    const query = Object.fromEntries(url.searchParams)
    const body = req.postDataJSON()
    calls.push({ path, method: req.method(), query, body, headers: req.headers() })
    const respond = (data, status = 200, code = 'OK', message = 'success') => route.fulfill({
      status, contentType: 'application/json', body: JSON.stringify({ code, message, data }),
    })
    const paged = (rows) => {
      const page = Number(query.page || 0)
      const size = Number(query.size || 10)
      return { items: rows.slice(page * size, (page + 1) * size), page, size, total: rows.length }
    }
    if (path === '/auth/me') return respond(user)
    if (path === '/auth/csrf') return route.fulfill({
      contentType: 'application/json',
      body: JSON.stringify({ headerName: 'X-XSRF-TOKEN', token: 'test-csrf' }),
    })
    if (path === '/notifications' || path === '/users/me/tasks') return respond(paged([]))
    if (path === `/tasks/${taskId}`) return respond({
      taskId, title: '测试任务', publisherId: '1', runnerId: '2', reward: 20, status: 'COMPLETED',
    })
    if (path.startsWith('/admin/') && user.role !== 'ADMIN')
      return respond(null, 403, 'FORBIDDEN', '仅管理员可执行此操作')
    if (path === '/admin/cancellation-requests') return respond(paged([]))
    if (path === '/admin/complaints')
      return respond(paged(complaints.filter((item) => !query.status || item.status === query.status)))
    if (/^\/admin\/complaints\/[^/]+\/actions$/.test(path)) {
      if (req.headers()['x-xsrf-token'] !== 'test-csrf') return respond(null, 403, 'FORBIDDEN', '缺少安全校验')
      const item = complaints.find((row) => row.complaintId === path.split('/')[3])
      if (conflict || item.status !== 'PENDING') return respond(null, 409, 'DUPLICATE_ACTION', '投诉已处理，请刷新列表')
      await new Promise((resolve) => setTimeout(resolve, 150))
      Object.assign(item, body, { status: 'PROCESSED', handledBy: user.userId, handledAt: now })
      return respond(item)
    }
    if (path === '/admin/transactions') {
      if (failTransactions) return respond(null, 500, 'INTERNAL_ERROR', '交易查询暂时失败')
      return respond(paged(transactions.filter((item) =>
        (!query.userId || item.userId === query.userId) && (!query.taskId || item.taskId === query.taskId),
      )))
    }
    return respond(null, 404, 'RESOURCE_NOT_FOUND', '测试场景未定义此接口')
  })
  const lastQuery = (resource) => calls.filter((call) => call.path === `/admin/${resource}`).at(-1)?.query
  const writes = () => calls.filter((call) => call.method === 'POST')
  const waitRows = (count) => page.waitForFunction((expected) =>
    document.querySelectorAll('tbody tr').length === expected, count)
  try {
    await page.setViewportSize({ width: 1440, height: 1000 })
    await page.goto(`${origin}/admin`)
    await page.getByRole('button', { name: '投诉处理', exact: true }).click()
    await waitRows(10)
    check(lastQuery('complaints').page === '0' && lastQuery('complaints').size === '10', '默认使用 0/10 分页')
    await page.getByRole('button', { name: '下一页', exact: true }).click()
    await waitRows(1)
    await page.getByRole('button', { name: '处理', exact: true }).click()
    await page.getByRole('dialog').waitFor()
    for (const name of ['扣除信用分', '账务处理', '限制功能', '冻结账号'])
      check(await page.getByRole('checkbox', { name: new RegExp(name) }).isDisabled(), `${name}应禁用`)
    await page.getByLabel('处理说明', { exact: true }).fill('   ')
    await page.getByRole('button', { name: '确认提交', exact: true }).click()
    await page.getByText('请填写处理说明。', { exact: true }).waitFor()
    check(writes().length === 0, '空白说明不能发送请求')
    await page.getByLabel('记录提醒与警告', { exact: true }).check()
    await page.getByLabel('处理说明', { exact: true }).fill('已核对双方说明，记录提醒结果。')
    await page.screenshot({ path: 'output/playwright/admin-complaint-dialog.png', fullPage: true })
    await page.getByRole('button', { name: '确认提交', exact: true }).click()
    check(await page.getByRole('button', { name: '正在提交…', exact: true }).isDisabled(), '提交期间应禁用按钮')
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    await waitRows(10)
    check(lastQuery('complaints').page === '0', '处理末页唯一记录后返回最后有效页')
    check(writes().length === 1 && writes()[0].headers['x-xsrf-token'] === 'test-csrf', '只提交一次且携带 CSRF')
    check(Object.keys(writes()[0].body).sort().join(',') === 'actions,decisionNote', '只发送方式和说明')
    results.push('投诉默认分页、处罚禁用、必填校验、CSRF、防重复提交与末页修正')

    await page.getByRole('combobox', { name: '状态', exact: true }).selectOption('PROCESSED')
    await waitRows(1)
    await page.getByText('处理说明：已核对双方说明，记录提醒结果。', { exact: true }).waitFor()
    check(!(await page.getByRole('button', { name: '处理', exact: true }).count()), '已处理记录不能再处理')
    await page.screenshot({ path: 'output/playwright/admin-complaints-desktop.png', fullPage: true })
    await page.getByRole('combobox', { name: '状态', exact: true }).selectOption('PENDING')
    await waitRows(10)
    conflict = true
    await page.getByRole('button', { name: '处理', exact: true }).first().click()
    await page.getByLabel('处理说明', { exact: true }).fill('并发处理测试')
    await page.getByRole('button', { name: '确认提交', exact: true }).click()
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    await page.getByText('投诉已处理，请刷新列表', { exact: true }).waitFor()
    results.push('已处理记录回显、状态筛选及 409 冲突刷新')

    await page.getByRole('button', { name: '交易流水', exact: true }).click()
    await waitRows(10)
    await page.getByRole('button', { name: '下一页', exact: true }).click()
    await waitRows(10)
    check(lastQuery('transactions').page === '1', '下一页使用 0 起始页码')
    await page.getByLabel('用户编号', { exact: true }).fill(' 1 ')
    await page.getByLabel('任务编号', { exact: true }).fill(taskId)
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await page.getByText('共 13 条记录', { exact: true }).waitFor()
    check(lastQuery('transactions').page === '0' && lastQuery('transactions').userId === '1' &&
      lastQuery('transactions').taskId === taskId && !lastQuery('transactions').status, '筛选取交集并清除旧页码和状态')
    await page.getByLabel('每页条数', { exact: true }).selectOption('20')
    await waitRows(13)
    check(lastQuery('transactions').page === '0' && lastQuery('transactions').size === '20', '变更每页条数返回首页')
    await page.getByLabel('每页条数', { exact: true }).selectOption('50')
    await waitRows(13)
    check(lastQuery('transactions').size === '50', '50 条分页生效')
    await page.screenshot({ path: 'output/playwright/admin-transactions-desktop.png', fullPage: true })
    results.push('交易流水翻页、用户与任务交集筛选、10/20/50 条分页')

    await page.getByLabel('用户编号', { exact: true }).fill('999')
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await page.getByRole('heading', { name: '暂无符合条件的记录', exact: true }).waitFor()
    failTransactions = true
    await page.getByRole('button', { name: '刷新', exact: true }).click()
    await page.getByText('交易查询暂时失败', { exact: true }).waitFor()
    failTransactions = false
    await page.getByRole('button', { name: '重新加载', exact: true }).click()
    await page.getByRole('heading', { name: '暂无符合条件的记录', exact: true }).waitFor()
    await page.getByLabel('用户编号', { exact: true }).fill('')
    await page.getByLabel('任务编号', { exact: true }).fill('')
    await page.getByRole('button', { name: '查询', exact: true }).click()
    await waitRows(23)
    results.push('空结果成功展示、错误状态与重试、清空筛选')

    await page.setViewportSize({ width: 390, height: 844 })
    check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), '移动端表格应在容器内滚动')
    await page.screenshot({ path: 'output/playwright/admin-transactions-mobile.png', fullPage: true })
    await page.getByRole('button', { name: '投诉处理', exact: true }).click()
    await waitRows(10)
    await page.getByRole('button', { name: '处理', exact: true }).first().click()
    check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), '移动端弹窗不应溢出')
    await page.screenshot({ path: 'output/playwright/admin-complaint-mobile.png', fullPage: true })
    await page.getByRole('button', { name: '返回', exact: true }).click()
    results.push('390px 移动端表格滚动与投诉弹窗布局')

    const adminCalls = calls.filter((call) => call.path.startsWith('/admin/')).length
    user.role = 'USER'
    await page.goto(`${origin}/admin`)
    await page.waitForURL('**/my-tasks')
    check(calls.filter((call) => call.path.startsWith('/admin/')).length === adminCalls, '普通用户不能触发管理查询')
    results.push('普通用户路由拦截且不发送管理请求')
    check(errors.length === 0, `浏览器运行错误：${errors.join('; ')}`)
    return { passed: results.length, results }
  } finally {
    page.off('pageerror', onError)
    await page.unrouteAll({ behavior: 'wait' })
  }
}
