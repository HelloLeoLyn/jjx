<template>
  <select :value="modelValue" @change="handleChange" :class="selectClasses">
    <option v-if="placeholder" value="">
      {{ placeholder }}
    </option>
    <option v-for="option in options" :key="option.value" :value="option.value">
      {{ option.label }}
    </option>
  </select>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  modelValue?: string
  options: Array<{ value: string; label: string; type?: string }>
  placeholder?: string
  clearable?: boolean
  size?: 'small' | 'default' | 'large'
}

interface Emits {
  (e: 'update:modelValue', value: string): void
  (e: 'change', value: string): void
}

const props = withDefaults(defineProps<Props>(), {
  placeholder: '',
  clearable: false,
  size: 'default',
})

const emit = defineEmits<Emits>()

const selectClasses = computed(() => {
  return ['status-select', `status-select-${props.size}`]
})

const handleChange = (event: Event) => {
  const target = event.target as HTMLSelectElement
  const value = target.value
  emit('update:modelValue', value)
  emit('change', value)
}
</script>

<style scoped>
.status-select {
  padding: 4px 8px;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  background-color: #fff;
  cursor: pointer;
  outline: none;
}

.status-select:hover {
  border-color: #40a9ff;
}

.status-select:focus {
  border-color: #40a9ff;
  box-shadow: 0 0 0 2px rgba(24, 144, 255, 0.2);
}

.status-select-small {
  padding: 2px 6px;
  font-size: 12px;
}

.status-select-large {
  padding: 6px 12px;
  font-size: 16px;
}
</style>
