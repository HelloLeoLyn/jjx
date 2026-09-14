<template>
  <slot v-if="$slots.default" :item="item" />
  <span
    v-else
    class="engineering-routing-item"
    :class="{ 'is-view': mode === 'view' }"
  >
    <template v-if="item.hasIndex === 1">
      <IconStepBadge
        v-if="mode === 'edit'"
        :icon="item.icon || ''"
        :size="16"
        :index="item.indexNumber ?? null"
        :work-instruction="item.description"
        @update:index="(n: number) => emit('update:index', n)"
      />
      <IconStepBadge
        v-else
        :icon="item.icon || ''"
        :size="16"
        :index="item.indexNumber ?? null"
        :work-instruction="item.description"
      />
    </template>
    <span v-else-if="item.icon" class="plain-icon"><SvgIcon :name="item.icon" :size="14" /><small v-if="item.description" :title="item.description">{{ item.description }}</small></span>
    <el-tag
      v-if="item.majorCategory === 'PRINT'"
      size="small"
      type="warning"
      effect="plain"
    >
      印刷
    </el-tag>
    <span class="item-name">{{ item.processName }}</span>
    <el-popover v-if="mode === 'edit' && (item.hasWorkInstruction === 1 || item.description)" placement="top" :width="300" trigger="click">
      <el-input v-model="item.description" clearable placeholder="作业说明（可选，如：冲窗口灯孔）" />
      <template #reference><el-button link size="small">{{ item.description ? '改说明' : '＋说明' }}</el-button></template>
    </el-popover>
    <el-icon v-if="mode === 'edit'" :size="12" class="item-close" @click="emit('remove')">
      <Close />
    </el-icon>
  </span>
</template>

<script setup lang="ts">
import type { PropType } from 'vue'
import { Close } from '@element-plus/icons-vue'
import IconStepBadge from '@/components/IconStepBadge/index.vue'
import type { EngineeringRoutingItemVO } from '@/types/product/routing'

defineOptions({ name: 'EngineeringRoutingItem' })

defineProps({
  item: { type: Object as PropType<EngineeringRoutingItemVO>, required: true },
  mode: { type: String as PropType<'view' | 'edit'>, default: 'view' },
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
