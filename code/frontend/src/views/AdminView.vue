<script setup>
import { computed, ref } from 'vue'
import { api } from '../api/index.js'
import { session, usePaged } from '../composables/state.js'
import { formatDate, formatNumber, taskStatuses, transactionTypes } from '../domain/tasks.js'
import AdminDecision from '../components/AdminDecision.vue'
import AppIcon from '../components/AppIcon.vue'
import AsyncPanel from '../components/AsyncPanel.vue'
import PageControls from '../components/PageControls.vue'
const section = ref('cancellation-requests')
const status = ref('PENDING_ADMIN')
const keyword = ref('')
const search = ref('')
const userId = ref('')
const taskId = ref('')
const actorId = ref('')
const logAction = ref('')
const filters = ref({})
const selected = ref(null)
const tabs = {
  'cancellation-requests': '取消裁决',
  appeals: '任务申诉',
  complaints: '投诉处理',
  users: '用户查询',
  tasks: '任务查询',
  transactions: '交易流水',
  'operation-logs': '操作日志',
}
// 尚未提供后端接口的管理模块：先标注“暂未开放”，不作为可用功能展示。
const pendingSections = ['complaints', 'users', 'transactions', 'operation-logs']
const statuses = computed(
  () =>
    ({
      'cancellation-requests': {
        PENDING_ADMIN: '待管理员处理',
        PENDING_RESPONSE: '待应答',
        APPROVED: '已同意',
        ARBITRATED: '已裁决',
      },
      appeals: { PENDING: '待处理', DECIDED: '已裁决' },
      complaints: { PENDING: '待处理', PROCESSED: '已处理' },
      tasks: taskStatuses,
    })[section.value] ?? {},
)
const { items, total, page, size, loading, error, reload } = usePaged(
  (pagination, signal) => {
    if (session.value?.role !== 'ADMIN')
      return Promise.reject(new Error('当前账号没有管理端访问权限。'))
    const query = { ...pagination, ...filters.value }
    if (Object.keys(statuses.value).length) query.status = status.value
    if (section.value === 'users' || section.value === 'tasks') query.keyword = search.value
    return section.value === 'tasks'
      ? api.tasks(query, signal)
      : api.adminList(section.value, query, signal)
  },
  [section, status, search, filters],
)
function switchSection(value) {
  if (pendingSections.includes(value)) return
  status.value =
    { 'cancellation-requests': 'PENDING_ADMIN', appeals: 'PENDING', complaints: 'PENDING' }[
      value
    ] ?? ''
  section.value = value
  search.value = ''
  keyword.value = ''
  filters.value = {}
  userId.value = ''
  taskId.value = ''
  actorId.value = ''
  logAction.value = ''
}
function applySearch() {
  search.value = keyword.value.trim()
  filters.value =
    section.value === 'transactions'
      ? { userId: userId.value.trim(), taskId: taskId.value.trim() }
      : section.value === 'operation-logs'
        ? { actorId: actorId.value.trim(), action: logAction.value.trim() }
        : {}
}
const canHandle = (item) =>
  section.value === 'cancellation-requests'
    ? item.status === 'PENDING_ADMIN'
    : ['appeals', 'complaints'].includes(section.value) && item.status === 'PENDING'
const labelForStatus = (value) => statuses.value[value] ?? value
</script>
<template>
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">ADMINISTRATION</p>
        <h1>
          管理工作台
          <span class="heading-dot">.</span>
        </h1>
        <p class="page-subtitle">处理争议，查询记录，维护任务秩序。</p>
      </div>
      <button class="button secondary" @click="reload">
        <AppIcon name="refresh" :size="16" />
        刷新
      </button>
    </div>
    <section class="panel">
      <nav class="filter-tabs admin-tabs" aria-label="管理模块">
        <button
          v-for="(label, key) in tabs"
          :key="key"
          :class="{ active: section === key }"
          :aria-pressed="section === key"
          :disabled="pendingSections.includes(key)"
          :title="pendingSections.includes(key) ? '该模块暂未开放' : undefined"
          @click="switchSection(key)"
        >
          {{ label }}
          <span v-if="pendingSections.includes(key)" class="pending-badge">暂未开放</span>
        </button>
      </nav>
      <form class="admin-filters" @submit.prevent="applySearch">
        <label v-if="Object.keys(statuses).length" class="inline-field">
          状态
          <select v-model="status">
            <option value="">全部状态</option>
            <option v-for="(label, key) in statuses" :key="key" :value="key">{{ label }}</option>
          </select>
        </label>
        <label v-if="['users', 'tasks'].includes(section)" class="inline-field">
          关键字
          <input v-model="keyword" maxlength="100" placeholder="输入关键字" />
        </label>
        <template v-if="section === 'transactions'">
          <label class="inline-field">
            用户编号
            <input v-model="userId" placeholder="全部用户" />
          </label>
          <label class="inline-field">
            任务编号
            <input v-model="taskId" placeholder="全部任务" />
          </label>
        </template>
        <template v-if="section === 'operation-logs'">
          <label class="inline-field">
            操作人编号
            <input v-model="actorId" placeholder="全部操作人" />
          </label>
          <label class="inline-field">
            操作类型
            <input v-model="logAction" placeholder="全部操作类型" />
          </label>
        </template>
        <button
          v-if="['users', 'tasks', 'transactions', 'operation-logs'].includes(section)"
          class="button secondary small"
        >
          查询
        </button>
        <span class="small muted">{{ tabs[section] }}</span>
      </form>
      <AsyncPanel
        :loading="loading"
        :error="error"
        :empty="!items.length"
        empty-text="暂无符合条件的记录"
        @retry="reload"
      >
        <div class="table-wrap">
          <table>
            <template v-if="section === 'users'">
              <thead>
                <tr>
                  <th>用户</th>
                  <th>账号</th>
                  <th>角色</th>
                  <th>联系方式</th>
                  <th>注册时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in items" :key="item.userId">
                  <td>
                    <strong>{{ item.displayName }}</strong>
                    <small>#{{ item.userId }}</small>
                  </td>
                  <td>{{ item.username }}</td>
                  <td>{{ item.role === 'ADMIN' ? '管理员' : '普通用户' }}</td>
                  <td>{{ item.phone || '—' }}</td>
                  <td>{{ formatDate(item.createdAt) }}</td>
                </tr>
              </tbody>
            </template>
            <template v-else-if="section === 'tasks'">
              <thead>
                <tr>
                  <th>任务</th>
                  <th>状态</th>
                  <th>发布者 / 跑腿员</th>
                  <th>报酬</th>
                  <th>截止时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in items" :key="item.taskId">
                  <td>
                    <RouterLink
                      class="text-link"
                      :to="{ name: 'task-detail', params: { taskId: item.taskId } }"
                    >
                      {{ item.title }}
                    </RouterLink>
                    <small>#{{ item.taskId }}</small>
                  </td>
                  <td>
                    <span class="status-badge" :class="item.status">
                      {{ taskStatuses[item.status] || item.status }}
                    </span>
                  </td>
                  <td>{{ item.publisherId }} / {{ item.runnerId || '—' }}</td>
                  <td>{{ formatNumber(item.reward) }}</td>
                  <td>{{ formatDate(item.deadline) }}</td>
                </tr>
              </tbody>
            </template>
            <template v-else-if="section === 'transactions'">
              <thead>
                <tr>
                  <th>流水编号</th>
                  <th>用户 / 任务</th>
                  <th>类型</th>
                  <th class="align-right">金额 / 余额</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in items" :key="item.transactionId">
                  <td>
                    #{{ item.transactionId }}
                    <small v-if="item.appealId">申诉 #{{ item.appealId }}</small>
                  </td>
                  <td>{{ item.userId || '—' }} / {{ item.taskId || '—' }}</td>
                  <td>{{ transactionTypes[item.type] || item.type }}</td>
                  <td class="align-right">
                    <strong :class="item.amount > 0 ? 'positive' : 'negative'">
                      {{ item.amount > 0 ? '+' : '' }}{{ formatNumber(item.amount) }}
                    </strong>
                    <small>{{ formatNumber(item.balanceAfter) }}</small>
                  </td>
                  <td>{{ formatDate(item.createdAt) }}</td>
                </tr>
              </tbody>
            </template>
            <template v-else-if="section === 'operation-logs'">
              <thead>
                <tr>
                  <th>日志编号</th>
                  <th>操作人</th>
                  <th>操作类型</th>
                  <th>关联对象</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in items" :key="item.logId">
                  <td>#{{ item.logId }}</td>
                  <td>{{ item.actorId }}</td>
                  <td>{{ item.action }}</td>
                  <td>{{ item.targetId || '—' }}</td>
                  <td>{{ formatDate(item.createdAt) }}</td>
                </tr>
              </tbody>
            </template>
            <template v-else>
              <thead>
                <tr>
                  <th>记录 / 关联任务</th>
                  <th>说明</th>
                  <th>状态</th>
                  <th>提交时间</th>
                  <th>处理</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="item in items"
                  :key="item.requestId || item.appealId || item.complaintId"
                >
                  <td>
                    <strong>#{{ item.requestId || item.appealId || item.complaintId }}</strong>
                    <RouterLink
                      v-if="item.taskId"
                      class="text-link small"
                      :to="{ name: 'task-detail', params: { taskId: item.taskId } }"
                    >
                      任务 #{{ item.taskId }}
                    </RouterLink>
                  </td>
                  <td class="record-description">
                    {{ item.description || item.reason || item.category }}
                    <small v-if="item.decisionNote || item.ruling">
                      {{ item.decisionNote || item.ruling }}
                    </small>
                    <small v-if="item.refundAmount != null">
                      退款 {{ formatNumber(item.refundAmount) }} · 跑腿员所得
                      {{ formatNumber(item.runnerAmount) }}
                    </small>
                    <small v-if="item.transferAmount != null">
                      裁决金额 {{ formatNumber(item.transferAmount) }}
                    </small>
                  </td>
                  <td>{{ labelForStatus(item.status) }}</td>
                  <td>{{ formatDate(item.createdAt) }}</td>
                  <td>
                    <button
                      v-if="canHandle(item)"
                      class="button secondary small"
                      @click="selected = item"
                    >
                      {{ section === 'complaints' ? '处理' : '裁决' }}
                    </button>
                    <span v-else class="small muted">
                      {{ item.status === 'PENDING_RESPONSE' ? '等待当事人应答' : '已处理' }}
                    </span>
                  </td>
                </tr>
              </tbody>
            </template>
          </table>
        </div>
      </AsyncPanel>
      <PageControls
        v-if="!loading && !error"
        v-model:page="page"
        v-model:size="size"
        :total="total"
      />
    </section>
    <AdminDecision v-if="selected" :kind="section" :record="selected" @close="selected = null" />
  </section>
</template>
