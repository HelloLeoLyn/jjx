/** 生成前领料预览的显示模型；数量计算和替代料分配仍以服务端为准。 */
export interface PickPreviewRow {
  materialCode?: string
  materialName?: string
  specification?: string
  processName?: string
  unit?: string
  moduleQty?: number | string | null
  baseQty?: number | string | null
  qtyPick?: number
  qtyNeeded?: number
  available?: number
  substitute?: boolean
  substituteOf?: string
}

export interface PickPreviewPrintData {
  orderNo: string
  productCode?: string
  productName?: string
  plannedQuantity?: number
  supplementQuantity?: number
  rows: PickPreviewRow[]
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
  const rows = data.rows
    .map(
      (row, index) => `<tr>
    <td>${index + 1}</td>
    <td>${escapeHtml(row.materialCode || '-')}<br>${escapeHtml(row.materialName || '-')}</td>
    <td>${escapeHtml(row.processName || '-')}</td>
    <td>${escapeHtml(row.specification || '-')}</td>
    <td>${escapeHtml(row.unit || '-')}</td>
    <td>${escapeHtml(formatPickRatio(row))}</td>
    <td>${formatNumber(row.qtyNeeded)}</td>
    <td>${row.substitute ? '自动补足' : formatNumber(row.qtyPick)}</td>
    <td>${formatNumber(row.available)}</td>
    <td>${row.substitute ? `替代 ${escapeHtml(row.substituteOf || '-')}` : ''}</td>
  </tr>`
    )
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
  <table><colgroup>
    <col style="width:5%"><col style="width:20%"><col style="width:9%"><col style="width:12%"><col style="width:6%">
    <col style="width:10%"><col style="width:9%"><col style="width:10%"><col style="width:9%"><col style="width:10%">
  </colgroup><thead><tr>
    <th>项次</th><th>物料编码/原料品名</th><th>项目</th><th>规格</th><th>单位</th>
    <th>模数/基数</th><th>需求数量</th><th>拟领数量</th><th>可用数量</th><th>备注</th>
  </tr></thead><tbody>${rows || '<tr><td colspan="10">暂无领料明细</td></tr>'}</tbody></table>
  <footer><span>主管：</span><span>核准：</span><span>领料人：</span><span>仓管员：</span><span>制表：</span></footer>
</main></body></html>`
}
