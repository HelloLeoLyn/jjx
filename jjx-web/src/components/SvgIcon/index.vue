<!-- src/components/SvgIcon/index.vue -->
<template>
  <svg aria-hidden="true" class="svg-icon" :width="sizeNum" :height="sizeNum">
    <use :href="symbolId" :fill="color" />
  </svg>
</template>

<script setup lang="ts">
import { computed } from 'vue'

interface Props {
  prefix?: string
  name: string
  color?: string
  size?: number | string
}

const props = withDefaults(defineProps<Props>(), {
  prefix: 'icon',
  color: 'currentColor',
  size: 18,
})

// 统一转换为数字（像素）
const sizeNum = computed(() => {
  if (typeof props.size === 'number') return props.size
  if (typeof props.size === 'string') {
    const parsed = parseInt(props.size, 10)
    return isNaN(parsed) ? 18 : parsed
  }
  return 18
})

const symbolId = computed(() => `#${props.prefix}-${props.name}`)
</script>

<style scoped>
.svg-icon {
  display: inline-block;
  vertical-align: middle;
}
</style>
