<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/index.js'
import { changed, notify, usePaged } from '../composables/state.js'
import { formatDate } from '../domain/tasks.js'
import AppIcon from '../components/AppIcon.vue'
import AsyncPanel from '../components/AsyncPanel.vue'
import PageControls from '../components/PageControls.vue'
const router = useRouter()
const unreadOnly = ref(false)
const busy = ref(new Set())
const { items, total, page, size, loading, error, reload } = usePaged(
  (pagination, signal) =>
    api.notifications({ ...pagination, unreadOnly: unreadOnly.value }, signal),
  [unreadOnly],
)
async function open(item, navigate = true) {
  if (busy.value.has(item.notificationId)) return
  busy.value.add(item.notificationId)
  try {
    if (!item.read) {
      await api.readNotification(item.notificationId)
      changed()
    }
    if (navigate && item.relatedTaskId)
      await router.push({ name: 'task-detail', params: { taskId: item.relatedTaskId } })
  } catch (failure) {
    notify(failure.message, 'error')
  } finally {
    busy.value.delete(item.notificationId)
  }
}
</script>
<template>
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">NOTIFICATIONS</p>
        <h1>
          通知中心
          <span class="heading-dot">.</span>
        </h1>
        <p class="page-subtitle">任务的新进展，在这里及时知晓。</p>
      </div>
      <button class="button secondary" @click="reload">
        <AppIcon name="refresh" :size="16" />
        刷新
      </button>
    </div>
    <section class="panel">
      <div class="panel-heading">
        <div class="segmented">
          <button
            :class="{ active: !unreadOnly }"
            :aria-pressed="!unreadOnly"
            @click="unreadOnly = false"
          >
            全部通知
          </button>
          <button
            :class="{ active: unreadOnly }"
            :aria-pressed="unreadOnly"
            @click="unreadOnly = true"
          >
            仅看未读
          </button>
        </div>
        <span class="small muted">点击查看后标记为已读</span>
      </div>
      <AsyncPanel
        :loading="loading"
        :error="error"
        :empty="!items.length"
        empty-text="暂无通知"
        @retry="reload"
      >
        <div class="notification-list">
          <article
            v-for="item in items"
            :key="item.notificationId"
            class="notification-item"
            :class="{ unread: !item.read }"
          >
            <span class="notification-icon"><AppIcon name="bell" :size="20" /></span>
            <div class="notification-content">
              <div>
                <h3>{{ item.title }}</h3>
                <span v-if="!item.read" class="unread-label">未读</span>
              </div>
              <p>{{ item.content }}</p>
              <time>{{ formatDate(item.createdAt) }}</time>
            </div>
            <div class="notification-actions">
              <button
                v-if="item.relatedTaskId"
                class="button secondary small"
                :disabled="busy.has(item.notificationId)"
                @click="open(item)"
              >
                查看任务
              </button>
              <button
                v-if="!item.read"
                class="text-link small"
                :disabled="busy.has(item.notificationId)"
                @click="open(item, false)"
              >
                标记已读
              </button>
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
  </section>
</template>
