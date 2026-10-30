<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from './api/index.js'
import {
  clearSession,
  notify,
  revision,
  session,
  toasts,
  useResource,
} from './composables/state.js'
import AppIcon from './components/AppIcon.vue'
import AsyncPanel from './components/AsyncPanel.vue'

const route = useRoute()
const router = useRouter()
const mobileMenu = ref(false)
const isDemo = import.meta.env.MODE === 'demo'
const isMock = import.meta.env.MODE === 'apifox'
const apiStatus = ref('checking')
const signingOut = ref(false)
const navigation = [
  { to: '/tasks', icon: 'pin', title: '任务大厅' },
  { to: '/tasks/new', icon: 'plus', title: '发布任务' },
  { to: '/my-tasks', icon: 'tasks', title: '我的任务' },
  { to: '/wallet', icon: 'wallet', title: '钱包与流水' },
  { to: '/notifications', icon: 'bell', title: '通知中心' },
  { to: '/disputes', icon: 'dispute', title: '取消与申诉' },
]
const unread = useResource(
  async (signal) => {
    if (!session.value || isDemo) return null
    return (await api.notifications({ unreadOnly: true, page: 0, size: 10 }, signal)).total
  },
  [session, revision],
)
const date = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  month: 'long',
  day: 'numeric',
  weekday: 'long',
}).format(new Date())

function handleAuthExpired() {
  if (!session.value) return
  const redirect = route.meta.authPage ? '/tasks' : route.fullPath
  clearSession()
  notify('登录已过期，请重新登录。', 'error')
  router.replace({ name: 'login', query: { redirect } })
}
async function checkApiStatus() {
  try {
    const response = await fetch('/actuator/health', { credentials: 'include' })
    apiStatus.value = response.ok ? 'connected' : 'unavailable'
  } catch {
    apiStatus.value = 'unavailable'
  }
}

onMounted(() => {
  globalThis.addEventListener('campus:auth-expired', handleAuthExpired)
  if (!isDemo && !isMock) void checkApiStatus()
})
onBeforeUnmount(() => globalThis.removeEventListener('campus:auth-expired', handleAuthExpired))

const activeNavigation = computed(() => {
  const matches = navigation.filter(
    (item) => route.path === item.to || route.path.startsWith(`${item.to}/`),
  )
  return matches.sort((left, right) => right.to.length - left.to.length)[0]?.to
})
function isActive(item) {
  return activeNavigation.value === item.to
}

async function logout() {
  if (signingOut.value) return
  signingOut.value = true
  mobileMenu.value = false
  try {
    await api.logout()
    clearSession()
    await router.replace('/login')
  } catch (error) {
    if (error.status === 401) {
      clearSession()
      await router.replace('/login')
    } else {
      notify(error.message, 'error')
    }
  } finally {
    signingOut.value = false
  }
}
</script>

<template>
  <a href="#main-content" class="skip-link">跳转到主要内容</a>
  <RouterView v-if="route.meta.authPage" />
  <div v-else-if="session" class="app-layout">
    <aside class="sidebar" :class="{ 'mobile-open': mobileMenu }">
      <RouterLink to="/tasks" class="brand" @click="mobileMenu = false">
        <span class="brand-symbol"><AppIcon name="campus" :size="29" /></span>
        <span>
          <strong>校园跑腿</strong>
          <small>CAMPUS ERRANDS</small>
        </span>
      </RouterLink>
      <div class="sidebar-label">我的工作台</div>
      <nav aria-label="主导航">
        <RouterLink
          v-for="item in navigation"
          :key="item.to"
          :to="item.to"
          class="nav-item"
          :class="{ current: isActive(item) }"
          @click="mobileMenu = false"
        >
          <AppIcon :name="item.icon" :size="21" />
          <span>{{ item.title }}</span>
          <span v-if="item.to === '/notifications' && unread.data.value > 0" class="nav-badge">
            {{ unread.data.value > 99 ? '99+' : unread.data.value }}
          </span>
        </RouterLink>
        <template v-if="session.role === 'ADMIN'">
          <div class="sidebar-divider" />
          <div class="sidebar-label">平台管理</div>
          <RouterLink to="/admin" class="nav-item" @click="mobileMenu = false">
            <AppIcon name="shield" :size="21" />
            <span>管理工作台</span>
          </RouterLink>
        </template>
      </nav>
      <div class="sidebar-bottom">
        <div class="sidebar-illustration">
          <span class="illustration-circle" />
          <AppIcon name="campus" :size="57" />
          <span class="illustration-dash" />
        </div>
        <p>校园里的每一程</p>
        <small>任务有进度，收支有记录。</small>
        <div class="sidebar-version">
          校园跑腿平台
          <span>V1.0</span>
        </div>
        <button class="button text-button sidebar-logout" :disabled="signingOut" @click="logout">
          {{ signingOut ? '正在退出…' : '退出登录' }}
        </button>
      </div>
    </aside>
    <button
      v-if="mobileMenu"
      class="sidebar-overlay"
      aria-label="关闭导航"
      @click="mobileMenu = false"
    />
    <div class="main-column">
      <header class="topbar">
        <div class="breadcrumbs">
          <button
            class="icon-button menu-button"
            aria-label="切换导航"
            :aria-expanded="mobileMenu"
            @click="mobileMenu = !mobileMenu"
          >
            <AppIcon name="tasks" />
          </button>
          <span>工作台</span>
          <AppIcon name="chevron" :size="13" />
          <strong>{{ route.meta.title }}</strong>
        </div>
        <div class="topbar-right">
          <span v-if="isDemo" class="mock-indicator">本地演示</span>
          <span v-else-if="isMock" class="mock-indicator">Apifox Mock</span>
          <span v-else class="api-health-indicator" :class="apiStatus" role="status">
            {{
              apiStatus === 'checking'
                ? '正在检查后端'
                : apiStatus === 'connected'
                  ? '后端连接正常'
                  : '后端暂不可用'
            }}
          </span>
          <span class="topbar-date">{{ date }}</span>
          <RouterLink
            to="/notifications"
            class="icon-button notification-link"
            aria-label="打开通知中心"
          >
            <AppIcon name="bell" />
            <span v-if="unread.data.value > 0" class="notification-dot" />
          </RouterLink>
          <div class="user-profile">
            <span class="avatar">{{ session?.displayName?.slice(0, 1) || '同' }}</span>
            <div>
              <strong>{{ session?.displayName || '校园用户' }}</strong>
              <small>{{ session.role === 'ADMIN' ? '管理员' : '校园成员' }}</small>
            </div>
          </div>
          <button class="button text-button logout-button" :disabled="signingOut" @click="logout">
            {{ signingOut ? '正在退出…' : '退出登录' }}
          </button>
        </div>
      </header>
      <main id="main-content" class="main-content">
        <RouterView :key="session.userId" />
      </main>
      <footer class="site-footer">
        <span>校园跑腿平台</span>
        <span>任务有进度，收支有记录。</span>
      </footer>
    </div>
  </div>
  <main v-else id="main-content" class="main-content"><AsyncPanel loading /></main>
  <div class="toast-stack" aria-live="polite" aria-atomic="false">
    <div
      v-for="toast in toasts"
      :key="toast.id"
      class="toast"
      :class="toast.tone"
      :role="toast.tone === 'error' ? 'alert' : 'status'"
    >
      <AppIcon :name="toast.tone === 'error' ? 'close' : 'check'" :size="19" />
      <span>{{ toast.message }}</span>
      <button
        class="icon-button"
        aria-label="关闭提示"
        @click="toasts = toasts.filter((item) => item.id !== toast.id)"
      >
        <AppIcon name="close" :size="16" />
      </button>
    </div>
  </div>
</template>
