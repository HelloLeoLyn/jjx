// views/sales/quotation/composables/useQuotation.ts
import { ref, reactive, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { useTable } from '@/composables/useTable'
import { quotationApi } from '@/api/sales/quotation'
import { customerApi } from '@/api/sales/customer'
import { listProduct } from '@/api/product'
import { roleApi } from '@/api/system/role'
import { getOperation } from '@/components/OperationPreviewDialog/registry'
import { QuotationStatusEnum } from '@/enums/sales'
import { download } from '@/utils/format'

export function useQuotation() {
  const route = useRoute()
  const userStore = useUserStore()

  // ============================================================
  // 1. 利用通用 useTable
  // ============================================================
  const {
    data: quotationList,
    loading,
    total,
    pageNum,
    pageSize,
    queryParams,
    getList,
    handleSearch,
    handleReset,
    handlePageChange,
    handleSizeChange,
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
  // 2. 报价单特有状态
  // ============================================================
  const tableRef = ref<any>()
  const ids = ref<number[]>([])
  const single = ref(true)
  const multiple = ref(true)
  const selectedQuotation = ref<any>(null)
  const stats = ref<any>(null)
  const dateRange = ref<string[]>([])

  // ============================================================
  // 3. 弹窗状态
  // ============================================================
  const open = ref(false)
  const title = ref('')
  const quotationDetailVisible = ref(false)
  const quotationDetailId = ref<number>()
  const quotationDetailMode = ref<'view' | 'submitReview'>('view')
  const traceDialogVisible = ref(false)
  const currentTraceId = ref('')
  const attachmentDialogVisible = ref(false)
  const attachmentQuotationId = ref<number | null>(null)
  const attachmentQuotationNo = ref('')
  const attachmentTraceId = ref('')
  const previewVisible = ref(false)
  const previewOperation = ref<any>(null)
  const previewBizId = ref<number | null>(null)
  const previewBizNo = ref('')
  const sendDialogVisible = ref(false)
  const sendQuotationId = ref<number>()

  // ============================================================
  // 4. 表单数据
  // ============================================================
  const form = reactive({
    quotationId: undefined as number | undefined,
    quotationNo: '',
    quotationType: 1,
    customerId: undefined as number | undefined,
    customerName: '',
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
    }>,
  })

  // ============================================================
  // 5. 状态机（直接使用 QuotationStatusEnum）
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
      canApprove: status === QuotationStatusEnum.PENDING_REVIEW.value,
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
        ].includes(status) && !completed,
      canModify: status === QuotationStatusEnum.COMPLETED.value,
    }
  })

  // ============================================================
  // 6. 状态工具（直接使用 QuotationStatusEnum）
  // ============================================================
  const getStatusTagType = (status: number) => {
    return QuotationStatusEnum.getTagProps(status).type || 'info'
  }

  const getStatusLabel = (status: number) => {
    const label = QuotationStatusEnum.getLabel(status)
    return label && label !== '未知' ? label : '未知状态'
  }

  const statusOptions = QuotationStatusEnum.items.map((item) => ({
    value: item.value,
    label: item.label,
  }))

  // ============================================================
  // 7. 币种选项
  // ============================================================
  const currencyOptions = [
    { value: 'CNY', label: '人民币' },
    { value: 'USD', label: '美元' },
    { value: 'EUR', label: '欧元' },
    { value: 'JPY', label: '日元' },
    { value: 'HKD', label: '港币' },
  ]

  // ============================================================
  // 8. 选择变化
  // ============================================================
  const handleSelectionChange = (selection: any[]) => {
    ids.value = selection.map((item) => item.quotationId)
    single.value = selection.length !== 1
    multiple.value = !selection.length
    selectedQuotation.value = selection.length === 1 ? selection[0] : null
  }

  // ============================================================
  // 9. 排序
  // ============================================================
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
  // 10. 统计
  // ============================================================
  const loadStatistics = async () => {
    try {
      const res: any = await quotationApi.statistics()
      stats.value = res?.data || null
    } catch {
      stats.value = null
    }
  }

  // ============================================================
  // 11. 操作预览器
  // ============================================================
  const openPreview = async (opKey: string, row?: any) => {
    const quotationId = row?.quotationId || ids.value[0]
    if (!quotationId) return
    let op = getOperation(opKey)
    if (!op) return

    if (opKey === 'quotation.toSample') {
      try {
        const res: any = await quotationApi.getItems(quotationId)
        const items: any[] = res?.data || []
        const qty = items.reduce((s: number, it: any) => s + (Number(it.quantity) || 0), 0)
        op = {
          ...op,
          fields: (op.fields || []).map((f: any) =>
            f.key === 'sampleQty' ? { ...f, defaultValue: qty > 0 ? qty : 1 } : f
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
  // 12. 各种操作
  // ============================================================
  const handleAdd = () => {
    resetForm()
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
    resetForm()
    const quotationId = row?.quotationId || ids.value[0]
    quotationApi.getInfo(quotationId).then((response: any) => {
      Object.assign(form, response.data)
      open.value = true
      title.value = `修改报价单【${response.data?.quotationNo || ''}】`
    })
  }

  const handleDelete = (row?: any) => {
    const quotationIds = row?.quotationId || ids.value
    ElMessageBox.confirm('是否确认删除报价单号为"' + quotationIds + '"的数据项？', '警告', {
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
    ElMessageBox.confirm('是否确认导出所有报价单数据项？', '警告', {
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

  const handleSend = (row?: any) => {
    const quotationId = row?.quotationId || ids.value[0]
    if (!quotationId) {
      ElMessage.warning('请先选中一行报价单')
      return
    }
    sendQuotationId.value = quotationId
    sendDialogVisible.value = true
  }

  const handleConvert = (row?: any) => openPreview('quotation.convert', row)
  const handleConvertToSample = (row?: any) => openPreview('quotation.toSample', row)

  const handleSubmitReview = async (row?: any) => {
    const quotationId = row?.quotationId || ids.value[0]
    if (!quotationId) return
    quotationDetailId.value = quotationId
    quotationDetailMode.value = 'submitReview'
    quotationDetailVisible.value = true
  }

  const handleReview = (approved: boolean, row?: any) =>
    openPreview(approved ? 'quotation.approve' : 'quotation.reject', row)

  const handleCustomerConfirm = (confirmed: boolean, row?: any) =>
    openPreview(confirmed ? 'quotation.customerConfirm' : 'quotation.customerReject', row)

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

  const handleCopy = (row?: any) => {
    const quotationId = row?.quotationId || ids.value[0]
    quotationApi.copy(quotationId).then((response: any) => {
      Object.assign(form, response.data)
      form.quotationNo = `COPY_${form.quotationNo}`
      open.value = true
      title.value = '复制报价单'
      ElMessage.success('复制成功')
    })
  }

  const handleExportPdf = (row?: any) => {
    const quotationId = row?.quotationId || ids.value[0]
    if (!quotationId) {
      ElMessage.warning('请先选择报价单')
      return
    }
    window.open(`/print/quotation/${quotationId}`, '_blank')
  }

  const handleExportExcel = (row?: any) => {
    const quotationId = row?.quotationId || ids.value[0]
    quotationApi.exportExcel(quotationId).then((response: any) => {
      download(response, `报价单_${quotationId}.xlsx`)
    })
  }

  const handleView = (row: any) => {
    quotationDetailId.value = row.quotationId
    quotationDetailMode.value = 'view'
    quotationDetailVisible.value = true
  }

  const showTrace = (row: any) => {
    currentTraceId.value = row.traceId || ''
    traceDialogVisible.value = true
  }

  const handleAttachment = () => {
    const q = selectedQuotation.value
    if (!q) return
    attachmentQuotationId.value = q.quotationId
    attachmentQuotationNo.value = q.quotationNo || ''
    attachmentTraceId.value = q.traceId || ''
    attachmentDialogVisible.value = true
  }

  const gotoInquiry = (row: any) => window.open('/sales/inquiry', '_blank')

  // ============================================================
  // 13. 表单操作
  // ============================================================
  const resetForm = () => {
    Object.assign(form, {
      quotationId: undefined,
      quotationNo: '',
      quotationType: 1,
      customerId: undefined,
      customerName: '',
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

  const submitForm = () => {
    // 由 QuotationFormDialog 内部处理
  }

  const cancel = () => {
    open.value = false
    resetForm()
  }

  // ============================================================
  // 14. 销售负责人选项
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
  // 15. 初始化
  // ============================================================
  const init = async () => {
    await getList()
    await loadStatistics()
    const targetId = route.query.quotationId
    if (targetId) {
      // 定位逻辑
    }
  }

  // ============================================================
  // 16. 返回
  // ============================================================
  return {
    // 来自 useTable
    quotationList,
    loading,
    total,
    pageNum,
    pageSize,
    queryParams,
    getList,
    handleSearch,
    handleReset,
    handlePageChange,
    handleSizeChange,

    // 特有状态
    tableRef,
    ids,
    single,
    multiple,
    selectedQuotation,
    stats,
    dateRange,
    quotationActions,
    salesPersonOptions,
    statusOptions,
    currencyOptions,

    // 弹窗状态
    open,
    title,
    quotationDetailVisible,
    quotationDetailId,
    quotationDetailMode,
    traceDialogVisible,
    currentTraceId,
    attachmentDialogVisible,
    attachmentQuotationId,
    attachmentQuotationNo,
    attachmentTraceId,
    previewVisible,
    previewOperation,
    previewBizId,
    previewBizNo,
    sendDialogVisible,
    sendQuotationId,

    // 表单
    form,

    // 工具方法
    getStatusTagType,
    getStatusLabel,

    // 操作方法
    handleSelectionChange,
    handleSortChange,
    loadStatistics,
    loadSalesPersons,
    openPreview,
    handleAdd,
    handleUpdate,
    handleDelete,
    handleExport,
    handleSend,
    handleConvert,
    handleConvertToSample,
    handleSubmitReview,
    handleReview,
    handleCustomerConfirm,
    handleReQuote,
    handleModify,
    handleCopy,
    handleExportPdf,
    handleExportExcel,
    handleView,
    showTrace,
    handleAttachment,
    gotoInquiry,
    submitForm,
    cancel,
    resetForm,
    init,
  }
}
