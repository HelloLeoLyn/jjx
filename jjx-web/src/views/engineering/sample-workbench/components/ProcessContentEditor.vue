<template>
    <!-- 行2：标准工序（组合，任意结构） -->
    <div class="pc-row">
      <div class="pc-items">
        <ProcessOperation
          v-if="readonly || !pc.editing"
          :items="operationItems(pc.items)"
          :remark="pc.operationRemark"
        />
        <template v-else>
          <!-- 2026-10-22：作业说明改为只读展示（在工艺路线明细维护），不再双击弹窗编辑（dev-20261022-011） -->
          <el-tag
            v-for="(it, ii) in pc.items"
            :key="ii"
            size="small"
            :closable="!readonly && pc.editing"
            :disable-transitions="false"
            class="process-item-tag"
            @close="$emit('remove-item', Number(ii))"
          >
            <IconStepBadge
              v-if="it.icon"
              :icon="it.icon || ''"
              :size="16"
              :work-instruction="it.workInstruction"
            />
            <span v-else>{{ it.processName }}</span>
          </el-tag>
          <el-button
            v-if="!readonly && pc.editing"
            size="small"
            link
            type="primary"
            @click="$emit('open-picker')"
            >＋ 添加标准工序</el-button
          >
          <span v-if="!pc.items.length" style="color: #c0c4cc; font-size: 12px"
            >未选择标准工序</span
          >
        </template>
      </div>
    </div>
</template>
<script setup lang="ts">
import IconStepBadge from '@/components/IconStepBadge/index.vue'
import ProcessOperation from '@/components/ProcessOperation/index.vue'
import type { ProcessOperationItem } from '@/components/ProcessOperation/types'
defineProps<{ pc: any; readonly?: boolean }>()
defineEmits<{
  (e: 'remove-item', index: number): void
  (e: 'update-index', item: any, value: number): void
  (e: 'open-picker'): void
}>()
function operationItems(items: any[]): ProcessOperationItem[] {
  return (items || []).map((item, index) => ({
    key: item.itemId ?? index,
    icon: item.icon,
    processName: item.processName,
    indexNumber: item.hasIndex === 1 ? item.indexNumber : null,
    hasWorkInstruction: item.hasWorkInstruction,
    workInstruction: item.workInstruction,
  }))
}

</script>
<style scoped>
.pc-row { display: flex; gap: 10px; align-items: flex-start; margin-bottom: 8px; }
.pc-items { flex: 1; min-width: 0; }
.process-item-tag { margin-right: 6px; margin-bottom: 4px; cursor: default; }
</style>
