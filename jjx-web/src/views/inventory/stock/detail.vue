<template>
  <div class="stock-detail">
    <!-- 面包屑 -->
    <el-page-header :content="`批次明细 - ${materialName}`" @back="goBack" />

    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="批次号">
          <el-input
            v-model="queryParams.batchNo"
            placeholder="请输入批次号"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="仓库">
          <el-select
            v-model="queryParams.warehouseId"
            placeholder="请选择仓库"
            clearable
            style="width: 150px"
          >
            <el-option
              v-for="item in warehouseOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="库位">
          <el-select
            v-model="queryParams.locationId"
            placeholder="请选择库位"
            clearable
            style="width: 150px"
          >
            <el-option
              v-for="item in locationOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="queryParams.status"
            placeholder="请选择状态"
            clearable
            style="width: 120px"
          >
            <el-option label="生效" :value="1" />
            <el-option label="未生效" :value="0" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 汇总信息 -->
    <el-card class="summary-card">
      <el-descriptions :column="4" border>
        <el-descriptions-item label="物料编码">{{ materialCode }}</el-descriptions-item>
        <el-descriptions-item label="物料名称">{{ materialName }}</el-descriptions-item>
        <el-descriptions-item label="规格型号">{{ specification }}</el-descriptions-item>
        <el-descriptions-item label="单位">{{ unit }}</el-descriptions-item>
        <el-descriptions-item label="总库存数量">{{ totalQuantity }}</el-descriptions-item>
        <el-descriptions-item label="总预留数量">{{ totalReserved }}</el-descriptions-item>
        <el-descriptions-item label="可用数量">{{ availableQuantity }}</el-descriptions-item>
        <el-descriptions-item label="最早有效期">{{ earliestExpiry || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 批次明细表格 -->
    <el-card class="table-card">
      <el-table v-loading="loading" :data="itemList" border style="width: 100%">
        <el-table-column label="批次号" prop="batchNo" width="130" />
        <el-table-column label="仓库" prop="warehouseName" width="120" />
        <el-table-column label="库位" prop="locationName" width="120" />
        <el-table-column label="数量" prop="quantity" width="100" align="right" />
        <el-table-column label="预留数量" prop="reservedQuantity" width="100" align="right" />
        <el-table-column label="可用数量" prop="availableQuantity" width="100" align="right" />
        <el-table-column label="单位成本" prop="unitCost" width="100" align="right">
          <template #default="{ row }">
            {{ row.unitCost ? formatCurrency(row.unitCost) : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="生产日期" prop="productionDate" width="110" align="center">
          <template #default="{ row }">
            {{ row.productionDate || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="有效期至" prop="expiryDate" width="110" align="center">
          <template #default="{ row }">
            <span v-if="row.expiryDate" :class="{ expiring: isExpiring(row.expiryDate) }">
              {{ row.expiryDate }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" size="small">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后入库" prop="lastInboundTime" width="160" align="center">
          <template #default="{ row }">
            {{ row.lastInboundTime || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="最后出库" prop="lastOutboundTime" width="160" align="center">
          <template #default="{ row }">
            {{ row.lastOutboundTime || '-' }}
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.current"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'StockDetail',
})

import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { stockItemApi } from '@/api/inventory/stockItem'
import { stockApi } from '@/api/inventory/stock'
import { formatCurrency } from '@/utils/format'
import type { StockItemQueryParams, StockItemVO, StockVO } from '@/types/inventory/stock'

const route = useRoute()
const router = useRouter()

const materialId = route.params.materialId as string

// 物料汇总信息
const materialCode = ref('')
const materialName = ref('')
const specification = ref('')
const unit = ref('')
const totalQuantity = ref(0)
const totalReserved = ref(0)
const availableQuantity = ref(0)
const earliestExpiry = ref('')

// 查询参数
const queryParams = reactive<StockItemQueryParams>({
  current: 1,
  pageSize: 10,
  materialId: materialId,
  batchNo: '',
  warehouseId: undefined,
  locationId: undefined,
  status: undefined,
})

// 响应式数据
const loading = ref(false)
const itemList = ref<StockItemVO[]>([])
const total = ref(0)

// 仓库选项（示例，实际应从API获取）
const warehouseOptions = ref<{ value: string; label: string }[]>([])
const locationOptions = ref<{ value: string; label: string }[]>([])

// 获取物料汇总信息
const getMaterialSummary = async () => {
  try {
    const res = await stockApi.getByMaterial(materialId)
    const data = res.data as StockVO
    if (data) {
      materialCode.value = data.materialCode || ''
      materialName.value = data.materialName || ''
      specification.value = data.specification || ''
      unit.value = data.unit || ''
      totalQuantity.value = data.totalQuantity || 0
      totalReserved.value = data.totalReserved || 0
      availableQuantity.value = data.availableQuantity || 0
      earliestExpiry.value = data.earliestExpiry || ''
    }
  } catch (error) {
    console.error('获取物料汇总信息失败:', error)
  }
}

// 获取批次明细列表
const getList = async () => {
  loading.value = true
  try {
    const res = await stockItemApi.list(queryParams)
    itemList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('获取批次明细失败:', error)
    ElMessage.error('获取批次明细失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleQuery = () => {
  queryParams.current = 1
  getList()
}

// 重置
const handleReset = () => {
  queryParams.current = 1
  queryParams.batchNo = ''
  queryParams.warehouseId = undefined
  queryParams.locationId = undefined
  queryParams.status = undefined
  getList()
}

// 返回
const goBack = () => {
  router.back()
}

// 判断是否临期（30天内）
const isExpiring = (dateStr: string): boolean => {
  if (!dateStr) return false
  const expiryDate = new Date(dateStr)
  const now = new Date()
  const diffDays = Math.ceil((expiryDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24))
  return diffDays <= 30 && diffDays >= 0
}

onMounted(() => {
  getMaterialSummary()
  getList()
})
</script>

<style scoped>
.stock-detail {
  padding: 20px;
}

.search-card,
.summary-card,
.table-card {
  margin-top: 16px;
}

.expiring {
  color: #e6a23c;
  font-weight: bold;
}
</style>
