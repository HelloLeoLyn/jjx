<template>
  <div class="status-flow-bar">
    <el-steps :active="activeIndex" align-center finish-status="success" class="flow-steps">
      <el-step
        v-for="(step, idx) in steps"
        :key="step.key"
        :title="step.label"
        :status="idx < activeIndex ? 'finish' : idx === activeIndex ? 'process' : 'wait'"
      >
        <template #icon>
          <el-icon v-if="idx < activeIndex"><Check /></el-icon>
          <span v-else>{{ idx + 1 }}</span>
        </template>
      </el-step>
    </el-steps>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Check } from '@element-plus/icons-vue'

export interface FlowStep {
  key: string | number
  label: string
  /** 可选：该状态对应标签颜色 */
  type?: 'info' | 'warning' | 'success' | 'danger' | 'primary'
}

const props = defineProps<{
  /** 状态流转步骤配置（调用方传入） */
  steps: FlowStep[]
  /** 当前状态 key */
  current?: string | number
}>()

// 当前状态在步骤中的下标；未命中则显示最后已完成的位置
const activeIndex = computed(() => {
  const idx = props.steps.findIndex((s) => s.key === props.current)
  return idx >= 0 ? idx : Math.max(props.steps.length - 1, 0)
})
</script>

<style scoped>
.status-flow-bar {
  padding: 12px 8px 4px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #eef1f5;
  margin-bottom: 12px;
}
.flow-steps {
  --el-step-icon-size: 26px;
  --el-step-title-font-size: 13px;
}
</style>
