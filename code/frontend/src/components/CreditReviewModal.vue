<script setup>
import { computed, ref } from 'vue'
import { api } from '../api/index.js'
import { changed, notify, session, useResource } from '../composables/state.js'
import {
  adjustmentPreview, canReviewComplaint, createSubmissionKeys, creditLevels,
  creditReceiptMessage, isUserId, parseAdjustment, signedCredit, validateCreditDecision,
} from '../domain/credit.js'
import AppModal from './AppModal.vue'
import AsyncPanel from './AsyncPanel.vue'

const props = defineProps({ mode: { type: String, default: 'adjustment' }, record: { type: Object, required: true } })
const emit = defineEmits(['close'])
const userId = props.mode === 'complaint' ? props.record.targetUserId : props.record.userId
const busy = ref(false)
const error = ref('')
const form = ref({ result: '', adjustment: '', reason: '' })
const submissionKey = createSubmissionKeys()
const title = { complaint: '复核投诉扣分', 'user-review': '处理信用复核申请', adjustment: '管理员复核调整' }[props.mode]
const user = useResource((signal) => {
  if (session.value?.role !== 'ADMIN' || !isUserId(userId)) return Promise.reject(new Error('无法访问该用户的信用信息。'))
  return api.adminCredit(userId, signal)
})
const allowed = computed(() => {
  if (user.loading.value || user.error.value || !user.data.value || session.value?.role !== 'ADMIN') return false
  if (props.mode === 'complaint') return canReviewComplaint(props.record)
  if (props.mode === 'user-review') return user.data.value.reviewDecisionAllowed === true &&
    user.data.value.pendingReview?.status === 'PENDING' &&
    user.data.value.pendingReview.reviewId === props.record.pendingReview?.reviewId
  return user.data.value.adjustmentAllowed === true
})
const needsAdjustment = computed(() => props.mode === 'adjustment' || form.value.result === 'ADJUSTED')
const preview = computed(() => {
  if (user.loading.value || user.error.value) return null
  const delta = needsAdjustment.value ? form.value.adjustment :
    form.value.result === 'REVOKED' ? -props.record.creditActualAdjustment : 0
  return adjustmentPreview(user.data.value?.creditScore, delta)
})

async function submit() {
  if (busy.value) return
  error.value = ''
  if (!allowed.value) { error.value = '当前记录暂不可调整或复核，请刷新后核对。'; return }
  const body = props.mode === 'adjustment' ? { reason: form.value.reason.trim() } :
    { result: form.value.result, decisionNote: form.value.reason.trim() }
  if (needsAdjustment.value) body.adjustment = parseAdjustment(form.value.adjustment)
  error.value = validateCreditDecision(body, props.mode)
  if (error.value) return
  const resource = props.mode === 'complaint' ? props.record.complaintId :
    props.mode === 'user-review' ? props.record.pendingReview.reviewId : userId
  const key = submissionKey(`${props.mode}:${resource}`, body)
  busy.value = true
  try {
    const receipt = props.mode === 'complaint'
      ? await api.reviewComplaintCredit(resource, userId, body, key)
      : props.mode === 'user-review'
        ? await api.decideCreditReview(resource, userId, body, key)
        : await api.adjustCredit(userId, body, key)
    notify(creditReceiptMessage(receipt), receipt.status === 'FAILED' ? 'error' : 'success')
    changed()
    emit('close')
  } catch (failure) {
    error.value = failure.message
    if (failure.status === 409) {
      notify(failure.message, 'error')
      changed()
      emit('close')
    } else if (failure.code === 'INVALID_RESPONSE') changed()
  } finally { busy.value = false }
}
</script>
<template>
  <AppModal :title="title" :busy="busy" @close="$emit('close')">
    <form class="modal-body" @submit.prevent="submit">
      <AsyncPanel :loading="user.loading.value" :error="user.error.value" @retry="user.reload">
        <dl v-if="user.data.value" class="info-grid compact-info">
          <div><dt>被复核用户</dt><dd>#{{ userId }} {{ user.data.value.displayName || '' }}</dd></div>
          <div><dt>当前信用</dt><dd>{{ user.data.value.creditScore }} 分 · {{ creditLevels[user.data.value.creditLevel] }}</dd></div>
        </dl>
      </AsyncPanel>
      <p v-if="mode === 'complaint'" class="notice preserve-lines">
        原投诉 #{{ record.complaintId }}
        请求扣分 {{ signedCredit(record.creditAdjustment) }}，实际扣分 {{ signedCredit(record.creditActualAdjustment) }}。
        原信用记录 #{{ record.creditRecordId }}
      </p>
      <p v-if="mode === 'user-review'" class="notice preserve-lines">申请说明：{{ record.pendingReview.reason }}</p>
      <label v-if="mode !== 'adjustment'" class="field">
        信用复核结论
        <select v-model="form.result" required :disabled="busy">
          <option disabled value="">请选择复核结论</option>
          <option value="UPHELD">维持原处理</option>
          <option v-if="mode === 'complaint'" value="REVOKED">撤销原投诉扣分</option>
          <option value="ADJUSTED">追加信用调整</option>
        </select>
      </label>
      <label v-if="needsAdjustment" class="field">
        本次分数调整
        <input v-model="form.adjustment" aria-label="本次分数调整" aria-describedby="credit-adjustment-hint" inputmode="numeric" pattern="[+-]?[0-9]+" maxlength="11" required :disabled="busy" placeholder="例如：5 或 -2" />
        <span id="credit-adjustment-hint" class="field-hint">填写整数增减值；最终分数限制在 0–100，实际变动以服务端回执为准。</span>
      </label>
      <p v-if="preview && (needsAdjustment || form.result)" class="credit-preview">
        预计 {{ preview.before }} → {{ preview.after }} 分，实际变动 {{ signedCredit(preview.actual) }}。
      </p>
      <p v-if="form.result === 'REVOKED'" class="small muted">撤销按原记录实际扣分反向纠正，不按原请求档位补回。</p>
      <label class="field">
        复核说明与事实依据
        <textarea v-model="form.reason" required maxlength="1000" rows="4" :disabled="busy" />
      </label>
      <p class="notice">原始投诉和信用记录会保留，调整以新记录追加；未生效的事件不能视为已经完成记分。</p>
      <p v-if="!user.loading.value && !user.error.value && !allowed" class="notice">当前记录尚未开放此操作，或复核状态已变化。</p>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <footer class="modal-footer">
        <button type="button" class="button secondary" :disabled="busy" @click="$emit('close')">返回</button>
        <button class="button primary" :disabled="busy || !allowed">{{ busy ? '正在提交…' : '提交信用复核' }}</button>
      </footer>
    </form>
  </AppModal>
</template>
