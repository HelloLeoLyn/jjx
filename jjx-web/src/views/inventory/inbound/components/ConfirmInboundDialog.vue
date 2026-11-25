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
    <div class="bulk-bar">
      <span class="bulk-label">统一设置库位</span>
      <el-select v-model="bulkLocationId" filterable clearable placeholder="选择库位" style="width: 260px">
        <el-option-group v-for="group in bulkGroups" :key="group.warehouseId" :label="group.label">
          <el-option
            v-for="loc in group.locations"
            :key="loc.locationId"
            :label="loc.locationName || loc.locationCode"
            :value="loc.locationId"
          />
        </el-option-group>
      </el-select>
      <el-button :disabled="!bulkLocationId" @click="applyBulk">应用到本仓库所有行</el-button>
      <span class="bulk-tip">只影响与所选库位同仓库的明细</span>
    </div>
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
const bulkLocationId = ref<number>()
const warehouseNames = reactive<Record<string, string>>({})

/** 统一设置：按仓库分组展示全部已加载库位 */
const bulkGroups = computed(() =>
  Object.entries(locations).map(([warehouseId, list]) => ({
    warehouseId,
    label: warehouseNames[warehouseId] || `仓库${warehouseId}`,
    locations: list,
  }))
)

/** 把所选库位应用到同仓库的全部明细行（不同仓库不动） */
function applyBulk() {
  if (!bulkLocationId.value) return
  const target = Object.values(locations).flat().find((loc) => loc.locationId === bulkLocationId.value)
  if (!target) return
  rows.value.forEach((row) => {
    if (String(row.warehouseId) === String(target.warehouseId)) row.locationId = target.locationId
  })
}
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
        warehouseNames[String(order.warehouseId ?? '')] = order.warehouseName || detail?.warehouseName || ''
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
.bulk-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
  flex-wrap: wrap;
}
.bulk-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.bulk-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.confirm-table {
  margin-top: 12px;
}
</style>
