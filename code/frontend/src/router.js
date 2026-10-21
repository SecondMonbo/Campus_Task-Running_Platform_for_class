import { createRouter, createWebHistory } from 'vue-router'
import { loadSession, notify, session } from './composables/state.js'

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
    {
      path: '/:pathMatch(.*)*',
      component: () => import('./views/NotFoundView.vue'),
      meta: { title: '页面未找到' },
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})

function safeDestination(value) {
  if (
    typeof value !== 'string' ||
    !value.startsWith('/') ||
    value.startsWith('//') ||
    value.includes('\\')
  )
    return '/tasks'
  if (['/login', '/register'].includes(value.split(/[?#]/, 1)[0])) return '/tasks'
  return value
}

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
  return true
})

router.afterEach((to) => {
  document.title = `${to.meta.title || '校园跑腿'} · 校园跑腿平台`
})

export default router
