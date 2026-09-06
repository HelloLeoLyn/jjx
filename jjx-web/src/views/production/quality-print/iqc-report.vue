<template>
  <div class="linked-print-page iqc-print-page">
    <div class="linked-print-toolbar no-print">
      <el-button @click="router.back()">返回</el-button>
      <el-tag v-if="selectedItem" type="info">{{ selectedItem.materialCode }} {{ selectedItem.materialName }}</el-tag>
      <el-tag
        v-if="inspection"
        :type="QualityReviewStatusEnum.getTagProps(inspection.reviewStatus || '').type"
      >
        {{ QualityReviewStatusEnum.getLabel(inspection.reviewStatus || '') }}
      </el-tag>
      <el-button type="primary" :loading="printing" :disabled="!isOfficial" @click="print"
        >打印正式版</el-button
      >
    </div>

    <A4Canvas v-if="info" :padding-mm="11">
      <section class="qr037-sheet">
        <div v-if="!isOfficial" class="draft-watermark">非正式版·待审核</div>
        <header class="qr037-header">
          <div class="qr037-company">深圳市精捷信科技有限公司</div>
          <div class="qr037-title">进料检验报告</div>
          <div class="qr037-subtitle">Incoming Quality Report</div>
          <div class="qr037-rn">R.N：{{ inspection?.inspectionNo || '________' }}</div>
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
              <td>{{ selectedItem?.materialCode || '' }}</td>
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
            <tr v-for="check in inspection?.items || []" :key="check.itemId || check.checkItem">
              <th>{{ check.checkItem }}</th>
              <td>
                {{ check.standard || '' }}
                <small v-if="check.inspectionMethod">（{{ check.inspectionMethod }}）</small>
              </td>
              <td>{{ check.equipment || '' }}</td>
              <td>{{ check.actualValue || check.remark || '' }}</td>
              <td>{{ defectValue(check.crQuantity) }}</td>
              <td>{{ defectValue(check.maQuantity) }}</td>
              <td>{{ defectValue(check.miQuantity) }}</td>
            </tr>
            <tr v-if="!inspection?.items?.length">
              <td colspan="7">暂无结构化检验项目</td>
            </tr>
            <tr class="total-row">
              <th colspan="4">TOTAL</th>
              <td>{{ defectValue(crTotal) }}</td>
              <td>{{ defectValue(maTotal) }}</td>
              <td>{{ defectValue(miTotal) }}</td>
            </tr>
          </tbody>
        </table>
        <div class="qr037-decision">
          结果判定：<span>{{ checkbox('pass') }}PASS（合格）</span>
          <span>{{ checkbox('fail') }}No-conformity(不合格）（□退货 □来厂重工）</span>
          <span>{{ checkbox('other') }}Other(其它）</span>
        </div>
        <div class="qr037-remark">
          <strong>Remark（备注）：</strong
          >{{ inspection?.remark || inspection?.defectDesc || info.inspectionRemark || '' }}
        </div>
        <div class="qr037-signatures">
          <span
            >检验员：<em>{{ inspection?.inspector || info.inspectorName || '' }}</em></span
          ><span
            >品质主管：<em>{{ inspection?.reviewerName || '' }}</em></span
          >
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
import { inboundApi } from '@/api/inventory/inbound'
import type { InboundVO } from '@/types/inventory/inbound'
import { qualityApi, type QualityVO } from '@/api/production/quality'
import {
  InspectionResult,
  InspectionResultEnum as QualityInspectionResultEnum,
  QualityReviewStatus,
  QualityReviewStatusEnum,
} from '@/enums/quality/InspectionEnum'
import { logTemplatePrint } from './shared'
import './print-common.css'

type Decision = 'pass' | 'fail' | 'other'
const route = useRoute(),
  router = useRouter()
const info = ref<InboundVO | null>(null),
  inspection = ref<QualityVO | null>(null),
  loading = ref(false),
  printing = ref(false)
const reportDate = computed(
  () => info.value?.inboundDate || info.value?.createTime?.slice(0, 10) || ''
)
const selectedItem = computed(() =>
  (info.value?.items || []).find((item) => Number(item.inboundItemId || item.itemId) === Number(inspection.value?.sourceItemId))
)
const incomingQuantity = computed(() => selectedItem.value?.quantity ?? '')
const sampledQuantity = computed(
  () => inspection.value?.totalQty ?? selectedItem.value?.sampledQuantity ?? ''
)
const isOfficial = computed(() => inspection.value?.reviewStatus === QualityReviewStatus.APPROVED)
const rejectedTotal = computed(
  () => inspection.value?.failQty ?? selectedItem.value?.rejectedQuantity ?? ''
)
const crTotal = computed(() => sumDefects('crQuantity'))
const maTotal = computed(() => sumDefects('maQuantity'))
const miTotal = computed(() => sumDefects('miQuantity'))
const materialDescriptions = computed(() =>
  [selectedItem.value?.materialName, selectedItem.value?.specification].filter(Boolean).join(' / ')
)

function sumDefects(field: 'crQuantity' | 'maQuantity' | 'miQuantity') {
  return (inspection.value?.items || []).reduce((sum, item) => sum + Number(item[field] || 0), 0)
}
function defectValue(value?: number) {
  return Number(value || 0) || ''
}

function checkbox(decision: Decision) {
  const result = inspection.value?.result
  if (!result || !QualityInspectionResultEnum.canDo(result)) return '□'
  const checked =
    decision === 'pass'
      ? result === InspectionResult.PASS
      : decision === 'fail'
        ? result === InspectionResult.FAIL
        : result !== InspectionResult.PASS && result !== InspectionResult.FAIL
  return checked ? '☑' : '□'
}
onMounted(async () => {
  const id = String(route.query.inboundId || '')
  if (!id) return ElMessage.error('缺少有效的采购收货单ID')
  loading.value = true
  try {
    info.value = (await inboundApi.getById(id)).data
    const requestedId = Number(route.query.inspectionId)
    if (requestedId) await loadInspection(requestedId)
    else ElMessage.warning('当前未指定单项 IQC 检验单')
  } catch (error: any) {
    ElMessage.error(error?.message || '加载失败')
  } finally {
    loading.value = false
  }
})
async function loadInspection(id: number) {
  inspection.value = (await qualityApi.getById(id)).data
}
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
.qr037-sheet {
  min-height: 270mm;
  box-sizing: border-box;
}
.draft-watermark {
  position: absolute;
  top: 45%;
  left: 18%;
  z-index: 2;
  transform: rotate(-28deg);
  color: rgb(180 0 0 / 16%);
  font-size: 54px;
  font-weight: 700;
  pointer-events: none;
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
