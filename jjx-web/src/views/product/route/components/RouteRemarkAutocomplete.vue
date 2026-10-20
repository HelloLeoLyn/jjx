<template>
  <el-autocomplete
    :model-value="modelValue"
    :fetch-suggestions="fetchSuggestions"
    placeholder="请输入备注"
    size="small"
    style="width: 100%"
    :highlight-first-item="false"
    @update:model-value="emit('update:modelValue', String($event))"
  />
</template>

<script setup lang="ts">
import { productRouteApi } from '@/api/product/routing'

const props = defineProps<{
  modelValue: string
  processCategory?: string
  processIds: number[]
}>()
const emit = defineEmits<{ 'update:modelValue': [value: string] }>()
let requestSequence = 0
async function fetchSuggestions(keyword: string, cb: (items: { value: string }[]) => void) {
  const sequence = ++requestSequence
  const context = JSON.stringify([props.processCategory, props.processIds])
  try {
    const response = await productRouteApi.suggestRemarks({
      processCategory: props.processCategory,
      processIds: props.processIds,
      keyword,
    })
    if (sequence !== requestSequence) return
    if (context !== JSON.stringify([props.processCategory, props.processIds])) {
      cb([])
      return
    }
    cb((response.data || []).map((value: string) => ({ value })))
  } catch {
    if (sequence === requestSequence) cb([])
  }
}
</script>
