<template>
  <EditableSearchSelect
    :model-value="selectedId"
    :display-value="displayValue"
    :options="search.items.value"
    :option-key="scene === 'PLATE' ? 'plateId' : 'versionId'"
    :label-of="labelOf"
    :loading="search.loading.value"
    :loading-more="search.loadingMore.value"
    :failed="search.failed.value"
    :total="search.total.value"
    :min-width="520"
    :placeholder="placeholder"
    :size="size"
    :disabled="disabled"
    :clearable="clearable"
    @search="search.search"
    @select="onSelect"
    @clear="onClear"
    @load-more="search.loadMore"
  >
    <template #option="{ item }">
      <PrintResourceSearchOption :item="item" />
      <div v-if="scene === 'PLATE'" class="plate-search-meta">
        实物菲林：{{ item.filmNo || '未记录' }} · 张力：{{ item.tensionNCm ?? '未记录' }} N/cm
      </div>
    </template>
    <template #footer>
      <div v-if="search.failed.value" class="search-failed">
        查询失败，
        <el-button link type="primary" size="small" @click="search.retry()">重试</el-button>
      </div>
    </template>
  </EditableSearchSelect>
</template>

<script setup lang="ts">
/** 印刷资源选择器（dev-20261125-011）：只维护选择，不保存未确认的输入文本。 */
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import EditableSearchSelect from './EditableSearchSelect.vue'
import PrintResourceSearchOption from './PrintResourceSearchOption.vue'
import { usePrintResourceSearch } from './usePrintResourceSearch'
import type { PrintSearchRow } from '@/api/engineering/printingSearch'

const props = withDefaults(
  defineProps<{
    scene?: string
    modelValue?: PrintSearchRow | number | null
    placeholder?: string
    size?: 'large' | 'default' | 'small'
    disabled?: boolean
    clearable?: boolean
  }>(),
  {
    modelValue: null,
    placeholder: '输入图案/菲林/网框/产品编号或名称搜索',
    size: 'default',
    disabled: false,
    clearable: true,
  }
)
const emit = defineEmits<{
  (e: 'update:modelValue', value: PrintSearchRow | number | null): void
  (e: 'select', row: PrintSearchRow): void
  (e: 'clear'): void
}>()

const search = usePrintResourceSearch({ scene: props.scene })

const selectedId = computed<number | null>(() => {
  const mv = props.modelValue
  if (mv == null) return null
  return typeof mv === 'object' ? Number((props.scene === 'PLATE' ? mv.plateId : mv.versionId) ?? 0) || null : Number(mv)
})

const displayValue = computed(() => {
  const mv = props.modelValue
  if (mv == null) return ''
  if (typeof mv === 'object') return mv.displayName || mv.plateNo || mv.artworkNo || ''
  return String(mv)
})

const labelOf = (item: PrintSearchRow) => item.displayName || item.artworkNo || item.versionNo || ''

function onSelect(item: PrintSearchRow) {
  if (props.scene === 'PLATE' && (!item.plateId || !item.versionId || item.availability !== true)) {
    ElMessage.warning('请选择已放行且关联资料可用的网版')
    return
  }
  emit('update:modelValue', item)
  emit('select', item)
}

function onClear() {
  emit('update:modelValue', null)
  emit('clear')
}
</script>

<style scoped>
.plate-search-meta { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 2px; }
.search-failed {
  padding: 6px 10px;
  font-size: 12px;
  color: var(--el-color-danger);
}
</style>
