import { createRouter, createWebHistory } from 'vue-router'
import MyTasksView from './views/MyTasksView.vue'
import { loadSession, notify, session } from './composables/state.js'

export const frontendBRoutes = [
  { path: '/my-tasks', name: 'my-tasks', component: MyTasksView, meta: { title: '我的任务' } },
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
    { path: '/', redirect: '/my-tasks' },
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
  if (!to.meta.admin) return true
  if (!session.value) {
    try {
      await loadSession()
    } catch {
      return '/my-tasks'
    }
  }
  if (session.value.role !== 'ADMIN') {
    notify('当前账号没有管理端访问权限。', 'error')
    return '/my-tasks'
  }
  return true
})
router.afterEach((to) => {
  document.title = `${to.meta.title || '校园跑腿'} · 校园跑腿平台`
})
export default router
