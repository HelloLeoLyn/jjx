import type {
  BomReviewPackage,
  BomReviewPackageItem,
  BomReviewPackageNode,
} from '@/types/product/bom'

export interface BomReviewTreeRow {
  key: string
  kind: 'scheme' | 'material'
  bomId: number
  label: string
  node?: BomReviewPackageNode
  item?: BomReviewPackageItem
  issues: string[]
  children: BomReviewTreeRow[]
}

/** 只组织展示关系；处理范围、去重和状态变化仍以服务端预览为准。共享方案按每条引用路径展示。 */
export function buildBomReviewTree(plan: BomReviewPackage): BomReviewTreeRow[] {
  const nodes = new Map(plan.nodes.map((node) => [node.bomId, node]))
  const issuesFor = (bomId: number, itemId?: number) => [
    ...new Set([
      ...(nodes.get(bomId)?.issues || [])
        .filter((issue) => issue.itemId === itemId)
        .map((issue) => issue.message)
        .filter((message): message is string => typeof message === 'string'),
      ...(plan.blockingIssues || [])
        .filter((issue) => issue.bomId === bomId && issue.itemId === itemId)
        .map((issue) => issue.message),
    ]),
  ]
  const walk = (bomId: number, key: string, ancestors: Set<number>): BomReviewTreeRow => {
    const node = nodes.get(bomId)
    const row: BomReviewTreeRow = {
      key,
      kind: 'scheme',
      bomId,
      node,
      label: node?.outputName || node?.bomName || node?.bomCode || `BOM #${bomId}`,
      issues: issuesFor(bomId),
      children: [],
    }
    if (ancestors.has(bomId)) {
      row.issues.push('引用形成循环，请检查下层方案')
      return row
    }
    if (!node) {
      row.issues.push('预览中缺少此方案，请刷新检查')
      return row
    }
    const nextAncestors = new Set(ancestors).add(bomId)
    const items = node.items || []
    const ids = new Set(items.map((item) => item.itemId).filter((id) => id != null))
    const visited = new Set<BomReviewPackageItem>()
    const walkItem = (
      item: BomReviewPackageItem,
      index: number,
      parentKey: string
    ): BomReviewTreeRow => {
      visited.add(item)
      const itemKey = `${parentKey}/item-${item.itemId ?? index}`
      const material: BomReviewTreeRow = {
        key: itemKey,
        kind: 'material',
        bomId,
        item,
        label: [item.materialCode, item.materialName].filter(Boolean).join(' · ') || '未命名材料',
        issues: issuesFor(bomId, item.itemId),
        children: [],
      }
      if (item.subBomId)
        material.children.push(
          walk(item.subBomId, `${itemKey}/bom-${item.subBomId}`, nextAncestors)
        )
      items.forEach((child, childIndex) => {
        if (item.itemId != null && child.parentMaterialId === item.itemId && !visited.has(child)) {
          material.children.push(walkItem(child, childIndex, itemKey))
        }
      })
      return material
    }
    items.forEach((item, index) => {
      if (!item.parentMaterialId || !ids.has(item.parentMaterialId))
        row.children.push(walkItem(item, index, key))
    })
    // 异常父行引用也必须显示，不能让预检中的问题行从页面消失。
    items.forEach((item, index) => {
      if (!visited.has(item)) row.children.push(walkItem(item, index, key))
    })
    return row
  }
  return nodes.has(plan.rootBomId) ? [walk(plan.rootBomId, `bom-${plan.rootBomId}`, new Set())] : []
}
