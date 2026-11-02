/**
 * 打样「成品方案带出」统计（dev-20261102-006）。
 * 纯展示口径放在前端算：材料总数 = 各卡片材料条数 + 未挂工序/整单通用材料条数。
 * 后端只回原值（rows[].materials 为 JSON 串、unlinkedMaterials 为清单），不再造派生字段。
 */
export interface PlanRowLike {
  materials?: string | null
}

/** 解析单个卡片的材料 JSON（坏数据当 0 条，不抛异常）。 */
export function cardMaterialCount(row?: PlanRowLike | null): number {
  if (!row?.materials) return 0
  try {
    const parsed = JSON.parse(row.materials)
    return Array.isArray(parsed) ? parsed.length : 0
  } catch {
    return 0
  }
}

/** 材料总条数：卡片材料 + 未分配材料。 */
export function countPlanMaterials(rows?: PlanRowLike[] | null, unlinked?: unknown[] | null): number {
  const attached = (rows || []).reduce((sum, row) => sum + cardMaterialCount(row), 0)
  return attached + (unlinked || []).length
}
