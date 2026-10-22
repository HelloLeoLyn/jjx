<template>
  <slot v-if="$slots.default" :item="item" />
  <span
    v-else
    class="engineering-routing-item"
    :class="{ 'is-view': mode === 'view' }"
  >
    <!-- 2026-10-22：角标只显示作业说明全文（有内容才显示），不再读 hasIndex/indexNumber（dev-20261022-011） -->
    <IconStepBadge
      v-if="item.icon"
      :icon="item.icon"
      :size="16"
      :work-instruction="instructionText"
    />
    <span v-else-if="instructionText" class="plain-icon"
      ><small :title="instructionText">{{ instructionText }}</small></span
    >
    <el-tag
      v-if="item.majorCategory === 'PRINT'"
      size="small"
      type="warning"
      effect="plain"
    >
      印刷
    </el-tag>
    <span class="item-name">{{ item.processName }}</span>
    <el-icon v-if="mode === 'edit'" :size="12" class="item-close" @click="emit('remove')">
      <Close />
    </el-icon>
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { PropType } from 'vue'
import { Close } from '@element-plus/icons-vue'
import IconStepBadge from '@/components/IconStepBadge/index.vue'
import type { EngineeringRoutingItemVO } from '@/types/product/routing'

defineOptions({ name: 'EngineeringRoutingItem' })

const props = defineProps({
  item: { type: Object as PropType<EngineeringRoutingItemVO>, required: true },
  mode: { type: String as PropType<'view' | 'edit'>, default: 'view' },
})

/** 作业说明：优先 work_instruction，历史数据回退 description（dev-20261022-011） */
const instructionText = computed(() => {
  const item = props.item as EngineeringRoutingItemVO & { workInstruction?: string }
  return (item.workInstruction || item.description || '').trim()
})

const emit = defineEmits<{
  (e: 'update:index', value: number): void
  (e: 'remove'): void
}>()
</script>

<style scoped>
.engineering-routing-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  min-height: 24px;
  padding: 0 6px;
  border: 1px solid #d9ecff;
  border-radius: 4px;
  background-color: #ecf5ff;
  color: #409eff;
  line-height: 22px;
  user-select: none;
}

.item-name {
  font-size: 12px;
  white-space: nowrap;
}

.plain-icon { position: relative; display: inline-flex; align-items: center; }
.plain-icon small { position: absolute; left: 70%; bottom: -8px; max-width: 96px; overflow: hidden; color: var(--el-text-color-regular); font-size: 10px; line-height: 12px; text-overflow: ellipsis; white-space: nowrap; }

.engineering-routing-item.is-view :deep(.icon-step-badge) {
  pointer-events: none;
}

.item-close {
  flex-shrink: 0;
  color: #c0c4cc;
  cursor: pointer;
}

.item-close:hover {
  color: #f56c6c;
}
</style>
