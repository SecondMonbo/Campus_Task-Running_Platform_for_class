<script setup>
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api/index.js'
import { changed, notify } from '../composables/state.js'
import AppIcon from '../components/AppIcon.vue'
import AppModal from '../components/AppModal.vue'
const route = useRoute()
const requestId = ref(String(route.query.requestId ?? ''))
const decision = ref('')
const note = ref('')
const confirm = ref(false)
const busy = ref(false)
const error = ref('')
const receipt = ref(null)
function confirmResponse() {
  confirm.value = true
  error.value = ''
}
async function respond() {
  if (busy.value) return
  if (!requestId.value.trim()) {
    error.value = '请填写申请编号。'
    return
  }
  busy.value = true
  error.value = ''
  try {
    receipt.value = await api.respondCancellation(requestId.value.trim(), {
      decision: decision.value,
      note: note.value.trim(),
    })
    confirm.value = false
    changed()
    notify('取消申请应答已提交。')
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">CANCELLATIONS & APPEALS</p>
        <h1>
          取消与申诉
          <span class="heading-dot">.</span>
        </h1>
        <p class="page-subtitle">申请变更，或对任务处理提出异议。</p>
      </div>
    </div>
    <div class="disputes-layout">
      <article class="panel form-panel">
        <h2>应答取消申请</h2>
        <p class="muted">收到对方的取消申请后，核对申请编号并选择应答。</p>
        <form @submit.prevent="confirmResponse">
          <label class="field">
            取消申请编号
            <input
              v-model="requestId"
              required
              maxlength="100"
              placeholder="请输入对方提供的申请编号"
            />
          </label>
          <label class="field">
            应答结果
            <select v-model="decision" required>
              <option disabled value="">请选择应答</option>
              <option value="APPROVE">同意取消</option>
              <option value="REJECT">拒绝取消，交由管理员裁决</option>
            </select>
          </label>
          <label class="field">
            应答说明
            <span class="muted">（选填）</span>
            <textarea v-model="note" maxlength="500" rows="4" placeholder="可以补充你的意见" />
          </label>
          <button class="button primary">确认应答</button>
        </form>
        <div v-if="receipt" class="success-box" role="status">
          <strong>应答已记录</strong>
          <p>
            申请 #{{ receipt.requestId }} ·
            {{
              {
                APPROVED: '已同意',
                PENDING_ADMIN: '待管理员处理',
                ARBITRATED: '已裁决',
                PENDING_RESPONSE: '待应答',
              }[receipt.status] || receipt.status
            }}
          </p>
          <RouterLink
            :to="{ name: 'task-detail', params: { taskId: receipt.taskId } }"
            class="text-link"
          >
            查看关联任务 →
          </RouterLink>
        </div>
      </article>
      <aside class="help-stack">
        <article class="panel help-card">
          <span class="stat-icon orange"><AppIcon name="dispute" :size="22" /></span>
          <h3>想要取消任务？</h3>
          <p>在“我的任务”中打开进行中或待验收的任务，选择“申请取消”并填写原因。</p>
          <p>同意后按规则取消；拒绝后转管理员裁决，争议期间暂停送达、完成与结算。</p>
          <RouterLink to="/my-tasks" class="text-link">前往我的任务 →</RouterLink>
        </article>
        <article class="panel help-card">
          <span class="stat-icon blue"><AppIcon name="shield" :size="22" /></span>
          <h3>任务完成后仍有异议？</h3>
          <p>打开已完成的任务，选择“提交申诉”。每位参与者最多提交一次，由管理员处理。</p>
          <p>申诉不会改变“已完成”状态，也不会修改原始结算流水。</p>
        </article>
      </aside>
    </div>
    <AppModal v-if="confirm" title="确认应答取消申请" :busy="busy" @close="confirm = false">
      <form class="modal-body" @submit.prevent="respond">
        <p>
          申请编号：
          <strong>{{ requestId }}</strong>
        </p>
        <p class="notice">
          {{
            decision === 'APPROVE'
              ? '同意后任务将取消，并按规则处理冻结报酬。请确认这是你要应答的申请。'
              : '拒绝后本次申请将转管理员裁决，期间该任务暂停送达、确认完成与结算。'
          }}
        </p>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <footer class="modal-footer">
          <button type="button" class="button secondary" :disabled="busy" @click="confirm = false">
            返回
          </button>
          <button class="button primary" :disabled="busy">
            {{ busy ? '正在提交…' : '确定' }}
          </button>
        </footer>
      </form>
    </AppModal>
  </section>
</template>
