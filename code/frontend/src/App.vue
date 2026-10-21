<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from './api/index.js'
import { clearSession, notify, session, toasts } from './composables/state.js'
import AppIcon from './components/AppIcon.vue'

const route = useRoute()
const router = useRouter()
const mobileMenu = ref(false)
const isDemo = import.meta.env.MODE === 'demo'
const signingOut = ref(false)
const navigation = [{ to: '/tasks', icon: 'pin', title: '任务列表' }]
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
onMounted(() => globalThis.addEventListener('campus:auth-expired', handleAuthExpired))
onBeforeUnmount(() => globalThis.removeEventListener('campus:auth-expired', handleAuthExpired))

function isActive(item) {
  return route.name === 'tasks' && item.to === '/tasks'
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
  <div v-else class="app-layout">
    <aside class="sidebar" :class="{ 'mobile-open': mobileMenu }">
      <RouterLink to="/tasks" class="brand" @click="mobileMenu = false">
        <span class="brand-symbol"><AppIcon name="campus" :size="29" /></span>
        <span>
          <strong>校园跑腿</strong>
          <small>CAMPUS ERRANDS</small>
        </span>
      </RouterLink>
      <div class="sidebar-label">任务发现</div>
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
        </RouterLink>
      </nav>
      <div class="sidebar-bottom">
        <div class="sidebar-illustration">
          <span class="illustration-circle" />
          <AppIcon name="campus" :size="57" />
          <span class="illustration-dash" />
        </div>
        <p>校园里的每一程</p>
        <small>浏览任务与当前状态。</small>
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
          <span>任务发现</span>
          <AppIcon name="chevron" :size="13" />
          <strong>{{ route.meta.title }}</strong>
        </div>
        <div class="topbar-right">
          <span v-if="isDemo" class="mock-indicator">本地演示</span>
          <span class="topbar-date">{{ date }}</span>
          <div class="user-profile">
            <span class="avatar">{{ session?.displayName?.slice(0, 1) || '同' }}</span>
            <div>
              <strong>{{ session?.displayName || '校园用户' }}</strong>
              <small>校园成员</small>
            </div>
          </div>
          <button class="button text-button logout-button" :disabled="signingOut" @click="logout">
            {{ signingOut ? '正在退出…' : '退出登录' }}
          </button>
        </div>
      </header>
      <main id="main-content" class="main-content">
        <RouterView />
      </main>
      <footer class="site-footer">
        <span>校园跑腿平台</span>
        <span>校园任务与当前状态。</span>
      </footer>
    </div>
  </div>
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
