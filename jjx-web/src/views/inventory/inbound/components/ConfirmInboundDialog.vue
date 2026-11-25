<template>
  <el-dialog
    :model-value="visible"
    title="确认入库 · 按明细选择库位"
    width="900px"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-alert
      title="确认入库后库存落到所选库位；每行明细都必须选库位，允许同一物料分放多个库位。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-table v-loading="loading" :data="rows" border size="small" class="confirm-table" max-height="420">
      <el-table-column prop="inboundNo" label="入库单号" width="150" />
      <el-table-column prop="materialCode" label="物料编码" width="130" />
      <el-table-column prop="materialName" label="物料名称" min-width="150" />
      <el-table-column prop="batchNo" label="批次/卷" min-width="130" />
      <el-table-column label="入库库位" min-width="220">
        <template #default="{ row }">
          <el-select v-model="row.locationId" filterable placeholder="请选择库位" style="width: 100%">
            <el-option
              v-for="loc in locationsOf(row.warehouseId)"
              :key="loc.locationId"
              :label="loc.locationName || loc.locationCode"
              :value="loc.locationId"
            />
          </el-select>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!allSelected" @click="submit">
        确认入库
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/** 确认入库专用弹窗（dev-20261125-002）：按明细行选库位，支持同一物料多库位。 */
import { computed, reactive, ref, watch } from 'vue'
import { inboundApi } from '@/api/inventory/inbound'
import { locationApi } from '@/api/inventory/location'
import type { InventoryStorageLocation } from '@/types/inventory/location'

export interface ConfirmTarget {
  inboundId: string | number
  inboundNo?: string
  warehouseId?: string | number
  warehouseName?: string
}

interface ConfirmRow {
  inboundId: string
  inboundNo?: string
  warehouseId?: string | number
  itemId: number
  materialCode?: string
  materialName?: string
  batchNo?: string
  locationId?: number
}

const props = withDefaults(
  defineProps<{ visible: boolean; orders: ConfirmTarget[]; submitting?: boolean }>(),
  { orders: () => [], submitting: false }
)
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'confirm', selections: Array<{ inboundId: string; items: Array<{ itemId: number; locationId: number }> }>): void
}>()

const loading = ref(false)
const rows = ref<ConfirmRow[]>([])
const locations = reactive<Record<string, InventoryStorageLocation[]>>({})

function locationsOf(warehouseId?: string | number): InventoryStorageLocation[] {
  return locations[String(warehouseId ?? '')] || []
}

async function loadLocations(warehouseId?: string | number) {
  const key = String(warehouseId ?? '')
  if (!key || locations[key]) return
  const res = await locationApi.list({ pageNum: 1, pageSize: 500, warehouseId: Number(key) })
  locations[key] = res.data?.records || []
}

watch(
  () => [props.visible, props.orders] as const,
  async ([visible]) => {
    if (!visible) return
    loading.value = true
    rows.value = []
    try {
      for (const order of props.orders || []) {
        await loadLocations(order.warehouseId)
        const detail = (await inboundApi.getById(String(order.inboundId))).data
        const items = detail?.items || []
        const list = locationsOf(order.warehouseId)
        const auto = list.length === 1 ? list[0].locationId : undefined
        for (const item of items) {
          rows.value.push({
            inboundId: String(order.inboundId),
            inboundNo: order.inboundNo || detail?.inboundNo,
            warehouseId: order.warehouseId ?? detail?.warehouseId,
            itemId: Number(item.inboundItemId || item.itemId),
            materialCode: item.materialCode,
            materialName: item.materialName,
            batchNo: item.batchNo || item.rollNo || '',
            locationId: auto,
          })
        }
      }
    } finally {
      loading.value = false
    }
  },
  { immediate: true, deep: true }
)

const allSelected = computed(() => rows.value.length > 0 && rows.value.every((row) => !!row.locationId))

function submit() {
  if (!allSelected.value) return
  const grouped = new Map<string, Array<{ itemId: number; locationId: number }>>()
  for (const row of rows.value) {
    const list = grouped.get(row.inboundId) || []
    list.push({ itemId: row.itemId, locationId: Number(row.locationId) })
    grouped.set(row.inboundId, list)
  }
  emit(
    'confirm',
    Array.from(grouped.entries()).map(([inboundId, items]) => ({ inboundId, items }))
  )
}
</script>

<style scoped>
.confirm-table {
  margin-top: 12px;
}
</style>
