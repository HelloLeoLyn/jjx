<template>
  <el-dialog :model-value="modelValue" :title="`快捷盘点 · ${materialName}`" width="1080px" destroy-on-close :close-on-click-modal="false" :before-close="beforeClose" @closed="clearSelection">
    <el-alert title="按批次原记账单位录入实盘数量；保存后待确认过账，生效前不会改变库存。" type="info" :closable="false" />
    <el-tabs v-model="tab">
      <el-tab-pane label="选择批次盘点" name="batches">
        <el-table v-loading="loading" :data="batches" highlight-current-row row-key="stock.itemId" @current-change="selectBatch">
          <el-table-column label="仓库" prop="warehouseName" width="120" />
          <el-table-column label="库位" prop="locationName" min-width="160" />
          <el-table-column label="批次" prop="stock.batchNo" min-width="160" />
          <el-table-column label="卷号" prop="stock.rollNo" width="130" />
          <el-table-column label="账面数量" min-width="130"><template #default="{ row }">{{ row.stock.quantity }} {{ row.unit }}</template></el-table-column>
          <el-table-column label="预留数量" prop="stock.reservedQuantity" width="110" />
        </el-table>
        <Pagination :total="batchTotal" v-model:page="batchPage" v-model:limit="batchSize" @pagination="loadBatches" />
        <el-form v-if="selected" label-width="120px" style="margin-top: 20px" @submit.prevent>
          <el-form-item label="当前批次">{{ selected.stock.batchNo }} · {{ selected.locationName }}</el-form-item>
          <el-form-item label="记账实盘数量" required>
            <el-input-number v-model="actual" :min="0" :max="99999999.9999" :precision="4" :controls="false" :disabled="saving || posting" />
            <span style="margin-left: 8px">{{ selected.unit }}</span>
          </el-form-item>
          <el-form-item label="预计差异">{{ previewLoading ? '核对中…' : preview ? `${preview.diffQuantity} ${preview.unit}` : '—' }}</el-form-item>
          <el-form-item label="盘点原因" required><el-input v-model="reason" type="textarea" maxlength="300" show-word-limit :disabled="saving || posting" /></el-form-item>
          <el-form-item><el-button v-if="canAdd" type="primary" :loading="saving" :disabled="posting" @click="save">保存盘点</el-button></el-form-item>
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="盘点记录与确认" name="records">
        <el-table v-loading="recordLoading" :data="records">
          <el-table-column label="盘点凭证" prop="stocktakeNo" min-width="220" show-overflow-tooltip />
          <el-table-column label="库存行" prop="stockItemId" width="90" />
          <el-table-column label="账面" prop="systemQuantity" width="90" />
          <el-table-column label="实盘" prop="actualQuantity" width="90" />
          <el-table-column label="差异" prop="diffQuantity" width="90" />
          <el-table-column label="单位" prop="unit" width="70" />
          <el-table-column label="原因" prop="reason" min-width="150" show-overflow-tooltip />
          <el-table-column label="状态" width="130"><template #default="{ row }"><el-tag :type="QuickStocktakeStatusEnum.getTagProps(row.orderStatus).type">{{ QuickStocktakeStatusEnum.getLabel(row.orderStatus) }}</el-tag></template></el-table-column>
          <el-table-column label="操作" width="190"><template #default="{ row }">
            <el-button v-if="canApprove && row.orderStatus === QuickStocktakeStatusEnum.CONFIRMED.value" link type="primary" :disabled="posting" @click="confirm(row)">确认过账</el-button>
            <el-button v-if="canEdit && row.orderStatus === QuickStocktakeStatusEnum.CONFIRMED.value" link type="danger" :disabled="posting" @click="cancel(row)">作废</el-button>
            <el-button v-if="canViewTransactions && row.orderStatus === QuickStocktakeStatusEnum.PROCESSED.value" link type="primary" @click="viewTransactions(row)">收发明细</el-button>
          </template></el-table-column>
        </el-table>
        <Pagination :total="recordTotal" v-model:page="recordPage" v-model:limit="recordSize" @pagination="loadRecords" />
      </el-tab-pane>
    </el-tabs>
    <template #footer><el-button :disabled="saving || posting" @click="$emit('update:modelValue', false)">关闭</el-button><el-button :disabled="saving || posting" @click="refresh">刷新批次与记录</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, ref, watch, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import Pagination from '@/components/Pagination/index.vue'
import { quickStocktakeApi, type QuickStocktakeBatch, type QuickStocktakeReceipt } from '@/api/inventory/stocktake'
import { QuickStocktakeStatusEnum } from '@/enums/inventory/StocktakeEnum'
import { usePermission } from '@/composables/usePermission'
const props = defineProps<{ modelValue: boolean; inventoryItemId: string; materialName: string; warehouseId?: string | number; locationId?: string | number }>()
const emit = defineEmits<{ 'update:modelValue': [boolean]; changed: [] }>()
const { hasPermission } = usePermission()
const canAdd = computed(() => hasPermission('inventory:stocktake:add'))
const canEdit = computed(() => hasPermission('inventory:stocktake:edit'))
const canApprove = computed(() => hasPermission('inventory:stocktake:approve'))
const canViewTransactions = computed(() => hasPermission('inventory:transaction:view'))
const router = useRouter()
const tab = ref('batches'), batches = ref<QuickStocktakeBatch[]>([]), records = ref<QuickStocktakeReceipt[]>([])
const batchPage = ref(1), batchSize = ref(10), batchTotal = ref(0), recordPage = ref(1), recordSize = ref(10), recordTotal = ref(0)
const loading = ref(false), recordLoading = ref(false), saving = ref(false), posting = ref(false)
const selected = ref<QuickStocktakeBatch>(), actual = ref<number>(), reason = ref('')
const preview = ref<{ diffQuantity: number; unit: string }>()
const previewLoading = ref(false)
let previewVersion = 0
let previewTimer: ReturnType<typeof setTimeout> | undefined
let requestId = '', batchVersion = 0, recordVersion = 0
function newRequestId() {
  const bytes = crypto.getRandomValues(new Uint8Array(16)); bytes[6] = (bytes[6] & 15) | 64; bytes[8] = (bytes[8] & 63) | 128
  const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}
watch(() => [selected.value?.fingerprint, actual.value, props.modelValue], () => {
  const version = ++previewVersion
  if (previewTimer) clearTimeout(previewTimer)
  preview.value = undefined; previewLoading.value = false
  const row = selected.value, quantity = actual.value
  if (!props.modelValue || !row || quantity == null || !Number.isFinite(quantity)) return
  previewLoading.value = true
  previewTimer = setTimeout(async () => {
    try { const res = await quickStocktakeApi.preview({ inventoryItemId: props.inventoryItemId, stockItemId: row.stock.itemId, fingerprint: row.fingerprint, actualQuantity: quantity })
      if (version === previewVersion) preview.value = res.data || undefined }
    catch (error) { console.error(error) }
    finally { if (version === previewVersion) previewLoading.value = false }
  }, 350)
})
onBeforeUnmount(() => { ++batchVersion; ++recordVersion; ++previewVersion; if (previewTimer) clearTimeout(previewTimer) })
function clearSelection() { selected.value = undefined; actual.value = undefined; reason.value = ''; requestId = '' }
function selectBatch(row?: QuickStocktakeBatch | null) {
  if (saving.value || posting.value) return
  clearSelection()
  if (row) { selected.value = row; requestId = newRequestId() }
}
async function loadBatches() {
  const version = ++batchVersion
  clearSelection(); loading.value = true
  try { const res = await quickStocktakeApi.batches({ inventoryItemId: props.inventoryItemId, pageNum: batchPage.value, pageSize: batchSize.value, warehouseId: props.warehouseId, locationId: props.locationId })
    if (version === batchVersion && props.modelValue) { batches.value = res.data?.records || []; batchTotal.value = res.data?.total || 0 } }
  catch (error) { if (version === batchVersion) { batches.value = []; batchTotal.value = 0 }; console.error(error) }
  finally { if (version === batchVersion) loading.value = false }
}
async function loadRecords() {
  const version = ++recordVersion
  recordLoading.value = true
  try { const res = await quickStocktakeApi.records({ inventoryItemId: props.inventoryItemId, pageNum: recordPage.value, pageSize: recordSize.value })
    if (version === recordVersion && props.modelValue) { records.value = res.data?.records || []; recordTotal.value = res.data?.total || 0 } }
  catch (error) { if (version === recordVersion) { records.value = []; recordTotal.value = 0 }; console.error(error) }
  finally { if (version === recordVersion) recordLoading.value = false }
}
async function refresh() { await Promise.all([loadBatches(), loadRecords()]) }
async function save() {
  const row = selected.value
  if (saving.value || posting.value || !canAdd.value || !row) return
  if (actual.value == null || !Number.isFinite(actual.value) || !reason.value.trim()) { ElMessage.warning('请填写实盘数量和盘点原因'); return }
  saving.value = true
  try {
    const result = await quickStocktakeApi.save({ requestId, inventoryItemId: props.inventoryItemId, stockItemId: row.stock.itemId, fingerprint: row.fingerprint, actualQuantity: actual.value, reason: reason.value.trim() })
    ElMessage.success(`盘点已保存，差异 ${result.data?.diffQuantity} ${row.unit}，待确认过账`)
    tab.value = 'records'; recordPage.value = 1; clearSelection(); await loadRecords()
  } catch (error) { console.error(error) }
  finally { saving.value = false }
}
async function confirm(row: QuickStocktakeReceipt) {
  if (posting.value || !canApprove.value) return
  try { await ElMessageBox.confirm(`确认盘点凭证 ${row.stocktakeNo}？账面 ${row.systemQuantity}，实盘 ${row.actualQuantity}，差异 ${row.diffQuantity} ${row.unit}。确认后生效。`, '确认过账', { type: 'warning' }) }
  catch { return }
  posting.value = true
  try { await quickStocktakeApi.confirm(row.stocktakeId); ElMessage.success('盘点已生效'); emit('changed'); await refresh() }
  catch (error) { console.error(error) }
  finally { posting.value = false }
}
async function cancel(row: QuickStocktakeReceipt) {
  if (posting.value || !canEdit.value) return
  try { await ElMessageBox.confirm(`作废未生效盘点凭证 ${row.stocktakeNo}？原记录保留，库存不变。`, '作废盘点', { type: 'warning' }) }
  catch { return }
  posting.value = true
  try { await quickStocktakeApi.cancel(row.stocktakeId); ElMessage.success('未生效盘点已作废'); await loadRecords() }
  catch (error) { console.error(error) }
  finally { posting.value = false }
}
function viewTransactions(row: QuickStocktakeReceipt) {
  void router.push({ path: '/inventory/transaction', query: { inventoryItemId: row.inventoryItemId, sourceNo: row.stocktakeNo } })
  emit('update:modelValue', false)
}
function beforeClose(done: () => void) { if (!saving.value && !posting.value) { emit('update:modelValue', false); done() } }
watch(() => [props.modelValue, props.inventoryItemId], ([open]) => {
  ++batchVersion; ++recordVersion; batches.value = []; records.value = []; clearSelection()
  if (open) { batchPage.value = 1; recordPage.value = 1; tab.value = 'batches'; void refresh() }
}, { immediate: true })
</script>
