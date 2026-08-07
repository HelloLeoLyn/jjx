<template>
  <div class="kanban-board" v-loading="loading">
    <div class="board-columns" ref="boardRef">
      <KanbanColumn
        v-for="(col, index) in columns"
        :key="col.def.id"
        :column="col"
        :column-index="index"
        @card-click="onCardClick"
        @card-added="onCardAdded"
        @add-card="onAddCard"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { BoardColumn } from '@/views/kanban/types/board'
import KanbanColumn from './KanbanColumn.vue'

const props = defineProps<{
  columns: BoardColumn[]
  loading: boolean
}>()

const emit = defineEmits<{
  cardClick: [cardId: string]
  cardMoved: [data: { cardId: string; toColumnId: string }]
  addCard: [columnId: string, columnLabel: string]
}>()

const boardRef = ref<HTMLElement | null>(null)

function onCardClick(cardId: string) {
  emit('cardClick', cardId)
}

function onCardAdded(data: { cardId: string; toColumnId: string }) {
  emit('cardMoved', data)
}

function onAddCard(columnId: string, columnLabel: string) {
  emit('addCard', columnId, columnLabel)
}
</script>

<style scoped>
.kanban-board {
  flex: 1;
  overflow-x: auto;
  padding: 16px;
  background: #f0f2f5;
  min-height: 0;
}

.board-columns {
  display: flex;
  gap: 12px;
  align-items: flex-start;
  min-height: 300px;
  height: 100%;
}
</style>
