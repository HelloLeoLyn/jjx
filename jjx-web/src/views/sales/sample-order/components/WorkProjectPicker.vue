<template>
  <div class="work-project-picker">
    <!-- 搜索 -->
    <div class="picker-search">
      <el-input
        v-model="searchKey"
        placeholder="🔍 搜索工序名称"
        clearable
        size="small"
        prefix-icon="Search"
      />
    </div>

    <!-- 项目结构 Tab（工序列表在 tab 内容区内，默认面板） -->
    <el-tabs v-model="activeStructTab" type="border-card" size="small" class="struct-tabs">
      <el-tab-pane
        v-for="s in categoryOptions"
        :key="s.itemValue"
        :label="`${s.label}（${groupCount(s.itemValue)}）`"
        :name="s.itemValue"
      >
        <div class="picker-grid">
          <template v-for="p in processes" :key="p.processId">
            <div
              v-if="
                p.processCategory === s.itemValue &&
                (!searchKey || (p.processName || '').includes(searchKey))
              "
              class="proc-item"
              :class="{ selected: isSelected(p.processId) }"
              :draggable="!readonly"
              @click="!readonly && toggle(p)"
              @dragstart="!readonly && onDragStart($event, p)"
              title="拖拽到右侧工序卡片"
            >
              <SvgIcon v-if="p.icon" :name="p.icon" :size="26" />
              <span v-else class="proc-emoji">📦</span>
              <span class="proc-name">{{ p.processName }}</span>
              <span class="proc-type">{{ typeLabel(p.processType) }}</span>
            </div>
          </template>
          <el-empty
            v-if="!groupCount(s.itemValue)"
            description="该结构下暂无标准工序"
            :image-size="60"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <!-- 底部 -->
    <div class="picker-footer">
      <span class="picker-count"
        >已选 <b>{{ (modelValue || []).length }}</b> 个</span
      >
      <el-button type="primary" size="small" :disabled="readonly" @click="handleConfirm"
        >确定选择</el-button
      >
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { standardProcessApi } from '@/api/product/standardProcess'
import { useDict } from '@/composables/useDict'
import type { StandardProcessItem } from '@/types/product/standardProcess'

const props = defineProps<{
  /** 已选标准工序ID列表（可选，不传=只浏览/拖拽） */
  modelValue?: number[]
  readonly?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: number[]): void
  (e: 'confirm', items: StandardProcessItem[]): void
}>()

// 项目结构/作业类型（字典）
const { options: categoryOptions } = useDict('process_category')
const { options: typeOptions } = useDict('process_type')

const activeStructTab = ref('')

// 默认选中第一个结构（面板）；字典加载后设置
watch(
  () => categoryOptions.value,
  (opts) => {
    if (!activeStructTab.value && opts && opts.length) {
      activeStructTab.value = opts[0].itemValue
    }
  },
  { immediate: true }
)
const searchKey = ref('')
const processes = ref<StandardProcessItem[]>([])
const loading = ref(false)

function typeLabel(value: string): string {
  return typeOptions.value.find((i) => i.itemValue === value)?.label || value || ''
}

// Tab 切换即筛选（不再需要 setStruct 点击切换）

function groupCount(struct: string): number {
  return processes.value.filter((p) => p.processCategory === struct).length
}

// 已选标准工序详情（确认时带回给父组件）
const selectedItems = computed(() =>
  processes.value.filter((p) => (props.modelValue || []).includes(p.processId))
)

function handleConfirm() {
  emit('confirm', selectedItems.value)
}

// 拖拽开始：把工序数据写入 dataTransfer
function onDragStart(e: DragEvent, p: StandardProcessItem) {
  e.dataTransfer?.setData(
    'application/json',
    JSON.stringify({
      processId: p.processId,
      processName: p.processName,
      processType: p.processType,
      processCategory: p.processCategory,
      icon: p.icon,
    })
  )
  if (e.dataTransfer) e.dataTransfer.effectAllowed = 'copy'
}

function isSelected(id: number): boolean {
  return (props.modelValue || []).includes(id)
}

function toggle(p: StandardProcessItem) {
  const id = p.processId
  const list = [...(props.modelValue || [])]
  const idx = list.indexOf(id)
  if (idx >= 0) {
    list.splice(idx, 1)
  } else {
    list.push(id)
  }
  emit('update:modelValue', list)
}

async function loadProcesses() {
  loading.value = true
  try {
    const res = await standardProcessApi.pageQuery({
      pageNum: 1,
      pageSize: 100,
      isEnabled: 1,
      orderByColumn: 'displayOrder',
      isAsc: 'asc',
    })
    processes.value = res.data?.records || []
  } catch (error) {
    console.error('加载标准工序失败:', error)
  } finally {
    loading.value = false
  }
}

onMounted(loadProcesses)
</script>

<style scoped>
.work-project-picker {
  padding: 4px 0;
}

.picker-search {
  margin-bottom: 10px;
}

.picker-step-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.picker-step-label b {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  font-size: 11px;
  margin-right: 4px;
}

.picker-structs {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 12px;
}

.struct-btn {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 8px 4px;
  text-align: center;
  cursor: pointer;
  transition: all 0.15s;
  background: #fff;
}

.struct-btn:hover {
  border-color: #409eff;
}

.struct-btn.active {
  border-color: #409eff;
  background: #ecf5ff;
  box-shadow: 0 0 0 2px rgba(64, 158, 255, 0.15);
}

.struct-name {
  font-size: 13px;
  font-weight: 600;
  display: block;
  color: #303133;
}

.struct-btn.active .struct-name {
  color: #409eff;
}

.struct-count {
  font-size: 11px;
  color: #c0c4cc;
  display: block;
  margin-top: 2px;
}

.picker-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  max-height: 300px;
  overflow-y: auto;
  padding-right: 4px;
}

.proc-item {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 10px 4px;
  text-align: center;
  cursor: pointer;
  transition: all 0.15s;
}

.proc-item:hover {
  border-color: #409eff;
  background: #f5f9ff;
}

.proc-item.selected {
  border-color: #409eff;
  background: #ecf5ff;
}

.proc-emoji {
  font-size: 26px;
  line-height: 1;
}

.proc-name {
  font-size: 11px;
  color: #606266;
  margin-top: 6px;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.proc-type {
  font-size: 10px;
  color: #a8abb2;
  display: block;
  margin-top: 2px;
}

.proc-item.selected .proc-name {
  color: #409eff;
}

.picker-footer {
  margin-top: 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* 项目结构 Tab（2026-08-10 按钮网格改 Tab） */
.struct-tabs {
  margin-bottom: 12px;
}

.struct-tabs :deep(.el-tabs__header) {
  margin-bottom: 0;
}

.struct-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
}

.struct-tabs :deep(.el-tabs__item) {
  padding: 0 10px;
  height: 32px;
  line-height: 32px;
  font-size: 12px;
}

.picker-count {
  font-size: 12px;
  color: #606266;
}

.picker-count b {
  color: #409eff;
}
</style>
