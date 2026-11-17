import { entryNumber } from '@/api/purchase/orderEntry'
import type { ProcessingTreeNode } from '@/api/purchase/orderEntry'
import { PurchaseProcessingOperationEnum } from '@/enums/purchase/processing'

export const processingTreeSpec = (n: ProcessingTreeNode) =>
  `${entryNumber(n.widthMm)}mm × ${entryNumber(n.unit === 'ROLL' ? Number(n.lengthMm) / 1000 : n.lengthMm)}${n.unit === 'ROLL' ? 'm' : 'mm'}`

interface DisplayBranch {
  key: string
  node: ProcessingTreeNode
  role: string
  quantity: number
  group: boolean
  sourceOnly: boolean
  slitSources: ProcessingTreeNode[]
  children: DisplayBranch[]
}

export interface ProcessingTreeDisplayRow extends DisplayBranch {
  depth: number
  number: string
  sourceLabel: string
  path: string
}

/** 仅整理显示所属关系，保留原节点引用和操作路径，不改快照或计算几何。 */
export function processingTreeDisplayRows(tree: ProcessingTreeNode): ProcessingTreeDisplayRow[] {
  function results(parent: ProcessingTreeNode, steps: ProcessingTreeNode[]): ProcessingTreeNode[] {
    return (parent.children || []).flatMap((child) => {
      // 全部继续分切的宽度余料只是过程量；其结果仍属于同一批来源母卷。
      const temporaryWidth =
        child.nodeId === `${parent.nodeId}-width` &&
        child.operation?.type === PurchaseProcessingOperationEnum.SLIT.value &&
        Number(child.operation.useQuantity) === Number(child.quantity) &&
        child.children?.length
      if (!temporaryWidth) return [child]
      steps.push(child)
      return results(child, steps)
    })
  }
  function branch(node: ProcessingTreeNode): DisplayBranch {
    const steps: ProcessingTreeNode[] = []
    const all = results(node, steps)
    const base: DisplayBranch = {
      key: node.nodeId,
      node,
      role: node.role,
      quantity: node.quantity,
      group: false,
      sourceOnly: false,
      slitSources:
        node.operation?.type === PurchaseProcessingOperationEnum.SLIT.value ? [node, ...steps] : [],
      children: [],
    }
    if (node.operation && Number(node.operation.useQuantity) < Number(node.quantity)) {
      const used: DisplayBranch = {
        ...base,
        key: `${node.nodeId}::used`,
        role: `加工其中${entryNumber(node.operation.useQuantity)}${node.unit === 'ROLL' ? '卷' : '块'}`,
        quantity: node.operation.useQuantity,
        group: true,
        children: all.filter((c) => c.nodeId !== `${node.nodeId}-unused`).map(branch),
      }
      base.sourceOnly = true
      base.children = [used, ...all.filter((c) => c.nodeId === `${node.nodeId}-unused`).map(branch)]
    } else base.children = all.map(branch)
    return base
  }
  const rows: ProcessingTreeDisplayRow[] = []
  function visit(
    b: DisplayBranch,
    depth: number,
    number: string,
    path: string,
    sourceLabel: string
  ) {
    const label = `序号${number} ${b.role}`
    const fullPath = [path, `${b.role} ${processingTreeSpec(b.node)}`].filter(Boolean).join(' / ')
    rows.push({ ...b, depth, number, sourceLabel: b.group ? sourceLabel : label, path: fullPath })
    b.children.forEach((child, i) => visit(child, depth + 1, `${number}.${i + 1}`, fullPath, label))
  }
  visit(branch(tree), 0, '1', '', '')
  return rows
}
