<template>
  <el-input
    v-bind="$attrs"
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    class="compact-input"
  >
    <template v-for="(_, name) in $slots" #[name]="slotData">
      <slot :name="name" v-bind="slotData" />
    </template>
  </el-input>
</template>

<script setup lang="ts">
defineProps<{
  modelValue?: string | number
}>()

defineEmits<{
  'update:modelValue': [value: string | number]
}>()
</script>

<style scoped lang="scss">
.compact-input {
  height: 100%;

  :deep(.el-input__wrapper) {
    padding: 0;
    box-shadow: none !important;
    height: 100%;
  }

  :deep(.el-input__inner) {
    padding: 0;
    border: none;
    height: 100%;
  }
}
</style>
