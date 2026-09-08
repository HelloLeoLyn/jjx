<template>
  <el-dialog
    :model-value="visible"
    :title="`IQC 隔离处置 - ${inboundNo || ''}`"
    width="950px"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
  >
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="materialCode" label="物料编码" width="150" />
      <el-table-column prop="materialName" label="物料名称" min-width="160" />
      <el-table-column prop="quantity" label="原始隔离" width="100" />
      <el-table-column prop="remainingQuantity" label="剩余数量" width="100" />
      <el-table-column label="状态" width="100"
        ><template #default="{ row }"
          ><el-tag :type="IqcQuarantineStatusEnum.getTagProps(row.status).type">{{
            IqcQuarantineStatusEnum.getLabel(row.status)
          }}</el-tag></template
        ></el-table-column
      >
      <el-table-column label="处置" width="330">
        <template #default="{ row }">
          <template v-if="row.status === IqcQuarantineStatus.PENDING">
            <el-input-number
              v-model="row.actionQuantity"
              :min="0.0001"
              :max="Number(row.remainingQuantity)"
              :precision="4"
              size="small"
            />
            <el-select v-model="row.action" size="small" style="width: 120px; margin-left: 8px">
              <el-option label="释放入库" :value="IqcQuarantineAction.RELEASE" />
              <el-option label="退货" :value="IqcQuarantineAction.RETURN" />
              <el-option label="返工" :value="IqcQuarantineAction.REWORK" />
              <el-option label="报废" :value="IqcQuarantineAction.SCRAP" />
            </el-select>
            <el-button link type="primary" @click="submit(row)">执行</el-button>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>
    <el-divider content-position="left">处置单历史</el-divider>
    <el-table v-loading="ordersLoading" :data="orders" border size="small">
      <el-table-column prop="dispositionNo" label="处置单号" min-width="190" />
      <el-table-column prop="action" label="类型" width="100" />
      <el-table-column prop="materialCode" label="物料" width="150" />
      <el-table-column prop="quantity" label="数量" width="90" />
      <el-table-column prop="operatorName" label="操作人" width="100" />
      <el-table-column prop="createTime" label="时间" min-width="170" />
    </el-table>
    <template #footer><el-button @click="emit('update:visible', false)">关闭</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { inboundApi } from '@/api/inventory/inbound'
import { useUserStore } from '@/store/modules/user'
import {
  IqcQuarantineAction,
  IqcQuarantineStatus,
  IqcQuarantineStatusEnum,
} from '@/enums/inventory/IqcQuarantineEnum'
const props = defineProps<{
  visible: boolean
  inboundId?: number
  inboundNo?: string
  itemId?: string
}>()
const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void
  (event: 'success'): void
}>()
const rows = ref<any[]>([])
const orders = ref<any[]>([])
const loading = ref(false)
const ordersLoading = ref(false)
const user = useUserStore()
watch(
  () => [props.visible, props.inboundId, props.itemId] as const,
  ([visible]) => {
    if (visible) load()
  },
  { immediate: true }
)
async function load() {
  if (!props.inboundId) return
  loading.value = true
  ordersLoading.value = true
  try {
    const [quarantine, dispositionOrders] = await Promise.all([
      inboundApi.listQuarantine(String(props.inboundId)),
      inboundApi.listDispositionOrders(String(props.inboundId)),
    ])
    rows.value = (quarantine.data || [])
      .filter((row) => !props.itemId || String(row.inboundItemId) === props.itemId)
      .map((row) => ({
        ...row,
        action: IqcQuarantineAction.RELEASE,
        actionQuantity: Number(row.remainingQuantity),
      }))
    orders.value = (dispositionOrders.data || []).filter(
      (row) => !props.itemId || String(row.inboundItemId) === props.itemId
    )
  } finally {
    loading.value = false
    ordersLoading.value = false
  }
}
async function submit(row: any) {
  await ElMessageBox.confirm(
    `确认处置 ${row.actionQuantity} 个 ${row.materialCode}？`,
    '隔离品处置'
  )
  await inboundApi.handleQuarantine(String(row.quarantineId), {
    action: row.action,
    quantity: row.actionQuantity,
    operatorId: String(user.userId || ''),
    operatorName: String(user.nickName || user.userName || ''),
  })
  ElMessage.success('隔离品处置已记账')
  await load()
  emit('success')
}
</script>
