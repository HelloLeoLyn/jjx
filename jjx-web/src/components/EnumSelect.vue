<template>
  <el-select
    :model-value="modelValue"
    :placeholder="placeholder"
    :clearable="clearable"
    :size="size"
    v-bind="$attrs"
    @update:model-value="handleUpdate"
    @change="handleChange"
  >
    <el-option
      v-for="item in enumObj.items"
      :key="String(item.value)"
      :label="item.label"
      :value="item.value"
    />
  </el-select>
</template>

<script setup lang="ts">
import type { EnumObject } from '@/enums/base'

interface Props {
  modelValue?: string | number | boolean
  enumObj: EnumObject<any>
  placeholder?: string
  clearable?: boolean
  size?: 'large' | 'default' | 'small'
}

interface Emits {
  (e: 'update:modelValue', value: any): void
  (e: 'change', value: any): void
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '请选择',
  clearable: false,
  size: 'default',
})

const emit = defineEmits<Emits>()

const handleUpdate = (value: any) => {
  emit('update:modelValue', value)
}

const handleChange = (value: any) => {
  emit('change', value)
}
</script>
