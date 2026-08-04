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
            <el-dropdown-item command="submit-review" v-if="order.orderStatus === 0">
              <el-icon><Promotion /></el-icon>
              提交审核
            </el-dropdown-item>
            <el-dropdown-item command="approve" v-if="order.orderStatus === 1">
              <el-icon><Check /></el-icon>
              审核通过
            </el-dropdown-item>
            <el-dropdown-item command="reject" v-if="order.orderStatus === 1">
              <el-icon><CloseBold /></el-icon>
              审核驳回
            </el-dropdown-item>
            <el-dropdown-item command="copy">
              <el-icon><CopyDocument /></el-icon>
              复制订单
            </el-dropdown-item>
            <el-dropdown-item command="pick-material" v-if="order.orderStatus === 2">
              <el-icon><Box /></el-icon>
              生成领料单
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

    <!-- 操作预览器 -->
    <OperationPreviewDialog
      v-model="previewVisible"
      :operation="previewOperation"
      :biz-id="previewBizId"
      :biz-no="previewBizNo"
      :status-text-map="orderStatusTextMap"
      @success="handlePreviewSuccess"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { More, CopyDocument, Download, Printer, Clock, Box, Promotion, Check, CloseBold } from '@element-plus/icons-vue'
import OperationPreviewDialog from '@/components/OperationPreviewDialog/index.vue'
import { getOperation } from '@/components/OperationPreviewDialog/registry'
import { ProductionOrderStatusEnum } from '@/enums/production/WorkOrderEnum'
import type { ProductionOrderVO } from '@/types/production/order'

interface Props {
  order: ProductionOrderVO
}

const props = defineProps<Props>()
const emit = defineEmits<{
  view: []
  edit: []
  convert: []
  start: []
  complete: []
  cancel: []
  delete: []
  'more-action': [command: string]
  refresh: []
}>()

// 计算属性
const canDelete = computed(() => {
  return props.order.orderStatus === 0 || props.order.orderStatus === 9
})

// 操作预览器状态
const previewVisible = ref(false)
const previewOperation = ref<any>(null)
const previewBizId = ref<number | null>(null)
const previewBizNo = ref('')

const orderStatusTextMap: Record<number, string> = Object.fromEntries(
  ProductionOrderStatusEnum.items.map((i) => [Number(i.value), i.label])
)

function openPreview(opKey: string) {
  const op = getOperation(opKey)
  if (!op) {
    ElMessage.warning(`未注册的操作：${opKey}`)
    return
  }
  previewOperation.value = op
  previewBizId.value = Number(props.order.orderId)
  previewBizNo.value = props.order.orderNo
  previewVisible.value = true
}

function handlePreviewSuccess() {
  emit('refresh')
}

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
  openPreview('production.start')
}

const handleComplete = () => {
  openPreview('production.complete')
}

const handleCancel = () => {
  openPreview('production.cancel')
}

const handleDelete = () => {
  emit('delete')
}

const handleMoreActionCommand = (command: string) => {
  if (command === 'submit-review') {
    openPreview('production.submitReview')
  } else if (command === 'approve') {
    openPreview('production.approve')
  } else if (command === 'reject') {
    openPreview('production.reject')
  } else if (command === 'delete') {
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
