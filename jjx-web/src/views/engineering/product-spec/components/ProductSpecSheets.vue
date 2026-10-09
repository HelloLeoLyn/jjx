<template>
  <div class="doc-sheets">
    <div v-for="(page, pageIndex) in pages" :key="page.key" :id="`doc-page-${pageIndex}`" class="sheet-slot">
      <div class="page-caption no-print"><span>{{ page.title }}</span><span>{{ pageIndex + 1 }} / {{ pages.length }}</span></div>
      <A4Canvas :padding-mm="9" :scale="1">
        <article class="paper-form">
          <header class="paper-heading">
            <h1>{{ page.title }}<small v-if="page.continuation">（续）</small></h1>
            <div class="paper-meta"><span>{{ data.product.productCode }}</span><span>{{ data.product.customerName || '' }}</span></div>
          </header>

          <template v-if="page.kind === 'spec'">
            <div class="material-area">
              <table class="paper-grid material-grid">
                <colgroup><col style="width: 8%" /><col style="width: 7%" /><col style="width: 30%" /><col style="width: 37%" /><col style="width: 18%" /></colgroup>
                <thead><tr><th>序号</th><th>项目</th><th>材　料</th><th>规格及模数</th><th>刀模位置</th></tr></thead>
                <tbody><tr v-for="(row, i) in padded(page.rows, 14)" :key="i">
                  <td class="center">{{ row ? (page.continuation || 0) * 14 + i + 1 : '' }}</td>
                  <td class="center"><SvgIcon v-if="row?.icon" :name="row.icon" :size="18" /><span v-else>{{ plain(row?.processName) }}</span></td>
                  <td>{{ row?.materialName || row?.materialCode }}</td>
                  <td>{{ materialSpec(row) }}</td>
                  <td>{{ row?.dieLocation || '' }}</td>
                </tr></tbody>
              </table>
              <div class="conditions">
                <div class="block-title">凹凸条件</div>
                <table class="paper-grid"><tbody><tr v-for="label in embossLabels" :key="label"><td>{{ label }}</td><td class="condition-value"></td></tr></tbody></table>
                <div class="die-locations"><strong>关联刀模 / 库位</strong><div v-for="die in data.dies" :key="die.resourceId">{{ die.resourceNo || die.die_no }}　{{ die.location || '未填写库位' }}</div><span v-if="!data.dies.length" class="blank-note">未关联刀模</span></div>
              </div>
            </div>

            <div class="flow-area">
              <div v-for="(group, groupIndex) in page.groups" :key="group.label" class="flow-column" :class="{ 'middle-flow': groupIndex === 1 }">
                <template v-if="groupIndex === 1">
                  <div class="block-title">产　品　结　构　图</div>
                  <div class="structure-image"><img v-if="structureImage" :src="structureImage" alt="产品结构图" /><span v-else class="blank-note">暂无产品结构图</span></div>
                  <div v-if="structureImage && structureCaption" class="structure-caption">{{ structureCaption }}</div>
                </template>
                <table class="paper-grid flow-grid">
                  <colgroup><col style="width: 13%" /><col style="width: 65%" /><col style="width: 22%" /></colgroup>
                  <thead><tr><th>序号</th><th>{{ group.label }}作业流程</th><th>工时(h)</th></tr></thead>
                  <tbody><tr v-for="(row, i) in padded(group.rows, groupIndex === 1 ? 6 : 14)" :key="i">
                    <td class="center">{{ row ? (page.continuation || 0) * (groupIndex === 1 ? 6 : 14) + i + 1 : '' }}</td>
                    <td><div v-if="row" class="flow-operation">
                      <template v-for="(operation, j) in operations(row)" :key="operation.itemId || j">
                        <span v-if="j" class="flow-plus">+</span>
                        <span class="operation-symbol"><SvgIcon v-if="operation.icon" :name="operation.icon" :size="21" /><span v-else>{{ plain(operation.processName) }}</span><sub v-if="operation.indexNumber">{{ operation.indexNumber }}</sub></span>
                      </template>
                      <span class="operation-note">{{ plain(row.workInstruction || row.description) }}</span>
                    </div></td>
                    <td class="center">{{ labor(row) }}</td>
                  </tr></tbody>
                  <tfoot><tr><td colspan="2">{{ group.label }}作业总耗时</td><td class="center">{{ totalLabor(group.rows) }}</td></tr></tfoot>
                </table>
              </div>
            </div>
            <div class="hours-total">本页合计人工工时（h）<span>{{ totalLabor((page.groups || []).flatMap(group => group.rows)) }}</span></div>
            <div class="issue-area"><div class="vertical-label">变更内容</div><div class="change-note">{{ plain(data.routing.remark || '') }}</div><div class="vertical-label">发行单位</div><div class="issue-blank"></div></div>
          </template>

          <template v-else-if="page.kind === 'print'">
            <div v-for="(group, groupIndex) in page.groups" :key="group.label" class="print-group">
              <table class="paper-grid print-grid">
                <colgroup><col style="width: 31%" /><col style="width: 17%" /><col style="width: 18%" /><col style="width: 18%" /><col style="width: 16%" /></colgroup>
                <thead><tr><th>{{ group.symbol }} {{ group.label }}印序</th><th>色　号</th><th>油墨放置区</th><th>网版放置区</th><th>备　注</th></tr></thead>
                <tbody><tr v-for="(row, i) in padded(group.rows, printCapacities[groupIndex])" :key="i">
                  <td><span v-if="row" class="print-number">{{ (page.continuation || 0) * printCapacities[groupIndex] + i + 1 }}.</span>{{ row ? plain(printParams(row).printName || row.processName) : '' }}</td>
                  <td>{{ row ? plain(printParams(row).colorNo) : '' }}</td>
                  <td>{{ row ? plain(printParams(row).inkNo) : '' }}</td>
                  <td>{{ row ? plain(printParams(row).screenNo) : '' }}</td>
                  <td>{{ row ? plain(row.remark || row.description) : '' }}</td>
                </tr></tbody>
              </table>
            </div>
            <div class="print-signoff"><span>备注</span><div></div><span>发行单位</span><div></div></div>
          </template>

          <template v-else-if="page.kind === 'image'">
            <div class="attachment-name">{{ page.file ? documentFileCaption(page.file) : '' }}</div>
            <div class="attachment-artwork"><img :src="page.image?.url" :alt="page.file?.name" /></div>
          </template>

          <template v-else-if="page.kind === 'pick' || page.kind === 'flow'">
            <table v-if="page.kind === 'pick'" class="paper-grid reference-grid"><thead><tr><th>领料单号</th><th>数量</th><th>状态</th><th>创建时间</th></tr></thead><tbody><tr v-for="row in page.rows" :key="row.outboundId"><td>{{ row.outboundNo }}</td><td>{{ row.totalQuantity }}</td><td>{{ outboundStatusText(row.orderStatus) }}</td><td>{{ row.createTime }}</td></tr></tbody></table>
            <table v-else class="paper-grid reference-grid"><thead><tr><th>顺序</th><th>工序名称</th><th>作业说明</th><th>人工工时（h）</th></tr></thead><tbody><tr v-for="(row, i) in page.rows" :key="row.itemId || i"><td>{{ row.processOrder }}</td><td>{{ plain(row.processName) }}</td><td>{{ plain(row.workInstruction || row.description) }}</td><td>{{ labor(row) }}</td></tr></tbody></table>
            <div v-if="!page.rows?.length" class="empty-paper">暂无{{ page.kind === 'pick' ? '打样领料单' : '工序记录' }}</div>
          </template>
          <div v-else class="empty-paper"><div class="empty-paper-frame"><span>{{ page.title }}</span><p>暂无已选文件</p></div></div>

          <footer class="paper-footer"><span>名称：{{ data.product.productName }}</span><span>编号：{{ data.product.productCode }}</span><span v-if="data.routing.routingVersion">路线：{{ data.routing.routingVersion }}</span><span>{{ pageIndex + 1 }} / {{ pages.length }} 页</span></footer>
        </article>
      </A4Canvas>
    </div>
  </div>
</template>

<script setup lang="ts">
import A4Canvas from '@/components/A4Canvas/index.vue'
import SvgIcon from '@/components/SvgIcon/index.vue'
import { InboundOrderStatusEnum } from '@/enums/inventory'
import { plain, printParams, printCapacities, documentFileCaption, type DocsetData, type DocPage } from './docset'

defineProps<{ data: DocsetData; pages: DocPage[]; structureImage?: string; structureCaption?: string }>()
const embossLabels = ['凹凸调机高度', '凹凸要求高度', '凹凸上模温度', '凹凸下模温度', '凹凸下压时间', '凹凸保持时间']
function padded(rows: Record<string, any>[] = [], size = 10): (Record<string, any> | null)[] { return Array.from({ length: Math.max(size, rows.length) }, (_, i) => rows[i] || null) }
function operations(row: Record<string, any>) { return row.children?.length ? row.children : [row] }
function outboundStatusText(value: unknown): string {
  const numeric = Number(value)
  return value == null ? '' : InboundOrderStatusEnum.canDo(numeric) ? InboundOrderStatusEnum.getLabel(numeric) : String(value)
}
function labor(row?: Record<string, any> | null): string {
  if (!row) return ''
  if (row.children?.length) return totalLabor(row.children)
  const value = row.customLaborHours ?? row.standardLaborHours
  return value == null ? '' : String(Number(value))
}
function totalLabor(rows: Record<string, any>[]): string {
  if (!rows.some((row) => labor(row) !== '')) return ''
  return String(Number(rows.reduce((sum, row) => sum + Number(labor(row) || 0), 0).toFixed(2)))
}
function materialSpec(row?: Record<string, any> | null): string {
  if (!row) return ''
  const specification = row.specification || (row.widthMm && row.lengthMm ? `${row.widthMm}×${row.lengthMm}mm` : '')
  return [specification, row.moduleQty != null ? `${row.moduleQty} PCS` : '', row.quantity != null ? `用量 ${row.quantity}${row.unit || ''}` : ''].filter(Boolean).join(' / ')
}
</script>

<style scoped>
.doc-sheets { display:flex; flex-direction:column; align-items:center; gap:24px; }
.sheet-slot { width:794px; scroll-margin-top:24px; }
.page-caption { display:flex; justify-content:space-between; color:#798493; font-size:12px; padding:0 2px 8px; }
.paper-form { min-height:1053px; box-sizing:border-box; display:flex; flex-direction:column; border:1.4px solid #262626; color:#252525; font-family:'SimSun','Songti SC',serif; }
.paper-heading { padding:12px 16px 8px; border-bottom:1.4px solid #262626; }
.paper-heading h1 { margin:0; font-weight:700; font-size:27px; letter-spacing:6px; text-align:center; line-height:1.6; }
.paper-heading small { font-size:15px; letter-spacing:1px; }
.paper-meta { display:flex; justify-content:space-between; margin-top:2px; font-size:11px; color:#555; }
.paper-grid { width:100%; border-collapse:collapse; table-layout:fixed; font-size:12px; }
.paper-grid th,.paper-grid td { border:1px solid #343434; padding:3px 5px; line-height:1.4; overflow-wrap:anywhere; }
.paper-grid th { font-weight:500; font-size:13px; height:29px; }
.paper-grid td { height:23px; box-sizing:border-box; }
.center { text-align:center; }
.material-area { display:flex; }
.material-grid { width:70%; }
.conditions { width:30%; border-left:1px solid #333; }
.block-title { height:30px; text-align:center; line-height:30px; border-bottom:1px solid #333; font-size:14px; }
.conditions .paper-grid td { height:27px; font-size:11px; padding:3px; }
.condition-value { width:31%; }
.die-locations { padding:10px; line-height:1.8; font-size:11px; }
.die-locations strong { display:block; font-weight:500; margin-bottom:5px; }
.blank-note { color:#a0a0a0; font-size:11px; }
.flow-area { display:flex; border-top:1.4px solid #333; }
.flow-column { width:30%; }
.middle-flow { width:40%; border-left:1px solid #333; border-right:1px solid #333; }
.flow-grid th { font-size:12px; padding:3px 2px; }
.flow-grid td { height:24px; padding:2px 3px; }
.flow-grid tfoot td { height:29px; font-size:11px; }
.flow-operation { display:flex; align-items:center; flex-wrap:wrap; gap:3px; min-height:20px; }
.operation-symbol { display:inline-flex; align-items:center; font-size:11px; position:relative; }
.operation-symbol sub { font-size:9px; }
.operation-note { font-size:10px; }
.flow-plus { font-family:Arial,sans-serif; }
.structure-image { height:174px; display:flex; align-items:center; justify-content:center; padding:10px; box-sizing:border-box; }
.structure-caption { height:12px; padding:0 6px; font-size:8px; line-height:12px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; box-sizing:border-box; }
.structure-image img { width:100%; height:100%; object-fit:contain; }
.hours-total { min-height:30px; border-top:1px solid #333; border-bottom:1px solid #333; padding:5px 10px; display:flex; gap:24px; align-items:center; box-sizing:border-box; }
.issue-area { display:flex; min-height:90px; flex:1; }
.vertical-label { width:28px; display:flex; align-items:center; justify-content:center; writing-mode:vertical-rl; letter-spacing:7px; border-right:1px solid #333; padding:6px 0; }
.change-note { flex:1; padding:9px; border-right:1px solid #333; font-size:11px; }
.issue-blank { width:130px; }
.print-group .paper-grid th { height:34px; font-size:14px; }
.print-grid td { height:25px; }
.print-number { font-size:10px; margin-right:4px; color:#666; }
.print-signoff { flex:1; display:grid; grid-template-columns:28px 1fr 28px 130px; min-height:90px; }
.print-signoff span { writing-mode:vertical-rl; text-align:center; letter-spacing:5px; padding:8px; border-right:1px solid #333; }
.print-signoff div:first-of-type { border-right:1px solid #333; }
.attachment-name { padding:8px 12px; font-size:11px; color:#555; overflow-wrap:anywhere; }
.attachment-artwork { flex:1; height:870px; min-height:0; padding:10px 14px; display:flex; align-items:center; justify-content:center; box-sizing:border-box; }
.attachment-artwork img { width:100%; height:100%; object-fit:contain; }
.reference-grid th { height:35px; }.reference-grid td { height:32px; }
.empty-paper { flex:1; display:flex; align-items:center; justify-content:center; min-height:700px; color:#a0a0a0; }
.empty-paper-frame { border:1px dashed #c8c8c8; padding:48px 90px; text-align:center; font-family:'Microsoft YaHei',sans-serif; }
.empty-paper-frame span { font-size:17px; }.empty-paper-frame p { font-size:12px; margin-top:15px; }
.paper-footer { margin-top:auto; display:flex; gap:10px; align-items:center; justify-content:space-between; border-top:1.3px solid #333; padding:7px 8px; font-size:11px; }
.paper-footer span { overflow-wrap:anywhere; }.paper-footer span:last-child { flex-shrink:0; }
@media print {
  .no-print { display:none !important; }
  .doc-sheets { display:block; }
  .sheet-slot { width:210mm; margin:0; break-after:page; page-break-after:always; }
  .sheet-slot:last-child { break-after:auto; page-break-after:auto; }
  .paper-form { min-height:279mm; }
  .sheet-slot :deep(.a4-canvas) { min-height:297mm; }
}
</style>
