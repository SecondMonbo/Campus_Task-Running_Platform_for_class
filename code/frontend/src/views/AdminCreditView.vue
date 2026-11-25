<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api/index.js'
import { revision, session, usePaged, useResource } from '../composables/state.js'
import { creditEvents, creditLevels, isUserId, signedCredit } from '../domain/credit.js'
import { formatDate } from '../domain/tasks.js'
import AppIcon from '../components/AppIcon.vue'
import AsyncPanel from '../components/AsyncPanel.vue'
import CreditReviewModal from '../components/CreditReviewModal.vue'
import PageControls from '../components/PageControls.vue'

const route = useRoute()
const initialId = typeof route.query.userId === 'string' && isUserId(route.query.userId) ? route.query.userId : ''
const form = ref({ userId: initialId, creditLevel: '', reviewPending: '' })
const filters = ref({ ...(initialId ? { userId: initialId } : {}) })
const filterError = ref('')
const selectedId = ref(initialId)
const eventType = ref('')
const operation = ref(null)
const isAdmin = () => session.value?.role === 'ADMIN'
const denied = () => Promise.reject(new Error('当前账号没有信用管理权限。'))
const list = usePaged((pagination, signal) => isAdmin() ?
  api.adminCredits({ ...pagination, ...filters.value }, signal) : denied(), [filters])
const detail = useResource((signal) => {
  if (!isAdmin()) return denied()
  return selectedId.value ? api.adminCredit(selectedId.value, signal) : Promise.resolve(null)
}, [selectedId, revision])
const credit = computed(() => !detail.loading.value && !detail.error.value &&
  detail.data.value?.userId === selectedId.value ? detail.data.value : null)
const records = usePaged((pagination, signal) => {
  if (!isAdmin()) return denied()
  return credit.value ? api.adminCreditRecords(selectedId.value, { ...pagination, eventType: eventType.value }, signal) :
    Promise.resolve({ items: [], total: 0, ...pagination })
}, [() => credit.value?.userId, selectedId, eventType])

function applyFilters() {
  filterError.value = ''
  const userId = form.value.userId.trim()
  if (userId && !isUserId(userId)) { filterError.value = '用户编号必须是有效的正整数编号。'; return }
  selectedId.value = ''
  operation.value = null
  filters.value = { userId, creditLevel: form.value.creditLevel,
    ...(form.value.reviewPending === 'true' ? { reviewPending: true } : {}) }
}
function resetFilters() {
  form.value = { userId: '', creditLevel: '', reviewPending: '' }
  applyFilters()
}
function selectUser(value) {
  selectedId.value = value
  eventType.value = ''
  operation.value = null
}
function refresh() {
  list.reload()
  detail.reload()
  records.reload()
}
</script>
<template>
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">CREDIT ADMINISTRATION</p>
        <h1>信用分管理<span class="heading-dot">.</span></h1>
        <p class="page-subtitle">查询分值与限制状态，核对信用事件，保留每次复核依据。</p>
      </div>
      <div class="heading-actions">
        <RouterLink to="/admin" class="button secondary">管理工作台</RouterLink>
        <button class="button secondary" @click="refresh"><AppIcon name="refresh" :size="16" />刷新信用查询</button>
      </div>
    </div>
    <section class="panel">
      <form class="admin-filters" @submit.prevent="applyFilters">
        <label class="inline-field">信用用户编号<input v-model="form.userId" inputmode="numeric" maxlength="19" placeholder="全部用户" /></label>
        <label class="inline-field">信用档位
          <select v-model="form.creditLevel" aria-label="信用档位筛选">
            <option value="">全部档位</option>
            <option v-for="(label, value) in creditLevels" :key="value" :value="value">{{ label }}</option>
          </select>
        </label>
        <label class="inline-field">复核申请
          <select v-model="form.reviewPending" aria-label="复核申请筛选"><option value="">全部用户</option><option value="true">有待处理申请</option></select>
        </label>
        <button class="button secondary small">查询信用分</button>
        <button type="button" class="button text-button small" @click="resetFilters">清空筛选</button>
        <p v-if="filterError" class="form-error" role="alert">{{ filterError }}</p>
      </form>
      <AsyncPanel :loading="list.loading.value" :error="list.error.value" :empty="!list.items.value.length" empty-text="暂无符合条件的信用记录" @retry="list.reload">
        <div class="table-wrap credit-table">
          <table>
            <thead><tr><th>用户</th><th>信用分 / 档位</th><th>当前权限</th><th>复核申请</th><th>更新时间</th><th>操作</th></tr></thead>
            <tbody>
              <tr v-for="item in list.items.value" :key="item.userId">
                <td><strong>{{ item.displayName || '用户' }}</strong><small>#{{ item.userId }}</small></td>
                <td><strong class="credit-score">{{ item.creditScore }}</strong><span class="credit-badge" :class="item.creditLevel">{{ creditLevels[item.creditLevel] }}</span></td>
                <td><span>{{ item.canAcceptNormalTasks ? '可接普通任务' : '不能接普通任务' }}</span><small>{{ item.requiresPublishReview ? '新发布任务需复核' : '新发布任务按常规流程' }}</small></td>
                <td>{{ item.pendingReview?.status === 'PENDING' ? '待管理员复核' : '—' }}</td>
                <td>{{ formatDate(item.updatedAt) }}</td>
                <td><button class="button small secondary" @click="selectUser(item.userId)">查看信用记录</button></td>
              </tr>
            </tbody>
          </table>
        </div>
      </AsyncPanel>
      <PageControls v-if="!list.loading.value && !list.error.value" v-model:page="list.page.value" v-model:size="list.size.value" :total="list.total.value" />
    </section>
    <section v-if="selectedId" class="panel credit-detail" aria-label="用户信用详情">
      <div class="panel-heading"><h2>用户 #{{ selectedId }} 的信用记录</h2><button class="button text-button small" @click="selectedId = ''">收起详情</button></div>
      <AsyncPanel :loading="detail.loading.value" :error="detail.error.value" @retry="detail.reload">
        <template v-if="credit">
          <div class="credit-summary">
            <div><span class="credit-score large">{{ credit.creditScore }}</span><span class="credit-badge" :class="credit.creditLevel">{{ creditLevels[credit.creditLevel] }}</span><p>连续履约 {{ credit.consecutiveCompletedCount }}/10</p></div>
            <div><strong>{{ credit.restrictionActive ? '低分限制生效中' : '当前未受低分限制' }}</strong><p>{{ credit.canAcceptNormalTasks ? '可接取普通任务。' : '暂不可接取普通任务，已接任务仍可继续履约。' }}</p><p>{{ credit.requiresPublishReview ? '新发布任务需要复核；信用恢复至 75 分后解除低分限制。' : '新任务按常规流程发布。' }}</p></div>
            <button class="button secondary" :disabled="credit.adjustmentAllowed !== true" :title="credit.adjustmentAllowed !== true ? '信用调整暂未开放' : undefined" @click="operation = { mode: 'adjustment', record: credit }">管理员复核调整</button>
          </div>
          <div v-if="credit.pendingReview?.status === 'PENDING'" class="credit-review-request">
            <div><strong>待处理复核申请</strong><p class="preserve-lines">{{ credit.pendingReview.reason }}</p><small>{{ formatDate(credit.pendingReview.createdAt) }}</small></div>
            <button class="button secondary small" :disabled="credit.reviewDecisionAllowed !== true" @click="operation = { mode: 'user-review', record: credit }">处理复核申请</button>
          </div>
          <div class="admin-filters">
            <label class="inline-field">信用事件类型
              <select v-model="eventType" aria-label="信用事件类型"><option value="">全部事件</option><option v-for="(label, value) in creditEvents" :key="value" :value="value">{{ label }}</option></select>
            </label>
            <span class="small muted">请求变动与实际变动分别留痕，原始记录不会被覆盖。</span>
          </div>
          <AsyncPanel :loading="records.loading.value" :error="records.error.value" :empty="!records.items.value.length" empty-text="暂无信用变更记录" @retry="records.reload">
            <div class="table-wrap credit-table">
              <table>
                <thead><tr><th>事件 / 来源</th><th>原因</th><th>请求 / 实际变动</th><th>变更前 → 后</th><th>管理员</th><th>发生时间</th></tr></thead>
                <tbody><tr v-for="item in records.items.value" :key="item.recordId">
                  <td>{{ creditEvents[item.eventType] || item.eventType }}<small>记录 #{{ item.recordId }}</small><small>来源 #{{ item.eventId }}</small></td>
                  <td class="record-description preserve-lines">{{ item.reason }}</td>
                  <td>{{ signedCredit(item.requestedAdjustment) }} / <strong>{{ signedCredit(item.actualAdjustment) }}</strong></td>
                  <td>{{ item.scoreBefore ?? '初始化' }} → {{ item.scoreAfter }}</td>
                  <td>{{ item.administratorId || '系统' }}</td><td>{{ formatDate(item.createdAt) }}</td>
                </tr></tbody>
              </table>
            </div>
          </AsyncPanel>
          <PageControls v-if="!records.loading.value && !records.error.value" v-model:page="records.page.value" v-model:size="records.size.value" :total="records.total.value" />
        </template>
      </AsyncPanel>
    </section>
    <CreditReviewModal v-if="operation" :key="`${operation.mode}:${selectedId}`" :mode="operation.mode" :record="operation.record" @close="operation = null" />
  </section>
</template>
