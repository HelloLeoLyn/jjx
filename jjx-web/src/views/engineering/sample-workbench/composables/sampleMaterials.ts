/** Material business values; old unit quantities are preserved through an exact ratio. */
export function normalizeMaterial(source: any, defaultProjects: number[] = []) {
  const row = { ...source }
  if (row.baseQty == null && row.moduleQty == null) {
    const quantity = Number(row.qty ?? 1)
    if (quantity > 0 && Number.isFinite(quantity)) {
      let numerator = Math.round(quantity * 10000), denominator = 10000
      let a = numerator, c = denominator
      while (c) [a, c] = [c, a % c]
      row.baseQty = numerator / a; row.moduleQty = denominator / a
    }
  }
  if (!Array.isArray(row.projectIds) || row.projectInherited) {
    row.projectIds = [...defaultProjects]
    row.projectInherited = true
  }
  if (Number.isInteger(row.baseQty) && row.baseQty >= 1 && Number.isInteger(row.moduleQty) && row.moduleQty >= 1) {
    row.qty = Number((row.baseQty / row.moduleQty).toFixed(4))
  }
  return row
}
export function validateMaterials(rows: any[]): string | null {
  // dev-20261024-019 wave B：搜索框里还有未确认的文字时，禁止保存/审核（不能静默用旧 materialId）
  const pending = rows.find(row => row.__matDraftPending === true)
  if (pending) return `材料「${pending.name || pending.__matDraftText || '未命名'}」搜索尚未确认，请选择候选物料或取消修改`
  const invalid = rows.find(row => !Number.isInteger(row.baseQty) || row.baseQty < 1 || !Number.isInteger(row.moduleQty) || row.moduleQty < 1)
  if (invalid) return `材料「${invalid.name || '未命名'}」模数、基数须为至少1的整数，请核对历史用量`
  const badSplit = rows.find(row => row.split != null && row.split !== '' && splitNumber(row.split) == null)
  return badSplit ? `材料「${badSplit.name || '未命名'}」跳距须为正整数，单位mm，或留空` : null
}
export function projectIdsOf(pc: any): number[] {
  return [...new Set<number>((pc.items || []).map((item: any) => item.stdProcessId).filter((id: any) => Number.isInteger(id) && id > 0))]
}

/** 落库/快照前剥离仅用于会话内继承判断的 projectInherited 标记，避免脏字段进入 materials JSON。 */
export function serializeMaterial(row: any) {
  const rest = { ...row }
  delete rest.projectInherited
  // dev-20261024-019：未确认标记只用于保存前阻断，不进 materials JSON
  delete rest.__matDraftPending
  delete rest.__matDraftText
  return rest
}

/** 与标准BOM一致：空/0表示未填，正整数固定以mm保存；不参与用量计算。 */
export function splitNumber(value?: string | null): number | undefined {
  if (!value || !/^\d+(?:mm)?$/.test(value)) return undefined
  const n = Number(value.replace(/mm$/, ''))
  return Number.isSafeInteger(n) && n > 0 ? n : undefined
}
export function setMaterialSplit(row: any, value?: number | null) {
  row.split = value != null && Number.isSafeInteger(value) && value > 0 ? `${value}mm` : ''
}
