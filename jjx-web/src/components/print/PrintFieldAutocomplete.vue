<template>
  <span class="print-field">
    <el-autocomplete
      :model-value="modelValue"
      size="small"
      :style="{ width: width || '160px' }"
      :fetch-suggestions="fetchSuggestions"
      :trigger-on-focus="true"
      clearable
      :placeholder="placeholder || defaultPlaceholder"
      @select="onSelect"
      @input="onInput"
    >
      <template #default="{ item }">
        <span>{{ item.value }}</span>
        <span
          v-if="item.hint || item.statusLabel"
          style="float: right; color: #909399; font-size: 12px; margin-left: 8px"
          >{{ item.hint || item.statusLabel }}</span
        >
      </template>
    </el-autocomplete>
    <el-tag
      v-if="field === 'screenNo' && frameTag"
      :type="frameTag.type as any"
      size="small"
      effect="plain"
      class="pf-status"
      >{{ frameTag.label }}</el-tag
    >
  </span>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import {
  ensureFrames,
  frameStatusOf,
  PRINT_FIELD_SUGGESTERS,
  type PrintFieldKey,
  type PrintSuggestItem,
} from '@/composables/usePrintFieldSuggest'

/**
 * 印刷工序字段联想输入（公共组件）——dev-20261009-026
 * 色号/油墨/菲林/网框 统一走 usePrintFieldSuggest；带状态/备注后缀。
 */
const props = defineProps<{
  modelValue: string
  field: PrintFieldKey
  placeholder?: string
  width?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [v: string]
  select: [item: PrintSuggestItem]
  input: [v: string]
}>()

const DEFAULT_PLACEHOLDER: Record<PrintFieldKey, string> = {
  colorNo: '如 PANTONE 123C',
  inkNo: '油墨编号',
  filmNo: '菲林编码',
  screenNo: '网框编号（带状态）',
}
const defaultPlaceholder = computed(() => DEFAULT_PLACEHOLDER[props.field])
// 网框状态标签（异常高亮：未制版/维护中/已报废）
const frameTag = computed(() =>
  props.field === 'screenNo' ? frameStatusOf(props.modelValue) : null
)

onMounted(() => {
  if (props.field === 'screenNo') ensureFrames()
})

function fetchSuggestions(query: string, cb: (items: PrintSuggestItem[]) => void) {
  if (props.field === 'screenNo') ensureFrames()
  PRINT_FIELD_SUGGESTERS[props.field](query, cb)
}

function onSelect(item: any) {
  emit('update:modelValue', String(item?.value ?? ''))
  emit('select', item as PrintSuggestItem)
}

function onInput(v: string | number) {
  const s = String(v)
  emit('update:modelValue', s)
  emit('input', s)
}
</script>

<style scoped>
.print-field {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
}
.pf-status {
  flex-shrink: 0;
}
</style>
