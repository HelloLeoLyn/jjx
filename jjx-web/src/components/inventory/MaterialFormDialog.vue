<template>
  <MaterialMasterFormDialog
    :model-value="modelValue"
    :material-id="materialId"
    :preset-data="presetData"
    :from-bom="fromBom"
    @update:model-value="emit('update:modelValue', $event)"
    @success="emit('success', $event)"
  />
</template>

<script setup lang="ts">
import type { InventoryMaterial } from '@/types/inventory/material'
import MaterialMasterFormDialog from './MaterialMasterFormDialog.vue'

defineProps<{
  modelValue: boolean
  materialId?: number | null
  /** 来自 BOM 快速建档（放宽颜色必填） */
  fromBom?: boolean
  presetData?: {
    materialName?: string
    specification?: string
    unit?: string
    supplierName?: string
  }
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success', material: InventoryMaterial): void
}>()
</script>
