<script setup>
import { computed, ref, watch } from 'vue'
import { api } from '../api/index.js'
import { notify, revision, session, useResource } from '../composables/state.js'
import { formatDate, validatePasswordChange, validateProfileDraft } from '../domain/tasks.js'
import AppIcon from '../components/AppIcon.vue'
import AsyncPanel from '../components/AsyncPanel.vue'

const profile = useResource((signal) => api.profile(signal), [revision])
const draft = ref({ displayName: '', phone: '' })
const profileError = ref('')
const savingProfile = ref(false)
const password = ref({ currentPassword: '', newPassword: '', confirmPassword: '' })
const passwordError = ref('')
const savingPassword = ref(false)

const rating = computed(() => profile.data.value?.rating)
// 评分展示接口预留：rating 由后端 /users/me/profile 返回，无评价时为 null。
const ratingText = computed(() =>
  Number.isFinite(rating.value) ? rating.value.toFixed(1) : '—',
)
const reviewCount = computed(() => profile.data.value?.reviewCount ?? 0)

function syncDraft() {
  const data = profile.data.value
  if (!data) return
  draft.value = { displayName: data.displayName ?? '', phone: data.phone ?? '' }
}
watch(profile.data, (data) => {
  if (data) syncDraft()
}, { immediate: true })

async function saveProfile() {
  if (savingProfile.value) return
  profileError.value = ''
  const validation = validateProfileDraft(draft.value)
  if (validation) {
    profileError.value = validation
    return
  }
  savingProfile.value = true
  try {
    const updated = await api.updateProfile({
      displayName: draft.value.displayName.trim(),
      phone: draft.value.phone.trim(),
    })
    if (session.value) {
      session.value = {
        ...session.value,
        displayName: updated.displayName,
        phone: updated.phone,
      }
    }
    profile.reload()
    notify('个人资料已更新。')
  } catch (error) {
    profileError.value = error.message
  } finally {
    savingProfile.value = false
  }
}

async function changePassword() {
  if (savingPassword.value) return
  passwordError.value = ''
  const validation = validatePasswordChange(password.value)
  if (validation) {
    passwordError.value = validation
    return
  }
  savingPassword.value = true
  try {
    await api.changePassword({
      currentPassword: password.value.currentPassword,
      newPassword: password.value.newPassword,
    })
    password.value = { currentPassword: '', newPassword: '', confirmPassword: '' }
    notify('密码已修改，下次登录请使用新密码。')
  } catch (error) {
    passwordError.value = error.message
  } finally {
    savingPassword.value = false
  }
}
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">USER CENTER</p>
        <h1>
          个人中心
          <span class="heading-dot">.</span>
        </h1>
        <p class="page-subtitle">管理你的账号资料与安全设置。</p>
      </div>
      <button class="button secondary" @click="profile.reload">
        <AppIcon name="refresh" :size="16" />
        刷新
      </button>
    </div>

    <AsyncPanel
      :loading="profile.loading.value"
      :error="profile.error.value"
      @retry="profile.reload"
    >
      <div v-if="profile.data.value" class="profile-grid">
        <article class="panel profile-card">
          <span class="avatar profile-avatar">
            {{ profile.data.value.displayName?.slice(0, 1) || '同' }}
          </span>
          <div>
            <h2>{{ profile.data.value.displayName }}</h2>
            <p class="muted">
              @{{ profile.data.value.username }} ·
              {{ profile.data.value.role === 'ADMIN' ? '管理员' : '校园成员' }}
            </p>
            <dl class="info-grid profile-info">
              <div>
                <dt>手机号</dt>
                <dd>{{ profile.data.value.phone || '未填写' }}</dd>
              </div>
              <div>
                <dt>注册时间</dt>
                <dd>{{ formatDate(profile.data.value.createdAt) }}</dd>
              </div>
            </dl>
          </div>
        </article>

        <article class="panel rating-card">
          <div>
            <AppIcon name="shield" :size="22" />
            <span>信誉评分</span>
          </div>
          <strong>
            {{ ratingText }}
            <small>分</small>
          </strong>
          <p class="small muted">
            基于 {{ reviewCount }} 条任务评价统计。
          </p>
          <dl class="info-grid">
            <div>
              <dt>信誉分（0-100）</dt>
              <dd>{{ profile.data.value.creditScore ?? '待接入' }}</dd>
            </div>
          </dl>
          <p class="notice">
            评分展示接口已预留；信誉分机制上线后将在此自动展示完整数据。
          </p>
        </article>
      </div>

      <div class="profile-forms">
        <form class="panel" @submit.prevent="saveProfile">
          <div class="panel-heading">
            <div>
              <h2>个人资料</h2>
              <p class="small muted">账号名不可修改，可调整显示名与手机号。</p>
            </div>
          </div>
          <label class="field">
            显示名
            <input
              v-model="draft.displayName"
              required
              maxlength="50"
              placeholder="其他用户看到的名字"
            />
          </label>
          <label class="field">
            手机号
            <input
              v-model="draft.phone"
              maxlength="30"
              placeholder="选填，仅支持数字、空格、+ 或 -"
            />
          </label>
          <p v-if="profileError" class="form-error" role="alert">{{ profileError }}</p>
          <footer class="form-footer">
            <button type="button" class="button secondary" @click="syncDraft">重置</button>
            <button class="button primary" :disabled="savingProfile">
              {{ savingProfile ? '正在保存…' : '保存资料' }}
            </button>
          </footer>
        </form>

        <form class="panel" @submit.prevent="changePassword">
          <div class="panel-heading">
            <div>
              <h2>修改密码</h2>
              <p class="small muted">新密码需为 8-128 位，且不能与当前密码相同。</p>
            </div>
          </div>
          <label class="field">
            当前密码
            <input
              v-model="password.currentPassword"
              type="password"
              required
              autocomplete="current-password"
            />
          </label>
          <div class="form-columns">
            <label class="field">
              新密码
              <input
                v-model="password.newPassword"
                type="password"
                required
                minlength="8"
                maxlength="128"
                autocomplete="new-password"
              />
            </label>
            <label class="field">
              确认新密码
              <input
                v-model="password.confirmPassword"
                type="password"
                required
                minlength="8"
                maxlength="128"
                autocomplete="new-password"
              />
            </label>
          </div>
          <p v-if="passwordError" class="form-error" role="alert">{{ passwordError }}</p>
          <footer class="form-footer">
            <button class="button primary" :disabled="savingPassword">
              {{ savingPassword ? '正在提交…' : '修改密码' }}
            </button>
          </footer>
        </form>
      </div>
    </AsyncPanel>
  </section>
</template>
