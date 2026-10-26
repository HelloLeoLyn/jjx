<template>
  <div class="material-size-editor">
    <template v-if="parsed">
      <div class="size-fields">
        <el-select :model-value="parts.round" aria-label="尺寸形状" @update:model-value="setRound">
          <el-option label="宽／长／厚" :value="false" />
          <el-option label="直径／厚" :value="true" />
        </el-select>
        <el-input :model-value="parts.width" :aria-label="parts.round ? '直径' : '宽'"
          :placeholder="parts.round ? '直径' : '宽'" @update:model-value="value => setPart('width', value)" />
        <el-input v-if="!parts.round" :model-value="parts.length" aria-label="长" placeholder="长"
          @update:model-value="value => setPart('length', value)" />
        <el-input :model-value="parts.thickness" aria-label="厚" placeholder="厚"
          @update:model-value="value => setPart('thickness', value)" />
        <el-select :model-value="parts.unit" aria-label="尺寸单位" @update:model-value="value => setPart('unit', value)">
          <el-option label="mm" value="mm" /><el-option label="不附单位" value="" />
        </el-select>
      </div>
      <div class="size-preview">{{ modelValue || '未填写尺寸' }}</div>
    </template>
    <template v-else>
      <el-input :model-value="modelValue" aria-label="原尺寸" @update:model-value="setRaw" />
      <div class="size-preview">原尺寸无法拆分，保持原文；可输入宽*长*厚或Φ直径*厚。</div>
    </template>
    <div v-if="error" role="alert" class="size-error">{{ error }}</div>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, watch } from 'vue'
import { composeMaterialSize, emptySizeParts, normalizeMaterialSize, parseMaterialSize } from './materialSize'
import type { MaterialSizeParts } from './materialSize'

const props = defineProps<{ modelValue: string }>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: string): void
  (e: 'valid-change', valid: boolean): void
}>()
const parts = reactive(emptySizeParts())
const parsed = ref(true)
const error = ref('')
let ownValue: string | undefined
watch(() => props.modelValue, value => {
  if (value === ownValue) { ownValue = undefined; return }
  const result = parseMaterialSize(value)
  parsed.value = result !== null
  if (result) Object.assign(parts, result)
  error.value = ''
  emit('valid-change', true)
  // 回显即收敛：可识别尺寸统一为半角 *，不要求用户再改动某个分项才生效。
  if (result) {
    const normalized = normalizeMaterialSize(value)
    if (normalized !== value) {
      ownValue = normalized
      emit('update:modelValue', normalized)
    }
  }
}, { immediate: true })

function publish() {
  try {
    const value = composeMaterialSize(parts)
    error.value = ''
    ownValue = value
    emit('valid-change', true)
    emit('update:modelValue', value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : '尺寸无效'
    emit('valid-change', false)
  }
}
function setPart(key: 'width' | 'length' | 'thickness' | 'unit', value: string) {
  parts[key] = value
  publish()
}
function setRound(value: boolean) {
  // 长度已有值时不静默丢弃它；切换前先由用户清空。
  if (value && parts.length) {
    error.value = '请先清空长度，再切换为圆形尺寸'
    return
  }
  parts.round = value
  publish()
}
function setRaw(value: string) {
  const result = parseMaterialSize(value)
  const valid = result !== null
  if (result) { Object.assign(parts, result); parsed.value = true }
  error.value = valid ? '' : '尺寸格式尚不能拆分，请核对原文'
  ownValue = value
  emit('update:modelValue', value)
  emit('valid-change', valid)
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
