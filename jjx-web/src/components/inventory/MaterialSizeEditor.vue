<template>
  <div class="material-size-editor">
    <div class="size-fields">
      <el-select :model-value="round" aria-label="尺寸形状" @update:model-value="setRound">
        <el-option label="宽／长／厚" :value="false" />
        <el-option label="直径／厚" :value="true" />
      </el-select>
      <el-input :model-value="parts.widthMm" :aria-label="round ? '直径' : '宽'" inputmode="decimal"
        :placeholder="round ? '直径' : '宽'" @update:model-value="value => setPart('widthMm', value)" />
      <el-input v-if="!round" :model-value="parts.lengthMm" aria-label="长" placeholder="长" inputmode="decimal"
        @update:model-value="value => setPart('lengthMm', value)" />
      <el-input :model-value="parts.thicknessMm" aria-label="厚" placeholder="厚" inputmode="decimal"
        @update:model-value="value => setPart('thicknessMm', value)" />
      <el-select :model-value="parts.sizeUnit" aria-label="尺寸单位" @update:model-value="value => setPart('sizeUnit', value)">
        <el-option label="mm" value="mm" /><el-option label="cm" value="cm" /><el-option label="m" value="m" />
      </el-select>
    </div>
    <div class="size-preview">{{ preview || '未填写尺寸' }}</div>
    <div v-if="error" role="alert" class="size-error">{{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { emptyMaterialSize, renderMaterialDimensions, validateMaterialDimensions } from './materialSize'
import type { MaterialSizeValue } from './materialSize'

const props = defineProps<{ modelValue: MaterialSizeValue }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: MaterialSizeValue): void
  (e: 'valid-change', valid: boolean): void
}>()
const parts = reactive({ widthMm: '', lengthMm: '', thicknessMm: '', sizeUnit: 'mm' })
const round = ref(false)
const error = ref('')
let ownValue: MaterialSizeValue | undefined
const preview = computed(() => {
  if (error.value) return ''
  return renderMaterialDimensions(numericValue(), round.value)
})
watch(() => props.modelValue, value => {
  if (ownValue && Object.keys(ownValue).every(k => value[k as keyof MaterialSizeValue] === ownValue![k as keyof MaterialSizeValue])) {
    ownValue = undefined
    return
  }
  const dimensions = { ...emptyMaterialSize(), ...value }
  for (const key of ['widthMm', 'lengthMm', 'thicknessMm'] as const) parts[key] = dimensions[key] == null ? '' : String(dimensions[key])
  parts.sizeUnit = dimensions.sizeUnit
  round.value = dimensions.widthMm != null && dimensions.lengthMm == null && dimensions.thicknessMm != null
  check(dimensions)
}, { immediate: true, deep: true })

function numericValue(): MaterialSizeValue {
  const number = (text: string) => !text.trim() ? null : /^\d+(?:\.\d+)?$/.test(text.trim()) ? Number(text) : NaN
  return { widthMm: number(parts.widthMm), lengthMm: number(parts.lengthMm), thicknessMm: number(parts.thicknessMm), sizeUnit: parts.sizeUnit }
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
function setPart(key: keyof typeof parts, value: string) {
  parts[key] = value
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
.size-preview { color: var(--el-text-color-secondary); font-size: 12px; }
.size-error { color: var(--el-color-danger); font-size: 12px; }
</style>
