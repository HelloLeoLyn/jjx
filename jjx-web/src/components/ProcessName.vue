<template>
  <span class="process-name">
    <ProcessIcon
      :icon="icon"
      :name="displayName"
      :category="matched?.processCategory"
      :size="size"
      class="process-name-icon"
    />
    <span class="process-name-text">{{ displayName }}</span>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ProcessIcon from '@/components/ProcessIcon.vue'
import { useEnabledProcesses } from '@/composables/useEnabledProcesses'

/**
 * 项目（标准工序）只读显示：图标 + 名称
 * 与可编辑的 StandardProcessSelector 共用同一份工序数据（全局单例缓存，不额外请求）。
 */
const props = withDefaults(
  defineProps<{
    /** 标准工序ID（优先按ID匹配以取图标） */
    processId?: number | null
    /** 项目名称（无ID时按名称匹配取图标） */
    name?: string | null
    /** 图标尺寸 */
    size?: number
  }>(),
  { processId: undefined, name: '', size: 16 }
)

const { processes } = useEnabledProcesses()

const matched = computed(() => {
  if (props.processId != null) {
    const byId = processes.value.find((p) => p.processId === props.processId)
    if (byId) return byId
  }
  const n = (props.name || '').trim()
  return n ? processes.value.find((p) => (p.processName || '').trim() === n) : undefined
})

const icon = computed(() => matched.value?.icon || '')
const displayName = computed(() => props.name || matched.value?.processName || '-')
</script>

<style scoped>
.process-name {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.process-name-icon {
  flex: none;
}
.process-name-text {
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
