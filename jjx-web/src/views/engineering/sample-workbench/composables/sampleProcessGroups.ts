/** 子工序归属保存在现有材料 JSON；稳定键与子工序备注复用现有扩展参数。 */
function paramsOf(value: unknown): Record<string, any> {
  try {
    const parsed = typeof value === 'string' ? JSON.parse(value) : value
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {}
  } catch {
    return {}
  }
}

export function hydrateProcessItems(items: any[]): any[] {
  const occurrences = new Map<string, number>()
  return items.map((item) => {
    const params = paramsOf(item.customProcessParams)
    const base = `process-${item.stdProcessId ?? item.processId ?? item.processName ?? 'custom'}`
    const occurrence = (occurrences.get(base) || 0) + 1
    occurrences.set(base, occurrence)
    return {
      ...item,
      itemKey: item.itemKey || params.sampleUic?.key || `${base}-${occurrence}`,
      subProcessRemark: item.subProcessRemark ?? params.sampleUic?.remark ?? '',
    }
  })
}

/** 已存的执行工序ID与标准工序ID分开；左侧工序库只有标准工序ID。 */
export function workbenchProcessItems(items: any[]) {
  return hydrateProcessItems(
    items.map((item) => {
      const saved = Object.prototype.hasOwnProperty.call(item, 'stdProcessId')
      return {
        ...item,
        stdProcessId: saved ? item.stdProcessId : (item.processId ?? null),
        processId: saved ? (item.processId ?? null) : null,
        processType: item.processType || '',
        processCategory: item.processCategory || '',
        icon: item.icon || '',
        hasIndex: item.hasIndex ?? 0,
        hasWorkInstruction: item.hasWorkInstruction ?? 0,
        indexNumber: item.indexNumber ?? null,
        workInstruction: item.workInstruction ?? item.processNote ?? '',
      }
    })
  )
}

export function processParams(item: any): string {
  return JSON.stringify({
    ...paramsOf(item.customProcessParams),
    sampleUic: { key: item.itemKey, remark: item.subProcessRemark || '' },
  })
}

export function materialOwner(pc: any, row: any): any | undefined {
  const items = pc.items || []
  if (row.processItemKey) return items.find((item: any) => item.itemKey === row.processItemKey)
  const projects = Array.isArray(row.projectIds) ? row.projectIds : []
  if (projects.length === 1) {
    const candidates = items.filter(
      (item: any) => Number(item.stdProcessId) === Number(projects[0])
    )
    if (candidates.length === 1) return candidates[0]
  }
  return items.length === 1 ? items[0] : undefined
}

export function assignMaterial(row: any, item: any) {
  row.processItemKey = item.itemKey
  row.projectIds = item.stdProcessId ? [Number(item.stdProcessId)] : []
  row.projectInherited = false
  return row
}

export function resolveMaterialOwners(pc: any, rows: any[]) {
  return rows.map((source) => {
    const row = { ...source }
    const owner = materialOwner(pc, row)
    return owner ? assignMaterial(row, owner) : row
  })
}

/** 老版共用清单只取一次；来源各有材料时按子工序保留，同材料跨子工序不合并。 */
export function mergeProcessMaterials(processes: any[]): string | null {
  const items = hydrateProcessItems(processes)
  const read = (json: string) => {
    try {
      const rows = JSON.parse(json || '[]')
      return Array.isArray(rows) ? rows : []
    } catch {
      return []
    }
  }
  const same = items.every((item) => (item.materials || '') === (items[0]?.materials || ''))
  if (same) {
    const rows = resolveMaterialOwners({ items }, read(items[0]?.materials))
    return rows.length ? JSON.stringify(rows) : null
  }
  const result: any[] = []
  const seen = new Map<string, number>()
  for (const item of items) {
    const occurrences = new Map<string, number>()
    for (const source of read(item.materials)) {
      const row = { ...source }
      if (!row.processItemKey) assignMaterial(row, item)
      const key = JSON.stringify(row)
      const occurrence = (occurrences.get(key) || 0) + 1
      occurrences.set(key, occurrence)
      if (occurrence > (seen.get(key) || 0)) result.push(row)
      seen.set(key, Math.max(occurrence, seen.get(key) || 0))
    }
  }
  return result.length ? JSON.stringify(result) : null
}
