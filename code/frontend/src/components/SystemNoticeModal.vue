<script setup>
import { ref } from 'vue'
import { api } from '../api/index.js'
import { changed, notify } from '../composables/state.js'
import { validateSystemNotification } from '../domain/notifications.js'
import AppModal from './AppModal.vue'
const emit = defineEmits(['close'])
const busy = ref(false)
const error = ref('')
const form = ref({ title: '', content: '' })
async function submit() {
  if (busy.value) return
  error.value = ''
  const body = { title: form.value.title.trim(), content: form.value.content.trim() }
  error.value = validateSystemNotification(body)
  if (error.value) return
  busy.value = true
  try {
    const result = await api.publishSystemNotification(body)
    notify(`系统通知已发布，共推送给 ${result?.recipientCount ?? 0} 位用户。`)
    changed()
    emit('close')
  } catch (failure) {
    error.value = failure.message
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <AppModal title="发布系统通知" :busy="busy" @close="$emit('close')">
    <form class="modal-body" @submit.prevent="submit">
      <label class="field">
        通知标题
        <input v-model="form.title" maxlength="100" required placeholder="例如：平台维护通知" />
      </label>
      <label class="field">
        通知内容
        <textarea
          v-model="form.content"
          maxlength="500"
          rows="5"
          required
          placeholder="填写面向全体用户的说明"
        />
      </label>
      <p class="notice">
        发布后将为当前全部已注册用户各生成一条“系统通知”，在通知中心与任务通知区分展示；用户可自行查看并标记已读。
      </p>
      <p v-if="error" class="form-error" role="alert">{{ error }}</p>
      <footer class="modal-footer">
        <button type="button" class="button secondary" :disabled="busy" @click="$emit('close')">
          取消
        </button>
        <button class="button primary" :disabled="busy">
          {{ busy ? '正在发布…' : '确认发布' }}
        </button>
      </footer>
    </form>
  </AppModal>
</template>
