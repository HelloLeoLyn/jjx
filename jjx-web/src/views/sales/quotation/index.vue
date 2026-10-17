<!-- views/sales/quotation/index.vue -->
<template>
  <div class="app-container">
    <!-- ============================================================ -->
    <!-- ✅ 搜索区域 -->
    <!-- ============================================================ -->
    <SkeletonQuery>
      <QuotationSearchForm
        :query-params.sync="queryParams"
        :date-range.sync="dateRange"
        @query="handleSearch"
        @reset="handleReset"
      />
    </SkeletonQuery>

    <!-- ============================================================ -->
    <!-- ✅ 统计卡片 -->
    <!-- ============================================================ -->
    <SkeletonStats>
      <QuotationStats :stats="stats" />
    </SkeletonStats>

    <!-- ============================================================ -->
    <!-- ✅ 操作按钮栏 -->
    <!-- ============================================================ -->
    <SkeletonToolbar>
      <QuotationToolbar
        :single="single"
        :multiple="multiple"
        :actions="quotationActions"
        @add="handleAdd"
        @update="handleUpdate"
        @delete="handleDelete"
        @export="handleExport"
        @send="handleSend"
        @convert="handleConvert"
        @convert-to-sample="handleConvertToSample"
        @customer-confirm="handleCustomerConfirm"
        @copy="handleCopy"
        @submit-review="handleSubmitReview"
        @review="handleReview"
        @export-pdf="handleExportPdf"
        @export-excel="handleExportExcel"
        @re-quote="handleReQuote"
        @modify="handleModify"
        @attachment="handleAttachment"
      />
    </SkeletonToolbar>

    <!-- ============================================================ -->
    <!-- ✅ 表格区域 -->
    <!-- ============================================================ -->
    <SkeletonTable>
      <el-card class="table-card" shadow="never">
        <el-table
          ref="tableRef"
          v-loading="loading"
          :data="quotationList"
          highlight-current-row
          @selection-change="handleSelectionChange"
          @sort-change="handleSortChange"
        >
          <el-table-column type="selection" width="55" align="center" />
          <el-table-column label="报价单号" align="center" width="160">
            <template #default="{ row }">
              <el-link type="primary" underline="never" @click="handleView(row)">
                {{ row.quotationNo }}
              </el-link>
            </template>
          </el-table-column>
          <el-table-column label="来源询价单" align="center" width="140">
            <template #default="{ row }">
              {{ row.sourceInquiryNo }}
            </template>
          </el-table-column>
          <el-table-column label="客户名称" align="center" prop="customerName" width="180" />
          <el-table-column label="报价日期" align="center" prop="quotationDate" width="120">
            <template #default="{ row }">
              <span>{{ parseTime(row.quotationDate, 'yyyy-MM-dd') }}</span>
            </template>
          </el-table-column>
          <el-table-column label="有效期至" prop="validUntil" width="120">
            <template #default="{ row }">
              <span v-if="row.validUntil">{{ parseTime(row.validUntil, 'yyyy-MM-dd') }}</span>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column label="报价状态" prop="quotationStatus" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusTagType(row.quotationStatus)">
                {{ getStatusLabel(row.quotationStatus) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="订单类型" align="center" width="120">
            <template #default="{ row }">
              <el-tag v-if="row.convertedOrderType === 2" type="warning" size="small"
                >样品单</el-tag
              >
              <el-tag v-else-if="row.convertedOrderType === 1" type="success" size="small"
                >销售订单</el-tag
              >
              <span v-else style="color: #c0c4cc">未转单</span>
            </template>
          </el-table-column>
          <el-table-column label="币种" align="center" prop="currency" width="80" />
          <el-table-column label="总金额" align="center" prop="totalAmount" width="120">
            <template #default="{ row }">
              <span>{{ formatCurrency(row.totalAmount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="销售员" align="center" prop="salesPersonName" width="100" />
          <el-table-column label="创建时间" align="center" prop="createTime" width="180">
            <template #default="{ row }">
              <span>{{ parseTime(row.createTime) }}</span>
            </template>
          </el-table-column>
          <TableActionColumn
            :actions="rowActions"
            align="left"
            class-name="small-padding fixed-width"
            :min-width="250"
            :max-visible="4"
            display="text"
            @action="handleRowAction"
          />
        </el-table>

        <pagination
          v-show="total > 0"
          :total="total"
          v-model:page="queryParams.pageNum"
          v-model:limit="queryParams.pageSize"
          @pagination="getList"
        />
      </el-card>
    </SkeletonTable>

    <!-- ============================================================ -->
    <!-- ✅ 新增/编辑弹窗 -->
    <!-- ============================================================ -->
    <SkeletonAction>
      <QuotationFormDialog
        ref="quotationFormDialogRef"
        v-model="open"
        :title="title"
        :form-data="form"
        :currency-options="currencyOptions"
        :draft-id="draftId"
        :draft-choices="draftChoices"
        :session="attachSession"
        @restore-draft="restoreAttachmentDraft"
        @discard-draft="discardAttachmentDraft"
        @submit="submitForm"
        @cancel="cancel"
      />
    </SkeletonAction>

    <!-- ============================================================ -->
    <!-- ✅ 详情弹窗 -->
    <!-- ============================================================ -->
    <SkeletonAction>
      <QuotationDetailDialog
        v-model="quotationDetailVisible"
        :quotation-id="quotationDetailId"
        :mode="quotationDetailMode"
        :is-sensitive="true"
        @submitted="getList"
      />
    </SkeletonAction>
    <!-- ============================================================ -->
    <!-- ✅ 流水弹窗 -->
    <!-- ============================================================ -->
    <SkeletonAction>
      <TraceTimeline v-model="traceDialogVisible" :trace-id="currentTraceId" />
    </SkeletonAction>
    <!-- 产品行内上传组件-->
    <SkeletonAction>
      <el-dialog
        v-model="attachmentDialogVisible"
        :title="`${attachmentQuotationNo || '报价单'} · 附件`"
        width="900px"
        append-to-body
        destroy-on-close
      >
        <!-- 列表行「附件」弹窗（dev-20261016-071，084 并入的裁定）：Upload + Manager 共用同一份会话队列；
             沿用同一 sourceType+sourceId（与报价单「整单附件」区同一队列键）、不加独立保存按钮
             （会话未注入 saveHandler）⇒ 上传只进「待保存」，随报价单「保存」落定 -->
        <template v-if="attachmentDialogVisible && attachmentQuotationId">
          <AttachmentUpload
            :session="attachDialogSession"
            class="attach-block"
            drop-zone
            button-text="上传附件"
          />
          <AttachmentManager
            :session="attachDialogSession"
            class="attach-block"
            title="附件"
            source-type="quotation"
            :source-no="attachmentQuotationNo"
          />
        </template>
      </el-dialog>
    </SkeletonAction>

    <!-- 操作预览器 提交审核-->
    <SkeletonAction>
      <OperationPreviewDialog
        v-model="previewVisible"
        :operation="previewOperation"
        :biz-id="previewBizId"
        :biz-no="previewBizNo"
        :status-text-map="quotationStatusTextMap"
        @success="getList"
      />
    </SkeletonAction>
    <!-- 发送报价 -->
    <SkeletonAction>
      <QuotationSendDialog
        v-model:visible="sendDialogVisible"
        :quotation-id="sendQuotationId"
        @success="getList"
      />
    </SkeletonAction>

    <!-- 操作预览器 -->
    <SkeletonAction>
      <OperationPreviewDialog
        v-model="previewVisible"
        :operation="previewOperation"
        :biz-id="previewBizId"
        :biz-no="previewBizNo"
        :status-text-map="quotationStatusTextMap"
        @success="getList"
      />
    </SkeletonAction>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'Quotation',
})

// ============================================================
// 1. 基础导入
// ============================================================
import { ref, reactive, computed, onMounted, onDeactivated } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import {
  CircleCheck,
  CircleClose,
  Collection,
  Connection,
  Delete,
  Edit,
  EditPen,
  Promotion,
  RefreshLeft,
  Switch,
  Upload,
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { useTable } from '@/composables/useTable'
import { quotationApi } from '@/api/sales/quotation'
import { roleApi } from '@/api/system/role'
import { QuotationStatusEnum } from '@/enums/sales'
import { parseTime, formatCurrency, download } from '@/utils/format'

// ============================================================
// 2. 组件导入
// ============================================================
import QuotationSearchForm from './components/QuotationSearchForm.vue'
import QuotationStats from './components/QuotationStats.vue'
import QuotationToolbar from './components/QuotationToolbar.vue'
import QuotationFormDialog from './components/QuotationFormDialog.vue'
import QuotationDetailDialog from './components/QuotationDetailDialog.vue' // ✅ 新增
import TraceTimeline from '@/components/TraceTimeline/index.vue'
import AttachmentUpload from '@/components/attachment/AttachmentUpload.vue'
import AttachmentManager from '@/components/attachment/AttachmentManager.vue'
import { attachmentRefApi } from '@/api/system/attachmentRef'
import {
  useAttachmentSession,
  type AttachmentChanges,
  type AttachmentSaveResult,
} from '@/composables/useAttachmentSession'
import { useAttachmentDraftInstance } from '@/composables/useAttachmentRefs'
import OperationPreviewDialog from '@/components/OperationPreviewDialog/index.vue'
import { getOperation } from '@/components/OperationPreviewDialog/registry'
import QuotationSendDialog from './components/QuotationSendDialog.vue'
import type { TableAction } from '@/components/common-ui/TableActionColumn/types'
const route = useRoute()
const router = useRouter()
// ============================================================
// 3. 状态选项
// ============================================================
const statusOptions = QuotationStatusEnum.items.map((item) => ({
  value: item.value,
  label: item.label,
}))

const currencyOptions = [
  { value: 'CNY', label: '人民币' },
  { value: 'USD', label: '美元' },
  { value: 'EUR', label: '欧元' },
  { value: 'JPY', label: '日元' },
  { value: 'HKD', label: '港币' },
]
// 状态
const traceDialogVisible = ref(false)
const currentTraceId = ref('')
const attachmentDialogVisible = ref(false)
const attachmentQuotationId = ref<number | null>(null)
const attachmentQuotationNo = ref('')
const attachmentTraceId = ref('')
/**
 * 列表行「附件」弹窗的附件会话（dev-20261016-071，084 并入的裁定）：
 * 沿用同一 sourceType+sourceId（→ 与报价单「整单附件」区**同一份待保存队列**，不另起键）；
 * **不注入 saveHandler**（⇒ 无独立保存按钮），弹窗里上传的附件只进「待保存」，
 * 随报价单「保存」一起落定（001 §7-1 / §2-4）。
 */
const attachDialogSession = useAttachmentSession({
  sourceType: 'quotation',
  sourceId: computed(() => attachmentQuotationId.value),
  traceId: computed(() => attachmentTraceId.value),
  viewPerm: 'sales:quotation:view',
  editPerm: 'sales:quotation:edit',
})
const previewVisible = ref(false)
const previewOperation = ref<any>(null)
const previewBizId = ref<number | null>(null)
const previewBizNo = ref('')
const sendDialogVisible = ref(false)
const sendQuotationId = ref<number>()
// ============================================================
// 4. useTable
// ============================================================
const {
  data: quotationList,
  loading,
  total,
  pageNum,
  pageSize,
  queryParams,
  getList,
  handleSearch: tableHandleSearch,
  handleReset: tableHandleReset,
} = useTable<any, any>({
  api: quotationApi.list,
  immediate: false,
  defaultParams: {
    quotationNo: undefined,
    inquiryNo: undefined,
    customerName: undefined,
    quotationStatus: undefined,
    startDate: undefined,
    endDate: undefined,
    orderByColumn: undefined,
    isAsc: undefined,
  },
})

// ============================================================
// 5. 日期范围
// ============================================================
const dateRange = ref<string[]>([])

// ============================================================
// 6. 统计数据
// ============================================================
const stats = ref<any>(null)

const loadStatistics = async () => {
  try {
    const res: any = await quotationApi.statistics()
    stats.value = res?.data || null
  } catch {
    stats.value = null
  }
}

// ============================================================
// 7. 表格状态
// ============================================================
const tableRef = ref<any>()
const ids = ref<number[]>([])
const single = ref(true)
const multiple = ref(true)
const selectedQuotation = ref<any>(null)

const handleSelectionChange = (selection: any[]) => {
  ids.value = selection.map((item) => item.quotationId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
  selectedQuotation.value = selection.length === 1 ? selection[0] : null
}

const handleSortChange = (column: any) => {
  if (column.prop && column.order) {
    queryParams.orderByColumn = column.prop
    queryParams.isAsc = column.order === 'ascending' ? 'asc' : 'desc'
  } else {
    queryParams.orderByColumn = undefined
    queryParams.isAsc = undefined
  }
  getList()
}

// ============================================================
// 8. 状态工具
// ============================================================
const getStatusTagType = (status: number) => {
  return QuotationStatusEnum.getTagProps(status).type || 'info'
}

const getStatusLabel = (status: number) => {
  const label = QuotationStatusEnum.getLabel(status)
  return label && label !== '未知' ? label : '未知状态'
}

// ============================================================
// 9. 行内操作权限
// ============================================================
const canEdit = (row: any) => {
  return (
    ![
      QuotationStatusEnum.SENT.value,
      QuotationStatusEnum.ACCEPTED.value,
      QuotationStatusEnum.REJECTED.value,
      QuotationStatusEnum.EXPIRED.value,
    ].includes(row.quotationStatus) && row.quotationStatus !== QuotationStatusEnum.COMPLETED.value
  )
}

const canDelete = (row: any) => {
  return ![
    QuotationStatusEnum.SENT.value,
    QuotationStatusEnum.ACCEPTED.value,
    QuotationStatusEnum.PENDING_REVIEW.value,
    QuotationStatusEnum.APPROVED.value,
    QuotationStatusEnum.MODIFYING.value,
    QuotationStatusEnum.COMPLETED.value,
  ].includes(row.quotationStatus)
}

const canReQuote = (row: any) => {
  return [QuotationStatusEnum.REJECTED.value, QuotationStatusEnum.EXPIRED.value].includes(
    row.quotationStatus
  )
}

const canConvert = (row: any) => {
  return row.quotationStatus === QuotationStatusEnum.ACCEPTED.value && row.quotationType !== 2
}

const canConvertToSample = (row: any) => {
  return row.quotationType !== 1 && row.quotationStatus === QuotationStatusEnum.ACCEPTED.value
}

const canSubmitReview = (row: any) => {
  return [QuotationStatusEnum.DRAFT.value, QuotationStatusEnum.MODIFYING.value].includes(
    row.quotationStatus
  )
}

const rowActions: TableAction<any>[] = [
  { key: 'trace', label: '查看流水', icon: Connection, type: 'info' },
  { key: 'edit', label: '修改', icon: Edit, visible: ({ row }) => canEdit(row) },
  {
    key: 'delete',
    label: '删除',
    icon: Delete,
    type: 'danger',
    visible: ({ row }) => canDelete(row),
  },
  {
    key: 'send',
    label: '发送报价',
    icon: Promotion,
    type: 'warning',
    permission: 'sales:quotation:edit',
    visible: ({ row }) => row.quotationStatus === QuotationStatusEnum.APPROVED.value,
  },
  {
    key: 'reQuote',
    label: '重新报价',
    icon: RefreshLeft,
    type: 'warning',
    permission: 'sales:quotation:edit',
    visible: ({ row }) => canReQuote(row),
  },
  {
    key: 'convert',
    label: '转为订单',
    icon: Switch,
    type: 'success',
    permission: 'sales:quotation:edit',
    visible: ({ row }) => canConvert(row),
  },
  {
    key: 'convertToSample',
    label: '转为样品单',
    icon: Collection,
    type: 'warning',
    permission: 'sales:quotation:edit',
    visible: ({ row }) => canConvertToSample(row),
  },
  {
    key: 'modify',
    label: '改单',
    icon: EditPen,
    type: 'warning',
    permission: 'sales:quotation:edit',
    visible: ({ row }) => row.quotationStatus === QuotationStatusEnum.COMPLETED.value,
  },
  {
    key: 'submitReview',
    label: '提交审核',
    icon: Upload,
    permission: 'sales:quotation:edit',
    visible: ({ row }) => canSubmitReview(row),
  },
  {
    key: 'customerConfirm',
    label: '客户确认',
    icon: CircleCheck,
    type: 'success',
    permission: 'sales:quotation:edit',
    visible: ({ row }) => row.quotationStatus === QuotationStatusEnum.SENT.value,
  },
  {
    key: 'customerReject',
    label: '客户拒绝',
    icon: CircleClose,
    type: 'danger',
    permission: 'sales:quotation:edit',
    visible: ({ row }) => row.quotationStatus === QuotationStatusEnum.SENT.value,
  },
  {
    key: 'approve',
    label: '审核通过',
    icon: CircleCheck,
    type: 'success',
    permission: 'sales:quotation:approve',
    visible: ({ row }) => row.quotationStatus === QuotationStatusEnum.PENDING_REVIEW.value,
  },
  {
    key: 'reject',
    label: '审核驳回',
    icon: CircleClose,
    type: 'danger',
    permission: 'sales:quotation:approve',
    visible: ({ row }) => row.quotationStatus === QuotationStatusEnum.PENDING_REVIEW.value,
  },
]

const handleRowAction = (key: string, row: any) => {
  const handlers: Record<string, () => void> = {
    trace: () => showTrace(row),
    edit: () => handleUpdate(row),
    delete: () => handleDelete(row),
    send: () => handleSend(row),
    reQuote: () => void handleReQuote(row),
    convert: () => void handleConvert(row),
    convertToSample: () => void handleConvertToSample(row),
    modify: () => void handleModify(row),
    submitReview: () => handleSubmitReview(row),
    customerConfirm: () => void handleCustomerConfirm(true, row),
    customerReject: () => void handleCustomerConfirm(false, row),
    approve: () => void handleReview(true, row),
    reject: () => void handleReview(false, row),
  }
  handlers[key]?.()
}

// ============================================================
// 10. 状态机
// ============================================================
const quotationActions = computed(() => {
  const q = selectedQuotation.value
  const status = q?.quotationStatus
  const completed = status === QuotationStatusEnum.COMPLETED.value
  return {
    canSend: status === QuotationStatusEnum.APPROVED.value,
    canSubmitReview: [
      QuotationStatusEnum.DRAFT.value,
      QuotationStatusEnum.MODIFYING.value,
    ].includes(status),
    canApprove: status === QuotationStatusEnum.APPROVED.value,
    canCustomerConfirm: status === QuotationStatusEnum.SENT.value,
    canConvert: status === QuotationStatusEnum.ACCEPTED.value && !completed,
    canConvertToSample: status === QuotationStatusEnum.ACCEPTED.value && !completed,
    canReQuote: [QuotationStatusEnum.REJECTED.value, QuotationStatusEnum.EXPIRED.value].includes(
      status
    ),
    canDelete:
      ![
        QuotationStatusEnum.SENT.value,
        QuotationStatusEnum.ACCEPTED.value,
        QuotationStatusEnum.PENDING_REVIEW.value,
        QuotationStatusEnum.APPROVED.value,
        QuotationStatusEnum.MODIFYING.value,
        QuotationStatusEnum.COMPLETED.value,
      ].includes(status) && !completed,
    canEdit:
      ![
        QuotationStatusEnum.SENT.value,
        QuotationStatusEnum.ACCEPTED.value,
        QuotationStatusEnum.REJECTED.value,
        QuotationStatusEnum.EXPIRED.value,
        QuotationStatusEnum.APPROVED.value,
      ].includes(status) && !completed,
    canModify: status === QuotationStatusEnum.COMPLETED.value,
  }
})
const quotationStatusTextMap = computed(() => {
  const map: Record<number, string> = {}
  QuotationStatusEnum.items.forEach((item: any) => {
    map[item.value] = item.label
  })
  return map
})
// ============================================================
// 11. 弹窗状态
// ============================================================
const open = ref(false)
const quotationFormDialogRef = ref<InstanceType<typeof QuotationFormDialog>>()
const title = ref('')
const quotationDetailVisible = ref(false) // ✅ 新增
const quotationDetailId = ref<number>(0) // ✅ 新增
const quotationDetailMode = ref<'view' | 'submitReview'>('view') // ✅ 新增
const userStore = useUserStore()
/** 草稿身份按报价实例隔离；刷新恢复原实例，新增始终创建新实例。 */
const draftSession = useAttachmentDraftInstance('quotation', {
  snapshot: () => form,
  restore: (data) => {
    resetForm()
    Object.assign(form, data)
  },
})
const { draftId, choices: draftChoices, complete: clearDraftId } = draftSession
function restoreAttachmentDraft(id: string) {
  // 恢复是整单覆盖（客户/产品/明细+附件）：先确认（dev-20261016-042）
  void draftSession.confirmRestore(id)
}
function discardAttachmentDraft(id: string) {
  // 放弃草稿：删本地记录/队列 + 库中草稿行（dev-20261016-042）
  void draftSession.confirmDiscard(id)
}

// ============================================================
// 12. 表单数据
// ============================================================
const form = reactive({
  quotationId: undefined as number | undefined,
  quotationNo: '',
  quotationType: 1,
  customerId: undefined as number | undefined,
  customerName: '',
  contactPerson: '',
  contactPhone: '',
  quotationDate: '',
  validUntil: '',
  currency: 'CNY',
  exchangeRate: 1.0,
  subtotalAmount: 0,
  taxRate: 0,
  taxAmount: 0,
  totalAmount: 0,
  discountAmount: 0,
  finalAmount: 0,
  quotationStatus: 0,
  salesPersonId: undefined as number | undefined,
  salesPersonName: '',
  remark: '',
  items: [] as Array<{
    productId?: number
    productCode: string
    productName: string
    quantity: number
    unitPrice: number
    amount: number
    unit: string
    // 编码参数（样品类型，2026-09-02：静默携带，随报价保存建档写入 product.spec_json）
    serialNo?: string
    panelType?: string
    panelFeature?: string
    circuitType?: string
    circuitFeature?: string
  }>,
})

// ============================================================
// 13. 销售负责人
// ============================================================
const salesPersonOptions = ref<Array<{ userId: number; nickName: string; userName: string }>>([])

const loadSalesPersons = async () => {
  try {
    const res: any = await roleApi.allocatedList({ roleId: 7, pageNum: 1, pageSize: 999 })
    if (res.code === 200 && res.data?.records) {
      salesPersonOptions.value = res.data.records.map((u: any) => ({
        userId: u.userId,
        nickName: u.nickName || '',
        userName: u.userName,
      }))
    }
  } catch (error) {
    console.error('加载销售负责人失败:', error)
  }
}

// ============================================================
// 14. 表单操作
// ============================================================
const resetForm = () => {
  Object.assign(form, {
    quotationId: 0,
    quotationNo: '',
    quotationType: 1,
    customerId: undefined,
    customerName: '',
    contactPerson: '',
    contactPhone: '',
    quotationDate: '',
    validUntil: '',
    currency: 'CNY',
    exchangeRate: 1.0,
    subtotalAmount: 0,
    taxRate: 0,
    taxAmount: 0,
    totalAmount: 0,
    discountAmount: 0,
    finalAmount: 0,
    quotationStatus: 0,
    salesPersonId: undefined,
    salesPersonName: '',
    remark: '',
    items: [],
  })
}

/**
 * 报价单编辑用的附件会话（dev-20261016-071）：**由容器创建、显式下传给表单弹窗**（不得用全局单例）。
 * - 队列键与列表「附件」弹窗完全一致（同一 sourceType+sourceId，键算法见 useAttachmentRefs.ts:407-413）；
 * - 落定（建引用）只由单据「保存」负责：待保存队列随 add/edit 的 attachments 提交（066 add / 081 edit）；
 * - saveHandler 只做「附件信息类」提交（二级类型/备注/排序 → 引用批量更新），不落定（001 §7-1 方案 A）。
 */
const attachSession = useAttachmentSession({
  sourceType: 'quotation',
  sourceId: computed(() => (form as any).quotationId || null),
  draftId: computed(() => draftId.value || ''),
  traceId: computed(() => (form as any).traceId || ''),
  viewPerm: 'sales:quotation:view',
  editPerm: 'sales:quotation:edit',
  saveHandler: saveAttachInfo,
})

/** 附件信息类提交（保存处理器的唯一出口）：只认 updates；uploads 随单据保存落定，这里明确拒绝而不是静默吞掉 */
async function saveAttachInfo(changes: AttachmentChanges): Promise<AttachmentSaveResult> {
  if (changes.uploads.length) {
    throw new Error('待保存附件随报价单「保存」落定，此处只提交附件信息类改动')
  }
  if (!changes.updates.length) return {}
  const items = changes.updates.map((u) => ({
    refId: u.refId,
    documentType: u.documentType,
    remark: u.remark,
    sortOrder: u.sortOrder,
  }))
  await attachmentRefApi.batchUpdate(items)
  return { acceptedUpdates: items.map((i) => i.refId) }
}

/** 保存前校验用：待保存队列里尚未选二级资料类型的条数（沿用 032 口径，谓词同 useAttachmentRefs:437） */
const attachMissingTypeCount = computed(
  () => attachSession.pending.value.filter((e) => e.state !== 'binding' && !e.documentType).length
)

/** 待保存队列 → 报价单保存请求的 attachments（064 的 QuotationAttachmentDTO：attachmentId/documentType/productId/productCode） */
function attachPayload() {
  return attachSession.getChanges({ kind: 'all' }).uploads.map((u) => ({
    attachmentId: u.attachmentId,
    documentType: u.documentType ?? null,
    productId: productIdOfOwnership(u.ownershipKey),
    productCode: u.productCode ?? null,
  }))
}

/** 上传归属键 → 正式产品 id：只有 `product:<id>` 能直接给出；`row:<键>` 交给后端按明细编码解析 */
function productIdOfOwnership(key?: string | null): number | null {
  const matched = /^product:(\d+)$/.exec(key || '')
  return matched ? Number(matched[1]) : null
}

/**
 * 单据保存**成功后**的附件收尾（承 077 §4 的 bindPending 语义：成功清队列、失败保留）：
 * 重取引用 → 按 attachmentId 取**真实 refId** → acceptSaved；没落定的仍留在「待保存」待重试。
 * 不做任何前端绑定请求（落定已在单据保存事务内完成）。
 */
async function finalizeAttachments(submittedIds: number[]) {
  if (!submittedIds.length) return
  await attachSession.refresh()
  const sourceId = Number((form as any).quotationId)
  const refIdByAttachment = new Map<number, number>()
  for (const row of attachSession.refs.value) {
    const attachmentId = Number((row as any).attachmentId)
    if (!attachmentId || row.deleted || Number(row.sourceId) !== sourceId) continue
    refIdByAttachment.set(attachmentId, row.id)
  }
  const acceptedUploads = submittedIds
    .filter((id) => refIdByAttachment.has(id))
    .map((id) => ({ attachmentId: id, refId: refIdByAttachment.get(id) as number }))
  if (acceptedUploads.length) attachSession.acceptSaved({ acceptedUploads })
  const remaining = submittedIds.length - acceptedUploads.length
  if (remaining) ElMessage.warning(`有 ${remaining} 个附件未落定，仍保留在「待保存」，请再保存一次`)
}

/**
 * 旧「行级草稿键」队列衔接（051 静态守卫点名的衔接点）：051 之前，明细行弹窗的待保存队列按
 * `<draftId>::r:<产品编码>` 单独存（键格式见 useAttachmentRefs.ts:81 的 STORAGE_PREFIX）。
 * 现在统一进共享队列——把存量键里的条目并入当前队列并删掉旧键，使其照常显示、随单据「保存」落定（001 §7-1）。
 */
function migrateLegacyRowQueues() {
  const base = draftId.value
  if (!base) return
  const prefix = `attach-pending:v2:${userStore.userId ?? 'anonymous'}:quotation:${base}::r:`
  const legacyKeys: string[] = []
  try {
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i)
      if (key && key.startsWith(prefix)) legacyKeys.push(key)
    }
  } catch {
    return
  }
  if (!legacyKeys.length) return
  const merged = [...attachSession.pending.value]
  const seen = new Set(merged.map((e) => e.attachmentId))
  let moved = 0
  for (const key of legacyKeys) {
    try {
      const parsed = JSON.parse(localStorage.getItem(key) || '[]')
      for (const entry of Array.isArray(parsed) ? parsed : []) {
        const attachmentId = Number(entry?.attachmentId)
        if (!attachmentId || seen.has(attachmentId)) continue
        merged.push({ ...entry, attachmentId, state: 'pending' } as any)
        seen.add(attachmentId)
        moved++
      }
      localStorage.removeItem(key)
    } catch {
      /* 单键坏数据不阻断其余 */
    }
  }
  if (!moved) return
  attachSession.pending.value = merged
  attachSession.persist()
}

// 打开/切换草稿实例时衔接旧行级队列（只在确有旧键时才动；纯本地合并，不发请求）
watch([open, draftId], () => {
  if (open.value) migrateLegacyRowQueues()
})

const submitForm = async () => {
  try {
    // 资料类型必选（dev-20261015-032）：保存前拦住缺二级类型的待保存附件，避免「保存成功但附件没落定」
    if (attachMissingTypeCount.value > 0) {
      ElMessage.warning(`有 ${attachMissingTypeCount.value} 个附件未选择资料类型，请先选择后再保存`)
      return
    }
    // 待保存附件随单据保存一并提交，由后端在同一事务内建引用（066 add 侧 / 081 edit 侧）
    const attachments = attachPayload()
    const submittedIds = attachments.map((a) => a.attachmentId)
    if (form.quotationId !== undefined && form.quotationId > 0) {
      // 修改：保存后不关弹窗，重新拉详情回填 product_id（产品资料可挂载）
      await quotationApi.edit({ ...(form as any), attachments } as any)
      const res: any = await quotationApi.getInfo(form.quotationId)
      Object.assign(form, res.data)
      await finalizeAttachments(submittedIds)
      title.value = `修改报价单【${res.data?.quotationNo || ''}】`
      ElMessage.success('保存成功')
      getList()
    } else {
      // 新增：保存后拿到新 ID，不关弹窗切到修改态，可继续编辑/挂资料
      const res: any = await quotationApi.add({ ...(form as any), attachments } as any)
      const newId: number | undefined = res?.data
      if (newId) {
        form.quotationId = newId
        const info: any = await quotationApi.getInfo(newId)
        Object.assign(form, info.data)
        await finalizeAttachments(submittedIds)
        clearDraftId() // 保存成功：清空草稿标识
        title.value = `修改报价单【${info.data?.quotationNo || ''}】`
      }
      ElMessage.success('保存成功，可继续编辑')
      getList()
    }
  } catch (e: any) {
    console.error('保存报价单失败:', e)
  }
}

const cancel = () => {
  draftSession.close()
  open.value = false
  resetForm()
}

// ============================================================
// 15. 操作按钮函数
// ============================================================
const handleAdd = () => {
  draftSession.close()
  resetForm()
  draftSession.startNew()
  form.quotationDate = new Date().toISOString().split('T')[0]
  const validUntil = new Date()
  validUntil.setDate(validUntil.getDate() + 30)
  form.validUntil = validUntil.toISOString().split('T')[0]
  form.salesPersonId = userStore.userId
  form.salesPersonName = userStore.nickName || ''
  open.value = true
  title.value = '新增报价单'
}

const handleUpdate = (row?: any) => {
  draftSession.close()
  resetForm()
  const quotationId = row?.quotationId || ids.value[0]
  quotationApi.getInfo(quotationId).then((response: any) => {
    Object.assign(form, response.data)
    form.quotationId = response.data?.quotationId
    open.value = true
    title.value = `修改报价单【${response.data?.quotationNo || ''}】`
  })
}

const handleDelete = (row?: any) => {
  const quotationIds = row?.quotationId || ids.value
  ElMessageBox.confirm('是否确认删除？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => quotationApi.remove(quotationIds))
    .then(() => {
      getList()
      ElMessage.success('删除成功')
    })
    .catch(() => {})
}

const handleExport = () => {
  ElMessageBox.confirm('是否确认导出？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      const loading = ElLoading.service({ text: '导出中...', lock: true })
      return quotationApi
        .export(queryParams)
        .then((response: any) => download(response, '报价单列表.xlsx'))
        .finally(() => loading.close())
    })
    .catch(() => {})
}

// ============================================================
// 附件
// ============================================================
const handleAttachment = () => {
  const q = selectedQuotation.value
  if (!q) {
    ElMessage.warning('请先选择一条报价单')
    return
  }
  attachmentQuotationId.value = q.quotationId
  attachmentQuotationNo.value = q.quotationNo || ''
  attachmentTraceId.value = q.traceId || ''
  attachmentDialogVisible.value = true
}

// ============================================================
// 发送报价
// ============================================================
const handleSend = (row?: any) => {
  const quotationId = row?.quotationId || ids.value[0]
  if (!quotationId) {
    ElMessage.warning('请先选择一条报价单')
    return
  }
  sendQuotationId.value = quotationId
  sendDialogVisible.value = true
}

// ============================================================
// 操作预览器
// ============================================================
const openPreview = async (opKey: string, row?: any) => {
  const quotationId = row?.quotationId || ids.value[0]
  if (!quotationId) {
    ElMessage.warning('请先选择一条报价单')
    return
  }
  let op = getOperation(opKey)
  if (!op) {
    ElMessage.warning('未知操作')
    return
  }
  // 转为样品单时，打样数量默认取报价单明细数量求和
  if (opKey === 'quotation.toSample') {
    try {
      const res: any = await quotationApi.getItems(quotationId)
      const items: any[] = res?.data || []
      const total = items.reduce((s: number, it: any) => s + (Number(it.quantity) || 0), 0)
      op = {
        ...op,
        fields: (op.fields || []).map((f: any) =>
          f.key === 'sampleQty' ? { ...f, defaultValue: total > 0 ? total : 1 } : f
        ),
      }
    } catch (e) {
      console.error('加载报价单明细失败', e)
    }
  }
  previewOperation.value = op
  previewBizId.value = quotationId
  previewBizNo.value = row?.quotationNo || selectedQuotation.value?.quotationNo || ''
  previewVisible.value = true
}

// ============================================================
// 转为订单
// ============================================================
const handleConvert = (row?: any) => openPreview('quotation.convert', row)

// ============================================================
// 转为样品单
// ============================================================
const handleConvertToSample = (row?: any) => openPreview('quotation.toSample', row)

// ============================================================
// 审核
// ============================================================
const handleReview = (approved: boolean, row?: any) =>
  openPreview(approved ? 'quotation.approve' : 'quotation.reject', row)

// ============================================================
// 客户确认/拒绝
// ============================================================
const handleCustomerConfirm = (confirmed: boolean, row?: any) =>
  openPreview(confirmed ? 'quotation.customerConfirm' : 'quotation.customerReject', row)

// ============================================================
// 重新报价
// ============================================================
const handleReQuote = async (row?: any) => {
  const quotationId = row?.quotationId || ids.value[0]
  if (!quotationId) return
  try {
    await quotationApi.changeStatus(quotationId, QuotationStatusEnum.DRAFT.value)
    ElMessage.success('已重新报价')
    getList()
  } catch (e: any) {
    ElMessage.error(e?.message || '重新报价失败')
  }
}

// ============================================================
// 改单
// ============================================================
const handleModify = async (row?: any) => {
  const quotationId = row?.quotationId || ids.value[0]
  if (!quotationId) return
  try {
    await ElMessageBox.confirm('确认将该已完成报价单改为改单状态？', '改单确认', {
      confirmButtonText: '确定改单',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await quotationApi.modify(quotationId)
    ElMessage.success('已改为改单状态')
    getList()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '改单失败')
  }
}

// ============================================================
// 复制报价
// ============================================================
const handleCopy = (row?: any) => {
  const quotationId = row?.quotationId || ids.value[0]
  quotationApi.copy(quotationId).then((response: any) => {
    resetForm()
    Object.assign(form, response.data)
    // 复制单走新增分支（2026-09-02：submitForm 以 quotationId!==undefined 判修改，复制源无 id 需显式清掉）
    form.quotationId = undefined
    form.quotationNo = `COPY_${form.quotationNo}`
    open.value = true
    title.value = '复制报价单'
    ElMessage.success('复制成功')
  })
}

// ============================================================
// 导出PDF
// ============================================================
const handleExportPdf = (row?: any) => {
  const quotationId = row?.quotationId || ids.value[0]
  if (!quotationId) {
    ElMessage.warning('请先选择一条报价单')
    return
  }
  window.open(`/print/quotation/${quotationId}`, '_blank')
}

// ============================================================
// 导出Excel
// ============================================================
const handleExportExcel = (row?: any) => {
  const quotationId = row?.quotationId || ids.value[0]
  quotationApi.exportExcel(quotationId).then((response: any) => {
    download(response, `报价单_${quotationId}.xlsx`)
  })
}

// ============================================================
// 提交审核
// ============================================================
const handleSubmitReview = async (row?: any) => {
  const quotationId = row?.quotationId || ids.value[0]
  if (!quotationId) return
  quotationDetailId.value = quotationId
  quotationDetailMode.value = 'submitReview'
  quotationDetailVisible.value = true
}

// ============================================================
// 16. 查看详情（完善）
// ============================================================
const handleView = (row: any) => {
  quotationDetailId.value = row.quotationId
  quotationDetailMode.value = 'view'
  quotationDetailVisible.value = true
}

// ============================================================
// 17. 占位函数
// ============================================================
// 方法
const showTrace = (row: any) => {
  currentTraceId.value = row.traceId || ''
  traceDialogVisible.value = true
}

// ============================================================
// 18. 搜索/重置
// ============================================================
const handleSearch = () => {
  if (dateRange.value?.length === 2) {
    queryParams.startDate = dateRange.value[0]
    queryParams.endDate = dateRange.value[1]
  } else {
    queryParams.startDate = undefined
    queryParams.endDate = undefined
  }
  tableHandleSearch()
}

const handleReset = () => {
  dateRange.value = []
  Object.assign(queryParams, {
    quotationNo: undefined,
    inquiryNo: undefined,
    customerName: undefined,
    quotationStatus: undefined,
    startDate: undefined,
    endDate: undefined,
    orderByColumn: undefined,
    isAsc: undefined,
  })
  tableHandleReset()
}

// ============================================================
// 19. 初始化
// ============================================================
onDeactivated(() => {
  open.value = false
})
onMounted(async () => {
  if (draftSession.resumeId && draftSession.restore(draftSession.resumeId)) {
    open.value = true
    title.value = '新增报价单（已恢复草稿）'
  }
  await getList()
  loadStatistics()
  loadSalesPersons()
  const bizId = Number(route.query.bizId)
  if (!bizId) return
  try {
    const res = await quotationApi.getInfo(bizId)
    if (!res?.data) return
    handleView(res.data)
    const { bizId: _bizId, ...query } = route.query
    router.replace({ path: route.path, query })
  } catch {
    // 目标不存在或无权限时保持正常列表
  }
})
</script>

<style scoped>
.table-card {
  margin-bottom: 16px;
}
.attach-block {
  margin-bottom: 8px;
}
</style>
