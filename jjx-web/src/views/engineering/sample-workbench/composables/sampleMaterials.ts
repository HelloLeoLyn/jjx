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
  const invalid = rows.find(row => !Number.isInteger(row.baseQty) || row.baseQty < 1 || !Number.isInteger(row.moduleQty) || row.moduleQty < 1)
  return invalid ? `材料「${invalid.name || '未命名'}」模数、基数须为至少1的整数，请核对历史用量` : null
}
export function projectIdsOf(pc: any): number[] {
  return [...new Set<number>((pc.items || []).map((item: any) => item.stdProcessId).filter((id: any) => Number.isInteger(id) && id > 0))]
}

/** 落库/快照前剥离仅用于会话内继承判断的 projectInherited 标记，避免脏字段进入 materials JSON。 */
export function serializeMaterial(row: any) {
  const rest = { ...row }
  delete rest.projectInherited
  return rest
}
