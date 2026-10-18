<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from './api/index.js'
import { loadSession, revision, session, toasts, useResource } from './composables/state.js'
import AppIcon from './components/AppIcon.vue'
import AsyncPanel from './components/AsyncPanel.vue'
const route = useRoute()
const mobileMenu = ref(false)
const isMock = import.meta.env.MODE === 'apifox'
const auth = useResource(loadSession)
const unread = useResource(
  async (signal) => {
    if (!session.value) return null
    return (await api.notifications({ unreadOnly: true, page: 0, size: 10 }, signal)).total
  },
  [session, revision],
)
const navigation = [
  { to: '/my-tasks', icon: 'tasks', title: '我的任务' },
  { to: '/wallet', icon: 'wallet', title: '钱包与流水' },
  { to: '/notifications', icon: 'bell', title: '通知中心' },
  { to: '/disputes', icon: 'dispute', title: '取消与申诉' },
]
const date = new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Shanghai',
  month: 'long',
  day: 'numeric',
  weekday: 'long',
}).format(new Date())
</script>
<template>
  <a href="#main-content" class="skip-link">跳转到主要内容</a>
  <div class="app-layout">
    <aside class="sidebar" :class="{ 'mobile-open': mobileMenu }">
      <RouterLink to="/my-tasks" class="brand" @click="mobileMenu = false">
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
          @click="mobileMenu = false"
        >
          <AppIcon :name="item.icon" :size="21" />
          <span>{{ item.title }}</span>
          <span v-if="item.to === '/notifications' && unread.data.value > 0" class="nav-badge">
            {{ unread.data.value > 99 ? '99+' : unread.data.value }}
          </span>
        </RouterLink>
        <template v-if="session?.role === 'ADMIN'">
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
          <span v-if="isMock" class="mock-indicator">Apifox Mock</span>
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
              <small>{{ session?.role === 'ADMIN' ? '管理员' : '校园成员' }}</small>
            </div>
          </div>
        </div>
      </header>
      <main id="main-content" class="main-content">
        <AsyncPanel :loading="auth.loading.value" :error="auth.error.value" @retry="auth.reload">
          <RouterView v-if="session" />
        </AsyncPanel>
      </main>
      <footer class="site-footer">
        <span>校园跑腿平台</span>
        <span>任务有进度，收支有记录。</span>
      </footer>
    </div>
    <div class="toast-stack" aria-live="polite" aria-atomic="false">
      <div
        v-for="toast in toasts"
        :key="toast.id"
        class="toast"
        :class="toast.tone"
        :role="toast.tone === 'error' ? 'alert' : 'status'"
      >
        <AppIcon :name="toast.tone === 'error' ? 'dispute' : 'check'" :size="19" />
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
  </div>
</template>
