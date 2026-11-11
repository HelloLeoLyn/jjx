<template>
  <el-dialog v-model="visible" title="采购收货卷计划" width="900px" append-to-body :close-on-click-modal="false">
    <el-descriptions :column="3" border>
      <el-descriptions-item label="物料">{{ item?.materialCode }} {{ item?.materialName }}</el-descriptions-item>
      <el-descriptions-item label="原收货面积(㎡)">{{ item?.quantity }}</el-descriptions-item>
      <el-descriptions-item label="允收入库面积(㎡)">{{ item?.acceptedQuantity ?? 0 }}</el-descriptions-item>
    </el-descriptions>
    <p>按本次允许入库的实际物理卷录入尺寸；卷号与来料批次分别保留。</p>
    <el-button v-if="!readonly" @click="addRow">添加一卷</el-button>
    <el-table :data="rows" border style="margin-top: 12px">
      <el-table-column label="卷号" min-width="180"><template #default="{ row }"><el-input v-model="row.rollNo" maxlength="64" :disabled="readonly" /></template></el-table-column>
      <el-table-column label="卷宽(mm)" width="170"><template #default="{ row }"><el-input-number v-model="row.widthMm" :controls="false" :min="0.0001" :precision="4" :disabled="readonly" @change="preview(row)" /></template></el-table-column>
      <el-table-column label="实际卷长(m)" width="170"><template #default="{ row }"><el-input-number v-model="row.lengthM" :controls="false" :min="0.0001" :precision="4" :disabled="readonly" @change="preview(row)" /></template></el-table-column>
      <el-table-column label="面积(㎡)" width="120"><template #default="{ row }">{{ row.area ?? '-' }}</template></el-table-column>
      <el-table-column v-if="!readonly" label="操作" width="80"><template #default="{ $index }"><el-button link type="danger" @click="rows.splice($index, 1)">移除</el-button></template></el-table-column>
    </el-table>
    <p>共 {{ rows.length }} 卷，预览合计 {{ previewTotal }}㎡；保存时按允收入库面积核对。</p>
    <template #footer><el-button @click="visible = false">关闭</el-button><el-button v-if="!readonly" type="primary" :loading="saving" @click="save">保存卷计划</el-button></template>
  </el-dialog>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { inboundApi } from '@/api/inventory/inbound'
import { rollApi } from '@/api/inventory/stockItem'
import type { InboundItemVO, RollReceiptEntry } from '@/types/inventory/inbound'
const props = defineProps<{ modelValue: boolean; item?: InboundItemVO; readonly?: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'success'): void }>()
const visible = computed({ get: () => props.modelValue, set: (v) => emit('update:modelValue', v) })
type PlanRow = RollReceiptEntry & { previewId?: number }
const rows = ref<PlanRow[]>([])
const saving = ref(false)
const previewTotal = computed(() => (rows.value.reduce((total, row) => total + Number(row.area || 0), 0)).toFixed(4))
watch(() => props.modelValue, (open) => {
  if (!open) return
  rows.value = (props.item?.rollPlan || []).map(row => ({ ...row }))
  if (!rows.value.length && !props.readonly) addRow()
})
const addRow = () => rows.value.push({ rollNo: '', widthMm: rows.value.at(-1)?.widthMm })
const preview = async (row: PlanRow) => {
  const id = (row.previewId || 0) + 1
  row.previewId = id; row.area = undefined
  if (!row.widthMm || !row.lengthM) return
  const result = await rollApi.area(row.widthMm, row.lengthM)
  if (row.previewId === id) row.area = Number(result.data)
}
const save = async () => {
  if (!props.item?.inboundItemId || !rows.value.length || rows.value.some(row => !row.rollNo.trim() || !row.widthMm || !row.lengthM)) {
    ElMessage.warning('请填写每卷的卷号、卷宽和实际卷长')
    return
  }
  saving.value = true
  try {
    await inboundApi.saveRollPlan(String(props.item.inboundItemId), rows.value)
    ElMessage.success('卷计划已保存，库存仍需在原凭证确认入库')
    emit('success'); visible.value = false
  } finally { saving.value = false }
}
</script>
