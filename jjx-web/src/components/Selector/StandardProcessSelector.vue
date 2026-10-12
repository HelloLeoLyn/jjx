<template>
  <!-- 模式一：下拉（el-select 分组 + 图标） -->
  <el-select
    v-if="mode === 'dropdown'"
    :model-value="currentValue"
    :placeholder="placeholder"
    :size="size"
    :disabled="disabled"
    :clearable="clearable"
    filterable
    :allow-create="allowCreate"
    default-first-option
    style="width: 100%"
    @change="onDropdownChange"
    @clear="onClear"
  >
    <!-- 历史/新建的纯名称（未关联工序 ID）回显 -->
    <el-option v-if="fallbackName" :label="fallbackName" :value="fallbackName" hidden />
    <el-option-group v-for="g in groups" :key="g.value" :label="g.label">
      <el-option
        v-for="p in g.items"
        :key="p.processId"
        :label="p.processName"
        :value="p.processId"
      >
        <div class="sps-option">
          <SvgIcon v-if="p.icon" :name="p.icon" :size="16" />
          <span class="sps-option-name">{{ p.processName }}</span>
        </div>
      </el-option>
    </el-option-group>
  </el-select>

  <!-- 模式二：弹窗（点击展开分组网格） -->
  <div
    v-else
    class="sps-trigger"
    :class="{ 'is-disabled': disabled, 'is-empty': !showName }"
    @click="openDialog"
  >
    <SvgIcon v-if="showIcon" :name="showIcon" :size="16" class="sps-trigger-icon" />
    <span v-if="showName" class="sps-trigger-name">{{ showName }}</span>
    <span v-else class="sps-trigger-ph">{{ placeholder }}</span>
    <el-icon
      v-if="clearable && !disabled && showName"
      class="sps-trigger-clear"
      @click.stop="onClear"
    >
      <CircleClose />
    </el-icon>
  </div>

  <!-- 选择弹窗（两个模式共用） -->
  <el-dialog v-model="dialogVisible" :title="dialogTitle" :width="width" append-to-body>
    <el-input
      v-model="keyword"
      placeholder="搜索项目名称 / 编码"
      clearable
      :prefix-icon="Search"
      class="sps-search"
    />
    <el-tabs v-model="activeTab" class="sps-tabs">
      <el-tab-pane
        v-for="g in groups"
        :key="g.value"
        :name="g.value"
        :label="`${g.label}（${g.items.length}）`"
      >
        <div class="sps-grid">
          <div
            v-for="p in filterItems(g.items)"
            :key="p.processId"
            class="sps-card"
            :class="{ 'is-active': p.processId === currentId }"
            @click="pick(p)"
          >
            <SvgIcon v-if="p.icon" :name="p.icon" :size="28" />
            <span v-else class="sps-card-emoji">📦</span>
            <span class="sps-card-name">{{ p.processName }}</span>
          </div>
          <el-empty v-if="!filterItems(g.items).length" description="无匹配项目" :image-size="56" />
        </div>
      </el-tab-pane>
    </el-tabs>
    <div v-if="canCreate" class="sps-create">
      <el-button link type="primary" :icon="Plus" @click="createFromKeyword">
        + 新建项目「{{ keyword.trim() }}」
      </el-button>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { CircleClose, Plus, Search } from '@element-plus/icons-vue'
import SvgIcon from '@/components/SvgIcon/index.vue'
import { standardProcessApi } from '@/api/product/standardProcess'
import { useDict } from '@/composables/useDict'
import type { StandardProcessItem } from '@/types/product/standardProcess'

/**
 * 项目（标准工序）选择器 —— 公共组件
 * 两个交互模式，由 mode 切换：
 *   - dropdown：表格内联下拉（分组 + 图标，可搜可新建）
 *   - dialog  ：点击弹出分组网格弹窗（一屏看全，图标直观）
 * 分组来自字典 process_category：面板 / 上线 / 下线 / 其他。
 * v-model 语义：工序ID(number) 或 纯名称(string，历史/新建)。
 */
const props = withDefaults(
  defineProps<{
    /** 值：工序ID（number）或纯名称（string） */
    modelValue?: number | string | null
    /** 名称回显（modelValue 为 ID 时提供） */
    processName?: string
    /** 交互模式 */
    mode?: 'dropdown' | 'dialog'
    size?: 'large' | 'default' | 'small'
    placeholder?: string
    disabled?: boolean
    clearable?: boolean
    /** 允许手动新建项目名称 */
    allowCreate?: boolean
    /** 弹窗宽度 */
    width?: string
    dialogTitle?: string
  }>(),
  {
    modelValue: undefined,
    processName: '',
    mode: 'dropdown',
    size: 'small',
    placeholder: '选择或输入项目名称',
    disabled: false,
    clearable: true,
    allowCreate: true,
    width: '720px',
    dialogTitle: '选择项目',
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: number | string | undefined): void
  (e: 'change', value: number | string | undefined): void
  (e: 'select', item: StandardProcessItem): void
  (e: 'create', name: string): void
}>()

// ==================== 数据 ====================
const processes = ref<StandardProcessItem[]>([])
const { options: categoryOptions } = useDict('process_category')

const CATEGORY_FALLBACK: Record<string, string> = {
  PANEL: '面板',
  UP_LINE: '上线',
  DOWN_LINE: '下线',
  OTHER: '其他',
}

async function loadProcesses() {
  try {
    const res = await standardProcessApi.getEnabledProcesses()
    processes.value = res.data || []
  } catch (error) {
    console.error('加载标准工序失败:', error)
  }
}
loadProcesses()

/** 分组：优先按字典顺序，字典里没有的分类补在后面 */
const groups = computed(() => {
  const order: { value: string; label: string }[] = categoryOptions.value.length
    ? categoryOptions.value.map((d) => ({ value: d.itemValue, label: d.label || d.itemValue }))
    : Object.entries(CATEGORY_FALLBACK).map(([value, label]) => ({ value, label }))
  const seen = new Set(order.map((o) => o.value))
  for (const p of processes.value) {
    if (p.processCategory && !seen.has(p.processCategory)) {
      order.push({
        value: p.processCategory,
        label: CATEGORY_FALLBACK[p.processCategory] || p.processCategory,
      })
      seen.add(p.processCategory)
    }
  }
  return order
    .map((o) => ({ ...o, items: processes.value.filter((p) => p.processCategory === o.value) }))
    .filter((g) => g.items.length > 0)
})

// ==================== 当前值回显 ====================
const currentValue = computed<number | string | undefined>(() => props.modelValue ?? undefined)
const currentId = computed<number | undefined>(() =>
  typeof props.modelValue === 'number' ? props.modelValue : undefined
)
const fallbackName = computed(() =>
  typeof props.modelValue === 'string' && props.modelValue ? props.modelValue : ''
)

/** 当前值命中的标准工序（用于取名称/图标） */
const matched = computed<StandardProcessItem | undefined>(() => {
  if (typeof props.modelValue === 'number') {
    return processes.value.find((p) => p.processId === props.modelValue)
  }
  const name = props.processName || (typeof props.modelValue === 'string' ? props.modelValue : '')
  return name ? processes.value.find((p) => p.processName === name) : undefined
})
const showName = computed(
  () =>
    matched.value?.processName ||
    props.processName ||
    (typeof props.modelValue === 'string' ? props.modelValue : '')
)
const showIcon = computed(() => matched.value?.icon || '')

// ==================== 弹窗状态 ====================
const dialogVisible = ref(false)
const keyword = ref('')
const activeTab = ref('')

function openDialog() {
  if (props.disabled) return
  keyword.value = ''
  activeTab.value = groups.value[0]?.value || ''
  dialogVisible.value = true
}

function filterItems(items: StandardProcessItem[]): StandardProcessItem[] {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return items
  return items.filter(
    (p) =>
      (p.processName || '').toLowerCase().includes(kw) ||
      (p.processCode || '').toLowerCase().includes(kw)
  )
}

const canCreate = computed(() => {
  if (!props.allowCreate) return false
  const kw = keyword.value.trim()
  if (!kw) return false
  return !processes.value.some((p) => (p.processName || '').trim() === kw)
})

function emitValue(value: number | string | undefined) {
  emit('update:modelValue', value)
  emit('change', value)
}

function pick(p: StandardProcessItem) {
  emitValue(p.processId)
  emit('select', p)
  dialogVisible.value = false
}

function createFromKeyword() {
  const name = keyword.value.trim()
  if (!name) return
  emitValue(name)
  emit('create', name)
  dialogVisible.value = false
}

function onClear() {
  emitValue(undefined)
}

function onDropdownChange(val: number | string | undefined | null) {
  if (typeof val === 'number') {
    emitValue(val)
    const item = processes.value.find((p) => p.processId === val)
    if (item) emit('select', item)
    return
  }
  const name = val ?? ''
  emitValue(name)
  const exact = processes.value.find((p) => (p.processName || '').trim() === name)
  if (exact) emit('select', exact)
  else if (name) emit('create', name)
}

defineExpose({ reload: loadProcesses, processes })
</script>

<style scoped>
.sps-option {
  display: flex;
  align-items: center;
  gap: 6px;
}
.sps-option-name {
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 弹窗模式触发器：仿输入框 */
.sps-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 24px;
  padding: 0 8px;
  border: 1px solid var(--el-border-color);
  border-radius: var(--el-border-radius-base);
  background: var(--el-fill-color-blank);
  cursor: pointer;
  font-size: 12px;
  line-height: 1.5;
  transition: border-color 0.15s;
}
.sps-trigger:hover {
  border-color: var(--el-border-color-hover);
}
.sps-trigger.is-disabled {
  cursor: not-allowed;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-disabled);
}
.sps-trigger.is-empty {
  color: var(--el-text-color-placeholder);
}
.sps-trigger-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sps-trigger-ph {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.sps-trigger-clear {
  flex: none;
  color: var(--el-text-color-placeholder);
}
.sps-trigger-clear:hover {
  color: var(--el-text-color-secondary);
}

.sps-search {
  margin-bottom: 12px;
}
.sps-tabs :deep(.el-tabs__header) {
  margin-bottom: 10px;
}
.sps-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  max-height: 320px;
  overflow-y: auto;
  padding: 2px;
}
.sps-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 6px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.sps-card:hover {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.sps-card.is-active {
  border-color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}
.sps-card-emoji {
  font-size: 28px;
  line-height: 1;
}
.sps-card-name {
  font-size: 12px;
  color: var(--el-text-color-regular);
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}
.sps-create {
  margin-top: 10px;
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 8px;
}
</style>
