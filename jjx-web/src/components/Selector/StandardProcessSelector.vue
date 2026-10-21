<template>
  <!-- 模式一：可编辑搜索下拉（EditableSearchSelect 分组 + 图标，dev-20261018-011） -->
  <EditableSearchSelect
    v-if="mode === 'dropdown'"
    :model-value="innerValue"
    :display-value="showName"
    :groups="dropdownGroups"
    option-key="processId"
    option-label="processName"
    :placeholder="placeholder"
    :size="size"
    :disabled="disabled"
    :clearable="clearable"
    :show-create="canCreate"
    :create-label="'+ 新建临时工序「' + keyword.trim() + '」'"
    @search="onDropdownSearch"
    @select="onDropdownPick"
    @clear="onClear"
    @create="onDropdownCreate"
  >
    <template #prefix>
      <SvgIcon v-if="showIcon" :name="showIcon" :size="16" class="sps-prefix-icon" />
    </template>
    <template #option="{ item }">
      <div class="sps-option">
        <SvgIcon v-if="item.icon" :name="item.icon" :size="16" />
        <span class="sps-option-name"
          >{{ item.processName }}{{ isPending(item) ? '（待审）' : '' }}</span
        >
      </div>
    </template>
  </EditableSearchSelect>

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
        v-for="g in visibleGroups"
        :key="g.value"
        :name="g.value"
        :label="`${g.label}（${g.items.length}）`"
      >
        <div class="sps-grid">
          <div
            v-for="p in g.items"
            :key="p.processId"
            class="sps-card"
            :class="{ 'is-active': p.processId === currentId }"
            @click="pick(p)"
          >
            <el-tag v-if="isPending(p)" class="sps-pending" size="small" type="warning"
              >待审</el-tag
            >
            <SvgIcon v-if="p.icon" :name="p.icon" :size="28" />
            <span v-else class="sps-card-emoji">📦</span>
            <span class="sps-card-name">{{ p.processName }}</span>
          </div>
          <el-empty v-if="!g.items.length" description="无匹配项目" :image-size="56" />
        </div>
      </el-tab-pane>
    </el-tabs>
    <div v-if="canCreate" class="sps-create">
      <el-button link type="primary" :icon="Plus" @click="createFromKeyword">
        + 新建临时工序「{{ keyword.trim() }}」
      </el-button>
    </div>
  </el-dialog>
  <TempProcessCreateDialog
    v-model="createDialogVisible"
    :initial-name="createName"
    :processes="processes"
    @created="onTempCreated"
  />
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { CircleClose, Plus, Search } from '@element-plus/icons-vue'
import SvgIcon from '@/components/SvgIcon/index.vue'
import EditableSearchSelect from './EditableSearchSelect.vue'
import TempProcessCreateDialog from './TempProcessCreateDialog.vue'
import { loadCommonStandardProcesses } from '@/api/product/standardProcess'
import { useEnabledProcesses } from '@/composables/useEnabledProcesses'
import { useDict } from '@/composables/useDict'
import { StandardProcessStatus } from '@/types/product/standardProcess'
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
// 启用的标准工序：全局单例缓存，多实例只请求一次
const { processes, reload: reloadProcesses } = useEnabledProcesses()
const commonProcessIds = ref<number[]>([])
loadCommonStandardProcesses()
  .then((items) => {
    commonProcessIds.value = items.map((item) => item.processId)
  })
  .catch((error) => {
    console.error('加载常用标准工序失败:', error)
  })
const { options: categoryOptions } = useDict('process_category')

const CATEGORY_FALLBACK: Record<string, string> = {
  PANEL: '面板',
  UP_LINE: '上线',
  DOWN_LINE: '下线',
  OTHER: '其他',
}

const COMMON_TAB = '__common__'
const SEARCH_TAB = '__search__'
/**
 * 默认常用项目：用户 2026-10-21 指定的工序编码与顺序（按编码匹配，改名不影响）。
 * /engineering/standard-processes/common 返回数据时以接口为准，这里仅作无数据兜底。
 */
const COMMON_CODES = [
  'SP-101',
  'SP-103',
  'SP-104',
  'SP-110',
  'SP-112',
  'SP-113',
  'SP-301',
  'SP-302',
  'SP-304',
  'SP-305',
  'SP-306',
  'SP-405',
  'SP-406',
  'SP-401',
  'SP-409',
  'SP-410',
  'SP-201',
  'SP-202',
  'SP-204',
  'SP-205',
]
const isPending = (item: StandardProcessItem) =>
  item.processStatus === StandardProcessStatus.PENDING
const selectableProcesses = computed(() =>
  processes.value
    .filter((p) => p.processStatus !== StandardProcessStatus.REJECTED)
    .slice()
    .sort((a, b) => Number(isPending(a)) - Number(isPending(b)))
)

/** 字典分类，正式项优先；未分类项也保留在“其他”。 */
const categoryGroups = computed(() => {
  const order: { value: string; label: string }[] = categoryOptions.value.length
    ? categoryOptions.value.map((d) => ({ value: d.itemValue, label: d.label || d.itemValue }))
    : Object.entries(CATEGORY_FALLBACK).map(([value, label]) => ({ value, label }))
  const seen = new Set(order.map((o) => o.value))
  for (const p of selectableProcesses.value) {
    if (p.processCategory && !seen.has(p.processCategory)) {
      order.push({
        value: p.processCategory,
        label: CATEGORY_FALLBACK[p.processCategory] || p.processCategory,
      })
      seen.add(p.processCategory)
    }
  }
  return order
    .map((o) => ({
      ...o,
      items: selectableProcesses.value.filter((p) => (p.processCategory || 'OTHER') === o.value),
    }))
    .filter((g) => g.items.length > 0 || g.value === 'PANEL')
})

const commonItems = computed(() => {
  // 默认常用固定在前（顺序按用户指定编码），接口返回的高频项去重后追加在后。
  // 用户 2026-10-21 口径：这 20 项是默认，使用频次只做「自动加」，不替换默认。
  const byId = new Map(selectableProcesses.value.map((p) => [p.processId, p]))
  const defaults = COMMON_CODES
    .map((code) => selectableProcesses.value.find((p) => p.processCode === code))
    .filter((p): p is StandardProcessItem => Boolean(p))
  const defaultIds = new Set(defaults.map((p) => p.processId))
  const extra = commonProcessIds.value
    .filter((id) => !defaultIds.has(id))
    .map((id) => byId.get(id))
    .filter((p): p is StandardProcessItem => Boolean(p))
  return [...defaults, ...extra]
})
const groups = computed(() => [
  { value: COMMON_TAB, label: '常用', items: commonItems.value },
  ...categoryGroups.value,
])
const defaultTab = computed(() =>
  commonItems.value.length
    ? COMMON_TAB
    : categoryGroups.value.find((g) => g.value === 'PANEL')?.value ||
      categoryGroups.value[0]?.value ||
      COMMON_TAB
)

// ==================== 当前值（内部受控，保证选中即时回显）====================
// el-select 单选的显示依赖 model-value 变化；这里维护内部值，选中即写入，不依赖父级回传。
const innerValue = ref<number | string | undefined>(props.modelValue ?? undefined)
watch(
  () => props.modelValue,
  (v) => {
    innerValue.value = v ?? undefined
  }
)

const currentId = computed<number | undefined>(() =>
  typeof innerValue.value === 'number' ? innerValue.value : undefined
)

/** 当前值命中的标准工序（用于取名称/图标） */
const matched = computed<StandardProcessItem | undefined>(() => {
  if (typeof innerValue.value === 'number') {
    return processes.value.find((p) => p.processId === innerValue.value)
  }
  const name = props.processName || (typeof innerValue.value === 'string' ? innerValue.value : '')
  return name ? processes.value.find((p) => p.processName === name) : undefined
})
const showName = computed(() => {
  if (matched.value && matched.value.processName) return matched.value.processName
  if (props.processName) return props.processName
  const v = innerValue.value
  return v == null ? '' : String(v)
})
const showIcon = computed(() => matched.value?.icon || '')

// ==================== 弹窗状态 ====================
const dialogVisible = ref(false)
const keyword = ref('')
const activeTab = ref('')
const createDialogVisible = ref(false)
const createName = ref('')
const visibleGroups = computed(() =>
  keyword.value.trim()
    ? [{ value: SEARCH_TAB, label: '搜索结果', items: filterItems(selectableProcesses.value) }]
    : groups.value
)
watch([keyword, defaultTab], () => {
  activeTab.value = keyword.value.trim() ? SEARCH_TAB : defaultTab.value
})

function openDialog() {
  if (props.disabled) return
  keyword.value = ''
  activeTab.value = defaultTab.value
  dialogVisible.value = true
}

/** 下拉模式可见分组：按当前输入关键字过滤（dev-20261018-011） */
const dropdownGroups = computed(() =>
  visibleGroups.value
    .map((g) => ({ label: g.label, items: g.items }))
    .filter((g) => g.items.length > 0)
)

function onDropdownSearch(value: string) {
  keyword.value = value
}

function onDropdownPick(item: StandardProcessItem) {
  emitValue(item.processId)
  emit('select', item)
  keyword.value = ''
}

function onDropdownCreate(name: string) {
  openCreateDialog(name)
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
  return filterItems(selectableProcesses.value).length === 0
})

function emitValue(value: number | string | undefined) {
  const v = value ?? undefined
  innerValue.value = v
  emit('update:modelValue', v)
  emit('change', v)
}

function pick(p: StandardProcessItem) {
  emitValue(p.processId)
  emit('select', p)
  dialogVisible.value = false
}

function createFromKeyword() {
  openCreateDialog(keyword.value)
}

function openCreateDialog(name: string) {
  if (props.disabled || !props.allowCreate || !name.trim()) return
  createName.value = name.trim()
  createDialogVisible.value = true
}

async function onTempCreated(item: StandardProcessItem) {
  await reloadProcesses()
  // reload 内部会记录加载失败；仍保留已创建项，保证当前与其他实例即时可选。
  if (!processes.value.some((p) => p.processId === item.processId)) {
    processes.value = [...processes.value, item]
  }
  pick(item)
  keyword.value = ''
}

function onClear() {
  emitValue(undefined)
  keyword.value = ''
}

defineExpose({ reload: reloadProcesses, processes })
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
.sps-prefix-icon {
  display: inline-flex;
  align-items: center;
  vertical-align: middle;
}
.sps-selected-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 24px 6px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}
.sps-pending {
  position: absolute;
  top: 2px;
  right: 2px;
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
