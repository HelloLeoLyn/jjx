<!-- components/UniversalIcon.vue -->
<template>
  <!-- SVG Icon -->
  <svg-icon v-if="iconType === 'svg'" :name="icon" :size="size" :color="color" />

  <!-- Element Icon -->
  <el-icon v-else-if="iconType === 'el'" :size="size" :color="color">
    <component :is="componentName" />
  </el-icon>

  <!-- 无图标时返回空占位 -->
  <span v-else :style="{ width: size + 'px' }">-</span>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  icon: {
    type: String,
    default: '',
  },
  size: {
    type: Number,
    default: 16,
  },
  color: {
    type: String,
    default: '',
  },
})

// 判断图标类型
const iconType = computed(() => {
  if (!props.icon) return 'none'

  // 判断是否为 SVG（小写字母、数字、横线组合）
  if (/^[a-z0-9-]+$/.test(props.icon)) {
    return 'svg'
  }

  // 判断是否为 Element 图标（首字母大写的驼峰）
  if (/^[A-Z][a-zA-Z]*$/.test(props.icon)) {
    return 'el'
  }

  return 'none'
})

// 获取 Element 组件名（确保首字母大写）
const componentName = computed(() => {
  if (iconType.value !== 'el') return ''

  // 如果后端返回的是小写，转为首字母大写
  if (/^[a-z]/.test(props.icon)) {
    return props.icon.charAt(0).toUpperCase() + props.icon.slice(1)
  }

  return props.icon
})
</script>
