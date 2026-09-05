<template>
  <el-checkbox-group v-model="selectedKeys" class="print-column-picker" @change="handleChange">
    <el-checkbox v-for="column in columns" :key="column.key" :value="column.key">
      {{ column.label }}
    </el-checkbox>
  </el-checkbox-group>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'

export interface PrintColumn {
  key: string
  label: string
}

const props = defineProps<{
  columns: PrintColumn[]
  storageKey: string
  modelValue?: string[]
}>()

const emit = defineEmits<{
  'update:modelValue': [keys: string[]]
  update: [keys: string[]]
}>()

const allKeys = () => props.columns.map((column) => column.key)

function normalize(keys: unknown): string[] {
  if (!Array.isArray(keys)) return allKeys()
  const validKeys = new Set(allKeys())
  return [...new Set(keys.filter((key): key is string => typeof key === 'string' && validKeys.has(key)))]
}

function readStored(): string[] {
  if (props.modelValue) return normalize(props.modelValue)
  try {
    const stored = localStorage.getItem(props.storageKey)
    return stored === null ? allKeys() : normalize(JSON.parse(stored))
  } catch {
    return allKeys()
  }
}

const selectedKeys = ref<string[]>(readStored())

function publish(keys: string[]) {
  const normalized = normalize(keys)
  selectedKeys.value = normalized
  localStorage.setItem(props.storageKey, JSON.stringify(normalized))
  emit('update:modelValue', normalized)
  emit('update', normalized)
}

function handleChange(value: Array<string | number | boolean>) {
  publish(value.filter((key): key is string => typeof key === 'string'))
}

watch(
  () => props.modelValue,
  (keys) => {
    if (keys) selectedKeys.value = normalize(keys)
  }
)

watch(
  () => props.columns,
  () => publish(selectedKeys.value),
  { deep: true }
)

onMounted(() => publish(selectedKeys.value))
</script>

<style scoped>
.print-column-picker {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0 16px;
}

.print-column-picker :deep(.el-checkbox) {
  margin-right: 0;
}
</style>
