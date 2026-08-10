<template>
  <div class="icon-step-badge" @click="openInput">
    <SvgIcon :name="icon" :size="size" />
    <!-- 下标数字（index 模式：直接显示 indexNumber；description 模式：解析 <jump>N</jump>） -->
    <span v-if="stepNum !== null" class="step-badge" :class="{ 'is-zero': stepNum === 0 }" @click.stop="onJump">
      {{ stepNum }}
    </span>

    <!-- 输入弹层 -->
    <el-popover
      :visible="inputVisible"
      placement="top"
      :width="220"
      :trigger="popTrigger"
      popper-class="icon-step-popover"
    >
      <div style="display: flex; align-items: center; gap: 8px">
        <el-input-number
          v-model="draftNum"
          :min="0"
          :max="999"
          controls-position="right"
          style="width: 110px"
          size="small"
        />
        <el-button type="primary" size="small" :loading="saving" @click="confirm">确定</el-button>
      </div>
      <div style="font-size: 12px; color: #909399; margin-top: 4px">
        <template v-if="useIndexMode">
          输入下标数字（如 4 显示为 ④），保存到工艺路线明细 index_number
        </template>
        <template v-else>
          输入数字将写入描述 <code>&lt;jump&gt;N&lt;/jump&gt;</code>，代表跳转到对应步骤
        </template>
      </div>
      <template #reference>
        <span style="display: none"></span>
      </template>
    </el-popover>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import SvgIcon from '@/components/SvgIcon/index.vue'

/**
 * 作业项目图标 + 可编辑步骤下标（2026-08-09 创建，2026-08-10 支持 index 模式）
 *
 * 两种模式：
 *  - description 模式（默认）：description 中存富文本标记 <jump>N</jump>，
 *    渲染时解析出下标数字显示在图标右下角；点击图标弹输入框改数字 → 更新 description
 *  - index 模式（传 index prop）：直接显示 indexNumber 值，点击图标改数字 →
 *    emit('update:index') 由父组件写入 routing_item.index_number
 *  - 点击下标数字 → emit jump（页面挂空函数，现阶段不实现跳转）
 */
const props = defineProps<{
  icon: string
  /** 描述文本（可能含 <jump>N</jump> 标记） */
  description?: string
  size?: number
  /** index 模式：显式下标数字（优先于 description 解析） */
  index?: number | null
}>()

const emit = defineEmits<{
  (e: 'update-description', value: string): void
  (e: 'update:index', value: number): void
  (e: 'jump', step: number): void
}>()

const inputVisible = ref(false)
const draftNum = ref<number | null>(null)
const saving = ref(false)
// el-popover manual 触发（类型兜底）
const popTrigger = 'manual' as any

// index 模式判定
const useIndexMode = computed(() => props.index !== undefined && props.index !== null)

// 解析下标：index 模式直接用 index；否则解析 <jump>N</jump>
const stepNum = computed<number | null>(() => {
  if (props.index !== undefined && props.index !== null) return Number(props.index)
  const m = (props.description || '').match(/<jump>(\d+)<\/jump>/)
  return m ? Number(m[1]) : null
})

function openInput() {
  draftNum.value = stepNum.value
  inputVisible.value = true
}

// 更新 description：有 <jump>N</jump> 则替换数字，无则追加到末尾（富文本标记保留，不被去除）
function buildDescription(step: number): string {
  const desc = props.description || ''
  if (/<jump>\d+<\/jump>/.test(desc)) {
    return desc.replace(/<jump>\d+<\/jump>/, `<jump>${step}</jump>`)
  }
  return `${desc} <jump>${step}</jump>`.trim()
}

async function confirm() {
  if (draftNum.value === null) return
  saving.value = true
  try {
    if (useIndexMode.value) {
      emit('update:index', draftNum.value)
    } else {
      emit('update-description', buildDescription(draftNum.value))
    }
    inputVisible.value = false
  } finally {
    saving.value = false
  }
}

// 跳转（页面挂空函数）
function onJump() {
  if (stepNum.value !== null) emit('jump', stepNum.value)
}
</script>

<style scoped>
.icon-step-badge {
  position: relative;
  display: inline-flex;
  cursor: pointer;
  line-height: 0;
}

.step-badge {
  position: absolute;
  right: -6px;
  bottom: -6px;
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  border-radius: 8px;
  background: #f56c6c;
  color: #fff;
  font-size: 10px;
  font-weight: 600;
  line-height: 16px;
  text-align: center;
  cursor: pointer;
  box-shadow: 0 0 0 1px #fff;
}

.step-badge.is-zero {
  background: #909399;
}
</style>
