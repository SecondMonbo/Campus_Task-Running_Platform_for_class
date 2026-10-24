<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api/index.js'
import { notify, setSession } from '../composables/state.js'
import AppIcon from '../components/AppIcon.vue'

const route = useRoute()
const router = useRouter()
const registering = computed(() => route.name === 'register')
const busy = ref(false)
const error = ref('')
const form = reactive({
  username: '',
  password: '',
  displayName: '',
  phone: '',
  confirmPassword: '',
})
watch(registering, () => {
  error.value = ''
  form.confirmPassword = ''
})

function redirectAfterAuth() {
  const destination = route.query.redirect
  if (
    typeof destination !== 'string' ||
    !destination.startsWith('/') ||
    destination.startsWith('//') ||
    destination.includes('\\') ||
    ['/login', '/register'].includes(destination.split(/[?#]/, 1)[0])
  )
    return '/tasks'
  return destination
}

async function submit() {
  error.value = ''
  if (registering.value && form.password !== form.confirmPassword) {
    error.value = '两次输入的密码不一致。'
    return
  }
  if (busy.value) return

  busy.value = true
  try {
    const user = registering.value
      ? await api.register({
          username: form.username.trim(),
          password: form.password,
          displayName: form.displayName.trim(),
          ...(form.phone.trim() ? { phone: form.phone.trim() } : {}),
        })
      : await api.login({ username: form.username.trim(), password: form.password })
    setSession(user)
    notify(registering.value ? '账号创建成功，欢迎加入校园跑腿。' : '登录成功。')
    await router.replace(redirectAfterAuth())
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <main id="main-content" class="auth-shell">
    <section class="auth-card" :class="{ 'register-card': registering }">
      <aside class="auth-intro">
        <RouterLink to="/login" class="auth-brand">
          <span class="brand-symbol"><AppIcon name="pin" :size="27" /></span>
          <span>
            <strong>校园跑腿</strong>
            <small>CAMPUS ERRANDS</small>
          </span>
        </RouterLink>
        <div class="auth-intro-copy">
          <p class="eyebrow">A LITTLE HELP, A BETTER CAMPUS</p>
          <h1>校园里的每一程，<br />都有人接力。</h1>
          <p>送的不仅是物品，<br />更是一份准时抵达的安心和便利。</p>
        </div>
        <div class="auth-decoration" aria-hidden="true">
          <span class="auth-orbit orbit-one" />
          <span class="auth-orbit orbit-two" />
          <AppIcon name="pin" :size="76" />
        </div>
        <span class="auth-footer-note">任务有进度，收支有记录。</span>
      </aside>

      <section
        class="auth-form-side"
        :aria-labelledby="registering ? 'register-title' : 'login-title'"
      >
        <div class="auth-topline">
          <span>校园跑腿平台</span>
          <span>安全会话登录</span>
        </div>
        <div class="auth-form-wrap">
          <p class="eyebrow">{{ registering ? 'GET STARTED' : 'WELCOME BACK' }}</p>
          <h2 :id="registering ? 'register-title' : 'login-title'">
            {{ registering ? '创建你的账号' : '欢迎回来' }}
          </h2>
          <p v-if="registering" class="auth-subtitle">
            填写基本信息，开始发现校园里的帮忙需求。
          </p>

          <form class="auth-form" @submit.prevent="submit">
            <label class="field">
              用户名
              <input
                v-model="form.username"
                name="username"
                autocomplete="username"
                required
                minlength="3"
                maxlength="50"
                placeholder="请输入用户名"
              />
            </label>
            <label v-if="registering" class="field">
              显示名称
              <input
                v-model="form.displayName"
                name="displayName"
                autocomplete="nickname"
                required
                minlength="1"
                maxlength="50"
                placeholder="例如：林同学"
              />
            </label>
            <label v-if="registering" class="field">
              <span>联系方式 <small class="optional-label">选填</small></span>
              <input
                v-model="form.phone"
                name="phone"
                type="tel"
                autocomplete="tel"
                maxlength="30"
                placeholder="可稍后补充"
              />
            </label>
            <label class="field">
              密码
              <input
                v-model="form.password"
                name="password"
                type="password"
                :autocomplete="registering ? 'new-password' : 'current-password'"
                required
                :minlength="registering ? 8 : undefined"
                maxlength="128"
                :placeholder="registering ? '至少 8 个字符' : '请输入密码'"
              />
            </label>
            <label v-if="registering" class="field">
              确认密码
              <input
                v-model="form.confirmPassword"
                name="confirmPassword"
                type="password"
                autocomplete="new-password"
                required
                minlength="8"
                maxlength="128"
                placeholder="再输入一次密码"
              />
            </label>
            <p v-if="error" class="form-error" role="alert">{{ error }}</p>
            <button class="button primary auth-submit" :disabled="busy">
              {{ busy ? '请稍候…' : registering ? '创建账号' : '登录' }}
              <AppIcon v-if="!busy" name="arrow" :size="17" />
            </button>
          </form>
          <p class="auth-switch">
            {{ registering ? '已经有账号？' : '还没有账号？' }}
            <RouterLink :to="registering ? '/login' : '/register'">
              {{ registering ? '返回登录' : '立即注册' }}
            </RouterLink>
          </p>
        </div>
      </section>
    </section>
  </main>
</template>
