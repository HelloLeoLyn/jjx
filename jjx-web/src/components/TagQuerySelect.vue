<!--
  标签查询辅助组件（dev-20260912-004）

  能力：多选标签 + 按分组展示 + 动态计数（facets）+ 与/或匹配 + 最近使用。
  用法：
    <TagQuerySelect v-model="query.tagIds" v-model:match-mode="query.tagMatchMode" biz-type="purchase_supplier" />
  业务侧只需把 tagIds / tagMatchMode 传给列表接口即可（后端按 sys_tag_rel 反查 bizId 过滤）。
-->
<template>
  <div class="tag-query-select">
    <el-select
      :model-value="modelValue"
      :placeholder="placeholder"
      :disabled="disabled"
      :style="{ width }"
      multiple
      filterable
      clearable
      collapse-tags
      collapse-tags-tooltip
      :loading="loading"
      @update:model-value="onSelect"
      @clear="onClear"
    >
      <el-option-group v-for="g in groups" :key="g.group" :label="groupLabel(g.group)">
        <el-option v-for="t in g.items" :key="t.tagId" :label="t.tagName" :value="t.tagId">
          <span class="tag-option">
            <span class="tag-option-name">{{ t.tagName }}</span>
            <span class="tag-option-count" :class="{ zero: !t.count }">{{ t.count }}</span>
          </span>
        </el-option>
      </el-option-group>
      <template #empty>
        <div class="tag-empty">{{ loading ? '加载中…' : '暂无可用标签' }}</div>
      </template>
    </el-select>

    <el-radio-group
      v-if="showMode"
      v-model="mode"
      size="small"
      :disabled="modelValue.length < 2"
      class="tag-mode"
    >
      <el-radio-button value="AND">同时满足</el-radio-button>
      <el-radio-button value="OR">任一满足</el-radio-button>
    </el-radio-group>

    <template v-if="showRecent && recentTags.length">
      <span class="tag-recent-label">最近</span>
      <el-tag
        v-for="t in recentTags"
        :key="`recent-${t.tagId}`"
        size="small"
        :type="isSelected(t.tagId) ? 'primary' : 'info'"
        effect="plain"
        class="tag-recent-item"
        @click="toggleTag(t.tagId)"
      >
        {{ t.tagName }}
      </el-tag>
      <el-button link size="small" class="tag-recent-clear" @click="clearRecent">清空最近</el-button>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { tagApi, type TagFacet } from '@/api/system/tag'

const props = withDefaults(
  defineProps<{
    /** 业务类型，如 purchase_supplier / inventory_material */
    bizType: string
    /** 已选标签ID */
    modelValue: number[]
    /** 匹配模式：AND=同时满足（默认）；OR=任一满足 */
    matchMode?: 'AND' | 'OR'
    /** 只展示某个分组（如 supplier_goods），不传=全部分组 */
    tagGroup?: string
    placeholder?: string
    width?: string
    disabled?: boolean
    /** 是否显示与/或切换 */
    showMode?: boolean
    /** 是否显示最近使用 */
    showRecent?: boolean
  }>(),
  {
    matchMode: 'AND',
    tagGroup: undefined,
    placeholder: '按标签筛选',
    width: '280px',
    disabled: false,
    showMode: true,
    showRecent: true,
  },
)

const emit = defineEmits<{
  'update:modelValue': [value: number[]]
  'update:matchMode': [value: 'AND' | 'OR']
  change: [tagIds: number[], matchMode: 'AND' | 'OR']
}>()

const loading = ref(false)
const facets = ref<TagFacet[]>([])
const mode = ref<'AND' | 'OR'>(props.matchMode)
const recentIds = ref<number[]>([])

const GROUP_LABELS: Record<string, string> = {
  supplier_goods: '供货品类',
  material_attribute: '物料属性',
}

function groupLabel(group: string): string {
  return GROUP_LABELS[group] || group
}

/** 按分组聚合（tagGroup 传入时只保留该组） */
const groups = computed(() => {
  const scoped = props.tagGroup ? facets.value.filter((t) => t.tagGroup === props.tagGroup) : facets.value
  const map = new Map<string, TagFacet[]>()
  for (const t of scoped) {
    const key = props.tagGroup || t.tagGroup || 'other'
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(t)
  }
  return Array.from(map.entries()).map(([group, items]) => ({ group, items }))
})

const facetMap = computed(() => new Map(facets.value.map((t) => [t.tagId, t])))

const recentTags = computed(() =>
  recentIds.value.map((id) => facetMap.value.get(id)).filter((t): t is TagFacet => !!t),
)

function isSelected(tagId: number): boolean {
  return props.modelValue.includes(tagId)
}

/**
 * 拉取标签与计数；
 * 已选标签参与「与」收窄（facets 计数随之变化）。
 * 组合无命中时后端返回空数组，此时保留上次选项，避免已选标签显示成 ID。
 */
async function loadFacets() {
  if (!props.bizType) return
  loading.value = true
  try {
    const res: any = await tagApi.facets({ bizType: props.bizType, tagIds: props.modelValue })
    const list: TagFacet[] = res?.data || []
    if (list.length || !props.modelValue.length) {
      facets.value = list
    }
  } catch {
    if (!props.modelValue.length) facets.value = []
  } finally {
    loading.value = false
  }
}

function onSelect(values: number[]) {
  emit('update:modelValue', values)
  emit('change', values, mode.value)
}

function onClear() {
  emit('update:modelValue', [])
  emit('change', [], mode.value)
}

function toggleTag(tagId: number) {
  const next = isSelected(tagId)
    ? props.modelValue.filter((id) => id !== tagId)
    : [...props.modelValue, tagId]
  onSelect(next)
}

function recentKey(): string {
  return `jjx.tagQuery.recent.${props.bizType}`
}

function saveRecent(ids: number[]) {
  if (!props.showRecent || !ids.length) return
  recentIds.value = ids.slice(-5).reverse()
  try {
    localStorage.setItem(recentKey(), JSON.stringify(recentIds.value))
  } catch {
    /* 忽略隐私模式等写入失败 */
  }
}

function clearRecent() {
  recentIds.value = []
  try {
    localStorage.removeItem(recentKey())
  } catch {
    /* ignore */
  }
}

watch(
  () => props.modelValue,
  (values, old) => {
    if (JSON.stringify(values) !== JSON.stringify(old)) {
      saveRecent(values)
      loadFacets()
    }
  },
  { deep: true },
)

watch(
  () => props.matchMode,
  (value) => {
    mode.value = value
  },
)

watch(mode, (value) => {
  emit('update:matchMode', value)
  emit('change', props.modelValue, value)
})

onMounted(() => {
  try {
    const cached = localStorage.getItem(recentKey())
    if (cached) recentIds.value = JSON.parse(cached)
  } catch {
    recentIds.value = []
  }
  loadFacets()
})
</script>

<style scoped>
.tag-query-select {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.tag-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}
.tag-option-count {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.tag-option-count.zero {
  color: var(--el-text-color-placeholder);
}
.tag-empty {
  padding: 8px 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.tag-recent-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.tag-recent-item {
  cursor: pointer;
}
.tag-recent-clear {
  font-size: 12px;
}
</style>
