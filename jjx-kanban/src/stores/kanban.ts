import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { BoardColumn, BoardCard, BoardFilter, BoardView, DragEvent, TemplateType } from '@/types/board'
import { boardTemplates } from '@/config/board'
import { fetchBoardData, fetchViews, moveCard, updateCard, fetchCardDetail, createCard } from '@/api/board-real'

export const useKanbanStore = defineStore('kanban', () => {
  // 当前选中的模板类型
  const currentTemplate = ref<TemplateType>('production')

  // 当前选中的视图 ID
  const currentViewId = ref<string>('')

  // 看板列数据
  const columns = ref<BoardColumn[]>([])

  // 当前视图定义
  const currentView = ref<BoardView | null>(null)

  // 所有可用视图
  const availableViews = ref<BoardView[]>([])

  // 当前选中的卡片
  const selectedCard = ref<BoardCard | null>(null)

  // 详情弹窗显隐
  const detailVisible = ref(false)

  // 筛选条件
  const filter = ref<BoardFilter>({})

  // 加载状态
  const loading = ref(false)

  // 当前模板
  const currentTemplateConfig = computed(() =>
    boardTemplates.find(t => t.type === currentTemplate.value),
  )

  // 所有模板（给菜单用）
  const templates = computed(() => boardTemplates)

  // 加载可用视图
  async function loadViews() {
    const res = await fetchViews(currentTemplate.value)
    if (res.code === 0) {
      availableViews.value = res.data
      // 默认选中第一个视图
      if (res.data.length > 0 && !currentViewId.value) {
        currentViewId.value = res.data[0].id
      }
    }
  }

  // 加载看板数据
  async function loadBoard() {
    loading.value = true
    try {
      const res = await fetchBoardData(currentTemplate.value, currentViewId.value, filter.value)
      if (res.code === 0) {
        columns.value = res.data.columns
        currentView.value = res.data.view
      }
    } finally {
      loading.value = false
    }
  }

  // 切换模板
  async function switchTemplate(type: TemplateType) {
    currentTemplate.value = type
    currentViewId.value = ''
    filter.value = {}
    selectedCard.value = null
    await loadViews()
    if (availableViews.value.length > 0) {
      currentViewId.value = availableViews.value[0].id
    }
    await loadBoard()
  }

  // 切换视图
  async function switchView(viewId: string) {
    currentViewId.value = viewId
    await loadBoard()
  }

  // 移动卡片
  async function handleDrag(event: DragEvent) {
    const { cardId, toColumnId } = event
    await moveCard(cardId, toColumnId, currentTemplate.value)
    // 更新本地状态
    const card = columns.value
      .flatMap(c => c.cards)
      .find(c => c.id === cardId)
    if (card && currentView.value) {
      const targetCol = currentView.value.columns.find(c => c.id === toColumnId)
      if (targetCol) {
        ;(card as Record<string, unknown>)[currentView.value.groupBy] = targetCol.filterValue ?? targetCol.label
      }
    }
    await loadBoard()
  }

  // 打开详情
  async function openDetail(cardId: string) {
    const res = await fetchCardDetail(cardId)
    if (res.code === 0 && res.data) {
      selectedCard.value = res.data
      detailVisible.value = true
    }
  }

  // 关闭详情
  function closeDetail() {
    detailVisible.value = false
    selectedCard.value = null
  }

  // 更新卡片
  async function handleUpdateCard(cardId: string, updates: Partial<BoardCard>) {
    await updateCard(cardId, updates)
    await loadBoard()
  }

  // 更新筛选
  async function updateFilter(newFilter: BoardFilter) {
    filter.value = { ...filter.value, ...newFilter }
    await loadBoard()
  }

  // 添加卡片
  async function addCard(card: Partial<BoardCard>, targetColumnId: string) {
    await createCard(card, currentTemplate.value, targetColumnId)
    await loadBoard()
  }

  // 重置筛选
  async function resetFilter() {
    filter.value = {}
    await loadBoard()
  }

  // 获取某个列的所有卡片数量
  function getColumnCount(columnId: string): number {
    return columns.value.find(c => c.def.id === columnId)?.cards.length ?? 0
  }

  // 获取所有卡片总数
  const totalCards = computed(() =>
    columns.value.reduce((sum, col) => sum + col.cards.length, 0),
  )

  return {
    // state
    currentTemplate,
    currentViewId,
    columns,
    currentView,
    availableViews,
    selectedCard,
    detailVisible,
    filter,
    loading,
    currentTemplateConfig,
    templates,
    totalCards,
    // actions
    loadViews,
    loadBoard,
    switchTemplate,
    switchView,
    handleDrag,
    openDetail,
    closeDetail,
    handleUpdateCard,
    addCard,
    updateFilter,
    resetFilter,
    getColumnCount,
  }
})
