<template>
  <div class="kanban-page">
    <!-- 顶部导航 -->
    <div class="page-header">
      <div class="template-tabs">
        <el-radio-group
          v-model="activeTemplate"
          size="large"
          @change="onTemplateChange"
        >
          <el-radio-button
            v-for="tmpl in store.templates"
            :key="tmpl.type"
            :value="tmpl.type"
          >
            <el-icon style="vertical-align: -2px; margin-right: 4px">
              <component :is="getIconComponent(tmpl.icon)" />
            </el-icon>
            {{ tmpl.name }}
          </el-radio-button>
        </el-radio-group>
      </div>

      <div class="header-right">
        <el-tooltip :content="broadcastEnabled ? '语音播报已开启' : '语音播报已关闭'" placement="bottom">
          <el-button
            :type="broadcastEnabled ? 'primary' : 'default'"
            :icon="Microphone"
            :class="{ 'muted': !broadcastEnabled }"
            text
            @click="toggleBroadcast"
          >
            {{ broadcastEnabled ? '播报中' : '已静音' }}
          </el-button>
        </el-tooltip>
        <el-button text @click="onRefresh" :loading="store.loading">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 视图切换 + 筛选 -->
    <div class="page-toolbar">
      <ViewSwitcher
        :views="store.availableViews"
        :model-value="store.currentViewId"
        @change="onViewChange"
      />
      <FilterBar
        :filter="store.filter"
        :total-cards="store.totalCards"
        @change="onFilterChange"
        @reset="onFilterReset"
      />
    </div>

    <!-- 看板主体 -->
    <div class="board-wrapper">
      <KanbanBoard
        :columns="store.columns"
        :loading="store.loading"
        @card-click="onCardClick"
        @card-moved="onCardMoved"
      @add-card="onShowAddDialog"
      />
    </div>

    <!-- 详情弹窗 -->
    <CardDetailDialog
      v-model:visible="store.detailVisible"
      :card="store.selectedCard"
      @save="onSaveDetail"
    />

    <!-- 新建卡片弹窗 -->
    <AddCardDialog
      v-model:visible="addDialogVisible"
      :template-type="store.currentTemplate"
      :target-column-id="addTargetColumnId"
      :target-column-label="addTargetColumnLabel"
      @create="onCreateCard"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Refresh, Microphone } from '@element-plus/icons-vue'
import * as ElementIcons from '@element-plus/icons-vue'
import { useKanbanStore } from '@/views/kanban/stores/kanban'
import ViewSwitcher from '@/views/kanban/components/ViewSwitcher.vue'
import FilterBar from '@/views/kanban/components/FilterBar.vue'
import KanbanBoard from '@/views/kanban/components/KanbanBoard.vue'
import CardDetailDialog from '@/views/kanban/components/CardDetailDialog.vue'
import AddCardDialog from '@/views/kanban/components/AddCardDialog.vue'
import { broadcast } from '@/views/kanban/utils/broadcast'
import type { BoardFilter, BoardCard, TemplateType } from '@/views/kanban/types/board'

const store = useKanbanStore()

const addDialogVisible = ref(false)
const addTargetColumnId = ref('')
const addTargetColumnLabel = ref('')
const broadcastEnabled = ref(true)

const activeTemplate = computed({
  get: () => store.currentTemplate,
  set: (val: TemplateType) => {
    store.switchTemplate(val)
  },
})

function getIconComponent(name: string) {
  return (ElementIcons as Record<string, unknown>)[name] as ReturnType<typeof defineComponent> || 'Sell'
}

onMounted(async () => {
  broadcast.init()
  await store.switchTemplate('production')
})

function onTemplateChange(type: TemplateType) {
  store.switchTemplate(type)
}

function onViewChange(viewId: string) {
  store.switchView(viewId)
}

function onFilterChange(filter: BoardFilter) {
  store.updateFilter(filter)
}

function onFilterReset() {
  store.resetFilter()
}

function onRefresh() {
  store.loadBoard()
}

function onCardClick(cardId: string) {
  store.openDetail(cardId)
}

function onCardMoved(data: { cardId: string; toColumnId: string }) {
  store.handleDrag({
    cardId: data.cardId,
    fromColumnId: '',
    toColumnId: data.toColumnId,
    newIndex: 0,
  })

  // 播报卡片移动
  const allCards = store.columns.flatMap(c => c.cards)
  const card = allCards.find(c => c.id === data.cardId)
  const toCol = store.columns.find(c => c.def.id === data.toColumnId)
  if (card && toCol) {
    broadcast.announceCardMove(card.title, '上一工序', toCol.def.label)
  }
}

function onShowAddDialog(columnId: string, columnLabel: string) {
  addTargetColumnId.value = columnId
  addTargetColumnLabel.value = columnLabel
  addDialogVisible.value = true
}

function onCreateCard(card: Partial<BoardCard>, targetColumnId: string) {
  store.addCard(card, targetColumnId)

  // 播报新建卡片
  const col = store.currentView?.columns.find(c => c.def.id === targetColumnId)
  if (col) {
    broadcast.announceNewCard(card.title || '', col.def.label)
  }
}

function onSaveDetail(cardId: string, updates: Record<string, unknown>) {
  store.handleUpdateCard(cardId, updates)
}

function toggleBroadcast() {
  broadcastEnabled.value = !broadcastEnabled.value
  broadcast.setEnabled(broadcastEnabled.value)
}
</script>

<style scoped>
.kanban-page {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f0f2f5;
  overflow: hidden;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-shrink: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.page-toolbar {
  display: flex;
  flex-direction: column;
  background: #fff;
  flex-shrink: 0;
  border-bottom: 1px solid #e4e7ed;
}

.board-wrapper {
  flex: 1;
  overflow: hidden;
  display: flex;
}

.muted {
  opacity: 0.5;
}
</style>
