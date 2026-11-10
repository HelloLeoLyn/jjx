<template>
    <!-- 打样领料：预览 + 改数量（JJX-QR-031 抬头「打样领料单」） -->
    <el-dialog
      v-model="pickVisible"
      :title="`打样领料 - ${pickSample?.orderNo || ''}`"
      width="1250px"
      append-to-body
    >
      <p>
        打样数量：{{ pickSample?.sampleQty ?? '-' }} {{ pickSample?.unit || '' }}； 单位用量 = 基数
        ÷ 模数（两者均大于 0，否则使用 BOM 用量）； 需求 = 单位用量 × 打样数量（向上取整）
      </p>
      <el-table
        :data="pickRows"
        size="small"
        border
        :row-class-name="
          ({ row }: { row: SamplePickPreviewRow }) =>
            Number(row.gap) > 0 ? 'sample-shortage-row' : ''
        "
      >
        <el-table-column
          label="物料名称"
          prop="materialName"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column label="匹配物料" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.materialId" size="small" type="success">{{
              row.materialCode
            }}</el-tag>
            <el-tag v-else size="small" type="danger">未匹配</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="规格" prop="specification" min-width="150" show-overflow-tooltip />
        <el-table-column label="单位" prop="unit" width="70" align="center" />
        <el-table-column label="模数" prop="moduleQty" width="80" align="right" />
        <el-table-column label="基数" prop="baseQty" width="80" align="right" />
        <el-table-column label="单位用量" prop="unitQuantity" width="100" align="right" />
        <el-table-column label="BOM需求" prop="quantity" width="90" align="right" />
        <el-table-column label="已领" width="80" align="right">
          <template #default="{ row }">{{ row.issuedQuantity ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="可用" width="80" align="right">
          <template #default="{ row }">{{ row.availableQuantity ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="缺口" width="100" align="right">
          <template #default="{ row }">
            <span v-if="Number(row.gap) > 0" class="sample-shortage-gap">缺 {{ row.gap }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="本次领料" width="140">
          <template #default="{ row }">
            <el-input-number
              v-model="row.pickQty"
              :min="0"
              :disabled="!row.materialId"
              size="small"
              style="width: 120px"
            />
          </template>
        </el-table-column>
      </el-table>
      <el-alert
        v-if="unmatchedCount > 0"
        type="warning"
        :closable="false"
        style="margin-top: 8px"
        :title="`有 ${unmatchedCount} 条样品BOM物料未匹配到物料档案，暂不参与领料（可先在物料档案补建后再来）`"
      />
      <template #footer>
        <el-button @click="pickVisible = false">取消</el-button>
        <el-button type="primary" :loading="pickSubmitting" @click="submitSamplePick"
          >生成领料单</el-button
        >
      </template>
    </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { outboundApi } from '@/api/inventory/outbound'
import type { SamplePickPreviewRow } from '@/types/inventory/outbound'

const emit = defineEmits<{ (e: 'preview-loaded'): void; (e: 'success'): void }>()
// ===== 打样领料单（按样品单生成；原在「产品作业规范」⑦ 页签，dev-20261010-013 挪到样品单页）=====
const pickVisible = ref(false)
const pickSample = ref<any>(null)
/** 开弹窗时守卫过的样品单号（列表行主键 orderId）：提交时直接用，不再从行对象取字段（dev-20261016-054） */
const pickSampleId = ref<number | null>(null)
const pickRows = ref<(SamplePickPreviewRow & { pickQty: number })[]>([])
const pickSubmitting = ref(false)
const unmatchedCount = computed(() => pickRows.value.filter((r) => !r.materialId).length)

async function openSamplePick(row: any) {
  // 列表行的主键是 orderId（SampleOrder.orderId）；曾误读 row.sampleOrderId（那是会签/不良记录的字段）
  // → Number(undefined)=NaN 拼进 URL，后端 Long 转换失败（dev-20261016-054）。取值前先守卫。
  const sampleOrderId = Number(row?.orderId)
  if (!Number.isFinite(sampleOrderId) || sampleOrderId <= 0) {
    ElMessage.error('取不到样品单号，无法生成领料单；请刷新列表后重试')
    return
  }
  pickSample.value = row
  pickSampleId.value = sampleOrderId
  pickRows.value = []
  try {
    const res: any = await outboundApi.samplePickPreview(sampleOrderId)
    const rows: SamplePickPreviewRow[] = res?.data || []
    emit('preview-loaded')
    pickRows.value = rows.map((r) => ({
      ...r,
      pickQty: Math.max(0, Number(r.quantity || 0) - Number(r.issuedQuantity || 0)),
    }))
  } catch (e: any) {
    ElMessage.error(e?.message || '加载打样领料预览失败')
    return
  }
  pickVisible.value = true
}

async function submitSamplePick() {
  if (!pickSample.value) return
  // 用开弹窗时守卫过的样品单号，避免再从行对象上取字段（dev-20261016-054）
  const sampleOrderId = pickSampleId.value
  if (!sampleOrderId) {
    ElMessage.error('取不到样品单号，无法生成领料单；请刷新列表后重试')
    return
  }
  const items = pickRows.value
    .filter((r) => r.materialId && Number(r.pickQty) > 0)
    .map((r) => ({
      materialId: Number(r.materialId),
      materialCode: r.materialCode || undefined,
      materialName: r.materialName,
      quantity: Number(r.pickQty),
    }))
  if (!items.length) {
    ElMessage.warning('请填写本次领料数量（至少一行、物料已匹配）')
    return
  }
  pickSubmitting.value = true
  try {
    const res: any = await outboundApi.createSamplePick(sampleOrderId, items)
    const outboundId = Number(res?.data || 0)
    ElMessage.success('打样领料单已生成（待仓库确认发料后扣库存）')
    pickVisible.value = false
    emit('success')
    // 生成后直接开打印页（JJX-QR-031 抬头「打样领料单」）；该单同时可在出库管理查看
    if (outboundId) window.open(`/print/outbound/${outboundId}`, '_blank')
  } catch (e: any) {
    ElMessage.error(e?.message || '生成失败')
  } finally {
    pickSubmitting.value = false
  }
}

defineExpose({ open: openSamplePick })
</script>

<style scoped>
:deep(.sample-shortage-row) {
  --el-table-tr-bg-color: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}
.sample-shortage-gap {
  color: var(--el-color-danger);
  font-weight: 600;
}

</style>
