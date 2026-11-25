<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api/index.js'
import { useCurrentCredit } from '../composables/credit.js'
import { changed, notify, session } from '../composables/state.js'
import { createSubmissionKeys, creditLevels, creditNotice, validateCreditReason } from '../domain/credit.js'
import AppModal from './AppModal.vue'

const route = useRoute()
const current = useCurrentCredit()
const notice = computed(() => creditNotice(current.data.value))
const publishReview = computed(() => route.name === 'task-publish' && current.data.value?.requiresPublishReview)
const reviewOpen = ref(false)
const reason = ref('')
const busy = ref(false)
const error = ref('')
const submissionKey = createSubmissionKeys()
const pending = computed(() => current.data.value?.pendingReview?.status === 'PENDING')

async function requestReview() {
  if (busy.value) return
  error.value = validateCreditReason(reason.value)
  if (error.value) return
  const credit = current.data.value
  if (!credit || credit.userId !== session.value?.userId || credit.reviewRequestAllowed !== true || pending.value) {
    error.value = '当前状态不允许申请复核，请刷新信用状态后核对。'
    return
  }
  const body = { reason: reason.value.trim() }
  busy.value = true
  try {
    await api.requestCreditReview(body, submissionKey(credit.userId, body), credit.userId)
    notify('信用复核申请已提交，等待管理员处理。')
    changed()
    reviewOpen.value = false
    reason.value = ''
  } catch (failure) {
    error.value = failure.message
    if (failure.status === 409) changed()
  } finally { busy.value = false }
}
</script>
<template>
  <aside v-if="current.loading.value" class="credit-notice neutral" role="status">
    正在确认信用状态…
  </aside>
  <aside v-else-if="current.error.value" class="credit-notice neutral" aria-label="信用状态暂不可用">
    <div>
      <strong>暂时无法读取信用状态</strong>
      <p>未获取到有效分数与权限，请稍后刷新。业务操作仍由服务端校验。</p>
    </div>
    <button type="button" class="button small secondary" @click="current.reload">刷新信用状态</button>
  </aside>
  <aside v-else-if="notice || publishReview" class="credit-notice" :class="notice?.tone || 'warning'" aria-label="信用提醒">
    <div>
      <strong>{{ notice?.title || '发布任务需要复核' }}</strong>
      <span class="credit-inline-score">
        {{ current.data.value.creditScore }} 分 · {{ creditLevels[current.data.value.creditLevel] }}
      </span>
      <p>{{ notice?.message || '新任务需经管理员复核，通过后才会出现在公开任务列表中。' }}</p>
      <p v-if="current.data.value.restrictionActive" class="small">
        连续履约进度 {{ current.data.value.consecutiveCompletedCount }}/10；发生负向信用事件会重新计数。
        没有在途任务时，可通过信用复核申请说明情况。
      </p>
      <p v-if="pending" class="small">信用复核申请已提交，正在等待管理员处理。</p>
    </div>
    <div class="credit-notice-actions">
      <button type="button" class="button small secondary" @click="current.reload">刷新信用状态</button>
      <button
        v-if="current.data.value.reviewRequestAllowed === true && !pending"
        type="button" class="button small secondary" @click="reviewOpen = true; error = ''"
      >申请信用复核</button>
    </div>
  </aside>
  <AppModal v-if="reviewOpen" title="申请信用复核" :busy="busy" @close="reviewOpen = false">
    <form class="modal-body" @submit.prevent="requestReview">
      <p class="notice">请说明信用处分需要复核的原因与事实依据。申请不会立即改变分数或解除限制。</p>
      <label class="field">
        复核申请说明
        <textarea v-model="reason" required maxlength="1000" rows="5" :disabled="busy" />
      </label>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <footer class="modal-footer">
        <button type="button" class="button secondary" :disabled="busy" @click="reviewOpen = false">返回</button>
        <button class="button primary" :disabled="busy">{{ busy ? '正在提交…' : '提交复核申请' }}</button>
      </footer>
    </form>
  </AppModal>
</template>
