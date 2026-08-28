<template>
  <div class="exchange-rate-page">
    <el-card shadow="never" v-loading="loading">
      <template #header>
        <div class="card-header">
          <div>
            <span class="card-title">汇率管理</span>
            <el-tag :type="snapshot?.source === 'live' ? 'success' : 'danger'" class="source-tag">
              {{ snapshot?.source === 'live' ? '实时汇率' : '兜底汇率' }}
            </el-tag>
          </div>
          <el-button type="primary" :loading="loading" @click="loadRates">手动刷新</el-button>
        </div>
      </template>

      <el-alert
        v-if="snapshot?.source === 'fallback'"
        title="外部汇率服务当前不可用，业务正在使用系统参数中的汇率兜底值。"
        type="warning"
        show-icon
        :closable="false"
        class="fallback-alert"
      />

      <div class="base-line">本位币：{{ snapshot?.base || 'CNY' }}（1 外币兑换人民币）</div>
      <el-table :data="rateRows" border>
        <el-table-column prop="currency" label="币种" width="180" />
        <el-table-column prop="rate" label="汇率（CNY）">
          <template #default="{ row }">{{ formatRate(row.rate) }}</template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getLatestExchangeRates, type ExchangeRateSnapshot } from '@/api/system/exchangeRate'

defineOptions({ name: 'SystemExchangeRate' })

const loading = ref(false)
const snapshot = ref<ExchangeRateSnapshot>()
const rateRows = computed(() =>
  Object.entries(snapshot.value?.rates || {})
    .map(([currency, rate]) => ({ currency, rate }))
    .sort((a, b) => a.currency.localeCompare(b.currency)),
)

function formatRate(rate: number) {
  return Number(rate).toFixed(4)
}

async function loadRates() {
  loading.value = true
  try {
    const res = await getLatestExchangeRates()
    snapshot.value = res.data || undefined
  } catch (error) {
    console.error('加载汇率失败:', error)
    ElMessage.error('加载汇率失败')
  } finally {
    loading.value = false
  }
}

onMounted(loadRates)
</script>

<style scoped>
.exchange-rate-page { padding: 16px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.card-title { font-size: 16px; font-weight: 600; }
.source-tag { margin-left: 12px; }
.fallback-alert { margin-bottom: 16px; }
.base-line { margin-bottom: 12px; color: #606266; }
</style>
