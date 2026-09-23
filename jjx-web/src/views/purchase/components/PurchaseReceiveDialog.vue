<template>
  <el-dialog
    :title="title"
    :model-value="props.visible"
    width="1200px"
    append-to-body
    :close-on-click-modal="false"
    @close="handleClose"
    @update:model-value="(val: boolean) => emit('update:visible', val)"
  >
    <el-form label-width="100px" v-loading="loading">
      <el-form-item label="订单号">
        <el-input :model-value="orderNo" disabled />
      </el-form-item>

      <div class="detail-head">
        <el-divider content-position="left">收货明细</el-divider>
        <div class="quick-actions">
          <el-button link type="primary" @click="fillAll">全部填满</el-button>
          <el-button link @click="clearAll">全部清空</el-button>
        </div>
      </div>

      <el-table :data="form.items" border style="width: 100%; margin-bottom: 8px">
        <el-table-column label="物料编码" prop="materialCode" width="160" />
        <el-table-column label="物料名称" prop="materialName" min-width="160" />
        <el-table-column label="规格型号" prop="materialSpec" width="120" />
        <el-table-column label="单位" prop="unit" width="60" />
        <el-table-column label="订购数量" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.quantity) }}</template>
        </el-table-column>
        <!-- 修②：已收数量只读展示，不再与「本次收货」共用同一字段 -->
        <el-table-column label="已收数量" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.receivedQuantity) }}</template>
        </el-table-column>
        <el-table-column label="未收" width="90" align="right">
          <template #default="{ row }">{{ fmt(remainOf(row)) }}</template>
        </el-table-column>
        <!-- ①②：默认填满「未收」；超出不夹断而是标红并拦截提交 -->
        <el-table-column label="本次收货" width="180">
          <template #default="{ row, $index }">
            <div class="qty-cell" :class="{ 'is-over': overOf(row) > 0 }">
              <el-input-number
                :ref="(el: any) => setQtyRef(el, $index)"
                v-model="row.receiveQuantity"
                :min="0"
                :precision="2"
                :step="1"
                controls-position="right"
                size="small"
                style="width: 100%"
                @keyup.enter="focusNext($index)"
              />
              <div v-if="overOf(row) > 0" class="qty-err">超出未收 {{ fmt(overOf(row)) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="fillRow(row)">填满</el-button>
            <el-button link size="small" @click="clearRow(row)">清零</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="detail-summary">
        <span>可收合计 <b>{{ fmt(totalRemain) }}</b></span>
        <span>本次合计 <b :class="{ 'is-err': totalOver > 0 }">{{ fmt(totalInput) }}</b></span>
        <span v-if="totalOver > 0" class="is-err">超出 <b>{{ fmt(totalOver) }}</b>，请调整后再提交</span>
        <span v-else>提交后剩余 <b>{{ fmt(totalRemain - totalInput) }}</b></span>
      </div>

      <el-divider content-position="left">收货票据</el-divider>

      <el-form-item label="上传票据">
        <template v-if="canUploadDoc">
          <el-upload
            ref="uploadRef"
            :auto-upload="true"
            list-type="picture-card"
            :file-list="imageList"
            :http-request="handleImageUpload"
            :on-preview="handleImagePreview"
            :on-remove="handleImageRemove"
            :before-upload="beforeImageUpload"
            accept="image/jpeg,image/png,image/gif,image/bmp,image/webp"
            multiple
          >
            <el-icon><Plus /></el-icon>
            <template #tip>
              <div class="el-upload__tip">支持 JPG/PNG/GIF/BMP/WebP，单张不超过 10MB；确认收货时随票单一并入库</div>
            </template>
          </el-upload>
        </template>
        <template v-else>
          <el-tag v-for="f in imageList" :key="f.url" class="doc-tag" type="info">{{ f.name }}</el-tag>
          <span v-if="!imageList.length" class="doc-tip">无票据上传权限（purchase:receipt:doc:add）</span>
          <span v-else class="doc-tip">只读：无票据上传权限</span>
        </template>
      </el-form-item>

      <!-- 收货结果：把「生成了哪张入库单」明确告诉用户，并引到 IQC（检验在质量域，不在这里录） -->
      <el-alert
        v-if="lastInboundNo"
        class="result-alert"
        type="success"
        :closable="false"
        show-icon
        :title="`收货成功，已生成入库单 ${lastInboundNo}（待来料检验）`"
      >
        <template #default>
          <span class="doc-tip">下一步：由质量部门在「质量管理 → 来料检验」录入并判定（库存未增加，仓库确认入库后才可用）。</span>
          <el-button v-if="canGotoIqc" link type="primary" @click="gotoIqc">去来料检验</el-button>
        </template>
      </el-alert>

      <el-image-viewer v-if="previewVisible" :url-list="[previewUrl]" @close="previewVisible = false" />
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">关 闭</el-button>
        <el-tooltip :disabled="!hasOver" content="有明细超出未收数量，请先调整" placement="top">
          <span>
            <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="handleSubmit">
              确认收货{{ totalInput > 0 ? `（${fmt(totalInput)}）` : '' }}
            </el-button>
          </span>
        </el-tooltip>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import type { UploadProps, UploadUserFile } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import {
  getReceiptsByOrder,
  confirmBatchReceive,
  getReceiptInboundOrders,
  uploadReceiptDocTemp,
  getReceiptDocDiskFiles,
  deleteReceiptDocTemp,
  confirmReceiptDocs,
} from '@/api/purchase/receipt'

/**
 * 采购收货弹窗（共享组件 · dev-20260923-004 方案 A + dev-20260923-005 收货小优化）
 *
 * 采购订单页与采购收货页共用同一实现：
 *  - 提交走**收货域**端点 `POST /purchase/receipt/confirm-batch`（`purchase:receipt:add`）；
 *  - 明细来自 `GET /purchase/receipt/order/{orderId}`（`purchase:receipt:view`）；
 *  - 已收数量只读、本次收货独立字段（缺陷②）；票据随提交落库（缺陷④）；
 *  - 检验不在本弹窗录入：收货生成的入库单进入 质量管理 → 来料检验（IQC），这里只给入库单号并可跳转。
 *
 * 收货小优化（2026-09-23 用户拍板）：
 *  ① 打开即**默认填满**每行「未收」数量；
 *  ② 超出未收**不夹断**，改为红框 + 红字 + 行内差额提示，并禁用提交（后端超收校验仍在，防绕过）；
 *  ④ 顶部「全部填满 / 全部清空」；⑤ 行内「填满 / 清零」；
 *  ⑥ 提交按钮带合计 + 二次确认；⑦ 回车跳下一行；⑧ 出现超量时自动把该行滚入视野。
 */
interface ReceiveItem {
  itemId: number
  materialCode: string
  materialName: string
  materialSpec?: string
  unit?: string
  quantity: number
  receivedQuantity: number
  receiveQuantity: number | undefined
}

interface DiskFileInfo {
  fileName: string
  storageName?: string
  fileUrl: string
  fileSize: number
  orderNo?: string
}

const props = defineProps<{
  visible: boolean
  orderId?: number
  orderNo: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success', payload?: { inboundNo?: string }): void
  (e: 'goto-iqc', inboundNo: string): void
}>()

const userStore = useUserStore()
const canUploadDoc = computed(() => userStore.hasPermission('purchase:receipt:doc:add'))
const canViewDoc = computed(() => userStore.hasPermission('purchase:receipt:doc:view'))
/** 来料检验在质量域，跳转按钮只在有 IQC 读权限时出现（否则点进去是 403） */
const canGotoIqc = computed(() => userStore.hasPermission('quality:lot:view'))

const uploadRef = ref()
const loading = ref(false)
const submitting = ref(false)
const lastInboundNo = ref('')

const form = reactive({ items: [] as ReceiveItem[] })

const imageList = ref<UploadUserFile[]>([])
const previewVisible = ref(false)
const previewUrl = ref('')

const title = computed(() => `收货 - ${props.orderNo}`)

function fmt(v?: number | string | null): string {
  const n = Number(v ?? 0)
  if (!Number.isFinite(n)) return '0'
  return String(Math.round(n * 100) / 100)
}

function remainOf(row: ReceiveItem): number {
  return Math.round((Number(row.quantity || 0) - Number(row.receivedQuantity || 0)) * 100) / 100
}

/** 超出量（>0 即超收） */
function overOf(row: ReceiveItem): number {
  return Math.round((Number(row.receiveQuantity || 0) - remainOf(row)) * 100) / 100
}

const totalRemain = computed(() => round2(form.items.reduce((s, it) => s + Math.max(0, remainOf(it)), 0)))
const totalInput = computed(() => round2(form.items.reduce((s, it) => s + Number(it.receiveQuantity || 0), 0)))
const totalOver = computed(() => round2(form.items.reduce((s, it) => s + Math.max(0, overOf(it)), 0)))
const hasOver = computed(() => totalOver.value > 0)
const filledCount = computed(() => form.items.filter((it) => Number(it.receiveQuantity || 0) > 0).length)
const canSubmit = computed(() => !submitting.value && filledCount.value > 0 && !hasOver.value)

/** ⑧ 第一个超量行下标（-1=没有），用于自动滚入视野 */
const firstOverIndex = computed(() => form.items.findIndex((it) => overOf(it) > 0))

// ⑦ 输入框引用（回车跳下一行）
const qtyRefs = ref<any[]>([])
function setQtyRef(el: any, index: number) {
  qtyRefs.value[index] = el
}
function focusNext(index: number) {
  const next = qtyRefs.value[index + 1]
  next?.focus?.()
}
function round2(v: number): number {
  return Math.round(v * 100) / 100
}

function fillRow(row: ReceiveItem) {
  row.receiveQuantity = Math.max(0, remainOf(row))
}
function clearRow(row: ReceiveItem) {
  row.receiveQuantity = 0
}
function fillAll() {
  form.items.forEach((it) => fillRow(it))
}
function clearAll() {
  form.items.forEach((it) => clearRow(it))
}

watch(
  () => props.visible,
  async (val) => {
    if (val) {
      await loadItems()
      await loadImages()
    }
  }
)

const loadItems = async () => {
  if (!props.orderId) return
  loading.value = true
  lastInboundNo.value = ''
  qtyRefs.value = []
  try {
    const response: any = await getReceiptsByOrder(Number(props.orderId))
    const items = response.data || []
    form.items = items.map((item: any) => {
      const quantity = Number(item.quantity || 0)
      const receivedQuantity = Number(item.receivedQuantity || 0)
      const remain = Math.round((quantity - receivedQuantity) * 100) / 100
      return {
        itemId: Number(item.itemId),
        materialCode: item.materialCode,
        materialName: item.materialName,
        materialSpec: item.materialSpec || '',
        unit: item.unit,
        quantity,
        receivedQuantity,
        // ① 默认填满未收（未收<=0 的行保持 0，不需要收）
        receiveQuantity: remain > 0 ? remain : 0,
      }
    })
  } catch (error) {
    console.error('加载收货明细失败:', error)
    ElMessage.error('加载收货明细失败')
  } finally {
    loading.value = false
  }
}

/** 磁盘上的票据（上传即落盘，确认收货时入库） */
const loadImages = async () => {
  if (!props.orderId || !canViewDoc.value) {
    imageList.value = []
    return
  }
  try {
    const response: any = await getReceiptDocDiskFiles(Number(props.orderId))
    const files: DiskFileInfo[] = response.data || []
    imageList.value = files.map((file, index) => ({
      name: file.storageName || file.fileName,
      url: file.fileUrl,
      uid: index + 1,
    }))
  } catch (error) {
    console.error('加载收货票据失败:', error)
  }
}

const beforeImageUpload: UploadProps['beforeUpload'] = (file) => {
  const isImage = ['image/jpeg', 'image/png', 'image/gif', 'image/bmp', 'image/webp'].includes(file.type)
  if (!isImage) {
    ElMessage.error('仅支持上传 JPG/PNG/GIF/BMP/WebP 格式的图片')
    return false
  }
  if (file.size / 1024 / 1024 >= 10) {
    ElMessage.error('图片大小不能超过 10MB')
    return false
  }
  return true
}

const handleImageUpload = async (options: any) => {
  const { file, onSuccess, onError } = options
  try {
    const response = await uploadReceiptDocTemp(Number(props.orderId), file)
    onSuccess(response, file)
    ElMessage.success('票据上传成功')
    await loadImages()
  } catch (error) {
    console.error('票据上传失败:', error)
    onError(error)
    ElMessage.error('票据上传失败')
  }
}

const handleImagePreview: UploadProps['onPreview'] = (file) => {
  previewUrl.value = file.url || ''
  previewVisible.value = true
}

const handleImageRemove: UploadProps['onRemove'] = async (file) => {
  try {
    const fileItem = imageList.value.find((item) => item.uid === file.uid)
    if (fileItem?.url) {
      await deleteReceiptDocTemp(fileItem.url)
      ElMessage.success('票据已删除')
    }
  } catch (error) {
    console.error('票据删除失败:', error)
    ElMessage.error('票据删除失败')
  }
}

/** ⑧ 一旦出现超量行，把它滚入视野（nearest：尽量少跳动） */
watch(firstOverIndex, async (idx) => {
  if (idx < 0) return
  await nextTick()
  const el = qtyRefs.value[idx]?.$el || qtyRefs.value[idx]
  el?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' })
})

const handleSubmit = async () => {
  if (!props.orderId || !canSubmit.value) return
  const receiveItems = form.items
    .filter((item) => Number(item.receiveQuantity || 0) > 0)
    .map((item) => ({
      itemId: Number(item.itemId),
      receivedQuantity: Math.round(Number(item.receiveQuantity) * 100) / 100,
    }))
  if (!receiveItems.length) {
    ElMessage.warning('请至少输入一个物料的收货数量')
    return
  }
  // ⑥ 二次确认（默认已填满，避免误提交）
  try {
    await ElMessageBox.confirm(
      `确认收货？本次合计 ${fmt(totalInput.value)}，涉及 ${receiveItems.length} 行明细（订单 ${props.orderNo}）。收货后自动生成待确认入库单。`,
      '收货确认',
      { type: 'warning', confirmButtonText: '确认收货', cancelButtonText: '再改改' }
    )
  } catch {
    return
  }
  submitting.value = true
  try {
    await confirmBatchReceive(Number(props.orderId), receiveItems)
    // 缺陷④修复：确认收货后把临时票据落库（原来只上传不登记）
    if (canUploadDoc.value) {
      try {
        const disk: any = await getReceiptDocDiskFiles(Number(props.orderId))
        const files: DiskFileInfo[] = disk.data || []
        if (files.length) {
          await confirmReceiptDocs(
            Number(props.orderId),
            files.map((f) => ({ fileName: f.fileName, fileUrl: f.fileUrl, fileSize: Number(f.fileSize || 0) }))
          )
        }
      } catch (docError) {
        // 票据落库失败不回滚收货（收货是主业务，票据可后续补登）
        console.error('收货票据登记失败:', docError)
        ElMessage.warning('收货成功，但票据登记失败，请稍后在采购发票/收货页补登记')
      }
    }
    // 把「收成了哪张入库单」回给用户（入库单号 = 后续来料检验的凭据）
    let inboundNo = ''
    try {
      const inbound: any = await getReceiptInboundOrders(Number(props.orderId))
      const list = inbound.data || []
      inboundNo = list.length ? String(list[0].inboundNo || '') : ''
    } catch (e) {
      console.error('查询收货入库单失败:', e)
    }
    lastInboundNo.value = inboundNo
    ElMessage.success(inboundNo ? `收货成功，已生成入库单 ${inboundNo}（待来料检验）` : '收货成功')
    await loadItems()
    await loadImages()
    emit('success', { inboundNo })
  } catch (error: any) {
    console.error('收货失败:', error)
    ElMessage.error(error?.message || '收货失败')
  } finally {
    submitting.value = false
  }
}

const gotoIqc = () => {
  if (!lastInboundNo.value) return
  emit('goto-iqc', lastInboundNo.value)
}

const handleClose = () => {
  form.items = []
  imageList.value = []
  lastInboundNo.value = ''
  qtyRefs.value = []
  emit('update:visible', false)
}
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.detail-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.detail-head :deep(.el-divider--horizontal) {
  flex: 1;
  margin: 16px 0;
}
.quick-actions {
  white-space: nowrap;
}
.qty-cell.is-over :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #f56c6c inset;
}
.qty-cell.is-over :deep(.el-input__inner) {
  color: #f56c6c;
}
.qty-err {
  margin-top: 2px;
  font-size: 12px;
  line-height: 16px;
  color: #f56c6c;
}
.detail-summary {
  display: flex;
  gap: 20px;
  margin: 0 0 8px;
  font-size: 13px;
  color: #606266;
}
.detail-summary b {
  color: #303133;
}
.detail-summary .is-err,
.detail-summary b.is-err {
  color: #f56c6c;
}
.result-alert {
  margin-top: 8px;
}
.doc-tip {
  color: #909399;
  font-size: 12px;
}
.doc-tag {
  margin-right: 6px;
}
</style>
