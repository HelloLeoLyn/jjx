<template>
  <el-dialog
    :title="`发送客户确认 - ${order?.orderNo || ''}`"
    v-model="dialogVisible"
    width="560px"
    append-to-body
    destroy-on-close
    @open="loadAttachments"
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
        <!-- 2026-08-11 内嵌上传区，去掉嵌套弹窗 -->
        <el-upload
          :action="uploadUrl"
          :headers="uploadHeaders"
          :data="uploadData"
          multiple
          :limit="9"
          :on-success="onUploadSuccess"
          :on-error="onUploadError"
          :file-list="fileList"
          list-type="text"
          drag
          style="margin-bottom: 8px"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到此处，或 <em>点击上传</em></div>
          <template #tip>
            <div class="el-upload__tip">支持图片、PDF、Word、Excel 等，最多 9 个文件</div>
          </template>
        </el-upload>

        <!-- 已上传凭证列表 -->
        <div class="att-list" v-if="attachments.length">
          <div v-for="att in attachments" :key="att.id" class="att-item">
            <div class="att-info">
              <el-icon><Document /></el-icon>
              <el-link type="primary" :href="downloadUrl(att.id)" :underline="false" target="_blank">
                {{ att.fileName }}
              </el-link>
              <span class="att-size">{{ formatSize(att.fileSize) }}</span>
            </div>
            <el-button link type="danger" :icon="Delete" @click="onDelete(att)"></el-button>
          </div>
        </div>
        <el-empty v-else-if="!fileList.length" description="暂无凭证" :image-size="50" />
      </div>
    </div>

    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" :loading="sending" @click="handleSend">发送确认</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, Document, Delete } from '@element-plus/icons-vue'
import { orderStatusApi } from '@/api/sales/orderStatus'
import { attachmentApi } from '@/api/system/attachment'
import { alertApi } from '@/api/inventory/alert'

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

// ===== 凭证上传（2026-08-11 内嵌，替代嵌套弹窗 AttachmentUploadDialog） =====
const attachments = ref<any[]>([])
const fileList = ref<any[]>([])

const uploadUrl = computed(() => {
  const base = (import.meta.env.VITE_BASE_API || '/api') as string
  return `${base}/system/attachment/upload`
})

const uploadHeaders = computed(() => {
  const token = localStorage.getItem('token')
  return token ? { token } : {}
})

const uploadData = computed(() => ({
  bizType: 'sales_order_confirmation',
  bizId: props.order?.orderId ?? 0,
}))

function downloadUrl(id: number): string {
  return attachmentApi.downloadUrl(id)
}

async function loadAttachments() {
  if (!props.order?.orderId) return
  try {
    const res: any = await attachmentApi.list('sales_order_confirmation', props.order.orderId)
    attachments.value = Array.isArray(res?.data) ? res.data : []
  } catch {
    attachments.value = []
  }
}

function onUploadSuccess(response: any) {
  if (response?.code === 200 && response?.data) {
    ElMessage.success('上传成功')
    loadAttachments()
  } else {
    ElMessage.warning(response?.msg || '上传响应异常')
  }
}

function onUploadError() {
  ElMessage.error('上传失败')
}

async function onDelete(att: any) {
  try {
    await ElMessageBox.confirm(`确认删除凭证「${att.fileName}」？`, '删除确认', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await attachmentApi.remove(att.id)
    ElMessage.success('删除成功')
    loadAttachments()
  } catch {
    // 取消删除
  }
}

function formatSize(bytes?: number): string {
  if (bytes == null) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

// 打开时加载已上传凭证
watch(
  () => props.visible,
  (v) => {
    if (v) {
      loadAttachments()
    }
  },
)

const fmt = (v?: number | string | null): string => {
  if (v === null || v === undefined || v === '') return ''
  const n = Number(v)
  return Number.isNaN(n) ? String(v) : n.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
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

/* 2026-08-11 内嵌凭证上传区样式 */
.att-list {
  margin-top: 4px;
}

.att-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 8px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  margin-bottom: 4px;
}

.att-info {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.att-size {
  font-size: 12px;
  color: #909399;
}
</style>
