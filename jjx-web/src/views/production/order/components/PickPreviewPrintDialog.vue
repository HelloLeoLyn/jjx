<template>
  <el-dialog
    :model-value="modelValue"
    title="领料单 - 打印预览"
    width="900px"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div class="print-columns">
      <el-radio-group v-model="printMode" size="small">
        <el-radio-button value="mode1">模式一：一级汇总</el-radio-button>
        <el-radio-button value="mode2">模式二：一级+二级</el-radio-button>
      </el-radio-group>
      <el-popover placement="bottom-start" :width="320" trigger="click">
        <template #reference><el-button>列设置</el-button></template>
        <el-button link type="primary" @click="selectedColumns = allPickPrintColumns()"
          >全选</el-button
        >
        <el-button link type="primary" @click="selectedColumns = allPickPrintColumns()"
          >恢复默认</el-button
        >
        <el-checkbox-group v-model="selectedColumns" class="column-options">
          <el-checkbox v-for="column in PICK_PRINT_COLUMNS" :key="column.key" :value="column.key">{{
            column.label
          }}</el-checkbox>
        </el-checkbox-group>
      </el-popover>
      <span v-if="!selectedColumns.length">请至少选择一列打印内容</span>
    </div>
    <iframe
      ref="printFrame"
      class="pick-print-frame"
      title="领料单 A4 打印预览"
      :srcdoc="printHtml"
      @load="ready = true"
    />
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button type="primary" :disabled="!ready || !selectedColumns.length" @click="handlePrint"
        >打印</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  buildPickPreviewHtml,
  type PickPreviewPrintData,
  PICK_PRINT_COLUMNS,
  PICK_PRINT_STORAGE_KEY,
  allPickPrintColumns,
  readPickPrintColumns,
  normalizePickPrintColumns,
} from './pickPreviewPrint'

const props = defineProps<PickPreviewPrintData & { modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const printFrame = ref<HTMLIFrameElement>()
const ready = ref(false)
const selectedColumns = ref<string[]>(readPickPrintColumns())
watch(selectedColumns, (keys) => {
  try {
    localStorage.setItem(PICK_PRINT_STORAGE_KEY, JSON.stringify(normalizePickPrintColumns(keys)))
  } catch {
    // 浏览器禁用存储时，仅不记忆；本次列选择仍然生效。
  }
})
const printMode = ref<'mode1' | 'mode2'>('mode2')
const printRows = computed(() => {
  const rows = props.rows || []
  if (printMode.value === 'mode2') return rows
  // 模式一：只打一级汇总；没有层级信息时按全部处理
  return rows.filter((row) => !row.level || row.level === 1)
})
const printHtml = computed(() =>
  buildPickPreviewHtml({ ...props, rows: printRows.value, visibleColumns: selectedColumns.value })
)
watch(printHtml, () => {
  ready.value = false
})

function handlePrint() {
  if (!ready.value || !selectedColumns.value.length || !printFrame.value?.contentWindow) return
  printFrame.value.contentWindow.focus()
  printFrame.value.contentWindow.print()
}
</script>

<style scoped>
.print-columns {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.column-options {
  display: flex;
  flex-direction: column;
}
.pick-print-frame {
  display: block;
  width: 100%;
  height: 65vh;
  border: 1px solid #dcdfe6;
}
</style>
