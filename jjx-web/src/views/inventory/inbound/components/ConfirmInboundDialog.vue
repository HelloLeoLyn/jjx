<template>
  <el-dialog
    :model-value="visible"
    title="确认入库 · 选择库位"
    width="780px"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-alert
      title="确认入库后库存落到所选库位；库位必选，且必须属于该单仓库。"
      type="info"
      :closable="false"
      show-icon
    />
    <el-table :data="rows" border size="small" class="confirm-table">
      <el-table-column prop="inboundNo" label="入库单号" min-width="170" />
      <el-table-column label="仓库" min-width="130">
        <template #default="{ row }">{{ row.warehouseName || warehouseIdOf(row) }}</template>
      </el-table-column>
      <el-table-column label="入库库位" min-width="240">
        <template #default="{ row }">
          <el-select
            v-model="row.locationId"
            filterable
            :loading="loadingWarehouses.has(String(row.warehouseId))"
            placeholder="请选择库位"
            style="width: 100%"
          >
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
/** 确认入库专用弹窗（dev-20261125-001）：库位必选，按单仓库加载候选。 */
import { computed, reactive, ref, watch } from 'vue'
import { locationApi } from '@/api/inventory/location'
import type { InventoryStorageLocation } from '@/types/inventory/location'

export interface ConfirmTarget {
  inboundId: string | number
  inboundNo?: string
  warehouseId?: string | number
  warehouseName?: string
}

const props = withDefaults(
  defineProps<{ visible: boolean; orders: ConfirmTarget[]; submitting?: boolean }>(),
  { orders: () => [], submitting: false }
)
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'confirm', selections: Array<{ inboundId: string; locationId: number }>): void
}>()

type Row = ConfirmTarget & { locationId?: number }
const rows = ref<Row[]>([])
const locations = reactive<Record<string, InventoryStorageLocation[]>>({})
const loadingWarehouses = reactive(new Set<string>())

const warehouseIdOf = (row: Row) => String(row.warehouseId ?? '')

function locationsOf(warehouseId?: string | number): InventoryStorageLocation[] {
  return locations[String(warehouseId ?? '')] || []
}

async function loadLocations(warehouseId?: string | number) {
  const key = String(warehouseId ?? '')
  if (!key) return
  if (locations[key] || loadingWarehouses.has(key)) return
  loadingWarehouses.add(key)
  try {
    const res = await locationApi.list({ pageNum: 1, pageSize: 500, warehouseId: Number(key) })
    locations[key] = res.data?.records || []
  } finally {
    loadingWarehouses.delete(key)
  }
}

watch(
  () => [props.visible, props.orders] as const,
  async ([visible]) => {
    if (!visible) return
    rows.value = (props.orders || []).map((order) => ({ ...order, locationId: undefined }))
    for (const order of rows.value) await loadLocations(order.warehouseId)
    // 只有一个候选库位时默认选中，减少点击
    rows.value.forEach((row) => {
      const list = locationsOf(row.warehouseId)
      if (!row.locationId && list.length === 1) row.locationId = list[0].locationId
    })
  },
  { immediate: true, deep: true }
)

const allSelected = computed(() => rows.value.length > 0 && rows.value.every((row) => !!row.locationId))

function submit() {
  if (!allSelected.value) return
  emit(
    'confirm',
    rows.value.map((row) => ({
      inboundId: String(row.inboundId),
      locationId: Number(row.locationId),
    }))
  )
}
</script>

<style scoped>
.confirm-table {
  margin-top: 12px;
}
</style>
