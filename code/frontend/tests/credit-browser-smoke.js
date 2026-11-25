async function verifyCreditFrontend(page) {
  const origin = new URL(page.url()).origin
  await page.unrouteAll({ behavior: 'wait' })
  // 独立浏览器契约场景：新增信用接口尚待后端实现，不向实际账号发送处罚请求。
  const results = []
  const errors = []
  const onError = (error) => errors.push(error.message)
  page.on('pageerror', onError)
  const check = (condition, message) => { if (!condition) throw new Error(message) }
  const now = '2026-11-25T00:00:00Z'
  const level = (score) => score >= 90 ? 'EXCELLENT' : score >= 75 ? 'GOOD' : score >= 60 ? 'WARNING' : 'RESTRICTED'
  const makeCredit = (userId, score, restricted = score < 60) => ({
    userId, displayName: `测试用户 ${userId}`, creditScore: score, creditLevel: level(score),
    restrictionActive: restricted, canAcceptNormalTasks: !restricted, requiresPublishReview: restricted,
    consecutiveCompletedCount: 3, updatedAt: now, adjustmentAllowed: true,
    reviewDecisionAllowed: false, reviewRequestAllowed: false, pendingReview: null,
  })
  const user = { userId: '99', username: 'test-admin', displayName: '测试管理员', role: 'ADMIN' }
  const credits = Array.from({ length: 23 }, (_, index) => makeCredit(String(index + 1), 80))
  Object.assign(credits[0], makeCredit('1', 65, true), { reviewDecisionAllowed: true,
    pendingReview: { reviewId: 'review-1', userId: '1', status: 'PENDING', reason: '请求核对原信用处分', createdAt: now } })
  Object.assign(credits[2], makeCredit('3', 99))
  const ownAdmin = makeCredit('99', 80)
  const history = (userId) => Array.from({ length: 10 }, (_, index) => ({ recordId: `record-${userId}-${index}`,
    userId, eventType: 'NEUTRAL_REVIEW', eventId: `review-source-${index}`, reason: '中性评价留痕',
    requestedAdjustment: 0, actualAdjustment: 0, scoreBefore: 80, scoreAfter: 80, createdAt: now }))
  const records = Object.fromEntries(credits.map((credit) => [credit.userId, history(credit.userId)]))
  records['1'].push({ recordId: 'credit-old', userId: '1', eventType: 'COMPLAINT_PENALTY', eventId: 'c2',
    reason: '原投诉严重档，受分值下限影响实际仅扣 3 分', requestedAdjustment: -10, actualAdjustment: -3,
    scoreBefore: 3, scoreAfter: 0, administratorId: '99', createdAt: now })
  records['1'].push({ recordId: 'initial-1', userId: '1', eventType: 'INITIALIZATION', eventId: 'user-1',
    reason: '信用初始化', requestedAdjustment: 0, actualAdjustment: 0, scoreBefore: null, scoreAfter: 80, createdAt: now })
  const actions = ['LOG_ONLY', 'WARNING', 'OFFLINE_REFERRAL', 'CREDIT_ADJUSTMENT']
  const complaint = (complaintId, description, targetUserId = '1') => ({ complaintId, description, targetUserId,
    reporterId: '2', category: '履约问题', taskId: null, status: 'PENDING', actions: [], allowedActions: actions, createdAt: now })
  const complaints = [complaint('c1', '待核实的严重投诉'), complaint('c2', '原始扣分复核样例'),
    complaint('c3', '等待异步记分样例', '2'), complaint('c4', '仅举报任务内容', null),
    { ...complaint('c5', '旧后端留痕样例'), allowedActions: undefined }, complaint('c6', '不完整回执样例', '2')]
  Object.assign(complaints[1], { status: 'PROCESSED', actions: ['CREDIT_ADJUSTMENT'], complaintResult: 'SUPPORTED',
    creditAdjustment: -10, creditActualAdjustment: -3, creditRecordId: 'credit-old', creditStatus: 'APPLIED', creditReviewAllowed: true })
  const makeTask = (taskId, status, publisherId, runnerId) => ({ taskId, status, publisherId, runnerId,
    title: `测试任务 ${taskId}`, description: '契约验证任务', reward: 20, deadline: new Date(Date.now() + 86400000).toISOString(),
    pickupLocation: '测试取件点', deliveryLocation: '测试送达点', createdAt: now })
  const tasks = [makeTask('waiting-task', 'WAITING_FOR_TAKER', '2', null), makeTask('inflight-task', 'IN_PROGRESS', '2', '1')]
  const calls = []
  const operationResults = new Map()
  let failCredits = false
  let malformedCredits = false
  let failAdjustmentOnce = false
  let reviewConflict = false
  let failOwn = false
  let expired = false
  let denyAcceptance = false
  let eventNumber = 0
  const ownerCredit = () => user.userId === '99' ? ownAdmin : credits.find((item) => item.userId === user.userId)
  function applyCredit(userId, adjustment, eventType, reason, extra = {}) {
    const credit = credits.find((item) => item.userId === userId)
    const before = credit.creditScore
    const after = Math.max(0, Math.min(100, before + adjustment))
    credit.creditScore = after
    credit.creditLevel = level(after)
    if (after < 60) credit.restrictionActive = true
    if (after >= 75) credit.restrictionActive = false
    credit.canAcceptNormalTasks = !credit.restrictionActive
    credit.requiresPublishReview = credit.restrictionActive
    const receipt = { eventId: `event-${++eventNumber}`, userId, status: 'APPLIED', creditRecordId: `new-record-${eventNumber}`,
      requestedAdjustment: adjustment, actualAdjustment: after - before, scoreBefore: before, scoreAfter: after, ...extra }
    records[userId].unshift({ recordId: receipt.creditRecordId, userId, eventType, eventId: receipt.eventId, reason,
      requestedAdjustment: adjustment, actualAdjustment: after - before, scoreBefore: before, scoreAfter: after,
      administratorId: '99', createdAt: now })
    return receipt
  }
  await page.route('**/actuator/health', (route) => route.fulfill({ contentType: 'application/json', body: '{"status":"UP"}' }))
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    const query = Object.fromEntries(url.searchParams)
    const body = request.postDataJSON()
    const method = request.method()
    calls.push({ path, query, body, method, headers: request.headers() })
    const respond = (data, status = 200, code = 'OK', message = 'success') => route.fulfill({ status,
      contentType: 'application/json', body: JSON.stringify({ code, message, data }) })
    const paged = (rows) => {
      const page = Number(query.page || 0), size = Number(query.size || 10)
      return { items: rows.slice(page * size, (page + 1) * size), page, size, total: rows.length }
    }
    if (path === '/auth/me') return expired ? respond(null, 401, 'UNAUTHORIZED', '会话失效') : respond(user)
    if (path === '/auth/csrf') return route.fulfill({ contentType: 'application/json',
      body: JSON.stringify({ headerName: 'X-XSRF-TOKEN', token: 'credit-test-csrf' }) })
    if (path === '/users/me/credit') return expired ? respond(null, 401, 'UNAUTHORIZED', '会话失效') : failOwn ?
      respond(null, 503, 'SERVICE_UNAVAILABLE', '信用服务暂时不可用') : respond(ownerCredit())
    if (path === '/notifications') return respond(paged([]))
    if (path.startsWith('/admin/') && user.role !== 'ADMIN') return respond(null, 403, 'FORBIDDEN', '无管理权限')
    if (method === 'POST' && request.headers()['x-xsrf-token'] !== 'credit-test-csrf')
      return respond(null, 403, 'FORBIDDEN', '缺少 CSRF')
    if (path === '/admin/credits') {
      if (failCredits) return respond(null, 500, 'INTERNAL_ERROR', '信用查询暂时失败')
      const rows = credits.filter((item) => (!query.userId || item.userId === query.userId) &&
        (!query.creditLevel || item.creditLevel === query.creditLevel) &&
        (!query.reviewPending || item.pendingReview?.status === 'PENDING'))
      return respond(paged(malformedCredits ? rows.map((item) => ({ ...item, creditScore: null })) : rows))
    }
    const creditPath = path.match(/^\/admin\/credits\/(\d+)(?:\/(records|adjustments))?$/)
    if (creditPath) {
      const userId = creditPath[1], action = creditPath[2]
      if (!action) return respond(credits.find((item) => item.userId === userId))
      if (action === 'records') return respond(paged(records[userId].filter((item) => !query.eventType || item.eventType === query.eventType)))
      if (failAdjustmentOnce) { failAdjustmentOnce = false; return respond(null, 503, 'SERVICE_UNAVAILABLE', '调整结果暂不可确认，请使用相同内容重试') }
      const key = request.headers()['idempotency-key']
      if (operationResults.has(key)) return respond(operationResults.get(key))
      const result = applyCredit(userId, body.adjustment, 'ADMIN_ADJUSTMENT', body.reason)
      operationResults.set(key, result)
      return respond(result)
    }
    if (path === '/admin/cancellation-requests') return respond(paged([]))
    if (path === '/admin/complaints') return respond(paged(complaints.filter((item) => !query.status || item.status === query.status)))
    const complaintPath = path.match(/^\/admin\/complaints\/(c\d+)\/(actions|credit-reviews)$/)
    if (complaintPath) {
      const item = complaints.find((row) => row.complaintId === complaintPath[1])
      if (complaintPath[2] === 'actions') {
        if (item.status !== 'PENDING') return respond(null, 409, 'DUPLICATE_ACTION', '投诉已处理')
        Object.assign(item, body, { status: 'PROCESSED', handledBy: '99', handledAt: now })
        if (item.complaintId === 'c6') return respond(item)
        if (body.actions.includes('CREDIT_ADJUSTMENT')) {
          if (item.complaintId === 'c3') Object.assign(item, { creditStatus: 'PENDING', creditActualAdjustment: null, creditRecordId: null })
          else {
            const receipt = applyCredit(item.targetUserId, body.creditAdjustment, 'COMPLAINT_PENALTY', body.decisionNote)
            Object.assign(item, { creditStatus: 'APPLIED', creditActualAdjustment: receipt.actualAdjustment,
              creditRecordId: receipt.creditRecordId, creditReviewAllowed: true })
          }
        }
        return respond(item)
      }
      if (reviewConflict) return respond(null, 409, 'DUPLICATE_ACTION', '该信用复核已由其他管理员处理')
      const delta = body.result === 'REVOKED' ? -item.creditActualAdjustment : body.result === 'UPHELD' ? 0 : body.adjustment
      const receipt = applyCredit(item.targetUserId, delta, 'COMPLAINT_REVIEW', body.decisionNote,
        { complaintId: item.complaintId, reviewId: 'complaint-review-1' })
      item.creditReviewAllowed = false
      return respond(receipt)
    }
    if (path === '/admin/credit-reviews/review-1/decision') {
      const receipt = applyCredit('1', body.result === 'UPHELD' ? 0 : body.adjustment,
        'ADMIN_ADJUSTMENT', body.decisionNote, { reviewId: 'review-1' })
      credits[0].pendingReview = null
      credits[0].reviewDecisionAllowed = false
      return respond(receipt)
    }
    if (path === '/users/me/credit-reviews') {
      const request = { reviewId: 'review-user-1', userId: user.userId, status: 'PENDING', reason: body.reason, createdAt: now }
      ownerCredit().pendingReview = request
      ownerCredit().reviewRequestAllowed = false
      return respond(request, 201)
    }
    if (path === '/tasks' && method === 'POST') {
      const task = { ...makeTask('pending-publication', 'WAITING_FOR_TAKER', user.userId, null), ...body, publicationReviewStatus: 'PENDING' }
      tasks.push(task)
      return respond(task, 201)
    }
    if (path === '/tasks' || path === '/users/me/tasks') return respond(paged(tasks.filter((task) =>
      (!query.status || task.status === query.status) && (!query.as ||
        (query.as === 'runner' ? task.runnerId === user.userId : task.publisherId === user.userId)))))
    const taskPath = path.match(/^\/tasks\/([^/]+)(?:\/(acceptance|delivery))?$/)
    if (taskPath) {
      const task = tasks.find((item) => item.taskId === taskPath[1])
      if (taskPath[2] === 'acceptance' && denyAcceptance) return respond(null, 403, 'CREDIT_RESTRICTED', '信用不足，无法接单')
      if (taskPath[2] === 'delivery') task.status = 'WAITING_FOR_ACCEPTANCE'
      return respond(task)
    }
    return respond(null, 404, 'RESOURCE_NOT_FOUND', '未定义的契约场景')
  })
  const writes = (suffix) => calls.filter((call) => call.method === 'POST' && call.path.endsWith(suffix))
  const row = (description) => page.locator('tbody tr').filter({ hasText: description })
  const go = (path) => page.goto(`${origin}${path}`)
  try {
    await page.setViewportSize({ width: 1440, height: 1000 })
    await go('/admin/credit')
    await page.getByRole('button', { name: '查看信用记录', exact: true }).first().waitFor()
    await page.getByRole('button', { name: '下一页', exact: true }).click()
    await page.getByRole('textbox', { name: '信用用户编号', exact: true }).fill('1')
    await page.getByRole('combobox', { name: '信用档位筛选', exact: true }).selectOption('WARNING')
    await page.getByRole('combobox', { name: '复核申请筛选', exact: true }).selectOption('true')
    await page.getByRole('button', { name: '查询信用分', exact: true }).click()
    await page.getByText('共 1 条记录', { exact: true }).waitFor()
    check(calls.filter((call) => call.path === '/admin/credits').at(-1).query.page === '0', '筛选必须返回首页')
    await page.getByRole('button', { name: '查看信用记录', exact: true }).click()
    const detail = page.getByRole('region', { name: '用户信用详情', exact: true })
    await detail.getByText('低分限制生效中', { exact: true }).waitFor()
    await detail.getByRole('combobox', { name: '信用事件类型', exact: true }).selectOption('INITIALIZATION')
    await detail.getByText('初始化 → 80', { exact: true }).waitFor()
    await detail.getByRole('combobox', { name: '每页条数', exact: true }).selectOption('20')
    await detail.getByRole('combobox', { name: '每页条数', exact: true }).selectOption('50')
    await detail.getByRole('combobox', { name: '信用事件类型', exact: true }).selectOption('')
    await detail.getByText('来源 #c2', { exact: true }).waitFor()
    results.push('管理员信用查询交集筛选、回首页、20/50 条分页、原始与零分变更记录')

    await detail.getByRole('button', { name: '处理复核申请', exact: true }).click()
    await page.getByRole('combobox', { name: '信用复核结论', exact: true }).selectOption('UPHELD')
    await page.getByLabel('复核说明与事实依据', { exact: true }).fill('核对后维持原处分，并保留申请与复核说明。')
    await page.getByRole('button', { name: '提交信用复核', exact: true }).click()
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    await page.getByText('复核记录已保存，实际分值未变动。', { exact: true }).waitFor()
    check(writes('/review-1/decision')[0].body.adjustment === undefined, '维持原处理不得提交手工增减值')
    results.push('管理员处理用户复核申请，零分变更不误报为加分')

    await page.getByRole('button', { name: '清空筛选', exact: true }).click()
    await page.getByRole('textbox', { name: '信用用户编号', exact: true }).fill('3')
    await page.getByRole('button', { name: '查询信用分', exact: true }).click()
    await page.getByRole('button', { name: '查看信用记录', exact: true }).click()
    await detail.getByRole('button', { name: '管理员复核调整', exact: true }).click()
    await page.getByLabel('本次分数调整', { exact: true }).fill('5')
    await page.getByLabel('复核说明与事实依据', { exact: true }).fill('核对已确认的履约记录，追加修正。')
    await page.getByText('预计 99 → 100 分，实际变动 +1。', { exact: true }).waitFor()
    failAdjustmentOnce = true
    await page.getByRole('button', { name: '提交信用复核', exact: true }).click()
    await page.getByText('调整结果暂不可确认，请使用相同内容重试', { exact: true }).waitFor()
    await page.getByRole('button', { name: '提交信用复核', exact: true }).click()
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    const attempts = writes('/3/adjustments')
    check(attempts.length === 2 && attempts[0].headers['idempotency-key'] === attempts[1].headers['idempotency-key'], '失败重试必须保留幂等键')
    check(attempts[0].headers['x-xsrf-token'] === 'credit-test-csrf' && Object.keys(attempts[0].body).sort().join(',') === 'adjustment,reason', '管理员写操作只携带允许字段、CSRF 与幂等键')
    check(credits[2].creditScore === 100, '实际分数按边界截断')
    results.push('复核调整 0–100 边界预览、CSRF、失败重试幂等与实际回执')

    failCredits = true
    await page.getByRole('button', { name: '刷新信用查询', exact: true }).click()
    await page.getByText('信用查询暂时失败', { exact: true }).waitFor()
    failCredits = false
    malformedCredits = true
    await page.getByRole('button', { name: '重新加载', exact: true }).click()
    await page.getByText('信用服务返回的信息不完整，请刷新后核对结果。', { exact: true }).waitFor()
    malformedCredits = false
    await page.getByRole('button', { name: '重新加载', exact: true }).click()
    await page.getByRole('button', { name: '查看信用记录', exact: true }).waitFor()
    await page.screenshot({ path: 'output/playwright/credit-admin-desktop.png', fullPage: true })
    await page.setViewportSize({ width: 390, height: 844 })
    check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), '信用管理移动端不能整页横向溢出')
    await page.screenshot({ path: 'output/playwright/credit-admin-mobile.png', fullPage: true, animations: 'disabled' })
    await page.setViewportSize({ width: 1440, height: 1000 })
    results.push('查询失败、无效分数回执和重试，桌面与移动端布局')

    await go('/admin')
    await page.getByRole('button', { name: '投诉处理', exact: true }).click()
    await row('待核实的严重投诉').getByRole('button', { name: '处理', exact: true }).click()
    await page.getByRole('combobox', { name: '投诉结论', exact: true }).selectOption('SUPPORTED')
    await page.getByRole('checkbox', { name: '扣除信用分', exact: true }).check()
    await page.getByRole('combobox', { name: '扣分档位', exact: true }).selectOption('-10')
    await page.getByLabel('处理说明', { exact: true }).fill('   ')
    await page.getByRole('button', { name: '确认提交', exact: true }).click()
    await page.getByText('请填写处理说明。', { exact: true }).waitFor()
    check(writes('/c1/actions').length === 0, '无事实依据不得提交扣分')
    await page.getByLabel('处理说明', { exact: true }).fill('证据支持严重违规，按严重档记录扣分依据。')
    await page.screenshot({ path: 'output/playwright/credit-complaint-deduction.png', fullPage: true })
    await page.getByRole('button', { name: '确认提交', exact: true }).click()
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    check(writes('/c1/actions')[0].body.creditAdjustment === -10 && writes('/c1/actions')[0].body.complaintResult === 'SUPPORTED', '扣分请求须明确成立结论与档位')
    results.push('投诉成立、轻重档选择、事实依据校验与实际扣分回执')

    await page.getByRole('combobox', { name: '状态', exact: true }).selectOption('PROCESSED')
    await row('原始扣分复核样例').getByRole('button', { name: '复核扣分', exact: true }).click()
    await page.getByRole('combobox', { name: '信用复核结论', exact: true }).selectOption('REVOKED')
    await page.getByText('预计 55 → 58 分，实际变动 +3。', { exact: true }).waitFor()
    await page.getByLabel('复核说明与事实依据', { exact: true }).fill('原处分被新证据推翻，纠正原实际扣分。')
    await page.screenshot({ path: 'output/playwright/credit-complaint-review.png', fullPage: true })
    await page.getByRole('button', { name: '提交信用复核', exact: true }).click()
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    check(writes('/c2/credit-reviews')[0].body.adjustment === undefined, '撤销原扣分不得按 -10 手工补回')
    check(records['1'].some((item) => item.recordId === 'credit-old'), '复核必须保留原记录')
    results.push('投诉复核按原实际扣分纠正，原始记录保留')

    await page.getByRole('combobox', { name: '状态', exact: true }).selectOption('PENDING')
    await row('等待异步记分样例').getByRole('button', { name: '处理', exact: true }).click()
    await page.getByRole('combobox', { name: '投诉结论', exact: true }).selectOption('SUPPORTED')
    await page.getByRole('checkbox', { name: '扣除信用分', exact: true }).check()
    await page.getByRole('combobox', { name: '扣分档位', exact: true }).selectOption('-2')
    await page.getByLabel('处理说明', { exact: true }).fill('经核对属于轻微违规，等待信用服务记分。')
    await page.getByRole('button', { name: '确认提交', exact: true }).click()
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    await page.getByText('投诉处理已保存，信用扣分等待记分。', { exact: true }).waitFor()
    for (const description of ['仅举报任务内容', '旧后端留痕样例']) {
      await row(description).getByRole('button', { name: '处理', exact: true }).click()
      check(await page.getByRole('checkbox', { name: /扣除信用分/ }).isDisabled(), '无目标或旧后端未开放时禁止扣分')
      await page.getByRole('button', { name: '返回', exact: true }).click()
    }
    results.push('异步记分不误报生效，无目标举报与旧接口处罚入口禁用')

    await row('不完整回执样例').getByRole('button', { name: '处理', exact: true }).click()
    await page.getByRole('combobox', { name: '投诉结论', exact: true }).selectOption('SUPPORTED')
    await page.getByRole('checkbox', { name: '扣除信用分', exact: true }).check()
    await page.getByRole('combobox', { name: '扣分档位', exact: true }).selectOption('-5')
    await page.getByLabel('处理说明', { exact: true }).fill('一般违约，但必须核对服务端真实扣分回执。')
    await page.getByRole('button', { name: '确认提交', exact: true }).click()
    await page.getByText('投诉处理回执不完整，请刷新列表核对，勿重复提交处罚。', { exact: true }).waitFor()
    check(writes('/c6/actions').length === 1, '不完整回执不得自动重试处罚')
    await page.getByRole('button', { name: '返回', exact: true }).click()
    await page.getByRole('combobox', { name: '状态', exact: true }).selectOption('PROCESSED')
    await row('待核实的严重投诉').getByRole('button', { name: '复核扣分', exact: true }).click()
    await page.getByRole('combobox', { name: '信用复核结论', exact: true }).selectOption('UPHELD')
    await page.getByLabel('复核说明与事实依据', { exact: true }).fill('重复处理冲突测试')
    reviewConflict = true
    await page.getByRole('button', { name: '提交信用复核', exact: true }).click()
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    await page.getByText('该信用复核已由其他管理员处理', { exact: true }).waitFor()
    results.push('忽略新字段的回执显式失败、复核 409 刷新且不自动重试')

    const adminCalls = calls.filter((call) => call.path.startsWith('/admin/')).length
    Object.assign(user, { userId: '1', username: 'test-user', displayName: '受限测试用户', role: 'USER' })
    Object.assign(credits[0], makeCredit('1', 59, true), { reviewRequestAllowed: true, pendingReview: null })
    await go('/admin/credit')
    await page.waitForURL('**/my-tasks')
    check(calls.filter((call) => call.path.startsWith('/admin/')).length === adminCalls, '普通用户不得发送信用管理查询')
    await go('/my-tasks/waiting-task')
    await page.getByRole('button', { name: '信用受限，暂不可接单', exact: true }).waitFor()
    check(await page.getByRole('button', { name: '信用受限，暂不可接单', exact: true }).isDisabled(), '受限用户不能点击接单')
    await page.setViewportSize({ width: 390, height: 844 })
    check(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), '受限提示在移动端不能横向溢出')
    await page.screenshot({ path: 'output/playwright/credit-restricted-mobile.png', fullPage: true, animations: 'disabled' })
    await page.setViewportSize({ width: 1440, height: 1000 })
    await go('/my-tasks/inflight-task')
    await page.getByRole('button', { name: '确认送达', exact: true }).click()
    await page.getByRole('button', { name: '确定', exact: true }).click()
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    check(writes('/inflight-task/delivery').length === 1, '低分限制不能阻止已有任务履约')
    results.push('普通用户管理路由拦截、低分禁新接单及在途任务继续履约')

    await page.getByRole('button', { name: '申请信用复核', exact: true }).click()
    await page.getByLabel('复核申请说明', { exact: true }).fill('当前没有新增接单权限，请核对信用处分事实。')
    await page.getByRole('button', { name: '提交复核申请', exact: true }).click()
    await page.getByRole('dialog').waitFor({ state: 'hidden' })
    await page.getByText('信用复核申请已提交，正在等待管理员处理。', { exact: true }).waitFor()
    check(Object.keys(writes('/users/me/credit-reviews')[0].body).join(',') === 'reason', '本人复核不得提交目标或操作者身份')
    await go('/tasks/new')
    await page.getByText('新发布任务需要管理员复核，通过后才会出现在任务大厅。', { exact: true }).waitFor()
    await page.getByLabel('任务标题', { exact: true }).fill('需要复核的测试任务')
    await page.getByLabel('任务说明', { exact: true }).fill('信用受限发布复核场景')
    await page.getByLabel('截止时间').fill(new Date(Date.now() + 86400000).toISOString().slice(0, 16))
    await page.getByLabel('取件地点', { exact: true }).fill('测试取件点')
    await page.getByLabel('送件地点', { exact: true }).fill('测试送达点')
    await page.getByRole('button', { name: '发布任务', exact: true }).click()
    await page.waitForURL('**/my-tasks/pending-publication')
    await page.getByText('任务“需要复核的测试任务”已提交复核，通过后才会公开，报酬已冻结。', { exact: true }).waitFor()
    results.push('受限用户申请复核与待复核发布反馈，不假称任务已公开')

    await go('/my-tasks/waiting-task')
    Object.assign(credits[0], makeCredit('1', 60, false))
    await page.getByRole('button', { name: '刷新信用状态', exact: true }).click()
    await page.getByText('履约预警', { exact: true }).waitFor()
    check(await page.getByRole('button', { name: '接取任务', exact: true }).isEnabled(), '未受限的 60 分账号仍可接单')
    Object.assign(credits[0], makeCredit('1', 74, true))
    await page.getByRole('button', { name: '刷新信用状态', exact: true }).click()
    await page.getByRole('button', { name: '信用受限，暂不可接单', exact: true }).waitFor()
    Object.assign(credits[0], makeCredit('1', 75, false))
    await page.getByRole('button', { name: '刷新信用状态', exact: true }).click()
    await page.getByRole('button', { name: '接取任务', exact: true }).waitFor()
    check(await page.getByRole('button', { name: '接取任务', exact: true }).isEnabled(), '恢复 75 并收到解除状态才允许接单')
    results.push('60 分预警与历史受限 74 分分别处理，75 分服务端解除后更新权限')

    failOwn = true
    denyAcceptance = true
    await page.reload()
    await page.getByText('暂时无法读取信用状态', { exact: true }).waitFor()
    await page.getByRole('button', { name: '接取任务', exact: true }).click()
    await page.getByRole('button', { name: '确定', exact: true }).click()
    await page.getByText('信用不足，无法接单', { exact: true }).waitFor()
    check(tasks[0].status === 'WAITING_FOR_TAKER', '服务端拒绝接单后不能修改任务状态')
    await page.getByRole('button', { name: '返回', exact: true }).click()
    expired = true
    await page.getByRole('button', { name: '刷新信用状态', exact: true }).click()
    await page.getByRole('heading', { name: '欢迎回来', exact: true }).waitFor()
    check(new URL(page.url()).pathname === '/login', '信用接口 401 沿用统一会话失效处理')
    check(errors.length === 0, `浏览器运行异常：${errors.join('; ')}`)
    results.push('信用读取失败保持未知、服务端最终拒绝权限与 401 会话清理')
    return { passed: results.length, results }
  } finally {
    page.off('pageerror', onError)
    await page.unrouteAll({ behavior: 'wait' })
  }
}
