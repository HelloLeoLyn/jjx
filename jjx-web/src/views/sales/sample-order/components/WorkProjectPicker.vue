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

    <!-- 第一步：项目结构（多选筛选，仅用于归类浏览，不限制选工序） -->
    <div class="picker-step-label"><b>1</b> 项目结构（可多选，不选=全部）</div>
    <div class="picker-structs">
      <div
        v-for="s in categoryOptions"
        :key="s.itemValue"
        class="struct-btn"
        :class="{ active: activeStruct === s.itemValue }"
        @click="setStruct(s.itemValue)"
      >
        <span class="struct-name">{{ s.label }}</span>
        <span class="struct-count">{{ groupCount(s.itemValue) }}</span>
      </div>
    </div>

    <!-- 第二步：工序多选 -->
    <div class="picker-step-label"><b>2</b> 勾选作业项目（可多选）</div>
    <div class="picker-grid">
      <div
        v-for="p in filteredProcesses"
        :key="p.processId"
        class="proc-item"
        :class="{ selected: isSelected(p.processId) }"
        draggable="true"
        @click="toggle(p)"
        @dragstart="onDragStart($event, p)"
        title="拖拽到右侧工序卡片"
      >
        <SvgIcon v-if="p.icon" :name="p.icon" :size="26" />
        <span v-else class="proc-emoji">📦</span>
        <span class="proc-name">{{ p.processName }}</span>
        <span class="proc-type">{{ typeLabel(p.processType) }}</span>
      </div>
      <el-empty
        v-if="!filteredProcesses.length"
        description="该结构下暂无作业项目"
        :image-size="60"
      />
    </div>

    <!-- 底部 -->
    <div class="picker-footer">
      <span class="picker-count">已选 <b>{{ (modelValue || []).length }}</b> 个</span>
      <el-button type="primary" size="small" @click="handleConfirm">确定选择</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { standardProcessApi } from '@/api/product/standardProcess'
import { useDict } from '@/composables/useDict'
import type { StandardProcessItem } from '@/types/product/standardProcess'

const props = defineProps<{
  /** 已选作业项目ID列表（可选，不传=只浏览/拖拽） */
  modelValue?: number[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: number[]): void
  (e: 'confirm', items: StandardProcessItem[]): void
}>()

// 项目结构/作业类型（字典）
const { options: categoryOptions } = useDict('process_category')
const { options: typeOptions } = useDict('process_type')

const activeStruct = ref('')
const searchKey = ref('')
const processes = ref<StandardProcessItem[]>([])
const loading = ref(false)

function typeLabel(value: string): string {
  return typeOptions.value.find((i) => i.itemValue === value)?.label || value || ''
}

// 结构单选：点击选中，再点取消（不选=全部）
function setStruct(value: string) {
  activeStruct.value = activeStruct.value === value ? '' : value
}

function groupCount(struct: string): number {
  return processes.value.filter((p) => p.processCategory === struct).length
}

const filteredProcesses = computed(() => {
  return processes.value.filter((p) => {
    if (activeStruct.value && p.processCategory !== activeStruct.value) return false
    if (searchKey.value && !(p.processName || '').includes(searchKey.value)) return false
    return true
  })
})

// 已选作业项目详情（确认时带回给父组件）
const selectedItems = computed(() =>
  processes.value.filter((p) => (props.modelValue || []).includes(p.processId)),
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
    }),
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
    console.error('加载作业项目失败:', error)
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

.picker-count {
  font-size: 12px;
  color: #606266;
}

.picker-count b {
  color: #409eff;
}
</style>
