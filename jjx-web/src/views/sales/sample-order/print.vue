<template>
  <div class="sample-print-page">
    <!-- 工具栏（打印时隐藏） -->
    <div class="print-toolbar no-print">
      <div class="toolbar-left">
        <el-button @click="router.back()">返回</el-button>
        <el-radio-group v-model="layout">
          <el-radio-button value="sample">样品单</el-radio-button>
          <el-radio-button value="requisition">样品需求单</el-radio-button>
        </el-radio-group>
        <span class="toolbar-tip">打印预览 - {{ info?.orderNo || '' }}</span>
      </div>
      <el-button type="primary" icon="Printer" @click="handlePrint">打印</el-button>
    </div>

    <!-- ==================== 版式二：样品需求单（QR-065） ==================== -->
    <A4Canvas v-if="info && layout === 'requisition'" :padding-mm="15">
      <PrintCompanyHeader variant="center" />

      <div class="doc-title">样 品 需 求 单</div>

      <table class="req-table">
        <tbody>
          <tr>
            <th>客户</th>
            <td>{{ info.customerName || '' }}</td>
            <th>落单日期</th>
            <td>{{ formatDay(info.orderDate) }}</td>
          </tr>
          <tr>
            <th>品名 (料号)</th>
            <td>{{ info.sampleProductName || productCode || '' }}</td>
            <th>交期</th>
            <td>{{ formatDay(info.deliveryDate) }}</td>
          </tr>
          <tr>
            <th>机种编号</th>
            <td>{{ productCode || '' }}</td>
            <th>客供资料</th>
            <td></td>
          </tr>
          <tr>
            <th>需求数量</th>
            <td>{{ info.sampleQty ?? info.totalQuantity ?? '' }}</td>
            <th>检验报告</th>
            <td></td>
          </tr>
          <tr>
            <th>订单号码</th>
            <td></td>
            <th>承认书</th>
            <td>份</td>
          </tr>
          <tr>
            <th>特别要求</th>
            <td colspan="3" class="multiline-content">{{ info.remark || '' }}</td>
          </tr>
        </tbody>
      </table>

      <!-- 制样记录（印刷 / 加工冲型） -->
      <table class="craft-table">
        <thead>
          <tr>
            <th colspan="2">印刷制样记录</th>
            <th colspan="2">加工冲型制样记录</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td class="craft-label">出现不良原因及改善</td>
            <td class="craft-body">
              <div v-for="r in printDefects" :key="r.id" class="craft-item">
                <div>原因：{{ r.defectReason }}</div>
                <div v-if="r.improvement">改善：{{ r.improvement }}</div>
              </div>
              <div v-if="!printDefects.length" class="craft-empty">&nbsp;</div>
            </td>
            <td class="craft-label">出现不良原因及改善</td>
            <td class="craft-body">
              <div v-for="r in punchDefects" :key="r.id" class="craft-item">
                <div>原因：{{ r.defectReason }}</div>
                <div v-if="r.improvement">改善：{{ r.improvement }}</div>
              </div>
              <div v-if="!punchDefects.length" class="craft-empty">&nbsp;</div>
            </td>
          </tr>
        </tbody>
      </table>

      <!-- 会签区（业务 / 核准 / 部门主管） -->
      <div class="sign-grid">
        <div v-for="s in signRoles" :key="s.role" class="sign-col">
          <div class="sign-col-title">{{ s.label }}</div>
          <template v-if="signOf(s.role)">
            <div class="sign-col-line">
              {{ signOf(s.role)?.approveResult === 1 ? '同意' : '不同意' }}
            </div>
            <div class="sign-col-meta">{{ signOf(s.role)?.signerName || '' }} {{ signOf(s.role)?.signTime || '' }}</div>
            <div v-if="signOf(s.role)?.comment" class="sign-col-comment">意见：{{ signOf(s.role)?.comment }}</div>
          </template>
          <template v-else>
            <div class="sign-col-blank">&nbsp;</div>
            <div class="sign-col-meta">未签</div>
          </template>
        </div>
      </div>
    </A4Canvas>

    <!-- ==================== 版式一：样品单 ==================== -->
    <A4Canvas v-else-if="info" :padding-mm="15">
      <PrintCompanyHeader variant="center" />

      <div class="doc-title">样 品 单</div>

      <div class="doc-info">
        <div class="info-item"><span class="info-label">样品单号</span>{{ info.orderNo }}</div>
        <div class="info-item"><span class="info-label">样品状态</span>{{ statusName }}</div>
        <div class="info-item"><span class="info-label">客户名称</span>{{ info.customerName || '-' }}</div>
        <div class="info-item"><span class="info-label">联系人</span>{{ info.contactPerson || '-' }}</div>
        <div class="info-item"><span class="info-label">来源报价</span>{{ info.quotationNo || '-' }}</div>
        <div class="info-item"><span class="info-label">迭代轮次</span>Round {{ info.sampleRound || 1 }}</div>
        <div class="info-item"><span class="info-label">打样数量</span>{{ info.sampleQty || '-' }}</div>
        <div class="info-item"><span class="info-label">送样日期</span>{{ info.sampleSendDate || '-' }}</div>
        <div class="info-item"><span class="info-label">快递单号</span>{{ info.sampleTrackingNo || '-' }}</div>
        <div class="info-item"><span class="info-label">客户确认</span>{{ info.sampleConfirmDate ? info.sampleConfirmDate + (info.sampleClientName ? ' / ' + info.sampleClientName : '') : '-' }}</div>
      </div>

      <div v-if="info.engineeringNote" class="doc-engineering">
        <div class="eng-title">工艺参数 / 工程备注</div>
        <div class="eng-content">{{ info.engineeringNote }}</div>
      </div>

      <div v-if="info.remark" class="doc-remark">备注：{{ info.remark }}</div>

      <div class="doc-signs">
        <div class="sign-item">
          <div class="sign-line">制单人：</div>
          <div class="sign-underline"></div>
        </div>
        <div class="sign-item">
          <div class="sign-line">客户确认：</div>
          <div class="sign-underline"></div>
        </div>
        <div class="sign-item">
          <div class="sign-line">日期：</div>
          <div class="sign-underline"></div>
        </div>
      </div>
    </A4Canvas>

    <div v-else v-loading="true" style="height: 400px"></div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { SampleOrderStatusEnum } from '@/enums/sales'
import {
  SampleCraftTypeEnum,
  SAMPLE_REQUISITION_SIGN_ROLES,
} from '@/enums/sales/SampleRequisitionEnum'
import type { SampleDefectRecord, SampleRequisitionSign } from '@/types/sales/sampleOrder'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'

const route = useRoute()
const router = useRouter()

const info = ref<any>(null)
const loading = ref(false)

// 版式：sample=样品单（默认）/ requisition=样品需求单（QR-065）
const layout = ref<'sample' | 'requisition'>(
  String(route.query.layout) === 'requisition' ? 'requisition' : 'sample'
)

const signRoles = SAMPLE_REQUISITION_SIGN_ROLES
const productCode = ref('')
const signs = ref<SampleRequisitionSign[]>([])
const defects = ref<SampleDefectRecord[]>([])

const statusName = computed(() => {
  const s = info.value?.sampleStatus
  if (s === undefined || s === null) return '-'
  return SampleOrderStatusEnum.getLabel(Number(s)) || String(s)
})

const printDefects = computed(() =>
  defects.value.filter((d) => d.craftType === SampleCraftTypeEnum.PRINT)
)
const punchDefects = computed(() =>
  defects.value.filter((d) => d.craftType === SampleCraftTypeEnum.PUNCH)
)

function signOf(role: string) {
  return signs.value.find((s) => s.signRole === role)
}

function formatDay(value?: string) {
  return value ? String(value).slice(0, 10) : ''
}

async function loadData() {
  const orderId = Number(route.params.id)
  if (!orderId) {
    ElMessage.error('缺少样品单ID')
    return
  }
  loading.value = true
  try {
    const res: any = await sampleOrderApi.getInfo(orderId)
    if (res.code === 200 && res.data) {
      info.value = res.data
      productCode.value = res.data.sampleProductCode || ''
    } else {
      ElMessage.error(res.msg || '加载样品单失败')
      return
    }
    // 机种编号=产品编码：作品明细兜底（样品单一对一产品）
    if (!productCode.value) {
      try {
        const p: any = await sampleOrderApi.getProducts(orderId)
        productCode.value = p?.data?.[0]?.productCode || ''
      } catch {
        /* 无产品明细不阻塞打印 */
      }
    }
    // 需求单用：会签记录 + 不良记录（失败不阻塞样品单版式）
    try {
      const s: any = await sampleOrderApi.listRequisitionSigns(orderId)
      signs.value = s?.data || []
    } catch {
      signs.value = []
    }
    try {
      const d: any = await sampleOrderApi.listDefects(orderId)
      defects.value = d?.data || []
    } catch {
      defects.value = []
    }
  } catch {
    ElMessage.error('加载样品单失败')
  } finally {
    loading.value = false
  }
}

function handlePrint() {
  window.print()
}

onMounted(async () => {
  await loadData()
})
</script>

<style scoped>
.sample-print-page {
  min-height: 100vh;
  background: #eef0f3;
  padding: 20px;
}

.print-toolbar {
  max-width: 794px;
  margin: 0 auto 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.toolbar-tip {
  font-size: 14px;
  color: #606266;
}

/* 画布内容样式 */
.doc-title {
  text-align: center;
  font-size: 18px;
  font-weight: 700;
  letter-spacing: 8px;
  margin: 14px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid var(--doc-theme, #2b5aa7);
}

/* ===== 样品需求单：表头字段表 ===== */
.req-table,
.craft-table {
  width: 100%;
  border-collapse: collapse;
  table-layout: fixed;
  font-size: 11px;
  margin-bottom: 10px;
}

.req-table th,
.req-table td,
.craft-table th,
.craft-table td {
  padding: 7px 8px;
  border: 1px solid #b8bec8;
  overflow-wrap: anywhere;
}

.req-table th {
  width: 13%;
  background: var(--doc-theme-soft, #eef3fa);
  color: #303133;
  font-weight: 600;
  text-align: center;
}

.craft-table th {
  background: var(--doc-theme-soft, #eef3fa);
  text-align: center;
  font-weight: 600;
}

.craft-label {
  width: 42px;
  text-align: center;
  color: #555;
  line-height: 1.4;
}

.craft-body {
  vertical-align: top;
  min-height: 90px;
}

.craft-item + .craft-item {
  margin-top: 6px;
  border-top: 1px dashed #dcdfe6;
  padding-top: 6px;
}

.craft-empty {
  height: 70px;
}

.multiline-content {
  min-height: 42px;
  white-space: pre-wrap;
}

/* ===== 会签区 ===== */
.sign-grid {
  display: flex;
  gap: 10px;
  margin-top: 14px;
}

.sign-col {
  flex: 1;
  border: 1px solid #b8bec8;
  font-size: 11px;
}

.sign-col-title {
  text-align: center;
  font-weight: 600;
  background: var(--doc-theme-soft, #eef3fa);
  padding: 4px;
  border-bottom: 1px solid #b8bec8;
}

.sign-col-line {
  text-align: center;
  padding: 6px;
  font-weight: 600;
}

.sign-col-meta {
  text-align: center;
  color: #606266;
  padding: 0 6px 6px;
}

.sign-col-comment {
  padding: 0 6px 6px;
  color: #555;
}

.sign-col-blank {
  height: 20px;
}

/* ===== 样品单版式沿用 ===== */
.doc-info {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px 24px;
  margin-bottom: 12px;
  font-size: 11px;
}

.info-item {
  display: flex;
}

.info-label {
  width: 70px;
  color: #888;
  flex-shrink: 0;
}

.doc-engineering {
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  margin-bottom: 12px;
  background: #f7f9fc;
}

.eng-title {
  background: var(--doc-theme, #2b5aa7);
  color: #fff;
  font-size: 11px;
  font-weight: 600;
  padding: 5px 10px;
}

.eng-content {
  font-size: 11px;
  color: #555;
  padding: 8px 10px;
  white-space: pre-wrap;
  line-height: 1.6;
}

.doc-remark {
  font-size: 10px;
  color: #555;
  margin-bottom: 20px;
}

.doc-signs {
  display: flex;
  justify-content: space-between;
  margin-top: 40px;
  padding: 0 20px;
}

.sign-item {
  width: 30%;
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

  .sample-print-page {
    padding: 0;
    background: #fff;
  }
}
</style>
