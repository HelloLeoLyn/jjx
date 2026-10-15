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
          <el-tooltip
            v-for="(it, ii) in pc.items"
            :key="ii"
            content="双击编辑作业说明"
            placement="top"
            :disabled="!(it.hasWorkInstruction === 1 || it.workInstruction)"
          >
            <span class="instruction-trigger" @dblclick.stop="openInstructionEditor(it)">
              <el-tag
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
                  :index="it.hasIndex === 1 ? (it.indexNumber ?? null) : undefined"
                  :work-instruction="it.workInstruction"
                  @update:index="(n: number) => $emit('update-index', it, n)"
                />
                <span v-else>{{ it.processName }}</span>
              </el-tag>
            </span>
          </el-tooltip>
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
    <el-dialog
      v-model="instructionDialogVisible"
      title="编辑作业说明"
      width="420px"
      append-to-body
      @closed="instructionEditingItem = null"
    >
      <el-input
        v-model="instructionDraft"
        type="textarea"
        :rows="3"
        maxlength="80"
        show-word-limit
        autofocus
        placeholder="作业说明（可选，如：线路外形）"
        @keyup.esc="closeInstructionEditor"
      />
      <template #footer>
        <el-button @click="closeInstructionEditor">取消</el-button>
        <el-button type="primary" @click="saveInstructionEditor">确定</el-button>
      </template>
    </el-dialog>
</template>
<script setup lang="ts">
import { ref } from 'vue'
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

const instructionEditingItem = ref<any | null>(null)
const instructionDialogVisible = ref(false)
const instructionDraft = ref('')

function openInstructionEditor(item: any) {
  instructionEditingItem.value = item
  instructionDraft.value = item.workInstruction || ''
  instructionDialogVisible.value = true
}

function closeInstructionEditor() {
  instructionEditingItem.value = null
  instructionDialogVisible.value = false
}

function saveInstructionEditor() {
  if (instructionEditingItem.value) {
    instructionEditingItem.value.workInstruction = instructionDraft.value.trim()
  }
  closeInstructionEditor()
}

</script>
<style scoped>
.pc-row { display: flex; gap: 10px; align-items: flex-start; margin-bottom: 8px; }
.pc-items { flex: 1; min-width: 0; }
.process-item-tag { margin-right: 6px; margin-bottom: 4px; cursor: default; }
.instruction-trigger { display: inline-flex; cursor: text; }
</style>
