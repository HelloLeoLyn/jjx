<template>
  <article ref="paper" class="work-paper">
    <header class="work-title">产 品 作 业 规 范<span v-if="page.continuation">（续）</span></header>
    <!-- 顶部三栏：结构图（左上）· 凹凸条件（居中）· 备注（右上），等高对齐。 -->
    <table class="grid work-top">
      <colgroup><col style="width:33.34%" /><col style="width:33.33%" /><col style="width:33.33%" /></colgroup>
      <tbody>
        <tr>
          <td class="top-structure">
            <div class="top-cell">
              <div class="top-label">产 品 结 构 图</div>
              <div class="structure-art"><img v-if="structureImage" :src="structureImage" alt="产品结构图" /></div>
              <div v-if="structureCaption" class="structure-caption">{{ structureCaption }}</div>
            </div>
          </td>
          <td class="top-emboss">
            <div class="top-cell">
              <div class="top-label">凹 凸 条 件</div>
              <div class="emboss-grid">
                <div v-for="field in embossFields" :key="field.key" class="emboss-item">
                  <span class="emboss-key">{{ field.label }}</span>
                  <span class="emboss-val">{{ embossValue(field.key, field.unit) }}</span>
                </div>
              </div>
            </div>
          </td>
          <td class="top-remark">
            <div class="top-cell">
              <div class="top-label">备　注</div>
              <div class="notes-content" data-overflow-title="备注" :data-overflow-repeat="page.continuation ? 'true' : undefined" :style="{ color: data.workSpec.requirementsColor }">{{ page.engineeringNotes }}</div>
            </div>
          </td>
        </tr>
      </tbody>
    </table>
    <table class="grid materials">
      <colgroup>
        <col style="width:6%" /><col style="width:6%" /><col style="width:30%" /><col style="width:38%" /><col style="width:20%" />
      </colgroup>
      <thead><tr><th>序号</th><th>项目</th><th>材　料</th><th>规格及模数</th><th>刀模位置</th></tr></thead>
      <tbody>
        <tr v-for="(row, i) in padded(page.rows, 14)" :key="i">
          <td class="center">{{ '(' + ((page.continuation || 0) * 14 + i + 1) + ')' }}</td>
          <td class="center"><SvgIcon v-if="row?.icon" :name="row.icon" :size="18" /><span v-else class="cell-single" :data-overflow-title="materialLabel(i) + '项目'" :title="plain(row?.processName)">{{ plain(row?.processName) }}</span></td>
          <td><span class="material-name" :data-overflow-title="materialLabel(i) + '材料'" :title="row?.materialName || row?.materialCode">{{ row?.materialName || row?.materialCode }}</span></td>
          <td><span class="cell-single" :data-overflow-title="materialLabel(i) + '规格及模数'" :title="workSpecMaterialSpec(row)">{{ workSpecMaterialSpec(row) }}</span></td>
          <td class="die-position">{{ page.diePositionRows?.[i] }}</td>
        </tr>
      </tbody>
    </table>
    <!-- 三组流程使用同一表格行，各占等宽一栏。 -->
    <table class="grid flows">
      <colgroup>
        <template v-for="group in page.groups" :key="group.label">
          <col style="width:5%" /><col style="width:21%" /><col style="width:calc(100% / 3 - 26%)" />
        </template>
      </colgroup>
      <thead><tr>
        <template v-for="group in page.groups" :key="group.label"><th>序号</th><th>{{ group.label }}作业流程</th><th>耗时(h)</th></template>
      </tr></thead>
      <tbody>
        <tr v-for="line in flowRows" :key="line.index">
          <template v-for="cell in line.cells" :key="cell.column">
            <td class="center">{{ '(' + cell.number + ')' }}</td>
            <td>
              <div v-if="cell.item" class="operation" :data-overflow-title="cell.label + '第' + cell.number + '道作业说明'" :data-overflow-text="operationText(cell.item)">
                <div class="operation-symbols" data-overflow-part>
                  <template v-for="(item, j) in operations(cell.item)" :key="item.itemId || j">
                    <span v-if="j" class="plus">＋</span>
                    <span class="symbol">
                      <SvgIcon v-if="item.icon" :name="item.icon" :size="20" />
                      <sub v-if="workSpecStepSubscript(item)" data-overflow-part>{{ workSpecStepSubscript(item) }}</sub>
                    </span>
                  </template>
                </div>
                <span v-if="workSpecOperationRemark(cell.item)" class="operation-remark" data-overflow-part :title="workSpecOperationRemark(cell.item)">{{ workSpecOperationRemark(cell.item) }}</span>
              </div>
            </td>
            <td class="center">{{ labor(cell.item) }}</td>
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
      <div class="changes"><div class="changes-content" data-overflow-title="变更内容" :data-overflow-repeat="page.continuation ? 'true' : undefined"><div v-for="(line, i) in page.changeLines" :key="i" data-overflow-record :style="{ color: line.color }">{{ line.text }}</div></div></div>
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
import { computed, ref } from 'vue'
import SvgIcon from '@/components/SvgIcon/index.vue'
import { embossFields } from '@/api/product/workSpec'
import { flowGroups, plain, workSpecMaterialSpec, workSpecStepSubscript, workSpecOperationRemark, type DocsetData, type DocPage, type PaperTextLine } from './docset'

import { useWorkSpecOverflow } from './useWorkSpecOverflow'

const props = defineProps<{ data: DocsetData; page: DocPage; pageIndex: number; pageCount: number; structureImage?: string; structureCaption?: string }>()
const emit = defineEmits<{ overflow: [lines: PaperTextLine[]] }>()
const paper = ref<HTMLElement>()
const { measure } = useWorkSpecOverflow(paper, () => JSON.stringify([props.page.rows, props.page.groups, props.page.engineeringNotes, props.page.changeLines, props.page.continuation]), lines => emit('overflow', lines))
defineExpose({ measure })
function materialLabel(index: number) { return '材料第' + ((props.page.continuation || 0) * 14 + index + 1) + '项 · ' }
function operationText(row: Record<string, any>) {
  return operations(row).map((item: Record<string, any>, index: number) => '图标' + (index + 1) + ' ' + workSpecStepSubscript(item)).join(' ＋ ') + '；' + workSpecOperationRemark(row)
}
const allOperations = computed<Record<string, any>[]>(() => props.data.routing.items || [])
const flowRows = computed(() => Array.from({ length: 14 }, (_, index) => ({
  index,
  cells: (props.page.groups || []).map((group, column) => ({
    column, label: group.label,
    number: (props.page.continuation || 0) * 14 + index + 1,
    item: group.rows[index],
  })),
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
.work-paper { height:290mm; display:flex; flex-direction:column; box-sizing:border-box; border:1.5px solid #222; color:#252525; font-family:SimSun,'Songti SC',serif; font-size:13px; }
.work-title { height:60px; flex-shrink:0; display:flex; align-items:center; justify-content:center; font-size:26px; letter-spacing:5px; border-bottom:1px solid #222; text-decoration:underline; text-underline-offset:7px; }
.work-title span { font-size:13px; letter-spacing:0; }
.grid { width:100%; border-collapse:collapse; table-layout:fixed; flex-shrink:0; }
.grid th,.grid td { border:0; border-right:1px solid #333; border-bottom:1px solid #333; padding:2px 4px; line-height:1.2; box-sizing:border-box; font-weight:400; overflow-wrap:anywhere; }
.grid tr > :last-child { border-right:0; }
.grid th { height:30px; font-size:14px; white-space:nowrap; }
.center { text-align:center; }
/* 顶部三栏：结构图 / 凹凸条件 / 备注 */
.work-top { height:176px; }
.work-top td { vertical-align:top; padding:0; }
.top-cell { height:176px; display:flex; flex-direction:column; }
.top-label { height:27px; flex-shrink:0; display:flex; align-items:center; justify-content:center; font-size:14px; border-bottom:1px solid #333; }
.structure-art { flex:1; min-height:0; padding:6px; box-sizing:border-box; display:flex; align-items:center; justify-content:center; }
.structure-art img { max-width:100%; max-height:100%; object-fit:contain; }
.structure-caption { flex-shrink:0; height:14px; font-size:9px; text-align:center; line-height:14px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap; padding:0 3px; box-sizing:border-box; }
.emboss-grid { flex:1; min-height:0; display:grid; grid-template-columns:1fr 1fr; grid-auto-rows:1fr; }
.emboss-item { display:flex; align-items:center; justify-content:space-between; gap:4px; padding:0 6px; font-size:11px; border-bottom:1px solid #d5d5d5; box-sizing:border-box; overflow:hidden; }
.emboss-item:nth-child(2n) { border-right:1px solid #d5d5d5; }
.emboss-item:nth-last-child(-n+2) { border-bottom:0; }
.emboss-key { color:#333; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.emboss-val { flex-shrink:0; white-space:nowrap; }
.notes-content { flex:1; min-height:0; overflow:hidden; white-space:pre-wrap; overflow-wrap:anywhere; line-height:17px; font-size:11px; padding:6px; box-sizing:border-box; }
.materials { height:300px; }
.materials tbody tr { height:19px; }
.materials td { height:19px; padding-top:0; padding-bottom:0; }
.cell-single { display:block; white-space:nowrap; overflow:hidden; text-overflow:ellipsis; }
.material-name { display:block; font-size:11px; line-height:19px; max-height:19px; overflow:hidden; overflow-wrap:anywhere; }
.die-position { font-size:11px; white-space:pre-wrap; }
.flows { height:372px; }
.flows tbody tr { height:22px; }
.flows tbody td,.flows tbody th { height:22px; }
.flows tfoot td { height:28px; border-bottom:0; font-size:13px; white-space:nowrap; }
.operation { display:flex; align-items:center; justify-content:space-between; gap:5px; height:20px; line-height:1.1; overflow:hidden; }
.operation-symbols { display:flex; align-items:center; flex-shrink:0; gap:2px; }
.symbol { display:inline-flex; align-items:flex-end; }
.symbol sub { font-size:8px; line-height:10px; margin-left:1px; transform:translateY(2px); max-width:40px; overflow-wrap:anywhere; }
.operation-remark { margin-left:auto; min-width:0; text-align:right; font-size:12px; line-height:12px; max-height:20px; overflow:hidden; overflow-wrap:anywhere; }
.plus { font-family:Arial,sans-serif; font-size:13px; }
.hours { display:flex; height:34px; flex-shrink:0; border-top:1px solid #333; border-bottom:1px solid #333; align-items:stretch; }
.hours span { width:110px; border-right:1px solid #333; display:flex; align-items:center; justify-content:center; font-size:17px; }
.hours div { flex:1; padding:4px 10px; }
.issuance { flex:1; min-height:0; display:flex; }
.vertical-label { width:38px; flex-shrink:0; border-right:1px solid #333; display:flex; justify-content:center; align-items:center; writing-mode:vertical-rl; letter-spacing:8px; font-size:17px; }
.changes { flex:1; min-width:0; padding:9px 15px; border-right:1px solid #333; font-size:12px; display:flex; flex-direction:column; overflow:hidden; }
.changes-content { flex:1; min-height:0; overflow:hidden; }
.changes-content > div { white-space:pre-wrap; min-height:18px; line-height:18px; overflow-wrap:anywhere; }
[data-overflow-title] { position:relative; }
[data-overflowing]::after { content:"续见附页"; position:absolute; right:0; bottom:0; max-width:100%; font-size:8px; line-height:10px; color:#555; background:#fff; white-space:nowrap; overflow:hidden; }
.issuer { width:144px; display:flex; align-items:center; justify-content:center; padding:6px; box-sizing:border-box; text-align:center; }
.approval { width:95px; height:95px; border:2px solid red; border-radius:50%; color:red; display:flex; flex-direction:column; justify-content:center; overflow:hidden; font-size:14px; }
.approval div { padding:3px; }
.approval div:nth-child(2) { border-top:1px solid red; border-bottom:1px solid red; font-size:12px; }
footer { height:27px; flex-shrink:0; border-top:1px solid #333; display:flex; align-items:center; justify-content:space-between; padding:0 4px; font-size:13px; white-space:nowrap; }
@media print { .work-paper { height:290mm; } }
</style>
