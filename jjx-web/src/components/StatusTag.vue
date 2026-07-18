<template>
  <component :is="tagComponent" :class="tagClasses" :style="customStyle">
    <slot>
      <span v-if="showIcon && icon" class="status-tag-icon">{{ icon }}</span>
      {{ displayLabel }}
    </slot>
  </component>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { TagType } from '@/types/status'

interface Props {
  status?: string
  label?: string
  type?: TagType
  customColor?: string
  size?: 'small' | 'default' | 'large'
  showIcon?: boolean
  icon?: string
}

const props = withDefaults(defineProps<Props>(), {
  type: 'default',
  size: 'default',
  showIcon: false,
})

// 显示的标签文本
const displayLabel = computed(() => {
  if (props.label) return props.label
  if (props.status) return props.status
  return ''
})

// 标签组件（原生实现，可根据实际 UI 库替换）
const tagComponent = computed(() => 'span')

// 标签类名
const tagClasses = computed(() => {
  const classes = ['status-tag']

  if (props.size === 'small') classes.push('status-tag-small')
  if (props.size === 'large') classes.push('status-tag-large')

  if (!props.customColor) {
    classes.push(`status-tag-${props.type}`)
  }

  return classes
})

// 自定义样式
const customStyle = computed(() => {
  if (props.customColor) {
    return {
      backgroundColor: props.customColor,
      borderColor: props.customColor,
    }
  }
  return {}
})
</script>

<style scoped>
.status-tag {
  display: inline-flex;
  align-items: center;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 12px;
  line-height: 1.5;
  border: 1px solid;
}

.status-tag-small {
  padding: 0 6px;
  font-size: 11px;
  line-height: 1.4;
}

.status-tag-large {
  padding: 0 12px;
  font-size: 14px;
  line-height: 1.6;
}

.status-tag-default {
  background-color: #f5f5f5;
  color: #666;
  border-color: #d9d9d9;
}

.status-tag-info {
  background-color: #e6f7ff;
  color: #1890ff;
  border-color: #91d5ff;
}

.status-tag-success {
  background-color: #f6ffed;
  color: #52c41a;
  border-color: #b7eb8f;
}

.status-tag-warning {
  background-color: #fffbe6;
  color: #faad14;
  border-color: #ffe58f;
}

.status-tag-danger {
  background-color: #fff2f0;
  color: #ff4d4f;
  border-color: #ffccc7;
}

.status-tag-icon {
  margin-right: 4px;
}
</style>
