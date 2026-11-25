<template>
  <el-dialog
    :model-value="visible"
    :title="`IQC 确认入库 - ${inboundNo || ''}`"
    width="850px"
    append-to-body
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-alert
      title="只有允收数量进入可用库存，其余数量自动进入 IQC 隔离台账。"
      type="warning"
      :closable="false"
      show-icon
    />
    <el-table v-loading="loading" :data="items" border class="posting-table">
      <el-table-column prop="materialCode" label="物料编码" width="150" />
      <el-table-column prop="materialName" label="物料名称" min-width="180" />
      <el-table-column prop="supplierMaterialName" label="供应商品名" min-width="180" />
      <el-table-column prop="quantity" label="收货数量" width="110" align="right" />
      <el-table-column label="可用入库" width="120" align="right">
        <template #default="{ row }">{{ Number(row.acceptedQuantity || 0) }}</template>
      </el-table-column>
      <el-table-column label="隔离数量" width="110" align="right">
        <template #default="{ row }">{{
          Math.max(0, Number(row.quantity) - Number(row.acceptedQuantity || 0))
        }}</template>
      </el-table-column>
      <el-table-column label="入库库位" width="220">
        <template #default="{ row }">
          <el-select v-model="row.locationId" filterable placeholder="请选择库位" style="width: 100%">
            <el-option
              v-for="loc in locations"
              :key="loc.locationId"
              :label="loc.locationName || loc.locationCode"
              :value="loc.locationId"
            />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="处置" width="120">
        <template #default="{ row }">{{
          row.disposition ? IqcQuarantineActionEnum.getLabel(row.disposition) : '-'
        }}</template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="confirm"
        >确认入库并生成隔离台账</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { inboundApi } from '@/api/inventory/inbound'
import { locationApi } from '@/api/inventory/location'
import type { InventoryStorageLocation } from '@/types/inventory/location'
import { useUserStore } from '@/store/modules/user'
import { IqcQuarantineActionEnum } from '@/enums/inventory/IqcQuarantineEnum'
import type { InboundItemVO } from '@/types/inventory/inbound'

const props = defineProps<{ visible: boolean; inboundId?: number; inboundNo?: string }>()
const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void
  (event: 'success'): void
}>()
const userStore = useUserStore()
const loading = ref(false)
const submitting = ref(false)
type PostingRow = InboundItemVO & { locationId?: number }
const items = ref<PostingRow[]>([])
const locations = ref<InventoryStorageLocation[]>([])

watch(
  () => [props.visible, props.inboundId] as const,
  async ([visible, id]) => {
    if (!visible || !id) return
    loading.value = true
    try {
      const detail = (await inboundApi.getById(String(id))).data
      const warehouseId = Number(detail?.warehouseId || 0)
      locations.value = warehouseId
        ? (await locationApi.list({ pageNum: 1, pageSize: 500, warehouseId })).data?.records || []
        : []
      const auto = locations.value.length === 1 ? locations.value[0].locationId : undefined
      items.value = (detail?.items || []).map((item) => ({ ...item, locationId: auto })) as PostingRow[]
    } finally {
      loading.value = false
    }
  },
  { immediate: true }
)

async function confirm() {
  if (!props.inboundId) return
  if (!items.value.length || items.value.some((item) => !item.locationId)) {
    ElMessage.warning('请为每一行明细选择入库库位')
    return
  }
  submitting.value = true
  try {
    await inboundApi.confirm(
      String(props.inboundId),
      String(userStore.userId || ''),
      String(userStore.nickName || userStore.userName || ''),
      {
        items: items.value.map((item) => ({
          itemId: Number(item.inboundItemId || item.itemId),
          locationId: Number(item.locationId),
        })),
      }
    )
    ElMessage.success('入库过账完成，不合格数量已进入隔离台账')
    emit('success')
    emit('update:visible', false)
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.location-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 12px 0;
}
.location-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.posting-table {
  margin-top: 16px;
}
</style>
