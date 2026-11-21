/** 生成前领料预览的显示模型；数量计算和替代料分配仍以服务端为准。 */
export interface PickPreviewRow {
  materialId?: number
  bomItemId?: number
  materialCode?: string
  materialName?: string
  specification?: string
  processName?: string
  unit?: string
  moduleQty?: number | string | null
  baseQty?: number | string | null
  qtyPick?: number
  qtyPickMax?: number
  qtyNeeded?: number
  available?: number
  substitute?: boolean
  substituteOf?: string
  insufficient?: boolean
  isBomDetail?: boolean
  depth?: number
  sequence?: string
  detailNote?: string
}

export interface PickPreviewPrintData {
  orderNo: string
  productCode?: string
  productName?: string
  plannedQuantity?: number
  supplementQuantity?: number
  rows: PickPreviewRow[]
  visibleColumns?: string[]
  detailWarning?: string
}

export const PICK_PRINT_COLUMNS = [
  { key: 'sequence', label: '项次', width: 5 },
  { key: 'material', label: '物料编码/原料品名', width: 20 },
  { key: 'project', label: '项目', width: 9 },
  { key: 'specification', label: '规格', width: 12 },
  { key: 'unit', label: '单位', width: 6 },
  { key: 'ratio', label: '模数/基数', width: 10 },
  { key: 'needed', label: '需求数量', width: 9 },
  { key: 'picked', label: '拟领数量', width: 10 },
  { key: 'available', label: '可用数量', width: 9 },
  { key: 'remark', label: '备注', width: 10 },
] as const

export const PICK_PRINT_STORAGE_KEY = 'jjx.pickPreview.printColumns'
export const allPickPrintColumns = () => PICK_PRINT_COLUMNS.map((column) => column.key)

export function normalizePickPrintColumns(value: unknown): string[] {
  return Array.isArray(value)
    ? PICK_PRINT_COLUMNS.filter((column) => value.includes(column.key)).map((column) => column.key)
    : allPickPrintColumns()
}

export function readPickPrintColumns(): string[] {
  try {
    const value = localStorage.getItem(PICK_PRINT_STORAGE_KEY)
    return value === null ? allPickPrintColumns() : normalizePickPrintColumns(JSON.parse(value))
  } catch {
    return allPickPrintColumns()
  }
}

function formatNumber(value: unknown): string {
  if (value === null || value === undefined || value === '') return '-'
  const number = Number(value)
  return Number.isFinite(number)
    ? number.toLocaleString('zh-CN', { maximumFractionDigits: 4 })
    : '-'
}

export function formatPickRatio(row: PickPreviewRow): string {
  return `${formatNumber(row.moduleQty)} / ${formatNumber(row.baseQty)}`
}

function escapeHtml(value: unknown): string {
  return String(value ?? '').replace(
    /[&<>"']/g,
    (char) =>
      ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;',
      })[char]!
  )
}

/** 独立 iframe 中预览并打印同一份 HTML，避免混入后台页面和编辑控件。 */
export function buildPickPreviewHtml(data: PickPreviewPrintData): string {
  const keys = normalizePickPrintColumns(data.visibleColumns)
  const columns = PICK_PRINT_COLUMNS.filter((column) => keys.includes(column.key))
  const totalWidth = columns.reduce((sum, column) => sum + column.width, 0)
  const rows = data.rows
    .map((row, index) => {
      const depth = Math.max(0, Math.min(32, Math.floor(Number(row.depth) || 0)))
      const cells: Record<string, string> = {
        sequence: escapeHtml(row.sequence || index + 1),
        material: `<div style="padding-left:${depth * 10}px;text-align:left">${depth ? '↳ ' : ''}${escapeHtml(row.materialCode || '-')}<br>${escapeHtml(row.materialName || '-')}</div>`,
        project: escapeHtml(row.processName || '-'),
        specification: escapeHtml(row.specification || '-'),
        unit: escapeHtml(row.unit || '-'),
        ratio: escapeHtml(formatPickRatio(row)),
        needed: formatNumber(row.qtyNeeded),
        picked: row.isBomDetail ? '-' : row.substitute ? '自动补足' : formatNumber(row.qtyPick),
        available: formatNumber(row.available),
        remark: [
          row.isBomDetail ? '制造BOM明细' : '',
          row.substitute ? `替代 ${escapeHtml(row.substituteOf || '-')}` : '',
          escapeHtml(row.detailNote),
        ]
          .filter(Boolean)
          .join('；'),
      }
      return `<tr data-depth="${depth}">${columns.map((column) => `<td>${cells[column.key]}</td>`).join('')}</tr>`
    })
    .join('')
  return `<!DOCTYPE html>
<html lang="zh-CN"><head><meta charset="utf-8"><title>领料单预览 ${escapeHtml(data.orderNo)}</title>
<style>
  @page { size: A4 portrait; margin: 15mm; }
  * { box-sizing: border-box; }
  body { margin: 0; background: #eef0f3; color: #000; font: 11px SimSun, '宋体', serif; }
  .sheet { width: 210mm; min-height: 297mm; margin: 12px auto; padding: 15mm; background: #fff; box-shadow: 0 2px 12px #0002; }
  .company { text-align: center; font-size: 20px; font-weight: bold; letter-spacing: 2px; }
  h1 { margin: 5px 0 8px; text-align: center; font: bold 20px SimHei, '黑体', sans-serif; letter-spacing: 6px; }
  .meta { display: grid; grid-template-columns: 1.25fr 1fr; line-height: 24px; overflow-wrap: anywhere; }
  .draft { margin: 6px 0 10px; text-align: center; }
  table { width: 100%; border-collapse: collapse; table-layout: fixed; }
  thead { display: table-header-group; }
  tr { break-inside: avoid; }
  th, td { height: 25px; padding: 3px; border: 1px solid #000; text-align: center; overflow-wrap: anywhere; }
  footer { display: flex; justify-content: space-between; margin-top: 14px; font-size: 12px; break-inside: avoid; }
  @media print { body { background: #fff; } .sheet { width: 100%; min-height: 0; margin: 0; padding: 0; box-shadow: none; } }
</style></head><body><main class="sheet">
  <header class="company">深圳市精捷信科技有限公司</header>
  <h1>领 料 单（预览）</h1>
  <div class="meta">
    <div>工单号：${escapeHtml(data.orderNo)}</div><div>计划数量：${formatNumber(data.plannedQuantity)}</div>
    <div>产品：${escapeHtml(data.productName || '-')}</div><div>产品编码：${escapeHtml(data.productCode || '-')}</div>
    ${data.supplementQuantity !== undefined ? `<div>补产数量：${formatNumber(data.supplementQuantity)}</div>` : ''}
  </div>
  <p class="draft">生成前预览 · 未生成单据 · 未发料；替代料数量以确认生成后的单据为准</p>
  ${data.detailWarning ? `<p>${escapeHtml(data.detailWarning)}</p>` : ''}
  ${columns.length ? `<table><colgroup>${columns.map((column) => `<col style="width:${((column.width / totalWidth) * 100).toFixed(4)}%">`).join('')}</colgroup><thead><tr>${columns.map((column) => `<th>${column.label}</th>`).join('')}</tr></thead><tbody>${rows || `<tr><td colspan="${columns.length}">暂无领料明细</td></tr>`}</tbody></table>` : '<p>请选择至少一列打印内容</p>'}
  <footer><span>主管：</span><span>核准：</span><span>领料人：</span><span>仓管员：</span><span>制表：</span></footer>
</main></body></html>`
}
