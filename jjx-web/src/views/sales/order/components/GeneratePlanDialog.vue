<template>
  <el-dialog v-model="dialogVisible" title="生成生产计划（确认订单）" width="600px" @closed="reset">
    <el-descriptions :column="2" border size="small">
      <el-descriptions-item label="订单号">{{ order?.orderNo || '-' }}</el-descriptions-item>
      <el-descriptions-item label="客户">{{ order?.customerName || '-' }}</el-descriptions-item>
      <el-descriptions-item label="订单金额">{{ fmt(order?.finalAmount) }}</el-descriptions-item>
      <el-descriptions-item label="交货日期">{{ order?.deliveryDate || '-' }}</el-descriptions-item>
    </el-descriptions>

    <el-alert
      type="info"
      :closable="false"
      style="margin: 12px 0"
      title="生成后订单进入「已确认」状态；计划需在【生产管理→生产订单→计划视图】审批后转工单，工单启动后订单进入「生产中」。"
    />

    <!-- 确认书上传（选填，2026-08-13：生成计划=确认动作，与确认凭证同分类，未传可后续补充） -->
    <div class="field-block">
      <div class="field-label">确认书（选填）</div>
      <AttachmentUploader
        biz-type="sales_order_confirmation"
        :biz-id="order?.orderId"
        button-text="上传确认书"
        tip="选填：客户确认书/签字扫描件等，未上传可在订单【确认凭证】入口补充"
        :accept="['.pdf', '.jpg', '.jpeg', '.png', '.doc', '.docx']"
      />
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm">确定生成</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import AttachmentUploader from '@/components/AttachmentUploader/index.vue'
import { orderStatusApi } from '@/api/sales/orderStatus'

interface Props {
  visible: boolean
  order?: any
}

const props = defineProps<Props>()

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'success'): void
}

const emit = defineEmits<Emits>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const loading = ref(false)

const fmt = (v?: number | string | null): string => {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  return Number.isNaN(n) ? String(v) : n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const handleConfirm = async () => {
  if (!props.order?.orderId) return
  loading.value = true
  try {
    await orderStatusApi.generatePlan(props.order.orderId)
    ElMessage.success('生产计划已生成，订单已确认，请到生产订单-计划视图审批')
    dialogVisible.value = false
    emit('success')
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '生成生产计划失败')
  } finally {
    loading.value = false
  }
}

const reset = () => {
  loading.value = false
}
</script>

<style scoped>
.field-block {
  margin-top: 12px;
}
.field-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 6px;
}
</style>
