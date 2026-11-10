import { generateBomSpecification } from './bomSpecification'
import type { BomSpecificationSource } from './bomSpecification'

export interface SpecRowLike {
  materialId?: number | null
  specification?: string | null
  materialSpecification?: string | null
  materialSpecificationSource?: BomSpecificationSource | null
}

/** 物料档案规格，空规格也以档案为准。 */
export function autoSpecOf(row?: SpecRowLike | null): string {
  if (row?.materialSpecificationSource) return generateBomSpecification(row.materialSpecificationSource)
  return row?.materialSpecification ?? ''
}

export function currentSpecOf(row?: SpecRowLike | null): string {
  return row?.specification ?? ''
}
