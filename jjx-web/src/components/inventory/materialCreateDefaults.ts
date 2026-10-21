import type { MaterialSaveDTO } from '@/types/inventory/material'

/**
 * 物料建档默认值（dev-20261021-026）：
 * MaterialFormDialog、库存单条导入、库存批量导入共用，禁止各处再硬编码 R / PCS。
 */
export const MATERIAL_DEFAULT_TYPE = 'R'
export const MATERIAL_DEFAULT_UNIT = 'PCS'
export const MATERIAL_DEFAULT_EXPIRY_ALERT_DAYS = 30

export interface MaterialCreatePreset {
  materialName?: string
  specification?: string
  unit?: string
  supplierName?: string
  materialType?: string
}

export interface MaterialImportRow {
  materialName?: string
  specification?: string
  supplierName?: string
}

/** 导入行 → 建档弹窗预填。 */
export function materialCreatePresetFromImport(row: MaterialImportRow): MaterialCreatePreset {
  return {
    materialName: row.materialName || '',
    specification: row.specification || '',
    supplierName: row.supplierName || '',
    materialType: MATERIAL_DEFAULT_TYPE,
    unit: MATERIAL_DEFAULT_UNIT,
  }
}

/** 导入行 → 批量建档保存载荷（只含导入能提供的字段，其余由后端默认）。 */
export function materialSaveFromImport(
  row: MaterialImportRow,
  materialCode: string
): Partial<MaterialSaveDTO> & { materialCode: string } {
  return {
    materialCode,
    materialName: row.materialName || '',
    materialType: MATERIAL_DEFAULT_TYPE,
    specification: row.specification || '',
    unit: MATERIAL_DEFAULT_UNIT,
    supplierName: row.supplierName || '',
  }
}
