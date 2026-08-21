<template>
  <div class="table-actions">
    <el-space :size="4">
      <!-- 查看按钮 -->
      <el-tooltip content="查看详情" placement="top">
        <el-button type="primary" size="small" icon="View" circle @click="handleView" />
      </el-tooltip>

      <!-- 生产履历（P4-C：只读时间线） -->
      <el-tooltip content="生产履历" placement="top">
        <el-button type="primary" size="small" icon="Tickets" v-hasPermi="['production:order:view']" circle @click="handleProductionTrace" />
      </el-tooltip>

      <!-- 编辑按钮 -->
      <el-tooltip content="编辑订单" placement="top" v-if="order.canEdit">
        <el-button type="primary" size="small" icon="Edit" v-hasPermi="['production:order:edit']" circle @click="handleEdit" />
      </el-tooltip>

      <!-- 转为工单按钮 -->
      <el-tooltip content="转为工单" placement="top" v-if="order.canConvertToWorkOrder">
        <el-button type="success" size="small" icon="RefreshRight" v-hasPermi="['production:order:edit']" circle @click="handleConvert" />
      </el-tooltip>

      <!-- 开始执行按钮 -->
      <el-tooltip content="开始执行" placement="top" v-if="order.canStart">
        <el-button type="warning" size="small" icon="VideoPlay" v-hasPermi="['production:operation-execution:edit']" circle @click="handleStart" />
      </el-tooltip>

      <!-- 完成按钮 -->
      <el-tooltip content="完成工单" placement="top" v-if="order.canComplete">
        <el-button type="success" size="small" icon="CircleCheck" v-hasPermi="['production:operation-execution:edit']" circle @click="handleComplete" />
      </el-tooltip>

      <!-- 生成领料单（2026-08-18：从下拉菜单提为行内按钮，高频操作） -->
      <el-tooltip
        :content="order.materialStatus === 1 ? '已生成领料单（待确认发料）' : '生成领料单'"
        placement="top"
        v-if="[2, 6].includes(order.orderStatus) && order.materialStatus !== 2"
      >
        <el-button
          type="warning"
          size="small"
          icon="Box"
          :disabled="order.materialStatus === 1"
          v-hasPermi="['production:order:edit']"
          circle
          @click="handlePickMaterial"
        />
      </el-tooltip>

      <!-- 更多操作 -->
      <el-dropdown @command="handleMoreActionCommand">
        <el-button size="small" circle>
          <el-icon><More /></el-icon>
        </el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="submit-review" v-if="order.orderStatus === 0 && hasPermi(['production:order:edit'])">
              <el-icon><Promotion /></el-icon>
              提交审核
            </el-dropdown-item>
            <el-dropdown-item command="approve" v-if="order.orderStatus === 1 && hasPermi(['production:order:edit'])">
              <el-icon><Check /></el-icon>
              审核通过
            </el-dropdown-item>
            <el-dropdown-item command="reject" v-if="order.orderStatus === 1 && hasPermi(['production:order:edit'])">
              <el-icon><CloseBold /></el-icon>
              审核驳回
            </el-dropdown-item>
            <el-dropdown-item command="copy" v-if="hasPermi(['production:order:add'])">
              <el-icon><CopyDocument /></el-icon>
              复制订单
            </el-dropdown-item>
            <el-dropdown-item command="cancel" v-if="order.canCancel && hasPermi(['production:order:edit'])">
              <el-icon><CircleClose /></el-icon>
              取消订单
            </el-dropdown-item>
            <el-dropdown-item command="export" v-if="hasPermi(['production:order:export'])">
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
            <el-dropdown-item command="trace">
              <el-icon><Connection /></el-icon>
              查看流水
            </el-dropdown-item>
            <el-dropdown-item divided command="delete" v-if="canDelete" v-hasPermi="['production:order:delete']">
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
import { hasPermi } from '@/directives'
import { ElMessage } from 'element-plus'
import { More, CopyDocument, Download, Printer, Clock, Box, Promotion, Check, CloseBold, Tickets } from '@element-plus/icons-vue'
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
  trace: [order: any]
  'production-trace': [order: any]
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

// P4-C：生产履历（只读时间线）
const handleProductionTrace = () => {
  emit('production-trace', props.order)
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

// 2026-08-18：生成领料单提为行内按钮（原下拉菜单）
const handlePickMaterial = () => {
  emit('more-action', 'pick-material')
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
  } else if (command === 'trace') {
    emit('trace', props.order)
  } else if (command === 'cancel') {
    // 2026-08-18：取消订单从行内移入下拉菜单
    openPreview('production.cancel')
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
