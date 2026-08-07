import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { BoardColumn, BoardCard, BoardFilter, BoardView, DragEvent, TemplateType } from '@/views/kanban/types/board'
import { boardTemplates } from '@/views/kanban/config/board'
import { fetchViews, fetchBoardData, moveCard, updateCard, fetchCardDetail, createCard, fetchColumnTasks, statusToSysTask } from '@/views/kanban/api/board-real'

export const useKanbanStore = defineStore('kanban', () => {
  // 当前选中的模板类型
  const currentTemplate = ref<TemplateType>('production')

  // 当前选中的视图 ID
  const currentViewId = ref<string>('')

  // 看板列数据
  const columns = ref<BoardColumn[]>([])

  // 每列分页状态（DEV-707）：key=columnId -> { pageNum, total, hasMore, loadingMore }
  const columnPageState = ref<Record<string, { pageNum: number; total: number; hasMore: boolean; loadingMore: boolean }>>({})

  const PAGE_SIZE = 5

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
    if (res.code === 200 || res.code === 0) {
      availableViews.value = res.data
      // 默认选中第一个视图
      if (res.data.length > 0 && !currentViewId.value) {
        currentViewId.value = res.data[0].id
      }
    }
  }

  // 加载看板数据（DEV-707：状态视图逐列分页，每页5条；非状态视图保持全量）
  async function loadBoard() {
    loading.value = true
    try {
      const view = currentView.value ?? availableViews.value.find(v => v.id === currentViewId.value) ?? null
      if (!view) return
      currentView.value = view

      // 非状态视图（优先级/部门/工序分组）：保持原逻辑全量+前端分组
      if (view.groupBy !== 'status') {
        const res = await fetchBoardData(currentTemplate.value, currentViewId.value, filter.value)
        if (res.code === 200 || res.code === 0) {
          columns.value = res.data.columns
          columnPageState.value = {}
        }
        return
      }

      // 状态视图：逐列按 status 分页
      const results = await Promise.all(
        view.columns.map(async (colDef) => {
          const status = statusToSysTask(colDef.filterValue ?? colDef.id)
          try {
            const { records, total } = await fetchColumnTasks(
              currentTemplate.value,
              status,
              1,
              PAGE_SIZE,
              filter.value,
            )
            return {
              def: colDef,
              cards: records.map((t: any) => toBoardCard(t, currentTemplate.value)),
              total,
              pageNum: 1,
            }
          } catch (e) {
            console.error(`加载看板列 ${colDef.label} 失败:`, e)
            return { def: colDef, cards: [], total: 0, pageNum: 1 }
          }
        }),
      )

      columns.value = results.map((r) => ({ def: r.def, cards: r.cards }))
      columnPageState.value = {}
      for (const r of results) {
        columnPageState.value[r.def.id] = {
          pageNum: r.pageNum,
          total: r.total,
          hasMore: r.cards.length < r.total,
          loadingMore: false,
        }
      }
    } finally {
      loading.value = false
    }
  }

  // 加载某列下一页（DEV-707：滚动到底部触发）
  async function loadMore(columnId: string) {
    const state = columnPageState.value[columnId]
    const col = columns.value.find(c => c.def.id === columnId)
    if (!state || !col || !state.hasMore || state.loadingMore) return

    state.loadingMore = true
    try {
      const status = statusToSysTask(col.def.filterValue ?? col.def.id)
      const nextPage = state.pageNum + 1
      const { records, total } = await fetchColumnTasks(
        currentTemplate.value,
        status,
        nextPage,
        PAGE_SIZE,
        filter.value,
      )
      col.cards.push(...records.map((t: any) => toBoardCard(t, currentTemplate.value)))
      state.pageNum = nextPage
      state.total = total
      state.hasMore = col.cards.length < total
    } catch (e) {
      console.error(`加载看板列 ${columnId} 下一页失败:`, e)
    } finally {
      state.loadingMore = false
    }
  }

  // 只刷新某一列（DEV-707：重载该列第一页，不影响其他列）
  async function reloadColumn(columnId: string) {
    const state = columnPageState.value[columnId]
    const col = columns.value.find(c => c.def.id === columnId)
    if (!state || !col) return
    if (state.loadingMore) return

    state.loadingMore = true
    try {
      const status = statusToSysTask(col.def.filterValue ?? col.def.id)
      const { records, total } = await fetchColumnTasks(
        currentTemplate.value,
        status,
        1,
        PAGE_SIZE,
        filter.value,
      )
      col.cards = records.map((t: any) => toBoardCard(t, currentTemplate.value))
      state.pageNum = 1
      state.total = total
      state.hasMore = records.length < total
    } catch (e) {
      console.error(`刷新看板列 ${columnId} 失败:`, e)
    } finally {
      state.loadingMore = false
    }
  }

  // sys_task → 看板卡片
  function toBoardCard(t: any, templateType: TemplateType): BoardCard {
    return {
      id: String(t.taskId),
      title: t.title,
      templateType,
      priority: (['urgent', 'high', 'normal', 'low'].includes(t.priority) ? t.priority : 'normal') as BoardCard['priority'],
      status: mapSysTaskStatus(t),
      assignee: t.assigneeName || '',
      deadline: t.deadline || '',
      remark: t.description || '',
      createdAt: t.createTime ? String(t.createTime).slice(0, 10) : '',
      updatedAt: '',
      taskType: t.taskType || 'general',
      department: '',
    }
  }

  /** sys_task.status(tinyint) → 看板状态 */
  function mapSysTaskStatus(t: any): BoardCard['status'] {
    const map: Record<number, BoardCard['status']> = {
      0: 'pending',
      1: 'in_progress',
      2: 'review',
      3: 'blocked',
      4: 'cancelled',
      10: 'completed',
    }
    const mapped = map[Number(t.status)]
    if (mapped) return mapped
    if (t.completedTime) return 'completed'
    return 'pending'
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

  // 移动卡片（DEV-707：乐观更新，不重新查询，零闪烁）
  async function handleDrag(event: DragEvent) {
    const { cardId, toColumnId } = event
    const fromColumnId = columns.value.find(c => c.cards.some(card => card.id === cardId))?.def.id
    if (!fromColumnId) return

    // 从所有列移除该卡片
    const movedCard = columns.value.flatMap(c => c.cards).find(card => card.id === cardId)
    for (const col of columns.value) {
      col.cards = col.cards.filter(card => card.id !== cardId)
    }

    // 插入目标列顶部（update_time 最新 → 后端排序第 1 位，位置准确）
    const targetCol = columns.value.find(c => c.def.id === toColumnId)
    if (targetCol && movedCard) {
      targetCol.cards.unshift({ ...movedCard, status: toColumnId as BoardCard['status'] })
      // 若超每页上限，末尾卡片移出显示（属于下一页，滚动时自然加载）
      if (targetCol.cards.length > PAGE_SIZE) {
        targetCol.cards = targetCol.cards.slice(0, PAGE_SIZE)
      }
    }

    // 更新分页状态：源列 total-1，目标列 total+1
    const fromState = columnPageState.value[fromColumnId]
    if (fromState) {
      fromState.total = Math.max(0, fromState.total - 1)
    }
    const toState = columnPageState.value[toColumnId]
    if (toState) {
      toState.total += 1
    }

    // 后台调后端更新状态（不等，失败回滚）
    try {
      await moveCard(cardId, toColumnId, currentTemplate.value)
    } catch (e) {
      console.error('移动卡片失败，回滚:', e)
      ElMessage.error('移动卡片失败')
      await loadBoard()
    }
  }

  // 打开详情
  async function openDetail(cardId: string) {
    const res = await fetchCardDetail(cardId, currentTemplate.value)
    if ((res.code === 200 || res.code === 0) && res.data) {
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
    const allCards = columns.value.flatMap(col => col.cards)
    const card = allCards.find(c => c.id === cardId)
    const templateType = card?.templateType ?? currentTemplate.value
    await updateCard(cardId, updates, templateType)
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

  // 获取某个列的总数（用后端 total，不是已加载卡片数）
  function getColumnCount(columnId: string): number {
    return columnPageState.value[columnId]?.total ?? columns.value.find(c => c.def.id === columnId)?.cards.length ?? 0
  }

  // 获取某列是否还有更多
  function hasMore(columnId: string): boolean {
    return columnPageState.value[columnId]?.hasMore ?? false
  }

  // 获取某列是否正在加载下一页
  function getColumnLoadingMore(columnId: string): boolean {
    return columnPageState.value[columnId]?.loadingMore ?? false
  }

  // 获取所有卡片总数（各列 total 合计）
  const totalCards = computed(() =>
    Object.values(columnPageState.value).reduce((sum, s) => sum + (s.total || 0), 0),
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
    loadMore,
    reloadColumn,
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
    hasMore,
    getColumnLoadingMore,
  }
})
