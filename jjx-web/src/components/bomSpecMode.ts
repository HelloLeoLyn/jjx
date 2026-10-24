/**
 * BOM 行「规格型号」的系统生成 / 人工改写判定（dev-20261024-023）
 *
 * 口径：不新增数据库列——实时比较「BOM 行规格串」与「物料主数据当前系统规格」：
 * - 与当前或上次保存的系统值一致 → 系统模式
 * - 与两者都不同，或用户明确清空 → 人工改写
 * - 物料未建档 / 物料无系统规格 → 无法判定，不标记、不覆盖
 */

import { generateBomSpecification } from './bomSpecification'
import type { BomSpecificationSource } from './bomSpecification'

export interface SpecRowLike {
  materialId?: number | null
  specification?: string | null
  /** 物料主数据当前的系统生成规格（后端查询带出，不落库） */
  materialSpecification?: string | null
  specificationBaseline?: string | null
  specificationManual?: boolean
  materialSpecificationSource?: BomSpecificationSource | null
}

/** 物料当前的系统生成规格（空串 = 没有可用系统值） */
export function autoSpecOf(row?: SpecRowLike | null): string {
  if (row?.materialSpecificationSource) return generateBomSpecification(row.materialSpecificationSource)
  return String(row?.materialSpecification ?? '').trim()
}

/** BOM 行当前规格 */
export function currentSpecOf(row?: SpecRowLike | null): string {
  return String(row?.specification ?? '').trim()
}

/** 已知旧系统值来自保存日志；历史没有对照时只按当前值比较，保留旧原文。 */
export function isManualSpec(row?: SpecRowLike | null): boolean {
  if (!row || !(Number(row.materialId) > 0)) return false
  const auto = autoSpecOf(row)
  const cur = currentSpecOf(row)
  if (!auto || cur === auto) return false
  if (row.specificationManual) return true
  const baseline = String(row.specificationBaseline ?? auto).trim()
  return !!cur && cur !== baseline
}

/**
 * 载入 / 物料属性变化时需要自动跟随的值：
 * - 无系统规格 → null（绝不用空串覆盖手填值）
 * - 当前为空且未人工清空，或仍等于旧系统值 → 自动跟随
 * - 人工改写 → null（不覆盖，仅提示）
 */
export function specFollowValue(row?: SpecRowLike | null): string | null {
  if (!row || !(Number(row.materialId) > 0)) return null
  const auto = autoSpecOf(row)
  if (!auto || isManualSpec(row)) return null
  return currentSpecOf(row) === auto ? null : auto
}

export function specSourceChanged(row: SpecRowLike): boolean {
  return row.specificationBaseline != null && !!autoSpecOf(row) &&
    String(row.specificationBaseline).trim() !== autoSpecOf(row)
}
