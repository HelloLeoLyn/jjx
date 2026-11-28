<template>
  <el-button v-if="hasBinding" link type="primary" size="small" @click="open">查看详情</el-button>
  <span v-else class="empty">请先选择网版</span>
  <el-dialog v-model="visible" title="网版关联资料" width="760px" append-to-body destroy-on-close>
    <div v-loading="loading">
      <el-alert v-if="error" :title="error" type="error" :closable="false">
        <el-button link type="primary" @click="load">重试</el-button>
      </el-alert>
      <el-alert v-if="row.printingSnapshot" title="以下为发布时记录的关联资料；未记录的信息不以当前资料补填。" type="info" :closable="false" />
      <el-alert v-else-if="!binding.defaultPlateId && (legacy.filmNo || legacy.screenNo || legacy.plateNo)" title="尚未选择实体网版，历史文字记录的关联关系未核验。" type="info" :closable="false" />
      <el-alert v-else-if="source?.availability === false" title="当前网版已不符合使用条件，请重新选择可用网版。" type="warning" :closable="false" />
      <el-descriptions :column="2" border class="source-info">
        <el-descriptions-item label="网版">{{ source?.plateNo || binding.defaultPlateNo || (legacy.plateNo ? `历史记录：${legacy.plateNo}（未核验）` : '') || (binding.defaultPlateId ? `网版 #${binding.defaultPlateId}` : '未选择') }}</el-descriptions-item>
        <el-descriptions-item label="网版状态">{{ source?.plateStatus ? ScreenPlateStatusEnum.getLabel(source.plateStatus) : '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="菲林">{{ source?.artworkNo || binding.artworkNo || legacy.filmNo || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="菲林版本">{{ source?.versionNo || binding.artworkVersionNo || (binding.artworkVersionId ? `版本 #${binding.artworkVersionId}` : '未记录') }}</el-descriptions-item>
        <el-descriptions-item label="实物菲林编号">{{ source?.filmNo || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="网框">{{ source?.frameNo || binding.defaultFrameNo || legacy.screenNo || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="绷网记录">{{ source?.stretchId ? `#${source.stretchId}` : '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="绷网时间">{{ source?.stretchTime || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="目数">{{ source?.mesh != null ? `${source.mesh} 目` : '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="登记张力">{{ source?.tensionNCm != null ? `${source.tensionNCm} N/cm` : '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="网布材质">{{ source?.stretchMaterial || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="实际制版排布">{{ source?.layout || (row.printingSnapshot as any)?.plateLayout || '未记录' }}</el-descriptions-item>
        <el-descriptions-item label="网版位置">{{ source?.position || '未记录' }}</el-descriptions-item>
      </el-descriptions>
      <template v-if="showStyles">
      <el-divider content-position="left">工序样式</el-divider>
      <ProcessStylesPicker :sources="plateSources" :model-value="styleRequirements" :readonly="readOnly || !!row.printingSnapshot" @update:model-value="v => emit('update:requirements', v)" />
      <p v-if="!plateSources.length">未记录样式，不从当前资料补推历史。</p>
      </template>
      <el-divider content-position="left">固定印刷要求（当前工序）</el-divider>
      <template v-if="readOnly || row.printingSnapshot"><p>{{ printingRequirements(row) }}</p></template>
      <el-form v-else label-width="110px" size="small">
        <p class="hint">按工序填写允许范围，网版的登记参数不会覆盖这些要求。</p>
        <el-form-item label="印刷位置"><el-input :model-value="requirement('position')" placeholder="印刷位置" @update:model-value="v => updateRequirement('position', v)" /></el-form-item>
        <el-form-item label="目数范围"><div class="range"><el-input :model-value="requirement('meshMin')" placeholder="目数下限" @update:model-value="v => updateRequirement('meshMin', v)" /><span>至</span><el-input :model-value="requirement('meshMax')" placeholder="目数上限" @update:model-value="v => updateRequirement('meshMax', v)" /></div></el-form-item>
        <el-form-item label="张力（N/cm）"><div class="range"><el-input :model-value="requirement('tensionMin')" placeholder="张力下限 N/cm" @update:model-value="v => updateRequirement('tensionMin', v)" /><span>至</span><el-input :model-value="requirement('tensionMax')" placeholder="张力上限 N/cm" @update:model-value="v => updateRequirement('tensionMax', v)" /></div></el-form-item>
      </el-form>
    </div>
    <template #footer><el-button @click="visible = false">{{ readOnly ? '关闭' : '返回工序录入' }}</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import ProcessStylesPicker from './ProcessStylesPicker.vue'
import { readSources } from './printStyles'
import { printingSearchApi, type PrintSearchRow } from '@/api/engineering/printingSearch'
import { ScreenPlateStatusEnum } from '@/enums/engineering/resource'
import { printingBinding, printingRequirements, type SpecProcessRow } from '@/views/engineering/product-spec/components/workSpecProcessRows'
const props = withDefaults(defineProps<{ row: SpecProcessRow; readOnly?: boolean; showStyles?: boolean }>(), { readOnly: true, showStyles: true })
const emit = defineEmits<{ 'update:requirements': [value: string] }>()
const visible = ref(false)
const loading = ref(false)
const error = ref('')
const source = ref<PrintSearchRow>()
const binding = computed(() => printingBinding(props.row))
const plateSources = computed(() => props.row.printingSnapshot ? ((props.row.printingSnapshot as any).plateSources || []) : readSources(source.value?.sourcesJson))
const styleRequirements = computed(() => props.row.printingSnapshot ? JSON.stringify((props.row.printingSnapshot as any).requirements || {}) : props.row.printRequirements)
const legacy = computed(() => {
  if (props.row.printingSnapshot) return {}
  try { return JSON.parse(props.row.customProcessParams || '{}') || {} } catch { return {} }
})
const hasBinding = computed(() => !!(binding.value.defaultPlateId || binding.value.artworkVersionId || binding.value.printRequirements || binding.value.requirements || legacy.value.filmNo || legacy.value.screenNo || legacy.value.plateNo))
let generation = 0
async function load() {
  const current = ++generation
  source.value = undefined
  error.value = ''
  loading.value = false
  if (props.row.printingSnapshot || !binding.value.defaultPlateId) return
  loading.value = true
  try {
    const response = await printingSearchApi.plate(Number(binding.value.defaultPlateId))
    if (current !== generation) return
    if (!response.data || Number(response.data.plateId) !== Number(binding.value.defaultPlateId)) throw new Error('网版关联资料不匹配')
    source.value = response.data
  } catch {
    if (current === generation) error.value = '网版关联资料加载失败，请重试'
  } finally { if (current === generation) loading.value = false }
}
function open() { visible.value = true; void load() }
function requirement(key: string) {
  try { return String(JSON.parse(props.row.printRequirements || '{}')?.[key] ?? '') } catch { return '' }
}
function updateRequirement(key: string, value: string) {
  let requirements: Record<string, unknown>
  try { requirements = JSON.parse(props.row.printRequirements || '{}') } catch { requirements = {} }
  if (!requirements || typeof requirements !== 'object' || Array.isArray(requirements)) requirements = {}
  if (!value.trim()) delete requirements[key]
  else requirements[key] = key !== 'position' && Number.isFinite(Number(value)) ? Number(value) : value.trim()
  emit('update:requirements', JSON.stringify(requirements))
}
watch(() => [binding.value.defaultPlateId, props.row.printingSnapshot], () => { if (visible.value) void load() })
watch(visible, value => { if (!value) { generation++; loading.value = false } })
onBeforeUnmount(() => generation++)
</script>

<style scoped>
.empty, .hint { color: var(--el-text-color-secondary); font-size: 12px; }
.source-info { margin-top: 12px; }
.range { display: flex; align-items: center; gap: 8px; width: 100%; }
</style>
