<template>
  <div v-loading="loading" class="route-style-fields">
    <el-alert v-if="error" :title="error" type="error" :closable="false"><el-button link type="primary" @click="load">重试</el-button></el-alert>
    <template v-if="available.length">
      <div class="name-summary"><span>印刷名称</span><strong>{{ selectedNames || '请至少选择一个样式' }}</strong></div>
      <div class="style-grid style-heading"><span>使用样式 / 所属菲林</span><span>色号</span><span>油墨编号</span></div>
      <div v-for="item in available" :key="key(item)" class="style-grid style-row">
        <div>
          <el-checkbox :model-value="!!selected(item)" @update:model-value="checked => toggle(item, !!checked)">{{ item.name }}</el-checkbox>
          <small>{{ item.artworkNo || item.filmNo }} {{ item.versionNo }} · {{ item.styleNo }}</small>
        </div>
        <el-input :model-value="selected(item)?.inkColor ?? item.inkColor" :disabled="!selected(item)" size="small" maxlength="100" placeholder="样式色号" @update:model-value="value => updateColor(item, value)" />
        <el-tooltip content="当前样式资料未提供油墨编号，本次仅预览页面，后续再对接。"><span class="unlinked">待关联</span></el-tooltip>
      </div>
      <p class="tip">色号默认取自样式，可在本工序调整。</p>
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
const selectedNames = computed(() => available.value.filter(style => selected(style)).map(style => style.name).join(' / '))
watch([selectedNames, () => available.value.length], () => {
  if (available.value.length) {
    if (selectedNames.value || hadSelection) emit('update:name', selectedNames.value)
    hadSelection = !!selectedNames.value
  }
})
function toggle(style: ProcessStyle, checked: boolean) {
  const styles = (required.value.styles || []).filter((value: ProcessStyle) => key(value) !== key(style))
  if (checked) styles.push({ versionId: style.versionId, styleNo: style.styleNo, inkColor: style.inkColor })
  emit('update:requirements', JSON.stringify({ ...required.value, styles }))
}
function updateColor(style: ProcessStyle, inkColor: string) {
  const styles = (required.value.styles || []).map((value: ProcessStyle) => key(value) === key(style) ? { ...value, inkColor } : value)
  emit('update:requirements', JSON.stringify({ ...required.value, styles }))
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
.name-summary { display: flex; gap: 12px; padding: 7px 10px; background: var(--el-fill-color-light); border-radius: 4px; margin-bottom: 8px; }
.name-summary > span, .tip, small, .unlinked { color: var(--el-text-color-secondary); }
.style-grid { display: grid; grid-template-columns: minmax(190px, 1fr) 145px 90px; gap: 12px; align-items: center; }
.style-heading { color: var(--el-text-color-secondary); font-size: 12px; padding: 0 6px 5px; border-bottom: 1px solid var(--el-border-color-lighter); }
.style-row { padding: 6px; border-bottom: 1px solid var(--el-border-color-lighter); }
small { display: block; font-size: 11px; line-height: 1.4; }
.tip { font-size: 12px; margin: 6px 0; }
.unlinked { font-size: 12px; }
</style>
