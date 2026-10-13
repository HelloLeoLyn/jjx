import { attachmentApi } from '@/api/system/attachment'
import { standardProcessApi } from '@/api/product/standardProcess'
import { ProcessCategoryEnum } from '@/enums/product'
import { productFileCategoryLabel, ENGINEERING_DRAWING_VISIBLE_CATEGORIES } from '@/components/product/productFileCategories'
import { DrawingCurrentFlagEnum, DrawingReleaseFlagEnum, DrawingFileRoleEnum } from '@/enums/product/drawing'
import { productWorkSpecApi, normalizeWorkSpec, type ProductWorkSpec, type DieLocationMap } from '@/api/product/workSpec'
import { productBomApi } from '@/api/product/bom'
import { productRouteApi } from '@/api/product/routing'
import type { FileImage } from '@/components/product/productFilePreview'
import { calculateBomQuantity } from '@/utils/bomQuantity'
import { parseSnapshotStrict, sectionForCategory, detectFileKind, buildVersionFiles, snapshotFileToDocFile, snapshotWarnings, type DocFile } from './versionDocset'
export { renderFile, type FileImage } from '@/components/product/productFilePreview'
export type { DocFile } from './versionDocset'
export { snapshotFileToDocFile } from './versionDocset'

export const documentSections = [
  { key: 'customer', label: '客供资料', note: '客供稿 · 客户确认样品' },
  { key: 'sample', label: '样品', note: '样品实物照片' },
  { key: 'spec', label: '产品作业规范', note: '材料 · 流程 · 结构图' },
  { key: 'print', label: '印刷规范', note: '印刷明细 · 整组备注 · 印刷指导图' },
  { key: 'atlas', label: '工程图集', note: '结构图 · 面板图 · 线路图等' },
  { key: 'ink', label: '油墨调配记录表', note: '空白记录表 · 每页五组' },
  { key: 'color', label: '分色检查表', note: '规范 · 面板菲林分色 · 线路 · 刀模治具' },
]

export function isEngineeringFile(file: DocFile): boolean {
  return file.productFile && (!!file.drawingNo || ENGINEERING_DRAWING_VISIBLE_CATEGORIES.includes(file.category))
}
/** 一个图纸版本仅默认选打印件；没有打印件时才允许可预览的原稿。 */
export function defaultDocumentFiles(files: DocFile[]): DocFile[] {
  return files.filter(file => {
    if (file.kind === 'other') return false
    // 分色检查表以工程录入的电子表为主；历史附件不默认选中，避免重复打印。
    if (file.section === 'color') return false
    if (!isEngineeringFile(file)) return true
    // 发布版本清单里的图纸即「当时已发布集合」，不受当前工作态现行/下发标记过滤（dev-20261013-009）。
    if (file.published) return true
    if (!file.drawingNo || file.isCurrent !== DrawingCurrentFlagEnum.CURRENT.value || file.released !== DrawingReleaseFlagEnum.RELEASED.value) return false
    if (file.fileRole === DrawingFileRoleEnum.PRINT.value) return true
    return file.fileRole === DrawingFileRoleEnum.ORIGINAL.value && !files.some(other => other.productFile && other.drawingNo === file.drawingNo && other.version === file.version && other.fileRole === DrawingFileRoleEnum.PRINT.value)
  })
}
export function documentFileCaption(file: DocFile): string {
  const parts = file.name ? [file.name] : []
  if (file.drawingNo) parts.push(file.drawingNo)
  if (file.version) parts.push(`版本 ${file.version}`)
  // 发布版本清单未记录现行/下发快照，不臆造状态标签（dev-20261013-009）。
  if (isEngineeringFile(file) && !file.published) {
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
  kind: 'spec' | 'spec-details' | 'print' | 'color' | 'image' | 'empty' | 'flow' | 'ink'
  rows?: Record<string, any>[]
  groups?: { label: string; symbol: string; rows: Record<string, any>[]; value?: string; capacity?: number; remark?: string }[]
  showFilm?: boolean
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
export function dieLocationKey(row: Record<string, any>): string {
  return [row?.materialCode, row?.layer, row?.processName].map(value => String(value ?? '').trim()).join('|')
}
export function dieLocationLabel(row: Record<string, any>): string {
  const extra = [row?.layer, row?.processName].map(value => String(value ?? '').trim()).filter(Boolean).join(' / ')
  const base = String(row?.materialName ?? row?.materialCode ?? '').trim() || '-'
  return extra ? `${base}（${extra}）` : base
}
export function flattenedBom(items?: Record<string, any>[] | null): Record<string, any>[] {
  return flatten(items || [])
}
/** 刀模位置按 BOM 行（展平后）对齐取值，与纸张行序一致。map 优先；该行无 map 值时回退旧版多行文本（dieLocationText，按行对齐）。 */
export function dieLocationRows(map: DieLocationMap | undefined, items: Record<string, any>[] | null | undefined, legacyText?: string): string[] {
  const legacy = legacyText ? legacyText.split(/\r?\n/) : []
  return flattenedBom(items).map((row, index) => (map && map[dieLocationKey(row)]) || (legacy[index] || '').trim())
}
function specText(data: DocsetData) {
  return {
    engineeringNotes: data.workSpec.engineeringRequirements,
    diePositionRows: dieLocationRows(data.workSpec.dieLocation, data.bom.items || [], data.workSpec.dieLocationText),
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
    const originalName = row.fileName || row.file_name || '未命名文件'
    const name = row.displayName || row.display_name || originalName
    const category = row.category || (customer ? '客供资料' : '其他')
    const mime = row.fileType || row.file_type || ''
    const kind = detectFileKind(name, mime)
    const section = sectionForCategory(customer, category)
    data.files.push({ key: String(id), id, name, originalName, category, kind, section, productFile: !customer,
      drawingNo: row.drawingNo, version: row.version, fileRole: row.fileRole, isCurrent: row.isCurrent, released: row.released })
  }
}

/**
 * 按【已发布版本】组装文档集（dev-20261011-036；统一读取 dev-20261013-011）：只读该版快照，不读当前态。
 * - 新发布版本（含 DOCSET_JSON 完整快照）：**只读该快照**，不请求当前产品/BOM/路线/图标/附件清单补空；
 * - 旧发布版本（无 DOCSET_JSON）：退回 SPEC_JSON/BOM/ROUTING/DRAWING 引用（仍按引用 id 取已批准版本，不回退工作态），
 *   并显式标注「未固化完整快照，可能缺项」。
 */
export async function loadDocsetForVersion(productId: number, versionId: number, productCode = '', productName = ''): Promise<DocsetData> {
  const res: any = await productWorkSpecApi.getVersion(versionId)
  const version = res?.data?.version
  const items: Record<string, any>[] = res?.data?.items || []
  if (!version) throw new Error('发布版本不存在')
  const docsetItem = items.find((item) => item.resourceType === 'DOCSET_JSON')
  if (docsetItem) return buildDocsetFromSnapshot(productId, docsetItem.snapshotJson, productCode, productName)
  return buildDocsetFromLegacyRefs(productId, items, productCode, productName)
}

/**
 * 由 DOCSET_JSON 完整快照组装文档集（dev-20261013-011）。快照自包含，全程不触网。
 * 快照损坏 / 无法解析时抛错（不输出看似完整的文档）。
 */
export function buildDocsetFromSnapshot(productId: number, snapshotJson: string | null | undefined, productCode = '', productName = ''): DocsetData {
  const parsed = parseSnapshotStrict(snapshotJson)
  if (!parsed.ok) throw new Error('该发布版本的完整快照损坏，无法预览')
  const snapshot = parsed.value
  const data: DocsetData = {
    product: {
      productId,
      productCode: snapshot.productCode || productCode || '',
      productName: snapshot.productName || productName || '',
      customerName: snapshot.customerName || '',
    },
    bom: snapshot.bom || {},
    routing: snapshot.routing || {},
    workSpec: normalizeWorkSpec(snapshot.spec || {}),
    files: [],
    warnings: [],
  }
  for (const file of snapshot.files || []) data.files.push(snapshotFileToDocFile(file))
  data.warnings.push(...snapshotWarnings(snapshot, data.files.length))
  data.files.sort((a, b) => a.id - b.id)
  return data
}

/** 旧发布版本（无完整快照）退回引用读取；显式标注未固化，不回退工作态。 */
export async function buildDocsetFromLegacyRefs(productId: number, items: Record<string, any>[], productCode = '', productName = ''): Promise<DocsetData> {
  const data: DocsetData = {
    product: { productId, productCode: productCode || '', productName: productName || '' },
    bom: {}, routing: {}, workSpec: normalizeWorkSpec({}), files: [], warnings: [],
  }
  data.warnings.push('该版本未固化完整快照（旧版发布），工程图/客供等可能缺项')

  // 规范正文：SPEC_JSON 快照；缺失或解析失败显式提示，不用空对象冒充。
  const specItem = items.find((item) => item.resourceType === 'SPEC_JSON')
  if (!specItem) {
    data.warnings.push('该发布版本未包含规范正文快照')
  } else {
    const spec = parseSnapshotStrict(specItem.snapshotJson)
    if (!spec.ok) data.warnings.push('该版规范正文快照解析失败，正文可能缺项')
    data.workSpec = normalizeWorkSpec(spec.value as Partial<ProductWorkSpec>)
  }

  // BOM：引用已批准版本（按引用 id 读取，非当前态），读取失败显式提示。
  const bomItem = items.find((item) => item.resourceType === 'BOM')
  const bomRef = parseSnapshotStrict(bomItem?.snapshotJson)
  if (!bomItem) data.warnings.push('该发布版本未包含 BOM 引用')
  else if (bomRef.ok && bomRef.value.bomId) {
    try { const r: any = await productBomApi.getEngineeringBomInfo(Number(bomRef.value.bomId)); data.bom = r?.data || {} }
    catch { data.warnings.push('该版 BOM 读取失败') }
  } else if (!bomRef.ok) data.warnings.push('该版 BOM 引用解析失败')

  // 工艺路线：同上。
  const routeItem = items.find((item) => item.resourceType === 'ROUTING')
  const routeRef = parseSnapshotStrict(routeItem?.snapshotJson)
  if (!routeItem) data.warnings.push('该发布版本未包含工艺路线引用')
  else if (routeRef.ok && routeRef.value.routingId) {
    try { const r: any = await productRouteApi.getProductRouteInfo(Number(routeRef.value.routingId)); data.routing = r?.data || {} }
    catch { data.warnings.push('该版工艺路线读取失败') }
  } else if (!routeRef.ok) data.warnings.push('该版工艺路线引用解析失败')

  // 工程图：读取**全部** DRAWING 条目，按发布清单入集，不受当前现行/下发标记过滤。
  const drawing = buildVersionFiles(items)
  data.files.push(...drawing.files)
  data.warnings.push(...drawing.warnings)

  // 旧版未固化：不补取当前态冒充历史。
  data.warnings.push('该版未纳入客供资料与样品')
  data.warnings.push('材料项目图标未随该发布版本固化，预览不补取当前图标')

  data.files.sort((a, b) => a.id - b.id)
  return data
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
      const groups = [...flowGroups, { value: '', label: '未分类', symbol: '' }].map((group, j) => ({ ...group, capacity: printCapacities[j]!, remark: data.workSpec.printRemarks[group.value] || '', rows: printRows.filter((row) => group.value ? row.processCategory === group.value : !flowGroups.some((g) => g.value === row.processCategory)) }))
      const count = Math.max(1, ...groups.map((group, j) => Math.ceil(group.rows.length / printCapacities[j])))
      const showFilm = printRows.some(row => String(printParams(row).filmNo ?? '').length > 0)
      for (let i = 0; i < count; i++) pages.push({ key: `print-${i}`, section: 'print', title: '印刷规范', kind: 'print', continuation: i, showFilm, groups: groups.filter(group => group.value || group.rows.length).map(group => ({ ...group, rows: group.rows.slice(i * group.capacity, (i + 1) * group.capacity) })) })
      const details = pages.filter(page => page.kind === 'print').flatMap(page => overflow[page.key] || [])
      for (let i = 0; i < details.length; i += 48) pages.push({ key: 'print-details-' + i, section: 'print', title: '印刷规范 · 内容附页', kind: 'spec-details', detailLines: details.slice(i, i + 48) })
      // 指导图跟随印刷表，整套文档中不再在工程图集重复打印；仅允许现行已下发的预览件。
      const files = defaultDocumentFiles(data.files).filter(file => file.section === 'print' && fileIds.includes(file.key))
      for (const file of files) (images[file.key] || []).forEach((image, i) => pages.push({ key: `file-${file.key}-${i}`, section: 'print', title: '印刷指导图', kind: 'image', file, image }))
    } else if (section.key === 'ink') {
      pages.push({ key: 'ink', section: 'ink', title: '油墨调配记录表', kind: 'ink' })
    } else if (section.key === 'color') {
      // 分色检查表：电子表纸张固定一张（未填写也可预览空白），历史附件排其后。
      pages.push({ key: 'color', section: 'color', title: '规范分色检查表', kind: 'color' })
      const files = data.files.filter((file) => file.section === 'color' && fileIds.includes(file.key))
      for (const file of files) (images[file.key] || []).forEach((image, i) => pages.push({ key: `file-${file.key}-${i}`, section: 'color', title: `规范分色检查表 · ${documentFileCaption(file)}`, kind: 'image', file, image }))
    } else {
      const files = data.files.filter((file) => file.section === section.key && fileIds.includes(file.key))
      for (const file of files) (images[file.key] || []).forEach((image, i) => pages.push({ key: `file-${file.key}-${i}`, section: section.key, title: `${section.label} · ${productFileCategoryLabel(file.category)}`, kind: 'image', file, image }))
      if (!files.length) pages.push({ key: `${section.key}-empty`, section: section.key, title: section.label, kind: 'empty' })
    }
  }
  return pages
}
