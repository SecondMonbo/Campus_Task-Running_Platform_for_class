<script setup>
import { ref } from 'vue'
import { api } from '../api/index.js'
import { revision, usePaged, useResource } from '../composables/state.js'
import { formatDate, formatNumber, transactionTypes } from '../domain/tasks.js'
import AppIcon from '../components/AppIcon.vue'
import AsyncPanel from '../components/AsyncPanel.vue'
import PageControls from '../components/PageControls.vue'
const type = ref('')
const wallet = useResource(api.wallet, [revision])
const { items, total, page, size, loading, error, reload } = usePaged(
  (pagination, signal) => api.transactions({ ...pagination, type: type.value }, signal),
  [type],
)
function refresh() {
  wallet.reload()
  reload()
}
</script>
<template>
  <section>
    <div class="page-heading">
      <div>
        <p class="eyebrow">MY WALLET</p>
        <h1>
          钱包与流水
          <span class="heading-dot">.</span>
        </h1>
        <p class="page-subtitle">每一笔收支，都有记录。</p>
      </div>
      <button class="button secondary" @click="refresh">
        <AppIcon name="refresh" :size="16" />
        刷新
      </button>
    </div>
    <AsyncPanel :loading="wallet.loading.value" :error="wallet.error.value" @retry="wallet.reload">
      <div v-if="wallet.data.value" class="wallet-grid">
        <article class="balance-card">
          <div>
            <AppIcon name="wallet" :size="25" />
            <span>当前可用余额</span>
          </div>
          <strong>
            {{ formatNumber(wallet.data.value.availableBalance) }}
            <small>虚拟币</small>
          </strong>
          <p>用于发布任务与任务相关收支</p>
        </article>
        <article class="panel frozen-card">
          <span class="stat-icon orange"><AppIcon name="shield" :size="23" /></span>
          <div>
            <p>冻结余额</p>
            <strong>
              {{ formatNumber(wallet.data.value.frozenBalance) }}
              <small>虚拟币</small>
            </strong>
            <p class="small muted">任务报酬暂存，验收或取消后按规则处理。</p>
          </div>
        </article>
      </div>
    </AsyncPanel>
    <section class="panel">
      <div class="panel-heading">
        <div>
          <h2>交易明细</h2>
          <p class="small muted">金额为整数虚拟币，正数入账，负数出账。</p>
        </div>
        <label class="inline-field">
          流水类型
          <select v-model="type">
            <option value="">全部类型</option>
            <option v-for="(label, key) in transactionTypes" :key="key" :value="key">
              {{ label }}
            </option>
          </select>
        </label>
      </div>
      <AsyncPanel
        :loading="loading"
        :error="error"
        :empty="!items.length"
        empty-text="暂无交易记录"
        @retry="reload"
      >
        <div class="table-wrap">
          <table>
            <thead>
              <tr>
                <th>流水 / 时间</th>
                <th>类型</th>
                <th>关联任务</th>
                <th class="align-right">变动金额</th>
                <th class="align-right">变动后余额</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in items" :key="item.transactionId">
                <td>
                  <strong>#{{ item.transactionId }}</strong>
                  <small>{{ formatDate(item.createdAt) }}</small>
                </td>
                <td>{{ transactionTypes[item.type] || item.type }}</td>
                <td>
                  <RouterLink
                    v-if="item.taskId"
                    class="text-link"
                    :to="{ name: 'task-detail', params: { taskId: item.taskId } }"
                  >
                    #{{ item.taskId }}
                  </RouterLink>
                  <span v-else>—</span>
                  <small v-if="item.appealId">申诉 #{{ item.appealId }}</small>
                </td>
                <td class="align-right amount" :class="item.amount > 0 ? 'positive' : 'negative'">
                  {{ item.amount > 0 ? '+' : '' }}{{ formatNumber(item.amount) }}
                </td>
                <td class="align-right">{{ formatNumber(item.balanceAfter) }}</td>
              </tr>
            </tbody>
          </table>
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
