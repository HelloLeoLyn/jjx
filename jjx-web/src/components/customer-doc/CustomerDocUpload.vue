<template>
  <el-dialog v-model="visible" title="上传客供资料" width="1050px" append-to-body destroy-on-close>
    <el-alert v-if="!bizId" title="确认后会暂存在本页，保存单据后自动上传。" type="info" :closable="false" style="margin-bottom: 12px" />
    <el-alert v-if="!uniqueProducts.length" title="请先在单据中填写产品" type="warning" :closable="false" />
    <el-table :data="uniqueProducts" border size="small" empty-text="暂无产品">
      <el-table-column label="产品" width="190">
        <template #default="{ row: product }">{{ product.productCode }} {{ product.productName || '' }}</template>
      </el-table-column>
      <el-table-column label="客供资料">
        <template #default="{ row: product }">
          <el-upload drag multiple :auto-upload="false" :show-file-list="false" :on-change="file => addFile(file, product.productCode)">
            <div>拖拽多个文件到此产品，或点击选择</div>
          </el-upload>
          <div v-for="(fileRow, index) in productRows(product.productCode)" :key="fileRow.key" class="file-row">
            <el-select v-model="fileRow.docType" placeholder="资料类型" :disabled="fileRow.status === 'success'" style="width: 140px">
              <el-option v-for="type in docTypes" :key="type.value" :label="type.label" :value="type.value" />
            </el-select>
            <span class="file-format">{{ extension(fileRow.file.name).toUpperCase() }}</span>
            <span class="file-name" :title="fileRow.file.name">{{ fileRow.file.name }}</span>
            <span class="file-name" :title="fileRow.displayName || plannedName(fileRow, index)">{{ fileRow.displayName || plannedName(fileRow, index) }}</span>
            <el-tag :type="fileRow.status === 'success' ? 'success' : fileRow.status === 'failed' ? 'danger' : 'info'" size="small">
              {{ fileRow.status === 'success' ? '已上传' : fileRow.status === 'failed' ? '失败，可重试' : '待确认' }}
            </el-tag>
            <el-button v-if="fileRow.previewUrl" link type="primary" @click="previewUrl = fileRow.previewUrl">预览</el-button>
            <el-button v-if="fileRow.status !== 'success'" link type="danger" @click="removeRow(fileRow)">移除</el-button>
            <span v-if="fileRow.error" class="row-error">{{ fileRow.error }}</span>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-image-viewer v-if="previewUrl" :url-list="[previewUrl]" @close="previewUrl = ''" />
    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button type="primary" :loading="uploading" :disabled="!rows.some(row => row.status !== 'success')" @click="confirmUpload">确认上传</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { attachmentApi } from '@/api/system/attachment'

interface ProductOption { productCode: string; productName?: string }
interface PendingRow {
  key: number
  file: File
  productCode: string
  docType: string
  previewUrl: string
  displayName: string
  status: 'pending' | 'success' | 'failed'
  error: string
}
const props = defineProps<{
  modelValue: boolean
  bizType: 'inquiry' | 'quotation' | 'sales_order' | 'sample'
  bizId?: number | null
  sourceNo?: string
  traceId?: string
  products: ProductOption[]
}>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; success: [id: number] }>()
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })
const docTypes = [
  { value: 'customer_drawing', label: '客户图纸' }, { value: 'specification', label: '技术规格书' },
  { value: 'approval_sheet', label: '承认书' }, { value: 'inspection_standard', label: '检验标准' },
  { value: 'change_notice', label: '变更通知' }, { value: 'golden_sample', label: '标准样品资料' },
  { value: 'packaging_spec', label: '包装规范' }, { value: 'compliance_report', label: '认证报告' },
  { value: 'other', label: '其他资料' },
] as const
const uniqueProducts = computed(() => Array.from(new Map(
  props.products.filter(product => product.productCode).map(product => [product.productCode, product]),
).values()))
const rows = ref<PendingRow[]>([])
const uploading = ref(false)
const previewUrl = ref('')
const confirmedPending = ref(false)
let nextKey = 0
const allowed = new Set(['pdf', 'dwg', 'dxf', 'jpg', 'jpeg', 'png', 'gif', 'webp', 'doc', 'docx', 'xls', 'xlsx', 'zip'])
function extension(name: string) { return name.includes('.') ? name.split('.').pop()?.toLowerCase() || '' : '' }
function productRows(productCode: string) { return rows.value.filter(row => row.productCode === productCode) }
function addFile(upload: UploadFile, productCode: string) {
  const file = upload.raw
  if (!file) return
  const ext = extension(file.name)
  if (!allowed.has(ext) || file.size > 10 * 1024 * 1024) {
    ElMessage.warning(`文件 ${file.name} 格式不支持或超过 10MB`)
    return
  }
  rows.value.push({ key: ++nextKey, file, productCode,
    docType: '', previewUrl: file.type.startsWith('image/') ? URL.createObjectURL(file) : '',
    displayName: '', status: 'pending', error: '' })
}
function plannedName(row: PendingRow, index: number) {
  const type = docTypes.find(item => item.value === row.docType)?.label || '待选类型'
  const sequence = String(rows.value.filter(item => item.productCode === row.productCode && item.docType === row.docType && item.key <= row.key).length).padStart(2, '0')
  return `${props.sourceNo || '待保存单号'}-${row.productCode}-${type}-预计${sequence}.${extension(row.file.name)}`
}
function removeRow(row: PendingRow) {
  if (row.previewUrl) URL.revokeObjectURL(row.previewUrl)
  rows.value = rows.value.filter(item => item.key !== row.key)
}
async function confirmUpload() {
  const pending = rows.value.filter(row => row.status !== 'success')
  if (pending.some(row => !row.productCode || !row.docType)) return ElMessage.warning('请逐行选择产品和资料类型')
  if (!props.bizId) {
    confirmedPending.value = true
    visible.value = false
    ElMessage.info('已确认待上传，保存单据后自动上传')
    return
  }
  await uploadRows(props.bizId, props.traceId)
}
async function uploadRows(bizId: number, traceId?: string) {
  const pending = rows.value.filter(row => row.status !== 'success')
  if (!pending.length) return true
  uploading.value = true
  try {
    for (const row of pending) {
      try {
        const response: any = await attachmentApi.uploadCustomerDoc(row.file, props.bizType, bizId,
          row.productCode, row.docType, traceId)
        row.displayName = response.data?.displayName || ''
        row.status = 'success'
        row.error = ''
        emit('success', Number(response.data?.id))
      } catch (error: any) {
        row.status = 'failed'
        row.error = error?.message || '上传失败'
      }
    }
    if (rows.value.every(row => row.status === 'success')) {
      confirmedPending.value = false
      ElMessage.success('客供资料已上传')
    } else {
      visible.value = true
    }
    return rows.value.every(row => row.status === 'success')
  } finally { uploading.value = false }
}
async function flushPending(bizId: number, traceId?: string) {
  return confirmedPending.value ? uploadRows(bizId, traceId) : true
}
function clearRows() { rows.value.forEach(row => { if (row.previewUrl) URL.revokeObjectURL(row.previewUrl) }); rows.value = [] }
function reset() { confirmedPending.value = false; clearRows() }
watch(() => props.modelValue, value => { if (!value && !uploading.value) { previewUrl.value = ''; if (!confirmedPending.value) clearRows() } })
onBeforeUnmount(clearRows)
defineExpose({ flushPending, reset })
</script>

<style scoped>
.row-error { color: #f56c6c; font-size: 11px; line-height: 1.2; }
.file-row { display: flex; align-items: center; gap: 8px; margin-top: 8px; flex-wrap: wrap; }
.file-format { width: 48px; color: #606266; }
.file-name { min-width: 120px; max-width: 180px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
</style>
