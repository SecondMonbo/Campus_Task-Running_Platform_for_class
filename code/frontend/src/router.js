import { createRouter, createWebHistory } from 'vue-router'
import { loadSession, notify, session } from './composables/state.js'
import { safeDestination } from './domain/auth.js'

export const frontendBRoutes = [
  {
    path: '/my-tasks',
    name: 'my-tasks',
    component: () => import('./views/MyTasksView.vue'),
    meta: { title: '我的任务' },
  },
  {
    path: '/my-tasks/:taskId',
    name: 'task-detail',
    component: () => import('./views/TaskDetailView.vue'),
    meta: { title: '任务详情' },
  },
  {
    path: '/wallet',
    component: () => import('./views/WalletView.vue'),
    meta: { title: '钱包与流水' },
  },
  {
    path: '/notifications',
    component: () => import('./views/NotificationsView.vue'),
    meta: { title: '通知中心' },
  },
  {
    path: '/disputes',
    component: () => import('./views/DisputesView.vue'),
    meta: { title: '取消与申诉' },
  },
  {
    path: '/admin',
    component: () => import('./views/AdminView.vue'),
    meta: { title: '管理工作台', admin: true },
  },
]

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    { path: '/', redirect: '/tasks' },
    {
      path: '/login',
      name: 'login',
      component: () => import('./views/AuthView.vue'),
      meta: { title: '登录', authPage: true },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('./views/AuthView.vue'),
      meta: { title: '创建账号', authPage: true },
    },
    {
      path: '/tasks',
      name: 'tasks',
      component: () => import('./views/TaskHallView.vue'),
      meta: { title: '任务大厅' },
    },
    ...frontendBRoutes,
    {
      path: '/:pathMatch(.*)*',
      component: () => import('./views/NotFoundView.vue'),
      meta: { title: '页面未找到' },
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

router.beforeEach(async (to) => {
  let currentUser = session.value
  if (!currentUser) {
    try {
      currentUser = await loadSession()
    } catch (error) {
      if (!to.meta.authPage) {
        notify(error.message, 'error')
        return { name: 'login', query: { redirect: to.fullPath } }
      }
    }
  }

  if (to.meta.authPage) {
    if (currentUser) return safeDestination(to.query.redirect)
    return true
  }
  if (!currentUser) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.admin && currentUser.role !== 'ADMIN') {
    notify('当前账号没有管理端访问权限。', 'error')
    return '/my-tasks'
  }
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title || '校园跑腿'} · 校园跑腿平台`
})

export default router
