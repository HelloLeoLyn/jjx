<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" :inline="true" label-width="80px">
        <el-form-item label="客户名称" prop="customerName">
          <el-input
            v-model="queryParams.customerName"
            placeholder="请输入客户名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="样品状态" prop="sampleStatus">
          <el-select
            v-model="queryParams.sampleStatus"
            placeholder="请选择状态"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="s in statusOptions"
              :key="s.value"
              :label="s.label"
              :value="s.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮 -->
    <el-card class="operation-card" shadow="never">
      <el-button
        v-hasPermi="['sales:sample:add']"
        type="primary"
        plain
        icon="Plus"
        @click="showCreateDialog"
        >新增样品单</el-button
      >
    </el-card>

    <!-- 表格 -->
    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="sampleList" style="width: 100%" border stripe>
        <el-table-column label="样品单号" prop="orderNo" width="180">
          <template #default="scope">
            <el-link type="primary" @click="showDetail(scope.row)">{{ scope.row.orderNo }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="客户简称" prop="customerShortName" width="160">
          <template #default="scope">
            <el-link type="primary" @click="showCustDetail(scope.row)">{{
              scope.row.customerShortName || scope.row.customerName || '-'
            }}</el-link>
          </template>
        </el-table-column>
        <el-table-column label="样品状态" width="130">
          <template #default="scope">
            <el-tag :type="statusTagType(scope.row.sampleStatus)" size="small">
              {{ statusLabel(scope.row.sampleStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="工程接单" width="110" align="center">
          <template #default="scope">
            <template v-if="isEngineering(scope.row)">
              <el-tag v-if="scope.row.engineeringAcceptor" type="success" size="small">
                {{ scope.row.engineeringAcceptor }}
              </el-tag>
              <el-tag v-else type="warning" size="small">待接单</el-tag>
            </template>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="当前工序" width="110" align="center">
          <template #default="scope">
            <span>{{ scope.row.currentProcess || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="迭代轮次" width="90" align="center">
          <template #default="scope">Round {{ scope.row.sampleRound || 1 }}</template>
        </el-table-column>
        <el-table-column label="打样数量" width="90" align="center" prop="sampleQty" />
        <el-table-column label="快递单号" prop="sampleTrackingNo" min-width="140" />
        <el-table-column label="送样日期" width="110" prop="sampleSendDate" />
        <el-table-column label="确认人" prop="sampleClientName" width="100" />
        <el-table-column label="工程备注" min-width="160">
          <template #default="scope">
            <el-tooltip :content="scope.row.engineeringNote || '-'" placement="top">
              <span>{{
                scope.row.engineeringNote
                  ? scope.row.engineeringNote.substring(0, 20) +
                    (scope.row.engineeringNote.length > 20 ? '...' : '')
                  : '-'
              }}</span>
            </el-tooltip>
          </template>
        </el-table-column>
        <TableActionColumn
          :actions="sampleActions"
          min-width="200"
          display="text"
          @action="handleSampleAction"
        />
      </el-table>
      <pagination
        v-show="total > 0"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        :total="total"
        @pagination="getList"
      />
    </el-card>

    <!-- ===== 创建样品单弹窗 ===== -->
    <el-dialog
      :title="createEditId ? `编辑样品单（${createEditOrderNo}）` : '新增样品单'"
      v-model="createVisible"
      width="860px"
      append-to-body
      @close="resetCreateForm"
    >
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="110px">
        <el-form-item label="客户" prop="customerId">
          <el-select
            v-model="createForm.customerId"
            placeholder="请选择客户"
            filterable
            remote
            :remote-method="searchCustomers"
            :loading="customerSearching"
            :disabled="!!createForm.quotationId"
            style="width: 100%"
            @change="onCustomerChange"
          >
            <el-option
              v-for="c in customerOptions"
              :key="c.customerId"
              :label="c.customerName"
              :value="c.customerId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="来源报价单" v-if="!createEditId">
          <el-select
            v-model="createForm.quotationId"
            placeholder="可选：从单产品报价单带出客户和产品"
            filterable
            clearable
            style="width: 100%"
            @change="onQuotationChange"
          >
            <el-option
              v-for="q in quotationOptions"
              :key="q.quotationId"
              :label="`${q.quotationNo} - ${q.customerName} (${q.finalAmount}元)`"
              :value="q.quotationId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="打样产品" required>
          <el-select
            v-if="!createForm.quotationId"
            v-model="createForm.product.productId"
            filterable
            remote
            :remote-method="searchProducts"
            :loading="productSearching"
            :disabled="!createForm.customerId"
            placeholder="请先选择客户，再选择一个打样产品"
            style="width: 100%"
            @change="onProductSelect"
          >
            <el-option
              v-for="p in productOptions"
              :key="p.productId"
              :label="`${p.productCode} - ${p.productName}`"
              :value="p.productId"
            />
          </el-select>
          <el-input
            v-else
            :model-value="createForm.product.productName"
            readonly
            placeholder="从来源报价单带入"
          />
          <div style="color: #909399; font-size: 12px">一张样品单对应一个成品，可打样多件。</div>
          <el-alert
            v-if="quotationProductError"
            :title="quotationProductError"
            type="warning"
            :closable="false"
          />
        </el-form-item>
        <el-form-item label="产品编码">
          <el-input :model-value="createForm.product.productCode" readonly />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input :model-value="createForm.product.productName" readonly />
        </el-form-item>
        <el-form-item label="打样数量" prop="product.quantity">
          <el-input-number
            v-model="createForm.product.quantity"
            :min="1"
            :precision="0"
            controls-position="right"
          />
        </el-form-item>
        <el-form-item label="单位">
          <el-input v-model="createForm.product.unit" placeholder="PCS" />
        </el-form-item>
        <el-form-item label="期望交样日期">
          <el-date-picker
            v-model="createForm.deliveryDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="默认继承报价单交期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input
            v-model="createForm.contactPerson"
            placeholder="默认带出客户/报价单联系人"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input
            v-model="createForm.contactPhone"
            placeholder="默认带出客户/报价单电话"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="技术要求">
          <el-input
            v-model="createForm.techRequirement"
            type="textarea"
            :rows="3"
            placeholder="工程打样要求（材质/工艺/颜色/按键数/连接器等），将传承给打样工作台"
          />
        </el-form-item>
        <el-form-item label="备注">
          <el-input
            v-model="createForm.remark"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button
          type="primary"
          @click="submitCreate"
          :loading="creating"
          :disabled="quotationProductLoading || !!quotationProductError"
          >{{ createEditId ? '保存' : '创建' }}</el-button
        >
      </template>
    </el-dialog>

    <!-- ===== 详情弹窗（含工程区） ===== -->
    <el-dialog
      title="样品单详情"
      v-model="detailVisible"
      width="820px"
      append-to-body
      @open="onDetailOpen"
    >
      <template v-if="detailData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="样品单号" :span="2">{{
            detailData.orderNo
          }}</el-descriptions-item>
          <el-descriptions-item label="客户名称">{{
            detailData.customerName
          }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{
            detailData.contactPerson || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="样品状态">
            <el-tag :type="statusTagType(detailData.sampleStatus)" size="small">{{
              statusLabel(detailData.sampleStatus)
            }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="迭代轮次"
            >Round {{ detailData.sampleRound || 1 }}</el-descriptions-item
          >
          <el-descriptions-item label="打样数量">{{
            detailData.sampleQty || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="送样日期">{{
            detailData.sampleSendDate || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="快递单号">{{
            detailData.sampleTrackingNo || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="客户确认日期">{{
            detailData.sampleConfirmDate || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="客户确认人">{{
            detailData.sampleClientName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="转量产订单ID">{{
            detailData.convertedOrderId || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="转量产时间">{{
            detailData.convertOrderTime || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{
            detailData.remark || '-'
          }}</el-descriptions-item>
        </el-descriptions>

        <!-- 产品明细（DEV-781：报价转样品后详情展示） -->
        <el-divider content-position="left">产品明细</el-divider>
        <el-table
          v-if="detailProducts.length"
          :data="detailProducts"
          size="small"
          border
          stripe
          style="width: 100%"
        >
          <el-table-column prop="productCode" label="产品编码" width="120" />
          <el-table-column prop="productName" label="产品名称" min-width="140" />
          <el-table-column prop="specification" label="规格/要求" min-width="140" />
          <el-table-column prop="quantity" label="数量" width="80" align="center" />
          <el-table-column prop="unit" label="单位" width="70" align="center" />
          <el-table-column prop="unitPrice" label="单价" width="90" align="right" />
        </el-table>
        <div v-else style="color: #999; font-size: 13px; padding: 8px 0">暂无产品明细</div>

        <!-- 相关文档 -->
        <el-divider content-position="left">相关文档</el-divider>
        <AttachmentPanel
          v-if="detailData?.orderId"
          biz-type="sample"
          :biz-id="detailData.orderId"
          :trace-id="detailData.traceId"
        />
      </template>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 工程打样工作台（独立路由页，按钮跳转） -->
    <!-- 操作预览器 -->
    <OperationPreviewDialog
      v-model="previewVisible"
      :operation="previewOperation"
      :biz-id="previewBizId"
      :biz-no="previewBizNo"
      :status-text-map="sampleStatusTextMap"
      @success="onPreviewSuccess"
      @error="onPreviewError"
    >
      <!-- 业务预览（提交打样申请） -->
      <template #preview>
        <SampleReviewPreview
          v-if="previewOperation?.key === 'sample.submitRequest'"
          :order="previewData?.order"
          :products="previewData?.products"
          :loading="previewLoading"
          @view-quotation="openQuotationDetail"
        />
        <SampleReviewPreview
          v-else-if="
            previewOperation?.key === 'sample.approve' ||
            previewOperation?.key === 'sample.rejectReview'
          "
          mode="audit"
          :order="previewData?.order"
          :products="previewData?.products"
          :loading="previewLoading"
          @view-quotation="openQuotationDetail"
          @view-detail="openAuditDetail"
        />
      </template>
    </OperationPreviewDialog>

    <!-- 来源报价单详情（复用共享报价详情组件，查看不离开当前页） -->
    <QuotationDetailDialog
      v-model="quotationDetailVisible"
      :quotation-id="quotationDetailId"
      :is-sensitive="true"
    />

    <CustomerDetailDialog v-model="customerDetailVisible" :customer-id="customerDetailId" />

    <!-- 查看流水 -->
    <TraceTimeline v-model="traceDrawerVisible" :trace-id="currentTraceId" />

    <!-- 转量产 · 就绪检查（资料未齐时展示） -->
    <SampleConvertCheckDialog
      v-model="convertDialogVisible"
      :order-id="convertRow?.orderId ?? null"
      :order-no="convertRow?.orderNo"
    />

    <!-- 转量产 · 预填标准订单表单弹窗（2026-09-07 复用销售订单新增表单） -->
    <SalesOrderFormDialog
      v-model="convertFormVisible"
      mode="convert"
      :order-id="convertRow?.orderId ?? null"
      :order-no="convertRow?.orderNo"
      @success="getList"
    />

    <!-- 打样领料：预览 + 改数量（JJX-QR-031 抬头「打样领料单」） -->
    <el-dialog v-model="pickVisible" :title="`打样领料 - ${pickSample?.orderNo || ''}`" width="780px" append-to-body>
      <el-table :data="pickRows" size="small" border>
        <el-table-column label="物料名称" prop="materialName" min-width="160" show-overflow-tooltip />
        <el-table-column label="匹配物料" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.materialId" size="small" type="success">{{ row.materialCode }}</el-tag>
            <el-tag v-else size="small" type="danger">未匹配</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="BOM需求" prop="quantity" width="90" align="right" />
        <el-table-column label="已领" width="80" align="right">
          <template #default="{ row }">{{ row.issuedQuantity ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="可用" width="80" align="right">
          <template #default="{ row }">{{ row.availableQuantity ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="本次领料" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.pickQty" :min="0" :disabled="!row.materialId" size="small" style="width: 120px" />
          </template>
        </el-table-column>
      </el-table>
      <el-alert v-if="unmatchedCount > 0" type="warning" :closable="false" style="margin-top: 8px"
        :title="`有 ${unmatchedCount} 条样品BOM物料未匹配到物料档案，暂不参与领料（可先在物料档案补建后再来）`" />
      <template #footer>
        <el-button @click="pickVisible = false">取消</el-button>
        <el-button type="primary" :loading="pickSubmitting" @click="submitSamplePick">生成领料单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, onActivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import type { TagType } from '@/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, UploadProps, UploadRawFile } from 'element-plus'
import request from '@/utils/request'
import AttachmentPanel from '@/components/AttachmentPanel/index.vue'
import TraceTimeline from '@/components/TraceTimeline/index.vue'
import SampleConvertCheckDialog from './components/SampleConvertCheckDialog.vue'
import SalesOrderFormDialog from '@/views/sales/order/components/SalesOrderFormDialog.vue'
import { useUserStore } from '@/store/modules/user'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { outboundApi } from '@/api/inventory/outbound'
import type { SamplePickPreviewRow } from '@/types/inventory/outbound'
import type { SampleOrderQueryParams } from '@/api/sales/sampleOrder'
import { quotationApi } from '@/api/sales/quotation'
import { customerApi } from '@/api/sales/customer'
import { searchProduct } from '@/api/product'
import { SampleOrderStatusEnum } from '@/enums/sales'
import OperationPreviewDialog from '@/components/OperationPreviewDialog/index.vue'
import { getOperation } from '@/components/OperationPreviewDialog/registry'
import QuotationDetailDialog from '@/views/sales/quotation/components/QuotationDetailDialog.vue'
import CustomerDetailDialog from '@/views/sales/customer/components/CustomerDetailDialog.vue'
import SampleReviewPreview from './components/SampleReviewPreview.vue'
import type { TableAction } from '@/components/common-ui/TableActionColumn/types'

defineOptions({ name: 'SalesSampleOrder' })

const router = useRouter()
const route = useRoute()

// ==================== 数据 ====================
const loading = ref(false)
const creating = ref(false)
const sampleList = ref<any[]>([])
const total = ref(0)
const statusOptions = ref<
  Array<{ value: number; label: string; description: string; terminal: boolean }>
>([])
const quotationOptions = ref<any[]>([])

const queryParams = reactive<SampleOrderQueryParams>({
  pageNum: 1,
  pageSize: 10,
  customerName: undefined,
  sampleStatus: undefined as number | undefined,
})

const createVisible = ref(false)
const detailVisible = ref(false)
const detailTab = ref('basic')

// 业务预览数据（提交审核 / 审核通过 / 审核驳回共用：打开弹窗前用现有 API 加载最新数据，失败不打开）
const previewLoading = ref(false)
const previewData = ref<{ order: any; products: any[] } | null>(null)
// 来源报价单详情（复用共享报价详情组件）
const quotationDetailVisible = ref(false)
const quotationDetailId = ref<number>(0)
const customerDetailVisible = ref(false)
const customerDetailId = ref<number>()

function showCustDetail(row: { customerId?: number }) {
  if (!row.customerId) return
  customerDetailId.value = row.customerId
  customerDetailVisible.value = true
}

function openQuotationDetail() {
  quotationDetailId.value = previewData.value?.order?.quotationId
  quotationDetailVisible.value = true
}

// 当前用户是否工程角色（9=工程管理）
const isEngineerRole = computed(() => {
  const userStore = useUserStore()
  const roles = userStore.roles || []
  return roles.some(
    (r: any) => String(r) === '9' || String(r).includes('工程') || String(r) === 'engineering'
  )
})

// 打开工程打样工作台（独立路由页，标签页打开）
function openWorkbench(row: any) {
  router.push({ path: '/engineering-workbench/workbench', query: { orderId: row.orderId } })
}
const detailData = ref<any>(null)
// 详情产品明细（DEV-781）
const detailProducts = ref<any[]>([])

// 加载样品单产品明细
async function loadDetailProducts(orderId: number) {
  try {
    const res = await sampleOrderApi.getProducts(orderId)
    detailProducts.value = res?.data || []
  } catch {
    detailProducts.value = []
  }
}

// ==================== 工程区数据 ====================
const engFileList = ref<any[]>([])
const engineeringForm = reactive({ note: '', process: '' })
const costForm = reactive({ cost: 0, workHours: 0 })
const roundList = ref<any[]>([])

// 加载轮次快照
async function loadRounds(orderId: number) {
  try {
    const res = await sampleOrderApi.getRounds(orderId)
    roundList.value = (res as any)?.data || []
  } catch {
    roundList.value = []
  }
}

// 迭代记录（基于现有字段动态生成）
const iterationHistory = computed(() => {
  const d = detailData.value
  if (!d) return []
  const history: Array<{ time: string; action: string; detail: string | null; type: TagType }> = []

  // 创建
  history.push({
    time: d.createTime || d.inquiryDate || '',
    action: `样品单创建（Round 1）`,
    detail: `样品单号: ${d.orderNo || ''}，打样数量: ${d.sampleQty || ''}`,
    type: 'primary',
  })

  // 审核
  if (d.sampleStatus >= 2) {
    history.push({ time: '', action: '提交审核', detail: null, type: 'info' })
  }
  if (d.sampleStatus >= 3) {
    history.push({
      time: '',
      action: '审核通过，进入工程打样',
      detail: d.engineeringNote || null,
      type: 'success',
    })
  }
  if (d.sampleStatus >= 4) {
    history.push({
      time: '',
      action: '样品完成，待送样',
      detail: d.sampleQty ? `打样数量: ${d.sampleQty}` : null,
      type: 'info',
    })
  }
  if (d.sampleStatus >= 5 && d.sampleSendDate) {
    history.push({
      time: d.sampleSendDate,
      action: '已送样待客户确认',
      detail: d.sampleTrackingNo ? `快递单号: ${d.sampleTrackingNo}` : null,
      type: 'warning',
    })
  }

  // 退回记录（多轮迭代）
  if (
    d.sampleStatus === SampleOrderStatusEnum.REJECTED.value ||
    (d.sampleRound && d.sampleRound > 1)
  ) {
    for (let r = 0; r < (d.sampleRound || 1); r++) {
      const roundNum = r + 1
      if (r > 0) {
        history.push({
          time: d.sampleConfirmDate || '',
          action: `Round ${roundNum} 客户退回要求修改`,
          detail: d.remark || '客户要求修改',
          type: 'danger',
        })
        history.push({
          time: '',
          action: `Round ${roundNum + 1} 重新打样`,
          detail: null,
          type: 'warning',
        })
      }
    }
  }

  // 客户确认
  if (d.sampleStatus === SampleOrderStatusEnum.CONFIRMED.value && d.sampleConfirmDate) {
    history.push({
      time: d.sampleConfirmDate,
      action: '✅ 客户确认样品OK',
      detail: d.sampleClientName ? `确认人: ${d.sampleClientName}` : null,
      type: 'success',
    })
  }

  // 转量产
  if (d.sampleStatus === SampleOrderStatusEnum.TRANSFERRED.value && d.convertOrderTime) {
    history.push({
      time: d.convertOrderTime,
      action: '📦 已转量产',
      detail: d.convertedOrderId ? `标准订单ID: ${d.convertedOrderId}` : null,
      type: 'success',
    })
  }

  return history
})

// ==================== 创建/编辑表单 ====================
const createFormRef = ref<FormInstance>()
// 编辑模式：非空表示当前弹窗为编辑（锁定单号与来源报价关系）
const createEditId = ref<number | null>(null)
const createEditOrderNo = ref('')
function emptySampleProduct() {
  return {
    productId: undefined as number | undefined,
    productCode: '',
    productName: '',
    quantity: 1,
    unit: 'PCS',
  }
}
const quotationProductLoading = ref(false)
const quotationProductError = ref('')
let quotationRequestId = 0
const createForm = reactive({
  customerId: undefined as number | undefined,
  quotationId: undefined as number | undefined,
  product: emptySampleProduct(),
  deliveryDate: '',
  contactPerson: '',
  contactPhone: '',
  techRequirement: '',
  remark: '',
})
const customerOptions = ref<any[]>([])
const customerSearching = ref(false)
const productOptions = ref<any[]>([])
const productSearching = ref(false)
const createRules = {
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
}

// ==================== 状态映射 ====================
// 使用统一枚举（对应后端 SampleOrderStatusEnum）
function statusLabel(status: number): string {
  const label = SampleOrderStatusEnum.getLabel(status)
  return label && label !== '未知' ? label : `未知(${status})`
}
function statusTagType(status: number): TagType {
  return (SampleOrderStatusEnum.getTagProps(status).type as TagType) || 'info'
}

// ==================== 接口 ====================
async function getList() {
  loading.value = true
  try {
    const res = await sampleOrderApi.page(queryParams)
    sampleList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    sampleList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleQuery() {
  queryParams.pageNum = 1
  getList()
}

function resetQuery() {
  queryParams.pageNum = 1
  queryParams.customerName = undefined
  queryParams.sampleStatus = undefined
  getList()
}

async function loadQuotationOptions() {
  try {
    const res = await request({
      url: '/sales/quotation/list',
      method: 'get',
      params: { pageNum: 1, pageSize: 50, quotationStatus: 2 },
    })
    quotationOptions.value = res.data?.records || []
  } catch {
    quotationOptions.value = []
  }
}

// ==================== 创建样品单 ====================
function showCreateDialog() {
  createEditId.value = null
  createEditOrderNo.value = ''
  createVisible.value = true
  createForm.customerId = undefined
  createForm.quotationId = undefined
  resetProductSelection()
  createForm.deliveryDate = ''
  createForm.contactPerson = ''
  createForm.contactPhone = ''
  createForm.techRequirement = ''
  createForm.remark = ''
  loadQuotationOptions()
  if (customerOptions.value.length === 0) searchCustomers('')
}

// 编辑样品单（驳回后编辑：仅 CREATED 状态入口；编辑前加载最新 getInfo + getProducts，不使用列表缓存）
async function handleEdit(row: any) {
  const orderId = row?.orderId
  if (!orderId || row.sampleStatus !== SampleOrderStatusEnum.CREATED.value) return
  try {
    const [infoRes, prodRes]: any[] = await Promise.all([
      sampleOrderApi.getInfo(orderId),
      sampleOrderApi.getProducts(orderId),
    ])
    const order = infoRes?.data || {}
    const items: any[] = prodRes?.data || []
    createForm.customerId = order.customerId
    createForm.quotationId = undefined // 编辑模式不展示来源报价选择（来源报价关系锁定）
    if (items.length > 1) {
      ElMessage.error('该历史样品单包含多个产品，请先按产品拆单，不能直接编辑覆盖')
      return
    }
    resetProductSelection()
    if (items.length === 1) fillSampleProduct(items[0])
    createForm.deliveryDate = order.deliveryDate || ''
    createForm.contactPerson = order.contactPerson || ''
    createForm.contactPhone = order.contactPhone || ''
    createForm.techRequirement = order.engineeringNote || ''
    createForm.remark = order.remark || ''
    // 回填客户选项（保证当前客户可显示）
    if (order.customerId && !customerOptions.value.some((c) => c.customerId === order.customerId)) {
      customerOptions.value.push({
        customerId: order.customerId,
        customerName: order.customerName || '',
        contactPerson: order.contactPerson || '',
        contactPhone: order.contactPhone || '',
      })
    }
    createEditId.value = orderId
    createEditOrderNo.value = order.orderNo || ''
    createVisible.value = true
  } catch (e: any) {
    ElMessage.error(e?.message || '加载样品单数据失败，请刷新后重试')
  }
}

// 客户搜索
async function searchCustomers(keyword: string) {
  customerSearching.value = true
  try {
    const res: any = await customerApi.searchCustomers(keyword || '')
    customerOptions.value = res.data || []
  } catch {
    customerOptions.value = []
  } finally {
    customerSearching.value = false
  }
}

// 选客户：带出联系人/电话
function onCustomerChange(cid: number) {
  const c = customerOptions.value.find((x) => x.customerId === cid)
  if (c) {
    if (!createForm.contactPerson) createForm.contactPerson = c.contactPerson || ''
    if (!createForm.contactPhone) createForm.contactPhone = c.contactPhone || ''
  }
  resetProductSelection()
}

function resetProductSelection() {
  quotationRequestId++
  quotationProductLoading.value = false
  quotationProductError.value = ''
  productOptions.value = []
  createForm.product = emptySampleProduct()
}

function fillSampleProduct(item: any) {
  createForm.product = {
    productId: item.productId ?? undefined,
    productCode: item.productCode || '',
    productName: item.productName || '',
    quantity: item.quantity ?? 1,
    unit: item.unit || 'PCS',
  }
  productOptions.value = item.productId ? [{ ...item }] : []
}

// 单产品报价直接带入；多产品报价保留整单拆分转换语义。
async function onQuotationChange(qid?: number) {
  resetProductSelection()
  if (!qid) return
  const q = quotationOptions.value.find((x) => x.quotationId === qid)
  if (q) {
    createForm.customerId = q.customerId
    createForm.contactPerson = q.contactPerson || ''
    createForm.contactPhone = q.contactPhone || ''
    createForm.deliveryDate = q.validUntil || ''
    if (!customerOptions.value.some((c) => c.customerId === q.customerId)) {
      customerOptions.value.push({ ...q })
    }
  }
  const requestId = quotationRequestId
  quotationProductLoading.value = true
  try {
    const res: any = await quotationApi.getItems(qid)
    if (requestId !== quotationRequestId) return
    const items: any[] = res?.data || []
    if (items.length !== 1) {
      quotationProductError.value =
        items.length > 1
          ? '该报价包含多个产品，请到「报价单」使用「转为样品单」，按产品分别生成样品单。'
          : '该报价没有产品，请补充报价产品或清除来源报价后选择打样产品。'
      return
    }
    fillSampleProduct(items[0])
  } catch (e: any) {
    if (requestId !== quotationRequestId) return
    quotationProductError.value = e?.message || '加载报价产品失败，请重新选择来源报价单'
  } finally {
    if (requestId === quotationRequestId) quotationProductLoading.value = false
  }
}

// 产品搜索：只展示当前客户的产品，并忽略切换客户后的旧请求。
async function searchProducts(keyword: string) {
  const customerId = createForm.customerId
  const requestId = quotationRequestId
  if (!customerId) return
  productSearching.value = true
  try {
    const res: any = await searchProduct(keyword || '', customerId)
    if (requestId !== quotationRequestId || customerId !== createForm.customerId) return
    productOptions.value = res.data || []
  } catch {
    if (requestId === quotationRequestId) productOptions.value = []
  } finally {
    productSearching.value = false
  }
}

// 选中产品：带出编码/名称/单位
function onProductSelect(productId: number) {
  const product = productOptions.value.find((p) => p.productId === productId)
  if (product) {
    createForm.product.productCode = product.productCode
    createForm.product.productName = product.productName
    createForm.product.unit = product.unit || 'PCS'
  }
}

function resetCreateForm() {
  resetProductSelection()
  createFormRef.value?.clearValidate()
}

async function submitCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!createForm.customerId) {
    ElMessage.warning('请选择客户')
    return
  }
  if (quotationProductLoading.value || quotationProductError.value) {
    ElMessage.warning(quotationProductError.value || '请等待报价产品加载完成')
    return
  }
  const product = createForm.product
  if (!product.productCode || !product.productName) {
    ElMessage.warning('请选择一个打样产品')
    return
  }
  if (!Number.isInteger(product.quantity) || product.quantity <= 0) {
    ElMessage.warning('打样数量必须为大于零的整数')
    return
  }
  creating.value = true
  const payload = {
    customerId: createForm.customerId,
    items: [{ ...product, unit: product.unit || 'PCS' }],
    deliveryDate: createForm.deliveryDate || undefined,
    contactPerson: createForm.contactPerson || undefined,
    contactPhone: createForm.contactPhone || undefined,
    techRequirement: createForm.techRequirement || undefined,
    remark: createForm.remark || undefined,
  }
  try {
    if (createEditId.value) {
      // 编辑模式：走更新接口（不含 quotationId，来源报价关系锁定）
      await sampleOrderApi.update(createEditId.value, payload)
      ElMessage.success(`样品单${createEditOrderNo.value}已保存`)
    } else {
      const res = await sampleOrderApi.create({
        ...payload,
        quotationId: createForm.quotationId || undefined,
      })
      ElMessage.success(`样品单创建成功: ${res.data.orderNo}，可前往工程打样`)
    }
    createVisible.value = false
    createEditId.value = null
    createEditOrderNo.value = ''
    getList()
  } catch (e: any) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    creating.value = false
  }
}

// ==================== 详情 / 工程区 ====================
async function showDetail(row: any) {
  detailData.value = row
  detailTab.value = 'basic'
  detailVisible.value = true
  loadRounds(row.orderId)
  loadDetailProducts(row.orderId) // DEV-781：加载产品明细

  // 初始化工程表单
  engineeringForm.note = row.engineeringNote || ''
  engineeringForm.process = row.currentProcess || ''
  // 加载工程附件
  await loadEngFiles(row.orderId)
}

// 打印样品单（跳转独立打印页）
function handlePrint(row: any) {
  window.open(`/print/sample-order/${row.orderId}`, '_blank')
}

function onDetailOpen() {
  // 每次打开再次加载最新数据
  if (detailData.value?.orderId) {
    sampleOrderApi
      .getInfo(detailData.value.orderId)
      .then((res) => {
        detailData.value = res.data
        engineeringForm.note = res.data.engineeringNote || ''
        engineeringForm.process = res.data.currentProcess || ''
        costForm.cost = res.data.sampleCost || 0
        costForm.workHours = res.data.sampleWorkHours || 0
        loadRounds(detailData.value.orderId)
        loadDetailProducts(detailData.value.orderId) // DEV-781：加载产品明细
      })
      .catch(() => {})
  }
}

// 工程附件
async function loadEngFiles(bizId: number) {
  try {
    const res: any = await request({
      url: '/system/attachment/list',
      method: 'get',
      params: { bizType: 'sample_order', bizId },
    })
    engFileList.value =
      res?.code === 200
        ? (res.data || []).map((a: any) => ({
            name: a.fileName,
            url: `/system/attachment/download/${a.id}`,
            response: a.id,
            status: 'success',
          }))
        : []
  } catch {
    engFileList.value = []
  }
}

// 上传类型校验
function engBeforeUpload(file: UploadRawFile) {
  const maxSize = 10 * 1024 * 1024
  const allowed = [
    '.pdf',
    '.doc',
    '.docx',
    '.xls',
    '.xlsx',
    '.jpg',
    '.jpeg',
    '.png',
    '.dwg',
    '.dxf',
    '.zip',
  ]
  const ext = '.' + (file.name.split('.').pop()?.toLowerCase() || '')
  if (!allowed.includes(ext)) {
    ElMessage.error('不支持的文件格式')
    return false
  }
  if (file.size > maxSize) {
    ElMessage.error('文件大小不能超过10MB')
    return false
  }
  return true
}

// 退回后重新打样（客户退回9 → 工程打样中3，走操作预览器）
async function handleRestart(row: any) {
  openPreview('sample.restart', row)
}

// 工程接单（预览器，DEV-526）
async function handleAcceptSample(row: any) {
  openPreview('sample.accept', row)
}

// 已接单 → 引导到打样平台
function goWorkbench() {
  ElMessage.info('请到「工程管理 → 打样平台」继续打样操作')
}

// ==================== 列表操作（操作预览器方式） ====================
// ===== 状态机谓词（canXXX）：操作显隐统一入口，内部仅引用枚举成员，禁止裸状态码 =====
function isCreated(row: any): boolean {
  return row?.sampleStatus === SampleOrderStatusEnum.CREATED.value
}
function isEngineering(row: any): boolean {
  return row?.sampleStatus === SampleOrderStatusEnum.ENGINEERING.value
}
function isTransferred(row: any): boolean {
  return row?.sampleStatus === SampleOrderStatusEnum.TRANSFERRED.value
}
function isClosed(row: any): boolean {
  return row?.sampleStatus === SampleOrderStatusEnum.CLOSED.value
}
function isCancelled(row: any): boolean {
  return row?.sampleStatus === SampleOrderStatusEnum.CANCELLED.value
}

// 工程接单（待打样2/打样中3且未接单；新模型无审核环节）/ 已接单跳工作台
function canAcceptEngineering(row: any): boolean {
  return (
    [SampleOrderStatusEnum.REQUEST.value, SampleOrderStatusEnum.ENGINEERING.value].includes(
      row?.sampleStatus
    ) &&
    isEngineerRole.value &&
    !row.engineeringAcceptor
  )
}
function canGoWorkbench(row: any): boolean {
  return isEngineering(row) && isEngineerRole.value && !!row.engineeringAcceptor
}
// 送样 / 客户确认 / 退回修改 / 转量产 / 重新打样
function canSendSample(row: any): boolean {
  return row?.sampleStatus === SampleOrderStatusEnum.SAMPLE_READY.value
}
function canConfirmSample(row: any): boolean {
  return row?.sampleStatus === SampleOrderStatusEnum.SAMPLE_SENT.value
}
function canRejectSample(row: any): boolean {
  return row?.sampleStatus === SampleOrderStatusEnum.SAMPLE_SENT.value
}
function canConvert(row: any): boolean {
  return row?.sampleStatus === SampleOrderStatusEnum.CONFIRMED.value
}
function canRestart(row: any): boolean {
  return row?.sampleStatus === SampleOrderStatusEnum.REJECTED.value
}
// 作废：非终态（草稿→已确认）
function canCancel(row: any): boolean {
  return [
    SampleOrderStatusEnum.CREATED.value,
    SampleOrderStatusEnum.REQUEST.value,
    SampleOrderStatusEnum.ENGINEERING.value,
    SampleOrderStatusEnum.SAMPLE_READY.value,
    SampleOrderStatusEnum.SAMPLE_SENT.value,
    SampleOrderStatusEnum.CONFIRMED.value,
  ].includes(row?.sampleStatus)
}
// 复制：任意状态均可复制（含打样中/已确认/客户退回，2026-09-15 B 口径放宽）
function canCopy(row: any): boolean {
  return row?.sampleStatus != null
}

// ===== 打样领料单（按样品单生成；原在「产品作业规范」⑦ 页签，dev-20261010-013 挪到样品单页）=====
const pickVisible = ref(false)
const pickSample = ref<any>(null)
const pickRows = ref<(SamplePickPreviewRow & { pickQty: number })[]>([])
const pickSubmitting = ref(false)
const unmatchedCount = computed(() => pickRows.value.filter((r) => !r.materialId).length)

async function openSamplePick(row: any) {
  pickSample.value = row
  pickRows.value = []
  try {
    const res: any = await outboundApi.samplePickPreview(Number(row.sampleOrderId))
    const rows: SamplePickPreviewRow[] = res?.data || []
    pickRows.value = rows.map((r) => ({
      ...r,
      pickQty: Math.max(0, Number(r.quantity || 0) - Number(r.issuedQuantity || 0)),
    }))
  } catch (e: any) {
    ElMessage.error(e?.message || '加载打样领料预览失败')
    return
  }
  pickVisible.value = true
}

async function submitSamplePick() {
  if (!pickSample.value) return
  const items = pickRows.value
    .filter((r) => r.materialId && Number(r.pickQty) > 0)
    .map((r) => ({
      materialId: Number(r.materialId),
      materialCode: r.materialCode || undefined,
      materialName: r.materialName,
      quantity: Number(r.pickQty),
    }))
  if (!items.length) {
    ElMessage.warning('请填写本次领料数量（至少一行、物料已匹配）')
    return
  }
  pickSubmitting.value = true
  try {
    const res: any = await outboundApi.createSamplePick(Number(pickSample.value.sampleOrderId), items)
    const outboundId = Number(res?.data || 0)
    ElMessage.success('打样领料单已生成（待仓库确认发料后扣库存）')
    pickVisible.value = false
    // 生成后直接开打印页（JJX-QR-031 抬头「打样领料单」）；该单同时可在出库管理查看
    if (outboundId) window.open(`/print/outbound/${outboundId}`, '_blank')
  } catch (e: any) {
    ElMessage.error(e?.message || '生成失败')
  } finally {
    pickSubmitting.value = false
  }
}

const sampleActions: TableAction<any>[] = [
  { key: 'trace', label: '查看流水', type: 'info' },
  {
    key: 'edit',
    label: '编辑',
    permission: 'sales:sample:edit',
    visible: ({ row }) => isCreated(row),
  },

  {
    key: 'accept',
    label: '工程接单',
    type: 'warning',
    permission: 'sales:sample:engineering',
    visible: ({ row }) => canAcceptEngineering(row),
  },
  {
    key: 'workbench',
    label: '已接单',
    type: 'success',
    permission: 'sales:sample:engineering',
    visible: ({ row }) => canGoWorkbench(row),
  },

  {
    key: 'request',
    label: '申请打样',
    permission: 'sales:sample:edit',
    visible: ({ row }) => isCreated(row),
  },
  {
    key: 'send',
    label: '送样登记',
    permission: 'sales:sample:deliver',
    visible: ({ row }) => canSendSample(row),
  },
  {
    key: 'confirm',
    label: '客户确认OK',
    type: 'success',
    permission: 'sales:sample:confirm',
    visible: ({ row }) => canConfirmSample(row),
  },
  {
    key: 'reject',
    label: '退回修改',
    type: 'warning',
    permission: 'sales:sample:confirm',
    visible: ({ row }) => canConfirmSample(row),
  },
  {
    key: 'convert',
    label: '转量产',
    permission: 'sales:sample:convert',
    visible: ({ row }) => canConvert(row),
  },
  {
    key: 'transferred',
    label: '已转量产',
    type: 'success',
    disabled: true,
    visible: ({ row }) => isTransferred(row),
  },
  {
    key: 'restart',
    label: '重新打样',
    type: 'warning',
    permission: 'sales:sample:engineering',
    visible: ({ row }) => canRestart(row),
  },
  {
    key: 'copy',
    label: '复制',
    type: 'warning',
    permission: 'sales:sample:add',
    visible: ({ row }) => canCopy(row),
  },
  {
    key: 'cancel',
    label: '作废',
    type: 'danger',
    permission: 'sales:sample:edit',
    visible: ({ row }) => canCancel(row),
  },

  { key: 'pick', label: '生成领料单', type: 'primary', permission: 'inventory:outbound:add' },

  { key: 'print', label: '打印', type: 'info' },
]
const handleSampleAction = (key: string, row: any) => {
  const handlers: Record<string, () => void> = {
    pick: () => void openSamplePick(row),
    print: () => handlePrint(row),
    trace: () => showTrace(row),
    copy: () => void handleCopySample(row),
    accept: () => void handleAcceptSample(row),
    workbench: goWorkbench,
    cancel: () => void handleCancel(row),
    edit: () => void handleEdit(row),
    request: () => void handleSubmitRequest(row),
    send: () => void handleSendSample(row),
    confirm: () => void handleConfirm(row),
    reject: () => void handleRejectSample(row),
    convert: () => void handleConvert(row),
    restart: () => void handleRestart(row),
  }
  handlers[key]?.()
}

// 操作预览器状态
const previewVisible = ref(false)
const previewOperation = ref<any>(null)
const previewBizId = ref<number | null>(null)
const previewBizNo = ref('')
// 状态码 → 状态名（预览器状态跳转展示用）
const sampleStatusTextMap = Object.fromEntries(
  SampleOrderStatusEnum.items.map((i: any) => [i.value, i.label])
)
function openPreview(opKey: string, row: any) {
  if (!row?.orderId) return
  let op = getOperation(opKey)
  if (!op) return
  // 动态默认值：实际打样数量默认取单据数量
  if (opKey === 'sample.markReady' && row.sampleQty) {
    op = {
      ...op,
      fields: (op.fields || []).map((f) =>
        f.key === 'sampleQty' ? { ...f, defaultValue: row.sampleQty } : f
      ),
    }
  }
  previewOperation.value = op
  previewBizId.value = row.orderId
  previewBizNo.value = row.orderNo || ''
  previewVisible.value = true
}

// 预览器执行成功 → 刷新列表（成功反馈由通用弹窗 ElMessage 承担，不再弹结果大窗）
function onPreviewSuccess() {
  getList()
}

// 预览器操作失败 → 刷新列表恢复后端最新状态（如状态冲突）；错误提示已由通用弹窗 ElMessage 承担，不重复提示、不重试、不开结果弹窗
function onPreviewError() {
  getList()
}

// 作废样品单（列表行 + 详情弹窗共用）
async function handleCancel(row: any) {
  const orderId = row?.orderId
  if (!orderId) return
  try {
    const { value } = await ElMessageBox.prompt(
      '请输入作废原因\n\n⚠️ 作废后将通知销售/工程管理' +
        (row?.engineeringAcceptor ? `，并派任务给接单人【${row.engineeringAcceptor}】` : ''),
      '作废样品单',
      {
        inputType: 'textarea',
        confirmButtonText: '确认作废',
        cancelButtonText: '取消',
        inputValidator: (v: string) => (v && v.trim() ? true : '请输入作废原因'),
      }
    )
    await sampleOrderApi.cancel(orderId, value)
    ElMessage.success('样品单已作废')
    detailVisible.value = false
    getList()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '作废失败')
  }
}

// 复制样品单（DEV-1114：仅已完成/已取消终态单，一键生成新草稿单）
async function handleCopySample(row: any) {
  const orderId = row?.orderId
  if (!orderId) return
  try {
    await ElMessageBox.confirm(
      `确定复制样品单【${row.orderNo}】生成一张新的样品单吗？`,
      '复制样品单',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
    )
    const res: any = await sampleOrderApi.copy(orderId)
    if (res?.code === 200) {
      ElMessage.success('复制成功，新样品单已生成')
      getList()
    } else {
      ElMessage.error(res?.msg || '复制失败')
    }
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '复制失败')
  }
}

// 加载最新业务数据（getInfo + getProducts），成功返回 true；每次加载前清空上一次预览数据
// 加载失败：提示一次错误、返回 false，不打开可确认弹窗，不允许用残留数据继续操作
async function loadPreviewData(orderId: number): Promise<boolean> {
  previewLoading.value = true
  previewData.value = null
  try {
    const [infoRes, prodRes]: any[] = await Promise.all([
      sampleOrderApi.getInfo(orderId),
      sampleOrderApi.getProducts(orderId),
    ])
    previewData.value = {
      order: infoRes?.data || null,
      products: prodRes?.data || [],
    }
    return true
  } catch (e: any) {
    ElMessage.error(e?.message || '加载样品单数据失败，请刷新后重试')
    return false
  } finally {
    previewLoading.value = false
  }
}

// 提交审核：先加载最新业务数据，成功后才打开弹窗
async function handleSubmitRequest(row: any) {
  const orderId = row?.orderId
  if (!orderId) return
  if (!(await loadPreviewData(orderId))) return
  openPreview('sample.submitRequest', row)
}

// 审核通过：先加载最新业务数据，成功后才打开弹窗
async function handleApprove(row: any) {
  const orderId = row?.orderId
  if (!orderId) return
  if (!(await loadPreviewData(orderId))) return
  openPreview('sample.approve', row)
}

// 审核驳回：先加载最新业务数据，成功后才打开弹窗
async function handleRejectReview(row: any) {
  const orderId = row?.orderId
  if (!orderId) return
  if (!(await loadPreviewData(orderId))) return
  openPreview('sample.rejectReview', row)
}

// 审核预览 → 查看详情（图纸/工艺文件/附件等完整信息）
function openAuditDetail() {
  if (previewData.value?.order) showDetail(previewData.value.order)
}

async function handleMarkReady(row: any) {
  // 软提醒（DEV-491）：工艺参数为空时确认
  if (!row?.engineeringNote) {
    try {
      await ElMessageBox.confirm('该样品单未填写工艺参数（工程备注），仍要标记完成？', '提示', {
        confirmButtonText: '仍要完成',
        cancelButtonText: '返回',
        type: 'warning',
      })
    } catch {
      return
    }
  }
  openPreview('sample.markReady', row)
}

async function handleSendSample(row: any) {
  openPreview('sample.sendSample', row)
}

async function handleConfirm(row: any) {
  openPreview('sample.confirm', row)
}

async function handleRejectSample(row: any) {
  openPreview('sample.rejectSample', row)
}

// 产品资料转移入口已移至打样平台（2026-08-12），样品单管理仅保留转量产

async function handleConvert(row: any) {
  // 转量产（2026-09-07 复用标准订单新增表单）：先就绪检查，通过→弹预填表单；不齐→弹检查明细
  convertRow.value = row
  try {
    const res: any = await sampleOrderApi.convertCheck(row.orderId)
    const check: any = res?.data
    if (check?.allPass) {
      convertFormVisible.value = true
    } else {
      convertDialogVisible.value = true
    }
  } catch {
    // 校验接口异常时仍展示检查弹窗，不阻断排查
    convertDialogVisible.value = true
  }
}

// 查看流水
const traceDrawerVisible = ref(false)
const currentTraceId = ref('')
// 1199/1141：流水带 bizType+bizId（sample_order），事件流聚合才能拉到样品附件
const currentTraceBizType = ref('sample_order')
const currentTraceBizId = ref('')

// 转量产标准化窗口
const convertDialogVisible = ref(false)
const convertFormVisible = ref(false)
const convertRow = ref<any>(null)
function showTrace(row: any) {
  currentTraceId.value = row.traceId || ''
  currentTraceBizId.value = row.orderId != null ? String(row.orderId) : ''
  traceDrawerVisible.value = true
}

// ==================== 初始化 ====================
onMounted(() => {
  getList()
  statusOptions.value = SampleOrderStatusEnum.items.map((item) => ({
    value: item.value,
    label: item.label,
    description: '',
    terminal: false,
  }))
})
onActivated(() => {
  getList()
})
</script>

<style scoped lang="scss">
.app-container {
  padding: 20px;
}
.search-card,
.operation-card,
.table-card {
  margin-bottom: 16px;
}
</style>
