/**
 * 产品作业规范「已发布版本」文档集的纯组装逻辑（dev-20261013-009）。
 *
 * 本模块**零外部依赖**，便于用 `node --test` 直接做回归（见 `__tests__/versionDocset.test.ts`）；
 * 网络读取（版本详情 / BOM / 工艺路线）仍由 `docset.ts` 负责。
 * `DocFile` 是本模块单一来源，`docset.ts` 复用并再导出，避免两处定义漂移。
 */

/** 文档集内的一个文件；工作态（loadDocset）与发布版本（loadDocsetForVersion）共用。 */
export interface DocFile {
  key: string
  id: number
  name: string
  originalName?: string
  category: string
  /** 二级资料类型编码（dev-20261019-018）；旧数据可能为空。 */
  documentType?: string
  section: string
  kind: 'image' | 'pdf' | 'other'
  productFile: boolean
  drawingNo?: string
  version?: string
  fileRole?: string
  isCurrent?: number
  released?: number
  /**
   * 来自已发布版本清单（dev-20261013-009）。
   * 发布时未记录「现行 / 下发」快照，故这类文件不受当前工作态的现行/下发过滤与标签影响，
   * 整批按发布清单默认入集展示。
   */
  published?: boolean
  /** 定版固化的打印选择；false仍可下载，不能临时勾选打印。 */
  printSelected?: boolean
  draftManaged?: boolean
}

export interface VersionFilesResult {
  files: DocFile[]
  warnings: string[]
}

/**
 * 解析发布条目快照。
 * 空值（null / undefined / 空串）视为「原本为空」；非空但非法 JSON 显式标错，绝不静默当成空对象。
 */
export function parseSnapshotStrict(value?: string | null): {
  ok: boolean
  value: Record<string, any>
} {
  if (value == null || value === '') return { ok: true, value: {} }
  try {
    const parsed = JSON.parse(value)
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed))
      return { ok: true, value: parsed }
    return { ok: false, value: {} }
  } catch {
    return { ok: false, value: {} }
  }
}

/** 由文件类别判定文档栏目（legacy：仅无 documentType 的历史非图纸数据回退用）。 */
export function sectionForCategory(customer: boolean, category: string): string {
  if (customer || ['客供稿', '承认书', '客供资料', '客户确认样品'].includes(category))
    return 'customer'
  if (category === '印刷指导图') return 'print'
  if (category === '样品照片') return 'sample'
  if (category === '分色检查表') return 'color'
  return 'atlas'
}

/** 工程图类二级类型（除下方显式映射外，其余归工程图集）。 */
export const ENGINEERING_DOCUMENT_TYPES = new Set([
  'ENGINEERING_DRAWING',
  'STRUCTURE_DRAWING',
  'OUTLINE_DRAWING',
  'PANEL_DRAWING',
  'CIRCUIT_DRAWING',
  'ASSEMBLY_DRAWING',
  'PACKAGING_DRAWING',
  'MOLD_DRAWING',
  'DIE_DRAWING',
  'DIE_PARAMETER_DRAWING',
  'CONFIRM_DRAWING',
  'FILM',
])

/**
 * 文档集分栏（dev-20261019-018）：优先按二级资料类型编码映射；
 * 无类型的历史数据仅对非图纸栏目按原类别回退，工程图不默认落图集（归 other/未分类）。
 */
export function sectionForDocumentType(
  documentType?: string | null,
  category?: string | null
): string {
  const type = (documentType || '').trim()
  if (type) {
    if (type.startsWith('CUSTOMER_')) return 'customer'
    if (type === 'PRODUCT_SPECIFICATION' || type === 'WORK_INSTRUCTION') return 'spec'
    if (type === 'PRINT_GUIDE') return 'print'
    if (type === 'COLOR_CHECK') return 'color'
    if (ENGINEERING_DOCUMENT_TYPES.has(type)) return 'atlas'
    return 'other'
  }
  const cat = (category || '').trim()
  if (['客供稿', '承认书', '客供资料', '客户确认样品'].includes(cat)) return 'customer'
  if (cat === '样品照片') return 'sample'
  if (cat === '印刷指导图') return 'print'
  if (cat === '分色检查表') return 'color'
  return 'other'
}

/** 按文件名 / MIME 判定文件形态。 */
export function detectFileKind(name: string, mime = ''): 'image' | 'pdf' | 'other' {
  const ext = (name.split('.').pop() || '').toLowerCase()
  if (
    mime.startsWith('image/') ||
    ['png', 'jpg', 'jpeg', 'webp', 'gif', 'bmp', 'svg'].includes(ext)
  )
    return 'image'
  if (mime.includes('pdf') || ext === 'pdf') return 'pdf'
  return 'other'
}

/** 快照文件条目 → 文档集文件（冻结元数据；published=true 表示来自发布清单，dev-20261013-011）。 */
export function snapshotFileToDocFile(file: Record<string, any>): DocFile {
  const originalName = file.fileName || '未命名文件'
  const name = file.displayName || originalName
  const documentType = file.documentType ? String(file.documentType) : ''
  // 新快照按 documentType 分栏；旧快照无该字段时沿用已固化的 section（兼容读取）。
  const section = documentType
    ? sectionForDocumentType(documentType, file.category)
    : file.section || 'other'
  return {
    key: String(file.attachmentId),
    id: Number(file.attachmentId),
    name,
    originalName,
    category: file.category || '',
    documentType: documentType || undefined,
    section,
    kind: detectFileKind(originalName, file.mime || ''),
    productFile: section !== 'customer',
    version: file.version ? String(file.version) : undefined,
    drawingNo: file.drawingNo ? String(file.drawingNo) : undefined,
    fileRole: file.fileRole ? String(file.fileRole) : undefined,
    published: true,
    printSelected: typeof file.printSelected === 'boolean' ? file.printSelected : undefined,
  }
}

/** 完整快照缺件属于读取失败，不能降级成空白客供页或回查工作态。 */
export function parseDocsetSnapshot(value?: string | null): Record<string, any> {
  const parsed = parseSnapshotStrict(value)
  const snapshot = parsed.value
  if (
    !parsed.ok ||
    snapshot.schemaVersion !== 1 ||
    !snapshot.spec ||
    typeof snapshot.spec !== 'object' ||
    Array.isArray(snapshot.spec) ||
    !Array.isArray(snapshot.files)
  ) {
    throw new Error('该发布版本的完整快照损坏或格式不支持，无法预览')
  }
  if (
    !Number.isInteger(snapshot.counts?.files) ||
    snapshot.counts.files !== snapshot.files.length
  ) {
    throw new Error('该发布版本的文件清单数量不一致，无法预览')
  }
  const ids = new Set<number>()
  for (const file of snapshot.files) {
    const id = Number(file?.attachmentId)
    if (
      !Number.isSafeInteger(id) ||
      id <= 0 ||
      ids.has(id) ||
      typeof file?.fileName !== 'string' ||
      !file.fileName.trim() ||
      !['customer', 'sample', 'atlas', 'print', 'color', 'icon'].includes(file.section)
    ) {
      throw new Error('该发布版本的文件清单不完整或附件重复，无法预览')
    }
    ids.add(id)
  }
  return snapshot
}

/** 快照完整性提示（纯）：区分「未固化」与「计数不一致」；空 BOM/路线属原本为空，不算缺项。 */
export function snapshotWarnings(snapshot: Record<string, any>, fileCount: number): string[] {
  const warnings: string[] = []
  if (!snapshot.spec) warnings.push('该发布版本未固化规范正文，正文可能缺项')
  const declared = snapshot?.counts?.files
  if (typeof declared === 'number' && declared !== fileCount) {
    warnings.push(`该版本清单声明 ${declared} 个文件，实际固化 ${fileCount} 个，请核查`)
  }
  return warnings
}

/**
 * 由发布版本条目组装工程图文件集（dev-20261013-009）。
 * - 读取**全部** DRAWING 条目（不再只取首条）；
 * - 类别取 operationRef，其次快照 category；保留真实图种、版本与文件角色；
 * - 解析失败 / 缺附件标识 / 附件重复的条目显式记入 warnings 并跳过，不冒充空白；
 * - 清单为空时也给出明确告警，避免「静默无图」。
 */
export function buildVersionFiles(items: Record<string, any>[] = []): VersionFilesResult {
  const files: DocFile[] = []
  const warnings: string[] = []
  const drawings = items.filter((item) => item?.resourceType === 'DRAWING')
  if (!drawings.length) {
    warnings.push('该发布版本未包含工程图引用')
    return { files, warnings }
  }
  drawings.forEach((item, index) => {
    const ref = parseSnapshotStrict(item?.snapshotJson)
    if (!ref.ok) {
      warnings.push(`该版工程图引用第 ${index + 1} 条解析失败，未纳入预览`)
      return
    }
    const attId = Number(ref.value.attachmentId)
    if (!attId) {
      warnings.push(`该版工程图引用第 ${index + 1} 条缺少附件标识，未纳入预览`)
      return
    }
    if (files.some((file) => file.id === attId)) {
      warnings.push(`该版工程图引用第 ${index + 1} 条附件重复，已合并`)
      return
    }
    const name = ref.value.fileName || '工程图'
    const category = item.operationRef || ref.value.category || '其他工程图'
    const documentType = ref.value.documentType ? String(ref.value.documentType) : ''
    files.push({
      key: String(attId),
      id: attId,
      name,
      category,
      documentType: documentType || undefined,
      section: documentType
        ? sectionForDocumentType(documentType, category)
        : sectionForCategory(false, category),
      kind: detectFileKind(name, ref.value.fileType || ''),
      productFile: true,
      version: ref.value.version ? String(ref.value.version) : undefined,
      fileRole: ref.value.fileRole,
      published: true,
    })
  })
  return { files, warnings }
}
