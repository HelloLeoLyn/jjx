<template>
  <ProductCodeGenDialog
    :model-value="modelValue"
    :customer-short="customerShort"
    :used-serials="usedSerials"
    title="新建订单产品编码"
    @update:model-value="emit('update:modelValue', $event)"
    @confirm="({ params }) => emit('confirm', params)"
  />
</template>

<script setup lang="ts">
import ProductCodeGenDialog from '@/components/ProductCodeGenDialog/index.vue'
import type { ProductCodeResult } from '@/composables/useProductCode'

// 不传 initState：旧行只在确认后替换，名称在订单行重新填写。
defineProps<{ modelValue: boolean; customerShort: string; usedSerials?: string[] }>()
const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  confirm: [params: ProductCodeResult]
}>()
</script>
