<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api/index.js'
import { session, useResource } from '../composables/state.js'
import { formatDate, formatNumber, taskPermissions, taskStatuses } from '../domain/tasks.js'
import AppIcon from '../components/AppIcon.vue'
import AsyncPanel from '../components/AsyncPanel.vue'
import TaskActions from '../components/TaskActions.vue'
const route = useRoute()
const {
  data: task,
  loading,
  error,
  reload,
} = useResource((signal) => api.task(route.params.taskId, signal), [() => route.params.taskId])
const permissions = computed(() => (task.value ? taskPermissions(task.value, session.value) : {}))
const steps = ['WAITING_FOR_TAKER', 'IN_PROGRESS', 'WAITING_FOR_ACCEPTANCE', 'COMPLETED']
</script>
<template>
  <section>
    <RouterLink to="/my-tasks" class="back-link">← 返回我的任务</RouterLink>
    <AsyncPanel
      :loading="loading && (!task || String(task.taskId) !== String(route.params.taskId))"
      :error="error"
      @retry="reload"
    >
      <template v-if="task">
        <div class="page-heading">
          <div>
            <p class="eyebrow">TASK #{{ task.taskId }}</p>
            <h1 class="detail-heading">{{ task.title }}</h1>
          </div>
          <span class="status-badge" :class="task.status">
            {{ taskStatuses[task.status] || task.status }}
          </span>
        </div>
        <div class="detail-layout">
          <article class="panel detail-content">
            <h2>任务进度</h2>
            <div v-if="task.status !== 'CANCELLED'" class="progress-steps">
              <div
                v-for="(step, index) in steps"
                :key="step"
                :class="{ reached: steps.indexOf(task.status) >= index }"
              >
                <span>{{ index + 1 }}</span>
                <p>{{ taskStatuses[step] }}</p>
              </div>
            </div>
            <p v-else class="notice">该任务已取消，相关记录和交易流水仍可查询。</p>
            <h2>取送信息</h2>
            <div class="detail-route">
              <div>
                <span class="location-dot pickup">取</span>
                <div>
                  <small>取件地点</small>
                  <p>{{ task.pickupLocation }}</p>
                </div>
              </div>
              <div>
                <span class="location-dot dropoff">送</span>
                <div>
                  <small>送达地点</small>
                  <p>{{ task.deliveryLocation }}</p>
                </div>
              </div>
            </div>
            <h2>任务说明</h2>
            <p class="long-description">{{ task.description }}</p>
            <dl class="info-grid">
              <div>
                <dt>截止时间</dt>
                <dd>{{ formatDate(task.deadline) }}</dd>
              </div>
              <div>
                <dt>发布时间</dt>
                <dd>{{ formatDate(task.createdAt) }}</dd>
              </div>
              <div>
                <dt>发布者编号</dt>
                <dd>{{ task.publisherId }}</dd>
              </div>
              <div>
                <dt>跑腿员编号</dt>
                <dd>{{ task.runnerId || '尚未接取' }}</dd>
              </div>
            </dl>
          </article>
          <aside>
            <section class="panel reward-panel">
              <p class="muted">任务报酬</p>
              <div class="detail-reward">
                {{ formatNumber(task.reward) }}
                <small>虚拟币</small>
              </div>
              <p class="small muted">发布者验收完成后全额结算</p>
              <hr />
              <TaskActions :task="task" @refresh="reload" />
              <p v-if="task.status === 'WAITING_FOR_TAKER' && !permissions.accept" class="notice">
                {{
                  String(task.publisherId) === String(session.userId)
                    ? '你不能接取自己发布的任务。'
                    : '该任务当前不可接取，请核对截止时间和接单状态。'
                }}
              </p>
              <p
                v-else-if="
                  task.status === 'WAITING_FOR_ACCEPTANCE' &&
                  permissions.cancel &&
                  !permissions.complete
                "
                class="notice"
              >
                你已确认送达，正在等待发布者验收。
              </p>
              <p v-if="task.status === 'COMPLETED'" class="notice">
                任务已完成。
                <RouterLink to="/wallet" class="text-link">查看钱包与流水</RouterLink>
              </p>
            </section>
            <p class="side-note">
              <AppIcon name="shield" :size="17" />
              请核对任务信息后操作。取消、申诉和投诉由对应流程处理。
            </p>
          </aside>
        </div>
      </template>
    </AsyncPanel>
  </section>
</template>
