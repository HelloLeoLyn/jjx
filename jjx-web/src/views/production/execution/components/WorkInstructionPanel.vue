<template>
  <div v-if="fields.length" class="work-instruction">
    <div class="work-instruction__title">
      {{ title || '作业说明' }}
      <el-tag v-if="isRework" type="danger" size="small" effect="plain">返工</el-tag>
    </div>
    <div v-if="isRework" class="work-instruction__hint">返工内容以本说明为准</div>
    <div
      v-if="frameTag"
      class="work-instruction__warn"
      :class="{ 'is-danger': frameTag.type === 'danger' }"
    >
      ⚠ 网框 {{ parsed.screenNo }}：{{ frameTag.label }}，生产前请确认
    </div>
    <div v-for="field in fields" :key="field.label" class="work-instruction__row">
      <span class="work-instruction__label">{{ field.label }}</span>
      <span class="work-instruction__value">{{ field.value }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 作业说明（区块 = 组件 = 文案同名）—— dev-20260929-022
 *
 * 数据源：production_operation_execution.custom_process_params（后端已原样返回的 JSON 串，无需新接口）。
 * 兼容两类工序：
 *   · 返工工序：reworkRequirement（返工要求）+ ncrNo（不良单号，便于追溯）+ 质量标准/技能要求
 *   · 普通工序：description（工序说明）/ qualityStandard / skillRequirement / processParamTemplate
 * 只读展示；无任何内容时整块不渲染（不占位、不显示空壳）。
 */
import { computed, onMounted } from 'vue'
import { ensureFrames, frameStatusOf } from '@/composables/usePrintFieldSuggest'

const props = defineProps<{
  /** 后端返回的 customProcessParams（JSON 字符串，或已解析对象） */
  params?: string | Record<string, any> | null
  /** 区块标题，默认「作业说明」 */
  title?: string
}>()

const parsed = computed<Record<string, any>>(() => {
  const raw = props.params
  if (!raw) return {}
  if (typeof raw === 'object') return raw as Record<string, any>
  try {
    const obj = JSON.parse(String(raw))
    return obj && typeof obj === 'object' ? obj : {}
  } catch {
    return {}
  }
})

const isRework = computed(() => Boolean(parsed.value.ncrNo || parsed.value.reworkRequirement))

// 网框状态警示（软提示）：印刷工序若引用了网框，生产端提醒其当前状态（未制版/维护中/已报废）
const frameTag = computed(() => {
  const no = parsed.value.screenNo
  return no ? frameStatusOf(String(no)) : null
})

onMounted(() => {
  if (parsed.value.screenNo) ensureFrames()
})

const fields = computed(() => {
  const p = parsed.value
  const rows: Array<{ label: string; value: string }> = []
  const push = (label: string, value: unknown) => {
    const text = value == null ? '' : String(value).trim()
    if (text) rows.push({ label, value: text })
  }
  push('不良单号', p.ncrNo)
  push('返工要求', p.reworkRequirement)
  push('处置备注', p.disposeRemark)
  push('质量标准', p.qualityStandard)
  push('技能要求', p.skillRequirement)
  push('工序说明', p.description)
  push('工艺参数', p.processParamTemplate)
  // 印刷工序参数（dev-20261009-026）
  push('印刷名称', p.printName)
  push('色号', p.colorNo)
  push('油墨', p.inkNo)
  push('菲林', p.filmNo)
  push('网框', p.screenNo)
  return rows
})
</script>

<style scoped>
.work-instruction {
  padding: 8px 10px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  line-height: 1.8;
}
.work-instruction__title {
  font-weight: 600;
}
.work-instruction__hint {
  color: var(--el-color-danger);
  font-size: 12px;
}
.work-instruction__warn {
  margin: 2px 0;
  padding: 2px 8px;
  border-radius: 3px;
  font-size: 12px;
  color: var(--el-color-warning);
  background: var(--el-color-warning-light-9);
}
.work-instruction__warn.is-danger {
  color: var(--el-color-danger);
  background: var(--el-color-danger-light-9);
}
.work-instruction__row {
  display: flex;
  gap: 8px;
  font-size: 13px;
}
.work-instruction__label {
  min-width: 76px;
  color: var(--el-text-color-secondary);
}
.work-instruction__value {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
