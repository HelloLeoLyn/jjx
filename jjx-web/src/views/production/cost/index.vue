<template>
  <div class="cost-page">
    <div class="page-header">
      <h1 class="page-title">成本核算</h1>
    </div>

    <!-- 汇总卡片 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-value">{{ summary.totalOrders }}</div>
          <div class="stat-label">工单总数</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-value cost">¥{{ formatMoney(summary.totalCost) }}</div>
          <div class="stat-label">总成本</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-value material">¥{{ formatMoney(summary.totalMaterialCost) }}</div>
          <div class="stat-label">材料成本</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never">
          <div class="stat-value labor">¥{{ formatMoney(summary.totalLaborCost) }}</div>
          <div class="stat-label">人工成本</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 表格 -->
    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" style="width:100%">
        <el-table-column prop="orderNo" label="工单编号" width="160" />
        <el-table-column prop="productName" label="产品名称" min-width="160" />
        <el-table-column prop="plannedQuantity" label="计划数量" width="100" align="right" />
        <el-table-column prop="completedQuantity" label="完成数量" width="100" align="right" />
        <el-table-column label="材料成本" width="120" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.materialCost) }}</template>
        </el-table-column>
        <el-table-column label="人工成本" width="120" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.laborCost) }}</template>
        </el-table-column>
        <el-table-column label="总成本" width="120" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.totalCost) }}</template>
        </el-table-column>
        <el-table-column label="单位成本" width="110" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.unitCost) }}</template>
        </el-table-column>
        <el-table-column label="标准成本" width="110" align="right">
          <template #default="{ row }">¥{{ formatMoney(row.standardCost) }}</template>
        </el-table-column>
        <el-table-column label="差异" width="110" align="right">
          <template #default="{ row }">
            <span :style="{ color: row.costDiff > 0 ? '#f56c6c' : '#67c23a' }">
              ¥{{ formatMoney(row.costDiff) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="orderStatus" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="statusType(row.orderStatus)" size="small">{{ statusLabel(row.orderStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import type { TagType } from '@/types'
import { getCostList, getCostSummary } from '@/api/production/cost'

const loading = ref(false)
const list = ref<any[]>([])
const summary = reactive({
  totalOrders: 0,
  totalMaterialCost: 0,
  totalLaborCost: 0,
  totalCost: 0,
  avgOrderCost: 0,
})

function formatMoney(v: number | string) {
  const n = Number(v) || 0
  return n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function statusType(s: string): TagType {
  const map: Record<string, TagType> = { DRAFT: 'info', PENDING_APPROVAL: 'warning', APPROVED: 'primary', IN_PROGRESS: 'info', COMPLETED: 'success', CANCELLED: 'danger', CLOSED: 'info' }
  return map[s] || 'info'
}

function statusLabel(s: string) {
  return { DRAFT: '草稿', PENDING_APPROVAL: '待审批', APPROVED: '已审批', IN_PROGRESS: '进行中', COMPLETED: '已完成', CANCELLED: '已取消', CLOSED: '已关闭' }[s] || s
}

async function loadData() {
  loading.value = true
  try {
    const [listRes, sumRes] = await Promise.all([getCostList(), getCostSummary()])
    if (listRes?.data) list.value = listRes.data
    if (sumRes?.data) Object.assign(summary, sumRes.data)
  } finally {
    loading.value = false
  }
}

onMounted(loadData)
</script>

<style scoped>
.cost-page { padding: 20px; }
.page-header { margin-bottom: 20px; }
.page-title { margin: 0; font-size: 24px; font-weight: 500; }
.stat-row { margin-bottom: 16px; }
.stat-value { font-size: 28px; font-weight: 700; color: #303133; }
.stat-value.cost { color: #409eff; }
.stat-value.material { color: #e6a23c; }
.stat-value.labor { color: #67c23a; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
</style>
