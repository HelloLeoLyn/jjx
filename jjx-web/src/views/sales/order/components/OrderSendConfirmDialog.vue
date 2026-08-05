<template>
  <el-dialog
    :title="`发送客户确认 - ${order?.orderNo || ''}`"
    v-model="dialogVisible"
    width="560px"
    append-to-body
    destroy-on-close
    @open="init"
  >
    <div v-if="order" class="send-confirm">
      <!-- 订单摘要 -->
      <el-descriptions :column="2" border size="small" class="order-summary">
        <el-descriptions-item label="订单号">{{ order.orderNo }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ order.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="币种">{{ order.currency || 'CNY' }}</el-descriptions-item>
        <el-descriptions-item label="订单金额">{{ fmt(order.finalAmount) }}</el-descriptions-item>
      </el-descriptions>

      <!-- 备注 -->
      <div class="field-block">
        <div class="field-label">发送备注</div>
        <el-input
          v-model="context"
          type="textarea"
          :rows="2"
          placeholder="给客户的确认说明（选填）"
          :maxlength="200"
          show-word-limit
        />
      </div>

      <!-- 凭证附件（截图/文件均可，DEV-343/314） -->
      <div class="field-block">
        <div class="field-label">
          确认凭证
          <span class="field-tip">客户回复的截图 / 确认文件 / PDF，作为确认依据（选填）</span>
        </div>
        <el-button size="small" @click="attachmentVisible = true">
          <el-icon style="margin-right: 4px"><Paperclip /></el-icon>
          {{
            attachmentCount > 0 ? `已上传 ${attachmentCount} 个凭证（点击查看/补充）` : '上传凭证（截图/文件）'
          }}
        </el-button>
      </div>
    </div>

    <!-- 凭证附件弹窗 -->
    <AttachmentUploadDialog
      v-model="attachmentVisible"
      biz-type="sales_order_confirmation"
      :biz-id="order?.orderId ?? 0"
      :dialog-title="`确认凭证 - ${order?.orderNo || ''}`"
      @success="loadAttachmentCount"
    />

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="sending" @click="handleSend">发送确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Paperclip } from '@element-plus/icons-vue'
import { orderStatusApi } from '@/api/sales/orderStatus'
import { attachmentApi } from '@/api/system/attachment'
import { alertApi } from '@/api/inventory/alert'
import AttachmentUploadDialog from '@/components/AttachmentUploadDialog/index.vue'

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

const sending = ref(false)
const context = ref('')
const attachmentVisible = ref(false)
const attachmentCount = ref(0)

const fmt = (v?: number | string | null): string => {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  return Number.isNaN(n) ? String(v) : n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

const loadAttachmentCount = async () => {
  if (!props.order?.orderId) return
  try {
    const res: any = await attachmentApi.list('sales_order_confirmation', props.order.orderId)
    attachmentCount.value = Array.isArray(res?.data) ? res.data.length : 0
  } catch {
    attachmentCount.value = 0
  }
}

// 发送客户确认（凭证可后续在"确认凭证"入口补充）
const handleSend = async () => {
  if (!props.order?.orderId) return
  sending.value = true
  try {
    await orderStatusApi.sendToCustomer(props.order.orderId, context.value || undefined)
    ElMessage.success('发送成功，等待客户确认')
    dialogVisible.value = false
    emit('success')
    // DEV-583：确认后检查缺料，有则弹窗提示（不阻断）
    try {
      const res: any = await alertApi.countUnprocessedShortage(props.order.orderId)
      const shortageCount = res?.data ?? 0
      if (Number(shortageCount) > 0) {
        ElMessageBox.alert(
          `订单【${props.order.orderNo}】齐套检查发现 ${shortageCount} 种物料缺料，已生成缺料预警，请及时安排补货（可在库存预警查看明细）。`,
          '缺料提示',
          { confirmButtonText: '知道了', type: 'warning' },
        )
      }
    } catch {
      // 缺料检查失败不阻断
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '发送失败')
  } finally {
    sending.value = false
  }
}

// 打开时加载凭证数量
const init = () => {
  context.value = ''
  attachmentCount.value = 0
  loadAttachmentCount()
}
</script>

<style scoped lang="scss">
.send-confirm {
  .order-summary {
    margin-bottom: 14px;
  }

  .field-block {
    margin-bottom: 14px;

    .field-label {
      font-size: 13px;
      color: #606266;
      margin-bottom: 6px;
      display: flex;
      align-items: center;
      gap: 8px;

      .field-tip {
        font-size: 12px;
        color: #909399;
      }
    }
  }
}
</style>
