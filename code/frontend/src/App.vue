<script setup>
import { onMounted, ref } from 'vue'

const apiStatus = ref('checking')

onMounted(async () => {
  try {
    const response = await fetch('/actuator/health', { credentials: 'include' })
    apiStatus.value = response.ok ? 'connected' : 'unavailable'
  } catch {
    apiStatus.value = 'unavailable'
  }
})
</script>

<template>
  <main class="page">
    <section class="card">
      <p class="eyebrow">开发环境已就绪</p>
      <h1>校园跑腿平台</h1>
      <p class="description">
        Vue 前端和 Spring Boot 后端开发骨架已启动。后续功能按 API 约定逐步实现。
      </p>
      <div class="status" :class="apiStatus" role="status">
        <span class="status-dot" />
        <span v-if="apiStatus === 'checking'">正在检查后端连接…</span>
        <span v-else-if="apiStatus === 'connected'">后端连接正常</span>
        <span v-else>暂时无法连接后端，请确认后端已在 8080 端口启动</span>
      </div>
      <div class="details">
        <div><span>API 前缀</span><code>/api/v1</code></div>
        <div><span>前端开发端口</span><code>5173</code></div>
        <div><span>后端端口</span><code>8080</code></div>
      </div>
    </section>
  </main>
</template>
