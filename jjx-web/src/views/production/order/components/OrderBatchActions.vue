<template>
  <div class="batch-actions">
    <el-space>
      <el-button type="primary" icon="Plus" @click="handleCreate" :loading="saving">
        新建订单
      </el-button>

      <el-dropdown @command="handleBatchCommand" v-if="selectedRows.length > 0">
        <el-button type="primary">
          批量操作
          <el-icon class="el-icon--right">
            <ArrowDown />
          </el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="approve" v-if="canBatchApprove">
              <el-icon><Check /></el-icon>
              批量审批
            </el-dropdown-item>
            <el-dropdown-item command="start" v-if="canBatchStart">
              <el-icon><VideoPlay /></el-icon>
              批量开始
            </el-dropdown-item>
            <el-dropdown-item command="complete" v-if="canBatchComplete">
              <el-icon><CircleCheck /></el-icon>
              批量完成
            </el-dropdown-item>
            <el-dropdown-item command="cancel" v-if="canBatchCancel">
              <el-icon><CircleClose /></el-icon>
              批量取消
            </el-dropdown-item>
            <el-dropdown-item divided command="delete" v-if="canBatchDelete">
              <el-icon><Delete /></el-icon>
              批量删除
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <el-button
        type="danger"
        icon="Delete"
        @click="handleBatchDelete"
        :disabled="selectedRows.length === 0"
        :loading="deleting"
      >
        批量删除
      </el-button>

      <el-button icon="Refresh" @click="handleRefresh" :loading="loading"> 刷新 </el-button>

      <el-button icon="Download" @click="handleExport"> 导出 </el-button>
    </el-space>

    <div class="selection-info" v-if="selectedRows.length > 0">
      已选择 {{ selectedRows.length }} 个订单
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import {
  ArrowDown,
  Check,
  VideoPlay,
  CircleCheck,
  CircleClose,
  Delete,
} from '@element-plus/icons-vue'
import type { ProductionOrderVO } from '@/types/production/order'

interface Props {
  selectedRows: ProductionOrderVO[]
  viewType: 'plan' | 'work_order' | 'all' | 'gantt'
  loading?: boolean
  saving?: boolean
  deleting?: boolean
}

interface Emits {
  (e: 'create'): void
  (e: 'refresh'): void
  (e: 'export'): void
  (e: 'batch-delete'): void
  (e: 'batch-command', command: string): void
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  saving: false,
  deleting: false,
})

const emit = defineEmits<Emits>()

// 计算属性
const canBatchApprove = computed(() => {
  if (props.selectedRows.length === 0) return false
  return props.selectedRows.every(
    (row) => row.orderType === 'plan' && row.orderStatus === 'pending_approval'
  )
})

const canBatchStart = computed(() => {
  if (props.selectedRows.length === 0) return false
  return props.selectedRows.every(
    (row) => row.orderType === 'work_order' && row.orderStatus === 'scheduled'
  )
})

const canBatchComplete = computed(() => {
  if (props.selectedRows.length === 0) return false
  return props.selectedRows.every(
    (row) => row.orderType === 'work_order' && row.orderStatus === 'in_progress'
  )
})

const canBatchCancel = computed(() => {
  if (props.selectedRows.length === 0) return false
  return props.selectedRows.every(
    (row) => row.orderStatus !== 'completed' && row.orderStatus !== 'cancelled'
  )
})

const canBatchDelete = computed(() => {
  if (props.selectedRows.length === 0) return false
  return props.selectedRows.every(
    (row) => row.orderStatus === 'draft' || row.orderStatus === 'cancelled'
  )
})

// 方法
const handleCreate = () => {
  emit('create')
}

const handleRefresh = () => {
  emit('refresh')
}

const handleExport = () => {
  emit('export')
}

const handleBatchDelete = () => {
  emit('batch-delete')
}

const handleBatchCommand = (command: string) => {
  emit('batch-command', command)
}
</script>

<style scoped>
.batch-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 16px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.selection-info {
  font-size: 14px;
  color: #606266;
  padding: 4px 8px;
  background-color: #e6f7ff;
  border-radius: 4px;
  border: 1px solid #91d5ff;
}
</style>
