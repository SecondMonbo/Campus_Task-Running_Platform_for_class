<script setup>
import { computed } from 'vue'
import { formatNumber } from '../domain/tasks.js'
const props = defineProps({ page: Number, size: Number, total: Number })
defineEmits(['update:page', 'update:size'])
const pageCount = computed(() => Math.max(1, Math.ceil(props.total / props.size)))
</script>
<template>
  <div class="pagination">
    <span>共 {{ formatNumber(total) }} 条记录</span>
    <div class="pagination-actions">
      <label>
        每页
        <select
          :value="size"
          aria-label="每页条数"
          @change="$emit('update:size', Number($event.target.value))"
        >
          <option :value="10">10 条</option>
          <option :value="20">20 条</option>
          <option :value="50">50 条</option>
        </select>
      </label>
      <button
        class="button small secondary"
        :disabled="page === 0"
        aria-label="上一页"
        @click="$emit('update:page', page - 1)"
      >
        上一页
      </button>
      <span>{{ formatNumber(page + 1) }} / {{ formatNumber(pageCount) }}</span>
      <button
        class="button small secondary"
        :disabled="page + 1 >= pageCount"
        aria-label="下一页"
        @click="$emit('update:page', page + 1)"
      >
        下一页
      </button>
    </div>
  </div>
</template>
