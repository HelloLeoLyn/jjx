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
        @update:index="(n: number) => emit('update:index', n)"
      />
      <IconStepBadge
        v-else
        :icon="item.icon || ''"
        :size="16"
        :index="item.indexNumber ?? null"
      />
    </template>
    <SvgIcon v-else-if="item.icon" :name="item.icon" :size="14" />
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
