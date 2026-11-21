import type { EngineeringBomItem } from '@/types/product/bom'
import { parseSpecModule } from './bomSpecImport'

export interface BomLayoutMaterial {
  materialId?: number
  materialCode?: string
  materialName: string
  unit?: string
  specification?: string
  searchPending?: boolean
  widthMm?: number
  lengthMm?: number
  pitchMm?: number
  /** 生产BOM子料原始参数，与父行比例分开保存。 */
  pcsPerPitch: 1
}

export interface BomLayoutDraft {
  sourceRow: number
  materialId?: number
  materialName: string
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
        materialName: name,
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

/** 方案B：每个Excel父行只生成一条产品BOM材料行，子料及其几何参数一律不复制。 */
export function productBomItemsFromLayout(drafts: BomLayoutDraft[]): EngineeringBomItem[] {
  return drafts.map((draft, index) => {
    positive(draft.moduleQty, '模数', 4)
    positive(draft.baseQty, '基数', 4)
    return {
      materialId: draft.materialId || 0,
      materialCode: '',
      materialName: draft.materialName,
      parentMaterialId: null,
      processName: draft.processName,
      isCommon: !draft.processName.trim(),
      specification: draft.specification,
      moduleQty: draft.moduleQty,
      baseQty: draft.baseQty,
      split: '',
      pitchMm: null,
      pcsPerPitch: 1,
      unit: draft.unit || 'PCS',
      quantity: 0,
      lossRate: 0,
      sortOrder: index + 1,
    }
  })
}

/** 仅在用户显式另存时传给独立生产BOM窗口；本卡只承接0或1条子料。 */
export function manufacturingItemsFromLayout(materials: BomLayoutMaterial[]): EngineeringBomItem[] {
  if (materials.length > 1) throw new Error('多条子料的生产BOM口径待确定，本次仅支持0或1条子料')
  return materials.map((material, index) => {
    if (material.searchPending) throw new Error('子料搜索尚未确认，请先选择候选物料或取消修改')
    if (material.widthMm != null) positive(material.widthMm, '宽度', 2)
    if (material.pitchMm != null) positive(material.pitchMm, '跳距', 2)
    if (material.pcsPerPitch !== 1) throw new Error('每跳片数本次固定为1')
    return {
      materialId: material.materialId || 0,
      materialCode: material.materialCode || '',
      materialName: material.materialName,
      specification: material.specification || '',
      widthMm: material.widthMm,
      lengthMm: material.lengthMm,
      pitchMm: material.pitchMm ?? null,
      pcsPerPitch: material.pcsPerPitch,
      unit: material.unit || (material.pitchMm != null ? 'M' : 'PCS'),
      quantity: 0,
      lossRate: 0,
      sortOrder: index + 1,
    }
  })
}
