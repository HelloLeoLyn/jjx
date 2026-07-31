<template>
  <div class="table-actions">
    <el-space :size="4">
      <!-- 查看按钮 -->
      <el-tooltip content="查看详情" placement="top">
        <el-button type="primary" size="small" icon="View" circle @click="handleView" />
      </el-tooltip>

      <!-- 编辑按钮 -->
      <el-tooltip content="编辑订单" placement="top" v-if="order.canEdit">
        <el-button type="primary" size="small" icon="Edit" circle @click="handleEdit" />
      </el-tooltip>

      <!-- 转为工单按钮 -->
      <el-tooltip content="转为工单" placement="top" v-if="order.canConvertToWorkOrder">
        <el-button type="success" size="small" icon="RefreshRight" circle @click="handleConvert" />
      </el-tooltip>

      <!-- 开始执行按钮 -->
      <el-tooltip content="开始执行" placement="top" v-if="order.canStart">
        <el-button type="warning" size="small" icon="VideoPlay" circle @click="handleStart" />
      </el-tooltip>

      <!-- 完成按钮 -->
      <el-tooltip content="完成工单" placement="top" v-if="order.canComplete">
        <el-button type="success" size="small" icon="CircleCheck" circle @click="handleComplete" />
      </el-tooltip>

      <!-- 取消按钮 -->
      <el-tooltip content="取消订单" placement="top" v-if="order.canCancel">
        <el-button type="danger" size="small" icon="CircleClose" circle @click="handleCancel" />
      </el-tooltip>

      <!-- 更多操作 -->
      <el-dropdown @command="handleMoreActionCommand">
        <el-button size="small" circle>
          <el-icon><More /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="copy">
              <el-icon><CopyDocument /></el-icon>
              复制订单
            </el-dropdown-item>
            <el-dropdown-item command="export">
              <el-icon><Download /></el-icon>
              导出订单
            </el-dropdown-item>
            <el-dropdown-item command="print">
              <el-icon><Printer /></el-icon>
              打印订单
            </el-dropdown-item>
            <el-dropdown-item command="history">
              <el-icon><Clock /></el-icon>
              操作历史
            </el-dropdown-item>
            <el-dropdown-item divided command="delete" v-if="canDelete">
              <el-icon><Delete /></el-icon>
              删除订单
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </el-space>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { More, CopyDocument, Download, Printer, Clock } from '@element-plus/icons-vue'
import type { ProductionOrderVO } from '@/types/production/order'

interface Props {
  order: ProductionOrderVO
}

interface Emits {
  (e: 'view'): void
  (e: 'edit'): void
  (e: 'convert'): void
  (e: 'start'): void
  (e: 'complete'): void
  (e: 'cancel'): void
  (e: 'delete'): void
  (e: 'more-action', command: string): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// 计算属性
const canDelete = computed(() => {
  return props.order.orderStatus === 0 || props.order.orderStatus === 9
})

// 方法
const handleView = () => {
  emit('view')
}

const handleEdit = () => {
  emit('edit')
}

const handleConvert = () => {
  emit('convert')
}

const handleStart = () => {
  emit('start')
}

const handleComplete = () => {
  emit('complete')
}

const handleCancel = () => {
  emit('cancel')
}

const handleDelete = () => {
  emit('delete')
}

const handleMoreActionCommand = (command: string) => {
  if (command === 'delete') {
    handleDelete()
  } else {
    emit('more-action', command)
  }
}
</script>

<style scoped>
.table-actions {
  display: flex;
  align-items: center;
}
</style>
