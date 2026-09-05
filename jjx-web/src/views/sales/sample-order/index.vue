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
            @keyup.enter="getList"
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
          <el-button type="primary" icon="Search" @click="getList">搜索</el-button>
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
        <el-table-column label="样品单号" prop="orderNo" width="180" />
        <el-table-column label="客户" prop="customerName" width="160" />
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
        <el-table-column label="操作" width="400" fixed="right">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="showDetail(scope.row)"
              >详情</el-button
            >
            <el-button link type="info" size="small" @click="handlePrint(scope.row)"
              >打印</el-button
            >

            <!-- 查看流水 -->
            <el-button link type="info" size="small" @click="showTrace(scope.row)"
              >查看流水</el-button
            >

            <!-- 复制（DEV-1114）：仅终态单（已转量产/已关闭/已取消）可复制，一键生成新草稿单 -->
            <el-button
              v-hasPermi="['sales:sample:add']"
              v-if="canCopy(scope.row)"
              link
              type="warning"
              size="small"
              @click="handleCopySample(scope.row)"
              >复制</el-button
            >

            <!-- 工程接单（预览器）：接单后到工程管理-打样平台操作 -->
            <el-button
              v-hasPermi="['sales:sample:engineering']"
              v-if="canAcceptEngineering(scope.row)"
              link
              type="warning"
              size="small"
              @click="handleAcceptSample(scope.row)"
              >🔧 工程接单</el-button
            >
            <el-button
              v-hasPermi="['sales:sample:engineering']"
              v-if="canGoWorkbench(scope.row)"
              link
              type="success"
              size="small"
              @click="goWorkbench"
              >✅ 已接单</el-button
            >

            <!-- 作废：非终态（草稿/待打样/打样中/待送样/已送样/已确认）可作废 -->
            <el-button
              v-hasPermi="['sales:sample:edit']"
              v-if="canCancel(scope.row)"
              link
              type="danger"
              size="small"
              @click="handleCancel(scope.row)"
              >作废</el-button
            >

            <template v-if="isCreated(scope.row)">
              <el-button
                v-hasPermi="['sales:sample:edit']"
                link
                type="primary"
                size="small"
                @click="handleEdit(scope.row)"
                >编辑</el-button
              >
              <el-button
                v-hasPermi="['sales:sample:edit']"
                link
                type="primary"
                size="small"
                @click="handleSubmitRequest(scope.row)"
                >申请打样</el-button
              >
            </template>

            <template v-else-if="canSendSample(scope.row)">
              <el-button
                v-hasPermi="['sales:sample:deliver']"
                link
                type="primary"
                size="small"
                @click="handleSendSample(scope.row)"
                >送样登记</el-button
              >
            </template>
            <template v-else-if="canConfirmSample(scope.row)">
              <el-button
                v-hasPermi="['sales:sample:confirm']"
                link
                type="success"
                size="small"
                @click="handleConfirm(scope.row)"
                >客户确认OK</el-button
              >
              <el-button
                v-hasPermi="['sales:sample:confirm']"
                link
                type="warning"
                size="small"
                @click="handleRejectSample(scope.row)"
                >退回修改</el-button
              >
            </template>
            <template v-else-if="canConvert(scope.row)">
              <el-button
                v-hasPermi="['sales:sample:convert']"
                link
                type="primary"
                size="small"
                @click="handleConvert(scope.row)"
                >转量产</el-button
              >
            </template>
            <template v-else-if="isTransferred(scope.row)">
              <el-tag size="small" type="success">已转量产</el-tag>
            </template>
            <template v-else-if="canRestart(scope.row)">
              <el-button
                v-hasPermi="['sales:sample:engineering']"
                link
                type="warning"
                size="small"
                @click="handleRestart(scope.row)"
                >重新打样</el-button
              >
            </template>
          </template>
        </el-table-column>
      </el-table>
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
            placeholder="可选：从报价单带出客户/产品明细"
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
        <el-form-item label="产品明细" prop="items">
          <el-table
            :data="createForm.items"
            border
            size="small"
            max-height="220"
            style="width: 100%"
          >
            <el-table-column label="产品" min-width="210">
              <template #default="scope">
                <el-select
                  v-model="scope.row.productId"
                  filterable
                  remote
                  :remote-method="(q) => searchProducts(q, scope.row)"
                  :loading="productSearching"
                  :disabled="!createForm.customerId"
                  placeholder="请先选择客户，再搜索该客户的产品"
                  style="width: 100%"
                  @change="onProductSelect(scope.row)"
                >
                  <el-option
                    v-for="p in productOptions"
                    :key="p.productId"
                    :label="`${p.productCode} - ${p.productName}`"
                    :value="p.productId"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="编码" prop="productCode" width="110" />
            <el-table-column
              label="名称"
              prop="productName"
              min-width="120"
              show-overflow-tooltip
            />
            <el-table-column label="数量" width="95">
              <template #default="scope">
                <el-input-number
                  v-model="scope.row.quantity"
                  :min="1"
                  size="small"
                  controls-position="right"
                  style="width: 85px"
                />
              </template>
            </el-table-column>
            <el-table-column label="单位" width="75">
              <template #default="scope">
                <el-input v-model="scope.row.unit" size="small" placeholder="PCS" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="55" align="center">
              <template #default="scope">
                <el-button link type="danger" @click="removeItem(scope.$index)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button
            size="small"
            type="primary"
            plain
            icon="Plus"
            style="margin-top: 6px"
            @click="addItem"
            >添加产品</el-button
          >
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
        <el-button type="primary" @click="submitCreate" :loading="creating">{{
          createEditId ? '保存' : '创建'
        }}</el-button>
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

    <!-- 查看流水 -->
    <TraceTimeline v-model="traceDrawerVisible" :trace-id="currentTraceId" />

    <!-- 转量产 · 就绪检查（DEV-xxx） -->
    <SampleConvertCheckDialog
      v-model="convertDialogVisible"
      :order-id="convertRow?.orderId ?? null"
      :order-no="convertRow?.orderNo"
      :sample-contact="
        convertRow
          ? { contactPerson: convertRow.contactPerson, contactPhone: convertRow.contactPhone }
          : undefined
      "
      @success="getList"
    />
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
import { useUserStore } from '@/store/modules/user'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { quotationApi } from '@/api/sales/quotation'
import { customerApi } from '@/api/sales/customer'
import { searchProduct } from '@/api/product'
import { SampleOrderStatusEnum } from '@/enums/sales'
import OperationPreviewDialog from '@/components/OperationPreviewDialog/index.vue'
import { getOperation } from '@/components/OperationPreviewDialog/registry'
import QuotationDetailDialog from '@/views/sales/quotation/components/QuotationDetailDialog.vue'
import SampleReviewPreview from './components/SampleReviewPreview.vue'

defineOptions({ name: 'SalesSampleOrder' })

const router = useRouter()
const route = useRoute()

// ==================== 数据 ====================
const loading = ref(false)
const creating = ref(false)
const sampleList = ref<any[]>([])
const statusOptions = ref<
  Array<{ value: number; label: string; description: string; terminal: boolean }>
>([])
const quotationOptions = ref<any[]>([])

const queryParams = reactive({
  customerName: '',
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
const createForm = reactive({
  customerId: undefined as number | undefined,
  quotationId: undefined as number | undefined,
  items: [] as any[],
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
    const res = await sampleOrderApi.list({ sampleStatus: queryParams.sampleStatus })
    sampleList.value = res.data || []
  } catch {
    sampleList.value = []
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  queryParams.customerName = ''
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
  createForm.items = []
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
    createForm.items = items.map((it: any) => ({
      productId: it.productId ?? undefined,
      productCode: it.productCode || '',
      productName: it.productName || '',
      quantity: it.quantity ?? 1,
      unit: it.unit || 'PCS',
    }))
    if (createForm.items.length === 0) addItem()
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
  productOptions.value = []
  createForm.items.forEach((item: any) => {
    item.productId = undefined
    item.productCode = ''
    item.productName = ''
  })
}

// 选择报价单：带出客户 + 联系人/电话/交期 + 产品明细（可继续编辑）
async function onQuotationChange(qid: number) {
  const q = quotationOptions.value.find((x) => x.quotationId === qid)
  if (q) {
    createForm.customerId = q.customerId
    createForm.contactPerson = q.contactPerson || ''
    createForm.contactPhone = q.contactPhone || ''
    createForm.deliveryDate = q.validUntil || ''
    onCustomerChange(q.customerId)
  }
  if (!qid) return
  try {
    const res: any = await quotationApi.getItems(qid)
    const items: any[] = (res as any)?.data || []
    createForm.items = items.map((it) => ({
      productId: it.productId,
      productCode: it.productCode || '',
      productName: it.productName || '',
      quantity: it.quantity || 1,
      unit: it.unit || 'PCS',
    }))
    if (items.length === 0) addItem()
  } catch {
    createForm.items = []
    addItem()
  }
}

// 产品搜索（明细行）
async function searchProducts(keyword: string, row: any) {
  productSearching.value = true
  try {
    if (!createForm.customerId) {
      productOptions.value = []
      return
    }
    const res: any = await searchProduct(keyword || '', createForm.customerId)
    productOptions.value = res.data || []
    if (row) row._options = productOptions.value
  } catch {
    productOptions.value = []
  } finally {
    productSearching.value = false
  }
}

// 选中产品：带出编码/名称/单位
function onProductSelect(row: any) {
  const p = productOptions.value.find((x) => x.productId === row.productId)
  if (p) {
    row.productCode = p.productCode
    row.productName = p.productName
    row.unit = p.unit || 'PCS'
  }
}

function addItem() {
  createForm.items.push({
    productId: undefined,
    productCode: '',
    productName: '',
    quantity: 1,
    unit: 'PCS',
  })
}

function removeItem(index: number) {
  createForm.items.splice(index, 1)
}

function resetCreateForm() {
  createFormRef.value?.resetFields()
}

async function submitCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  if (!createForm.customerId) {
    ElMessage.warning('请选择客户')
    return
  }
  const validItems = createForm.items.filter((i) => i.productId || i.productCode)
  if (validItems.length === 0) {
    ElMessage.warning('请至少添加一个产品明细')
    return
  }
  creating.value = true
  const payload = {
    customerId: createForm.customerId,
    items: validItems.map((i) => ({
      productId: i.productId || undefined,
      productCode: i.productCode,
      productName: i.productName,
      quantity: i.quantity,
      unit: i.unit || 'PCS',
    })),
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
// 复制：仅终态（已转量产/已关闭/已取消）
function canCopy(row: any): boolean {
  return [
    SampleOrderStatusEnum.TRANSFERRED.value,
    SampleOrderStatusEnum.CLOSED.value,
    SampleOrderStatusEnum.CANCELLED.value,
  ].includes(row?.sampleStatus)
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
  // 转量产：就绪检查（产品/BOM/工艺路线/菲林清单）
  convertRow.value = row
  convertDialogVisible.value = true
}

// 查看流水
const traceDrawerVisible = ref(false)
const currentTraceId = ref('')
// 1199/1141：流水带 bizType+bizId（sample_order），事件流聚合才能拉到样品附件
const currentTraceBizType = ref('sample_order')
const currentTraceBizId = ref('')

// 转量产标准化窗口
const convertDialogVisible = ref(false)
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
