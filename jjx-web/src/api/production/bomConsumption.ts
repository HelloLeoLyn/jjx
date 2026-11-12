import request from '@/utils/request'
import type { R } from '@/types'

export interface BomConsumptionInput {
  key: string
  materialId?: number | null
  unit: string
  baseQty?: number | null
  moduleQty?: number | null
  lossRate?: number | null
  minIssueQty?: number | null
  referenceRollLengthM?: number | null
}
export interface BomConsumptionResult {
  key: string
  valid: boolean
  message?: string | null
  unit?: string
  unitConsumption?: string | null
  totalConsumption?: string | null
  exactConsumption?: string | null
  metres?: string | null
  referenceRollCount?: string | null
  referenceRollLengthM?: number | null
  archiveUnit?: string | null
  widthMm?: number | null
  lengthMm?: number | null
  /** 仅连续计量投入的辅助能力，不表示物料形态或已有制造关系。 */
  pitchSupported?: boolean
}
export interface PitchPreviewResult {
  moduleQtySuggested: number
  baseQtySuggested?: number | null
  baseQtyStorableInBom: boolean
  baseQtyStorageNote?: string | null
  exactConsumptionText: string
  displayConsumption: number
  unit: string
}
/** 草稿只读试算，不保存、不扣库存；每件与整批均由同一后端计算器返回。 */
export function previewBomConsumption(data: { outputQuantity: number; outputUnit: string; rows: BomConsumptionInput[] }) {
  return request.post<R<{ readOnly: boolean; rows: BomConsumptionResult[] }>>('/production/bom/consumption/preview', data)
}
/** 复用004的跳距接口，只有用户确认采用后才回填草稿原始比例。 */
export function previewPitchRequirement(params: { widthMm: number; pitchMm: number; pcsPerJump: number; inputUnit: string; outputPcs: number }) {
  return request.get<R<PitchPreviewResult>>('/production/bom/pitch-requirement/preview', { params })
}
