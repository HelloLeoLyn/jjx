<template>
  <div class="bom-entry-tools">
    <div class="trial-toolbar">
      <div class="trial-controls">
        <label>试算产出</label>
        <el-input-number v-model="outputQuantity" :min="0.0001" :max="99999999" :precision="4" :controls="false" size="small" aria-label="试算产出数量" />
        <span class="muted">{{ outputUnit || '份' }}</span>
        <el-checkbox v-model="showConversions" size="small">显示米数／卷数</el-checkbox>
        <el-button link type="primary" @click="advancedOpen = !advancedOpen">
          高级设置{{ activeMinimumCount ? `（${activeMinimumCount}行最低投料）` : '' }}
        </el-button>
      </div>
      <span class="muted">每件不含损耗；总需求为本行参考，含损耗与最低投料。领料以工单汇总为准。</span>
    </div>
    <div v-if="errorMessage" class="preview-error"><el-alert :title="errorMessage" type="warning" :closable="false" show-icon /><el-button link type="primary" @click="retryCounter++">重试试算</el-button></div>
    <slot :resultFor="resultFor" :showConversions="showConversions" :showAdvanced="advancedOpen" :openPitch="openPitch" :canPitch="canPitch" :loading="loading" />
    <section v-if="pitchRow" class="pitch-panel">
      <div class="panel-title"><strong>{{ pitchRow.materialCode || pitchRow.materialName }} · 跳距辅助定额</strong><el-button link @click="closePitch">收起</el-button></div>
      <div class="trial-controls">
        <span>卷宽 {{ pitchMetadata?.widthMm }} mm</span>
        <label>跳距 mm</label>
        <el-input-number v-model="pitchMm" :disabled="readonly" :min="0.01" :precision="2" :controls="false" size="small" aria-label="跳距" />
        <label>每跳产出 PCS</label>
        <el-input-number v-model="pcsPerJump" :disabled="readonly" :min="1" :precision="0" :controls="false" size="small" aria-label="每跳产出" />
        <el-button type="primary" size="small" :disabled="readonly || !pitchResult?.baseQtyStorableInBom || pitchLoading" @click="applyPitch">确认采用定额</el-button>
      </div>
      <p v-if="pitchResult" class="pitch-result">按 {{ outputQuantity }} {{ outputUnit }}，理论投入 <strong>{{ pitchResult.displayConsumption }} {{ pitchResult.unit }}</strong>（不含额外损耗、最低投料）</p>
      <p v-if="pitchResult && !pitchResult.baseQtyStorableInBom" class="muted">{{ pitchResult.baseQtyStorageNote || '此比例不能直接按现有精度保存，请调整定额表达；不自动舍入。' }}</p>
      <p v-if="pitchError" class="pitch-error">{{ pitchError }}</p>
      <p class="muted">已有跳距保留原值；片长只作新方案预填。修改跳距不会自动覆盖人工比例，确认采用后才回填模数和基数。</p>
    </section>
    <section v-if="advancedOpen" class="advanced-panel">
      <div v-for="row in rows" :key="keyOf(row)" class="advanced-row">
        <strong>{{ row.materialCode || row.materialName || '未选物料' }}</strong>
        <label>最低投料</label>
        <el-input-number :model-value="row.minIssueQty ?? 0" :disabled="readonly" :min="0" :precision="4" :controls="false" size="small" @update:model-value="value => patchRow(row, { minIssueQty: value ?? 0 })" />
        <template v-if="continuous(row)">
          <label>参考卷长 m</label>
          <el-input-number :model-value="referenceLengths[referenceKeyOf(row)] ?? resultFor(row)?.referenceRollLengthM ?? undefined" :min="0.01" :precision="2" :controls="false" size="small" @update:model-value="value => setReferenceLength(row, value)" />
          <el-button v-if="canPitch(row)" link type="primary" @click="openPitch(row)">跳距辅助定额</el-button>
        </template>
        <span v-if="row.split && !continuous(row)" class="muted">原跳距：{{ row.split }}</span>
        <template v-if="unitDiffers(row)">
          <span class="muted">当前BOM {{ row.unit }}；档案 {{ archiveUnitFor(row) }}。历史值保留，重新选料后带入档案单位。</span>
          <label>换算系数</label>
          <el-input-number :model-value="row.unitFactor ?? undefined" :disabled="readonly" :min="0" :precision="8" :controls="false" size="small" @update:model-value="value => patchRow(row, { unitFactor: value ?? null })" />
          <el-input :model-value="row.unitFactorBasis" :disabled="readonly" maxlength="200" size="small" placeholder="换算依据" @update:model-value="value => patchRow(row, { unitFactorBasis: value })" />
          <el-input :model-value="row.unitFactorSource" :disabled="readonly" maxlength="100" size="small" placeholder="依据来源（选填）" @update:model-value="value => patchRow(row, { unitFactorSource: value })" />
        </template>
      </div>
      <p class="muted">折叠设置不清空原值。卷数按参考长度估算，实际发料以所选整卷或余卷为准。</p>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { previewBomConsumption, previewPitchRequirement } from '@/api/production/bomConsumption'
import type { BomConsumptionResult, PitchPreviewResult } from '@/api/production/bomConsumption'
import type { EngineeringBomItem } from '@/types/product/bom'

const props = withDefaults(defineProps<{
  rows: EngineeringBomItem[]
  readonly?: boolean
  outputUnit?: string
  outputLengthMm?: number | null
}>(), { readonly: false, outputUnit: '', outputLengthMm: null })
const emit = defineEmits<{ (event: 'change'): void }>()
defineSlots<{ default(props: {
  resultFor: (row: EngineeringBomItem) => BomConsumptionResult | undefined
  showConversions: boolean
  showAdvanced: boolean
  openPitch: (row: EngineeringBomItem) => void
  canPitch: (row: EngineeringBomItem) => boolean
  loading: boolean
}): unknown }>()
const outputQuantity = ref<number | undefined>(100)
const showConversions = ref(false)
const advancedOpen = ref(false)
const referenceLengths = ref<Record<string, number | null>>({})
const results = ref<Record<string, BomConsumptionResult>>({})
const metadata = new Map<number, BomConsumptionResult>()
const loading = ref(false)
const errorMessage = ref('')
const retryCounter = ref(0)
let generation = 0, disposed = false
let timer: ReturnType<typeof setTimeout> | undefined
const keyOf = (row: EngineeringBomItem) => row.itemId == null ? `new-${props.rows.indexOf(row)}` : String(row.itemId)
const referenceKeyOf = (row: EngineeringBomItem) => `${keyOf(row)}|${row.materialId}|${row.unit}`
const resultFor = (row: EngineeringBomItem) => results.value[keyOf(row)]
const metadataFor = (row: EngineeringBomItem) => resultFor(row) ?? metadata.get(Number(row.materialId))
const continuous = (row: EngineeringBomItem) => ['M', 'M2', '㎡', '米', '平方', '平方米'].includes(String(row.unit).trim().toUpperCase())
const canPitch = (row: EngineeringBomItem) => props.outputUnit === 'PCS' && continuous(row) && Boolean(metadataFor(row)?.pitchSupported)
const archiveUnitFor = (row: EngineeringBomItem) => metadataFor(row)?.archiveUnit || row.archiveUnit
const unitDiffers = (row: EngineeringBomItem) => Boolean(archiveUnitFor(row) && archiveUnitFor(row) !== row.unit)
const activeMinimumCount = computed(() => props.rows.filter(row => Number(row.minIssueQty) > 0).length)
const requestRows = () => props.rows.map(row => ({
  key: keyOf(row), materialId: Number(row.materialId) > 0 ? Number(row.materialId) : null,
  unit: row.unit || '', baseQty: row.baseQty ?? null, moduleQty: row.moduleQty ?? null,
  lossRate: row.lossRate ?? 0, minIssueQty: row.minIssueQty ?? 0,
  referenceRollLengthM: referenceLengths.value[referenceKeyOf(row)] ?? null,
  consumptionMode: row.consumptionMode ?? null,
  pitchMm: row.pitchMm ?? null,
  pcsPerPitch: row.pcsPerPitch ?? 1,
  unitConsumption: row.unitConsumption ?? null,
}))
const signature = computed(() => JSON.stringify({ retry: retryCounter.value, outputQuantity: outputQuantity.value, outputUnit: props.outputUnit, rows: requestRows() }))
watch(signature, () => {
  const current = ++generation
  if (timer) clearTimeout(timer)
  results.value = {}
  errorMessage.value = ''
  const quantity = Number(outputQuantity.value)
  if (!props.rows.length || !Number.isFinite(quantity) || quantity <= 0) { loading.value = false; return }
  loading.value = true
  const payload = { outputQuantity: quantity, outputUnit: props.outputUnit, rows: requestRows() }
  timer = setTimeout(async () => {
    try {
      const response = await previewBomConsumption(payload)
      if (disposed || generation !== current) return
      if (response.data?.readOnly !== true || !Array.isArray(response.data.rows)) throw new Error('未返回有效的只读试算结果')
      results.value = Object.fromEntries(response.data.rows.map(row => [row.key, row]))
      for (const row of payload.rows) {
        const result = results.value[row.key]
        if (row.materialId && result) metadata.set(row.materialId, result)
      }
    } catch (error) {
      if (disposed || generation !== current) return
      errorMessage.value = `试算暂不可用，草稿已保留：${error instanceof Error ? error.message : '请稍后重试'}`
    } finally { if (!disposed && generation === current) loading.value = false }
  }, 250)
}, { immediate: true, flush: 'sync' })
function patchRow(row: EngineeringBomItem, patch: Partial<EngineeringBomItem>) {
  if (props.readonly || !props.rows.includes(row)) return
  Object.assign(row, patch, { quantityStale: true })
  emit('change')
}
function setReferenceLength(row: EngineeringBomItem, value: number | undefined | null) {
  referenceLengths.value[referenceKeyOf(row)] = value ?? null
}
const pitchKey = ref<string | null>(null)
const pitchRow = computed(() => props.rows.find(row => keyOf(row) === pitchKey.value))
const pitchMetadata = computed(() => pitchRow.value ? metadataFor(pitchRow.value) : undefined)
const pitchMm = ref<number | undefined>()
const pcsPerJump = ref<number | undefined>(1)
const pitchResult = ref<PitchPreviewResult | null>(null)
const pitchLoading = ref(false)
const pitchError = ref('')
let pitchGeneration = 0
let pitchTimer: ReturnType<typeof setTimeout> | undefined
function openPitch(row: EngineeringBomItem) {
  if (!canPitch(row)) return
  pitchKey.value = keyOf(row)
  const stored = Number.parseFloat(row.split || '')
  pitchMm.value = Number.isFinite(stored) && stored > 0 ? stored : Number(props.outputLengthMm) > 0 ? Number(props.outputLengthMm) : undefined
  pcsPerJump.value = 1
}
watch(() => props.outputLengthMm, value => {
  if (pitchRow.value && pitchMm.value == null && Number(value) > 0) pitchMm.value = Number(value)
})
function closePitch() { pitchKey.value = null; pitchResult.value = null; ++pitchGeneration; if (pitchTimer) clearTimeout(pitchTimer) }
const pitchSignature = computed(() => JSON.stringify({ key: pitchKey.value, materialId: pitchRow.value?.materialId,
  unit: pitchRow.value?.unit, outputUnit: props.outputUnit, width: pitchMetadata.value?.widthMm,
  pitch: pitchMm.value, perJump: pcsPerJump.value, quantity: outputQuantity.value }))
watch(pitchSignature, () => {
  const current = ++pitchGeneration
  if (pitchTimer) clearTimeout(pitchTimer)
  pitchResult.value = null; pitchError.value = ''; pitchLoading.value = false
  const row = pitchRow.value, width = Number(pitchMetadata.value?.widthMm), pitch = Number(pitchMm.value)
  const perJump = Number(pcsPerJump.value), quantity = Number(outputQuantity.value)
  if (!row || !canPitch(row) || !(width > 0 && pitch > 0) || !Number.isInteger(perJump) || perJump <= 0 || !Number.isInteger(quantity) || quantity <= 0) return
  pitchLoading.value = true
  const params = { widthMm: width, pitchMm: pitch, pcsPerJump: perJump, inputUnit: row.unit, outputPcs: quantity }
  pitchTimer = setTimeout(async () => {
    try {
      const response = await previewPitchRequirement(params)
      if (!disposed && current === pitchGeneration) pitchResult.value = response.data
    } catch (error) {
      if (!disposed && current === pitchGeneration) pitchError.value = error instanceof Error ? error.message : '跳距试算失败'
    } finally { if (!disposed && current === pitchGeneration) pitchLoading.value = false }
  }, 250)
}, { flush: 'sync' })
function applyPitch() {
  const row = pitchRow.value, result = pitchResult.value
  if (!row || props.readonly || pitchLoading.value || !result?.baseQtyStorableInBom || result.baseQtySuggested == null) return
  patchRow(row, { moduleQty: result.moduleQtySuggested, baseQty: result.baseQtySuggested, split: `${pitchMm.value}mm` })
}
onBeforeUnmount(() => {
  disposed = true; ++generation; ++pitchGeneration
  if (timer) clearTimeout(timer)
  if (pitchTimer) clearTimeout(pitchTimer)
})
defineExpose({ resultFor, outputQuantity, openPitch, applyPitch, canPitch, showConversions, advancedOpen })
</script>

<style scoped>
.bom-entry-tools{min-width:0}.trial-toolbar,.trial-controls,.panel-title,.advanced-row{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.trial-toolbar{justify-content:space-between;padding:10px 0}.trial-controls{font-size:13px}.trial-controls :deep(.el-input-number){width:100px}.muted{font-size:12px;color:var(--el-text-color-secondary);line-height:1.7}.preview-error{margin-bottom:10px}.pitch-panel,.advanced-panel{padding:14px 16px;margin-top:10px;background:var(--el-fill-color-light);border:1px solid var(--el-border-color-lighter);border-radius:6px}.panel-title{justify-content:space-between;margin-bottom:12px;font-size:13px}.pitch-result{font-size:13px}.pitch-result strong{color:var(--el-color-primary);margin:0 8px}.pitch-error{font-size:12px;color:var(--el-color-danger)}.advanced-row{padding:9px 0;font-size:12px}.advanced-row strong{min-width:110px}.advanced-row :deep(.el-input-number){width:105px}.advanced-row :deep(.el-input){max-width:210px}.advanced-row label{white-space:nowrap}@media(max-width:800px){.trial-toolbar{align-items:flex-start}.trial-controls{gap:8px}}
</style>
