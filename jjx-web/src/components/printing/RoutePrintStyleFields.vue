<template>
  <div v-loading="loading" class="route-style-fields">
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link type="primary" @click="load">重试</el-button></el-alert>
    <template v-if="available.length">
      <p v-if="multipleSaved" class="tip">原记录包含多个样式，点选后改为一个样式。</p>
      <el-radio-group :model-value="selectedKey" class="style-options" aria-label="印刷样式（单选）" @update:model-value="value => choose(String(value))">
        <el-radio v-for="item in available" :key="key(item)" :value="key(item)" border class="style-option">
          <strong>{{ item.name }}</strong>
          <small>{{ item.filmNo }}<template v-if="item.versionNo"> · {{ item.versionNo }}</template> · {{ (selected(item)?.inkColor ?? item.inkColor) || '色号未记录' }}</small>
        </el-radio>
      </el-radio-group>
      <div v-if="activeStyle" class="selection-summary">
        <span>印刷名称：<strong>{{ activeStyle.name }}</strong></span>
        <label>色号<el-input :model-value="selected(activeStyle)?.inkColor ?? activeStyle.inkColor" size="small" maxlength="100" placeholder="样式色号" @update:model-value="updateActiveColor" /></label>
        <span class="unlinked">油墨待关联</span>
      </div>
      <p v-else-if="!multipleSaved" class="tip">点选一个样式，自动带出印刷名称和色号。</p>
    </template>
    <div v-else-if="!loading && !error" class="legacy-fields">
      <p class="tip">{{ plateId ? '该网版未记录样式，暂保留原工序录入。' : '选择网版后会显示菲林样式，并自动带出印刷名称和色号。' }}</p>
      <details :open="!!plateId"><summary>原工序录入</summary><slot name="legacy" /></details>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { printingSearchApi } from '@/api/engineering/printingSearch'
import { readSources, type PlateSource, type ProcessStyle } from './printStyles'

const props = defineProps<{ plateId?: number | null; sourcesJson?: string | null; requirements?: string | null }>()
const emit = defineEmits<{ 'update:requirements': [value: string]; 'update:name': [value: string] }>()
const sources = ref<PlateSource[]>([])
const loading = ref(false), error = ref('')
let generation = 0, hadSelection = false
const key = (style: ProcessStyle) => `${style.versionId}:${style.styleNo}`
const required = computed(() => {
  try { return JSON.parse(props.requirements || '{}') } catch { return {} }
})
const available = computed(() => {
  const rows = sources.value.flatMap(source => source.styles.map(style => ({ ...style, styleNo: style.styleNo!, versionId: source.versionId,
    artworkNo: source.artworkNo, versionNo: source.versionNo, filmNo: source.filmNo })))
  return [...new Map(rows.map(style => [key(style), style])).values()]
})
function selected(style: ProcessStyle): ProcessStyle | undefined {
  return (required.value.styles || []).find((value: ProcessStyle) => key(value) === key(style))
}
const multipleSaved = computed(() => (required.value.styles || []).length > 1)
const selectedKey = computed(() => (required.value.styles || []).length === 1 ? key(required.value.styles[0]) : '')
const activeStyle = computed(() => available.value.find(style => key(style) === selectedKey.value))
const selectedNames = computed(() => activeStyle.value?.name || '')
watch([selectedNames, () => available.value.length], () => {
  if (available.value.length) {
    if (selectedNames.value || hadSelection) emit('update:name', selectedNames.value)
    hadSelection = !!selectedNames.value
  }
})
function choose(value: string) {
  const style = available.value.find(item => key(item) === value)
  if (!style) return
  const styles = [{ versionId: style.versionId, styleNo: style.styleNo, inkColor: selected(style)?.inkColor ?? style.inkColor }]
  emit('update:requirements', JSON.stringify({ ...required.value, styles }))
}
function updateColor(style: ProcessStyle, inkColor: string) {
  const styles = (required.value.styles || []).map((value: ProcessStyle) => key(value) === key(style) ? { ...value, inkColor } : value)
  emit('update:requirements', JSON.stringify({ ...required.value, styles }))
}
function updateActiveColor(inkColor: string) {
  if (activeStyle.value) updateColor(activeStyle.value, inkColor)
}
async function load() {
  const current = ++generation
  hadSelection = false
  sources.value = []; error.value = ''; loading.value = false
  if (!props.plateId) return
  try {
    if (props.sourcesJson != null) { sources.value = readSources(props.sourcesJson); return }
    loading.value = true
    const response = await printingSearchApi.plate(props.plateId)
    if (current !== generation) return
    if (!response.data || Number(response.data.plateId) !== Number(props.plateId)) throw new Error('网版资料身份不匹配')
    sources.value = readSources(response.data.sourcesJson)
  } catch {
    if (current === generation) error.value = '菲林样式加载失败，请重试'
  } finally { if (current === generation) loading.value = false }
}
watch(() => [props.plateId, props.sourcesJson], () => { void load() }, { immediate: true })
onBeforeUnmount(() => generation++)
</script>

<style scoped>
.route-style-fields { margin-top: 8px; }
.tip, small, .unlinked { color: var(--el-text-color-secondary); }
.style-options { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 8px; width: 100%; max-height: 220px; overflow-y: auto; padding: 1px; box-sizing: border-box; }
.style-option { margin: 0; padding: 10px; height: auto; min-height: 56px; }
.style-option :deep(.el-radio__label) { white-space: normal; line-height: 1.5; overflow-wrap: anywhere; }
.selection-summary { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin-top: 8px; font-size: 12px; }
.selection-summary label { display: flex; gap: 6px; align-items: center; }
.selection-summary .el-input { width: 105px; }
small { display: block; font-size: 11px; line-height: 1.4; }
.tip { font-size: 12px; margin: 6px 0; }
.unlinked { font-size: 12px; }
</style>
