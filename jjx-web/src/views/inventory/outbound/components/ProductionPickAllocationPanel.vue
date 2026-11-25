<template>
  <div v-loading="loading" class="allocation-panel">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="view">
      <el-alert
        v-if="view.unknownSupplier"
        title="部分已选批次供应商未知，补齐入库来源前不能审批或发料"
        type="error"
        :closable="false"
      />
      <el-alert
        v-else-if="view.mixedSupplier"
        title="跨供应商混批须专门审批；修改分配后原审批失效"
        type="warning"
        :closable="false"
      />
      <div class="allocation-actions">
        <template v-if="editable">
          <el-button v-hasPermi="['inventory:outbound:edit']" :loading="busy" @click="reallocate"
            >按 FIFO 重新分配</el-button
          >
          <el-button v-hasPermi="['inventory:outbound:edit']" @click="clearAll"
            >清空本次数量</el-button
          >
          <el-button
            v-hasPermi="['inventory:outbound:edit']"
            type="primary"
            :loading="busy"
            @click="save"
            >保存分配</el-button
          >
          <el-button v-if="canSubmit" v-hasPermi="['inventory:outbound:edit']" @click="submit"
            >提交审核</el-button
          >
        </template>
        <template v-if="view.status === OutboundOrderStatusEnum.MIXED_BATCH_PENDING.value">
          <el-button
            v-hasPermi="['outbound:pick:mixed-batch:approve']"
            type="success"
            :disabled="dirty || view.unknownSupplier"
            :loading="busy"
            @click="review(true)"
            >混批审批通过</el-button
          >
          <el-button
            v-hasPermi="['outbound:pick:mixed-batch:approve']"
            type="danger"
            :disabled="dirty"
            :loading="busy"
            @click="review(false)"
            >驳回混批</el-button
          >
        </template>
        <el-button :disabled="dirty" @click="print('pick')">打印领料单</el-button>
        <el-button :disabled="dirty" @click="print('picking')">打印拣货单</el-button>
      </div>
      <el-table
        :data="materials"
        border
        highlight-current-row
        :row-class-name="({ row }: any) => (row.isGroup ? 'pick-parent-group' : '')"
        @current-change="selectMaterial"
      >
        <el-table-column label="物料编码" prop="materialCode" width="115" />
        <el-table-column label="物料名称" prop="materialName" min-width="150" />
        <el-table-column label="项目" prop="processName" width="95" />
        <el-table-column label="规格" prop="specification" min-width="100" />
        <el-table-column label="单位" prop="unit" width="65" />
        <el-table-column label="跳距/模数/基数" width="125"
          ><template #default="{ row }"
            >{{ row.pitchMm ?? '-' }}/{{ row.moduleQty ?? '-' }}/{{ row.baseQty ?? '-' }}</template
          ></el-table-column
        >
        <el-table-column label="需求" width="85"
          ><template #default="{ row }">{{
            row.isGroup ? '-' : row.quantity
          }}</template></el-table-column
        >
        <el-table-column label="已分配" width="85"
          ><template #default="{ row }">{{
            row.isGroup ? '-' : assigned(row.materialId)
          }}</template></el-table-column
        >
        <el-table-column label="实发" width="85"
          ><template #default="{ row }">{{
            row.isGroup ? '-' : row.issued
          }}</template></el-table-column
        >
        <el-table-column label="库存" width="85"
          ><template #default="{ row }">{{
            row.isGroup ? '-' : stockTotal(row.materialId)
          }}</template></el-table-column
        >
        <el-table-column label="批次状态" min-width="130"
          ><template #default="{ row }">
            <template v-if="!row.isGroup"
              ><el-tag v-if="row.unknownSupplier" type="danger">供应商未知</el-tag
              ><el-tooltip v-else :content="batchTip(row.materialId)"
                ><el-tag
                  :type="
                    row.supplierIds.size > 1 ? 'danger' : row.batchIds.size > 1 ? 'warning' : 'info'
                  "
                  >{{
                    row.supplierIds.size > 1
                      ? '跨供应商'
                      : row.batchIds.size > 1
                        ? '同供应商多批次'
                        : '单批次'
                  }}</el-tag
                ></el-tooltip
              ></template
            >
          </template></el-table-column
        >
      </el-table>
      <template v-if="selected">
        <h4>{{ selected.materialCode }} · {{ selected.materialName }} 批次明细</h4>
        <el-table :data="batchRows" border size="small">
          <el-table-column v-if="selected.demands.length > 1" label="需求行" width="90"
            ><template #default="{ row }">{{
              row.demand.sourceBomItemId || row.demand.demandItemId
            }}</template></el-table-column
          >
          <el-table-column label="批次号" prop="batchNo" min-width="110" />
          <el-table-column label="卷号" prop="rollNo" min-width="105" />
          <el-table-column label="原始尺寸" width="115"
            ><template #default="{ row }">{{
              row.rollWidthMm ? `${row.rollWidthMm}mm × ${row.originalRollLengthM}m` : '-'
            }}</template></el-table-column
          >
          <el-table-column label="供应商" min-width="110"
            ><template #default="{ row }">{{
              row.supplierName || (row.supplierId ? `供应商 ${row.supplierId}` : '未知')
            }}</template></el-table-column
          >
          <el-table-column label="仓库" width="90"
            ><template #default>{{
              detail?.warehouseName || detail?.warehouseId || '-'
            }}</template></el-table-column
          >
          <el-table-column label="库位" prop="locationName" min-width="90" />
          <el-table-column label="结存" prop="quantity" width="85" />
          <el-table-column label="可用" prop="availableQuantity" width="85" />
          <el-table-column label="本次出库数量" min-width="185"
            ><template #default="{ row }">
              <el-input-number
                v-if="editable && !row.rollNo"
                v-model="draft[row.key]"
                :min="0"
                :max="limit(row)"
                :precision="4"
                :step="1"
                controls-position="right"
              />
              <el-checkbox
                v-else-if="editable"
                :model-value="Number(draft[row.key] || 0) > 0"
                :disabled="!draft[row.key] && limit(row) === 0"
                @change="(value: any) => (draft[row.key] = value ? limit(row) : 0)"
                >整卷 {{ row.quantity }}</el-checkbox
              >
              <span v-else>{{ draft[row.key] || 0 }}</span>
            </template></el-table-column
          >
          <el-table-column v-if="editable" label="操作" width="95"
            ><template #default="{ row }"
              ><el-button link @click="draft[row.key] = limit(row)">填满</el-button
              ><el-button link @click="draft[row.key] = 0">清零</el-button></template
            ></el-table-column
          >
        </el-table>
        <div class="allocation-totals">
          需求 {{ selected.quantity }} / 已分配 {{ assigned(selected.materialId) }} /
          <span
            :class="{
              shortage: Math.abs(selected.quantity - assigned(selected.materialId)) > 0.00001,
            }"
            >差额 {{ Number((selected.quantity - assigned(selected.materialId)).toFixed(4)) }}</span
          >
        </div>
        <el-alert
          v-if="hasRolls"
          title="卷材只能整卷分配；整卷数量超过本条需求时请调整领料需求，系统不会自动拆卷或超领。"
          type="info"
          :closable="false"
        />
      </template>
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { outboundApi } from '@/api/inventory/outbound'
import { OutboundOrderStatusEnum } from '@/enums/inventory/OutboundEnum'
import type { OutboundVO, PickAllocationView } from '@/types/inventory/outbound'
import { allocationDraft, materialRows, draftKey, sameId, batchLimit } from './pickAllocation'
const props = defineProps<{ outboundId: string | number; detail?: OutboundVO | null }>()
const emit = defineEmits<{ changed: []; ready: [canIssue: boolean] }>()
const view = ref<PickAllocationView | null>(null)
const draft = ref<Record<string, number>>({})
const loading = ref(false),
  busy = ref(false),
  error = ref(''),
  selectedId = ref('')
const initial = ref('')
const dirty = computed(() => JSON.stringify(draft.value) !== initial.value)
const editable = computed(
  () =>
    !!view.value &&
    (
      [
        OutboundOrderStatusEnum.DRAFT.value,
        OutboundOrderStatusEnum.PENDING.value,
        OutboundOrderStatusEnum.APPROVED.value,
        OutboundOrderStatusEnum.REJECTED.value,
        OutboundOrderStatusEnum.MIXED_BATCH_PENDING.value,
      ] as number[]
    ).includes(view.value.status)
)
const canSubmit = computed(
  () =>
    view.value &&
    (
      [OutboundOrderStatusEnum.DRAFT.value, OutboundOrderStatusEnum.REJECTED.value] as number[]
    ).includes(view.value.status)
)
const materials = computed(() =>
  view.value ? materialRows(view.value, props.detail?.items || []) : []
)
const selected = computed(() =>
  materials.value.find((row) => !row.isGroup && row.materialId === selectedId.value)
)
const batchRows = computed(() =>
  !view.value || !selected.value
    ? []
    : selected.value.demands.flatMap((demand: any) =>
        view
          .value!.batches.filter((batch) => sameId(batch.materialId, demand.materialId))
          .map((batch) => ({
            ...batch,
            demand,
            key: draftKey(demand.demandItemId, batch.stockItemId),
          }))
      )
)
const hasRolls = computed(() => batchRows.value.some((row: any) => !!row.rollNo))
const assigned = (materialId: string) =>
  Number(
    (
      view.value?.demands
        .filter((d) => sameId(d.materialId, materialId))
        .reduce(
          (sum, d) =>
            sum +
            (view.value?.batches.reduce(
              (q, b) => q + Number(draft.value[draftKey(d.demandItemId, b.stockItemId)] || 0),
              0
            ) || 0),
          0
        ) || 0
    ).toFixed(4)
  )
const stockTotal = (materialId: string) =>
  Number(
    (
      view.value?.batches
        .filter((b) => sameId(b.materialId, materialId))
        .reduce((sum, b) => sum + Number(b.quantity), 0) || 0
    ).toFixed(4)
  )
const limit = (row: any) => (view.value ? batchLimit(row.demand, row, draft.value, view.value) : 0)
const batchTip = (materialId: string) =>
  view.value?.batches
    .filter(
      (b) =>
        sameId(b.materialId, materialId) &&
        view.value?.demands.some(
          (d) => Number(draft.value[draftKey(d.demandItemId, b.stockItemId)]) > 0
        )
    )
    .map((b) => `${b.supplierName || '未知供应商'} / ${b.batchNo} / ${b.rollNo || ''}`)
    .join('；') || '未分配'
function selectMaterial(row: any) {
  if (row && !row.isGroup) selectedId.value = row.materialId
}
function apply(data: PickAllocationView) {
  view.value = data
  draft.value = allocationDraft(data)
  initial.value = JSON.stringify(draft.value)
  if (!materials.value.some((row) => row.materialId === selectedId.value))
    selectedId.value = materials.value.find((row) => !row.isGroup)?.materialId || ''
}
async function load() {
  loading.value = true
  error.value = ''
  emit('ready', false)
  try {
    const response = await outboundApi.getAllocation(String(props.outboundId))
    if (!response.data) throw new Error('分配数据为空')
    apply(response.data)
  } catch (e: any) {
    error.value = e?.message || '读取分配失败'
    view.value = null
  } finally {
    loading.value = false
  }
}
watch(() => props.outboundId, load, { immediate: true })
watch(
  [dirty, view],
  () =>
    emit(
      'ready',
      !!view.value &&
        !dirty.value &&
        !view.value.unknownSupplier &&
        view.value.status === OutboundOrderStatusEnum.APPROVED.value &&
        view.value.demands.every((d) =>
          d.allocations.every((a) => Number(a.quantity) === 0 || a.stockItemId != null)
        )
    ),
  { deep: true }
)
async function reallocate() {
  try {
    await ElMessageBox.confirm('重新按 FIFO 分配将覆盖本单未发料的分配结果，继续？', '重新分配')
    busy.value = true
    await outboundApi.allocate(String(props.outboundId))
    await load()
    emit('changed')
    ElMessage.success('重新分配完成')
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || '重新分配失败')
  } finally {
    busy.value = false
  }
}
function clearAll() {
  draft.value = Object.fromEntries(Object.keys(draft.value).map((key) => [key, 0]))
}
async function save() {
  if (!view.value) return
  try {
    const active = view.value.batches.filter((b) =>
      view.value!.demands.some(
        (d) => Number(draft.value[draftKey(d.demandItemId, b.stockItemId)]) > 0
      )
    )
    if (
      active.some((b) => b.supplierId == null) ||
      materials.value.some(
        (row) =>
          !row.isGroup &&
          new Set(
            active
              .filter((b) => sameId(b.materialId, row.materialId))
              .map((b) => String(b.supplierId))
          ).size > 1
      )
    )
      await ElMessageBox.confirm(
        '所选批次存在跨供应商混批或供应商未知，保存后需处理审批，继续？',
        '混批提示'
      )
    busy.value = true
    const lines = view.value.demands.flatMap((d) =>
      view
        .value!.batches.map((b) => ({
          demandItemId: d.demandItemId,
          stockItemId: b.stockItemId,
          quantity: Number(draft.value[draftKey(d.demandItemId, b.stockItemId)] || 0),
        }))
        .filter((row) => row.quantity > 0)
    )
    const response = await outboundApi.saveAllocation(String(props.outboundId), {
      expectedFingerprint: view.value.fingerprint,
      lines,
    })
    apply(response.data!)
    emit('changed')
    ElMessage.success('分配已保存')
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || '保存分配失败')
  } finally {
    busy.value = false
  }
}
async function review(approved: boolean) {
  if (!view.value) return
  try {
    const { value } = await ElMessageBox.prompt(
      approved ? '请填写混批审批意见' : '请填写驳回原因（必填）',
      approved ? '混批审批通过' : '驳回混批',
      {
        inputValidator: (text) => approved || !!text?.trim() || '必须填写驳回原因',
        inputType: 'textarea',
      }
    )
    busy.value = true
    const response = await outboundApi.reviewMixedBatch(String(props.outboundId), {
      expectedFingerprint: view.value.fingerprint,
      approved,
      reason: value || '',
    })
    apply(response.data!)
    emit('changed')
    ElMessage.success(approved ? '混批审批通过' : '已驳回')
  } catch (e: any) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(e?.message || '审批失败')
  } finally {
    busy.value = false
  }
}
async function submit() {
  if (dirty.value) {
    ElMessage.warning('请先保存分配')
    return
  }
  try {
    await outboundApi.submitApprove(String(props.outboundId))
    await load()
    emit('changed')
  } catch (e: any) {
    ElMessage.error(e?.message || '提交失败')
  }
}
function print(mode: string) {
  window.open(`/print/outbound/${props.outboundId}?mode=${mode}`, '_blank')
}
defineExpose({ reload: load })
</script>
<style scoped>
.allocation-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin: 12px 0;
}
.allocation-totals {
  padding: 12px;
  text-align: right;
}
.shortage {
  color: #d93025;
  font-weight: 600;
}
:deep(.pick-parent-group) {
  --el-table-tr-bg-color: #f0f2f5;
  color: #909399;
}
h4 {
  margin: 18px 0 10px;
}
</style>
