<template>
  <article class="work-paper">
    <header class="work-title">产 品 作 业 规 范<span v-if="page.continuation">（续）</span></header>
    <div class="upper-section">
      <table class="grid materials">
        <colgroup><col style="width:7%" /><col style="width:7%" /><col style="width:31%" /><col style="width:35%" /><col style="width:20%" /></colgroup>
        <thead><tr><th>序号</th><th>项目</th><th>材　料</th><th>规格及模数</th><th>刀模位置</th></tr></thead>
        <tbody>
          <tr v-for="(row, i) in padded(page.rows, 14)" :key="i">
            <td class="center">{{ '(' + ((page.continuation || 0) * 14 + i + 1) + ')' }}</td>
            <td class="center"><SvgIcon v-if="row?.icon" :name="row.icon" :size="18" /><span v-else class="cell-single" :title="plain(row?.processName)">{{ plain(row?.processName) }}</span></td>
            <td><span class="cell-single" :title="row?.materialName || row?.materialCode">{{ row?.materialName || row?.materialCode }}</span></td>
            <td><span class="cell-single" :title="materialSpec(row)">{{ materialSpec(row) }}</span></td>
            <td v-if="i === 0" rowspan="14" class="die-position">{{ page.diePosition }}</td>
          </tr>
        </tbody>
      </table>
      <div class="requirements">
        <table class="grid emboss">
          <colgroup><col style="width:62%" /><col style="width:38%" /></colgroup>
          <thead><tr><th>凹凸条件</th><th></th></tr></thead>
          <tbody><tr v-for="field in embossFields" :key="field.key"><td>{{ field.label }}</td><td class="center">{{ embossValue(field.key, field.unit) }}</td></tr></tbody>
        </table>
        <div class="engineering-notes" :style="{ color: data.workSpec.requirementsColor }">{{ page.engineeringNotes }}</div>
      </div>
    </div>
    <div class="flows">
      <section v-for="(group, column) in page.groups" :key="group.label" class="flow-column">
        <div v-if="column === 1" class="structure">
          <div class="structure-title">产 品 结 构 图</div>
          <div class="structure-art"><img v-if="structureImage" :src="structureImage" alt="产品结构图" /></div>
          <div v-if="structureCaption" class="structure-caption">{{ structureCaption }}</div>
        </div>
        <table class="grid flow">
          <colgroup><col style="width:15%" /><col style="width:63%" /><col style="width:22%" /></colgroup>
          <thead><tr><th>序号</th><th>{{ group.label }}作业流程</th><th>耗时(h)</th></tr></thead>
          <tbody><tr v-for="(row, i) in padded(group.rows, column === 1 ? 6 : 14)" :key="i">
            <td class="center">{{ '(' + ((page.continuation || 0) * (column === 1 ? 6 : 14) + i + 1) + ')' }}</td>
            <td><div v-if="row" class="operation">
              <template v-for="(item, j) in operations(row)" :key="item.itemId || j">
                <span v-if="j" class="plus">＋</span>
                <span class="symbol"><SvgIcon v-if="item.icon" :name="item.icon" :size="19" /><span v-else>{{ plain(item.processName) }}</span><sub v-if="item.indexNumber">{{ item.indexNumber }}</sub><small v-if="item.workInstruction || item.description">{{ plain(item.workInstruction || item.description) }}</small></span>
              </template>
              <small v-if="row.children?.length && (row.workInstruction || row.description)">{{ plain(row.workInstruction || row.description) }}</small>
              <small v-if="row.remark">{{ plain(row.remark) }}</small>
            </div></td>
            <td class="center">{{ labor(row) }}</td>
          </tr></tbody>
          <tfoot><tr><td colspan="2">{{ group.label }}作业总耗时</td><td class="center">{{ totalLabor(fullGroup(group.label)) }}</td></tr></tfoot>
        </table>
      </section>
    </div>
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
import { flowGroups, plain, type DocsetData, type DocPage } from './docset'

const props = defineProps<{ data: DocsetData; page: DocPage; pageIndex: number; pageCount: number; structureImage?: string; structureCaption?: string }>()
const allOperations = computed<Record<string, any>[]>(() => props.data.routing.items || [])
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
function embossValue(key: string, unit: string) { const value = props.data.workSpec.emboss[key]; return value == null || value === '' ? '' : value + ' ' + unit }
function materialSpec(row?: Record<string, any> | null) {
  if (!row) return ''
  const specification = row.widthMm != null && row.lengthMm != null ? row.widthMm + '×' + row.lengthMm + 'mm' : row.specification || ''
  return specification + (row.moduleQty == null ? '' : '＝' + row.moduleQty + 'PCS')
}
function shortDate(value?: string | null) { return value ? value.slice(2, 10) : '' }
</script>

<style scoped>
.work-paper { height:1098px; display:flex; flex-direction:column; box-sizing:border-box; border:1.5px solid #222; color:#252525; font-family:SimSun,'Songti SC',serif; font-size:13px; }
.work-title { height:65px; flex-shrink:0; display:flex; align-items:center; justify-content:center; font-size:27px; letter-spacing:5px; border-bottom:1px solid #222; text-decoration:underline; text-underline-offset:7px; }
.work-title span { font-size:13px; letter-spacing:0; }
.grid { width:100%; border-collapse:collapse; table-layout:fixed; }
.grid th,.grid td { border:1px solid #333; padding:2px 4px; line-height:1.2; box-sizing:border-box; font-weight:400; overflow-wrap:anywhere; }
.grid th { height:32px; font-size:14px; white-space:nowrap; }
.center { text-align:center; }
.upper-section { height:340px; display:flex; flex-shrink:0; }
.materials { width:73%; height:340px; }
.materials td { height:22px; }
.cell-single { display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.die-position { vertical-align:top; white-space:pre-wrap; padding-top:6px !important; }
.requirements { width:27%; display:flex; flex-direction:column; }
.emboss td { height:22px; font-size:12px; white-space:nowrap; padding:2px; }
.engineering-notes { flex:1; padding:8px 5px; border:1px solid #333; border-top:0; white-space:pre-wrap; overflow-wrap:anywhere; line-height:1.5; font-size:12px; overflow:hidden; }
.flows { display:flex; height:470px; flex-shrink:0; }
.flow-column { width:33.333333%; min-width:0; }
.flow th { height:32px; }
.flow tbody td { height:29px; }
.flow tfoot td { height:32px; font-size:13px; white-space:nowrap; }
.operation { display:flex; align-items:center; flex-wrap:wrap; gap:2px; line-height:1.1; height:24px; overflow:hidden; }
.symbol { display:inline-flex; align-items:center; gap:2px; }
.symbol sub { font-size:9px; }
.operation small { font-size:9px; }
.plus { font-family:Arial,sans-serif; font-size:13px; }
.structure { height:232px; position:relative; box-sizing:border-box; border:1px solid #333; }
.structure-title { height:32px; display:flex; align-items:center; justify-content:center; border-bottom:1px solid #333; font-size:15px; }
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
