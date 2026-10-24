/**
 * 入库新增：物料选择的载荷回填（dev-20261024-019 wave B）
 *
 * 只改选择与表单载荷，不触发库存写账。
 */

export interface InboundMaterialRow {
  materialId?: string | number | null
  materialCode?: string | null
  materialName?: string | null
  specification?: string | null
  unit?: string | null
}

export interface PickedMaterial {
  materialId?: number | string | null
  materialCode?: string | null
  materialName?: string | null
  specification?: string | null
  unit?: string | null
}

/** 选中候选后回填：ID 必须来自档案，不能用输入文字冒充 */
export function applyMaterialSelection(row: InboundMaterialRow | null | undefined, material: PickedMaterial | null | undefined): boolean {
  if (!row) return false
  if (!material || !material.materialId) {
    clearMaterialSelection(row)
    return false
  }
  row.materialId = String(material.materialId)
  row.materialCode = material.materialCode ?? ''
  row.materialName = material.materialName ?? ''
  row.specification = material.specification ?? ''
  row.unit = material.unit ?? ''
  return true
}

/** 清空选择：必须同时清掉旧 ID，避免"文字清空了、ID 还在" */
export function clearMaterialSelection(row: InboundMaterialRow | null | undefined): void {
  if (!row) return
  row.materialId = ''
  row.materialCode = ''
  row.materialName = ''
  row.specification = ''
  row.unit = ''
}
