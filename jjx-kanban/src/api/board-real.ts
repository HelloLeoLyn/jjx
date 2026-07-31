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
  // office/emergency/dev 视图均为本地配置（后端不提供）
  if (templateType === 'dev' || templateType === 'office' || templateType === 'emergency') {
    const tmpl = boardTemplates.find(t => t.type === templateType)
    return { code: 0, data: tmpl?.views ?? [], message: 'ok' }
  }
  if (useMockFallback) return mockFetchViews(templateType)

  try {
    const res = await http.get(`${API}/views`, { params: { templateType } })
    if (res?.code === 200 && res?.data?.length > 0) return res
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
  if (templateType === 'dev') {
    return fetchDevBoardData(templateType, viewId, filter)
  }
  // office/emergency 读 sys_task 表（看板任务接口）
  if (templateType === 'office' || templateType === 'emergency') {
    return fetchSysTaskBoardData(templateType, viewId, filter)
  }
  if (useMockFallback) return mockFetchBoardData(templateType, viewId, filter)

  try {
    const res = await http.get(`${API}/data`, { params: { templateType, viewId, ...filter } })
    if (res?.code === 200 && res?.data) return res
  } catch { /* 降级 */ }

  useMockFallback = true
  return mockFetchBoardData(templateType, viewId, filter)
}

/**
 * 办公室/紧急任务看板：从 ERP 后端读 sys_task
 * 接口: GET /kanban/board/{module}/tasks → { code:200, data: SysTask[] }
 * SysTask: { taskId, title, description, assigneeName, priority, status, deadline, createTime, completedTime, taskType, kanbanModule }
 */
async function fetchSysTaskBoardData(
  templateType: string,
  viewId: string,
  filter?: BoardFilter,
): Promise<ApiResponse<ViewConfig>> {
  try {
    const res = await http.get(`/kanban/board/${templateType}/tasks`)
    if (res?.code !== 200 || !Array.isArray(res?.data)) throw new Error('invalid')

    const tmpl = boardTemplates.find(t => t.type === templateType)
    const view = tmpl?.views.find(v => v.id === viewId)
    if (!tmpl || !view) return { code: 404, data: { view, columns: [] } as unknown as ViewConfig, message: 'view not found' }

    // sys_task → 看板卡片
    const cards: BoardCard[] = (res.data as any[]).map((t: any) => ({
      id: `TASK-${t.taskId}`,
      title: t.title,
      templateType: templateType as TemplateType,
      priority: (['urgent', 'high', 'normal', 'low'].includes(t.priority) ? t.priority : 'normal') as BoardCard['priority'],
      status: mapSysTaskStatus(t),
      assignee: t.assigneeName || '',
      deadline: t.deadline || '',
      remark: t.description || '',
      createdAt: t.createTime ? String(t.createTime).slice(0, 10) : '',
      updatedAt: '',
      taskType: t.taskType || 'general',
      department: mapSysTaskDept(t),
    }))

    let filtered = cards
    if (filter) {
      if (filter.keyword) {
        const kw = filter.keyword.toLowerCase()
        filtered = filtered.filter(c => c.title.toLowerCase().includes(kw) || c.remark.toLowerCase().includes(kw))
      }
      if (filter.assignee) filtered = filtered.filter(c => c.assignee.includes(filter.assignee))
      if (filter.priority) filtered = filtered.filter(c => c.priority === filter.priority)
      if (filter.status) filtered = filtered.filter(c => c.status === filter.status)
    }

    const groups = groupCardsByColumn(filtered, view.columns, view.groupBy)
    return { code: 200, data: { view, columns: groups.map(g => ({ def: g.column, cards: g.cards })) }, message: 'ok' }
  } catch (e) {
    return { code: 500, data: { view: undefined, columns: [] } as unknown as ViewConfig, message: String(e) }
  }
}

/** sys_task.status(tinyint) + completedTime → 看板状态 */
function mapSysTaskStatus(t: any): BoardCard['status'] {
  if (t.completedTime) return 'completed'
  const map: Record<number, BoardCard['status']> = {
    0: 'pending',
    1: 'in_progress',
    2: 'review',
    3: 'blocked',
  }
  return map[Number(t.status)] ?? 'pending'
}

/** sys_task → 部门（办公室部门视图用） */
function mapSysTaskDept(t: any): string {
  const map: Record<string, string> = {
    sales: '销售部',
    purchase: '采购部',
    product: '设计部',
    production: '生产管理',
    quality: '品质部',
  }
  return map[t.bizType] || (t.assignRole ? '生产管理' : '')
}

/**
 * 开发任务看板：从文档中心(8899)拉取任务
 * 数据格式: { id, title, desc, priority: P0-P4, status: done/todo/pending, date, tags }
 */
async function fetchDevBoardData(
  templateType: string,
  viewId: string,
  filter?: BoardFilter,
): Promise<ApiResponse<ViewConfig>> {
  try {
    const res = await http.get('/dev-tasks/api/tasks')
    if (res?.code !== 0 || !Array.isArray(res?.data)) throw new Error('invalid')

    const tmpl = boardTemplates.find(t => t.type === templateType)
    const view = tmpl?.views.find(v => v.id === viewId)
    if (!tmpl || !view) return { code: 404, data: { view, columns: [] } as unknown as ViewConfig, message: 'view not found' }

    // 8899 任务 → 看板卡片
    const cards: BoardCard[] = (res.data as any[]).map((t: any) => ({
      id: `DEV-${t.id}`,
      title: t.title,
      templateType: 'dev' as const,
      priority: mapDevPriority(t.priority),
      status: mapDevStatus(t.status),
      assignee: '',
      deadline: '',
      remark: t.desc || '',
      createdAt: t.date ? `2026-${t.date}` : '',
      updatedAt: '',
      taskType: '开发',
    }))

    let filtered = cards
    if (filter) {
      if (filter.keyword) {
        const kw = filter.keyword.toLowerCase()
        filtered = filtered.filter(c => c.title.toLowerCase().includes(kw) || c.remark.toLowerCase().includes(kw))
      }
      if (filter.priority) filtered = filtered.filter(c => c.priority === filter.priority)
      if (filter.status) filtered = filtered.filter(c => c.status === filter.status)
    }

    const groups = groupCardsByColumn(filtered, view.columns, view.groupBy)
    return { code: 0, data: { view, columns: groups.map(g => ({ def: g.column, cards: g.cards })) }, message: 'ok' }
  } catch (e) {
    return { code: 500, data: { view: undefined, columns: [] } as unknown as ViewConfig, message: String(e) }
  }
}

/** P0/P1/P2/P3 → 看板优先级 */
function mapDevPriority(p: string): BoardCard['priority'] {
  const map: Record<string, BoardCard['priority']> = { P0: 'urgent', P1: 'high', P2: 'normal', P3: 'low' }
  return map[p] ?? (String(p).toLowerCase() === 'p0' ? 'urgent' : String(p).toLowerCase() === 'p1' ? 'high' : 'normal')
}

/** done/todo/pending → 看板状态 */
function mapDevStatus(s: string): BoardCard['status'] {
  if (s === 'done') return 'completed'
  if (s === 'pending') return 'in_progress'
  return 'pending'
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
    if (res?.code === 200) return res
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
    if (res?.code === 200 && res?.data) return res
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
    if (res?.code === 200) return res
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
    if (res?.code === 200) return res
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
