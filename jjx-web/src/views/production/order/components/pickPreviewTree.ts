import { BomSourceTypeEnum } from '@/enums/product'
import type { EngineeringBomItem } from '@/types/product/bom'
import type { PickPreviewRow } from './pickPreviewPrint'

export interface PickBomDetail {
  bomId: number
  outputType?: string
  outputId?: number | null
  items?: EngineeringBomItem[]
}

export interface PickPreviewTreeNode {
  key: string
  row: PickPreviewRow
  children: PickPreviewTreeNode[]
  error?: string
}

export function flatPickPreviewNodes(rows: PickPreviewRow[]): PickPreviewTreeNode[] {
  return rows.map((row, index) => ({ key: `pick:${index}`, row, children: [] }))
}

/** 只读取显式 subBomId，分组 parentMaterialId 不参与制造层级；根行保持原引用以保留实领编辑。 */
export async function buildPickPreviewTree(
  rows: PickPreviewRow[],
  bomId: number,
  getBom: (id: number) => Promise<PickBomDetail>
): Promise<PickPreviewTreeNode[]> {
  const cache = new Map<number, Promise<PickBomDetail>>()
  const load = (id: number) => {
    if (!cache.has(id)) cache.set(id, getBom(id))
    return cache.get(id)!
  }
  const root = await load(bomId)
  if (Number(root?.bomId) !== bomId || !Array.isArray(root.items)) {
    throw new Error('工单绑定 BOM 明细不可用')
  }
  const rootItems = new Map(root.items.map((item) => [Number(item.itemId), item]))
  const nodes = flatPickPreviewNodes(rows)

  async function expand(node: PickPreviewTreeNode, item: EngineeringBomItem, path: number[]) {
    if (item.sourceType !== BomSourceTypeEnum.MAKE || !item.subBomId) return
    const subBomId = Number(item.subBomId)
    try {
      if (path.includes(subBomId)) throw new Error('制造 BOM 循环引用，已停止展开')
      if (path.length >= 32) throw new Error('制造 BOM 层级超过 32 层，已停止展开')
      const detail = await load(subBomId)
      if (
        Number(detail?.bomId) !== subBomId ||
        detail.outputType !== 'MATERIAL' ||
        Number(detail.outputId) !== Number(item.materialId)
      ) {
        throw new Error('制造 BOM 产出与当前材料不一致')
      }
      if (!detail.items?.length) throw new Error('制造 BOM 没有明细')
      const sorted = [...detail.items].sort(
        (a, b) => (a.itemOrder ?? 0) - (b.itemOrder ?? 0) || Number(a.itemId) - Number(b.itemId)
      )
      node.children = await Promise.all(
        sorted.map(async (child, index) => {
          const childNode: PickPreviewTreeNode = {
            key: `${node.key}/bom:${subBomId}/item:${child.itemId ?? index}`,
            row: {
              materialId: child.materialId,
              materialCode: child.materialCode,
              materialName: child.materialName,
              specification: child.specification,
              processName: child.processName,
              unit: child.unit,
              moduleQty: child.moduleQty,
              baseQty: child.baseQty,
              isBomDetail: true,
            },
            children: [],
          }
          await expand(childNode, child, [...path, subBomId])
          return childNode
        })
      )
    } catch (error) {
      node.error = error instanceof Error ? error.message : '制造 BOM 明细加载失败'
    }
  }

  await Promise.all(
    nodes.map(async (node) => {
      if (node.row.substitute) return
      const item = rootItems.get(Number(node.row.bomItemId))
      if (!item || Number(item.materialId) !== Number(node.row.materialId)) {
        node.error = '领料行与工单绑定 BOM 明细不一致，未展开'
        return
      }
      await expand(node, item, [bomId])
    })
  )
  return nodes
}

export function flattenPickPreviewTree(
  nodes: PickPreviewTreeNode[],
  collapsed = new Set<string>()
) {
  const result: Array<{
    node: PickPreviewTreeNode
    row: PickPreviewRow
    depth: number
    sequence: string
  }> = []
  function visit(level: PickPreviewTreeNode[], depth: number, prefix: string) {
    level.forEach((node, index) => {
      const sequence = prefix ? `${prefix}.${index + 1}` : String(index + 1)
      result.push({ node, row: node.row, depth, sequence })
      if (!collapsed.has(node.key)) visit(node.children, depth + 1, sequence)
    })
  }
  visit(nodes, 0, '')
  return result
}
