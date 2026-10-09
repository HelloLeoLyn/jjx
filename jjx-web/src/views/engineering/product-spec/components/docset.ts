import { getFullProduct } from '@/api/product'
import { attachmentApi } from '@/api/system/attachment'
import { engineeringResourceApi } from '@/api/engineering/resource'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { outboundApi } from '@/api/inventory/outbound'
import request from '@/utils/request'
import { ProcessCategoryEnum } from '@/enums/product'

export const documentSections = [
  { key: 'customer', label: '客供资料', note: '客供稿 · 客户确认样品' },
  { key: 'spec', label: '产品作业规范', note: '材料 · 流程 · 结构图' },
  { key: 'print', label: '印刷规范', note: '印序 · 色号 · 油墨 · 网版' },
  { key: 'atlas', label: '产品图集', note: '工程图纸 · 印刷指导图' },
  { key: 'color', label: '分色检查表', note: '已上传的检查表' },
  { key: 'sample', label: '样品', note: '样品实物照片' },
  { key: 'pick', label: '打样领料单', note: '已有领料单汇总' },
]

export interface DocFile {
  key: string
  id: number
  name: string
  category: string
  section: string
  kind: 'image' | 'pdf' | 'other'
}
export interface DocsetData {
  product: Record<string, any>
  bom: Record<string, any>
  routing: Record<string, any>
  dies: Record<string, any>[]
  files: DocFile[]
  picks: Record<string, any>[]
  warnings: string[]
}
export interface FileImage { url: string; width: number; height: number }
export interface DocPage {
  key: string
  section: string
  title: string
  kind: 'spec' | 'print' | 'image' | 'empty' | 'pick' | 'flow'
  rows?: Record<string, any>[]
  groups?: { label: string; symbol: string; rows: Record<string, any>[] }[]
  image?: FileImage
  file?: DocFile
  continuation?: number
}

export function plain(value: unknown): string {
  return String(value ?? '').replace(/<[^>]*>/g, '').trim()
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
  const result = await getFullProduct(productId)
  const full = result.data
  if (!full?.product) throw new Error('未找到产品资料')
  const data: DocsetData = { product: full.product, bom: full.bom || {}, routing: full.routing || {}, dies: [], files: [], picks: [], warnings: [] }
  const jobs = [
    { label: '刀模资料', run: async () => { const r: any = await engineeringResourceApi.byProduct('DIE', productId); data.dies = r.data || [] } },
    { label: '客供资料', run: async () => { const r: any = await attachmentApi.customerDocs(productId); appendFiles(data, r.data || [], true) } },
    { label: '产品文件', run: async () => { const r: any = await attachmentApi.productFiles(data.product.productCode); appendFiles(data, r.data || [], false) } },
    { label: '打样领料单', run: async () => {
      const r: any = await sampleOrderApi.page({ productId, productCode: data.product.productCode, pageNum: 1, pageSize: 100 } as any)
      const orders = (r.data?.records || []).filter((row: any) => Number(row.productId) === productId || row.productCode === data.product.productCode)
      const ids = new Set(orders.map((row: any) => Number(row.sampleOrderId)))
      if (!ids.size) return
      const ob: any = await outboundApi.list({ sourceType: 'sample', pageNum: 1, pageSize: 200 } as any)
      data.picks = (ob.data?.records || []).filter((row: any) => ids.has(Number(row.sourceId)))
    } },
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
    data.files.push({ key: String(id), id, name, category, kind, section })
  }
}

// 使用仓库已有 pdfjs；将 PDF 各页与图片统一成纸张内容，预览、打印共用。
export async function renderFile(file: DocFile): Promise<FileImage[]> {
  const blob = await request.get<Blob>(`/system/attachment/download/${file.id}`, { responseType: 'blob' })
  if (!blob.size) throw new Error('文件内容为空')
  if (blob.type.includes('json')) throw new Error('文件下载失败，请重新加载')
  if (file.kind === 'image') {
    const url = URL.createObjectURL(blob)
    try {
      const img = new Image()
      img.src = url
      await img.decode()
      return [{ url, width: img.naturalWidth, height: img.naturalHeight }]
    } catch (error) { URL.revokeObjectURL(url); throw error }
  }
  const pdfjs = await import('pdfjs-dist')
  pdfjs.GlobalWorkerOptions.workerSrc = new URL('pdfjs-dist/build/pdf.worker.min.mjs', import.meta.url).toString()
  const task = pdfjs.getDocument({ data: await blob.arrayBuffer() })
  const images: FileImage[] = []
  try {
    const pdf = await task.promise
    for (let i = 1; i <= pdf.numPages; i++) {
      const page = await pdf.getPage(i)
      const viewport = page.getViewport({ scale: 2 })
      const canvas = document.createElement('canvas')
      canvas.width = Math.ceil(viewport.width)
      canvas.height = Math.ceil(viewport.height)
      const context = canvas.getContext('2d')
      if (!context) throw new Error('无法显示 PDF 页面')
      await page.render({ canvas, canvasContext: context, viewport }).promise
      const pageBlob = await new Promise<Blob>((resolve, reject) => canvas.toBlob((value) => value ? resolve(value) : reject(new Error('PDF 页面转换失败')), 'image/png'))
      images.push({ url: URL.createObjectURL(pageBlob), width: canvas.width, height: canvas.height })
      canvas.width = canvas.height = 0
      page.cleanup()
    }
    return images
  } catch (error) {
    images.forEach((image) => URL.revokeObjectURL(image.url))
    throw error
  } finally { await task.destroy() }
}

export function makePages(data: DocsetData, sections: string[], fileIds: string[], images: Record<string, FileImage[]>): DocPage[] {
  const pages: DocPage[] = []
  const all = flatten(data.routing.items || [])
  const printRows = all.filter((row) => row.majorCategory === 'PRINT')
  const assembly = (data.routing.items || []).filter((row: any) => row.majorCategory !== 'PRINT')
  for (const section of documentSections.filter((item) => sections.includes(item.key))) {
    if (section.key === 'spec') {
      const bom = flatten(data.bom.items || [])
      const groups = flowGroups.map((group) => ({ ...group, rows: assembly.filter((row) => row.processCategory === group.value) }))
      const capacities = [14, 6, 14]
      const count = Math.max(1, Math.ceil(bom.length / 14), ...groups.map((group, i) => Math.ceil(group.rows.length / capacities[i])))
      for (let i = 0; i < count; i++) pages.push({ key: `spec-${i}`, section: 'spec', title: '产品作业规范', kind: 'spec', continuation: i, rows: bom.slice(i * 14, (i + 1) * 14), groups: groups.map((group, j) => ({ label: group.label, symbol: group.symbol, rows: group.rows.slice(i * capacities[j], (i + 1) * capacities[j]) })) })
      const uncategorized = assembly.filter((row) => !flowGroups.some((group) => group.value === row.processCategory))
      for (let i = 0; i < uncategorized.length; i += 22) pages.push({ key: `flow-${i}`, section: 'spec', title: '产品作业规范 · 未分类工序', kind: 'flow', rows: uncategorized.slice(i, i + 22) })
    } else if (section.key === 'print') {
      const groups = [...flowGroups, { value: '', label: '未分类', symbol: '' }].map((group) => ({ ...group, rows: printRows.filter((row) => group.value ? row.processCategory === group.value : !flowGroups.some((g) => g.value === row.processCategory)) }))
      const count = Math.max(1, ...groups.map((group, j) => Math.ceil(group.rows.length / printCapacities[j])))
      for (let i = 0; i < count; i++) pages.push({ key: `print-${i}`, section: 'print', title: '印刷规范', kind: 'print', continuation: i, groups: groups.filter((group) => group.value || group.rows.length).map((group, j) => ({ label: group.label, symbol: group.symbol, rows: group.rows.slice(i * printCapacities[j], (i + 1) * printCapacities[j]) })) })
    } else if (section.key === 'pick') {
      for (let i = 0; i < Math.max(1, data.picks.length); i += 22) pages.push({ key: `pick-${i}`, section: 'pick', title: '打样领料单汇总', kind: 'pick', rows: data.picks.slice(i, i + 22) })
    } else {
      const files = data.files.filter((file) => file.section === section.key && fileIds.includes(file.key))
      for (const file of files) (images[file.key] || []).forEach((image, i) => pages.push({ key: `file-${file.key}-${i}`, section: section.key, title: `${section.label} · ${file.category}`, kind: 'image', file, image }))
      if (!files.length) pages.push({ key: `${section.key}-empty`, section: section.key, title: section.label, kind: 'empty' })
    }
  }
  return pages
}
