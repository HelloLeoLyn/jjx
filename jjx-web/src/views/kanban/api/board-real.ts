/**
 * 看板 API - 对接 ERP 后端（jjx-web 集成版）
 * 全部模块统一走 /kanban/board/{module}/tasks（sys_task 表 / production_order 表）
 * 真实数据，不降级 mock
 */
import type {
  ApiResponse,
  BoardCard,
  BoardView,
  ViewConfig,
  BoardFilter,
} from '@/views/kanban/types/board'
import { boardTemplates } from '@/views/kanban/config/board'
import http from '@/utils/request'

/** 统一成功判断（后端 Result=200） */
function isOk(code: number | undefined): boolean {
  return code === 200 || code === 0
}

/**
 * 获取可用视图（所有模块都用本地配置，不再走后端）
 */
export async function fetchViews(templateType: string): Promise<ApiResponse<BoardView[]>> {
  const tmpl = boardTemplates.find((t) => t.type === templateType)
  return { code: 0, data: tmpl?.views ?? [], message: 'ok' }
}

/**
 * 获取看板数据
 * 全部模块统一走 /kanban/board/{module}/tasks
 */
export async function fetchBoardData(
  templateType: string,
  viewId: string,
  filter?: BoardFilter
): Promise<ApiResponse<ViewConfig>> {
  return fetchSysTaskBoardData(templateType, viewId, filter)
}

/**
 * 按列分页获取任务（DEV-707：每列按状态查询，每页5条，滚动加载下一页）
 * 后端返回 { records, total }，不传分页参数时返回全量数组
 */
export async function fetchColumnTasks(
  templateType: string,
  status: number,
  pageNum: number,
  pageSize: number,
  filter?: BoardFilter
): Promise<{ records: any[]; total: number }> {
  const res = await http.get(`/kanban/board/${templateType}/tasks`, {
    params: {
      status,
      pageNum,
      pageSize,
      keyword: filter?.keyword || undefined,
      assignee: filter?.assignee || undefined,
      priority: filter?.priority || undefined,
    },
  })
  if (!isOk(res?.code)) throw new Error(res?.msg || '加载失败')
  const data = res.data as any
  return {
    records: data?.records || [],
    total: data?.total ?? 0,
  }
}

/** 看板状态 → sys_task.status(tinyint) */
export function statusToSysTask(status: string): number {
  const map: Record<string, number> = {
    pending: 0,
    in_progress: 1,
    review: 2,
    blocked: 3,
    cancelled: 4,
    completed: 10,
  }
  return map[status] ?? 0
}

/**
 * 办公室/紧急任务看板：读 sys_task 表
 * 接口: GET /kanban/board/{module}/tasks → { code:200, data: SysTask[] }
 */
async function fetchSysTaskBoardData(
  templateType: string,
  viewId: string,
  filter?: BoardFilter
): Promise<ApiResponse<ViewConfig>> {
  try {
    const res = await http.get(`/kanban/board/${templateType}/tasks`)
    if (!isOk(res?.code) || !Array.isArray(res?.data)) throw new Error('invalid')

    const tmpl = boardTemplates.find((t) => t.type === templateType)
    const view = tmpl?.views.find((v) => v.id === viewId)
    if (!tmpl || !view)
      return {
        code: 404,
        data: { view, columns: [] } as unknown as ViewConfig,
        message: 'view not found',
      }

    // sys_task → 看板卡片
    const cards: BoardCard[] = (res.data as any[]).map((t: any) => ({
      id: String(t.taskId),
      taskCode: t.taskCode || '',
      title: t.title,
      templateType: templateType as BoardCard['templateType'],
      priority: (['urgent', 'high', 'normal', 'low'].includes(t.priority)
        ? t.priority
        : 'normal') as BoardCard['priority'],
      status: mapSysTaskStatus(t),
      assignee: t.assigneeName || '',
      deadline: t.deadline || '',
      remark: t.description || '',
      createdAt: t.createTime ? String(t.createTime).slice(0, 10) : '',
      updatedAt: '',
      taskType: t.taskType || 'general',
      department: mapSysTaskDept(t),
      extraData: { sourceEvent: t.sourceEvent, bizId: t.bizId, bizType: t.bizType },
    }))

    let filtered = cards
    if (filter) {
      if (filter.keyword) {
        const kw = filter.keyword.toLowerCase()
        filtered = filtered.filter(
          (c) => c.title.toLowerCase().includes(kw) || (c.remark || '').toLowerCase().includes(kw)
        )
      }
      if (filter.assignee)
        filtered = filtered.filter((c) => (c.assignee || '').includes(filter.assignee || ''))
      if (filter.priority) filtered = filtered.filter((c) => c.priority === filter.priority)
      if (filter.status) filtered = filtered.filter((c) => c.status === filter.status)
    }

    const groups = groupCardsByColumn(filtered, view.columns, view.groupBy)
    return {
      code: 200,
      data: { view, columns: groups.map((g) => ({ def: g.column, cards: g.cards })) },
      message: 'ok',
    }
  } catch (e) {
    return {
      code: 500,
      data: { view: undefined, columns: [] } as unknown as ViewConfig,
      message: String(e),
    }
  }
}

/** sys_task.status(tinyint) + completedTime → 看板状态 */
function mapSysTaskStatus(t: any): BoardCard['status'] {
  // 以 status 数字为准（10=已完成），completedTime 只作辅助兜底
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
  // status 无映射时兜底：有完成时间视为已完成
  if (t.completedTime) return 'completed'
  return 'pending'
}

/** 看板状态 → sys_task.status(tinyint) */
function mapStatusToSysTask(status: string): number {
  const map: Record<string, number> = {
    pending: 0,
    in_progress: 1,
    review: 2,
    blocked: 3,
    cancelled: 4,
    completed: 10,
  }
  return map[status] ?? 0
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
  if (t.bizType && map[t.bizType]) return map[t.bizType]
  // 兜底：按指派角色归属部门（角色ID → 部门）
  const roleDept: Record<number, string> = {
    7: '销售部', // 销售人员
    8: '销售部', // 订单审核员
    9: '设计部', // 工程管理
    10: '销售部', // 销售管理
    6: '系统', // 系统用户
  }
  const roleId = Number(t.assignRole)
  return roleDept[roleId] || ''
}

/** 从卡片 ID 解析 sys_task 的 taskId（ID 是纯数字） */
function extractTaskId(cardId: string): string {
  return cardId.replace(/^TASK-/, '').replace(/^DEV-/, '')
}

/**
 * 移动卡片
 * office/emergency → 更新 sys_task.status；production → 生产接口
 */
export async function moveCard(
  cardId: string,
  toColumnId: string,
  templateType: string
): Promise<ApiResponse<null>> {
  if (
    templateType === 'dev' ||
    templateType === 'office' ||
    templateType === 'emergency' ||
    templateType === 'production'
  ) {
    const taskId = extractTaskId(cardId)
    const res = await http.patch(`/kanban/board/${templateType}/tasks/${taskId}/status`, {
      status: mapStatusToSysTask(toColumnId),
    })
    return isOk(res?.code)
      ? { code: 200, data: null, message: 'ok' }
      : { code: 500, data: null, message: res?.msg || 'update failed' }
  }
  return { code: 500, data: null, message: 'unknown template' }
}

/**
 * 卡片详情
 * office/emergency → 读 sys_task
 */
export async function fetchCardDetail(
  cardId: string,
  templateType?: string
): Promise<ApiResponse<BoardCard | null>> {
  if (
    templateType === 'dev' ||
    templateType === 'office' ||
    templateType === 'emergency' ||
    templateType === 'production'
  ) {
    const taskId = extractTaskId(cardId)
    const res = await http.get(`/kanban/board/${templateType}/tasks/${taskId}`)
    if (isOk(res?.code) && res?.data) {
      const t = res.data as any
      const card: BoardCard = {
        id: String(t.taskId),
        taskCode: t.taskCode || '',
        title: t.title,
        templateType: templateType as BoardCard['templateType'],
        priority: (['urgent', 'high', 'normal', 'low'].includes(t.priority)
          ? t.priority
          : 'normal') as BoardCard['priority'],
        status: mapSysTaskStatus(t),
        assignee: t.assigneeName || '',
        deadline: t.deadline || '',
        remark: t.description || '',
        createdAt: t.createTime ? String(t.createTime).slice(0, 10) : '',
        updatedAt: '',
        taskType: t.taskType || 'general',
        department: mapSysTaskDept(t),
        extraData: { sourceEvent: t.sourceEvent, bizId: t.bizId, bizType: t.bizType },
      }
      return { code: 200, data: card, message: 'ok' }
    }
    return { code: 500, data: null, message: res?.msg || 'detail failed' }
  }
  return { code: 500, data: null, message: 'unknown template' }
}

/**
 * 创建卡片
 * office/emergency → 插入 sys_task
 */
export async function createCard(
  card: Partial<BoardCard>,
  templateType: string,
  targetColumnId: string
): Promise<ApiResponse<BoardCard>> {
  if (
    templateType === 'dev' ||
    templateType === 'office' ||
    templateType === 'emergency' ||
    templateType === 'production'
  ) {
    const res = await http.post(`/kanban/board/${templateType}/tasks`, {
      title: card.title,
      description: card.remark,
      priority: card.priority || 'normal',
      status: mapStatusToSysTask(targetColumnId),
      assigneeName: card.assignee,
      deadline: card.deadline || null,
      taskType: card.taskType || 'general',
      bizType: templateType === 'emergency' ? 'production' : 'production',
    })
    if (isOk(res?.code)) {
      const taskId = Number(res?.data)
      // 上传任务截图（bizType=task, bizId=taskId）
      const shots = (card as any).screenshots as { file: File }[] | undefined
      if (taskId && shots && shots.length > 0) {
        try {
          const { attachmentApi } = await import('@/api/system/attachment')
          for (const s of shots) {
            await attachmentApi.upload(s.file, 'task', taskId)
          }
        } catch (e) {
          console.error('任务截图上传失败:', e)
        }
      }
      return { code: 200, data: { ...card, id: String(taskId) } as BoardCard, message: 'ok' }
    }
    return { code: 500, data: card as BoardCard, message: res?.msg || 'create failed' }
  }
  return { code: 500, data: card as BoardCard, message: 'unknown template' }
}

/**
 * 更新卡片
 * office/emergency → 更新 sys_task
 */
export async function updateCard(
  cardId: string,
  updates: Partial<BoardCard>,
  templateType?: string
): Promise<ApiResponse<BoardCard | null>> {
  if (
    templateType === 'dev' ||
    templateType === 'office' ||
    templateType === 'emergency' ||
    templateType === 'production'
  ) {
    const taskId = extractTaskId(cardId)
    const body: Record<string, unknown> = {}
    if (updates.title !== undefined) body.title = updates.title
    if (updates.remark !== undefined) body.description = updates.remark
    if (updates.priority !== undefined) body.priority = updates.priority
    if (updates.assignee !== undefined) body.assigneeName = updates.assignee
    if (updates.deadline !== undefined) body.deadline = updates.deadline
    if (updates.status !== undefined) {
      const statusRes = await http.patch(
        `/kanban/board/${templateType}/tasks/${taskId}/status`,
        { status: mapStatusToSysTask(updates.status) }
      )
      if (!isOk(statusRes?.code)) {
        return { code: 500, data: null, message: statusRes?.msg || 'update failed' }
      }
    }
    const res = await http.patch(`/kanban/board/${templateType}/tasks/${taskId}/info`, body)
    return isOk(res?.code)
      ? { code: 200, data: null, message: 'ok' }
      : { code: 500, data: null, message: res?.msg || 'update failed' }
  }
  return { code: 500, data: null, message: 'unknown template' }
}

// ========== 分组工具（前端本地分组，与后端无关） ==========

export function groupCardsByColumn(
  cards: BoardCard[],
  columns: BoardView['columns'],
  groupBy: string
): { column: BoardView['columns'][number]; cards: BoardCard[] }[] {
  if (groupBy === 'deadline') {
    return groupByDeadline(cards, columns)
  }
  return columns.map((col) => ({
    column: col,
    cards: cards.filter((c) => {
      const val = (c as unknown as Record<string, unknown>)[groupBy] ?? ''
      return String(val) === String(col.filterValue ?? col.label)
    }),
  }))
}

function groupByDeadline(cards: BoardCard[], columns: BoardView['columns']) {
  const now = new Date()
  const today = now.toISOString().slice(0, 10)
  const groups = columns.map((col) => ({ column: col, cards: [] as BoardCard[] }))
  for (const card of cards) {
    let idx = columns.length - 1
    if (card.deadline < today) idx = 0
    else if (card.deadline === today) idx = 1
    else if (isThisWeek(card.deadline)) idx = 2
    else idx = 3
    groups[idx].cards.push(card)
  }
  return groups
}

function isThisWeek(dateStr: string): boolean {
  if (!dateStr) return false
  const now = new Date()
  const d = new Date(dateStr)
  const dayOfWeek = now.getDay() || 7
  const weekEnd = new Date(now.getTime() + (7 - dayOfWeek) * 86400000)
  return d <= weekEnd && d >= now
}
