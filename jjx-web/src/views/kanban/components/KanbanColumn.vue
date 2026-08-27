<template>
  <div class="kanban-column" :style="{ borderTopColor: column.def.color || '#e4e7ed' }">
    <div class="column-header">
      <div class="column-title">
        <span class="column-dot" :style="{ background: column.def.color || '#909399' }"></span>
        <span class="column-label">{{ column.def.label }}</span>
        <el-tag :type="countType" size="small" effect="plain">
          {{ totalCount }}
        </el-tag>
      </div>
      <el-button circle size="small" type="primary" plain class="add-btn" @click.stop="onAdd">
        <el-icon><Plus /></el-icon>
      </el-button>
      <el-button
        circle
        size="small"
        class="refresh-btn"
        :loading="loadingMore"
        @click.stop="onRefresh"
      >
        <el-icon><Refresh /></el-icon>
      </el-button>
    </div>

    <draggable
      :list="column.cards"
      :group="{ name: 'kanban', pull: true, put: true }"
      class="column-cards"
      item-key="id"
      ghost-class="ghost-card"
      :sort="true"
      @change="onChange"
    >
      <template #item="{ element }">
        <KanbanCard :card="element" @click="onCardClick" />
      </template>
      <!-- 列内滚动触底监听（用于滚动加载） -->
      <template #footer>
        <div class="column-load-area" ref="loadAreaRef"></div>
      </template>
    </draggable>

    <div v-if="loadingMore" class="column-loading">
      <el-icon class="is-loading"><Loading /></el-icon> 加载中...
    </div>
    <div v-else-if="hasMore && column.cards.length > 0" class="column-more">
      <el-button link type="primary" size="small" @click="onLoadMoreClick">
        加载更多（还有 {{ moreCount }} 条）
      </el-button>
    </div>
    <div v-else-if="!hasMore && column.cards.length > 0" class="column-end">已加载全部</div>

    <div v-if="column.cards.length === 0" class="column-empty">暂无卡片</div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { Plus, Loading, Refresh } from '@element-plus/icons-vue'
import draggable from 'vuedraggable'
import KanbanCard from './KanbanCard.vue'
import type { BoardColumn } from '@/views/kanban/types/board'

const props = defineProps<{
  column: BoardColumn
  columnIndex: number
  total?: number
  hasMore?: boolean
  loadingMore?: boolean
}>()

const emit = defineEmits<{
  cardClick: [cardId: string]
  cardAdded: [payload: { cardId: string; toColumnId: string }]
  addCard: [columnId: string, columnLabel: string]
  loadMore: [columnId: string]
  refresh: [columnId: string]
}>()

// 列头总数：优先用后端 total，回退到已加载卡片数
const totalCount = computed(() => props.total ?? props.column.cards.length)

const countType = computed(() => {
  const count = totalCount.value
  if (count > 5) return 'danger'
  if (count > 2) return 'warning'
  return 'info'
})

// 还有多少条未加载
const moreCount = computed(() =>
  Math.max(0, (props.total ?? props.column.cards.length) - props.column.cards.length)
)

// ---------- 加载更多触发（三保险） ----------
const loadAreaRef = ref<HTMLElement | null>(null)
let observer: IntersectionObserver | null = null

// ① 按钮点击（最可靠）
function onLoadMoreClick() {
  emit('loadMore', props.column.def.id)
}

// 刷新本列（只重载该列第一页）
function onRefresh() {
  emit('refresh', props.column.def.id)
}

// ② IntersectionObserver：加载区进入视口时自动加载
function setupObserver() {
  if (typeof IntersectionObserver === 'undefined') return
  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting && props.hasMore && !props.loadingMore) {
          emit('loadMore', props.column.def.id)
        }
      }
    },
    { rootMargin: '80px' }
  )
  if (loadAreaRef.value) observer.observe(loadAreaRef.value)
}

// ③ 列容器滚动触底（兜底）
function onColumnScroll(e: Event) {
  const el = e.target as HTMLElement
  if (!el || props.loadingMore) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 30) {
    emit('loadMore', props.column.def.id)
  }
}

watch(
  () => [props.column.cards.length, props.hasMore],
  () => {
    // 卡片变化后重新观察（新内容可能改变滚动位置）
    if (observer && loadAreaRef.value) {
      observer.unobserve(loadAreaRef.value)
      observer.observe(loadAreaRef.value)
    }
  }
)

onMounted(() => {
  setupObserver()
})

onBeforeUnmount(() => {
  observer?.disconnect()
})

function onCardClick(cardId: string) {
  emit('cardClick', cardId)
}

function onAdd() {
  emit('addCard', props.column.def.id, props.column.def.label)
}

function onChange(evt: { added?: { element: { id: string }; newIndex: number } }) {
  if (evt.added) {
    // A card was dragged INTO this column
    emit('cardAdded', {
      cardId: evt.added.element.id,
      toColumnId: props.column.def.id,
    })
  }
}
</script>

<style scoped>
.kanban-column {
  min-width: 260px;
  max-width: 280px;
  flex-shrink: 0;
  background: #f5f7fa;
  border-radius: 8px;
  border-top: 3px solid #e4e7ed;
  display: flex;
  flex-direction: column;
  max-height: 100%;
}

.column-header {
  padding: 12px 12px 8px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.column-title {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.column-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
  flex-shrink: 0;
}

.column-label {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.column-cards {
  padding: 4px 8px 8px;
  flex: 1;
  overflow-y: auto;
  min-height: 60px;
  max-height: calc(100vh - 200px);
}

.column-load-area {
  height: 1px;
}

.column-more {
  padding: 6px 8px;
  text-align: center;
  flex-shrink: 0;
}

.column-loading {
  padding: 8px;
  text-align: center;
  color: #909399;
  font-size: 13px;
  flex-shrink: 0;
}

.column-empty {
  padding: 24px 12px;
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
}

.add-btn {
  flex-shrink: 0;
  opacity: 0.6;
  transition: opacity 0.2s;
}

.add-btn:hover {
  opacity: 1;
}

.refresh-btn {
  flex-shrink: 0;
  opacity: 0.6;
  transition: opacity 0.2s;
  margin-left: 4px;
}

.refresh-btn:hover {
  opacity: 1;
}

.ghost-card {
  opacity: 0.4;
  border: 2px dashed #409eff;
  background: #ecf5ff;
  border-radius: 8px;
}
</style>
