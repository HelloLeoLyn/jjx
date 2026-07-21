/**
 * 看板 API - 对接 ERP 后端
 * 
 * 生产工单数据从 ERP 后端获取，视图和模板配置仍用本地配置
 * 降级策略：后端不可用时自动使用 Mock 数据
 */
import type { ApiResponse, BoardCard, BoardView, ViewConfig, BoardFilter } from '@/types/board'
import { boardTemplates } from '@/config/board'
import http from './board'

/** 后端 API 基础路径 */
const API = '/production/kanban'

// 降级标志
let useMockFallback = false

/**
 * 获取可用视图（优先后端，降级到本地配置）
 */
export async function fetchViews(templateType: string): Promise<ApiResponse<BoardView[]>> {
  if (useMockFallback) return mockFetchViews(templateType)

  try {
    const res = await http.get(`${API}/views`, { params: { templateType } })
    if (res?.code === 0 && res?.data?.length > 0) return res
  } catch { /* 降级 */ }

  useMockFallback = true
  return mockFetchViews(templateType)
}

/**
 * 获取看板数据
 */
export async function fetchBoardData(
  templateType: string,
  viewId: string,
  filter?: BoardFilter,
): Promise<ApiResponse<ViewConfig>> {
  if (useMockFallback) return mockFetchBoardData(templateType, viewId, filter)

  try {
    const res = await http.get(`${API}/data`, { params: { templateType, viewId, ...filter } })
    if (res?.code === 0 && res?.data) return res
  } catch { /* 降级 */ }

  useMockFallback = true
  return mockFetchBoardData(templateType, viewId, filter)
}

/**
 * 移动卡片
 */
export async function moveCard(
  cardId: string,
  toColumnId: string,
  templateType: string,
): Promise<ApiResponse<null>> {
  if (useMockFallback) return mockMoveCard(cardId, toColumnId, templateType)

  try {
    const res = await http.patch(`/production/order/${cardId}/move`, { toColumnId })
    if (res?.code === 0) return res
  } catch { /* 降级 */ }

  useMockFallback = true
  return mockMoveCard(cardId, toColumnId, templateType)
}

/**
 * 卡片详情
 */
export async function fetchCardDetail(cardId: string): Promise<ApiResponse<BoardCard | null>> {
  if (useMockFallback) return mockFetchCardDetail(cardId)

  try {
    const res = await http.get(`/production/order/${cardId}`)
    if (res?.code === 0 && res?.data) return res
  } catch { /* 降级 */ }

  useMockFallback = true
  return mockFetchCardDetail(cardId)
}

/**
 * 创建卡片
 */
export async function createCard(
  card: Partial<BoardCard>,
  templateType: string,
  targetColumnId: string,
): Promise<ApiResponse<BoardCard>> {
  if (useMockFallback) return mockCreateCard(card, templateType, targetColumnId)

  try {
    const res = await http.post(`${API}/cards`, { ...card, templateType, targetColumnId })
    if (res?.code === 0) return res
  } catch { /* 降级 */ }

  useMockFallback = true
  return mockCreateCard(card, templateType, targetColumnId)
}

/**
 * 更新卡片
 */
export async function updateCard(cardId: string, updates: Partial<BoardCard>): Promise<ApiResponse<BoardCard | null>> {
  if (useMockFallback) return mockUpdateCard(cardId, updates)

  try {
    const res = await http.patch(`${API}/cards/${cardId}`, updates)
    if (res?.code === 0) return res
  } catch { /* 降级 */ }

  useMockFallback = true
  return mockUpdateCard(cardId, updates)
}

// ========== 降级：Mock 数据 ==========

import { generateProductionCards, generateOfficeTasks, generateEmergencyCards, groupCardsByColumn } from '@/mock/data/production'

type CardMap = Record<string, BoardCard[]>

const cardCache: CardMap = {
  production: generateProductionCards(),
  office: generateOfficeTasks(),
  emergency: generateEmergencyCards(),
}

function delay(ms = 200): Promise<void> {
  return new Promise(resolve => setTimeout(resolve, ms))
}

async function mockFetchViews(templateType: string): Promise<ApiResponse<BoardView[]>> {
  await delay()
  const tmpl = boardTemplates.find(t => t.type === templateType)
  return { code: 0, data: tmpl?.views ?? [], message: 'ok' }
}

async function mockFetchBoardData(templateType: string, viewId: string, filter?: BoardFilter): Promise<ApiResponse<ViewConfig>> {
  await delay(300)
  const tmpl = boardTemplates.find(t => t.type === templateType)
  const view = tmpl?.views.find(v => v.id === viewId)
  if (!tmpl || !view) return { code: 404, data: { view, columns: [] } as unknown as ViewConfig, message: 'not found' }

  let cards = cardCache[templateType] ?? []
  if (filter) cards = applyMockFilter(cards, filter)

  const groups = groupCardsByColumn(cards, view.columns, view.groupBy)
  return { code: 0, data: { view, columns: groups.map(g => ({ def: g.column, cards: g.cards })) }, message: 'ok' }
}

async function mockMoveCard(cardId: string, toColumnId: string, templateType: string): Promise<ApiResponse<null>> {
  await delay(100)
  const cards = cardCache[templateType]
  const card = cards.find(c => c.id === cardId)
  if (!card) return { code: 404, data: null, message: 'card not found' }

  const tmpl = boardTemplates.find(t => t.type === templateType)
  const targetCol = tmpl?.views.flatMap(v => v.columns).find(c => c.id === toColumnId)
  if (targetCol) {
    const field = card.templateType === 'production' ? 'currentProcess' : 'status'
    ;(card as Record<string, unknown>)[field] = targetCol.filterValue ?? targetCol.label
  }
  return { code: 0, data: null, message: 'ok' }
}

async function mockFetchCardDetail(cardId: string): Promise<ApiResponse<BoardCard | null>> {
  await delay(150)
  const allCards = [...(cardCache.production ?? []), ...(cardCache.office ?? []), ...(cardCache.emergency ?? [])]
  const card = allCards.find(c => c.id === cardId)
  return { code: card ? 0 : 404, data: card ?? null, message: card ? 'ok' : 'not found' }
}

async function mockCreateCard(card: Partial<BoardCard>, templateType: string, _targetColumnId: string): Promise<ApiResponse<BoardCard>> {
  await delay()
  const newCard = { ...card, id: card.id ?? `NEW-${Date.now()}`, createdAt: new Date().toISOString().slice(0, 10), updatedAt: new Date().toISOString().slice(0, 10) } as BoardCard
  if (!cardCache[templateType]) cardCache[templateType] = []
  cardCache[templateType].push(newCard)
  return { code: 0, data: newCard, message: 'ok' }
}

async function mockUpdateCard(cardId: string, updates: Partial<BoardCard>): Promise<ApiResponse<BoardCard | null>> {
  await delay(150)
  const allCards = [...(cardCache.production ?? []), ...(cardCache.office ?? []), ...(cardCache.emergency ?? [])]
  const card = allCards.find(c => c.id === cardId)
  if (!card) return { code: 404, data: null, message: 'not found' }
  Object.assign(card, updates, { updatedAt: new Date().toISOString().slice(0, 10) })
  return { code: 0, data: card, message: 'ok' }
}

function applyMockFilter(cards: BoardCard[], filter: BoardFilter): BoardCard[] {
  let result = [...cards]
  if (filter.keyword) {
    const kw = filter.keyword.toLowerCase()
    result = result.filter(c => c.title.toLowerCase().includes(kw) || c.id.toLowerCase().includes(kw) || c.assignee.includes(kw))
  }
  if (filter.assignee) result = result.filter(c => c.assignee === filter.assignee)
  if (filter.priority) result = result.filter(c => c.priority === filter.priority)
  if (filter.status) result = result.filter(c => c.status === filter.status)
  if (filter.deadlineFrom) result = result.filter(c => c.deadline >= filter.deadlineFrom!)
  if (filter.deadlineTo) result = result.filter(c => c.deadline <= filter.deadlineTo!)
  return result
}
