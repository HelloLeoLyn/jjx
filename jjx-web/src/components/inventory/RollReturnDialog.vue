<template>
  <el-dialog v-model="visible" title="余卷退料" width="520px" append-to-body :close-on-click-modal="false">
    <el-descriptions :column="1" border>
      <el-descriptions-item label="物料">{{ row?.materialCode }} {{ row?.materialName }}</el-descriptions-item>
      <el-descriptions-item label="卷号">{{ row?.rollNo }}</el-descriptions-item>
      <el-descriptions-item label="原始尺寸">{{ row?.rollWidthMm }}mm × {{ row?.originalRollLengthM }}m</el-descriptions-item>
    </el-descriptions>
    <el-form label-width="110px" style="margin-top: 16px">
      <el-form-item label="退回实测长度(m)"><el-input-number v-model="length" :min="0.0001" :precision="4" @change="preview" /></el-form-item>
      <el-form-item label="退回面积(㎡)">{{ area ?? '-' }}</el-form-item>
      <el-form-item label="退入仓库">
        <el-select v-model="warehouseId" @change="loadLocations">
          <el-option v-for="w in warehouses" :key="w.warehouseId" :value="String(w.warehouseId)" :label="w.warehouseName" />
        </el-select>
      </el-form-item>
      <el-form-item label="退入库位">
        <el-select v-model="locationId" clearable>
          <el-option v-for="l in locations" :key="l.locationId" :value="String(l.locationId)" :label="l.locationName || l.locationCode" />
        </el-select>
      </el-form-item>
      <el-form-item label="退料原因"><el-input v-model="reason" maxlength="200" type="textarea" /></el-form-item>
    </el-form>
    <template #footer><el-button @click="visible = false">取消</el-button><el-button type="primary" :loading="saving" @click="submit">确认退料入库</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { rollApi } from '@/api/inventory/stockItem'
import { warehouseApi } from '@/api/inventory/warehouse'
import { locationApi } from '@/api/inventory/location'
import type { StockItemVO } from '@/types/inventory/stock'
import type { InventoryWarehouse } from '@/types/inventory/warehouse'
import type { InventoryStorageLocation } from '@/types/inventory/location'
const props = defineProps<{ modelValue: boolean; row?: StockItemVO }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'success'): void }>()
const visible = computed({ get: () => props.modelValue, set: (v) => emit('update:modelValue', v) })
const length = ref<number>()
const area = ref<number>()
const warehouseId = ref('')
const locationId = ref('')
const reason = ref('')
const saving = ref(false)
const warehouses = ref<InventoryWarehouse[]>([])
const locations = ref<InventoryStorageLocation[]>([])
let previewId = 0
watch(() => props.modelValue, async (open) => {
  if (!open) return
  previewId++
  length.value = undefined; area.value = undefined; reason.value = ''
  warehouseId.value = String(props.row?.warehouseId || '')
  warehouses.value = (await warehouseApi.list({ current: 1, pageSize: 100 })).data?.records || []
  await loadLocations()
  locationId.value = String(props.row?.locationId || '')
})
const loadLocations = async () => {
  locationId.value = ''
  locations.value = warehouseId.value ? (await locationApi.getByWarehouse(Number(warehouseId.value))).data || [] : []
}
const preview = async () => {
  const id = ++previewId
  area.value = undefined
  if (!length.value || !props.row?.rollWidthMm) return
  const res = await rollApi.area(Number(props.row.rollWidthMm), length.value)
  if (id === previewId) area.value = Number(res.data)
}
const submit = async () => {
  if (!length.value || !warehouseId.value || !reason.value.trim() || !props.row?.lastOutboundItemId || !props.row.rollNo) {
    ElMessage.warning('请填写退回实测长度、仓库和原因，并关联原发料卷')
    return
  }
  saving.value = true
  try {
    await rollApi.returnRoll({ sourceOutboundItemId: String(props.row.lastOutboundItemId), rollNo: props.row.rollNo,
      returnedLengthM: length.value, warehouseId: warehouseId.value, locationId: locationId.value || undefined, reason: reason.value.trim() })
    ElMessage.success('余卷已退料入库')
    emit('success'); visible.value = false
  } finally { saving.value = false }
}
</script>
