<template>
  <section class="qr039-sheet">
    <header>深圳市精捷信科技有限公司</header>
    <div class="company-en">Shen Zhen Jing Jie Xin Technology Co.,Limited</div>
    <h1>成品检验报告</h1>
    <table>
      <tbody>
        <tr>
          <th colspan="2">客 户：</th>
          <td colspan="3">{{ text(data.partnerName) }}</td>
          <th colspan="2">订单数量：</th>
          <td colspan="2">{{ num(data.lotQuantity) }}</td>
          <th colspan="2">抽验数量：</th>
          <td>{{ num(data.sampleQuantity) }}</td>
        </tr>
        <tr>
          <th colspan="2">品 名：</th>
          <td colspan="3">{{ productName }}</td>
          <th colspan="2">订单号码：</th>
          <td colspan="2">{{ text(data.sourceNo) }}</td>
          <th colspan="2">生产批号：</th>
          <td>{{ text(data.batchNo) }}</td>
        </tr>
        <tr>
          <th colspan="2">料 号：</th>
          <td colspan="3">{{ productCode }}</td>
          <th colspan="2">机种号码：</th>
          <td colspan="2">{{ text(data.machineModel) }}</td>
          <th colspan="2">版本号：</th>
          <td>{{ text(data.version) }}</td>
        </tr>
        <tr>
          <th colspan="2">检验单号：</th>
          <td colspan="3">{{ text(data.lotNo) }}</td>
          <th colspan="2">检验时间：</th>
          <td colspan="2">{{ dateTime(data.inspectTime) }}</td>
          <th colspan="2">不良数：</th>
          <td>{{ num(data.failQuantity) }}</td>
        </tr>
        <tr class="column-head">
          <th colspan="2">检验项目</th>
          <th colspan="2">检验方法</th>
          <th colspan="2">检验规范</th>
          <th>SAMPLE1</th>
          <th>SAMPLE2</th>
          <th>SAMPLE3</th>
          <th>MA</th>
          <th>MI</th>
          <th>判定</th>
        </tr>
        <template v-for="group in displayGroups" :key="group.name"
          ><tr v-for="(row, index) in group.rows" :key="`${group.name}-${index}`">
            <th v-if="index === 0" :rowspan="group.rows.length" class="category">
              {{ group.name }}
            </th>
            <th>{{ row.name }}</th>
            <td colspan="2">{{ text(row.item?.inspectionMethod) }}</td>
            <td colspan="2">{{ text(row.item?.standard) }}</td>
            <td>{{ splitSamples(row.item?.sampleValues)[0] }}</td>
            <td>{{ splitSamples(row.item?.sampleValues)[1] }}</td>
            <td>{{ splitSamples(row.item?.sampleValues)[2] }}</td>
            <td>{{ num(row.item?.maQuantity) }}</td>
            <td>{{ num(row.item?.miQuantity) }}</td>
            <td>{{ itemResult(row.item?.result) }}</td>
          </tr></template
        >
        <tr class="summary-head">
          <th>样本数</th>
          <th colspan="2">缺点类别</th>
          <th>A.Q.L</th>
          <th colspan="2">ACC REJ</th>
          <th>不良数</th>
          <th colspan="2">不良品数</th>
          <th colspan="3">判定结果</th>
        </tr>
        <tr>
          <td rowspan="2">{{ num(data.sampleQuantity) }}</td>
          <th colspan="2">重缺点</th>
          <td>{{ num(data.aqlValue) }}</td>
          <td colspan="2">{{ accRej }}</td>
          <td>{{ majorTotal }}</td>
          <td colspan="2">{{ num(data.failQuantity) }}</td>
          <td colspan="3" rowspan="2" class="decision">
            □OK&nbsp;&nbsp;□NG&nbsp;&nbsp;□AOD<br /><strong>{{ resultText }}</strong>
          </td>
        </tr>
        <tr>
          <th colspan="2">轻缺点</th>
          <td></td>
          <td colspan="2"></td>
          <td>{{ minorTotal }}</td>
          <td colspan="2"></td>
        </tr>
        <tr>
          <td colspan="12" class="note">不良说明：{{ text(data.remark) }}</td>
        </tr>
        <tr>
          <td colspan="12" class="signatures">
            品管主管：<span>检验员：{{ text(data.inspector) }}</span>
          </td>
        </tr>
        <tr class="record-row">
          <td colspan="6"></td>
          <td colspan="3">检验日期：{{ date(data.inspectTime) }}</td>
          <td colspan="3">表单编号：{{ data.recordNo || 'JJX-QR-039' }}</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { InspectionResult } from '@/enums/quality'

type ReportItem = Record<string, any>
const props = defineProps<{ data: Record<string, any> }>()
const data = computed(() => props.data)
const layout = [
  { name: '材 质', rows: ['面板', '上线', '下线', '背胶'] },
  { name: '尺 寸', rows: ['长度', '宽度', '厚度', 'Key高度', '线头', '视窗'] },
  { name: '功能', rows: ['电阻', '防水', '寿命'] },
  { name: '印 刷', rows: ['图文', '灯孔', '网点', '线头'] },
  { name: '组合', rows: ['LED', 'PIN', '弹片', '背胶'] },
  { name: '颜 色', rows: ['Color', 'Color', 'Color', 'Color', 'Color', 'Color', 'Color'] },
]
const displayGroups = computed(() => {
  const queues = new Map<string, ReportItem[]>()
  for (const item of data.value.items || []) {
    const key = String(item.checkItem || '')
      .trim()
      .toLowerCase()
    queues.set(key, [...(queues.get(key) || []), item])
  }
  return layout.map((group) => ({
    name: group.name,
    rows: group.rows.map((name) => ({ name, item: queues.get(name.toLowerCase())?.shift() })),
  }))
})
const productName = computed(() => text(data.value.productName || data.value.materialName))
const productCode = computed(() => text(data.value.productCode || data.value.materialCode))
const majorTotal = computed(() =>
  (data.value.items || []).reduce(
    (sum: number, item: ReportItem) => sum + Number(item.maQuantity || 0),
    0
  )
)
const minorTotal = computed(() =>
  (data.value.items || []).reduce(
    (sum: number, item: ReportItem) => sum + Number(item.miQuantity || 0),
    0
  )
)
const accRej = computed(() => `${num(data.value.acceptNumber)} / ${num(data.value.rejectNumber)}`)
const resultText = computed(() =>
  data.value.result === InspectionResult.FAIL
    ? 'NG（不合格）'
    : data.value.result === InspectionResult.PASS
      ? 'OK（合格）'
      : '待判定'
)
const text = (value?: unknown) => (value == null ? '' : String(value))
const num = (value?: unknown) =>
  value == null || value === ''
    ? ''
    : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
const dateTime = (value?: string) => (value ? value.replace('T', ' ').slice(0, 16) : '')
const date = (value?: string) => value?.slice(0, 10) || ''
const splitSamples = (value?: string) => {
  const parts = String(value || '')
    .split(/[|,，;；/]/)
    .map((v) => v.trim())
    .filter(Boolean)
  return [parts[0] || '', parts[1] || '', parts[2] || '']
}
const itemResult = (result?: string) =>
  result === InspectionResult.PASS ? 'OK' : result === InspectionResult.FAIL ? 'NG' : text(result)
</script>

<style scoped>
.qr039-sheet {
  color: #000;
  font-family: SimSun, '宋体', serif;
  font-size: 9.5px;
}
header {
  text-align: center;
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 2px;
}
.company-en {
  text-align: center;
  font-size: 10px;
}
h1 {
  margin: 3px 0 4px;
  text-align: center;
  font:
    700 21px SimHei,
    '黑体',
    sans-serif;
  letter-spacing: 4px;
}
table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
th,
td {
  height: 17px;
  padding: 1px 2px;
  border: 1px solid #000;
  text-align: center;
  overflow-wrap: anywhere;
}
th {
  font-weight: 700;
}
.column-head th,
.summary-head th {
  height: 21px;
}
.category {
  width: 22px;
  letter-spacing: 2px;
}
.decision {
  line-height: 16px;
}
.note {
  height: 25px !important;
  text-align: left !important;
}
.signatures {
  height: 28px !important;
  text-align: left !important;
  padding: 3px 14px !important;
}
.signatures span {
  float: right;
  margin-right: 70px;
}
.record-row td {
  height: 17px;
  border: 0;
  text-align: right;
}
@media print {
  .qr039-sheet {
    break-inside: avoid;
  }
}
</style>
