<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api/index.js'
import { revision, usePaged, useResource } from '../composables/state.js'
import { formatDate, formatNumber, taskStatuses } from '../domain/tasks.js'
import AppIcon from '../components/AppIcon.vue'
import AsyncPanel from '../components/AsyncPanel.vue'
import PageControls from '../components/PageControls.vue'
import TaskActions from '../components/TaskActions.vue'

const route = useRoute()
const role = ref(route.query.as === 'publisher' ? 'publisher' : 'runner')
const status = ref('')
watch(
  role,
  () => {
    status.value = ''
  },
  { flush: 'sync' },
)
const { items, total, loading, error, page, size, reload } = usePaged(
  (pagination, signal) =>
    api.myTasks({ ...pagination, as: role.value, status: status.value }, signal),
  [role, status],
)
const counts = useResource(
  async (signal) => {
    const states = ['', 'IN_PROGRESS', 'WAITING_FOR_ACCEPTANCE']
    const results = await Promise.all(
      states.map((status) => api.myTasks({ as: role.value, status, page: 0, size: 10 }, signal)),
    )
    return results.map((result) => result.total)
  },
  [role, revision],
)
const stats = computed(() => [
  {
    title: '全部任务',
    value: counts.data.value?.[0],
    icon: 'tasks',
    tone: 'green',
    note: role.value === 'runner' ? '我接取的任务记录' : '我发布的任务记录',
  },
  {
    title: '正在进行',
    value: counts.data.value?.[1],
    icon: 'pin',
    tone: 'orange',
    note: '每一程，都有进度',
  },
  {
    title: '等待验收',
    value: counts.data.value?.[2],
    icon: 'check',
    tone: 'blue',
    note: '送达后等待发布者确认',
  },
])
function refresh() {
  reload()
  counts.reload()
}
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">TASK WORKSPACE</p>
        <h1>
          我的任务
          <span class="heading-dot">.</span>
        </h1>
        <p class="page-subtitle">查看每一单的进展，完成接取、送达与验收。</p>
      </div>
      <div class="heading-actions">
        <button class="button secondary" :disabled="loading" @click="refresh">
          <AppIcon name="refresh" :size="16" />
          刷新
        </button>
        <RouterLink to="/tasks" class="button primary">
          <AppIcon name="plus" :size="17" />
          接取任务
        </RouterLink>
      </div>
    </div>
    <div class="stats-grid">
      <article v-for="stat in stats" :key="stat.title" class="stat-card">
        <div>
          <p>{{ stat.title }}</p>
          <strong>
            {{ counts.loading.value || counts.error.value ? '—' : formatNumber(stat.value) }}
          </strong>
          <span>{{ stat.note }}</span>
        </div>
        <span class="stat-icon" :class="stat.tone"><AppIcon :name="stat.icon" :size="23" /></span>
      </article>
    </div>
    <p v-if="counts.error.value" class="inline-error">
      任务统计暂时不可用。
      <button class="text-link" @click="counts.reload">重试</button>
    </p>
    <section class="panel task-list-panel">
      <div class="panel-heading">
        <div class="segmented" aria-label="任务参与身份">
          <button
            :class="{ active: role === 'runner' }"
            :aria-pressed="role === 'runner'"
            @click="role = 'runner'"
          >
            我接取的
          </button>
          <button
            :class="{ active: role === 'publisher' }"
            :aria-pressed="role === 'publisher'"
            @click="role = 'publisher'"
          >
            我发布的
          </button>
        </div>
        <span class="small muted">把约定的事，按时完成</span>
      </div>
      <div class="filter-tabs" aria-label="任务状态">
        <button :class="{ active: !status }" :aria-pressed="!status" @click="status = ''">
          全部
        </button>
        <template v-for="(label, value) in taskStatuses" :key="value">
          <button
            v-if="role === 'publisher' || value !== 'WAITING_FOR_TAKER'"
            :class="{ active: status === value }"
            :aria-pressed="status === value"
            @click="status = value"
          >
            {{ label }}
          </button>
        </template>
      </div>
      <AsyncPanel
        :loading="loading"
        :error="error"
        :empty="!items.length"
        empty-text="这个分类下还没有任务"
        @retry="reload"
      >
        <div class="task-grid">
          <article v-for="task in items" :key="task.taskId" class="task-card">
            <div class="task-card-top">
              <span class="status-badge" :class="task.status">
                {{ taskStatuses[task.status] || task.status }}
              </span>
              <span class="task-reference">#{{ task.taskId }}</span>
            </div>
            <RouterLink
              :to="{ name: 'task-detail', params: { taskId: task.taskId } }"
              class="task-title"
            >
              {{ task.title }}
            </RouterLink>
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
              <div class="task-card-actions">
                <RouterLink
                  class="detail-link"
                  :to="{ name: 'task-detail', params: { taskId: task.taskId } }"
                >
                  详情
                  <AppIcon name="chevron" :size="14" />
                </RouterLink>
                <TaskActions :task="task" compact />
              </div>
            </div>
          </article>
        </div>
      </AsyncPanel>
      <PageControls
        v-if="!loading && !error"
        v-model:page="page"
        v-model:size="size"
        :total="total"
      />
    </section>
    <div class="bottom-note">
      <AppIcon name="shield" :size="16" />
      送达后由发布者手动验收，任务完成后报酬自动结算。
    </div>
  </section>
</template>
