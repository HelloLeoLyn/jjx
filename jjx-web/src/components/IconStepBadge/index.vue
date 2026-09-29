<template>
  <el-popover
    v-model:visible="inputVisible"
    :disabled="!canEdit"
    placement="bottom-start"
    :fallback-placements="['top-start', 'bottom-end', 'top-end']"
    :teleported="true"
    :offset="4"
    :width="300"
    trigger="click"
    popper-class="icon-step-popover"
  >
    <div @keydown.esc.stop="inputVisible = false">
      <template v-if="editWorkInstruction">
        <el-input
          v-model="draftInstruction"
          clearable
          maxlength="80"
          placeholder="作业说明，如：冲窗口灯孔"
          @keydown.enter.prevent="confirm"
        />
        <div class="common-work-instructions">
          <span>常用：</span>
          <el-button
            v-for="text in commonWorkInstructions"
            :key="text"
            size="small"
            @click="draftInstruction = text"
            >{{ text }}</el-button
          >
        </div>
      </template>
      <template v-else>
        <el-input-number
          v-model="draftNum"
          :min="1"
          :max="999"
          :precision="0"
          controls-position="right"
          style="width: 100%"
          size="small"
          @keydown.enter.prevent="confirm"
        />
      </template>
      <div class="edit-actions">
        <el-button size="small" @click="inputVisible = false">取消</el-button>
        <el-button type="primary" size="small" :disabled="!draftValid" @click="confirm"
          >确定</el-button
        >
      </div>
    </div>
    <template #reference>
      <div class="icon-step-badge">
        <SvgIcon :name="icon" :size="size" />
        <span v-if="subscriptText" class="subscript" :title="subscriptText" @click="onJump">
          {{ subscriptText }}
        </span>
      </div>
    </template>
  </el-popover>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import SvgIcon from '@/components/SvgIcon/index.vue'

/**
 * 标准工序图标 + 可编辑步骤下标（2026-08-09 创建，2026-08-10 支持 index 模式）
 *
 * 两种模式：
 *  - description 模式（默认）：description 中存富文本标记 <jump>N</jump>，
 *    渲染时解析出下标数字显示在图标右下角；点击图标弹输入框改数字 → 更新 description
 *  - index 模式（传 index prop）：直接显示 indexNumber 值，点击图标改数字 →
 *    emit('update:index') 由父组件写入 routing_item.index_number
 *  - 作业说明通过 editWorkInstruction 显式启用；统一草稿、确认和取消。
 *  - 只读时点击下标数字 → emit jump。
 */
const props = defineProps<{
  icon: string
  /** 描述文本（可能含 <jump>N</jump> 标记） */
  description?: string
  size?: number
  /** index 模式：显式下标数字（优先于 description 解析） */
  index?: number | null
  /** 本次工序作业说明，按工程图习惯显示在图标下标位置 */
  workInstruction?: string
  /** 是否允许编辑数字下标 */
  editable?: boolean
  /** 使用同一弹层编辑作业说明，默认仍为数字下标模式 */
  editWorkInstruction?: boolean
}>()

const emit = defineEmits<{
  (e: 'update-description', value: string): void
  (e: 'update:index', value: number): void
  (e: 'jump', step: number): void
  (e: 'update:work-instruction', value: string): void
}>()

const inputVisible = ref(false)
const draftNum = ref<number | null>(null)
const draftInstruction = ref('')
const commonWorkInstructions = [
  '线路外形',
  '冲窗口灯孔',
  '一车一模',
  '一车二模',
  '撕保护膜',
  '贴保护膜',
]
const canEdit = computed(
  () => props.editable !== false && (props.editWorkInstruction || props.index !== undefined)
)
const draftValid = computed(
  () =>
    props.editWorkInstruction ||
    (draftNum.value != null &&
      Number.isInteger(draftNum.value) &&
      draftNum.value >= 1 &&
      draftNum.value <= 999)
)

// index 模式判定
const useIndexMode = computed(() => props.index !== undefined)

// 解析下标：index 模式直接用 index；否则解析 <jump>N</jump>
const stepNum = computed<number | null>(() => {
  if (props.index !== undefined && props.index !== null) return Number(props.index)
  const m = (props.description || '').match(/<jump>(\d+)<\/jump>/)
  return m ? Number(m[1]) : null
})

const subscriptText = computed(
  () => props.workInstruction || (stepNum.value !== null ? String(stepNum.value) : '')
)

function openInput() {
  draftNum.value = stepNum.value
  draftInstruction.value = props.workInstruction || ''
}

watch(
  inputVisible,
  (visible) => {
    if (visible) openInput()
  },
  { flush: 'sync' }
)

// 更新 description：有 <jump>N</jump> 则替换数字，无则追加到末尾（富文本标记保留，不被去除）
function buildDescription(step: number): string {
  const desc = props.description || ''
  if (/<jump>\d+<\/jump>/.test(desc)) {
    return desc.replace(/<jump>\d+<\/jump>/, `<jump>${step}</jump>`)
  }
  return `${desc} <jump>${step}</jump>`.trim()
}

function confirm() {
  if (!canEdit.value || !draftValid.value) return
  if (props.editWorkInstruction) {
    emit('update:work-instruction', draftInstruction.value.trim())
  } else if (draftNum.value != null) {
    if (useIndexMode.value) emit('update:index', draftNum.value)
    else emit('update-description', buildDescription(draftNum.value))
  }
  inputVisible.value = false
}

// 跳转（页面挂空函数）
function onJump() {
  if (!canEdit.value && stepNum.value !== null) emit('jump', stepNum.value)
}
</script>

<style scoped>
.common-work-instructions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
}
.common-work-instructions .el-button {
  margin-left: 0;
}
.edit-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}
.icon-step-badge {
  position: relative;
  display: inline-flex;
  cursor: pointer;
  line-height: 0;
}

.subscript {
  position: absolute;
  left: 95%;
  bottom: 0;
  min-width: unset;
  width: auto;
  height: auto;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--el-text-color-regular);
  font-size: 10px;
  font-weight: 400;
  line-height: 14px;
  text-align: left;
  box-shadow: none;
  max-width: 96px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
