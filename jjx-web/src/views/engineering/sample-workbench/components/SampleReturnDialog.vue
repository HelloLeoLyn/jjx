<template>
  <el-dialog
    v-model="visible"
    :title="`打样退料 - ${sampleOrderNo}`"
    width="1060px"
    append-to-body
    :close-on-click-modal="!submitting"
    :close-on-press-escape="!submitting"
    :show-close="!submitting"
  >
    <el-alert
      title="仅显示已完成发料的物料；退料回到原发料批次与库位。不同仓库请分次退料。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-table
      v-loading="loading"
      :data="rows"
      border
      style="margin-top: 12px"
      empty-text="暂无可退明细：需先完成发料（出库单状态=已完成）后才能退料"
    >
      <el-table-column prop="materialCode" label="物料编码" width="130" />
      <el-table-column prop="materialName" label="物料名称" min-width="140" />
      <el-table-column prop="batchNo" label="原发料批次" min-width="140" />
      <el-table-column prop="warehouseId" label="仓库ID" width="80" />
      <el-table-column prop="locationId" label="库位ID" width="80" />
      <el-table-column prop="issuedQty" label="已发" width="80" />
      <el-table-column prop="returnedQty" label="已退" width="80" />
      <el-table-column prop="returnableQty" label="可退" width="80" />
      <el-table-column label="本次退料" width="175">
        <template #default="{ row }">
          <el-input-number
            v-model="row.quantity"
            :min="0"
            :max="Number(row.returnableQty)"
            :precision="4"
            :step="1"
            :disabled="submitting || Number(row.returnableQty) <= 0"
            controls-position="right"
          />
        </template>
      </el-table-column>
    </el-table>
    <el-input
      v-model="reason"
      placeholder="退料原因（选填）"
      maxlength="200"
      style="margin-top: 12px"
      :disabled="submitting"
    />
    <template #footer>
      <el-button :disabled="submitting" @click="visible = false">关闭</el-button>
      <el-button :disabled="loading || submitting" @click="loadPreview">刷新可退量</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="loading || !hasQuantity"
        @click="submitReturn"
        >退料入库</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { inboundApi, type SampleReturnPreviewRow } from '@/api/inventory/inbound'

const props = defineProps<{
  sampleOrderId: string | number
  sampleOrderNo?: string
}>()
const visible = defineModel<boolean>({ default: false })
const emit = defineEmits<{ success: [] }>()
const loading = ref(false)
const submitting = ref(false)
const reason = ref('')
const rows = ref<(SampleReturnPreviewRow & { quantity: number })[]>([])
const hasQuantity = computed(() => rows.value.some((row) => Number(row.quantity) > 0))
let previewVersion = 0

async function loadPreview() {
  const version = ++previewVersion
  rows.value = []
  loading.value = true
  try {
    const response = await inboundApi.sampleReturnPreview(props.sampleOrderId)
    if (version === previewVersion) {
      rows.value = (response.data || []).map((row) => ({ ...row, quantity: 0 }))
    }
  } catch (error: any) {
    if (version === previewVersion) ElMessage.error(error?.message || '加载打样退料预览失败')
  } finally {
    if (version === previewVersion) loading.value = false
  }
}

async function submitReturn() {
  if (submitting.value || loading.value) return
  const items = rows.value.filter((row) => Number(row.quantity) > 0)
  if (!items.length) return
  if (items.some((row) => Number(row.quantity) > Number(row.returnableQty))) {
    ElMessage.warning('本次退料量不能超过可退量')
    return
  }
  if (new Set(items.map((row) => String(row.warehouseId))).size > 1) {
    ElMessage.warning('不同仓库的物料请分次退料')
    return
  }
  submitting.value = true
  try {
    await inboundApi.sampleReturn(
      props.sampleOrderId,
      items.map((row) => ({ returnKey: row.returnKey, quantity: Number(row.quantity) })),
      reason.value
    )
    ElMessage.success('打样退料已入库')
    emit('success')
    reason.value = ''
    await loadPreview()
  } catch (error: any) {
    ElMessage.error(error?.message || '打样退料失败，请刷新可退量后重试')
  } finally {
    submitting.value = false
  }
}

watch(
  () => [visible.value, props.sampleOrderId] as const,
  ([open]) => {
    if (open) {
      reason.value = ''
      void loadPreview()
    } else {
      ++previewVersion
      rows.value = []
      loading.value = false
    }
  },
  { immediate: true }
)
</script>
