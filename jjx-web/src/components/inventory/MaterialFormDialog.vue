<template>
  <MaterialMasterFormDialog
    :model-value="modelValue"
    :material-id="materialId"
    :preset-data="presetData"
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
  presetData?: {
    materialName?: string
    specification?: string
    unit?: string
    supplierName?: string
    materialType?: string
  }
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success', material: InventoryMaterial): void
}>()
</script>
