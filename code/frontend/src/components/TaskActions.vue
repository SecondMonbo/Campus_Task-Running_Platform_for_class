<script setup>
import { computed, ref } from 'vue'
import { api } from '../api/index.js'
import { changed, notify, session } from '../composables/state.js'
import { taskPermissions } from '../domain/tasks.js'
import AppModal from './AppModal.vue'

const props = defineProps({ task: { type: Object, required: true }, compact: Boolean })
const emit = defineEmits(['refresh'])
const permissions = computed(() => taskPermissions(props.task, session.value))
const action = ref('')
const busy = ref(false)
const error = ref('')
const form = ref({})
const receipt = ref(null)
const titles = {
  accept: '确认接取任务',
  deliver: '确认任务已送达',
  complete: '验收并确认完成',
  cancel: '申请取消任务',
  appeal: '提交任务申诉',
  complain: '提交投诉',
}
const copy = {
  accept: '接取后请按约定完成任务；你可以在“我接取的”中查看执行进度。',
  deliver: '请确认已完成约定的取送事项。送达后，任务进入待验收，报酬继续冻结。',
  complete: '请确认已验收无误。完成后，冻结的任务报酬将全额结算给跑腿员。',
  cancel: '申请提交后由对方应答，任务不会立即取消。对方拒绝后将转管理员裁决。',
  appeal: '申诉不会撤销任务完成状态或原结算。每位参与者对同一任务最多提交一次。',
  complain: '请如实描述任务中的问题，提交后由管理员处理。',
}
function open(kind) {
  action.value = kind
  error.value = ''
  form.value = { reason: '', description: '', category: '' }
}
async function submit() {
  if (busy.value) return
  error.value = ''
  if (action.value === 'cancel' && !form.value.reason.trim()) {
    error.value = '请填写取消原因。'
    return
  }
  if (['appeal', 'complain'].includes(action.value) && !form.value.description.trim()) {
    error.value = '请填写具体说明。'
    return
  }
  busy.value = true
  const kind = action.value
  try {
    let result
    if (['accept', 'deliver', 'complete'].includes(kind))
      result = await api[kind](props.task.taskId)
    if (kind === 'cancel')
      result = await api.cancel(props.task.taskId, { reason: form.value.reason.trim() })
    if (kind === 'appeal')
      result = await api.appeal(props.task.taskId, {
        reason: form.value.reason,
        description: form.value.description.trim(),
      })
    if (kind === 'complain')
      result = await api.complain({
        taskId: props.task.taskId,
        targetUserId:
          String(props.task.publisherId) === String(session.value.userId)
            ? props.task.runnerId
            : props.task.publisherId,
        category: form.value.category,
        description: form.value.description.trim(),
      })
    const messages = {
      accept: '接单成功。',
      deliver: '已送达，等待发布者验收。',
      complete: '任务已完成，报酬已结算。',
      cancel: '取消申请已提交。',
      appeal: '申诉已提交，等待管理员处理。',
      complain: '投诉已提交，等待管理员处理。',
    }
    receipt.value =
      kind === 'cancel'
        ? { label: '取消申请编号', id: result.requestId }
        : kind === 'appeal'
          ? { label: '申诉编号', id: result.appealId }
          : kind === 'complain'
            ? { label: '投诉编号', id: result.complaintId }
            : null
    notify(messages[kind])
    action.value = ''
    changed()
    emit('refresh')
  } catch (failure) {
    error.value = failure.message
    if (failure.status === 409) {
      notify(`${failure.message} 正在刷新任务信息。`, 'error')
      action.value = ''
      changed()
      emit('refresh')
    }
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="task-actions">
    <button v-if="permissions.accept" class="button primary" @click="open('accept')">
      接取任务
    </button>
    <button v-if="permissions.deliver" class="button primary" @click="open('deliver')">
      确认送达
    </button>
    <button v-if="permissions.complete" class="button primary" @click="open('complete')">
      验收并完成
    </button>
    <template v-if="!compact">
      <button v-if="permissions.cancel" class="button secondary" @click="open('cancel')">
        申请取消
      </button>
      <button v-if="permissions.appeal" class="button secondary" @click="open('appeal')">
        提交申诉
      </button>
      <button v-if="permissions.complain" class="button text-button" @click="open('complain')">
        投诉
      </button>
    </template>
  </div>
  <p v-if="receipt && !compact" class="receipt">
    {{ receipt.label }}：
    <strong>{{ receipt.id }}</strong>
    。请保留编号以便查询和沟通。
  </p>
  <AppModal v-if="action" :title="titles[action]" :busy="busy" @close="action = ''">
    <form class="modal-body" @submit.prevent="submit">
      <p class="modal-task-title">{{ task.title }}</p>
      <p class="muted">{{ copy[action] }}</p>
      <label v-if="action === 'cancel'" class="field">
        取消原因
        <textarea
          v-model="form.reason"
          required
          maxlength="200"
          rows="3"
          placeholder="请说明取消原因"
        />
      </label>
      <template v-if="action === 'appeal'">
        <label class="field">
          申诉理由
          <select v-model="form.reason" required>
            <option disabled value="">请选择理由</option>
            <option value="FULFILLMENT_ISSUE">履约问题</option>
            <option value="SETTLEMENT_ISSUE">结算问题</option>
            <option value="OTHER">其他</option>
          </select>
        </label>
        <label class="field">
          申诉说明
          <textarea
            v-model="form.description"
            required
            maxlength="500"
            rows="4"
            placeholder="请填写具体问题与诉求"
          />
        </label>
      </template>
      <template v-if="action === 'complain'">
        <label class="field">
          投诉类型
          <select v-model="form.category" required>
            <option disabled value="">请选择类型</option>
            <option>对方违约</option>
            <option>失联</option>
            <option>物品损坏</option>
            <option>其他</option>
          </select>
        </label>
        <label class="field">
          投诉内容
          <textarea
            v-model="form.description"
            required
            maxlength="2000"
            rows="4"
            placeholder="请描述事情经过"
          />
        </label>
      </template>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <footer class="modal-footer">
        <button type="button" class="button secondary" :disabled="busy" @click="action = ''">
          返回
        </button>
        <button class="button primary" :disabled="busy">{{ busy ? '正在提交…' : '确定' }}</button>
      </footer>
    </form>
  </AppModal>
</template>
