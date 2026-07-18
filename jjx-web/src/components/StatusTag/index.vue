<template>
  <el-tag
    :type="tagType"
    :size="size"
    :effect="effect"
    :closable="closable"
    :disable-transitions="disableTransitions"
    :hit="hit"
    :color="customColor"
    @close="handleClose"
  >
    <slot>
      <span v-if="showIcon && icon" class="status-tag-icon">
        <el-icon><component :is="icon" /></el-icon>
      </span>
      {{ displayLabel }}
    </slot>
  </el-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { Component } from 'vue'

// Element Plus Tag 组件支持的类型
export type TagType = 'success' | 'info' | 'warning' | 'danger' | 'primary'
export type TagEffect = 'dark' | 'light' | 'plain'

// 状态配置项
export interface StatusConfig {
  [key: string]: {
    label: string
    type: TagType
    icon?: string | Component
    color?: string
  }
}

interface Props {
  // 状态值
  status: string | number | boolean
  // 状态配置映射
  config?: StatusConfig
  // 自定义标签文本（优先级高于配置）
  label?: string
  // 自定义标签类型（优先级高于配置）
  type?: TagType
  // 自定义颜色
  color?: string
  // 标签尺寸
  size?: 'large' | 'default' | 'small'
  // 主题
  effect?: TagEffect
  // 是否显示图标
  showIcon?: boolean
  // 是否可关闭
  closable?: boolean
  // 是否高亮
  hit?: boolean
  // 是否禁用过渡动画
  disableTransitions?: boolean
  // 默认文本（当状态不在配置中时使用）
  defaultLabel?: string
  // 默认类型（当状态不在配置中时使用）
  defaultType?: TagType
}

interface Emits {
  (e: 'close'): void
}

const props = withDefaults(defineProps<Props>(), {
  config: () => ({}),
  size: 'small',
  effect: 'light',
  showIcon: false,
  closable: false,
  hit: false,
  disableTransitions: false,
  defaultLabel: '未知',
  defaultType: 'info',
})

const emit = defineEmits<Emits>()

// 获取当前状态的配置
const currentConfig = computed(() => {
  const statusKey = String(props.status)
  return props.config?.[statusKey]
})

// 显示标签
const displayLabel = computed(() => {
  if (props.label) return props.label
  if (currentConfig.value?.label) return currentConfig.value.label
  return props.defaultLabel
})

// 标签类型
const tagType = computed((): TagType => {
  if (props.type) return props.type
  if (currentConfig.value?.type) return currentConfig.value.type
  return props.defaultType
})

// 图标
const icon = computed(() => {
  if (currentConfig.value?.icon) return currentConfig.value.icon
  return null
})

// 自定义颜色
const customColor = computed(() => {
  if (props.color) return props.color
  if (currentConfig.value?.color) return currentConfig.value.color
  return undefined
})

// 关闭事件
const handleClose = () => {
  emit('close')
}
</script>

<style scoped lang="scss">
.status-tag-icon {
  margin-right: 4px;
  display: inline-flex;
  align-items: center;

  .el-icon {
    font-size: 12px;
  }
}
</style>
