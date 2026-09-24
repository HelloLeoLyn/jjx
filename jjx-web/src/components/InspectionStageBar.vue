<template>
  <div class="inspection-stage-bar">
    <div
      v-for="(stage, index) in stages"
      :key="stage"
      class="stage-item"
      :class="stageClass(index)"
    >
      <span class="stage-no">{{ index + 1 }}</span>
      <span class="stage-text">{{ stage }}</span>
      <span v-if="index < stages.length - 1" class="stage-arrow">→</span>
    </div>
    <span v-if="hint" class="stage-hint">{{ hint }}</span>
  </div>
</template>

<script setup lang="ts">
/**
 * 检验流程阶段条（dev-20260924-017 P2）
 * 把"录入 → 提交 → 复核 → 入库"显式化：一眼看出当前卡在哪一步、下一步干嘛。
 * 只做展示，不做门禁（门禁仍由后端 allowedActions / 权限点决定，前端不重复写状态条件）。
 */
const props = defineProps<{
  /** 阶段名列表，如 ['录入检验','提交检验','主管复核','确认入库'] */
  stages: string[]
  /** 当前阶段下标（0 起）；小于它的算已完成 */
  current: number
  /** 右侧补充说明，如「本单：3 行待录入 / 2 行待复核」 */
  hint?: string
}>()

function stageClass(index: number) {
  return {
    done: index < props.current,
    active: index === props.current,
    todo: index > props.current,
  }
}
</script>

<style scoped>
.inspection-stage-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 12px;
  margin-bottom: 12px;
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
  font-size: 13px;
}
.stage-item {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-text-color-secondary);
}
.stage-no {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  font-size: 12px;
  line-height: 1;
  background: var(--el-fill-color-dark);
  color: var(--el-text-color-secondary);
}
.stage-arrow {
  margin: 0 2px 0 6px;
  color: var(--el-text-color-placeholder);
}
.stage-item.active {
  color: var(--el-color-primary);
  font-weight: 600;
}
.stage-item.active .stage-no {
  background: var(--el-color-primary);
  color: #fff;
}
.stage-item.done {
  color: var(--el-color-success);
}
.stage-item.done .stage-no {
  background: var(--el-color-success);
  color: #fff;
}
.stage-hint {
  margin-left: auto;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
