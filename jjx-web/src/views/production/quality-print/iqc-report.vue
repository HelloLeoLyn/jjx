<template>
  <div class="linked-print-page iqc-print-page">
    <div class="linked-print-toolbar no-print">
      <el-button @click="router.back()">返回</el-button>
      <el-radio-group :model-value="mode" size="small" @change="changeMode">
        <el-radio-button value="system">系统版</el-radio-button>
        <el-radio-button value="paper">纸版(QR-037)</el-radio-button>
      </el-radio-group>
      <el-button type="primary" :loading="printing" :disabled="!info" @click="print"
        >打印</el-button
      >
    </div>

    <A4Canvas v-if="info" :padding-mm="mode === 'paper' ? 11 : 14">
      <section v-if="mode === 'system'" class="system-sheet">
        <PrintCompanyHeader variant="center" />
        <div class="linked-print-title">进料检验报告</div>
        <div class="linked-print-meta">
          <div>记录编号：JJX-QR-037</div>
          <div>收货单：{{ display(info.inboundNo) }}</div>
          <div>收货日期：{{ display(reportDate) }}</div>
          <div>供应商：{{ display(info.supplierName) }}</div>
          <div>来源单号：{{ display(info.sourceNo) }}</div>
          <div>检验结果：{{ inspectionLabel }}</div>
        </div>
        <table class="linked-print-table">
          <thead>
            <tr>
              <th style="width: 42px">序号</th>
              <th>物料编码</th>
              <th>物料名称</th>
              <th>规格</th>
              <th>批次</th>
              <th>收货数</th>
              <th>合格数</th>
              <th>拒收数</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="(item, index) in info.items || []"
              :key="item.inboundItemId || item.itemId || index"
            >
              <td>{{ index + 1 }}</td>
              <td>{{ display(item.materialCode) }}</td>
              <td>{{ display(item.materialName) }}</td>
              <td>{{ display(item.specification) }}</td>
              <td>{{ display(item.batchNo) }}</td>
              <td>{{ display(item.quantity) }} {{ item.unit || '' }}</td>
              <td>{{ display(item.qualifiedQuantity) }}</td>
              <td>{{ display(item.rejectedQuantity) }}</td>
            </tr>
          </tbody>
        </table>
        <div class="linked-print-note">
          检验说明：当前系统无独立 IQC 单，本报告依据采购入库/收货单的检验字段与物料明细生成。<br />
          检验备注：{{ display(info.inspectionRemark || info.remark) }}
        </div>
        <div class="linked-print-signs">
          <div>
            检验员：<span>{{ info.inspectorName }}</span>
          </div>
          <div>采购：<span></span></div>
          <div>审核：<span></span></div>
        </div>
      </section>

      <section v-else class="qr037-sheet">
        <header class="qr037-header">
          <div class="qr037-company">深圳市精捷信科技有限公司</div>
          <div class="qr037-title">进料检验报告</div>
          <div class="qr037-subtitle">Incoming Quality Report</div>
          <div class="qr037-rn">R.N：{{ info.inboundNo || '________' }}</div>
        </header>
        <table class="qr037-info-table">
          <tbody>
            <tr>
              <th>供应厂商</th>
              <td>{{ info.supplierName || '' }}</td>
              <th>品名规格</th>
              <td>{{ materialDescriptions }}</td>
              <th>来料批量</th>
              <td>{{ incomingQuantity }}</td>
            </tr>
            <tr>
              <th>验收单号</th>
              <td>{{ info.inboundNo || '' }}</td>
              <th>产品编号</th>
              <td>{{ materialCodes }}</td>
              <th>抽检数量</th>
              <td>{{ sampledQuantity }}</td>
            </tr>
            <tr>
              <th>日期</th>
              <td>{{ reportDate }}</td>
              <td colspan="4" class="sampling-plan">
                抽样计划：依据“MIL-STD-105D 抽样方案” II级抽取样板
              </td>
            </tr>
          </tbody>
        </table>

        <table class="qr037-aql-table">
          <thead>
            <tr>
              <th>AQL（质量允收水准）</th>
              <th>CR（致命）</th>
              <th>MA（主要）</th>
              <th>MI（次要）</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <th>AC（接受）</th>
              <td></td>
              <td></td>
              <td></td>
            </tr>
            <tr>
              <th>RE（拒收）</th>
              <td></td>
              <td></td>
              <td></td>
            </tr>
          </tbody>
        </table>

        <table class="qr037-result-table">
          <colgroup>
            <col class="col-item" />
            <col class="col-method" />
            <col class="col-device" />
            <col class="col-record" />
            <col class="col-defect" />
            <col class="col-defect" />
            <col class="col-defect" />
          </colgroup>
          <thead>
            <tr>
              <th rowspan="2">检验项目</th>
              <th rowspan="2">检验方法</th>
              <th rowspan="2">设备</th>
              <th rowspan="2">检验记录</th>
              <th colspan="3">缺陷等级、数量</th>
            </tr>
            <tr>
              <th>CR</th>
              <th>MA</th>
              <th>MI</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <th>规格</th>
              <td>核对《采购订单》应与实物一致</td>
              <td>目视</td>
              <td></td>
              <td></td>
              <td></td>
              <td></td>
            </tr>
            <tr>
              <th>颜色</th>
              <td>比较样板或限度样板不应有明显偏差</td>
              <td>目视/样板</td>
              <td></td>
              <td></td>
              <td></td>
              <td></td>
            </tr>
            <tr>
              <th rowspan="2">外观</th>
              <td>
                将抽取样板置于正常环境下，以30cm之距离目视样板并比较标准及限度样板，不应有以下现象
              </td>
              <td>目视</td>
              <td></td>
              <td></td>
              <td></td>
              <td></td>
            </tr>
            <tr>
              <td>脏污、黑点、变形、折伤、刮伤、混料、晶点、毛边等</td>
              <td></td>
              <td></td>
              <td></td>
              <td></td>
              <td></td>
            </tr>
            <tr>
              <th rowspan="3">尺寸</th>
              <td>长度：±　mm</td>
              <td>钢直尺/卡尺</td>
              <td></td>
              <td></td>
              <td></td>
              <td></td>
            </tr>
            <tr>
              <td>宽度：±　mm</td>
              <td>钢直尺/卡尺</td>
              <td></td>
              <td></td>
              <td></td>
              <td></td>
            </tr>
            <tr>
              <td>厚度：±　mm</td>
              <td>千分尺</td>
              <td></td>
              <td></td>
              <td></td>
              <td></td>
            </tr>
            <tr>
              <th rowspan="2">特性</th>
              <td>1.附着力测试</td>
              <td>3M600胶</td>
              <td></td>
              <td></td>
              <td></td>
              <td></td>
            </tr>
            <tr>
              <td>2.其它</td>
              <td></td>
              <td></td>
              <td></td>
              <td></td>
              <td></td>
            </tr>
            <tr>
              <th rowspan="4">包装、标识</th>
              <td>不应有散乱、变形</td>
              <td>目视</td>
              <td></td>
              <td></td>
              <td></td>
              <td></td>
            </tr>
            <tr>
              <td>标识应与实物及采购订单相符合</td>
              <td>目视</td>
              <td></td>
              <td></td>
              <td></td>
              <td></td>
            </tr>
            <tr>
              <td>不应有混料或明显短缺</td>
              <td>目视</td>
              <td></td>
              <td></td>
              <td></td>
              <td></td>
            </tr>
            <tr>
              <td>应符合环保标识</td>
              <td>目视</td>
              <td></td>
              <td></td>
              <td></td>
              <td></td>
            </tr>
            <tr class="total-row">
              <th colspan="4">TOTAL</th>
              <td></td>
              <td></td>
              <td>{{ rejectedTotal }}</td>
            </tr>
          </tbody>
        </table>
        <div class="qr037-decision">
          结果判定：<span>{{ checkbox('pass') }}PASS（合格）</span>
          <span>{{ checkbox('fail') }}No-conformity(不合格）（□退货 □来厂重工）</span>
          <span>{{ checkbox('other') }}Other(其它）</span>
        </div>
        <div class="qr037-remark">
          <strong>Remark（备注）：</strong>{{ info.inspectionRemark || info.remark || '' }}
        </div>
        <div class="qr037-signatures">
          <span
            >检验员：<em>{{ info.inspectorName || '' }}</em></span
          ><span>品质主管：<em></em></span>
        </div>
        <footer class="qr037-footer">JJX-QR-037</footer>
      </section>
    </A4Canvas>
    <div v-else v-loading="loading" class="linked-print-loading" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'
import { inboundApi } from '@/api/inventory/inbound'
import type { InboundVO } from '@/types/inventory/inbound'
import { InspectionResultEnum } from '@/enums/inventory/InboundEnum'
import { InspectionResult } from '@/enums/quality/InspectionEnum'
import { display, logTemplatePrint } from './shared'
import './print-common.css'

type PrintMode = 'system' | 'paper'
type Decision = 'pass' | 'fail' | 'other'
const route = useRoute(),
  router = useRouter()
const info = ref<InboundVO | null>(null),
  loading = ref(false),
  printing = ref(false)
const mode = computed<PrintMode>(() => (route.query.mode === 'paper' ? 'paper' : 'system'))
const reportDate = computed(
  () => info.value?.inboundDate || info.value?.createTime?.slice(0, 10) || ''
)
const inspectionLabel = computed(() =>
  info.value?.inspectionResult ? InspectionResultEnum.getLabel(info.value.inspectionResult) : '-'
)
const incomingQuantity = computed(() => sumItems('quantity') ?? '')
const sampledQuantity = computed(() => {
  const qualified = sumItems('qualifiedQuantity'),
    rejected = sumItems('rejectedQuantity')
  return qualified === undefined && rejected === undefined ? '' : (qualified || 0) + (rejected || 0)
})
const rejectedTotal = computed(() => sumItems('rejectedQuantity') ?? '')
const materialDescriptions = computed(() =>
  compactItemText((item) => [item.materialName, item.specification].filter(Boolean).join(' / '))
)
const materialCodes = computed(() => compactItemText((item) => item.materialCode || ''))

function sumItems(field: 'quantity' | 'qualifiedQuantity' | 'rejectedQuantity') {
  const values = (info.value?.items || [])
    .map((item) => item[field])
    .filter((value): value is number => value !== null && value !== undefined)
  return values.length ? values.reduce((sum, value) => sum + Number(value), 0) : undefined
}
function compactItemText(getText: (item: InboundVO['items'][number]) => string) {
  const texts = (info.value?.items || []).map(getText).filter(Boolean),
    shown = texts.slice(0, 3)
  return texts.length > 3 ? `${shown.join('；')}；等${texts.length}项` : shown.join('；')
}
function checkbox(decision: Decision) {
  const result = info.value?.inspectionResult
  if (!result || !InspectionResultEnum.canDo(result)) return '□'
  const checked =
    decision === 'pass'
      ? result === InspectionResult.PASS
      : decision === 'fail'
        ? result === InspectionResult.FAIL
        : result !== InspectionResult.PASS && result !== InspectionResult.FAIL
  return checked ? '☑' : '□'
}
function changeMode(value: string | number | boolean | undefined) {
  const nextMode: PrintMode = value === 'paper' ? 'paper' : 'system'
  if (nextMode !== mode.value) router.replace({ query: { ...route.query, mode: nextMode } })
}
onMounted(async () => {
  if (route.query.mode !== 'system' && route.query.mode !== 'paper') {
    await router.replace({ query: { ...route.query, mode: 'system' } })
  }
  const id = String(route.query.inboundId || '')
  if (!id) return ElMessage.error('缺少有效的采购收货单ID')
  loading.value = true
  try {
    info.value = (await inboundApi.getById(id)).data
  } catch (error: any) {
    ElMessage.error(error?.message || '加载失败')
  } finally {
    loading.value = false
  }
})
async function print() {
  printing.value = true
  try {
    await logTemplatePrint('JJX-QR-037')
    window.print()
  } catch (error: any) {
    ElMessage.error(error?.message || '打印留痕失败')
  } finally {
    printing.value = false
  }
}
</script>

<style scoped>
.iqc-print-page :deep(.a4-canvas) {
  position: relative;
}
.linked-print-toolbar {
  gap: 14px;
}
.linked-print-toolbar > :nth-child(2) {
  margin-left: auto;
}
.system-sheet,
.qr037-sheet {
  min-height: 270mm;
  box-sizing: border-box;
}
.qr037-sheet {
  position: relative;
  color: #000;
  font:
    11px/1.35 SimSun,
    '宋体',
    serif;
  padding-bottom: 18px;
}
.qr037-header {
  position: relative;
  text-align: center;
  min-height: 75px;
}
.qr037-company {
  font-size: 19px;
  font-weight: 700;
  letter-spacing: 2px;
}
.qr037-title {
  margin-top: 8px;
  font:
    700 23px/1.1 SimHei,
    '黑体',
    sans-serif;
  letter-spacing: 5px;
}
.qr037-subtitle {
  margin-top: 2px;
  font-size: 11px;
}
.qr037-rn {
  position: absolute;
  right: 0;
  bottom: 4px;
  min-width: 150px;
  text-align: left;
}
.qr037-sheet table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
}
.qr037-sheet th,
.qr037-sheet td {
  border: 1px solid #000;
  padding: 3px 5px;
  vertical-align: middle;
  overflow-wrap: anywhere;
}
.qr037-info-table th {
  width: 70px;
  text-align: center;
}
.qr037-info-table td {
  height: 28px;
}
.qr037-info-table td:nth-child(2) {
  width: 116px;
}
.qr037-info-table td:nth-child(4) {
  width: 220px;
  font-size: 9px;
}
.sampling-plan {
  font-size: 11px !important;
}
.qr037-aql-table {
  margin-top: 7px;
  text-align: center;
}
.qr037-aql-table th,
.qr037-aql-table td {
  height: 21px;
}
.qr037-aql-table th:first-child {
  width: 34%;
}
.qr037-result-table {
  margin-top: 7px;
  text-align: center;
}
.qr037-result-table .col-item {
  width: 10%;
}
.qr037-result-table .col-method {
  width: 45%;
}
.qr037-result-table .col-device {
  width: 15%;
}
.qr037-result-table .col-record {
  width: 12%;
}
.qr037-result-table .col-defect {
  width: 6%;
}
.qr037-result-table th,
.qr037-result-table td {
  height: 28px;
}
.qr037-result-table thead th {
  height: 22px;
}
.qr037-result-table .total-row th,
.qr037-result-table .total-row td {
  height: 24px;
  font-weight: 700;
}
.qr037-decision {
  display: flex;
  flex-wrap: wrap;
  gap: 5px 17px;
  min-height: 31px;
  align-items: center;
  padding: 3px 6px;
  border: 1px solid #000;
  border-top: 0;
}
.qr037-decision span {
  white-space: nowrap;
}
.qr037-remark {
  min-height: 47px;
  padding: 6px;
  border: 1px solid #000;
  border-top: 0;
}
.qr037-signatures {
  display: flex;
  justify-content: flex-end;
  gap: 55px;
  margin-top: 16px;
  font-size: 12px;
}
.qr037-signatures em {
  display: inline-block;
  width: 95px;
  min-height: 17px;
  border-bottom: 1px solid #000;
  font-style: normal;
  text-align: center;
}
.qr037-footer {
  position: absolute;
  right: 0;
  bottom: 0;
  font-size: 11px;
}
@media print {
  .iqc-print-page {
    padding: 0;
    background: #fff;
  }
}
</style>
