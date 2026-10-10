<template>
  <article class="work-paper">
    <header class="work-title">产 品 作 业 规 范<span v-if="page.continuation">（续）</span></header>
    <table class="grid materials">
      <!-- 两张整宽表共用1/3边界：材料右边线与面板耗时右边线贯通。 -->
      <colgroup>
        <col style="width:5%" /><col style="width:5%" /><col style="width:calc(100% / 3 - 10%)" />
        <col style="width:calc(60% - 100% / 3)" /><col style="width:13%" />
        <col style="width:16.74%" /><col style="width:10.26%" />
      </colgroup>
      <thead><tr><th>序号</th><th>项目</th><th>材　料</th><th>规格及模数</th><th>刀模位置</th><th>凹凸条件</th><th></th></tr></thead>
      <tbody>
        <tr v-for="(row, i) in padded(page.rows, 14)" :key="i">
          <td class="center">{{ '(' + ((page.continuation || 0) * 14 + i + 1) + ')' }}</td>
          <td class="center"><SvgIcon v-if="row?.icon" :name="row.icon" :size="18" /><span v-else class="cell-single" :title="plain(row?.processName)">{{ plain(row?.processName) }}</span></td>
          <td><span class="material-name" :title="row?.materialName || row?.materialCode">{{ row?.materialName || row?.materialCode }}</span></td>
          <td><span class="cell-single" :title="workSpecMaterialSpec(row)">{{ workSpecMaterialSpec(row) }}</span></td>
          <td class="die-position">{{ page.diePositionRows?.[i] }}</td>
          <template v-if="i < embossFields.length">
            <td class="emboss-label">{{ embossFields[i]?.label }}</td>
            <td class="center emboss-value">{{ embossValue(embossFields[i]?.key, embossFields[i]?.unit) }}</td>
          </template>
          <td v-else-if="i === embossFields.length" :rowspan="14 - embossFields.length" colspan="2" class="engineering-notes" :style="{ color: data.workSpec.requirementsColor }">{{ page.engineeringNotes }}</td>
        </tr>
      </tbody>
    </table>
    <!-- 三组流程使用同一表格行，结构图跨7行，上线表头占第8行。 -->
    <table class="grid flows">
      <colgroup>
        <template v-for="group in page.groups" :key="group.label">
          <col style="width:5%" /><col style="width:21%" /><col style="width:calc(100% / 3 - 26%)" />
        </template>
      </colgroup>
      <thead><tr>
        <template v-for="(group, column) in page.groups" :key="group.label">
          <th v-if="column === 1" colspan="3" class="structure-title">产 品 结 构 图</th>
          <template v-else><th>序号</th><th>{{ group.label }}作业流程</th><th>耗时(h)</th></template>
        </template>
      </tr></thead>
      <tbody>
        <tr v-for="line in flowRows" :key="line.index">
          <template v-for="cell in line.cells" :key="cell.column">
            <template v-if="cell.column !== 1 || line.index >= 8">
              <td class="center">{{ '(' + cell.number + ')' }}</td>
              <td>
                <div v-if="cell.item" class="operation">
                  <div class="operation-symbols">
                    <template v-for="(item, j) in operations(cell.item)" :key="item.itemId || j">
                      <span v-if="j" class="plus">＋</span>
                      <span class="symbol">
                        <SvgIcon v-if="item.icon" :name="item.icon" :size="20" />
                        <sub v-if="workSpecStepSubscript(item)">{{ workSpecStepSubscript(item) }}</sub>
                      </span>
                    </template>
                  </div>
                  <span v-if="workSpecOperationRemark(cell.item)" class="operation-remark" :title="workSpecOperationRemark(cell.item)">{{ workSpecOperationRemark(cell.item) }}</span>
                </div>
              </td>
              <td class="center">{{ labor(cell.item) }}</td>
            </template>
            <td v-else-if="line.index === 0" colspan="3" rowspan="7" class="structure">
              <div class="structure-art"><img v-if="structureImage" :src="structureImage" alt="产品结构图" /></div>
              <div v-if="structureCaption" class="structure-caption">{{ structureCaption }}</div>
            </td>
            <template v-else-if="line.index === 7"><th>序号</th><th>{{ cell.label }}作业流程</th><th>耗时(h)</th></template>
          </template>
        </tr>
      </tbody>
      <tfoot><tr>
        <template v-for="group in page.groups" :key="group.label"><td colspan="2">{{ group.label }}作业总耗时</td><td class="center">{{ totalLabor(fullGroup(group.label)) }}</td></template>
      </tr></tfoot>
    </table>
    <div class="hours"><span>合计工时</span><div>{{ totalLabor(allOperations) }}</div></div>
    <div class="issuance">
      <div class="vertical-label">变更内容</div>
      <div class="changes"><div v-for="(line, i) in page.changeLines" :key="i" :style="{ color: line.color }">{{ line.text }}</div></div>
      <div class="vertical-label">发行单位</div>
      <div class="issuer">
        <div v-if="data.workSpec.approved" class="approval"><div>{{ data.workSpec.issueUnit }}</div><div>{{ shortDate(data.workSpec.issueDate || data.workSpec.confirmedAt) }}</div><div>已批准</div></div>
        <span v-else>{{ data.workSpec.issueUnit }}</span>
      </div>
    </div>
    <footer><span>名称：{{ data.product.productName }}</span><span>编号：{{ data.product.productCode }}</span><span>发行日期：{{ shortDate(data.workSpec.issueDate) }}</span><span>{{ pageIndex + 1 }}／{{ pageCount }}页</span></footer>
  </article>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import SvgIcon from '@/components/SvgIcon/index.vue'
import { embossFields } from '@/api/product/workSpec'
import { flowGroups, plain, workSpecMaterialSpec, workSpecStepSubscript, workSpecOperationRemark, type DocsetData, type DocPage } from './docset'

const props = defineProps<{ data: DocsetData; page: DocPage; pageIndex: number; pageCount: number; structureImage?: string; structureCaption?: string }>()
const allOperations = computed<Record<string, any>[]>(() => props.data.routing.items || [])
const flowRows = computed(() => Array.from({ length: 14 }, (_, index) => ({
  index,
  cells: (props.page.groups || []).map((group, column) => {
    const rowIndex = column === 1 ? index - 8 : index
    return { column, label: group.label, number: (props.page.continuation || 0) * (column === 1 ? 6 : 14) + rowIndex + 1, item: group.rows[rowIndex] }
  }),
})))
function fullGroup(label: string) { const category = flowGroups.find(group => group.label === label)?.value; return allOperations.value.filter(row => row.processCategory === category) }
function padded(rows: Record<string, any>[] = [], length: number) { return Array.from({ length }, (_, i) => rows[i] || null) }
function operations(row: Record<string, any>) { return row.children?.length ? row.children : [row] }
function hourValue(row: Record<string, any>): number | null {
  if (row.children?.length) {
    const values = row.children.map(hourValue).filter((value: number | null) => value !== null)
    return values.length ? values.reduce((sum: number, value: number) => sum + value, 0) : null
  }
  const value = row.customLaborHours ?? row.standardLaborHours
  return value == null || value === '' ? null : Number(value)
}
function labor(row?: Record<string, any> | null) { const value = row ? hourValue(row) : null; return value == null ? '' : String(Number(value.toFixed(4))) }
function totalLabor(rows: Record<string, any>[]) {
  const values = rows.map(hourValue).filter((value): value is number => value !== null)
  return values.length ? String(Number(values.reduce((sum, value) => sum + value, 0).toFixed(4))) : ''
}
function embossValue(key?: string, unit?: string) { const value = key ? props.data.workSpec.emboss[key] : ''; return value == null || value === '' ? '' : value + ' ' + (unit || '') }
function shortDate(value?: string | null) { return value ? value.slice(2, 10) : '' }
</script>

<style scoped>
.work-paper { height:1098px; display:flex; flex-direction:column; box-sizing:border-box; border:1.5px solid #222; color:#252525; font-family:SimSun,'Songti SC',serif; font-size:13px; }
.work-title { height:65px; flex-shrink:0; display:flex; align-items:center; justify-content:center; font-size:27px; letter-spacing:5px; border-bottom:1px solid #222; text-decoration:underline; text-underline-offset:7px; }
.work-title span { font-size:13px; letter-spacing:0; }
.grid { width:100%; border-collapse:collapse; table-layout:fixed; flex-shrink:0; }
.grid th,.grid td { border:0; border-right:1px solid #333; border-bottom:1px solid #333; padding:2px 4px; line-height:1.2; box-sizing:border-box; font-weight:400; overflow-wrap:anywhere; }
.grid tr > :last-child { border-right:0; }
.grid th { height:32px; font-size:14px; white-space:nowrap; }
.center { text-align:center; }
.materials { height:340px; }
.materials tbody tr { height:22px; }
.materials td { height:22px; padding-top:0; padding-bottom:0; }
/* 右侧要求跨行时，刀模单元格仍保留与要求区之间的边线。 */
.materials tbody td.die-position { border-right:1px solid #333; }
.cell-single { display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.material-name { display:block; font-size:11px; line-height:10px; max-height:20px; overflow:hidden; overflow-wrap:anywhere; }
.die-position { font-size:11px; white-space:pre-wrap; }
.emboss-label,.emboss-value { font-size:12px; white-space:nowrap; padding:0 2px !important; }
.materials td.engineering-notes { vertical-align:top; padding:8px 5px; white-space:pre-wrap; overflow-wrap:anywhere; line-height:1.5; font-size:12px; }
.flows { height:470px; }
.flows tbody tr { height:29px; }
.flows tbody td,.flows tbody th { height:29px; }
.flows tfoot td { height:32px; border-bottom:0; font-size:13px; white-space:nowrap; }
.operation { display:flex; align-items:center; justify-content:space-between; gap:5px; height:24px; line-height:1.1; overflow:hidden; }
.operation-symbols { display:flex; align-items:center; flex-shrink:0; gap:2px; }
.symbol { display:inline-flex; align-items:flex-end; }
.symbol sub { font-size:8px; line-height:10px; margin-left:1px; transform:translateY(2px); max-width:40px; overflow-wrap:anywhere; }
.operation-remark { margin-left:auto; min-width:0; text-align:right; font-size:12px; line-height:12px; max-height:24px; overflow:hidden; overflow-wrap:anywhere; }
.plus { font-family:Arial,sans-serif; font-size:13px; }
.flows td.structure { vertical-align:middle; padding:0; }
.flows th.structure-title { font-size:15px; }
.structure-art { height:184px; padding:8px; box-sizing:border-box; display:flex; align-items:center; justify-content:center; }
.structure-art img { max-width:100%; max-height:100%; object-fit:contain; }
.structure-caption { font-size:8px; text-align:center; line-height:13px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; padding:0 3px; }
.hours { display:flex; height:34px; flex-shrink:0; border-top:1px solid #333; border-bottom:1px solid #333; align-items:stretch; }
.hours span { width:110px; border-right:1px solid #333; display:flex; align-items:center; justify-content:center; font-size:17px; }
.hours div { flex:1; padding:4px 10px; }
.issuance { flex:1; min-height:0; display:flex; }
.vertical-label { width:38px; flex-shrink:0; border-right:1px solid #333; display:flex; justify-content:center; align-items:center; writing-mode:vertical-rl; letter-spacing:8px; font-size:17px; }
.changes { flex:1; padding:9px 15px; border-right:1px solid #333; font-size:12px; line-height:1.6; overflow:hidden; }
.changes div { white-space:pre-wrap; min-height:18px; line-height:18px; overflow-wrap:anywhere; }
.issuer { width:144px; display:flex; align-items:center; justify-content:center; padding:6px; box-sizing:border-box; text-align:center; }
.approval { width:95px; height:95px; border:2px solid red; border-radius:50%; color:red; display:flex; flex-direction:column; justify-content:center; overflow:hidden; font-size:14px; }
.approval div { padding:3px; }
.approval div:nth-child(2) { border-top:1px solid red; border-bottom:1px solid red; font-size:12px; }
footer { height:27px; flex-shrink:0; border-top:1px solid #333; display:flex; align-items:center; justify-content:space-between; padding:0 4px; font-size:13px; white-space:nowrap; }
@media print { .work-paper { height:290mm; } }
</style>
