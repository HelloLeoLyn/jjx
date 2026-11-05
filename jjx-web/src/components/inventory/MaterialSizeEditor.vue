<template>
  <div class="material-size-editor">
    <div class="size-fields">
      <el-select :model-value="round" aria-label="尺寸形状" @update:model-value="setRound">
        <el-option label="宽／长／厚" :value="false" />
        <el-option label="直径／厚" :value="true" />
      </el-select>
      <div v-for="key in visibleKeys" :key="key" class="dimension-input">
        <el-input :model-value="parts[key]" :aria-label="label(key)" :placeholder="label(key)" inputmode="decimal"
          @update:model-value="value => setPart(key, value)" />
        <el-select :model-value="units[key]" :aria-label="`${label(key)}单位`"
          @update:model-value="value => setUnit(key, value)">
          <el-option v-for="unit in dimensionUnits" :key="unit" :label="unit" :value="unit" />
        </el-select>
      </div>
    </div>
    <div class="size-preview">{{ preview || '未填写尺寸' }}</div>
    <div v-if="error" role="alert" class="size-error">{{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { dimensionField, displayDimensionOf, emptyMaterialSize, parseMaterialDimension, renderMaterialDimensions, validateMaterialDimensions } from './materialSize'
import type { MaterialDimensionFields, MaterialSizeValue } from './materialSize'
import type { MaterialDimensionUnit } from '@/types/inventory/materialMaster'

const props = defineProps<{ modelValue: MaterialSizeValue; dimensionFields?: MaterialDimensionFields }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: MaterialSizeValue): void
  (e: 'valid-change', valid: boolean): void
}>()
type DimensionKey = keyof MaterialSizeValue
const keys: DimensionKey[] = ['widthMm', 'lengthMm', 'thicknessMm']
const dimensionUnits: MaterialDimensionUnit[] = ['mm', 'cm', 'm', 'μm']
const parts = reactive({ widthMm: '', lengthMm: '', thicknessMm: '' })
const units = reactive<Record<DimensionKey, MaterialDimensionUnit>>({ widthMm: 'mm', lengthMm: 'mm', thicknessMm: 'mm' })
const source = reactive(emptyMaterialSize())
const round = ref(false)
const error = ref('')
const visibleKeys = computed(() => keys.filter(key => !round.value || key !== 'lengthMm'))
const label = (key: DimensionKey) => ({ widthMm: round.value ? '直径' : '宽', lengthMm: '长', thicknessMm: '厚' })[key]
let ownValue: MaterialSizeValue | undefined
const preview = computed(() => {
  if (error.value) return ''
  return renderMaterialDimensions(numericValue(), round.value, props.dimensionFields)
})
watch(() => [props.modelValue, props.dimensionFields] as const, ([value], previous) => {
  if (previous && previous[1] === props.dimensionFields && ownValue && Object.keys(ownValue).every(k => value[k as keyof MaterialSizeValue] === ownValue![k as keyof MaterialSizeValue])) {
    ownValue = undefined
    return
  }
  ownValue = undefined
  const dimensions = { ...emptyMaterialSize(), ...value }
  for (const key of keys) {
    source[key] = dimensions[key]
    const field = dimensionField(props.dimensionFields || [], key)
    units[key] = field?.unit || 'mm'
    const valueMm = dimensions[key]
    parts[key] = valueMm == null ? '' : displayDimensionOf(valueMm, units[key], field?.scale)
  }
  round.value = dimensions.widthMm != null && dimensions.lengthMm == null && dimensions.thicknessMm != null
  check(dimensions)
}, { immediate: true, deep: true })

function numericValue(): MaterialSizeValue {
  return { ...source }
}
function check(value: MaterialSizeValue) {
  try {
    validateMaterialDimensions(value)
    error.value = ''
    emit('valid-change', true)
    return true
  } catch (e) {
    error.value = e instanceof Error ? e.message : '尺寸无效'
    emit('valid-change', false)
    return false
  }
}
function publish() {
  const value = numericValue()
  if (!check(value)) return
  ownValue = value
  emit('update:modelValue', value)
}
function setPart(key: DimensionKey, value: string) {
  if (parts[key] === value) return
  parts[key] = value
  source[key] = parseMaterialDimension(value, units[key])
  publish()
}
function setUnit(key: DimensionKey, unit: MaterialDimensionUnit) {
  if (units[key] === unit) return
  units[key] = unit
  source[key] = parseMaterialDimension(parts[key], unit)
  publish()
}
function setRound(value: boolean) {
  if (value && parts.lengthMm) {
    error.value = '请先清空长度，再切换为圆形尺寸'
    return
  }
  round.value = value
  publish()
}
</script>

<style scoped>
.material-size-editor { width: 100%; }
.size-fields { display: flex; gap: 8px; }
.size-fields > * { flex: 1; min-width: 65px; }
.size-fields > :first-child { flex: 1.8; }
.dimension-input { display: flex; flex: 2; gap: 4px; min-width: 0; }
.dimension-input .el-input { flex: 1; min-width: 0; }
.dimension-input .el-select { width: 76px; flex: 0 0 76px; }
.size-preview { color: var(--el-text-color-secondary); font-size: 12px; }
.size-error { color: var(--el-color-danger); font-size: 12px; }
</style>
