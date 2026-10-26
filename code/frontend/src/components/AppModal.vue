<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import AppIcon from './AppIcon.vue'
defineProps({ title: String, busy: Boolean })
const emit = defineEmits(['close'])
const dialog = ref(null)
onMounted(() => dialog.value.showModal())
onUnmounted(() => dialog.value?.close())
</script>
<template>
  <dialog
    ref="dialog"
    class="modal"
    aria-labelledby="modal-heading"
    @cancel.prevent="!busy && emit('close')"
    @click="$event.target === dialog && !busy && emit('close')"
  >
    <header class="modal-header">
      <h2 id="modal-heading">{{ title }}</h2>
      <button class="icon-button" :disabled="busy" aria-label="关闭弹窗" @click="emit('close')">
        <AppIcon name="close" />
      </button>
    </header>
    <slot />
  </dialog>
</template>
