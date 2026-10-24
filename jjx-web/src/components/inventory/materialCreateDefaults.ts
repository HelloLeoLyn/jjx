import type { MaterialSaveDTO } from '@/types/inventory/material'

/**
 * 物料建档默认值（dev-20261021-026）：
 * MaterialFormDialog、库存单条导入、库存批量导入共用；业务类型只从分类派生，不默认归类。
 */
export const MATERIAL_DEFAULT_UNIT = 'PCS'
export const MATERIAL_DEFAULT_EXPIRY_ALERT_DAYS = 30

export interface MaterialCreatePreset {
  materialName?: string
  specification?: string
  unit?: string
  supplierName?: string
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
    specification: row.specification || '',
    unit: MATERIAL_DEFAULT_UNIT,
    supplierName: row.supplierName || '',
  }
}
