<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '../api/index.js'
import { usePaged } from '../composables/state.js'
import { formatDate, formatNumber, taskStatuses } from '../domain/tasks.js'
import AppIcon from '../components/AppIcon.vue'
import AsyncPanel from '../components/AsyncPanel.vue'
import PageControls from '../components/PageControls.vue'

const keywordInput = ref('')
const keyword = ref('')
const status = ref('')
const isDemo = import.meta.env.MODE === 'demo'
const backendHealth = ref(isDemo ? 'demo' : 'checking')
let healthController
const { items, total, loading, error, page, size, reload } = usePaged(
  (pagination, signal) =>
    api.tasks({ ...pagination, status: status.value, keyword: keyword.value }, signal),
  [status, keyword],
)
const statusTabs = [
  { value: '', label: '全部任务' },
  ...Object.entries(taskStatuses).map(([value, label]) => ({ value, label })),
]
const backendHealthLabel = computed(() => {
  if (isDemo) return '本地演示数据'
  if (backendHealth.value === 'up') return '后端服务正常'
  if (backendHealth.value === 'checking') return '正在检查后端'
  return '后端暂不可用'
})

async function checkBackendHealth() {
  if (isDemo) return
  healthController?.abort()
  const controller = new AbortController()
  healthController = controller
  backendHealth.value = 'checking'
  try {
    const response = await fetch('/actuator/health', {
      headers: { Accept: 'application/json' },
      credentials: 'omit',
      signal: controller.signal,
    })
    if (!response.ok) throw new Error('健康检查请求失败')
    const result = await response.json()
    backendHealth.value = result?.status === 'UP' ? 'up' : 'down'
  } catch {
    if (!controller.signal.aborted) backendHealth.value = 'down'
  } finally {
    if (healthController === controller) healthController = undefined
  }
}

onMounted(() => {
  if (!isDemo) checkBackendHealth()
})
onBeforeUnmount(() => healthController?.abort())

function search() {
  keyword.value = keywordInput.value.trim()
}

function clearSearch() {
  keywordInput.value = ''
  keyword.value = ''
}
</script>

<template>
  <section>
    <div class="task-hall-hero">
      <div class="task-hall-copy">
        <p class="eyebrow">CAMPUS TASKS</p>
        <h1>让校园里的小事，<br />更容易完成。</h1>
        <p>浏览校园里的取送需求，查看每个任务当前所处的状态。</p>
      </div>
      <div class="task-hall-art" aria-hidden="true">
        <span class="art-sun" />
        <span class="art-path" />
        <span class="art-pin pin-a"><AppIcon name="pin" :size="21" /></span>
        <span class="art-pin pin-b"><AppIcon name="campus" :size="21" /></span>
        <span class="art-card"><AppIcon name="arrow" :size="20" /></span>
        <span class="art-caption">每一次帮忙<br />都让校园更近一点</span>
      </div>
    </div>

    <div class="page-heading task-hall-heading">
      <div>
        <p class="eyebrow">FIND A TASK</p>
        <h2>看看大家正在寻找什么帮助</h2>
        <p class="page-subtitle">任务状态与字段按 OpenAPI 契约展示。</p>
      </div>
      <div class="task-heading-tools">
        <button
          class="backend-health"
          :class="backendHealth"
          :disabled="isDemo || backendHealth === 'checking'"
          :aria-label="`${backendHealthLabel}，点击重新检查`"
          @click="checkBackendHealth"
        >
          <span class="backend-health-dot" aria-hidden="true" />
          {{ backendHealthLabel }}
        </button>
        <button class="button secondary" :disabled="loading" @click="reload">
          <AppIcon name="refresh" :size="16" />
          刷新列表
        </button>
      </div>
    </div>

    <section class="panel task-list-panel">
      <form class="task-search-row" role="search" @submit.prevent="search">
        <label class="task-search-box">
          <AppIcon name="search" :size="18" />
          <span class="sr-only">按标题或地点搜索</span>
          <input
            v-model="keywordInput"
            maxlength="100"
            placeholder="搜索任务标题或地点关键字"
          />
          <button
            v-if="keywordInput || keyword"
            type="button"
            class="search-clear"
            aria-label="清空搜索"
            @click="clearSearch"
          >
            <AppIcon name="close" :size="15" />
          </button>
        </label>
        <button class="button primary" type="submit">搜索任务</button>
      </form>

      <div class="filter-tabs task-status-tabs" aria-label="按任务状态筛选">
        <button
          v-for="tab in statusTabs"
          :key="tab.value || 'all'"
          :class="{ active: status === tab.value }"
          :aria-pressed="status === tab.value"
          @click="status = tab.value"
        >
          {{ tab.label }}
        </button>
      </div>

      <div class="task-result-line">
        <span>{{ loading ? '正在更新任务…' : `找到 ${formatNumber(total)} 条任务` }}</span>
        <span>每页 {{ size }} 条</span>
      </div>

      <AsyncPanel
        :loading="loading"
        :error="error"
        :empty="!items.length"
        empty-text="暂无符合条件的任务"
        @retry="reload"
      >
        <div class="task-grid">
          <article v-for="task in items" :key="task.taskId" class="task-card">
            <div class="task-card-top">
              <span class="status-badge" :class="task.status">
                {{ taskStatuses[task.status] || task.status }}
              </span>
              <span class="task-reference">发布于 {{ formatDate(task.createdAt, true) }}</span>
            </div>
            <span class="task-title">{{ task.title }}</span>
            <p class="task-description">{{ task.description }}</p>
            <div class="task-route">
              <div>
                <span class="location-dot pickup">取</span>
                <span>{{ task.pickupLocation }}</span>
              </div>
              <div>
                <span class="location-dot dropoff">送</span>
                <span>{{ task.deliveryLocation }}</span>
              </div>
            </div>
            <p class="deadline">
              <AppIcon name="clock" :size="14" />
              截止 {{ formatDate(task.deadline, true) }}
            </p>
            <div class="task-card-footer">
              <div class="reward">
                <strong>{{ formatNumber(task.reward) }}</strong>
                <span>虚拟币</span>
              </div>
            </div>
          </article>
        </div>
      </AsyncPanel>
      <PageControls
        v-if="!loading && !error && total > 0"
        v-model:page="page"
        v-model:size="size"
        :total="total"
      />
    </section>
    <div class="bottom-note">
      <AppIcon name="shield" :size="16" />
      任务状态标签使用 OpenAPI 定义的状态值。
    </div>
  </section>
</template>
