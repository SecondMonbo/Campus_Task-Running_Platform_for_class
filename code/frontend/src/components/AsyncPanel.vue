<script setup>
import AppIcon from './AppIcon.vue'
defineProps({
  loading: Boolean,
  error: Object,
  empty: Boolean,
  emptyText: { type: String, default: '暂无记录' },
})
defineEmits(['retry'])
</script>
<template>
  <div v-if="loading" class="state-panel" role="status">
    <span class="spinner" />
    <p>正在加载，请稍候…</p>
  </div>
  <div v-else-if="error" class="state-panel error-state" role="alert">
    <AppIcon name="dispute" :size="32" />
    <h3>{{ error.status === 401 ? '请先登录' : '暂时无法加载' }}</h3>
    <p>{{ error.message }}</p>
    <button class="button secondary" @click="$emit('retry')">重新加载</button>
  </div>
  <div v-else-if="empty" class="state-panel">
    <AppIcon name="inbox" :size="36" />
    <h3>{{ emptyText }}</h3>
    <p>有新的记录时，会在这里显示。</p>
  </div>
  <slot v-else />
</template>
