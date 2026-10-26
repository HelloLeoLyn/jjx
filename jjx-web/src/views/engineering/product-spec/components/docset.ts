import { attachmentApi } from '@/api/system/attachment'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import { standardProcessApi } from '@/api/product/standardProcess'
import { ProcessCategoryEnum } from '@/enums/product'
import {
  productFileCategoryLabel,
  engineeringDocumentTypeLabel,
} from '@/components/product/productFileCategories'
import {
  DrawingCurrentFlagEnum,
  DrawingReleaseFlagEnum,
  DrawingFileRoleEnum,
} from '@/enums/product/drawing'
import {
  productWorkSpecApi,
  normalizeWorkSpec,
  type ProductWorkSpec,
  type ProjectMaterial,
  nestProjectMaterials,
  type DieLocationMap,
} from '@/api/product/workSpec'
import { productBomApi } from '@/api/product/bom'
import { productRouteApi } from '@/api/product/routing'
import type { FileImage } from '@/components/product/productFilePreview'
import { composeMaterialSpec, isZeroSpec, plainText } from '@/utils/materialSpec'
import { bomGroupRows } from '@/utils/bomGroups'
import {
  parseSnapshotStrict,
  parseDocsetSnapshot,
  sectionForDocumentType,
  detectFileKind,
  buildVersionFiles,
  snapshotFileToDocFile,
  snapshotWarnings,
  type DocFile,
} from './versionDocset'
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
  { key: 'other', label: '其他资料', note: '未分类 · 其它资料' },
]

export function isEngineeringFile(file: DocFile): boolean {
  // 图种退役后以二级类型分栏（atlas）为准；无类型的旧数据不得默认落工程图集。
  return file.productFile && (!!file.drawingNo || file.section === 'atlas')
}
/** 正式打印按文件下发集合准入；补入的未下发成员不得混入。历史快照自带当时集合。 */
export function canPrintDocumentFile(file: DocFile): boolean {
  if (file.printSelected === false) return false
  if (file.draftManaged) return file.kind !== 'other'
  return (
    file.kind !== 'other' &&
    (!isEngineeringFile(file) ||
      !!file.published ||
      (!!file.drawingNo &&
        file.section !== 'other' &&
        file.isCurrent === DrawingCurrentFlagEnum.CURRENT.value &&
        file.released === DrawingReleaseFlagEnum.RELEASED.value))
  )
}
export function orderedPrintFiles(files: DocFile[], keys: string[]): DocFile[] {
  const byKey = new Map(files.map((file) => [file.key, file]))
  const selected = [...new Set(keys)]
    .map((key) => byKey.get(key))
    .filter((file): file is DocFile => !!file && canPrintDocumentFile(file))
  return files.some((file) => file.published && typeof file.printSelected === 'boolean')
    ? selected.sort((a, b) => files.indexOf(a) - files.indexOf(b))
    : selected
}
/** 一个图纸版本仅默认选打印件；没有打印件时才允许可预览的原稿。 */
export function defaultDocumentFiles(files: DocFile[]): DocFile[] {
  return files.filter((file) => {
    if (!canPrintDocumentFile(file)) return false
    if (file.draftManaged || typeof file.printSelected === 'boolean') return true
    // 分色检查表以工程录入的电子表为主；历史附件不默认选中，避免重复打印。
    if (file.section === 'color') return false
    // 未分类文件不默认进工程图集，也不默认勾选打印
    if (file.section === 'other') return false
    if (!isEngineeringFile(file)) return true
    // 发布版本清单里的图纸即「当时已发布集合」，不受当前工作态现行/下发标记过滤（dev-20261013-009）。
    if (file.published) return true
    if (
      !file.drawingNo ||
      file.isCurrent !== DrawingCurrentFlagEnum.CURRENT.value ||
      file.released !== DrawingReleaseFlagEnum.RELEASED.value
    )
      return false
    if (file.fileRole === DrawingFileRoleEnum.PRINT.value) return true
    return (
      file.fileRole === DrawingFileRoleEnum.ORIGINAL.value &&
      !files.some(
        (other) =>
          canPrintDocumentFile(other) &&
          other.productFile &&
          other.drawingNo === file.drawingNo &&
          other.version === file.version &&
          other.fileRole === DrawingFileRoleEnum.PRINT.value
      )
    )
  })
}
export function documentFileCaption(file: DocFile): string {
  const parts = file.name ? [file.name] : []
  if (file.drawingNo) parts.push(file.drawingNo)
  if (file.version) parts.push(`版本 ${file.version}`)
  // 发布版本清单未记录现行/下发快照，不臆造状态标签（dev-20261013-009）。
  if (file.draftManaged) parts.unshift('草稿')
  if (isEngineeringFile(file) && !file.published && !file.draftManaged) {
    if (!file.drawingNo) parts.unshift('待归集')
    if (file.isCurrent !== DrawingCurrentFlagEnum.CURRENT.value) parts.unshift('非现行')
    if (file.released !== DrawingReleaseFlagEnum.RELEASED.value) parts.unshift('未下发')
  }
  return parts.join(' · ')
}
export interface DocsetData {
  published?: boolean
  product: Record<string, any>
  bom: Record<string, any>
  routing: Record<string, any>
  workSpec: ProductWorkSpec
  files: DocFile[]
  warnings: string[]
  unavailableSections?: Record<string, string>
}

export interface DocPage {
  flowHtml?: string
  flowPending?: boolean
  key: string
  section: string
  title: string
  kind: 'spec' | 'spec-details' | 'print' | 'color' | 'image' | 'empty' | 'flow' | 'ink'
  rows?: Record<string, any>[]
  groups?: {
    label: string
    symbol: string
    rows: Record<string, any>[]
    value?: string
    capacity?: number
    remark?: string
  }[]
  showFilm?: boolean
  image?: FileImage
  file?: DocFile
  continuation?: number
  engineeringNotes?: string
  diePositionRows?: string[]
  changeLines?: PaperTextLine[]
  detailLines?: PaperTextLine[]
  emptyMessage?: string
}
export interface PaperTextLine {
  text: string
  color?: string
}

/** 固定区域按中文字符宽度保守折行，超出内容另起附页，避免CSS裁掉数据。 */
function paperLines(value: string, width: number): string[] {
  return value.split(/\r?\n/).flatMap((paragraph) => {
    const result: string[] = []
    let line = '',
      used = 0
    for (const char of Array.from(paragraph)) {
      const size = char.charCodeAt(0) < 128 ? 0.6 : 1
      if (used + size > width) {
        result.push(line)
        line = ''
        used = 0
      }
      line += char
      used += size
    }
    result.push(line)
    return result
  })
}
export function dieLocationKey(row: Record<string, any>): string {
  return [row?.materialCode, row?.layer, row?.processName]
    .map((value) => String(value ?? '').trim())
    .join('|')
}
export function dieLocationLabel(row: Record<string, any>): string {
  const extra = [row?.layer, row?.processName]
    .map((value) => String(value ?? '').trim())
    .filter(Boolean)
    .join(' / ')
  const base = String(row?.materialName ?? row?.materialCode ?? '').trim() || '-'
  return extra ? `${base}（${extra}）` : base
}
export function flattenedBom(items?: Record<string, any>[] | null): Record<string, any>[] {
  return flatten(items || [])
}
const materialSourceFields = [
  'materialId',
  'materialCode',
  'materialName',
  'processId',
  'processName',
  'specification',
  'quantity',
  'unit',
  'moduleQty',
  'baseQty',
  'split',
  'widthMm',
  'lengthMm',
] as const
export function projectMaterialFromBom(
  row: Record<string, any>,
  index: number,
  spec?: ProductWorkSpec
): ProjectMaterial {
  return {
    materialId: row.materialId,
    materialCode: row.materialCode || '',
    materialName: row.materialName || '',
    processId: row.processId,
    processName: row.processName || '',
    specification: isZeroSpec(String(row.specification ?? '').trim())
      ? ''
      : row.specification || '',
    params: '',
    widthMm: row.widthMm,
    lengthMm: row.lengthMm,
    quantity: Number(row.quantity || 0),
    unit: row.unit || '',
    dieLocation:
      spec?.dieLocation[dieLocationKey(row)] || spec?.dieLocationText?.split(/\r?\n/)[index] || '',
    sortOrder: index,
    moduleQty: row.moduleQty,
    baseQty: row.baseQty,
    split: row.split,
    sourceBom: Object.fromEntries(materialSourceFields.map((key) => [key, String(row[key] ?? '')])),
  }
}
/** 只合并连续项目；同一项目再次出现时组号递增。 */
export function groupProjectMaterials(rows: Record<string, any>[]): Record<string, any>[] {
  const groups = nestProjectMaterials(rows as ProjectMaterial[])
  return groups.flatMap((group) =>
    group.materials.map((row, index) => ({
      ...row,
      processId: group.processId,
      processName: group.projectName,
      icon: group.projectIcon,
      projectGroup: group.groupNo,
      projectGroupKey: group.groupKey,
      projectSpan: index === 0 ? group.materials.length : 0,
    }))
  )
}
export function projectMaterialPrintRows(data: DocsetData): Record<string, any>[] {
  const bom = flatten(data.bom.items || [])
  if (data.workSpec.projectMaterials === undefined)
    return data.published ? groupProjectMaterials(bom) : groupBomMaterials(bom)
  return data.workSpec.projectMaterials.flatMap((group) =>
    group.materials.map((row, index) => ({
      ...row,
      processId: group.processId,
      processName: group.projectName,
      icon: group.projectIcon || bom.find((item) => item.processId === group.processId)?.icon,
      projectGroup: group.groupNo,
      projectGroupKey: group.groupKey,
      projectSpan: index === 0 ? group.materials.length : 0,
    }))
  )
}
/** 仅用于BOM回退；独立维护的项目材料及其快照沿用自己的组模型。 */
export function groupBomMaterials(rows: Record<string, any>[]): Record<string, any>[] {
  return bomGroupRows(rows, true).map(group => ({
    ...group.item, projectGroup: group.displayNumber, projectGroupKey: group.groupKey,
    projectSpan: group.span, bomGroupLabel: group.label,
  }))
}
/** 刀模位置按 BOM 行（展平后）对齐取值，与纸张行序一致。map 优先；该行无 map 值时回退旧版多行文本（dieLocationText，按行对齐）。 */
export function dieLocationRows(
  map: DieLocationMap | undefined,
  items: Record<string, any>[] | null | undefined,
  legacyText?: string
): string[] {
  const legacy = legacyText ? legacyText.split(/\r?\n/) : []
  return flattenedBom(items).map(
    (row, index) => (map && map[dieLocationKey(row)]) || (legacy[index] || '').trim()
  )
}
function specText(data: DocsetData) {
  return {
    engineeringNotes: data.workSpec.engineeringRequirements,
    diePositionRows: dieLocationRows(
      data.workSpec.dieLocation,
      data.bom.items || [],
      data.workSpec.dieLocationText
    ),
    changeLines: [...data.workSpec.changes]
      .filter((change) => change.print && change.text.trim())
      .sort((a, b) => (a.date || '').localeCompare(b.date || ''))
      .map((change) => ({
        text: (change.date ? change.date.slice(2) + '　' : '') + change.text,
        color: change.color,
      })),
  }
}
/** 附页已由DOM确认需要，再按附页正文宽度分行；不用于判断主表是否溢出。 */
export function detailTextLines(text: string, color?: string): PaperTextLine[] {
  return paperLines(text, 44).map((line) => ({ text: line, color }))
}

/** 去掉富文本标签；实现与打印共用 @/utils/materialSpec.plainText。 */
export const plain = plainText
/** B模板规格独立成列，保留尺寸与跳距，不拼接模数或基数。 */
export function workSpecMaterialDimensions(row?: Record<string, any> | null): string {
  if (!row) return ''
  const width = Number(row.widthMm),
    length = Number(row.lengthMm)
  const specification =
    Number.isFinite(width) && width > 0 && Number.isFinite(length) && length > 0
      ? width + '*' + length + 'mm'
      : plain(row.specification)
  const split = plain(row.split)
  return specification + (split ? '跳' + split : '')
}
/**
 * 作业规范「规格及模数」：只组合 BOM 事实，尺寸 0 视为未填。
 * 判定顺序与 BOM 打印、定版固化同源 → `@/utils/materialSpec`（dev-20261026-009）。
 */
export const workSpecMaterialSpec = composeMaterialSpec
/** 空 icon = 文字符号工序：预览/打印用作业说明文本当符号（dev-20261026-006）。 */
export function workSpecSymbolText(item: Record<string, any>): string {
  return plain(item.icon) ? '' : plain(item.workInstruction)
}

export function workSpecStepSubscript(item: Record<string, any>): string {
  return [item.indexNumber == null ? '' : String(item.indexNumber), plain(item.workInstruction)]
    .filter(Boolean)
    .join(' ')
}
export function workSpecOperationRemark(row?: Record<string, any>): string {
  if (!row) return ''
  return [
    plain(row.remark),
    ...(row.children || []).map((item: Record<string, any>) => plain(item.remark)),
  ]
    .filter(Boolean)
    .join('；')
}
export function printParams(row: Record<string, any>): Record<string, any> {
  try {
    return JSON.parse(row.customProcessParams || '{}') || {}
  } catch {
    return {}
  }
}
function flatten(items: Record<string, any>[]): Record<string, any>[] {
  return items.flatMap((row) => [row, ...flatten(row.children || [])])
}
export const flowGroups = ProcessCategoryEnum.items
  .filter((item) => item.value !== 'OTHER')
  .map((item, index) => ({
    value: item.value,
    label: item.label,
    symbol: ['□', '△', '▽'][index] || '',
  }))
export const printCapacities = [12, 5, 7, 4]

export async function loadDocset(productId: number): Promise<DocsetData> {
  const specification = await productWorkSpecApi.get(productId)
  const full = specification.data?.sourceData
  if (!full?.product) throw new Error('未找到产品资料')
  const data: DocsetData = {
    product: full.product,
    bom: full.bom || {},
    routing: full.routing || {},
    workSpec: normalizeWorkSpec(specification.data || {}),
    files: [],
    warnings: [],
  }
  const jobs = [
    {
      label: '材料项目图标',
      run: async () => {
        const ids = [
          ...new Set(
            [...flatten(data.bom.items || []), ...(data.workSpec.projectMaterials || [])]
              .map((row) => Number(row.processId))
              .filter(Boolean)
          ),
        ]
        const results = await Promise.allSettled(ids.map((id) => standardProcessApi.getById(id)))
        const icons = new Map<number, string>()
        results.forEach((result, i) => {
          if (result.status === 'fulfilled') {
            const item: any = result.value.data
            if (item?.icon) icons.set(ids[i], item.icon)
          } else data.warnings.push('项目图标加载失败，保留项目名称')
        })
        const enrich = (rows: any[]): any[] =>
          rows.map((row) => ({
            ...row,
            icon: icons.get(Number(row.processId)) || row.icon,
            ...(row.children ? { children: enrich(row.children) } : {}),
          }))
        data.bom = { ...data.bom, items: enrich(data.bom.items || []) }
        if (data.workSpec.projectMaterials)
          data.workSpec.projectMaterials = data.workSpec.projectMaterials.map((group) => ({
            ...group,
            projectIcon: icons.get(Number(group.processId)) || group.projectIcon,
          }))
      },
    },
    {
      label: '客供资料',
      run: async () => {
        const r: any = await attachmentApi.customerDocs(productId)
        appendFiles(data, r.data || [], true)
      },
    },
    {
      label: '产品文件',
      run: async () => {
        // 产品文件改按产品查引用（refsByProduct）：补显经公共组件新上传的图纸/文件（dev-20261014-036）
        const r: any = await attachmentRefApi.refsByProduct(productId)
        const draftFiles = specification.data?.draftFiles || []
        const managed = data.workSpec.draftManaged === true
        const selected = new Map(
          (data.workSpec.draftAttachments || []).map((file) => [file.attachmentId, file])
        )
        let otherRefs: any[] = []
        if (managed) {
          try {
            const dictionary = await attachmentRefApi.documentTypes()
            if (!Array.isArray(dictionary.data) || !dictionary.data.length)
              throw new Error('未读取到资料类型字典')
            const parents = new Map(
              dictionary.data
                .filter((type: any) => type.dict_code === 'attachment_document_type')
                .map((type: any) => {
                  const ext =
                    typeof type.ext_data === 'string'
                      ? parseSnapshotStrict(type.ext_data).value
                      : type.ext_data
                  return [type.item_key, ext?.parent]
                })
            )
            // 只补入字典证实属于其它资料域的引用；停用/新增工程类型同样不得从当前refs补回。
            otherRefs = (r.data || []).filter((file: any) => {
              const parent = parents.get(file.documentType)
              return !!parent && parent !== 'ENGINEERING_DOC'
            })
          } catch (cause) {
            data.warnings.push(
              '工程资料字典不可用，仅展示已保存草稿附件：' +
                (cause instanceof Error ? cause.message : String(cause))
            )
          }
        }
        const source = managed ? [...draftFiles, ...otherRefs] : r.data || []
        const rows = source.map((x: any) => ({
          id: x.attachmentId ?? x.id,
          fileName: x.fileName,
          displayName: x.displayName,
          category: x.category || engineeringDocumentTypeLabel(x.documentType),
          documentType: x.documentType,
          fileType: x.fileType,
          drawingNo: x.drawingNo,
          version: x.version,
          fileRole: x.fileRole,
          isCurrent: x.isCurrent,
          released: x.released,
          section:
            managed && selected.has(x.attachmentId)
              ? x.documentType === 'PRINT_GUIDE'
                ? 'print'
                : 'atlas'
              : undefined,
          draftManaged: managed && selected.has(x.attachmentId),
          printSelected:
            managed && selected.has(x.attachmentId)
              ? selected.get(x.attachmentId)?.print !== false
              : undefined,
        }))
        appendFiles(data, rows, false)
      },
    },
  ]
  const results = await Promise.allSettled(jobs.map((job) => job.run()))
  results.forEach((result, i) => {
    if (result.status === 'rejected')
      data.warnings.push(
        `${jobs[i].label}加载失败：${result.reason instanceof Error ? result.reason.message : '请重试'}`
      )
  })
  if (!data.workSpec.draftManaged) data.files.sort((a, b) => a.id - b.id)
  return data
}
function appendFiles(data: DocsetData, rows: any[], customer: boolean) {
  for (const row of rows) {
    const id = Number(row.id)
    if (!id || data.files.some((file) => file.id === id)) continue
    const originalName = row.fileName || row.file_name || '未命名文件'
    const name = row.displayName || row.display_name || originalName
    const category = row.category || (customer ? '客供资料' : '')
    const documentType = row.documentType || row.document_type || ''
    const mime = row.fileType || row.file_type || ''
    const kind = detectFileKind(name, mime)
    const section = customer
      ? 'customer'
      : row.section || sectionForDocumentType(documentType, category)
    data.files.push({
      key: String(id),
      id,
      name,
      originalName,
      category,
      documentType: documentType || undefined,
      kind,
      section,
      productFile: !customer,
      drawingNo: row.drawingNo,
      version: row.version,
      fileRole: row.fileRole,
      isCurrent: row.isCurrent,
      released: row.released,
      printSelected: row.printSelected,
      draftManaged: row.draftManaged,
    })
  }
}

/**
 * 按【已发布版本】组装文档集（dev-20261011-036；统一读取 dev-20261013-011）：只读该版快照，不读当前态。
 * - 新发布版本（含 DOCSET_JSON 完整快照）：**只读该快照**，不请求当前产品/BOM/路线/图标/附件清单补空；
 * - 旧发布版本（无 DOCSET_JSON）：退回 SPEC_JSON/BOM/ROUTING/DRAWING 引用（仍按引用 id 取已批准版本，不回退工作态），
 *   并显式标注「未固化完整快照，可能缺项」。
 */
export async function loadDocsetForVersion(
  productId: number,
  versionId: number,
  productCode = '',
  productName = ''
): Promise<DocsetData> {
  const res: any = await productWorkSpecApi.getVersion(versionId)
  const version = res?.data?.version
  const items: Record<string, any>[] = res?.data?.items || []
  if (!version) throw new Error('发布版本不存在')
  const docsetItem = items.find((item) => item.resourceType === 'DOCSET_JSON')
  if (docsetItem)
    return buildDocsetFromSnapshot(productId, docsetItem.snapshotJson, productCode, productName)
  return buildDocsetFromLegacyRefs(productId, items, productCode, productName)
}

/**
 * 由 DOCSET_JSON 完整快照组装文档集（dev-20261013-011）。快照自包含，全程不触网。
 * 快照损坏 / 无法解析时抛错（不输出看似完整的文档）。
 */
export function buildDocsetFromSnapshot(
  productId: number,
  snapshotJson: string | null | undefined,
  productCode = '',
  productName = ''
): DocsetData {
  const snapshot = parseDocsetSnapshot(snapshotJson)
  const data: DocsetData = {
    published: true,
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
  if (!data.workSpec.draftManaged) data.files.sort((a, b) => a.id - b.id)
  return data
}

/** 旧发布版本（无完整快照）退回引用读取；显式标注未固化，不回退工作态。 */
export async function buildDocsetFromLegacyRefs(
  productId: number,
  items: Record<string, any>[],
  productCode = '',
  productName = ''
): Promise<DocsetData> {
  const data: DocsetData = {
    published: true,
    product: { productId, productCode: productCode || '', productName: productName || '' },
    bom: {},
    routing: {},
    workSpec: normalizeWorkSpec({}),
    files: [],
    warnings: [],
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
    try {
      const r: any = await productBomApi.getEngineeringBomInfo(Number(bomRef.value.bomId))
      data.bom = r?.data || {}
    } catch {
      data.warnings.push('该版 BOM 读取失败')
    }
  } else if (!bomRef.ok) data.warnings.push('该版 BOM 引用解析失败')

  // 工艺路线：同上。
  const routeItem = items.find((item) => item.resourceType === 'ROUTING')
  const routeRef = parseSnapshotStrict(routeItem?.snapshotJson)
  if (!routeItem) data.warnings.push('该发布版本未包含工艺路线引用')
  else if (routeRef.ok && routeRef.value.routingId) {
    try {
      const r: any = await productRouteApi.getProductRouteInfo(Number(routeRef.value.routingId))
      data.routing = r?.data || {}
    } catch {
      data.warnings.push('该版工艺路线读取失败')
    }
  } else if (!routeRef.ok) data.warnings.push('该版工艺路线引用解析失败')

  // 工程图：读取**全部** DRAWING 条目，按发布清单入集，不受当前现行/下发标记过滤。
  const drawing = buildVersionFiles(items)
  data.files.push(...drawing.files)
  data.warnings.push(...drawing.warnings)

  // 旧版未固化：不补取当前态冒充历史。
  data.unavailableSections = {
    customer: '该发布版本未收录客供资料。请切换工作态查看当前资料；完整归档需另行发布新版本。',
  }
  data.warnings.push(data.unavailableSections.customer!)
  data.warnings.push('材料项目图标未随该发布版本固化，预览不补取当前图标')

  data.files.sort((a, b) => a.id - b.id)
  return data
}

/** B的测量源：所有材料/备注在前，全部流程随后连续排列。 */
export function workSpecFlowSource(data: DocsetData): DocPage {
  const text = specText(data)
  const materials = projectMaterialPrintRows(data)
  const diePositions =
    data.workSpec.projectMaterials !== undefined
      ? materials.map((row) => row.dieLocation || '')
      : text.diePositionRows
  return {
    key: 'spec-b-source',
    section: 'spec',
    title: '产品作业规范',
    kind: 'spec',
    continuation: 0,
    engineeringNotes: text.engineeringNotes,
    diePositionRows: diePositions,
    changeLines: text.changeLines,
    rows: materials,
    groups: flowGroups.map((group) => ({
      ...group,
      rows: (data.routing.items || []).filter((row: any) => row.processCategory === group.value),
    })),
  }
}
export function makePages(
  data: DocsetData,
  sections: string[],
  fileIds: string[],
  images: Record<string, FileImage[]>,
  overflow: Record<string, PaperTextLine[]> = {},
  specTemplate: 'A' | 'B' = 'A',
  measuredSpecPages: string[] = [],
  generateOverflowAppendix = false
): DocPage[] {
  const pages: DocPage[] = []
  const printFiles = orderedPrintFiles(data.files, fileIds)
  const all = flatten(data.routing.items || [])
  const printRows = all.filter((row) => row.majorCategory === 'PRINT')
  const assembly = data.routing.items || []
  for (const section of documentSections.filter((item) => sections.includes(item.key))) {
    if (section.key === 'spec') {
      if (specTemplate === 'B') {
        pages.push(
          ...(measuredSpecPages.length
            ? measuredSpecPages.map(
                (flowHtml, index): DocPage => ({
                  key: `spec-b-${index}`,
                  section: 'spec',
                  title: '产品作业规范',
                  kind: 'spec',
                  flowHtml,
                })
              )
            : [
                {
                  key: 'spec-b-pending',
                  section: 'spec',
                  title: '产品作业规范',
                  kind: 'spec',
                  flowPending: true,
                } as DocPage,
              ])
        )
      }
      const materialCapacity = 14
      const bom = flatten(data.bom.items || [])
      const groups = flowGroups.map((group) => ({
        ...group,
        rows: assembly.filter((row: any) => row.processCategory === group.value),
      }))
      const capacities = [14, 6, 14]
      const text = specText(data)
      const count = Math.max(
        1,
        Math.ceil(bom.length / materialCapacity),
        Math.ceil(text.diePositionRows.length / materialCapacity),
        ...groups.map((group, i) => Math.ceil(group.rows.length / capacities[i]))
      )
      for (let i = 0; specTemplate === 'A' && i < count; i++)
        pages.push({
          key: `spec-${i}`,
          section: 'spec',
          title: '产品作业规范',
          kind: 'spec',
          continuation: i,
          engineeringNotes: text.engineeringNotes,
          diePositionRows: text.diePositionRows.slice(
            i * materialCapacity,
            (i + 1) * materialCapacity
          ),
          changeLines: text.changeLines,
          rows: bom.slice(i * materialCapacity, (i + 1) * materialCapacity),
          groups: groups.map((group, j) => ({
            label: group.label,
            symbol: group.symbol,
            rows: group.rows.slice(i * capacities[j], (i + 1) * capacities[j]),
          })),
        })
      const details = pages
        .filter((page) => page.kind === 'spec')
        .flatMap((page) => overflow[page.key] || [])
      for (let i = 0; generateOverflowAppendix && i < details.length; i += 48)
        pages.push({
          key: 'spec-details-' + i,
          section: 'spec',
          title: '产品作业规范 · 工程内容附页',
          kind: 'spec-details',
          detailLines: details.slice(i, i + 48),
        })
      const uncategorized = assembly.filter(
        (row: any) => !flowGroups.some((group) => group.value === row.processCategory)
      )
      for (let i = 0; i < uncategorized.length; i += 22)
        pages.push({
          key: `flow-${i}`,
          section: 'spec',
          title: '产品作业规范 · 未分类工序',
          kind: 'flow',
          rows: uncategorized.slice(i, i + 22),
        })
      // 旧快照/资料映射明确属于spec的附件也必须实际出纸，不能只留在files数组。
      for (const file of printFiles.filter((file) => file.section === 'spec'))
        (images[file.key] || []).forEach((image, i) =>
          pages.push({
            key: `file-${file.key}-${i}`,
            section: 'spec',
            title: `产品作业规范 · ${documentFileCaption(file)}`,
            kind: 'image',
            file,
            image,
          })
        )
    } else if (section.key === 'print') {
      const groups = [...flowGroups, { value: '', label: '未分类', symbol: '' }].map(
        (group, j) => ({
          ...group,
          capacity: printCapacities[j]!,
          remark: data.workSpec.printRemarks[group.value] || '',
          rows: printRows.filter((row) =>
            group.value
              ? row.processCategory === group.value
              : !flowGroups.some((g) => g.value === row.processCategory)
          ),
        })
      )
      const count = Math.max(
        1,
        ...groups.map((group, j) => Math.ceil(group.rows.length / printCapacities[j]))
      )
      const showFilm = printRows.some((row) => String(printParams(row).filmNo ?? '').length > 0)
      for (let i = 0; i < count; i++)
        pages.push({
          key: `print-${i}`,
          section: 'print',
          title: '印刷规范',
          kind: 'print',
          continuation: i,
          showFilm,
          groups: groups
            .filter((group) => group.value || group.rows.length)
            .map((group) => ({
              ...group,
              rows: group.rows.slice(i * group.capacity, (i + 1) * group.capacity),
            })),
        })
      const details = pages
        .filter((page) => page.kind === 'print')
        .flatMap((page) => overflow[page.key] || [])
      for (let i = 0; i < details.length; i += 48)
        pages.push({
          key: 'print-details-' + i,
          section: 'print',
          title: '印刷规范 · 内容附页',
          kind: 'spec-details',
          detailLines: details.slice(i, i + 48),
        })
      // 指导图跟随印刷表，整套文档中不再在工程图集重复打印；仅允许现行已下发的预览件。
      const files = printFiles.filter((file) => file.section === 'print')
      for (const file of files)
        (images[file.key] || []).forEach((image, i) =>
          pages.push({
            key: `file-${file.key}-${i}`,
            section: 'print',
            title: '印刷指导图',
            kind: 'image',
            file,
            image,
          })
        )
    } else if (section.key === 'ink') {
      pages.push({ key: 'ink', section: 'ink', title: '油墨调配记录表', kind: 'ink' })
    } else if (section.key === 'color') {
      // 分色检查表：电子表纸张固定一张（未填写也可预览空白），历史附件排其后。
      pages.push({ key: 'color', section: 'color', title: '规范分色检查表', kind: 'color' })
      const files = printFiles.filter((file) => file.section === 'color')
      for (const file of files)
        (images[file.key] || []).forEach((image, i) =>
          pages.push({
            key: `file-${file.key}-${i}`,
            section: 'color',
            title: `规范分色检查表 · ${documentFileCaption(file)}`,
            kind: 'image',
            file,
            image,
          })
        )
    } else {
      const files = printFiles.filter((file) => file.section === section.key)
      for (const file of files)
        (images[file.key] || []).forEach((image, i) =>
          pages.push({
            key: `file-${file.key}-${i}`,
            section: section.key,
            title: `${section.label} · ${productFileCategoryLabel(file.category)}`,
            kind: 'image',
            file,
            image,
          })
        )
      if (!files.length)
        pages.push({
          key: `${section.key}-empty`,
          section: section.key,
          title: section.label,
          kind: 'empty',
          emptyMessage: data.unavailableSections?.[section.key],
        })
    }
  }
  return pages
}

/** 使用实际DOM布局顺序装入A4；返回的HTML仅来自已安全渲染的Vue只读纸张。 */
export function paginateMeasuredWorkSpec(source: HTMLElement): string[] {
  const doc = source.ownerDocument
  const host = doc.createElement('div')
  host.style.cssText = `position:absolute;left:-100000px;top:0;width:${source.offsetWidth}px;visibility:hidden;pointer-events:none`
  doc.body.append(host)
  const pages: HTMLElement[] = []
  let page: HTMLElement
  let footer: HTMLElement
  const sourceHeader = source.querySelector(':scope > header')!
  const sourceFooter = source.querySelector(':scope > footer')!
  const clone = <T extends Node>(node: T) => node.cloneNode(true) as T
  function nextPage() {
    if (pages.length >= 1000) throw new Error('作业规范页数过多，请检查内容或字号')
    page = source.cloneNode(false) as HTMLElement
    page.classList.remove('continuous')
    page.classList.add('flow-page')
    const heading = clone(sourceHeader)
    if (pages.length && !heading.querySelector('span')) {
      const continuation = doc.createElement('span')
      continuation.textContent = '（续）'
      heading.append(continuation)
    }
    footer = clone(sourceFooter) as HTMLElement
    const number = footer.lastElementChild as HTMLElement
    number.textContent = ''
    number.setAttribute('data-work-spec-page', '')
    page.append(heading, footer)
    host.append(page)
    pages.push(page)
  }
  const fits = () => {
    // 结构图首次出现的页保留结构图标题，后续纯流程页才使用上线列标题。
    for (const table of page.querySelectorAll<HTMLTableElement>('table.flows')) {
      const original = source.querySelector<HTMLTableElement>('table.flows')!
      const merged = table.tHead?.querySelector('th.structure-title')
      if (table.querySelector('.structure-art')) {
        if (!merged && original.tHead) table.tHead?.replaceWith(clone(original.tHead))
      } else if (merged) {
        const upperHeading = original.tBodies[0]?.rows[7]?.querySelectorAll('th')
        if (upperHeading?.length === 3)
          merged.replaceWith(...Array.from(upperHeading, (cell) => clone(cell)))
      }
    }
    return page.scrollHeight <= page.clientHeight + 1
  }
  const hasContent = () => page.children.length > 2
  const insert = (node: HTMLElement) => page.insertBefore(node, footer)

  function tableFragment(table: HTMLTableElement) {
    const fragment = table.cloneNode(false) as HTMLTableElement
    for (const child of table.children)
      if (child.tagName === 'COLGROUP' || child.tagName === 'THEAD') fragment.append(clone(child))
    const body = doc.createElement('tbody')
    fragment.append(body)
    insert(fragment)
    return { fragment, body }
  }

  function rowGroups(rows: HTMLTableRowElement[]) {
    const groups: HTMLTableRowElement[][] = []
    for (let start = 0; start < rows.length; ) {
      let end = start
      for (let i = start; i <= end; i++) {
        for (const cell of rows[i].cells)
          end = Math.min(rows.length - 1, Math.max(end, i + cell.rowSpan - 1))
        if (Array.from(rows[i].cells).every((cell) => cell.tagName === 'TH'))
          end = Math.min(rows.length - 1, Math.max(end, i + 1))
      }
      groups.push(rows.slice(start, end + 1))
      start = end + 1
    }
    return groups
  }

  // 超高的带跨行结构图组退化为逐行单元格，保留结构图和列对齐，不裁切正文。
  function expandSpans(rows: HTMLTableRowElement[]) {
    const active: { column: number; until: number; cell: HTMLTableCellElement }[] = []
    return rows.map((row, rowIndex) => {
      const result = clone(row)
      const original = Array.from(result.cells)
      result.replaceChildren()
      let column = 0,
        index = 0
      while (
        index < original.length ||
        active.some((span) => span.until >= rowIndex && span.column >= column)
      ) {
        const span = active.find((item) => item.column === column && item.until >= rowIndex)
        if (span) {
          const empty = span.cell.cloneNode(false) as HTMLTableCellElement
          empty.removeAttribute('rowspan')
          result.append(empty)
          column += empty.colSpan
        } else {
          const cell = original[index++]
          if (!cell) break
          if (cell.rowSpan > 1)
            active.push({ column, until: rowIndex + cell.rowSpan - 1, cell: clone(cell) })
          cell.removeAttribute('rowspan')
          result.append(cell)
          column += cell.colSpan
        }
      }
      return result
    })
  }

  function addMaterials(table: HTMLTableElement) {
    const rows = Array.from(table.tBodies[0]?.rows || []).map((row) => clone(row))
    let projectCell: HTMLTableCellElement | undefined
    let remaining = 0
    for (const row of rows) {
      if (remaining > 0 && projectCell) {
        row.insertBefore(clone(projectCell), row.cells[1])
        remaining--
      } else {
        projectCell = row.cells[1] ? clone(row.cells[1]) : undefined
        remaining = (projectCell?.rowSpan || 1) - 1
      }
      if (projectCell) projectCell.rowSpan = 1
      if (row.cells[1]) row.cells[1].rowSpan = 1
    }
    const conditions = rows.slice(0, 6).map((row) => Array.from(row.cells).slice(5).map(clone))
    const notes = table.querySelector<HTMLTableCellElement>('.engineering-notes')
    let current = tableFragment(table)
    // 每个片段沿用 A 的右侧凹凸/加工要求与列宽；正文编号取源行，不按固定容量重算。
    function rightCells() {
      const fragmentRows = Array.from(current.body.rows)
      fragmentRows.forEach((row, index) => {
        while (row.cells.length > 5) row.deleteCell(5)
        if (index < 6) row.append(...(conditions[index] || []).map(clone))
        else if (index === 6 && notes) {
          const cell = clone(notes)
          cell.rowSpan = fragmentRows.length - 6
          row.append(cell)
        }
      })
    }
    for (const row of rows) {
      const copy = clone(row)
      while (copy.cells.length > 5) copy.deleteCell(5)
      current.body.append(copy)
      rightCells()
      if (fits()) continue
      copy.remove()
      rightCells()
      if (!current.body.rows.length) {
        current.fragment.remove()
        if (!hasContent()) throw new Error('材料单行超过A4可用高度，请检查字号或加工要求')
      }
      nextPage()
      current = tableFragment(table)
      current.body.append(copy)
      rightCells()
      if (!fits()) throw new Error('材料单行超过A4可用高度，请检查字号或加工要求')
    }
    // 跨页时重建项目单元格，保留同一个组序号；右侧凹凸/加工要求仍按原列定位。
    for (const printedPage of pages)
      for (const materialTable of printedPage.querySelectorAll<HTMLTableElement>(
        'table.materials'
      )) {
        let first: HTMLTableCellElement | undefined, group: string | undefined
        for (const row of Array.from(materialTable.tBodies[0]?.rows || [])) {
          const next = row.dataset.materialGroup
          const cell = row.cells[1]
          if (next && next === group && first) {
            first.rowSpan++
            cell.remove()
          } else {
            first = cell
            group = next
          }
        }
      }
  }

  function addTable(table: HTMLTableElement) {
    if (table.classList.contains('materials')) {
      addMaterials(table)
      return
    }
    let current = tableFragment(table)
    function advance() {
      if (!current.body.children.length) current.fragment.remove()
      nextPage()
      current = tableFragment(table)
    }
    function splitRow(row: HTMLTableRowElement) {
      const selector =
        '.material-remarks-content,.material-name,.cell-single,.operation-remark,.symbol sub,.die-position'
      let remaining = Array.from(row.querySelectorAll<HTMLElement>(selector), (node) =>
        Array.from(node.textContent || '')
      )
      if (!remaining.some((text) => text.length))
        throw new Error('单行内容超过A4可用高度，请减小该列字号')
      let first = true
      while (remaining.some((text) => text.length)) {
        const copy = clone(row)
        if (!first) copy.querySelectorAll('.structure').forEach((cell) => cell.replaceChildren())
        const targets = Array.from(copy.querySelectorAll<HTMLElement>(selector))
        current.body.append(copy)
        const maximum = Math.max(...remaining.map((text) => text.length))
        const prefixLength = (text: string[], amount: number) =>
          Math.ceil((text.length * amount) / maximum)
        function setPrefix(amount: number) {
          targets.forEach((target, i) => {
            target.textContent = remaining[i].slice(0, prefixLength(remaining[i], amount)).join('')
          })
        }
        let low = 0,
          high = maximum
        while (low < high) {
          const mid = Math.ceil((low + high) / 2)
          setPrefix(mid)
          if (fits()) low = mid
          else high = mid - 1
        }
        if (!low) {
          copy.remove()
          const occupied = current.body.children.length > 0 || page.children.length > 3
          if (!occupied) throw new Error('该内容最小片段无法放入A4，请减小字号')
          advance()
          continue
        }
        setPrefix(low)
        first = false
        remaining = remaining.map((text) => text.slice(prefixLength(text, low)))
        if (remaining.some((text) => text.length)) advance()
      }
    }
    function addRows(rows: HTMLTableRowElement[]) {
      const copies = rows.map((row) => clone(row))
      current.body.append(...copies)
      if (fits()) return
      copies.forEach((row) => row.remove())
      if (
        rows.length === 1 &&
        rows[0].classList.contains('material-remarks') &&
        rows[0].querySelector('.material-remarks-content')?.textContent
      ) {
        splitRow(rows[0])
        return
      }
      if (current.body.children.length || page.children.length > 3) {
        advance()
        current.body.append(...copies)
        if (fits()) return
        copies.forEach((row) => row.remove())
      }
      if (rows.length > 1) {
        expandSpans(rows).forEach((row) => addRows([row]))
        return
      }
      splitRow(rows[0])
    }
    rowGroups(Array.from(table.tBodies[0]?.rows || [])).forEach(addRows)
    if (table.tFoot) addRows(Array.from(table.tFoot.rows))
    if (!current.body.children.length) current.fragment.remove()
  }

  function splitTextBlock(block: HTMLElement, selector: string) {
    const sourceText = block.querySelector<HTMLElement>(selector)!
    const records = sourceText.children.length
      ? (Array.from(sourceText.children) as HTMLElement[])
      : [sourceText]
    let remaining = records.map((node) => ({ node, text: Array.from(node.textContent || '') }))
    while (remaining.some((record) => record.text.length)) {
      const copy = clone(block),
        target = copy.querySelector<HTMLElement>(selector)!
      insert(copy)
      function setPrefix(amount: number) {
        target.replaceChildren()
        for (const record of remaining) {
          if (!record.text.length) continue
          if (!amount) break
          const part = record.node.cloneNode(false) as HTMLElement
          const count = Math.min(amount, record.text.length)
          part.textContent = record.text.slice(0, count).join('')
          target.append(part)
          amount -= count
        }
      }
      let low = 0,
        high = remaining.reduce((sum, record) => sum + record.text.length, 0)
      while (low < high) {
        const mid = Math.ceil((low + high) / 2)
        setPrefix(mid)
        if (fits()) low = mid
        else high = mid - 1
      }
      if (!low) {
        copy.remove()
        if (!hasContent()) throw new Error('变更内容最小片段无法放入A4，请减小字号')
        nextPage()
        continue
      }
      setPrefix(low)
      let consumed = low
      remaining = remaining.map((record) => {
        const count = Math.min(consumed, record.text.length)
        consumed -= count
        return { ...record, text: record.text.slice(count) }
      })
      if (remaining.some((record) => record.text.length)) nextPage()
    }
  }
  try {
    nextPage()
    for (const child of Array.from(source.children) as HTMLElement[]) {
      if (child === sourceHeader || child === sourceFooter) continue
      if (child.tagName === 'TABLE') {
        addTable(child as HTMLTableElement)
        continue
      }
      const copy = clone(child)
      insert(copy)
      if (fits()) continue
      copy.remove()
      if (hasContent()) nextPage()
      insert(copy)
      if (fits()) continue
      copy.remove()
      if (child.querySelector('.changes-content')) splitTextBlock(child, '.changes-content')
      else throw new Error('内容块无法放入A4，请减小字号')
    }
    return pages.map((item) => item.outerHTML)
  } finally {
    host.remove()
  }
}
