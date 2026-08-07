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
      <el-button
        circle
        size="small"
        type="primary"
        plain
        class="add-btn"
        @click.stop="onAdd"
      >
        <el-icon><Plus /></el-icon>
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
      @scroll="onScroll"
    >
      <template #item="{ element }">
        <KanbanCard :card="element" @click="onCardClick" />
      </template>
    </draggable>

    <div v-if="loadingMore" class="column-loading">
      <el-icon class="is-loading"><Loading /></el-icon> 加载中...
    </div>
    <div v-else-if="!hasMore && column.cards.length > 0" class="column-end">
      已加载全部
    </div>

    <div v-if="column.cards.length === 0" class="column-empty">
      暂无卡片
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { Plus, Loading } from '@element-plus/icons-vue'
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
}>()

// 列头总数：优先用后端 total，回退到已加载卡片数
const totalCount = computed(() => props.total ?? props.column.cards.length)

const countType = computed(() => {
  const count = totalCount.value
  if (count > 5) return 'danger'
  if (count > 2) return 'warning'
  return 'info'
})

// 滚动到底部触发加载下一页（DEV-707）
function onScroll(e: Event) {
  const el = e.target as HTMLElement
  if (!el || props.loadingMore) return
  if (el.scrollTop + el.clientHeight >= el.scrollHeight - 20) {
    emit('loadMore', props.column.def.id)
  }
}

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

.ghost-card {
  opacity: 0.4;
  border: 2px dashed #409eff;
  background: #ecf5ff;
  border-radius: 8px;
}
</style>
