<template>
  <div class="production-work-card">
    <!-- 工具栏（打印时隐藏） -->
    <div class="card-toolbar no-print">
      <div class="toolbar-left">
        <span class="toolbar-tip">{{ info?.orderNo || '' }} 生产随工单</span>
      </div>
      <el-button type="primary" icon="Printer" @click="handlePrint">打印</el-button>
    </div>

    <!-- A4 画布 -->
    <A4Canvas :padding-mm="12" :scale="0.82" v-if="info">
      <!-- 标题 + 工单二维码（扫码枪扫工单号定位，2026-08-12 DEV-001） -->
      <div class="doc-title-row">
        <div class="doc-title">生 产 随 工 单</div>
        <img v-if="qrDataUrl" :src="qrDataUrl" class="doc-qrcode" alt="工单二维码" title="扫码定位工单" />
      </div>

      <!-- 工单头信息 -->
      <div class="doc-info">
        <div class="info-item"><span class="info-label">工单号</span>{{ info.orderNo || '-' }}</div>
        <div class="info-item"><span class="info-label">来源销售单</span>{{ info.salesOrderNo || '-' }}</div>
        <div class="info-item"><span class="info-label">产品编码</span>{{ info.productCode || '-' }}</div>
        <div class="info-item"><span class="info-label">产品名称</span>{{ info.productName || '-' }}</div>
        <div class="info-item"><span class="info-label">产品规格</span>{{ info.productSpec || '-' }}</div>
        <div class="info-item"><span class="info-label">产品单位</span>{{ info.productUnit || '-' }}</div>
        <div class="info-item"><span class="info-label">计划开始</span>{{ info.planStartDate || '-' }}</div>
        <div class="info-item"><span class="info-label">计划结束</span>{{ info.planEndDate || '-' }}</div>
        <div class="info-item"><span class="info-label">工艺路线</span>{{ routingText }}</div>
        <div class="info-item"><span class="info-label">单据状态</span>{{ info.orderStatusDesc || '-' }}</div>
      </div>

      <!-- 数量汇总 -->
      <div class="doc-summary">
        <div class="summary-item">
          <span class="summary-label">计划数量</span>
          <span class="summary-value">{{ fmtNum(info.plannedQuantity) }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">已完成</span>
          <span class="summary-value">{{ fmtNum(info.completedQuantity) }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">剩余</span>
          <span class="summary-value">{{ fmtNum(info.remainingQuantity) }}</span>
        </div>
        <div class="summary-item">
          <span class="summary-label">进度</span>
          <span class="summary-value">{{ info.completionPercentage != null ? info.completionPercentage + '%' : '-' }}</span>
        </div>
      </div>

      <!-- 工序明细表 -->
      <div class="section-title">一、工序明细（作业指导）</div>
      <table class="doc-table">
        <thead>
          <tr>
            <th style="width: 40px">序号</th>
            <th>工序名称</th>
            <th>工艺参数 / 作业要求</th>
            <th style="width: 90px">状态</th>
            <th style="width: 80px">合格数</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(p, i) in processList" :key="i">
            <td class="center">{{ p.processOrder ?? i + 1 }}</td>
            <td>{{ p.processName || '-' }}</td>
            <td class="params-cell">{{ p.processParams || '—' }}</td>
            <td class="center">{{ p.statusText || '-' }}</td>
            <td class="center">{{ p.qualifiedQuantity != null ? fmtNum(p.qualifiedQuantity) : '-' }}</td>
          </tr>
          <tr v-if="processList.length === 0">
            <td colspan="5" class="center empty">暂无工序明细</td>
          </tr>
        </tbody>
      </table>

      <!-- 领料明细（2026-08-18：加需求/已领/剩余列 + 追加领料入口） -->
      <div class="section-title pick-title">
        <span>二、领料明细</span>
        <el-button
          v-if="canPick"
          type="primary"
          size="small"
          v-hasPermi="['inventory:outbound:add']"
          @click="openPickDialog"
          >追加领料</el-button
        >
      </div>
      <table class="doc-table">
        <thead>
          <tr>
            <th style="width: 40px">序号</th>
            <th>物料编码</th>
            <th>物料名称</th>
            <th style="width: 70px">需求</th>
            <th style="width: 70px">已领</th>
            <th style="width: 70px">剩余</th>
            <th style="width: 55px">单位</th>
            <th style="width: 100px">批次</th>
            <th style="width: 85px">库位</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(m, i) in pickRows" :key="i">
            <td class="center">{{ i + 1 }}</td>
            <td>{{ m.materialCode || '-' }}</td>
            <td>{{ m.materialName || '-' }}</td>
            <td class="center">{{ fmtNum(m.demand) }}</td>
            <td class="center">{{ fmtNum(m.picked) }}</td>
            <td class="center">{{ fmtNum(m.remaining) }}</td>
            <td class="center">{{ m.unit || '-' }}</td>
            <td class="center">{{ m.batchNo || '-' }}</td>
            <td class="center">{{ m.locationCode || m.locationName || '-' }}</td>
          </tr>
          <tr v-if="pickRows.length === 0">
            <td colspan="9" class="center empty">暂无领料记录</td>
          </tr>
        </tbody>
      </table>

      <!-- 追加领料弹窗 -->
      <el-dialog v-model="pickDialogVisible" title="追加领料" width="720px" append-to-body>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="按剩余可领量补领（累计领料不超过 BOM 需求量）"
          style="margin-bottom: 12px"
        />
        <el-table :data="pickRows.filter((r) => Number(r.remaining) > 0)" border size="small" max-height="360">
          <el-table-column type="selection" width="42" />
          <el-table-column prop="materialCode" label="物料编码" width="140" />
          <el-table-column prop="materialName" label="物料名称" min-width="150" show-overflow-tooltip />
          <el-table-column prop="remaining" label="剩余可领" width="90" align="right" />
          <el-table-column label="追加数量" width="130" align="center">
            <template #default="{ row }">
              <el-input-number
                v-model="row.pickQty"
                :min="0"
                :max="Number(row.remaining)"
                size="small"
                controls-position="right"
                style="width: 110px"
              />
            </template>
          </el-table-column>
        </el-table>
        <template #footer>
          <el-button @click="pickDialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="pickSubmitting" @click="submitPick">确认追加</el-button>
        </template>
      </el-dialog>

      <!-- 质检结果 -->
      <div class="section-title">三、质检记录</div>
      <table class="doc-table">
        <thead>
          <tr>
            <th style="width: 60px">类型</th>
            <th style="width: 100px">检验单号</th>
            <th style="width: 80px">结果</th>
            <th style="width: 70px">总数</th>
            <th style="width: 70px">合格</th>
            <th style="width: 70px">不良</th>
            <th>检验员</th>
            <th style="width: 120px">检验时间</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(q, i) in qualityList" :key="i">
            <td class="center">{{ q.inspectionTypeName || q.inspectionType || '-' }}</td>
            <td class="center">{{ q.inspectionNo || '-' }}</td>
            <td class="center">{{ q.resultName || q.result || '-' }}</td>
            <td class="center">{{ q.totalQty != null ? q.totalQty : '-' }}</td>
            <td class="center">{{ q.passQty != null ? q.passQty : '-' }}</td>
            <td class="center">{{ q.failQty != null ? q.failQty : '-' }}</td>
            <td class="center">{{ q.inspector || '-' }}</td>
            <td class="center">{{ fmtDateTime(q.inspectTime) }}</td>
          </tr>
          <tr v-if="qualityList.length === 0">
            <td colspan="8" class="center empty">暂无质检记录</td>
          </tr>
        </tbody>
      </table>

      <!-- 备注 -->
      <div v-if="info.remark" class="doc-remark">备注：{{ info.remark }}</div>

      <!-- 签字区 -->
      <div class="doc-signs">
        <div class="sign-item">
          <div class="sign-line">操作工：</div>
          <div class="sign-underline"></div>
        </div>
        <div class="sign-item">
          <div class="sign-line">质检员：</div>
          <div class="sign-underline"></div>
        </div>
        <div class="sign-item">
          <div class="sign-line">仓管员：</div>
          <div class="sign-underline"></div>
        </div>
        <div class="sign-item">
          <div class="sign-line">日期：</div>
          <div class="sign-underline"></div>
        </div>
      </div>
    </A4Canvas>

    <div v-else v-loading="loading" style="height: 400px"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getProductionOrderDetail } from '@/api/production/order'
import { operationExecutionApi } from '@/api/production/operationExecution'
import { productRouteApi } from '@/api/product/routing'
import { outboundApi } from '@/api/inventory/outbound'
import { qualityApi } from '@/api/production/quality'
import A4Canvas from '@/components/A4Canvas/index.vue'
import QRCode from 'qrcode'

const props = defineProps<{
  orderId: string | number
}>()

const info = ref<any>(null)
const loading = ref(false)
const executions = ref<any[]>([])
const routeItems = ref<any[]>([])
const outbounds = ref<any[]>([])
const qualities = ref<any[]>([])
const qrDataUrl = ref('')

/** 工艺路线文本 */
const routingText = computed(() => {
  if (!info.value) return '-'
  const parts: string[] = []
  if (info.value.routingCode) parts.push(info.value.routingCode)
  if (info.value.routingName && info.value.routingName !== info.value.routingCode) {
    parts.push(info.value.routingName)
  }
  return parts.length ? parts.join(' ') : '-'
})

/** 工序明细：优先工序执行记录（实际工艺参数），回退工艺路线工序（定制工艺参数） */
const processList = computed(() => {
  if (executions.value.length > 0) {
    return executions.value
      .slice()
      .sort((a, b) => (a.processOrder ?? 0) - (b.processOrder ?? 0))
      .map((e) => ({
        processOrder: e.processOrder,
        processName: e.processName || e.processCode || '-',
        processParams: parseParams(e.actualProcessParams) || e.customProcessParams || e.processName || '',
        statusText: e.executionStatusDesc || '',
        qualifiedQuantity: e.qualifiedQuantity,
      }))
  }
  if (routeItems.value.length > 0) {
    return routeItems.value
      .slice()
      .sort((a, b) => (a.processOrder ?? 0) - (b.processOrder ?? 0))
      .map((r) => ({
        processOrder: r.processOrder,
        processName: r.processName || r.processCode || '-',
        processParams: parseParams(r.customProcessParams) || r.description || '',
        statusText: '',
        qualifiedQuantity: null,
      }))
  }
  return []
})

/** 领料明细：合并所有 PICK 出库单明细（批次/库位取最后一条） */
const materialList = computed(() => {
  const rows: any[] = []
  for (const ob of outbounds.value) {
    const items = ob.items || []
    for (const it of items) {
      rows.push({
        materialCode: it.materialCode,
        materialName: it.materialName,
        quantity: it.quantity,
        unit: it.unit,
        batchNo: it.batchNo,
        locationCode: it.locationCode,
        locationName: it.locationName,
      })
    }
  }
  return rows
})

/** 2026-08-18：领料汇总视图 = 剩余可领量(需求/已领/剩余) + 已领明细批次/库位 */
const remainingRows = ref<any[]>([])
const pickRows = computed(() => {
  const batchMap = new Map<string, any>()
  for (const m of materialList.value) {
    batchMap.set(m.materialCode, m) // 批次/库位取最后一次领料
  }
  return remainingRows.value.map((r: any) => ({
    ...r,
    batchNo: batchMap.get(r.materialCode)?.batchNo || '-',
    locationCode: batchMap.get(r.materialCode)?.locationCode,
    locationName: batchMap.get(r.materialCode)?.locationName,
  }))
})

// 追加领料（2026-08-18 P1-1：补上前端缺失的追加领料入口）
const pickDialogVisible = ref(false)
const pickSubmitting = ref(false)
const canPick = ref(false)

async function loadPickRemaining() {
  try {
    const { materialPickApi } = await import('@/api/inventory/materialPick')
    const res: any = await materialPickApi.getPickRemaining(Number(props.orderId))
    const list: any[] = res?.data || []
    remainingRows.value = list.map((r) => ({ ...r, pickQty: Number(r.remaining) > 0 ? Number(r.remaining) : 0 }))
    canPick.value = list.some((r) => Number(r.remaining) > 0)
  } catch {
    remainingRows.value = []
    canPick.value = false
  }
}

function openPickDialog() {
  pickDialogVisible.value = true
}

async function submitPick() {
  const items = pickRows.value
    .filter((r) => Number(r.pickQty) > 0)
    .map((r) => ({
      materialId: r.materialId,
      materialCode: r.materialCode,
      materialName: r.materialName,
      quantity: r.pickQty,
    }))
  if (items.length === 0) {
    ElMessage.warning('请选择要追加领料的物料并填写数量')
    return
  }
  pickSubmitting.value = true
  try {
    const { materialPickApi } = await import('@/api/inventory/materialPick')
    await materialPickApi.createProductionPick(Number(props.orderId), items)
    ElMessage.success(`追加领料成功，生成领料单（${items.length} 个物料）`)
    pickDialogVisible.value = false
    await loadData()
  } catch (e: any) {
    ElMessage.error(e?.message || '追加领料失败')
  } finally {
    pickSubmitting.value = false
  }
}

const qualityList = computed(() => qualities.value || [])

/** 解析 JSON 工艺参数 → 可读文本 */
function parseParams(raw?: string): string {
  if (!raw) return ''
  const t = raw.trim()
  if (!t) return ''
  try {
    const obj = JSON.parse(t)
    if (typeof obj === 'string') return obj
    if (Array.isArray(obj)) {
      return obj
        .map((item) => {
          if (typeof item === 'string') return item
          if (item && typeof item === 'object') {
            return Object.entries(item)
              .map(([k, v]) => `${k}: ${v}`)
              .join('；')
          }
          return String(item)
        })
        .filter(Boolean)
        .join('；')
    }
    if (typeof obj === 'object' && obj !== null) {
      return Object.entries(obj)
        .map(([k, v]) => `${k}: ${v}`)
        .join('；')
    }
    return t
  } catch {
    return t
  }
}

const fmtNum = (v?: number | string | null): string => {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  return Number.isNaN(n) ? String(v) : n.toLocaleString('zh-CN')
}

const fmtDateTime = (v?: string): string => {
  if (!v) return '-'
  return String(v).replace('T', ' ').slice(0, 16)
}

async function loadData() {
  const orderId = String(props.orderId)
  if (!orderId) {
    ElMessage.error('缺少工单ID')
    return
  }
  loading.value = true
  try {
    // 1. 工单详情
    const res: any = await getProductionOrderDetail(orderId)
    if (res.code === 200 && res.data) {
      info.value = res.data
    } else {
      ElMessage.error(res.msg || '加载工单失败')
      return
    }

    // 2. 工序执行记录（含实际工艺参数）
    try {
      const execRes: any = await operationExecutionApi.getByOrderId(Number(orderId))
      executions.value = execRes?.data || []
    } catch {
      executions.value = []
    }

    // 3. 若没有执行记录，取工艺路线工序（定制工艺参数）
    if (executions.value.length === 0 && info.value?.routingId) {
      try {
        const routeRes: any = await productRouteApi.getProductRouteInfo(info.value.routingId)
        const rv: any = routeRes?.data
        routeItems.value = rv?.items || []
        if (!info.value.routingName && rv?.routingName) {
          info.value = { ...info.value, routingName: rv.routingName }
        }
      } catch {
        routeItems.value = []
      }
    }

    // 4. 领料单（PICK：sourceType=PRODUCTION/work_order）
    try {
      const obRes: any = await outboundApi.list({
        current: 1,
        pageSize: 20,
        sourceType: 'PRODUCTION',
      })
      const list: any[] = obRes?.data?.records || obRes?.data || []
      outbounds.value = list.filter((o) => String(o.sourceId) === String(orderId))
    } catch {
      outbounds.value = []
    }

    // 4.1 剩余可领量（2026-08-18：需求/已领/剩余 + 追加领料入口）
    await loadPickRemaining()

    // 5. 质检记录
    try {
      const qRes: any = await qualityApi.page({ pageNum: 1, pageSize: 20, orderId: Number(orderId) })
      qualities.value = qRes?.data?.records || []
    } catch {
      qualities.value = []
    }
  } catch (e) {
    console.error('加载随工单失败:', e)
    ElMessage.error('加载随工单失败')
  } finally {
    loading.value = false
  }
}

function handlePrint() {
  window.print()
}

/** 生成工单二维码（内容=工单号，扫码枪识别后定位工单） */
async function genQr() {
  if (!info.value?.orderNo) return
  try {
    qrDataUrl.value = await QRCode.toDataURL(info.value.orderNo, { width: 96, margin: 1 })
  } catch {
    qrDataUrl.value = ''
  }
}

onMounted(async () => {
  await loadData()
  await genQr()
})
</script>

<style scoped>
.production-work-card {
  min-height: 100vh;
  background: #eef0f3;
  padding: 20px;
}

.card-toolbar {
  max-width: 794px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toolbar-tip {
  font-size: 14px;
  color: #606266;
}

/* 单据标题行（标题居中 + 二维码右上角，DEV-001） */
.doc-title-row {
  position: relative;
  padding-right: 96px; /* 给右侧二维码留位，避免遮挡标题 */
}

/* 工单二维码（扫码枪扫工单号定位） */
.doc-qrcode {
  position: absolute;
  top: 0;
  right: 0;
  width: 72px;
  height: 72px;
  border: 1px solid #dcdfe6;
  padding: 3px;
  background: #fff;
}

/* 单据标题 */
.doc-title {
  text-align: center;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 6px;
  margin: 4px 0 12px;
  padding-bottom: 8px;
  border-bottom: 2px solid #2b5aa7;
  color: #2b5aa7;
}

/* 信息区 */
.doc-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 24px;
  margin-bottom: 10px;
  font-size: 11px;
}

.info-item {
  display: flex;
}

.info-label {
  width: 76px;
  color: #888;
  flex-shrink: 0;
}

/* 数量汇总 */
.doc-summary {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  margin-bottom: 14px;
}

.summary-item {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  padding: 6px 10px;
  text-align: center;
  background: #f7f9fc;
}

.summary-label {
  display: block;
  font-size: 10px;
  color: #888;
}

.summary-value {
  display: block;
  font-size: 14px;
  font-weight: 700;
  color: #2b5aa7;
  margin-top: 2px;
}

/* 区块标题 */
.pick-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.section-title {
  font-size: 12px;
  font-weight: 700;
  color: #2b5aa7;
  margin: 12px 0 6px;
  padding-left: 8px;
  border-left: 3px solid #2b5aa7;
}

/* 表格 */
.doc-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 10.5px;
  margin-bottom: 6px;
}

.doc-table th,
.doc-table td {
  border: 1px solid #999;
  padding: 4px 6px;
  text-align: left;
  word-break: break-all;
}

.doc-table th {
  background: #eef2f8;
  font-weight: 600;
  white-space: nowrap;
}

.doc-table .center {
  text-align: center;
}

.doc-table .params-cell {
  color: #444;
}

.doc-table .empty {
  color: #aaa;
  padding: 14px 0;
}

/* 备注 */
.doc-remark {
  font-size: 10px;
  color: #555;
  margin: 10px 0;
}

/* 签字区 */
.doc-signs {
  display: flex;
  justify-content: space-between;
  margin-top: 34px;
  padding: 0 12px;
}

.sign-item {
  width: 22%;
  text-align: center;
  font-size: 11px;
}

.sign-line {
  padding-bottom: 4px;
}

.sign-underline {
  border-bottom: 1px solid #999;
}

@media print {
  .no-print {
    display: none !important;
  }

  .production-work-card {
    padding: 0;
    background: #fff;
  }
}
</style>
