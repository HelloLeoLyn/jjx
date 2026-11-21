import type { EngineeringBomItem } from '@/types/product/bom'
import { parseSpecModule } from './bomSpecImport'

export interface BomLayoutMaterial {
  materialId?: number
  materialName: string
  widthMm?: number
  lengthMm?: number
  pitchMm?: number
  /** 排版资料；比例计算模式下不启用耗用跳距组。 */
  pcsPerPitch: 1
}

export interface BomLayoutDraft {
  sourceRow: number
  processName: string
  specification: string
  moduleQty: number
  baseQty: number
  unit: string
  materials: BomLayoutMaterial[]
}

const text = (value: unknown) => String(value ?? '').trim()
const header = (value: unknown) => text(value).replace(/\s|[（(].*?[）)]/g, '')
const aliases = {
  combined: ['规格及模数'],
  name: ['材料', '材料名称', '原料品名', '品名'],
  process: ['项目', '项目名称'],
  unit: ['单位'],
}

function columns(rows: unknown[][]) {
  for (let index = 0; index < rows.length; index++) {
    const cells = (rows[index] || []).map(header)
    const combined = cells.findIndex((cell) => aliases.combined.includes(cell))
    if (combined < 0) continue
    const find = (key: 'name' | 'process' | 'unit') =>
      cells.findIndex((cell) => aliases[key].includes(cell))
    return { index, combined, name: find('name'), process: find('process'), unit: find('unit') }
  }
  return null
}

export const hasBomLayoutHeader = (rows: unknown[][]): boolean => columns(rows) !== null

function positive(value: number, label: string, decimals: number): number {
  if (
    !Number.isFinite(value) ||
    value <= 0 ||
    value >= 100000000 ||
    !new RegExp(`^\\d+(\\.\\d{1,${decimals}})?$`).test(String(value))
  ) {
    throw new Error(`${label}必须大于0且最多${decimals}位小数、8位整数`)
  }
  return value
}

/** 只解析导入原文，不查物料、不计算耗用、不创建物料档案。 */
export function parseBomLayoutRows(rows: unknown[][]): BomLayoutDraft[] {
  const col = columns(rows)
  if (!col) throw new Error('未找到「规格及模数」表头')
  const drafts: BomLayoutDraft[] = []
  for (let index = col.index + 1; index < rows.length; index++) {
    const row = rows[index] || []
    const name = text(row[col.name])
    const raw = text(row[col.combined])
    if (/^(合计|总计|小计)/.test(name) || (!name && !raw)) continue
    try {
      const normalized = raw.replace(/＝/g, '=').replace(/／/g, '/')
      const cut = normalized.match(
        /^(\d+(?:\.\d+)?)\s*(?:mm)?\s*宽\s*跳\s*(\d+(?:\.\d+)?)\s*mm(?:\s*=\s*(\d+(?:\.\d+)?)\s*PCS)?$/i
      )
      const parsed = parseSpecModule(
        cut && !normalized.includes('=') ? `${normalized}=1PCS` : normalized
      )
      if (!parsed) throw new Error('规格及模数无法解析')
      const moduleQty = positive(parsed.moduleQty, '模数', 4)
      const baseQty = positive(parsed.baseQty, '基数', 4)
      let specification = parsed.specification
      let widthMm: number | undefined
      let lengthMm: number | undefined
      let pitchMm: number | undefined
      if (cut) {
        widthMm = positive(Number(cut[1]), '宽度', 2)
        pitchMm = positive(Number(cut[2]), '跳距', 2)
        specification = `${widthMm}mm*${pitchMm}mm`
      } else {
        // 普通宽×长仅记录几何尺寸，不从板材长度猜测跳距。
        const size = specification.match(
          /^(\d+(?:\.\d+)?)\s*(?:mm)?\s*[*×x]\s*(\d+(?:\.\d+)?)\s*mm$/i
        )
        if (size) {
          widthMm = positive(Number(size[1]), '宽度', 2)
          lengthMm = positive(Number(size[2]), '长度', 2)
        }
        if (parsed.split) throw new Error('请使用「205宽跳440mm=6PCS」格式填写宽度和跳距')
      }
      const names = name ? name.split(/[+＋]/).map((part) => part.trim()) : ['']
      if (names.some((part) => !part) && name) throw new Error('组合材料名称中有空项')
      drafts.push({
        sourceRow: index + 1,
        processName: text(row[col.process]),
        specification,
        moduleQty,
        baseQty,
        unit: text(row[col.unit]) || 'PCS',
        materials: names.map((materialName) => ({
          materialName,
          widthMm,
          lengthMm,
          pitchMm,
          pcsPerPitch: 1,
        })),
      })
    } catch (error) {
      throw new Error(`Excel第${index + 1}行：${(error as Error).message}`)
    }
  }
  return drafts
}

/**
 * 父行只属导入草稿；每个子明细生成一条现有 DTO，不向后端传嵌套数据。
 * 用户确认：比例是计算输入，排版跳距存 lengthMm/split，不启用 pitchMm 计算组。
 */
export function flattenBomLayoutDrafts(drafts: BomLayoutDraft[]): EngineeringBomItem[] {
  const items: EngineeringBomItem[] = []
  for (const draft of drafts) {
    positive(draft.moduleQty, '模数', 4)
    positive(draft.baseQty, '基数', 4)
    if (!draft.materials.length) throw new Error(`Excel第${draft.sourceRow}行没有材料明细`)
    for (const material of draft.materials) {
      if (material.widthMm != null) positive(material.widthMm, '宽度', 2)
      if (material.pitchMm != null) positive(material.pitchMm, '跳距', 2)
      if (material.lengthMm != null) positive(material.lengthMm, '长度', 2)
      if (material.pcsPerPitch !== 1) throw new Error('排版每跳片数固定为1，不启用跳距计算组')
      items.push({
        materialId: material.materialId || 0,
        materialCode: '',
        materialName: material.materialName,
        parentMaterialId: null,
        processName: draft.processName,
        isCommon: !draft.processName.trim(),
        specification: draft.specification,
        moduleQty: draft.moduleQty,
        baseQty: draft.baseQty,
        widthMm: material.widthMm,
        lengthMm: material.pitchMm ?? material.lengthMm,
        split: material.pitchMm != null ? `${material.pitchMm}mm` : '',
        pitchMm: null,
        pcsPerPitch: 1,
        unit: draft.unit || 'PCS',
        quantity: 0,
        lossRate: 0,
        sortOrder: items.length + 1,
      })
    }
  }
  return items
}
