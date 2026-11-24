<template>
  <el-table :data="items" border class="purchase-entry-table" :row-key="rowKey">
    <el-table-column type="expand" width="36">
      <template #default="{ row, $index }">
        <div class="entry-details">
          <div class="entry-basis">
            <template v-if="usesRoll(row)">
              <label>每卷长度(m)</label>
              <el-input-number v-model="row.rollLengthM" :disabled="disabled || row.defaultLoading" :min="0.0001" :precision="4" :controls="false" @change="emit('pricing', $index)" />
              <label>卷宽(mm)</label>
              <el-input-number v-model="row.rollWidthMm" :disabled="disabled || row.defaultLoading || Number(row.archiveWidthMm) > 0" :min="0.01" :precision="2" :controls="false" @change="emit('pricing', $index)" />
            </template>
            <template v-else-if="usesPieceArea(row)">
              <label>每片宽度(mm)</label>
              <el-input-number v-model="row.pieceWidthMm" :disabled="disabled || row.defaultLoading || Number(row.archiveWidthMm) > 0" :min="0.01" :precision="2" :controls="false" @change="emit('pricing', $index)" />
              <label>每片长度(mm)</label>
              <el-input-number v-model="row.pieceLengthMm" :disabled="disabled || row.defaultLoading || Number(row.archiveLengthMm) > 0" :min="0.01" :precision="2" :controls="false" @change="emit('pricing', $index)" />
            </template>
            <template v-else-if="row.purchaseUnit !== row.priceUnit && [row.purchaseUnit, row.priceUnit].includes('M')">
              <label>宽度(mm)</label>
              <el-input-number v-model="row.rollWidthMm" :disabled="disabled || row.defaultLoading || Number(row.archiveWidthMm) > 0" :min="0.01" :precision="2" :controls="false" @change="emit('pricing', $index)" />
            </template>
            <template v-if="measurementMode(row) === MeasurementModeEnum.PIECE_WEIGHT.value">
              <span>单重(g)</span><el-input-number :disabled="disabled || row.defaultLoading" :model-value="row.measurementActual?.unitWeightG" :min="0" :precision="8" :controls="false" @update:model-value="value => updateActual(row, 'unitWeightG', value)" @change="emit('pricing', $index)" />
            </template>
            <template v-if="[MeasurementModeEnum.AREA_WEIGHT.value, MeasurementModeEnum.ROLL_WEIGHT.value, MeasurementModeEnum.PIECE_AREA_WEIGHT.value].includes(measurementMode(row) as any)">
              <span>克重(g/㎡)</span><el-input-number :disabled="disabled || row.defaultLoading" :model-value="row.measurementActual?.grammage" :min="0" :precision="4" :controls="false" @update:model-value="value => updateActual(row, 'grammage', value)" @change="emit('pricing', $index)" />
            </template>
            <template v-if="measurementMode(row) === MeasurementModeEnum.MEASURED_WEIGHT.value">
              <span>对应实物数量</span><el-input-number :disabled="disabled || row.defaultLoading" :model-value="row.measurementActual?.physicalQuantity" :min="0" :precision="4" :controls="false" @update:model-value="value => updateActual(row, 'physicalQuantity', value)" @change="emit('pricing', $index)" />
              <span>实际净重(KG)</span><el-input-number :disabled="disabled || row.defaultLoading" :model-value="row.measurementActual?.actualWeightKg" :min="0" :precision="4" :controls="false" @update:model-value="value => updateActual(row, 'actualWeightKg', value)" @change="emit('pricing', $index)" />
            </template>
            <el-checkbox v-model="row.priceTaxInclusive" :disabled="disabled || row.defaultLoading" @change="emit('pricing', $index)">单价含税</el-checkbox>
            <label>税率(%)</label>
            <el-input-number v-model="row.taxRate" :disabled="disabled || row.defaultLoading" :min="0" :max="100" :precision="2" :controls="false" @change="emit('pricing', $index)" />
            <span>库存基本数量：{{ row.pricingLoading || row.pricingError ? '—' : entryNumber(row.quantity) }} {{ unitLabel(row.unit) }}</span>
            <span>折合单价：{{ row.equivalentUnitPrice == null || row.pricingLoading ? '—' : entryPrice(row.equivalentUnitPrice) + ' ' + (currency || 'CNY') + '/㎡' }}</span>
          </div>
          <div class="entry-basis">
            <el-button v-if="row.defaultError" link type="primary" :disabled="disabled || row.defaultLoading" @click="emit('retryDefault', $index)">重新读取采购约定</el-button>
            <span class="muted">{{ row.defaultConfigured ? '已带出该供应商的采购约定；本单可以调整。' : '未设置采购约定，可在本单选择单位；不会按规格猜采购方式。' }}</span>
            <el-button v-hasPermi="['purchase:supplier:edit']" link type="primary" :disabled="disabled || !row.materialId || row.defaultLoading" @click="emit('saveDefault', $index)">设为本供应商默认单位</el-button>
          </div>
          <el-input v-model="row.purchaseRemark" :disabled="disabled || row.defaultLoading" type="textarea" :rows="2" maxlength="2000" show-word-limit placeholder="采购/加工/交货要求，例如分切规格、切片数量、余料交回；不参与金额合计" />
          <PurchaseProcessingEditor v-model="row.processingPlan" :item="row" :disabled="disabled || row.defaultLoading" @change="emit('pricing', $index)" />
          <p v-if="row.inspectionRemark" class="muted">原检验备注：{{ row.inspectionRemark }}</p>
          <p class="muted">档案已确定尺寸只读，换规格请重新选料；每卷预估长度及本单确认的换算依据随订单保留；收货按实收核算，普通采购说明不自动生成加工记录；结构化加工要求随采购单审核，收货按实际切片和余卷登记。</p>
        </div>
      </template>
    </el-table-column>
    <el-table-column label="序号" type="index" width="48" align="center" />
    <el-table-column label="品名 / 物料" min-width="220">
      <template #default="{ row, $index }">
        <MaterialSelector
          :model-value="row.materialId"
          value-type="materialId"
          :options="materials"
          label-field="supplierMaterialName"
          :disabled="disabled || materialLocked || !supplierId"
          :loading="materialLoading"
          clearable
          size="small"
          :min-keyword-length="0"
          :placeholder="supplierId ? '搜索名称 / 编码 / 规格' : '请先选择供应商'"
          @search="keyword => emit('search', keyword)"
          @change="id => emit('materialChange', id === null ? undefined : id, $index)"
        />
        <div v-if="row.materialId" class="material-subtitle"><el-button link type="primary" @click="emit('view', row)">{{ row.materialCode }}</el-button><span>{{ row.materialName }}</span></div>
        <p v-if="row.pricingError || row.defaultError" class="entry-error">{{ row.defaultError ? '采购约定读取失败，请展开明细重新读取' : row.pricingError }}</p>
      </template>
    </el-table-column>
    <el-table-column label="规格" min-width="150">
      <template #default="{ row }"><span>{{ entrySpecification(row) }}</span></template>
    </el-table-column>
    <el-table-column label="采购数量" width="110">
      <template #default="{ row, $index }"><el-input-number v-model="row.purchaseQuantity" :disabled="disabled || row.defaultLoading" :min="0" :precision="['ROLL','PCS','SHEET'].includes(row.purchaseUnit || '') ? 0 : 4" :controls="false" aria-label="采购数量" @change="emit('quantityChange', $index)" /></template>
    </el-table-column>
    <el-table-column label="采购单位" width="90">
      <template #default="{ row, $index }"><el-select v-model="row.purchaseUnit" :disabled="disabled || row.defaultLoading" aria-label="采购单位" @change="emit('unitChange', $index)"><el-option v-for="unit in optionsFor(row)" :key="unit.value" :label="unit.label" :value="unit.value" /></el-select></template>
    </el-table-column>
    <el-table-column label="计价数量" width="115" align="right">
      <template #default="{ row }"><strong>{{ row.pricingLoading || row.pricingError ? '—' : entryNumber(row.priceQuantity, 12) }}</strong></template>
    </el-table-column>
    <el-table-column label="报价单位" width="90">
      <template #default="{ row, $index }"><el-select v-model="row.priceUnit" :disabled="disabled || row.defaultLoading" aria-label="报价单位" @change="emit('pricing', $index)"><el-option v-for="unit in optionsFor(row)" :key="unit.value" :label="unit.label" :value="unit.value" /></el-select><small class="muted">{{ currency || 'CNY' }}/{{ unitLabel(row.priceUnit) }}</small></template>
    </el-table-column>
    <el-table-column label="单价" width="115">
      <template #default="{ row, $index }"><el-input-number v-model="row.unitPrice" :disabled="disabled || row.defaultLoading" :min="0" :precision="4" :controls="false" aria-label="单价" @change="emit('pricing', $index)" /><small class="muted">{{ row.priceTaxInclusive ? '含税' : '未税' }}</small></template>
    </el-table-column>
    <el-table-column label="金额（报价口径）" width="120" align="right">
      <template #default="{ row }">{{ row.pricingLoading || row.pricingError ? '—' : Number(row.quotedAmount ?? row.amount ?? 0).toFixed(2) }}</template>
    </el-table-column>
    <el-table-column label="操作" width="65" fixed="right">
      <template #default="{ $index }"><el-button link type="danger" :disabled="disabled || materialLocked || items.length <= 1" @click="emit('remove', $index)">移除</el-button></template>
    </el-table-column>
  </el-table>
</template>
<script setup lang="ts">
import PurchaseProcessingEditor from './PurchaseProcessingEditor.vue'
import MaterialSelector from '@/components/Selector/MaterialSelector.vue'
import type { PurchaseEntryItem, PurchaseUnitOption } from '@/api/purchase/orderEntry'
import { entryNumber, entryPrice, entrySpecification } from '@/api/purchase/orderEntry'
import type { InventoryMaterial } from '@/types/inventory/material'
import { MeasurementModeEnum } from '@/enums/inventory/MeasurementEnum'
import type { MeasurementPolicy } from '@/types/inventory/measurement'
const props = defineProps<{ items: PurchaseEntryItem[]; materials: InventoryMaterial[]; supplierId?: string; materialLoading: boolean; units: PurchaseUnitOption[]; currency?: string; disabled?: boolean; materialLocked?: boolean }>()
const emit = defineEmits<{
  (e: 'materialChange', id: number | undefined, index: number): void
  (e: 'search', keyword: string): void
  (e: 'dropdown', visible: boolean): void
  (e: 'pricing' | 'quantityChange' | 'unitChange' | 'saveDefault' | 'retryDefault' | 'remove', index: number): void
  (e: 'view', row: PurchaseEntryItem): void
}>()
const unitLabel = (code?: string) => props.units.find(u => u.value === code)?.label || code || '—'
const rowKeys = new WeakMap<PurchaseEntryItem, string>()
let nextKey = 0
const rowKey = (row: PurchaseEntryItem) => {
  if (!rowKeys.has(row)) rowKeys.set(row, `purchase-row-${++nextKey}`)
  return rowKeys.get(row)!
}
const optionsFor = (row: PurchaseEntryItem) => props.units.filter(u => !row.allowedUnits || row.allowedUnits.includes(u.value))
const updateActual = (row: PurchaseEntryItem, field: 'physicalQuantity' | 'unitWeightG' | 'grammage' | 'actualWeightKg', value?: number) => { row.measurementActual = { ...row.measurementActual, [field]: value } }
function measurementPolicy(row: PurchaseEntryItem): MeasurementPolicy | undefined {
  if (row.measurementSnapshot) {
    try { return JSON.parse(row.measurementSnapshot).policy } catch { return undefined }
  }
  return row.measurementPolicy || undefined
}
const measurementMode = (row: PurchaseEntryItem) => measurementPolicy(row)?.mode
const usesRoll = (row: PurchaseEntryItem) => measurementPolicy(row)?.physicalUnit === 'ROLL' || row.purchaseUnit === 'ROLL' || row.priceUnit === 'ROLL'
const usesPieceArea = (row: PurchaseEntryItem) => [row.unit, row.purchaseUnit, row.priceUnit].some(u => u === 'PCS' || u === 'SHEET') && [row.unit, row.purchaseUnit, row.priceUnit].includes('㎡')
</script>
<style scoped>
.purchase-entry-table{margin-bottom:16px}.purchase-entry-table :deep(.el-input-number),.purchase-entry-table :deep(.el-select){width:100%}.entry-details{padding:12px 20px}.entry-basis{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin-bottom:12px;font-size:13px}.entry-basis :deep(.el-input-number){width:120px}.material-subtitle{display:flex;gap:8px;align-items:center;flex-wrap:wrap;font-size:12px;color:var(--el-text-color-secondary)}.material-subtitle .el-button{font-size:12px}.entry-error{color:var(--el-color-danger);font-size:12px;white-space:normal;margin:5px 0}.material-option{display:flex;gap:12px;justify-content:space-between}.material-option small,.muted{font-size:12px;color:var(--el-text-color-secondary)}
</style>
