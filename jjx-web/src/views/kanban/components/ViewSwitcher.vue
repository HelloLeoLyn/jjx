<template>
  <div class="view-switcher">
    <el-radio-group
      v-model="activeView"
      size="large"
      @change="onChange"
    >
      <el-radio-button
        v-for="view in views"
        :key="view.id"
        :value="view.id"
      >
        {{ view.name }}
      </el-radio-button>
    </el-radio-group>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { BoardView } from '@/views/kanban/types/board'

const props = defineProps<{
  views: BoardView[]
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  change: [value: string]
}>()

const activeView = computed({
  get: () => props.modelValue,
  set: (val: string) => emit('update:modelValue', val),
})

function onChange(val: string) {
  emit('change', val)
}
</script>

<style scoped>
.view-switcher {
  display: flex;
  align-items: center;
}
</style>
