<template>
  <el-dialog
    :model-value="visible"
    :title="`IQC 隔离处置 - ${inboundNo || ''}`"
    width="1200px"
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
      <el-table-column label="处置" width="360">
        <template #default="{ row }">
          <template v-if="canDispose && row.status === IqcQuarantineStatus.PENDING">
            <el-select
              v-model="row.action"
              placeholder="请选择处置方式（必选）"
              size="small"
              style="width: 100%"
            >
              <el-option
                v-for="item in IqcQuarantineActionEnum.items"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
            <div class="action-effect">{{ actionEffect(row.action) }}</div>
            <div class="action-qty">
              <span>处置数量</span>
              <el-input-number
                v-model="row.actionQuantity"
                :min="0.0001"
                :max="Number(row.remainingQuantity)"
                :precision="4"
                size="small"
                style="width: 110px"
              />
              <span class="action-remain">/ 剩余 {{ num(row.remainingQuantity) }}</span>
            </div>
            <el-button type="primary" link :disabled="!row.action" @click="submit(row)">
              执行处置
            </el-button>
          </template>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>
    <el-divider content-position="left">处置单历史</el-divider>
    <el-table v-loading="ordersLoading" :data="orders" border size="small">
      <el-table-column prop="dispositionNo" label="处置单号" min-width="190" />
      <el-table-column label="类型" width="110">
        <template #default="{ row }">{{ actionLabel(row.action) }}</template>
      </el-table-column>
      <el-table-column prop="materialCode" label="物料" width="150" />
      <el-table-column prop="quantity" label="数量" width="90" />
      <el-table-column prop="operatorName" label="操作人" width="100" />
      <el-table-column prop="createTime" label="时间" min-width="170" />
    </el-table>
    <template #footer><el-button @click="emit('update:visible', false)">关闭</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { inboundApi } from '@/api/inventory/inbound'
import { useUserStore } from '@/store/modules/user'
import { hasPermi } from '@/directives'
import {
  IqcQuarantineAction,
  IqcQuarantineActionEffect,
  IqcQuarantineActionEnum,
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
const canDispose = computed(() => hasPermi(['quality:ncr:dispose']))
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
        // 之前默认预选「释放入库」（把不良品计入可用库存）且数量默认全部，一确认就生效；
        // 改为必须人工选动作，数量仍默认全部（退货/报废通常就是整批）。
        action: '',
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
  if (!row.action) return ElMessage.warning('请先选择处置方式')
  const quantity = Number(row.actionQuantity || 0)
  const remaining = Number(row.remainingQuantity || 0)
  if (!(quantity > 0)) return ElMessage.warning('处置数量必须大于 0')
  if (quantity > remaining) {
    return ElMessage.warning(`处置数量不能超过剩余数量 ${num(remaining)}`)
  }
  const label = IqcQuarantineActionEnum.getLabel(row.action)
  const effect = IqcQuarantineActionEffect[row.action] || ''
  const isRelease = row.action === IqcQuarantineAction.RELEASE
  await ElMessageBox.confirm(
    `确认对 ${row.materialCode} 执行【${label}】${num(quantity)} 个？影响：${effect}`,
    isRelease ? '释放入库确认（不良品将计入可用库存）' : '隔离品处置确认',
    {
      type: isRelease ? 'warning' : 'info',
      confirmButtonText: '确认执行',
      cancelButtonText: '取消',
    }
  )
  await inboundApi.handleQuarantine(String(row.quarantineId), {
    action: row.action,
    quantity,
    operatorId: String(user.userId || ''),
    operatorName: String(user.nickName || user.userName || ''),
  })
  ElMessage.success(`处置完成：【${label}】${num(quantity)} 个 ${row.materialCode}`)
  await load()
  emit('success')
}
const num = (value?: number | string | null) =>
  value == null || value === ''
    ? '-'
    : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
const actionLabel = (value?: string) => (value ? IqcQuarantineActionEnum.getLabel(value) : '-')
const actionEffect = (value?: string) => (value ? IqcQuarantineActionEffect[value] || '' : '')
</script>

<style scoped>
.action-effect {
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
  line-height: 1.5;
}
.action-qty {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
  color: #606266;
}
.action-remain {
  color: #909399;
}
</style>
