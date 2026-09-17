<template>
  <section class="lot-report">
    <header class="company">深圳市精捷信科技有限公司</header>
    <div class="company-en">Shen Zhen Jing Jie Xin Technology Co.,Limited</div>
    <h1>{{ data.reportTitle || '检验报告' }}</h1>

    <table class="sheet">
      <tbody>
        <tr>
          <th>{{ isIqc ? '供应厂商' : '客 户' }}</th>
          <td colspan="2">{{ text(data.partnerName) }}</td>
          <th>{{ isIqc ? '验收单号' : '工单号码' }}</th>
          <td colspan="2">{{ text(data.sourceNo) }}</td>
          <th>检验批号</th>
          <td colspan="2">{{ text(data.lotNo) }}</td>
        </tr>
        <tr>
          <th>{{ isIqc ? '品名规格' : '品 名' }}</th>
          <td colspan="2">{{ productText }}</td>
          <th>{{ isIqc ? '来料批量' : '订单数量' }}</th>
          <td colspan="2">{{ num(data.lotQuantity) }}</td>
          <th>抽检数量</th>
          <td colspan="2">
            {{ num(data.sampleQuantity) }}
            <span v-if="!isIqc" class="hint">（全检）</span>
          </td>
        </tr>
        <tr>
          <th>AQL</th>
          <td>{{ data.aqlValue != null ? num(data.aqlValue) : '-' }}</td>
          <th>检验水平</th>
          <td>{{ text(data.inspectionLevel) || '-' }}</td>
          <th>AC / RE</th>
          <td>{{ data.acceptNumber != null || data.rejectNumber != null ? `${num(data.acceptNumber)} / ${num(data.rejectNumber)}` : '-' }}</td>
          <th>版本号</th>
          <td colspan="2">{{ text(data.version) }}</td>
        </tr>
        <tr>
          <th>生产批号</th>
          <td colspan="2">{{ text(data.batchNo) }}</td>
          <th>检验时间</th>
          <td colspan="2">{{ dateTime(data.inspectTime) }}</td>
          <th>不良数</th>
          <td colspan="2">{{ num(data.failQuantity) }}</td>
        </tr>

        <tr class="column-head">
          <th>检验项目</th>
          <th>检验方法</th>
          <th>检验规范</th>
          <th>SAMPLE1</th>
          <th>SAMPLE2</th>
          <th>SAMPLE3</th>
          <th>MA</th>
          <th>MI</th>
          <th>判定</th>
        </tr>
        <tr v-for="(row, index) in data.items || []" :key="index">
          <th>{{ text(row.checkItem) }}</th>
          <td>{{ text(row.inspectionMethod) }}</td>
          <td>{{ text(row.standard) }}</td>
          <td>{{ samples(row.sampleValues)[0] }}</td>
          <td>{{ samples(row.sampleValues)[1] }}</td>
          <td>{{ samples(row.sampleValues)[2] }}</td>
          <td>{{ num(row.maQuantity) }}</td>
          <td>{{ num(row.miQuantity) }}</td>
          <td>{{ row.result === 'pass' ? 'OK' : row.result === 'fail' ? 'NG' : '' }}</td>
        </tr>
        <tr v-if="!(data.items || []).length">
          <td colspan="9" class="center">（未录入检验项）</td>
        </tr>

        <tr class="summary">
          <th>样本数</th>
          <th>缺点类别</th>
          <td>不良数</td>
          <th>已处置</th>
          <th>待处置</th>
          <td colspan="2">不良品数</td>
          <td>判定结果</td>
          <td>{{ resultText }}</td>
        </tr>
        <tr>
          <td>{{ num(data.sampleQuantity) }}</td>
          <th>重缺点（CR+MA）</th>
          <td>{{ num(heavyDefect) }}</td>
          <td>{{ num(data.defectDisposed) }}</td>
          <td>{{ num(pendingDefect) }}</td>
          <td colspan="2">{{ num(data.defectQuantity) }}</td>
          <td>OK / NG / 特采</td>
          <td>□OK □NG □特采</td>
        </tr>
        <tr>
          <td colspan="2">轻缺点（MI）</td>
          <td colspan="7">{{ num(data.defectMi) }}</td>
        </tr>
        <tr>
          <td colspan="9" class="note">备注：{{ text(data.remark) || '—' }}</td>
        </tr>
        <tr>
          <td colspan="9" class="sign">品管主管：________________　　检验员：{{ text(data.inspector) }}</td>
        </tr>
        <tr>
          <td colspan="6"></td>
          <td colspan="3">表单编号：{{ data.recordNo || '' }}</td>
        </tr>
      </tbody>
    </table>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ data: Record<string, any> }>()
const isIqc = computed(() => props.data.lotType === 'IQC')
const productText = computed(() => {
  const code = props.data.materialCode || props.data.productCode || ''
  const name = props.data.materialName || props.data.productName || ''
  const spec = props.data.specification ? ` / ${props.data.specification}` : ''
  return `${code}${name ? ' · ' + name : ''}${spec}`
})
const heavyDefect = computed(() => Number(props.data.defectCr || 0) + Number(props.data.defectMa || 0))
const pendingDefect = computed(
  () => Number(props.data.defectQuantity || 0) - Number(props.data.defectDisposed || 0)
)
const resultText = computed(() => {
  const result = props.data.result
  return result === 'fail' ? 'NG（不合格）' : result === 'concession' ? '特采（让步接收）' : result === 'pass' ? 'OK（合格）' : '待判定'
})
const text = (value?: string | null) => value || ''
const num = (value?: number | null) =>
  value == null ? '-' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
const dateTime = (value?: string) => (value ? value.replace('T', ' ').slice(0, 16) : '')
const samples = (value?: string) => {
  const parts = String(value || '')
    .split(/[|,，;；/]/)
    .map((v) => v.trim())
    .filter(Boolean)
  return [parts[0] || '', parts[1] || '', parts[2] || '']
}
</script>

<style scoped>
.lot-report {
  color: #000;
  font-family: SimSun, '宋体', serif;
  font-size: 10px;
}
.company {
  text-align: center;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 2px;
}
.company-en {
  text-align: center;
  font-size: 9px;
  color: #444;
}
h1 {
  text-align: center;
  font-size: 16px;
  letter-spacing: 6px;
  margin: 8px 0 10px;
  border-bottom: 2px solid #000;
  padding-bottom: 6px;
}
.sheet {
  width: 100%;
  border-collapse: collapse;
}
.sheet th,
.sheet td {
  border: 1px solid #555;
  padding: 4px 5px;
  text-align: left;
  vertical-align: middle;
  word-break: break-all;
}
.sheet th {
  font-weight: 600;
  background: #f2f2f2;
}
.column-head th {
  text-align: center;
}
.summary th,
.summary td {
  text-align: center;
}
.center {
  text-align: center;
}
.note,
.sign {
  padding: 6px;
}
.hint {
  color: #666;
}
</style>
