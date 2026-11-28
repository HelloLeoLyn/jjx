<template>
  <div v-if="sources.length">
    <p>选择当前工序使用的样式；油墨色号可独立修改。</p>
    <div v-for="item in available" :key="key(item)" style="display:flex;gap:10px;margin:6px 0;align-items:center">
      <el-checkbox :model-value="!!selected(item)" :disabled="readonly" @update:model-value="checked => toggle(item, !!checked)">{{ item.filmNo }} / {{ item.styleNo }} {{ item.name }}</el-checkbox>
      <el-input v-if="selected(item)" :model-value="selected(item)?.inkColor ?? item.inkColor" :disabled="readonly" maxlength="100" style="width:160px" placeholder="工序油墨色号" @update:model-value="v => updateInk(item, v)" />
    </div>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import type { PlateSource, ProcessStyle } from './printStyles'
const props = defineProps<{ sources: PlateSource[]; modelValue?: string; readonly?: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
const available = computed(() => {
  const rows = props.sources.flatMap(source => source.styles.map(style => ({ ...style, styleNo: style.styleNo!, versionId: source.versionId, filmNo: source.filmNo })))
  return [...new Map(rows.map(row => [key(row), row])).values()]
})
const requirements = computed(() => JSON.parse(props.modelValue || '{}'))
const key = (item: ProcessStyle) => `${item.versionId}:${item.styleNo}`
function selected(item: ProcessStyle): ProcessStyle | undefined { return (requirements.value.styles || []).find((row: ProcessStyle) => key(row) === key(item)) }
function toggle(item: ProcessStyle, checked: boolean) {
  const styles = (requirements.value.styles || []).filter((row: ProcessStyle) => key(row) !== key(item))
  if (checked) styles.push({ versionId: item.versionId, styleNo: item.styleNo, inkColor: item.inkColor })
  emit('update:modelValue', JSON.stringify({ ...requirements.value, styles }))
}
function updateInk(item: ProcessStyle, inkColor: string) {
  const styles = (requirements.value.styles || []).map((row: ProcessStyle) => key(row) === key(item) ? { ...row, inkColor } : row)
  emit('update:modelValue', JSON.stringify({ ...requirements.value, styles }))
}
</script>
