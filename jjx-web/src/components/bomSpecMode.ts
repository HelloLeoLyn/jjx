/**
 * BOM 行「规格型号」的系统生成 / 人工改写判定（dev-20261024-023）
 *
 * 口径：不新增数据库列——实时比较「BOM 行规格串」与「物料主数据当前系统规格」：
 * - 一致（或 BOM 为空）→ 系统模式
 * - 不一致且两边都非空 → 人工改写
 * - 物料未建档 / 物料无系统规格 → 无法判定，不标记、不覆盖
 */

export interface SpecRowLike {
  materialId?: number | null
  specification?: string | null
  /** 物料主数据当前的系统生成规格（后端查询带出，不落库） */
  materialSpecification?: string | null
}

/** 物料当前的系统生成规格（空串 = 没有可用系统值） */
export function autoSpecOf(row?: SpecRowLike | null): string {
  return String(row?.materialSpecification ?? '').trim()
}

/** BOM 行当前规格 */
export function currentSpecOf(row?: SpecRowLike | null): string {
  return String(row?.specification ?? '').trim()
}

/** 是否处于「人工改写」：已建档 + 有系统规格 + 当前非空且与系统值不同 */
export function isManualSpec(row?: SpecRowLike | null): boolean {
  if (!row || !Number(row.materialId)) return false
  const auto = autoSpecOf(row)
  const cur = currentSpecOf(row)
  return !!auto && !!cur && cur !== auto
}

/**
 * 载入 / 物料属性变化时需要自动跟随的值：
 * - 无系统规格 → null（绝不用空串覆盖手填值）
 * - 当前为空 → 系统值（自动补齐）
 * - 当前与系统值不同 → null（视为人工改写，不覆盖，仅提示）
 */
export function specFollowValue(row?: SpecRowLike | null): string | null {
  if (!row || !Number(row.materialId)) return null
  const auto = autoSpecOf(row)
  if (!auto) return null
  return currentSpecOf(row) ? null : auto
}
