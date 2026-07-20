import type { ApiResponse, BoardCard, BoardView, ViewConfig, BoardFilter } from '@/types/board'
import { boardTemplates } from '@/config/board'
import { generateProductionCards, generateOfficeTasks, generateEmergencyCards, groupCardsByColumn } from './data/production'

type CardMap = Record<string, BoardCard[]>

const cardCache: CardMap = {
  production: generateProductionCards(),
  office: generateOfficeTasks(),
  emergency: generateEmergencyCards(),
}

/** 模拟网络延迟 */
function delay(ms = 200): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

/** 获取可用视图 */
export async function fetchViews(templateType: string): Promise<ApiResponse<BoardView[]>> {
  await delay()
  const tmpl = boardTemplates.find(t => t.type === templateType)
  return {
    code: 0,
    data: tmpl?.views ?? [],
    message: 'ok',
  }
}

/** 获取看板数据 */
export async function fetchBoardData(
  templateType: string,
  viewId: string,
  filter?: BoardFilter,
): Promise<ApiResponse<ViewConfig>> {
  await delay(300)

  const tmpl = boardTemplates.find(t => t.type === templateType)
  const view = tmpl?.views.find(v => v.id === viewId)

  if (!tmpl || !view) {
    return { code: 404, data: { view, columns: [] } as unknown as ViewConfig, message: 'not found' }
  }

  let cards = cardCache[templateType] ?? []

  // 应用筛选
  if (filter) {
    cards = applyFilter(cards, filter)
  }

  const groups = groupCardsByColumn(cards, view.columns, view.groupBy)

  return {
    code: 0,
    data: { view, columns: groups.map(g => ({ def: g.column, cards: g.cards })) },
    message: 'ok',
  }
}

/** 移动卡片 */
export async function moveCard(
  cardId: string,
  toColumnId: string,
  templateType: string,
): Promise<ApiResponse<null>> {
  await delay(100)

  const cards = cardCache[templateType]
  const card = cards.find(c => c.id === cardId)
  if (!card) return { code: 404, data: null, message: 'card not found' }

  const tmpl = boardTemplates.find(t => t.type === templateType)
  const allViews = tmpl?.views ?? []
  const targetCol = allViews.flatMap(v => v.columns).find(c => c.id === toColumnId)

  if (targetCol) {
    // 根据当前视图更新对应字段
    const field = card.templateType === 'production' ? 'currentProcess' : 'status'
    ;(card as Record<string, unknown>)[field] = targetCol.filterValue ?? targetCol.label
  }

  return { code: 0, data: null, message: 'ok' }
}

/** 获取单张卡片详情 */
export async function fetchCardDetail(cardId: string): Promise<ApiResponse<BoardCard | null>> {
  await delay(150)
  const allCards = [...(cardCache.production ?? []), ...(cardCache.office ?? []), ...(cardCache.emergency ?? [])]
  const card = allCards.find(c => c.id === cardId)
  return { code: card ? 0 : 404, data: card ?? null, message: card ? 'ok' : 'not found' }
}

/** 创建卡片 */
export async function createCard(
  card: Partial<BoardCard>,
  templateType: string,
  targetColumnId: string,
): Promise<ApiResponse<BoardCard>> {
  await delay()
  const newCard = {
    ...card,
    id: card.id ?? `NEW-${Date.now()}`,
    createdAt: new Date().toISOString().slice(0, 10),
    updatedAt: new Date().toISOString().slice(0, 10),
  } as BoardCard

  if (!cardCache[templateType]) {
    cardCache[templateType] = []
  }
  cardCache[templateType].push(newCard)

  return { code: 0, data: newCard, message: 'ok' }
}

/** 更新卡片 */
export async function updateCard(cardId: string, updates: Partial<BoardCard>): Promise<ApiResponse<BoardCard | null>> {
  await delay(150)
  const allCards = [...(cardCache.production ?? []), ...(cardCache.office ?? []), ...(cardCache.emergency ?? [])]
  const card = allCards.find(c => c.id === cardId)
  if (!card) return { code: 404, data: null, message: 'not found' }
  Object.assign(card, updates, { updatedAt: new Date().toISOString().slice(0, 10) })
  return { code: 0, data: card, message: 'ok' }
}

function applyFilter(cards: BoardCard[], filter: BoardFilter): BoardCard[] {
  let result = [...cards]
  if (filter.keyword) {
    const kw = filter.keyword.toLowerCase()
    result = result.filter(c =>
      c.title.toLowerCase().includes(kw) ||
      c.id.toLowerCase().includes(kw) ||
      c.assignee.includes(kw),
    )
  }
  if (filter.assignee) result = result.filter(c => c.assignee === filter.assignee)
  if (filter.priority) result = result.filter(c => c.priority === filter.priority)
  if (filter.status) result = result.filter(c => c.status === filter.status)
  if (filter.deadlineFrom) result = result.filter(c => c.deadline >= filter.deadlineFrom!)
  if (filter.deadlineTo) result = result.filter(c => c.deadline <= filter.deadlineTo!)
  return result
}
