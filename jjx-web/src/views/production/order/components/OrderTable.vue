<template>
  <div class="order-table">
    <el-table
      v-loading="loading"
      :data="orderList"
      style="width: 100%"
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
      :row-key="rowKey"
    >
      <el-table-column type="selection" width="55" />

      <el-table-column prop="orderNo" label="订单编号" min-width="250" sortable="custom">
        <template #default="{ row }">
          <div class="order-no-cell">
            <el-tag
              :type="row.orderType === 'plan' ? 'primary' : 'warning'"
              size="small"
              effect="plain"
            >
              {{ row.orderType === 'plan' ? '计划' : '工单' }}
            </el-tag>
            <span class="order-no">{{ row.orderNo }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="productName" label="产品信息" min-width="200">
        <template #default="{ row }">
          <div class="product-info">
            <div class="product-name">{{ row.productName }}</div>
            <div class="product-code">{{ row.productCode }}</div>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="quantity" label="数量" width="120">
        <template #default="{ row }">
          <div class="quantity-info">
            <div class="quantity-label">计划: {{ row.plannedQuantity }}</div>
            <div class="quantity-label">完成: {{ row.completedQuantity }}</div>
            <div class="quantity-label">剩余: {{ row.remainingQuantity }}</div>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="progress" label="进度" width="120">
        <template #default="{ row }">
          <div class="progress-info">
            <el-progress
              :percentage="row.progress"
              :status="getProgressStatus(row.progress)"
              :show-text="false"
              style="width: 80px"
            />
            <span class="progress-label">{{ row.progressLabel }}</span>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="time" label="时间" width="180">
        <template #default="{ row }">
          <div class="time-info">
            <div class="time-label">计划: {{ row.planDateRange }}</div>
            <div class="time-label" v-if="row.actualTimeRange">实际: {{ row.actualTimeRange }}</div>
          </div>
        </template>
      </el-table-column>

      <el-table-column prop="status" label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.statusType as any" size="small">
            {{ row.statusLabel }}
          </el-tag>
          <el-tag v-if="row.reworkFlag === 1" type="danger" size="small">
            返工中
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="materialStatusLabel" label="领料状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="getMaterialStatusTagType(row.materialStatus) as any" size="small" effect="plain">
            {{ row.materialStatusLabel || '未领料' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="priority" label="优先级" width="80">
        <template #default="{ row }">
          <el-tag :type="getPriorityTagType(row.priority) as any" size="small" effect="plain">
            {{ row.priorityLabel }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <OrderTableActions
            :order="row"
            @view="() => handleView(row)"
            @edit="() => handleEdit(row)"
            @convert="() => handleConvert(row)"
            @start="() => handleStart(row)"
            @complete="() => handleComplete(row)"
            @cancel="() => handleCancel(row)"
            @delete="() => handleDelete(row)"
            @more-action="(command: string) => handleMoreAction(row, command)"
            @trace="(o: ProductionOrderVO) => emit('trace', o)"
            @production-trace="(o: ProductionOrderVO) => emit('production-trace', o)"
            @refresh="emit('refresh')"
          />
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination" v-if="total > 0">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import OrderTableActions from './OrderTableActions.vue'
import type { ProductionOrderVO } from '@/types/production/order'
import { getPriorityTagType } from '../utils/orderFormatters'

// 领料状态标签样式
function getMaterialStatusTagType(status?: number | null): string {
  const map: Record<number, string> = {
    0: 'info', // 未领料
    1: 'warning', // 待发料
    2: 'success', // 已领料
  }
  return map[status ?? 0] || 'info'
}

interface Props {
  orderList: ProductionOrderVO[]
  loading?: boolean
  total?: number
  pageNum?: number
  pageSize?: number
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  total: 0,
  pageNum: 1,
  pageSize: 20,
})

const emit = defineEmits<{
  'selection-change': [selection: ProductionOrderVO[]]
  'sort-change': [prop: string, order: 'ascending' | 'descending' | null]
  'page-change': [page: number, size: number]
  view: [order: ProductionOrderVO]
  edit: [order: ProductionOrderVO]
  convert: [order: ProductionOrderVO]
  start: [order: ProductionOrderVO]
  complete: [order: ProductionOrderVO]
  cancel: [order: ProductionOrderVO]
  delete: [order: ProductionOrderVO]
  'more-action': [order: ProductionOrderVO, command: string]
  trace: [order: ProductionOrderVO]
  'production-trace': [order: ProductionOrderVO]
  refresh: []
}>()

// 分页状态
const currentPage = ref(props.pageNum)
const pageSize = ref(props.pageSize)

// 计算属性
const rowKey = (row: ProductionOrderVO) => row.orderId

// 方法
const handleSelectionChange = (selection: ProductionOrderVO[]) => {
  emit('selection-change', selection)
}

const handleSortChange = ({
  prop,
  order,
}: {
  prop: string
  order: 'ascending' | 'descending' | null
}) => {
  emit('sort-change', prop, order)
}

const handleSizeChange = (size: number) => {
  pageSize.value = size
  emit('page-change', currentPage.value, size)
}

const handleCurrentChange = (page: number) => {
  currentPage.value = page
  emit('page-change', page, pageSize.value)
}

const handleView = (order: ProductionOrderVO) => {
  emit('view', order)
}

const handleEdit = (order: ProductionOrderVO) => {
  emit('edit', order)
}

const handleConvert = (order: ProductionOrderVO) => {
  emit('convert', order)
}

const handleStart = (order: ProductionOrderVO) => {
  emit('start', order)
}

const handleComplete = (order: ProductionOrderVO) => {
  emit('complete', order)
}

const handleCancel = (order: ProductionOrderVO) => {
  emit('cancel', order)
}

const handleDelete = (order: ProductionOrderVO) => {
  emit('delete', order)
}

const handleMoreAction = (order: ProductionOrderVO, command: string) => {
  emit('more-action', order, command)
}

const getProgressStatus = (progress: number) => {
  if (progress >= 100) return 'success'
  if (progress >= 80) return 'warning'
  return ''
}
</script>

<style scoped>
.order-table {
  margin-bottom: 20px;
}

.order-no-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.order-no {
  font-weight: 500;
}

.product-info {
  line-height: 1.4;
}

.product-name {
  font-weight: 500;
  margin-bottom: 2px;
}

.product-code {
  font-size: 12px;
  color: #909399;
}

.quantity-info {
  line-height: 1.4;
}

.quantity-label {
  font-size: 12px;
  color: #606266;
}

.progress-info {
  display: flex;
  align-items: center;
  gap: 8px;
}

.progress-label {
  font-size: 12px;
  color: #606266;
  min-width: 40px;
}

.time-info {
  line-height: 1.4;
}

.time-label {
  font-size: 12px;
  color: #606266;
  margin-bottom: 2px;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
