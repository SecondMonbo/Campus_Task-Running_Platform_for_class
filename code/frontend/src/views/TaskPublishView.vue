<script setup>
import { computed, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api } from '../api/index.js'
import { changed, notify } from '../composables/state.js'
import { validateTaskDraft } from '../domain/tasks.js'
import AppIcon from '../components/AppIcon.vue'

const router = useRouter()
const busy = ref(false)
const error = ref('')
const errorCode = ref('')
const form = reactive({
  title: '',
  description: '',
  reward: 10,
  deadline: '',
  pickupLocation: '',
  deliveryLocation: '',
})

function localDateTime(date) {
  const pad = (value) => String(value).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}
const minDeadline = computed(() => localDateTime(new Date(Date.now() + 60 * 1000)))

async function submit() {
  if (busy.value) return
  error.value = ''
  errorCode.value = ''
  const deadline = form.deadline ? new Date(form.deadline) : null
  const draft = {
    title: form.title.trim(),
    description: form.description.trim(),
    reward: form.reward,
    deadline: deadline && !Number.isNaN(deadline.getTime()) ? deadline.toISOString() : '',
    pickupLocation: form.pickupLocation.trim(),
    deliveryLocation: form.deliveryLocation.trim(),
  }
  const problem = validateTaskDraft(draft)
  if (problem) {
    error.value = problem
    return
  }
  busy.value = true
  try {
    const task = await api.createTask(draft)
    changed()
    notify(`任务“${task.title}”发布成功，报酬已冻结。`)
    await router.push('/tasks')
  } catch (failure) {
    error.value = failure.message
    errorCode.value = failure.code
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">PUBLISH A TASK</p>
        <h1>
          发布任务
          <span class="heading-dot">.</span>
        </h1>
        <p class="page-subtitle">写清取送需求与截止时间，报酬将在发布时冻结。</p>
      </div>
    </div>
    <div class="disputes-layout">
      <article class="panel form-panel">
        <h2>任务信息</h2>
        <p class="muted">发布后任务会立即出现在任务大厅，等待其他同学接取。</p>
        <form @submit.prevent="submit">
          <label class="field">
            任务标题
            <input
              v-model="form.title"
              name="title"
              required
              maxlength="100"
              placeholder="例如：代取图书馆预订资料"
            />
          </label>
          <label class="field">
            任务说明
            <textarea
              v-model="form.description"
              name="description"
              required
              maxlength="2000"
              rows="5"
              placeholder="请说明任务内容、注意事项与交付要求"
            />
          </label>
          <div class="form-columns">
            <label class="field">
              报酬（虚拟币）
              <input
                v-model.number="form.reward"
                name="reward"
                type="number"
                required
                min="1"
                max="1000"
                step="1"
                placeholder="1-1000"
              />
              <span class="field-hint">发布时从可用余额中冻结，完成后结算给跑腿员。</span>
            </label>
            <label class="field">
              截止时间
              <input
                v-model="form.deadline"
                name="deadline"
                type="datetime-local"
                required
                :min="minDeadline"
              />
              <span class="field-hint">必须晚于当前时间，过期的任务不能再被接取。</span>
            </label>
          </div>
          <div class="form-columns">
            <label class="field">
              取件地点
              <input
                v-model="form.pickupLocation"
                name="pickupLocation"
                required
                maxlength="200"
                placeholder="例如：图书馆一楼服务台"
              />
            </label>
            <label class="field">
              送件地点
              <input
                v-model="form.deliveryLocation"
                name="deliveryLocation"
                required
                maxlength="200"
                placeholder="例如：东区 3 号宿舍"
              />
            </label>
          </div>
          <p v-if="error" class="form-error" role="alert">
            {{ error }}
            <RouterLink
              v-if="errorCode === 'INSUFFICIENT_BALANCE'"
              to="/wallet"
              class="text-link"
            >
              查看钱包余额
            </RouterLink>
          </p>
          <button class="button primary" :disabled="busy">
            {{ busy ? '正在发布…' : '发布任务' }}
            <AppIcon v-if="!busy" name="arrow" :size="17" />
          </button>
        </form>
      </article>
      <aside class="help-stack">
        <article class="panel help-card">
          <span class="stat-icon green"><AppIcon name="shield" :size="22" /></span>
          <h3>发布前确认</h3>
          <p>报酬在发布时从可用余额冻结；任务被撤回时全额退回，确认完成后全额结算给跑腿员。</p>
          <p>请如实填写取送地点与截止时间，接单后修改空间有限。</p>
          <RouterLink to="/wallet" class="text-link">查看钱包与流水 →</RouterLink>
        </article>
        <article class="panel help-card">
          <span class="stat-icon blue"><AppIcon name="tasks" :size="22" /></span>
          <h3>发布之后</h3>
          <p>任务会出现在任务大厅，其他同学可以接取执行。</p>
          <p>任务进度在“我的任务 → 我发布的”中查看。</p>
          <RouterLink to="/tasks" class="text-link">前往任务大厅 →</RouterLink>
        </article>
      </aside>
    </div>
  </section>
</template>
