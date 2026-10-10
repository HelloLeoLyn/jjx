import { attachmentApi } from '@/api/system/attachment'
import { standardProcessApi } from '@/api/product/standardProcess'
import { ProcessCategoryEnum } from '@/enums/product'
import { productFileCategoryLabel, ENGINEERING_DRAWING_VISIBLE_CATEGORIES } from '@/components/product/productFileCategories'
import { DrawingCurrentFlagEnum, DrawingReleaseFlagEnum, DrawingFileRoleEnum } from '@/enums/product/drawing'
import { productWorkSpecApi, normalizeWorkSpec, type ProductWorkSpec } from '@/api/product/workSpec'
import type { FileImage } from '@/components/product/productFilePreview'
import { calculateBomQuantity } from '@/utils/bomQuantity'
export { renderFile, type FileImage } from '@/components/product/productFilePreview'

export const documentSections = [
  { key: 'customer', label: '客供资料', note: '客供稿 · 客户确认样品' },
  { key: 'spec', label: '产品作业规范', note: '材料 · 流程 · 结构图' },
  { key: 'print', label: '印刷规范', note: '印序 · 色号 · 油墨 · 网版' },
  { key: 'atlas', label: '工程图集', note: '工程图纸 · 印刷指导图' },
  { key: 'color', label: '分色检查表', note: '已上传的检查表' },
  { key: 'sample', label: '样品', note: '样品实物照片' },
]

export interface DocFile {
  key: string
  id: number
  name: string
  category: string
  section: string
  kind: 'image' | 'pdf' | 'other'
  productFile: boolean
  drawingNo?: string
  version?: string
  fileRole?: string
  isCurrent?: number
  released?: number
}

export function isEngineeringFile(file: DocFile): boolean {
  return file.productFile && (!!file.drawingNo || ENGINEERING_DRAWING_VISIBLE_CATEGORIES.includes(file.category))
}
/** 一个图纸版本仅默认选打印件；没有打印件时才允许可预览的原稿。 */
export function defaultDocumentFiles(files: DocFile[]): DocFile[] {
  return files.filter(file => {
    if (file.kind === 'other') return false
    if (!isEngineeringFile(file)) return true
    if (!file.drawingNo || file.isCurrent !== DrawingCurrentFlagEnum.CURRENT.value || file.released !== DrawingReleaseFlagEnum.RELEASED.value) return false
    if (file.fileRole === DrawingFileRoleEnum.PRINT.value) return true
    return file.fileRole === DrawingFileRoleEnum.ORIGINAL.value && !files.some(other => other.productFile && other.drawingNo === file.drawingNo && other.version === file.version && other.fileRole === DrawingFileRoleEnum.PRINT.value)
  })
}
export function documentFileCaption(file: DocFile): string {
  const parts = file.name ? [file.name] : []
  if (file.drawingNo) parts.push(file.drawingNo)
  if (file.version) parts.push(`版本 ${file.version}`)
  if (isEngineeringFile(file)) {
    if (!file.drawingNo) parts.unshift('待归集')
    if (file.isCurrent !== DrawingCurrentFlagEnum.CURRENT.value) parts.unshift('非现行')
    if (file.released !== DrawingReleaseFlagEnum.RELEASED.value) parts.unshift('未下发')
  }
  return parts.join(' · ')
}
export interface DocsetData {
  product: Record<string, any>
  bom: Record<string, any>
  routing: Record<string, any>
  workSpec: ProductWorkSpec
  files: DocFile[]
  warnings: string[]
}

export interface DocPage {
  key: string
  section: string
  title: string
  kind: 'spec' | 'spec-details' | 'print' | 'image' | 'empty' | 'flow'
  rows?: Record<string, any>[]
  groups?: { label: string; symbol: string; rows: Record<string, any>[] }[]
  image?: FileImage
  file?: DocFile
  continuation?: number
  engineeringNotes?: string
  diePositionRows?: string[]
  changeLines?: PaperTextLine[]
  detailLines?: PaperTextLine[]
}
export interface PaperTextLine { text: string; color?: string }

/** 固定区域按中文字符宽度保守折行，超出内容另起附页，避免CSS裁掉数据。 */
function paperLines(value: string, width: number): string[] {
  return value.split(/\r?\n/).flatMap(paragraph => {
    const result: string[] = []
    let line = '', used = 0
    for (const char of Array.from(paragraph)) {
      const size = char.charCodeAt(0) < 128 ? 0.6 : 1
      if (used + size > width) { result.push(line); line = ''; used = 0 }
      line += char; used += size
    }
    result.push(line)
    return result
  })
}
function specText(data: DocsetData) {
  return {
    engineeringNotes: data.workSpec.engineeringRequirements,
    diePositionRows: paperLines(data.workSpec.dieLocation, 10),
    changeLines: [...data.workSpec.changes]
      .filter(change => change.print && change.text.trim())
      .sort((a, b) => (a.date || '').localeCompare(b.date || ''))
      .map(change => ({ text: (change.date ? change.date.slice(2) + '　' : '') + change.text, color: change.color })),
  }
}
/** 附页已由DOM确认需要，再按附页正文宽度分行；不用于判断主表是否溢出。 */
export function detailTextLines(text: string, color?: string): PaperTextLine[] {
  return paperLines(text, 44).map(line => ({ text: line, color }))
}

export function plain(value: unknown): string {
  return String(value ?? '').replace(/<[^>]*>/g, '').trim()
}
/** 作业规范只组合BOM事实；尺寸0视为未填，计件材料使用不含损耗的单位用量。 */
export function workSpecMaterialSpec(row?: Record<string, any> | null): string {
  if (!row) return ''
  const width = Number(row.widthMm), length = Number(row.lengthMm)
  const hasWidth = Number.isFinite(width) && width > 0
  const hasLength = Number.isFinite(length) && length > 0
  const specification = hasWidth && hasLength ? width + '*' + length + 'mm' : plain(row.specification)
  const module = Number(row.moduleQty)
  if (specification) return specification + (Number.isFinite(module) && module > 0 ? '=' + module + 'PCS' : '')
  const unit = plain(row.unit).toUpperCase()
  if (!hasWidth && !hasLength && (unit === 'PCS' || unit === '个') && module === 1) {
    const quantity = calculateBomQuantity(row.baseQty, row.moduleQty)
    if (quantity !== undefined) return quantity + '个/PCS'
  }
  return ''
}
export function workSpecStepSubscript(item: Record<string, any>): string {
  return [item.indexNumber == null ? '' : String(item.indexNumber), plain(item.workInstruction)].filter(Boolean).join(' ')
}
export function workSpecOperationRemark(row?: Record<string, any>): string {
  if (!row) return ''
  return [plain(row.remark), ...(row.children || []).map((item: Record<string, any>) => plain(item.remark))].filter(Boolean).join('；')
}
export function printParams(row: Record<string, any>): Record<string, any> {
  try { return JSON.parse(row.customProcessParams || '{}') || {} } catch { return {} }
}
function flatten(items: Record<string, any>[]): Record<string, any>[] {
  return items.flatMap((row) => [row, ...flatten(row.children || [])])
}
export const flowGroups = ProcessCategoryEnum.items.filter((item) => item.value !== 'OTHER')
  .map((item, index) => ({ value: item.value, label: item.label, symbol: ['□', '△', '▽'][index] || '' }))
export const printCapacities = [12, 5, 7, 4]

export async function loadDocset(productId: number): Promise<DocsetData> {
  const specification = await productWorkSpecApi.get(productId)
  const full = specification.data?.sourceData
  if (!full?.product) throw new Error('未找到产品资料')
  const data: DocsetData = { product: full.product, bom: full.bom || {}, routing: full.routing || {}, workSpec: normalizeWorkSpec(specification.data || {}), files: [], warnings: [] }
  const jobs = [
    { label: '材料项目图标', run: async () => {
      const ids = [...new Set(flatten(data.bom.items || []).map(row => Number(row.processId)).filter(Boolean))]
      const results = await Promise.allSettled(ids.map(id => standardProcessApi.getById(id)))
      const icons = new Map<number, string>()
      results.forEach((result, i) => {
        if (result.status === 'fulfilled') { const item: any = result.value.data; if (item?.icon) icons.set(ids[i], item.icon) }
        else data.warnings.push('项目图标加载失败，保留项目名称')
      })
      const enrich = (rows: any[]): any[] => rows.map(row => ({ ...row, icon: icons.get(Number(row.processId)) || row.icon, ...(row.children ? { children: enrich(row.children) } : {}) }))
      data.bom = { ...data.bom, items: enrich(data.bom.items || []) }
    } },
    { label: '客供资料', run: async () => { const r: any = await attachmentApi.customerDocs(productId); appendFiles(data, r.data || [], true) } },
    { label: '产品文件', run: async () => { const r: any = await attachmentApi.productFiles(data.product.productCode); appendFiles(data, r.data || [], false) } },
  ]
  const results = await Promise.allSettled(jobs.map((job) => job.run()))
  results.forEach((result, i) => {
    if (result.status === 'rejected') data.warnings.push(`${jobs[i].label}加载失败：${result.reason instanceof Error ? result.reason.message : '请重试'}`)
  })
  data.files.sort((a, b) => a.id - b.id)
  return data
}
function appendFiles(data: DocsetData, rows: any[], customer: boolean) {
  for (const row of rows) {
    const id = Number(row.id)
    if (!id || data.files.some((file) => file.id === id)) continue
    const name = row.fileName || row.file_name || '未命名文件'
    const category = row.category || (customer ? '询价／报价附件' : '其他')
    const ext = name.split('.').pop()?.toLowerCase()
    const mime = row.fileType || row.file_type || ''
    const kind = mime.startsWith('image/') || ['png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp', 'svg'].includes(ext)
      ? 'image' : (mime.includes('pdf') || ext === 'pdf' ? 'pdf' : 'other')
    const section = customer || ['客供稿', '客户确认样品'].includes(category) ? 'customer'
      : category === '样品照片' ? 'sample' : category === '分色检查表' ? 'color' : 'atlas'
    data.files.push({ key: String(id), id, name, category, kind, section, productFile: !customer,
      drawingNo: row.drawingNo, version: row.version, fileRole: row.fileRole, isCurrent: row.isCurrent, released: row.released })
  }
}

export function makePages(data: DocsetData, sections: string[], fileIds: string[], images: Record<string, FileImage[]>, overflow: Record<string, PaperTextLine[]> = {}): DocPage[] {
  const pages: DocPage[] = []
  const all = flatten(data.routing.items || [])
  const printRows = all.filter((row) => row.majorCategory === 'PRINT')
  const assembly = data.routing.items || []
  for (const section of documentSections.filter((item) => sections.includes(item.key))) {
    if (section.key === 'spec') {
      const bom = flatten(data.bom.items || [])
      const groups = flowGroups.map((group) => ({ ...group, rows: assembly.filter((row: any) => row.processCategory === group.value) }))
      const capacities = [14, 6, 14]
      const text = specText(data)
      const count = Math.max(1, Math.ceil(bom.length / 14), Math.ceil(text.diePositionRows.length / 14), ...groups.map((group, i) => Math.ceil(group.rows.length / capacities[i])))
      for (let i = 0; i < count; i++) pages.push({ key: `spec-${i}`, section: 'spec', title: '产品作业规范', kind: 'spec', continuation: i, engineeringNotes: text.engineeringNotes, diePositionRows: text.diePositionRows.slice(i * 14, (i + 1) * 14), changeLines: text.changeLines, rows: bom.slice(i * 14, (i + 1) * 14), groups: groups.map((group, j) => ({ label: group.label, symbol: group.symbol, rows: group.rows.slice(i * capacities[j], (i + 1) * capacities[j]) })) })
      const details = pages.filter(page => page.kind === 'spec').flatMap(page => overflow[page.key] || [])
      for (let i = 0; i < details.length; i += 48) pages.push({ key: 'spec-details-' + i, section: 'spec', title: '产品作业规范 · 工程内容附页', kind: 'spec-details', detailLines: details.slice(i, i + 48) })
      const uncategorized = assembly.filter((row: any) => !flowGroups.some((group) => group.value === row.processCategory))
      for (let i = 0; i < uncategorized.length; i += 22) pages.push({ key: `flow-${i}`, section: 'spec', title: '产品作业规范 · 未分类工序', kind: 'flow', rows: uncategorized.slice(i, i + 22) })
    } else if (section.key === 'print') {
      const groups = [...flowGroups, { value: '', label: '未分类', symbol: '' }].map((group) => ({ ...group, rows: printRows.filter((row) => group.value ? row.processCategory === group.value : !flowGroups.some((g) => g.value === row.processCategory)) }))
      const count = Math.max(1, ...groups.map((group, j) => Math.ceil(group.rows.length / printCapacities[j])))
      for (let i = 0; i < count; i++) pages.push({ key: `print-${i}`, section: 'print', title: '印刷规范', kind: 'print', continuation: i, groups: groups.filter((group) => group.value || group.rows.length).map((group, j) => ({ label: group.label, symbol: group.symbol, rows: group.rows.slice(i * printCapacities[j], (i + 1) * printCapacities[j]) })) })
    } else {
      const files = data.files.filter((file) => file.section === section.key && fileIds.includes(file.key))
      for (const file of files) (images[file.key] || []).forEach((image, i) => pages.push({ key: `file-${file.key}-${i}`, section: section.key, title: `${section.label} · ${productFileCategoryLabel(file.category)}`, kind: 'image', file, image }))
      if (!files.length) pages.push({ key: `${section.key}-empty`, section: section.key, title: section.label, kind: 'empty' })
    }
  }
  return pages
}
