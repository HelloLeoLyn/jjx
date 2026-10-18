<template>
  <el-dialog :model-value="modelValue" title="新建产品" width="1200px" append-to-body destroy-on-close :close-on-click-modal="false" @update:model-value="emit('update:modelValue', $event)">
    <ProductFormContent v-if="modelValue" ref="content" mode="draft" :customer-id="customerId" :customer-short-name="customerShort" :initial-draft="initialDraft" @draft-confirm="emit('confirm', $event)" />
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" @click="confirm">确认并回填订单</el-button>
    </template>
  </el-dialog>
</template>
<script setup lang="ts">
import { ref } from 'vue'
import ProductFormContent from '@/views/product/list/components/ProductFormContent.vue'
import type { ProductFormData } from '@/types/product'
import type { ProductCodeResult } from '@/composables/useProductCode'
defineProps<{ modelValue: boolean; customerId?: number; customerShort: string; initialDraft?: Partial<ProductFormData> & Partial<ProductCodeResult> }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; confirm: [value: ProductFormData & ProductCodeResult] }>()
const content = ref<InstanceType<typeof ProductFormContent>>()
async function confirm() { try { await content.value?.handleSubmit() } catch { /* 校验失败保留表单 */ } }
</script>
