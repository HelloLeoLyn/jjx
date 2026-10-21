<template>
  <article
    ref="paper"
    class="work-paper"
    :class="{ continuous: specTemplate === 'B', 'inline-content': !generateOverflowAppendix }"
    :style="fontStyle"
  >
    <header class="work-title">
      产 品 作 业 规 范<span v-if="page.continuation">（续）</span>
    </header>
    <table class="grid materials">
      <!-- A/B 共用模板 A 的列宽及右侧凹凸/加工要求。 -->
      <colgroup>
        <col style="width: 5%" />
        <col style="width: 5%" />
        <col style="width: calc(100% / 3 - 10%)" />
        <col style="width: calc(60% - 100% / 3)" />
        <col style="width: 13%" />
        <col style="width: 16.74%" />
        <col style="width: 10.26%" />
      </colgroup>
      <thead>
        <tr>
          <th
            v-for="column in materialFontColumns"
            :key="column.key"
            :style="columnFont(column.key, 'Title')"
          >
            {{ column.heading }}
          </th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="(row, i) in padded(page.rows, materialCapacity)"
          :key="i"
          :data-material-group="specTemplate === 'B' ? row?.projectGroupKey : undefined"
        >
          <td class="center" :style="columnFont('materialNumber', 'Content')">
            {{ (page.continuation || 0) * materialCapacity + i + 1 }}
          </td>
          <td
            v-if="specTemplate !== 'B' || !row || row.projectSpan"
            class="center"
            :rowspan="specTemplate === 'B' ? row?.projectSpan || 1 : 1"
            :style="columnFont('materialProject', 'Content')"
          >
            <SvgIcon
              v-if="row?.icon"
              :name="row.icon"
              :size="fontSettings.materialProjectContent"
            /><span
              v-else
              class="cell-single"
              :data-overflow-title="materialLabel(i) + '项目'"
              :title="plain(row?.processName)"
              >{{ plain(row?.processName) }}</span
            ><sub v-if="specTemplate === 'B' && row">{{ row.projectGroup }}</sub>
          </td>
          <td :style="columnFont('materialName', 'Content')">
            <span
              class="material-name"
              :data-overflow-title="materialLabel(i) + '材料'"
              :title="row?.materialName || row?.materialCode"
              >{{ row?.materialName || row?.materialCode }}</span
            >
          </td>
          <td :style="columnFont('materialSpec', 'Content')">
            <span
              class="cell-single"
              :data-overflow-title="materialLabel(i) + '规格及模数'"
              :class="{ 'manual-params': specTemplate === 'B' && row?.params }"
              :title="materialSpec(row)"
              >{{ materialSpec(row) }}</span
            >
          </td>
          <td class="die-position" :style="columnFont('materialDie', 'Content')">
            {{ page.diePositionRows?.[i] }}
          </td>
          <template v-if="i < embossFields.length">
            <td class="emboss-label" :style="columnFont('materialEmboss', 'Content')">
              {{ embossFields[i]?.label }}
            </td>
            <td class="center emboss-value" :style="columnFont('materialEmbossValue', 'Content')">
              {{ embossValue(embossFields[i]?.key, embossFields[i]?.unit) }}
            </td>
          </template>
          <td
            v-else-if="i === embossFields.length"
            :rowspan="materialCapacity - embossFields.length"
            colspan="2"
            class="engineering-notes"
          >
            <div
              class="notes-content"
              data-overflow-title="加工要求"
              :data-overflow-repeat="page.continuation ? 'true' : undefined"
              :style="{ color: data.workSpec.requirementsColor }"
            >
              {{ page.engineeringNotes }}
            </div>
          </td>
        </tr>
      </tbody>
    </table>
    <!-- 三组流程使用同一表格行，结构图跨7行，上线表头占第8行。 -->
    <table class="grid flows">
      <colgroup>
        <template v-for="group in page.groups" :key="group.label">
          <col style="width: 5%" />
          <col style="width: 21%" />
          <col style="width: calc(100% / 3 - 26%)" />
        </template>
      </colgroup>
      <thead>
        <tr>
          <template v-for="(group, column) in page.groups" :key="group.label">
            <th
              v-if="column === 1"
              colspan="3"
              class="structure-title"
              :style="fontSize('structureTitle')"
            >
              产 品 结 构 图
            </th>
            <template v-else
              ><th :style="flowFont(column, 'Number', 'Title')">序号</th>
              <th :style="flowFont(column, 'Operation', 'Title')">{{ group.label }}作业流程</th>
              <th :style="flowFont(column, 'Hours', 'Title')">耗时(h)</th></template
            >
          </template>
        </tr>
      </thead>
      <tbody>
        <tr v-for="line in flowRows" :key="line.index">
          <template v-for="cell in line.cells" :key="cell.column">
            <template v-if="cell.column !== 1 || line.index >= 8">
              <td class="center" :style="flowFont(cell.column, 'Number', 'Content')">
                {{ cell.number }}
              </td>
              <td :style="flowFont(cell.column, 'Operation', 'Content')">
                <div
                  v-if="cell.item"
                  class="operation"
                  :data-overflow-title="cell.label + '第' + cell.number + '道作业说明'"
                  :data-overflow-text="operationText(cell.item)"
                >
                  <div class="operation-symbols" data-overflow-part>
                    <template v-for="(item, j) in operations(cell.item)" :key="item.itemId || j">
                      <span v-if="j" class="plus">＋</span>
                      <span class="symbol">
                        <SvgIcon
                          v-if="item.icon"
                          :name="item.icon"
                          :size="
                            fontSettings[`flow${cell.column}OperationContent` as WorkSpecFontArea]
                          "
                        />
                        <sub v-if="workSpecStepSubscript(item)" data-overflow-part>{{
                          workSpecStepSubscript(item)
                        }}</sub>
                      </span>
                    </template>
                  </div>
                  <span
                    v-if="workSpecOperationRemark(cell.item)"
                    class="operation-remark"
                    data-overflow-part
                    :title="workSpecOperationRemark(cell.item)"
                    >{{ workSpecOperationRemark(cell.item) }}</span
                  >
                </div>
              </td>
              <td class="center" :style="flowFont(cell.column, 'Hours', 'Content')">
                {{ labor(cell.item) }}
              </td>
            </template>
            <td v-else-if="line.index === 0" colspan="3" rowspan="7" class="structure">
              <div class="structure-art">
                <img v-if="structureImage" :src="structureImage" alt="产品结构图" />
              </div>
              <div
                v-if="structureCaption"
                class="structure-caption"
                :style="fontSize('structureCaption')"
              >
                {{ structureCaption }}
              </div>
            </td>
            <template v-else-if="line.index === 7"
              ><th :style="flowFont(cell.column, 'Number', 'Title')">序号</th>
              <th :style="flowFont(cell.column, 'Operation', 'Title')">{{ cell.label }}作业流程</th>
              <th :style="flowFont(cell.column, 'Hours', 'Title')">耗时(h)</th></template
            >
          </template>
        </tr>
      </tbody>
      <tfoot>
        <tr>
          <template v-for="(group, column) in page.groups" :key="group.label"
            ><td colspan="2" :style="flowFont(column, 'Operation', 'Title')">
              {{ group.label }}作业总耗时
            </td>
            <td class="center" :style="flowFont(column, 'Hours', 'Content')">
              {{ totalLabor(fullGroup(group.label)) }}
            </td></template
          >
        </tr>
      </tfoot>
    </table>
    <div class="hours">
      <span>合计工时</span>
      <div>{{ totalLabor(allOperations) }}</div>
    </div>
    <div class="issuance">
      <div class="vertical-label">变更内容</div>
      <div class="changes">
        <div
          class="changes-content"
          data-overflow-title="变更内容"
          :data-overflow-repeat="page.continuation ? 'true' : undefined"
        >
          <div
            v-for="(line, i) in page.changeLines"
            :key="i"
            data-overflow-record
            :style="{ color: line.color }"
          >
            {{ line.text }}
          </div>
        </div>
      </div>
      <div class="vertical-label">发行单位</div>
      <div class="issuer">
        <div v-if="data.workSpec.approved" class="approval">
          <div>{{ data.workSpec.issueUnit }}</div>
          <div>{{ shortDate(effectiveIssueDate) }}</div>
          <div>已批准</div>
        </div>
        <span v-else>{{ data.workSpec.issueUnit }}</span>
      </div>
    </div>
    <footer>
      <span>名称：{{ data.product.productName }}</span
      ><span>编号：{{ data.product.productCode }}</span
      ><span>发行日期：{{ shortDate(effectiveIssueDate) }}</span
      ><span>{{ pageIndex + 1 }}／{{ pageCount }}页</span>
    </footer>
  </article>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import SvgIcon from '@/components/SvgIcon/index.vue'
import { embossFields } from '@/api/product/workSpec'
import {
  flowGroups,
  plain,
  workSpecMaterialSpec,
  workSpecStepSubscript,
  workSpecOperationRemark,
  type DocsetData,
  type DocPage,
  type PaperTextLine,
} from './docset'

import { useWorkSpecOverflow } from './useWorkSpecOverflow'
import {
  materialFontColumns,
  type WorkSpecTemplate,
  type WorkSpecFontSettings,
  type WorkSpecFontArea,
  type WorkSpecFontColumn,
} from './workSpecFont'

const props = withDefaults(
  defineProps<{
    data: DocsetData
    page: DocPage
    pageIndex: number
    pageCount: number
    structureImage?: string
    structureCaption?: string
    fontSettings: WorkSpecFontSettings
    specTemplate?: WorkSpecTemplate
    generateOverflowAppendix?: boolean
  }>(),
  { specTemplate: 'A', generateOverflowAppendix: false }
)
const materialCapacity = computed(() =>
  props.specTemplate === 'B'
    ? Math.max(14, props.page.rows?.length || 0, props.page.diePositionRows?.length || 0)
    : 14
)
function materialSpec(row?: Record<string, any> | null) {
  return props.specTemplate === 'B' && row?.params ? row.params : workSpecMaterialSpec(row)
}
const emit = defineEmits<{ overflow: [lines: PaperTextLine[]] }>()
/**
 * 发行日期口径（dev-20261013-029）：优先 issueDate；未填且已批准回退 confirmedAt（批准即定稿发行）；未批准留空。
 * 与圆章、页脚统一使用同一口径，避免“发布/发行”混淆。
 */
const effectiveIssueDate = computed<string | null>(
  () =>
    props.data.workSpec.issueDate ||
    (props.data.workSpec.approved ? props.data.workSpec.confirmedAt || null : null)
)
const paper = ref<HTMLElement>()
const fontStyle = computed(() => ({
  '--font-title': `${props.fontSettings.title}px`,
  '--font-notes': `${props.fontSettings.notes}px`,
  '--font-notes-title': `${props.fontSettings.notesTitle}px`,
  '--font-footer': `${props.fontSettings.footer}px`,
}))
function fontSize(area: WorkSpecFontArea) {
  return { fontSize: `${props.fontSettings[area]}px` }
}
function columnFont(column: WorkSpecFontColumn, part: 'Title' | 'Content') {
  return fontSize(`${column}${part}`)
}
function flowFont(
  column: number,
  role: 'Number' | 'Operation' | 'Hours',
  part: 'Title' | 'Content'
) {
  return columnFont(`flow${column}${role}` as WorkSpecFontColumn, part)
}
const { measure } = useWorkSpecOverflow(
  paper,
  () =>
    JSON.stringify([
      props.page.rows,
      props.page.groups,
      props.page.engineeringNotes,
      props.page.changeLines,
      props.page.continuation,
      props.fontSettings,
      props.specTemplate,
      props.generateOverflowAppendix,
    ]),
  (lines) => emit('overflow', lines)
)
defineExpose({ measure })
function materialLabel(index: number) {
  return '材料第' + ((props.page.continuation || 0) * materialCapacity.value + index + 1) + '项 · '
}
function operationText(row: Record<string, any>) {
  return (
    operations(row)
      .map(
        (item: Record<string, any>, index: number) =>
          '图标' + (index + 1) + ' ' + workSpecStepSubscript(item)
      )
      .join(' ＋ ') +
    '；' +
    workSpecOperationRemark(row)
  )
}
const allOperations = computed<Record<string, any>[]>(() => props.data.routing.items || [])
const flowRows = computed(() =>
  Array.from(
    {
      length:
        props.specTemplate === 'B'
          ? Math.max(
              14,
              ...(props.page.groups || []).map(
                (group, column) => group.rows.length + (column === 1 ? 8 : 0)
              )
            )
          : 14,
    },
    (_, index) => ({
      index,
      cells: (props.page.groups || []).map((group, column) => {
        const rowIndex = column === 1 ? index - 8 : index
        return {
          column,
          label: group.label,
          number: (props.page.continuation || 0) * (column === 1 ? 6 : 14) + rowIndex + 1,
          item: group.rows[rowIndex],
        }
      }),
    })
  )
)
function fullGroup(label: string) {
  const category = flowGroups.find((group) => group.label === label)?.value
  return allOperations.value.filter((row) => row.processCategory === category)
}
function padded(rows: Record<string, any>[] = [], length: number) {
  return Array.from({ length }, (_, i) => rows[i] || null)
}
function operations(row: Record<string, any>) {
  return row.children?.length ? row.children : [row]
}
function hourValue(row: Record<string, any>): number | null {
  if (row.children?.length) {
    const values = row.children.map(hourValue).filter((value: number | null) => value !== null)
    return values.length ? values.reduce((sum: number, value: number) => sum + value, 0) : null
  }
  const value = row.customLaborHours ?? row.standardLaborHours
  return value == null || value === '' ? null : Number(value)
}
function labor(row?: Record<string, any> | null) {
  const value = row ? hourValue(row) : null
  return value == null ? '' : String(Number(value.toFixed(4)))
}
function totalLabor(rows: Record<string, any>[]) {
  const values = rows.map(hourValue).filter((value): value is number => value !== null)
  return values.length
    ? String(Number(values.reduce((sum, value) => sum + value, 0).toFixed(4)))
    : ''
}
function embossValue(key?: string, unit?: string) {
  const value = key ? props.data.workSpec.emboss[key] : ''
  if (value == null) return ''
  const text = String(value).trim()
  return unit && /^[+-]?(?:\d+\.?\d*|\.\d+)$/.test(text) ? text + ' ' + unit : text
}
function shortDate(value?: string | null) {
  return value ? value.slice(2, 10) : ''
}
</script>

<style scoped>
.manual-params {
  white-space: pre-wrap !important;
  overflow-wrap: anywhere;
}
.work-paper {
  width: 100%;
  height: 278mm;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
  border: 0;
  padding: 1.5px;
  position: relative;
  color: #252525;
  font-family: SimSun, 'Songti SC', serif;
  font-size: 13px;
}
.work-title {
  height: 65px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 27px;
  letter-spacing: 5px;
  border-bottom: 1px solid #222;
  text-decoration: underline;
  text-underline-offset: 7px;
}
.work-title span {
  font-size: 13px;
  letter-spacing: 0;
}
.grid {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  flex-shrink: 0;
}
.grid th,
.grid td {
  border: 0;
  border-right: 1px solid #333;
  border-bottom: 1px solid #333;
  padding: 2px 4px;
  line-height: 1.2;
  box-sizing: border-box;
  font-weight: 400;
  overflow-wrap: anywhere;
}
.grid tr > :last-child {
  border-right: 0;
}
.grid th {
  height: 32px;
  font-size: 14px;
  white-space: nowrap;
}
.center {
  text-align: center;
}
.materials {
  height: 340px;
}
.materials tbody tr {
  height: 22px;
}
.materials td {
  height: 22px;
  padding-top: 0;
  padding-bottom: 0;
}
/* 右侧要求跨行时，刀模单元格仍保留与要求区之间的边线。 */
.materials tbody td.die-position {
  border-right: 1px solid #333;
}
.cell-single {
  display: block;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.material-name {
  display: block;
  font-size: 12px;
  line-height: 10px;
  max-height: 20px;
  overflow: hidden;
  overflow-wrap: anywhere;
}
.die-position {
  font-size: 12px;
  white-space: pre-wrap;
}
.emboss-label,
.emboss-value {
  font-size: 12px;
  white-space: nowrap;
  padding: 0 2px !important;
}
.materials td.engineering-notes {
  vertical-align: top;
  padding: 8px 5px;
}
.notes-content {
  height: 159px;
  overflow: hidden;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 18px;
  font-size: 12px;
}
.flows {
  height: 470px;
}
.flows tbody tr {
  height: 29px;
}
.flows tbody td,
.flows tbody th {
  height: 29px;
}
.flows tfoot td {
  height: 32px;
  border-bottom: 0;
  font-size: 13px;
  white-space: nowrap;
}
.operation {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 5px;
  height: 24px;
  line-height: 1.1;
  overflow: hidden;
}
.operation-symbols {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 2px;
}
.symbol {
  display: inline-flex;
  align-items: flex-end;
}
.symbol sub {
  font-size: 8px;
  line-height: 10px;
  margin-left: 1px;
  transform: translateY(2px);
  max-width: 40px;
  overflow-wrap: anywhere;
}
.operation-remark {
  margin-left: auto;
  min-width: 0;
  text-align: right;
  font-size: 12px;
  line-height: 12px;
  max-height: 24px;
  overflow: hidden;
  overflow-wrap: anywhere;
}
.plus {
  font-family: Arial, sans-serif;
  font-size: 13px;
}
.flows td.structure {
  vertical-align: middle;
  padding: 0;
}
.flows th.structure-title {
  font-size: 15px;
}
.structure-art {
  height: 184px;
  padding: 8px;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
}
.structure-art img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}
.structure-caption {
  font-size: 8px;
  text-align: center;
  line-height: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  padding: 0 3px;
}
.hours {
  display: flex;
  height: 34px;
  flex-shrink: 0;
  border-top: 1px solid #333;
  border-bottom: 1px solid #333;
  align-items: stretch;
}
.hours span {
  width: 110px;
  border-right: 1px solid #333;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 17px;
}
.hours div {
  flex: 1;
  padding: 4px 10px;
}
.issuance {
  flex: 0 0 auto;
  min-height: 0;
  display: flex;
}
.vertical-label {
  width: 38px;
  flex-shrink: 0;
  border-right: 1px solid #333;
  display: flex;
  justify-content: center;
  align-items: center;
  writing-mode: vertical-rl;
  letter-spacing: 8px;
  font-size: 17px;
}
.changes {
  flex: 1;
  min-width: 0;
  padding: 9px 15px;
  border-right: 1px solid #333;
  font-size: 12px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.changes-content {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.changes-content > div {
  white-space: pre-wrap;
  min-height: 18px;
  line-height: 18px;
  overflow-wrap: anywhere;
}
[data-overflow-title] {
  position: relative;
}
[data-overflowing]::after {
  content: '续见附页';
  position: absolute;
  right: 0;
  bottom: 0;
  max-width: 100%;
  font-size: 8px;
  line-height: 10px;
  color: #555;
  background: #fff;
  white-space: nowrap;
  overflow: hidden;
}
.issuer {
  width: 144px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 6px;
  box-sizing: border-box;
  text-align: center;
}
.approval {
  width: 95px;
  height: 95px;
  border: 2px solid red;
  border-radius: 50%;
  color: red;
  display: flex;
  flex-direction: column;
  justify-content: center;
  overflow: hidden;
  font-size: 14px;
}
.approval div {
  padding: 3px;
}
.approval div:nth-child(2) {
  border-top: 1px solid red;
  border-bottom: 1px solid red;
  font-size: 12px;
}
footer {
  margin-top: auto;
  height: 27px;
  flex-shrink: 0;
  border-top: 1px solid #333;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px;
  font-size: 13px;
  white-space: nowrap;
}
/* Only type sizes change; the A4 frame and print margins remain fixed. */
.work-title {
  font-size: var(--font-title);
}
.work-title span {
  font-size: 0.5em;
}
.materials .material-name,
.materials .die-position,
.materials .emboss-label,
.materials .emboss-value {
  font-size: inherit;
}
.materials .material-name {
  line-height: 1;
}
.flows .plus,
.flows .operation-remark,
.flows .symbol sub {
  font-size: inherit;
}
.flows .operation-remark {
  line-height: 1.2;
}
.notes-content,
.changes,
.issuer,
.approval,
.approval div:nth-child(2),
.hours div {
  font-size: var(--font-notes);
}
.notes-content,
.changes-content > div {
  line-height: 1.5;
}
.hours span,
.vertical-label {
  font-size: var(--font-notes-title);
}
footer {
  font-size: var(--font-footer);
}
/* B 的连续源只用于测量；最终每页使用固定 A4 正文高度。 */
/* Draw the content frame per block so the footer's free space stays outside it.
   Inset outlines preserve the measured column widths and pagination geometry. */
.work-paper > :not(footer) {
  outline: 1.5px solid #222;
  outline-offset: -1.5px;
}
.inline-content:not(.continuous):not(.flow-page)
  :is(
    .material-name,
    .cell-single,
    .notes-content,
    .operation,
    .operation-remark,
    .changes,
    .changes-content
  ) {
  height: auto;
  max-height: none;
  overflow: visible;
  white-space: normal;
}
.inline-content:not(.continuous):not(.flow-page) .changes-content {
  flex: 0 0 auto;
}
.continuous,
.flow-page {
  display: flex;
  flex-direction: column;
}
.continuous {
  height: auto;
}
.flow-page {
  height: 278mm;
  overflow: hidden;
}
.continuous .materials,
.flow-page .materials {
  height: auto;
  min-height: 0;
}
.continuous .flows,
.flow-page .flows {
  height: auto;
  min-height: 0;
}
.continuous .notes-content,
.flow-page .notes-content {
  height: auto;
  overflow: visible;
  min-height: 0;
}
.continuous .issuance,
.flow-page .issuance {
  flex: 0 0 auto;
  min-height: 110px;
}
.continuous .changes-content,
.flow-page .changes-content {
  overflow: visible;
}
.continuous .material-name,
.flow-page .material-name,
.continuous .cell-single,
.flow-page .cell-single,
.continuous .operation,
.flow-page .operation,
.continuous .operation-remark,
.flow-page .operation-remark {
  height: auto;
  max-height: none;
  overflow: visible;
  white-space: normal;
}
.continuous .operation,
.flow-page .operation {
  min-height: 24px;
}
.flow-page footer {
  margin-top: auto;
}
@media print {
  .work-paper {
    width: 100%;
    height: 278mm;
  }
}
</style>
