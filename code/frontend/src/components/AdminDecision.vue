<script setup>
import { computed, ref } from 'vue'
import { api } from '../api/index.js'
import { changed, notify, useResource } from '../composables/state.js'
import {
  formatNumber,
  validateAppealDecision,
  validateCancellationRuling,
} from '../domain/tasks.js'
import AppModal from './AppModal.vue'
import AsyncPanel from './AsyncPanel.vue'
import { allowedComplaintActions, complaintActions, validateComplaintDecision } from '../domain/admin.js'
import { complaintPenalties, signedCredit } from '../domain/credit.js'
const props = defineProps({ kind: String, record: Object })
const emit = defineEmits(['close'])
const busy = ref(false)
const error = ref('')
const form = ref({
  ruling: 'SUPPORT_REQUESTER_CANCEL',
  refundAmount: 0,
  runnerAmount: 0,
  rulingDetail: '',
  result: 'REJECTED',
  transferAmount: 0,
  decisionNote: '',
  actions: ['LOG_ONLY'],
  creditAdjustment: '',
  complaintResult: '',
})
const allowedActions = computed(() => allowedComplaintActions(props.record))
const creditAvailable = computed(() => allowedActions.value.includes('CREDIT_ADJUSTMENT'))
if (props.kind === 'complaints') form.value.actions = form.value.actions.filter((action) => allowedActions.value.includes(action))
const {
  data: task,
  loading,
  error: taskError,
  reload,
} = useResource((signal) =>
  props.record.taskId ? api.task(props.record.taskId, signal) : Promise.resolve(null),
)
const titles = {
  'cancellation-requests': '裁决取消争议',
  appeals: '裁决已完成任务申诉',
  complaints: '处理投诉',
}
function resetAllocation() {
  form.value.refundAmount = 0
  form.value.runnerAmount = 0
}
async function submit() {
  if (busy.value) return
  error.value = ''
  const values = form.value
  let body
  if (props.kind === 'cancellation-requests') {
    body = {
      ruling: values.ruling,
      refundAmount: Number(values.refundAmount),
      runnerAmount: Number(values.runnerAmount),
      rulingDetail: values.rulingDetail.trim(),
    }
    error.value = validateCancellationRuling(body, task.value?.reward)
  } else if (props.kind === 'appeals') {
    body = {
      result: values.result,
      transferAmount: Number(values.transferAmount),
      decisionNote: values.decisionNote.trim(),
    }
    error.value = validateAppealDecision(body)
  } else {
    body = { actions: [...values.actions], decisionNote: values.decisionNote.trim() }
    if (creditAvailable.value && values.complaintResult) body.complaintResult = values.complaintResult
    if (body.actions.includes('CREDIT_ADJUSTMENT')) body.creditAdjustment = Number(values.creditAdjustment)
    error.value = validateComplaintDecision(body, props.record)
  }
  if (error.value) return
  busy.value = true
  try {
    if (props.kind === 'cancellation-requests') await api.arbitrate(props.record.requestId, body)
    else if (props.kind === 'appeals') await api.decideAppeal(props.record.appealId, body)
    else {
      const receipt = await api.handleComplaint(props.record.complaintId, body, props.record.targetUserId)
      if (body.actions.includes('CREDIT_ADJUSTMENT')) {
        notify(receipt.creditStatus === 'APPLIED'
          ? `投诉处理已保存，信用分实际变动 ${signedCredit(receipt.creditActualAdjustment)}。`
          : receipt.creditStatus === 'PENDING' ? '投诉处理已保存，信用扣分等待记分。' : '投诉处理已保存，信用记分异常，请核对记录。',
        receipt.creditStatus === 'FAILED' ? 'error' : 'success')
      } else notify('投诉处理记录已保存。')
    }
    if (props.kind !== 'complaints') notify('处理结果已提交。')
    changed()
    emit('close')
  } catch (failure) {
    error.value = failure.message
    if (props.kind === 'complaints' && failure.code === 'INVALID_RESPONSE') changed()
    if (props.kind === 'complaints' && failure.status === 409) {
      notify(failure.message, 'error')
      changed()
      emit('close')
    }
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <AppModal :title="titles[kind]" :busy="busy" @close="$emit('close')">
    <form class="modal-body" @submit.prevent="submit">
      <dl class="info-grid compact-info">
        <div>
          <dt>记录编号</dt>
          <dd>{{ record.requestId || record.appealId || record.complaintId }}</dd>
        </div>
        <div>
          <dt>关联任务</dt>
          <dd>{{ record.taskId || '—' }}</dd>
        </div>
        <template v-if="kind === 'complaints'">
          <div>
            <dt>投诉人 / 被投诉人</dt>
            <dd>{{ record.reporterId }} / {{ record.targetUserId || '—' }}</dd>
          </div>
          <div>
            <dt>投诉类型</dt>
            <dd>{{ record.category }}</dd>
          </div>
        </template>
      </dl>
      <p v-if="record.reason || record.description" class="notice preserve-lines">
        {{ record.reason }}
        <template v-if="record.description">
          <br />
          {{ record.description }}
        </template>
      </p>
      <AsyncPanel v-if="record.taskId" :loading="loading" :error="taskError" @retry="reload">
        <p v-if="task" class="small muted">
          {{ task.title }} · 任务报酬 {{ formatNumber(task.reward) }} 虚拟币
        </p>
      </AsyncPanel>
      <template v-if="kind === 'cancellation-requests'">
        <label class="field">
          裁决结论
          <select v-model="form.ruling" required @change="resetAllocation">
            <option value="SUPPORT_REQUESTER_CANCEL">支持申请方取消</option>
            <option value="SUPPORT_CONTINUE">支持继续履约</option>
            <option value="CANCEL_PROPORTIONALLY">按比例取消</option>
          </select>
        </label>
        <div class="form-columns">
          <label class="field">
            退还发布者
            <input
              v-model="form.refundAmount"
              type="number"
              min="0"
              step="1"
              required
              :readonly="form.ruling === 'SUPPORT_CONTINUE'"
            />
          </label>
          <label class="field">
            支付跑腿员
            <input
              v-model="form.runnerAmount"
              type="number"
              min="0"
              step="1"
              required
              :readonly="form.ruling === 'SUPPORT_CONTINUE'"
            />
          </label>
        </div>
        <p class="small muted">取消时两项之和需等于冻结报酬；继续履约时，两项均为 0。</p>
        <label class="field">
          裁决说明
          <textarea v-model="form.rulingDetail" required maxlength="500" rows="3" />
        </label>
      </template>
      <template v-else-if="kind === 'appeals'">
        <label class="field">
          裁决结论
          <select v-model="form.result" required @change="form.transferAmount = 0">
            <option value="SUPPORTED">支持申诉</option>
            <option value="PARTIALLY_SUPPORTED">部分支持</option>
            <option value="REJECTED">驳回申诉</option>
          </select>
        </label>
        <label class="field">
          划转金额（虚拟币）
          <input
            v-model="form.transferAmount"
            type="number"
            :min="form.result === 'REJECTED' ? 0 : 1"
            step="1"
            required
            :readonly="form.result === 'REJECTED'"
          />
        </label>
        <p class="small muted">
          划转金额不能超过被申诉方可用余额；驳回时为 0。任务保持已完成，原流水不变。
        </p>
        <label class="field">
          裁决说明
          <textarea v-model="form.decisionNote" required maxlength="500" rows="3" />
        </label>
      </template>
      <template v-else>
        <label v-if="creditAvailable" class="field">
          投诉结论
          <select v-model="form.complaintResult" :disabled="busy" @change="form.complaintResult === 'REJECTED' && (form.actions = form.actions.filter((action) => action !== 'CREDIT_ADJUSTMENT'))">
            <option value="">仅处理留痕</option>
            <option value="SUPPORTED">投诉成立</option>
            <option value="REJECTED">投诉驳回</option>
          </select>
        </label>
        <fieldset class="checkbox-group">
          <legend>处理方式（可多选）</legend>
          <label v-for="(label, value) in complaintActions" :key="value">
            <input
              v-model="form.actions"
              type="checkbox"
              :value="value"
              :disabled="busy || !allowedActions.includes(value) || (value === 'CREDIT_ADJUSTMENT' && form.complaintResult !== 'SUPPORTED')"
            />
            {{ label }}
            <span v-if="!allowedActions.includes(value)" class="small muted">{{ value === 'CREDIT_ADJUSTMENT' && !record.targetUserId ? '无扣分对象' : '暂未开放' }}</span>
          </label>
        </fieldset>
        <label v-if="form.actions.includes('CREDIT_ADJUSTMENT')" class="field">
          扣分档位
          <select v-model.number="form.creditAdjustment" required :disabled="busy" aria-label="扣分档位">
            <option disabled value="">请选择扣分档位</option>
            <option v-for="(penalty, value) in complaintPenalties" :key="value" :value="Number(value)">{{ penalty.label }}</option>
          </select>
          <span class="field-hint">{{ complaintPenalties[form.creditAdjustment]?.basis || '档位依据事实和证据选择，不由投诉分类自动决定。' }}</span>
        </label>
        <label class="field">
          处理说明
          <textarea
            v-model="form.decisionNote"
            required
            maxlength="1000"
            rows="4"
            :disabled="busy"
            placeholder="说明处理依据、涉及对象与处理结论"
          />
        </label>
      </template>
      <p v-if="kind === 'complaints'" class="notice">
        {{ creditAvailable ? '投诉成立并选择扣分后，请在处理说明中记录事实和档位依据；实际分值及记分进度以处理回执为准。' : '当前记录仅支持已开放的处理留痕；信用扣分需服务端开放后使用。' }}
        提醒与线下移交需另行完成，账务处理、功能限制和账号冻结暂未开放。
      </p>
      <p v-else class="notice">请核对处理结论和金额后提交。</p>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <footer class="modal-footer">
        <button type="button" class="button secondary" :disabled="busy" @click="$emit('close')">
          返回
        </button>
        <button
          class="button primary"
          :disabled="busy || (kind === 'cancellation-requests' && (loading || !!taskError))"
        >
          {{ busy ? '正在提交…' : '确认提交' }}
        </button>
      </footer>
    </form>
  </AppModal>
</template>
