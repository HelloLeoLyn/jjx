/**
 * BOM 批量关联物料：前端分组/选择/提交载荷（dev-20261024-020）
 *
 * 口径：匹配只作推荐，必须人工确认；未选中的行不提交；取消零写入。
 */
import type { BomMaterialMatchResult, EngineeringBomItem } from '@/types/product/bom'

export interface BatchCandidate {
  materialId?: number
  materialCode?: string
  materialName?: string
  specification?: string
  size?: string
  unit?: string
}

export interface BatchRow {
  itemId: number
  originalName: string
  originalSpec: string
  status: 'MATCHED' | 'AMBIGUOUS' | 'NOT_FOUND'
  matchCount: number
  candidates: BatchCandidate[]
  selectedMaterialId?: number
}

export function buildBatchRows(
  items: EngineeringBomItem[],
  results: BomMaterialMatchResult[]
): BatchRow[] {
  return (items || []).map((item, index) => {
    const match = (results || []).find((r) => r.index === index)
    return {
      itemId: Number(item.itemId),
      originalName: String(item.materialName || ''),
      originalSpec: String(item.specification || ''),
      status: match?.status || 'NOT_FOUND',
      matchCount: match?.matchCount ?? 0,
      candidates: (match?.candidates || []).map((c) => ({ ...c })),
      selectedMaterialId: undefined,
    }
  })
}

export function selectCandidate(row: BatchRow, materialId?: number): void {
  row.selectedMaterialId = materialId || undefined
}

export function selectedCandidate(row: BatchRow): BatchCandidate | undefined {
  return row.candidates.find((c) => c.materialId === row.selectedMaterialId)
}

/** 只提交人工选中了候选的行 */
export function buildBatchLinks(rows: BatchRow[]): Array<{ itemId: number; materialId: number }> {
  return (rows || [])
    .filter((row) => row.selectedMaterialId != null && row.selectedMaterialId > 0)
    .map((row) => ({ itemId: row.itemId, materialId: Number(row.selectedMaterialId) }))
}

export function batchStatusLabel(status: BatchRow['status']): string {
  if (status === 'MATCHED') return '唯一候选'
  if (status === 'AMBIGUOUS') return '多候选'
  return '未匹配'
}

export function batchStatusTagType(status: BatchRow['status']): 'success' | 'warning' | 'info' {
  if (status === 'MATCHED') return 'success'
  if (status === 'AMBIGUOUS') return 'warning'
  return 'info'
}

/** 提交前检查：至少选中一行；同一行不会被重复提交（由数组结构保证） */
export function validateBatchSelection(rows: BatchRow[]): string | null {
  const links = buildBatchLinks(rows)
  if (!links.length) return '请至少为一行选择物料档案'
  const seen = new Set<number>()
  for (const link of links) {
    if (seen.has(link.itemId)) return `明细 ${link.itemId} 重复提交`
    seen.add(link.itemId)
  }
  return null
}
