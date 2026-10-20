<template>
  <el-dialog v-model="dialogVisible" title="生成生产计划（确认订单）" width="1200" @closed="reset">
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

    <!-- 确认书（选填，2026-08-13：生成计划=确认动作，与确认凭证同分类，未传可后续补充；
         dev-20261018-019：统一为 Upload + Manager 共享会话，点「确定生成」时先落定附件再生成计划） -->
    <div class="field-block">
      <div class="field-label">确认书（选填）</div>
      <template v-if="visible && order?.orderId">
        <AttachmentUpload
          :session="attachSession"
          class="attach-block"
          :accept="['.pdf', '.jpg', '.jpeg', '.png', '.doc', '.docx']"
          button-text="上传确认书"
          @uploaded="(item) => attachSession.registerUploaded(item.pending, item.ownership)"
          @error="onAttachError"
        />
        <AttachmentManager
          :session="attachSession"
          class="attach-block"
          title="确认书"
          source-type="sales_order"
          :source-no="order?.orderNo || ''"
          row-save
          batch-save
          @error="onAttachError"
        />
        <div class="attach-hint">确认书会先进入「待保存」，点「确定生成」时自动建立引用。</div>
      </template>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="loading" @click="handleConfirm">确定生成</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AttachmentUpload from '@/components/attachment/AttachmentUpload.vue'
import AttachmentManager from '@/components/attachment/AttachmentManager.vue'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import {
  useAttachmentSession,
  type AttachmentChanges,
  type AttachmentSaveResult,
} from '@/composables/useAttachmentSession'
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

/**
 * 确认书附件会话（dev-20261018-019）：
 * 订单此时已保存，弹窗内没有「订单保存」可搭，因此由本弹窗在「确定生成」时统一落定：
 * 新上传先待保存，落定成功才清队列；失败保留并中止生成（不丢证据、不假成功）。
 */
const attachSession = useAttachmentSession({
  sourceType: 'sales_order',
  sourceId: computed(() => props.order?.orderId ?? null),
  traceId: computed(() => props.order?.traceId || ''),
  viewPerm: 'sales:order:view',
  editPerm: 'sales:order:edit',
  saveHandler: saveConfirmDocs,
})

async function saveConfirmDocs(changes: AttachmentChanges): Promise<AttachmentSaveResult> {
  const sourceId = Number(props.order?.orderId)
  if (!sourceId) throw new Error('订单尚未保存，不能上传确认书')
  const acceptedUploads: { attachmentId: number; refId: number }[] = []
  for (const entry of changes.uploads) {
    if (!entry.documentType) {
      throw new Error(`附件「${entry.fileName || entry.attachmentId}」未选择资料类型`)
    }
    const res: any = await attachmentRefApi.createRef({
      attachmentId: entry.attachmentId,
      sourceType: 'sales_order',
      sourceId,
      productId: null,
      productCode: null,
      documentType: entry.documentType,
    })
    const refId = Number(res?.data)
    if (!refId) throw new Error(res?.msg || '确认书附件保存失败')
    acceptedUploads.push({ attachmentId: entry.attachmentId, refId })
  }
  const acceptedUpdates: number[] = []
  if (changes.updates.length) {
    const items = changes.updates.map((u) => ({
      refId: u.refId,
      documentType: u.documentType,
      remark: u.remark,
      sortOrder: u.sortOrder,
    }))
    await attachmentRefApi.batchUpdate(items)
    acceptedUpdates.push(...items.map((item) => item.refId))
  }
  return { acceptedUploads, acceptedUpdates }
}

function onAttachError(payload: { action: string; message: string }) {
  if (payload.action === 'upload') return
  ElMessage.error(payload.message || '确认书处理失败')
}

const loading = ref(false)

const fmt = (v?: number | string | null): string => {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  return Number.isNaN(n)
    ? String(v)
    : n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const handleConfirm = async () => {
  if (!props.order?.orderId) return
  loading.value = true
  try {
    // 确认书/资料改动先落定：无类型、上传未完成或无权限时中止，不生成计划
    const committed = await attachSession.commit({ kind: 'all' })
    if (!committed.validation.ok) {
      ElMessage.warning(committed.validation.issues[0]?.message || '确认书附件未就绪，请处理后重试')
      return
    }
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
.attach-block {
  margin-bottom: 8px;
}
.attach-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
