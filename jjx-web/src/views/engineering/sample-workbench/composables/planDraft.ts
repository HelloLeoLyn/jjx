/** Only editable values participate in an edit session or dirty comparison. */
export function materialValues(pc: any): any[] {
  let rows = pc.materialRows
  if (!pc.editing) {
    try { rows = JSON.parse(pc.materials || '[]') } catch { rows = [] }
  }
  if (!Array.isArray(rows)) rows = []
  return (rows || []).filter((m: any) => String(m.name || '').trim()).map((m: any) => {
    const { options, loading, uid, pageNum, total, lastQuery, _matKw, ...value } = m
    return value
  })
}
export function cardDraft(pc: any) {
  return { items: pc.items, category: pc.category, operationRemark: pc.operationRemark, materials: materialValues(pc) }
}
export function planFingerprint(plan: any[], prints: any[]): string {
  return JSON.stringify({ plan: plan.map(cardDraft), prints: prints.filter((r) => String(r.printName || '').trim()).map((r) => ({
    processId: r.processId, printName: r.printName, category: r.category, colorNo: r.colorNo,
    colorNoLabel: r.colorNoLabel, inkMaterialId: r.inkMaterialId, inkNo: r.inkNo, screenNo: r.screenNo, materials: r.materials,
  })) })
}
export function createCardEditSessions() {
  const snapshots = new WeakMap<object, any>()
  return {
    begin(pc: any) {
      if (snapshots.has(pc)) return
      snapshots.set(pc, JSON.parse(JSON.stringify({ items: pc.items, category: pc.category, operationRemark: pc.operationRemark, materials: pc.materials, materialRows: pc.materialRows, saveState: pc.saveState })))
    },
    cancel(pc: any) {
      const snapshot = snapshots.get(pc)
      if (snapshot) Object.assign(pc, snapshot)
      pc.editing = false
      snapshots.delete(pc)
    },
    finish(pc: any) {
      const rows = materialValues(pc)
      pc.materials = rows.length ? JSON.stringify(rows) : null
      pc.editing = false
      snapshots.delete(pc)
    },
  }
}
