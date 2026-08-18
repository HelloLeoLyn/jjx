<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" :inline="true" label-width="80px">
        <el-form-item label="订单号" prop="orderNo">
          <el-input
            v-model="queryParams.orderNo"
            placeholder="请输入订单号"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="客户名称" prop="customerName">
          <el-input
            v-model="queryParams.customerName"
            placeholder="请输入客户名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="订单状态" prop="orderStatus">
          <el-select
            v-model="queryParams.orderStatus"
            placeholder="请选择订单状态"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="item in SalesOrderStatusEnum.items"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="订单日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮区域 -->
    <el-card class="operation-card" shadow="never">
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Plus" v-hasPermi="['sales:order:add']" @click="handleAdd">新增</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" plain icon="Edit" v-hasPermi="['sales:order:edit']" :disabled="single" @click="handleUpdate">
            修改
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain icon="Delete" v-hasPermi="['sales:order:delete']" :disabled="multiple" @click="handleDelete">
            删除
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="Download" v-hasPermi="['sales:order:export']" @click="handleExport">导出</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="info" plain icon="Document" v-hasPermi="['sales:order:export']" @click="handleExportPdf">导出PDF</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" plain icon="DocumentCopy" v-hasPermi="['sales:order:export']" @click="handleExportExcel">导出Excel</el-button>
          <el-button type="warning" plain icon="CopyDocument" v-hasPermi="['sales:order:add']" :disabled="!single" @click="handleCopySelected">复制</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="orderList"
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
        border
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="订单号" align="center" prop="orderNo" width="180">
          <template #default="scope">
            <el-button link type="primary" @click="handleView(scope.row)">
              {{ scope.row.orderNo }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="客户名称" align="center" prop="customerName" width="180" />
        <el-table-column label="订单日期" align="center" prop="orderDate" width="120">
          <template #default="scope">
            <span>{{ parseDate(scope.row.orderDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="交货日期" align="center" prop="deliveryDate" width="120">
          <template #default="scope">
            <span>{{ parseDate(scope.row.deliveryDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="订单状态" prop="orderStatus" width="120">
          <template #default="scope">
            <el-tag :type="SalesOrderStatusEnum.getTagProps(scope.row.orderStatus).type">
              {{ SalesOrderStatusEnum.getLabel(scope.row.orderStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生产状态" prop="prodStatus" width="120">
          <template #default="scope">
            <el-tag :type="ProdStatusEnum.getTagProps(scope.row.prodStatus).type">
              {{ ProdStatusEnum.getLabel(scope.row.prodStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="支付状态" prop="paymentStatus" width="120">
          <template #default="scope">
            <el-tag :type="PaymentStatusEnum.getTagProps(scope.row.paymentStatus).type">
              {{ PaymentStatusEnum.getLabel(scope.row.paymentStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="币种" align="center" prop="currency" width="80" />
        <el-table-column label="总金额" align="center" prop="totalAmount" width="120">
          <template #default="scope">
            <span>{{ formatCurrency(scope.row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="销售员" align="center" prop="salesManagerName" width="100" />
        <el-table-column label="创建时间" align="center" prop="createTime" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="360" fixed="right">
          <template #default="{ row }">
            <div class="operation-buttons">
              <el-button link type="info" icon="Connection" @click="showTrace(row)">查看流水</el-button>
              <!-- 草稿状态 (1) -->
              <template v-if="row.orderStatus === 1">
                <el-button type="primary" size="small" v-hasPermi="['sales:order:submit']" @click="handleSubmitReview(row)">
                  提交审核
                </el-button>
              </template>

              <!-- 待审核状态 (2) -->
              <template v-else-if="row.orderStatus === 2">
                <el-button type="primary" size="small" v-hasPermi="['sales:order:review']" @click="handleStartReview(row)">
                  开始审核
                </el-button>
              </template>

              <!-- 审核中状态 (3) -->
              <template v-else-if="row.orderStatus === 3">
                <el-button type="primary" size="small" v-hasPermi="['sales:order:approve']" @click="handleApprove(row)">
                  审核通过
                </el-button>
                <el-button type="danger" size="small" v-hasPermi="['sales:order:approve']" @click="handleReject(row)">
                  审核驳回
                </el-button>
              </template>

              <!-- 已审核状态 (4) -->
              <template v-else-if="row.orderStatus === 4">
                <el-button type="primary" size="small" @click="handleGeneratePlan(row)">
                  生成生产计划
                </el-button>
                <!-- 打印确认书（2026-08-13：直接打开打印预览，不下载） -->
                <el-button type="info" size="small" plain v-hasPermi="['sales:order:export']" @click="handleExportConfirmPdf(row)">
                  打印确认书
                </el-button>
              </template>

              <!-- 已驳回状态 (5) -->
              <template v-else-if="row.orderStatus === 5">
                <el-button type="primary" size="small" v-hasPermi="['sales:order:submit']" @click="handleResubmit(row)">
                  重新提交
                </el-button>
              </template>

              <!-- 已确认状态 (6)：计划已生成，只保留齐套检查/打印确认书/确认凭证（2026-08-13） -->
              <template v-else-if="row.orderStatus === 6">
                <el-button type="info" size="small" plain v-hasPermi="['sales:order:edit']" @click="handleRecheckShortage(row)">
                  齐套检查
                </el-button>
                <el-tooltip
                  :content="`确认人：${row.confirmBy || '-'}｜方式：${row.confirmMethod || '-'}｜时间：${row.confirmTime || '-'}`"
                  placement="top"
                >
                  <el-button type="success" size="small" plain v-hasPermi="['sales:order:export']" @click="handleExportConfirmPdf(row)">
                    打印确认书
                  </el-button>
                </el-tooltip>
                <el-button type="info" size="small" plain v-hasPermi="['sales:order:view']" @click="openConfirmAttachment(row)">
                  确认凭证
                </el-button>
              </template>

              <!-- 生产中状态 (7) -->
              <template v-else-if="row.orderStatus === 7">
                <el-tag type="info" size="small">生产中</el-tag>
                <el-button type="warning" size="small" v-hasPermi="['sales:order:edit']" @click="handleShip(row)">
                  发货
                </el-button>
              </template>

              <!-- 已发货状态 (8) -->
              <template v-else-if="row.orderStatus === 8">
                <el-button type="success" size="small" v-hasPermi="['sales:order:edit']" @click="handleCompleteOrder(row)">
                  完成订单
                </el-button>
              </template>

              <!-- 已完成状态 (9) -->
              <template v-else-if="row.orderStatus === 9">
                <el-button type="info" size="small" disabled> 订单已完成 </el-button>
              </template>

              <!-- 已取消状态 (10) -->
              <template v-else-if="row.orderStatus === 10">
                <el-button type="info" size="small" disabled> 订单已取消 </el-button>
              </template>

              <!-- 取消订单按钮（已发货/已完成/已取消不显示） -->
              <el-button
                v-if="row.orderStatus !== 8 && row.orderStatus !== 9 && row.orderStatus !== 10"
                type="danger"
                size="small"
                v-hasPermi="['sales:order:edit']"
                @click="handleCancelOrder(row)"
              >
                取消订单
              </el-button>

              <!-- 修改按钮（草稿和已驳回状态可修改） -->
              <el-button
                v-if="row.orderStatus === 1 || row.orderStatus === 5"
                type="primary"
                size="small"
                plain
                v-hasPermi="['sales:order:edit']"
                @click="handleUpdate(row)"
              >
                修改
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>


    <!-- 审核对话框 -->
    <ReviewDialog
      v-model="reviewDialogVisible"
      :order-id="currentOrderId"
      :action="currentAction"
      @success="handleReviewSuccess"
    />

    <!-- 订单详情对话框 -->
    <OrderDetailDrawer v-model="detailOpen" :order-id="currentOrderId" />

    <!-- 发送客户确认弹窗（备注 + 凭证截图，DEV-343/314） -->
    <OrderSendConfirmDialog
      v-model:visible="sendConfirmVisible"
      :order="sendConfirmOrder"
      @success="getList"
    />

    <!-- 生成生产计划弹窗（2026-08-13：生成计划=确认动作，可上传确认书） -->
    <GeneratePlanDialog
      v-model:visible="generatePlanVisible"
      :order="generatePlanOrder"
      @success="getList"
    />

    <!-- 确认凭证附件弹窗 -->
    <AttachmentUploadDialog
      v-model="confirmAttachmentVisible"
      biz-type="sales_order_confirmation"
      :biz-id="confirmAttachmentOrderId"
      :dialog-title="`确认凭证 - ${confirmAttachmentOrderNo}`"
    />

    <!-- 验证对话框 -->
    <ValidationDialog
      v-model="validationDialogVisible"
      :order-id="currentOrderId"
      :order-no="currentOrderNo"
      @success="handleValidationSuccess"
      @cancel="handleValidationCancel"
    />
    <TraceTimeline v-model="traceDrawerVisible" :traceId="currentTraceId" :bizType="'order'" :bizId="currentBizId" />
  </div>

</template>

<script setup lang="ts">
defineOptions({
  name: 'SalesOrder',
})

import { ref, reactive, onMounted, h } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'

const router = useRouter()
import TraceTimeline from '@/components/TraceTimeline/index.vue'
import { orderApi } from '@/api/sales/order'
import { orderStatusApi } from '@/api/sales/orderStatus'
import { alertApi } from '@/api/inventory/alert'
import { parseTime, download, formatCurrency, parseDate } from '@/utils/format'
import ReviewDialog from './components/ReviewDialog.vue'
import OrderDetailDrawer from './components/OrderDetailDrawer.vue'
import OrderSendConfirmDialog from './components/OrderSendConfirmDialog.vue'
import GeneratePlanDialog from './components/GeneratePlanDialog.vue'
import AttachmentUploadDialog from '@/components/AttachmentUploadDialog/index.vue'
import ValidationDialog from './components/ValidationDialog.vue'
import type { SalesOrderQueryDTO } from '@/types/sales/order'
import { SalesOrderStatusEnum, PaymentStatusEnum, ProdStatusEnum } from '@/enums/sales/OrderEnum'

// 查询参数
const queryParams = reactive<SalesOrderQueryDTO>({
  pageNum: 1,
  pageSize: 10,
  // 销售订单列表只显示标准订单(1)，样品单走样品单页，避免混入（DEV-xxx 修复）
  orderType: 1,
  orderNo: undefined,
  customerName: undefined,
  orderStatus: undefined,
  orderDateStart: undefined,
  orderDateEnd: undefined,
  orderByColumn: undefined,
  isAsc: undefined,
})

// 响应式数据
const loading = ref(false)
const ids = ref<number[]>([])
const selectedRow = ref<any>(null)
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const detailOpen = ref(false)
const dateRange = ref<string[]>([])

// 审核相关
const reviewDialogVisible = ref(false)
const currentOrderId = ref(0)
const currentOrderNo = ref('')
const currentAction = ref<'approve' | 'reject' | null>(null)

// 验证相关
const validationDialogVisible = ref(false)

// 表格数据
const orderList = ref<any[]>([])

// 获取订单列表
const getList = async () => {
  loading.value = true
  try {
    if (dateRange.value && dateRange.value.length === 2) {
      queryParams.orderDateStart = dateRange.value[0]
      queryParams.orderDateEnd = dateRange.value[1]
    } else {
      queryParams.orderDateStart = undefined
      queryParams.orderDateEnd = undefined
    }

    const response = await orderApi.getOrders(queryParams)
    orderList.value = response.data?.records || []
    total.value = response.data?.total || 0
  } catch (error) {
    console.error('获取订单列表失败:', error)
    ElMessage.error('获取订单列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索按钮操作
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置按钮操作
const resetQuery = () => {
  dateRange.value = []
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    orderNo: undefined,
    customerName: undefined,
    orderStatus: undefined,
    startDate: undefined,
    endDate: undefined,
    orderByColumn: undefined,
    isAsc: undefined,
  })
  getList()
}

// 多选框选中数据
const handleSelectionChange = (selection: any[]) => {
  ids.value = selection.map((item) => item.orderId)
  selectedRow.value = selection.length === 1 ? selection[0] : null
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

// 排序触发
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

// 新增订单
const handleAdd = () => {
  router.push('/sales/order/add')
}

// 修改订单
const handleUpdate = (row?: any) => {
  const orderId = row?.orderId || ids.value[0]
  if (!orderId) {
    ElMessage.warning('请选择要修改的订单')
    return
  }

  // 检查订单状态是否可修改
  const order = orderList.value.find((o) => o.orderId === orderId)
  if (order && order.orderStatus !== 1 && order.orderStatus !== 5) {
    ElMessage.warning('只有草稿或已驳回状态的订单才能修改')
    return
  }

  router.push(`/sales/order/edit/${orderId}`)
}

// 删除订单
const handleDelete = (row?: any) => {
  const orderIds = row?.orderId || ids.value
  ElMessageBox.confirm('是否确认删除订单号为"' + orderIds + '"的数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => orderApi.deleteOrder(orderIds))
    .then(() => {
      getList()
      ElMessage.success('删除成功')
    })
    .catch(() => {})
}

// 导出订单
const handleExport = () => {
  ElMessageBox.confirm('是否确认导出所有订单数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      const loading = ElLoading.service({ text: '导出中...', lock: true })
      return orderApi
        .exportOrders(queryParams)
        .then((response: any) => {
          download(response, '订单列表.xlsx')
        })
        .finally(() => loading.close())
    })
    .catch(() => {})
}

// 导出PDF（单张订单表单，需选中一行）
const handleExportPdf = () => {
  const orderId = ids.value[0]
  if (!orderId) {
    ElMessage.warning('请先选中一行订单')
    return
  }
  orderApi.exportOrderPdf(orderId).then((response: any) => {
    download(response, `销售订单_${orderId}.pdf`)
  })
}

// 导出Excel（单张订单表单，需选中一行）
const handleExportExcel = () => {
  const orderId = ids.value[0]
  if (!orderId) {
    ElMessage.warning('请先选中一行订单')
    return
  }
  orderApi.exportOrderExcel(orderId).then((response: any) => {
    download(response, `销售订单_${orderId}.xlsx`)
  })
}

// 提交审核
const handleSubmitReview = async (row: any) => {
  try {
    // 先打开验证对话框
    currentOrderId.value = row.orderId
    currentOrderNo.value = row.orderNo
    validationDialogVisible.value = true
  } catch (error) {
    console.error('打开验证对话框失败', error)
  }
}

// 开始审核
const handleStartReview = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定要开始审核订单【${row.orderNo}】吗？`, '开始审核', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info',
    })
    await orderStatusApi.startReview(row.orderId)
    // ElMessage.success('开始审核成功')
    // 开始审核成功后，直接打开审核对话框
    currentOrderId.value = row.orderId
    currentAction.value = 'approve'
    reviewDialogVisible.value = true
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('开始审核失败', error)
    }
  }
}

// 审核通过
const handleApprove = (row: any) => {
  currentOrderId.value = row.orderId
  currentAction.value = 'approve'
  reviewDialogVisible.value = true
}

// 审核驳回（2026-08-12 DEV-013：3→5，复用审核弹窗）
const handleReject = (row: any) => {
  currentOrderId.value = row.orderId
  currentAction.value = 'reject'
  reviewDialogVisible.value = true
}

// 重新提交审核
const handleResubmit = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定要重新提交订单【${row.orderNo}】审核吗？`, '重新提交', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info',
    })
    await orderStatusApi.resubmit(row.orderId)
    ElMessage.success('重新提交成功')
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('重新提交失败', error)
    }
  }
}

// 发送客户确认（DEV-343/314：弹窗填写备注+凭证截图，替代原 confirm 提示框）
const sendConfirmVisible = ref(false)
const sendConfirmOrder = ref<any>(null)
const handleSendToCustomer = async (row: any) => {
  sendConfirmOrder.value = row
  sendConfirmVisible.value = true
}

// 客户确认（2026-08-12 DEV-011：4→6，发送确认后的确认动作）
const handleCustomerConfirm = async (row: any) => {
  try {
    const { value } = await ElMessageBox.prompt(
      `确认订单【${row.orderNo}】已获客户确认，请输入确认人：`,
      '客户确认',
      {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        inputPlaceholder: '客户方确认人',
        inputValidator: (v: string) => (v && v.trim() ? true : '请输入确认人'),
      },
    )
    await orderStatusApi.confirmOrder(row.orderId, value.trim())
    ElMessage.success('客户确认成功，订单已进入已确认状态')
    getList()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '客户确认失败')
  }
}

// 发货（2026-08-12 DEV-012：7→8，自动创建销售出库单并扣产品库存）
const handleShip = async (row: any) => {
  try {
    await ElMessageBox.confirm(
      `确认对订单【${row.orderNo}】发货？将自动创建销售出库单并扣减产品库存。`,
      '发货确认',
      { confirmButtonText: '确认发货', cancelButtonText: '取消', type: 'warning' },
    )
    await orderStatusApi.shipOrder(row.orderId)
    ElMessage.success('发货成功，订单已进入已发货状态')
    getList()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '发货失败')
  }
}

// 打印确认书（2026-08-13：打开新窗口预览 PDF 直接打印，不再下载文件；弹窗被拦截则降级下载）
const handleExportConfirmPdf = async (row: any) => {
  try {
    const res: any = await orderApi.exportConfirmationPdf(row.orderId)
    const blob = res instanceof Blob ? res : new Blob([res], { type: 'application/pdf' })
    const url = URL.createObjectURL(blob)
    const win = window.open(url, '_blank')
    if (!win) {
      download(blob, `确认书_${row.orderNo}.pdf`)
    } else {
      win.focus()
    }
  } catch {
    ElMessage.error('确认书生成失败')
  }
}

// 确认凭证附件（截图/文件查看与补充）
const confirmAttachmentVisible = ref(false)
const confirmAttachmentOrderId = ref<number>()
const confirmAttachmentOrderNo = ref('')
const openConfirmAttachment = (row: any) => {
  confirmAttachmentOrderId.value = row.orderId
  confirmAttachmentOrderNo.value = row.orderNo || ''
  confirmAttachmentVisible.value = true
}

// 订单齐套检查（手动重新检查，DEV-572 8-04）
const handleRecheckShortage = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定要对订单【${row.orderNo}】重新执行齐套检查（按BOM算料，缺口扣除在途采购量）吗？`, '齐套检查', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    const res: any = await alertApi.checkOrderShortage(row.orderId)
    const items: any[] = res?.data || []
    if (items.length === 0) {
      ElMessage.success('齐套检查完成：无缺料（含在途已覆盖），未生成缺料预警')
      return
    }
    // 弹窗展示缺料明细（含在途采购量，销售可见采购在途情况）
    const thStyle = 'padding:6px 8px;border:1px solid #ebeef5;background:#f5f7fa;text-align:left'
    const tdStyle = 'padding:6px 8px;border:1px solid #ebeef5'
    const fmt = (v: any) => (v === null || v === undefined ? '-' : String(v))
    ElMessageBox.alert(
      h('div', null, [
        h('p', { style: 'margin-bottom:10px;color:#f56c6c;font-weight:600' }, `发现 ${items.length} 种物料缺料（已扣除在途采购量），缺料预警已生成：`),
        h('table', { style: 'width:100%;border-collapse:collapse;font-size:13px' }, [
          h('tr', null, [
            h('th', { style: thStyle }, '物料'),
            h('th', { style: thStyle }, '需求'),
            h('th', { style: thStyle }, '可用'),
            h('th', { style: thStyle }, '在途'),
            h('th', { style: thStyle }, '实际缺口'),
          ]),
          ...items.map((it) =>
            h('tr', null, [
              h('td', { style: tdStyle }, `${it.materialCode} ${it.materialName}`),
              h('td', { style: tdStyle }, fmt(it.demand)),
              h('td', { style: tdStyle }, fmt(it.available)),
              h('td', { style: tdStyle }, Number(it.inTransit) > 0 ? fmt(it.inTransit) : '-'),
              h('td', { style: tdStyle, color: '#f56c6c', fontWeight: 600 }, fmt(it.actualGap)),
            ])
          ),
        ]),
      ]),
      '齐套检查缺料明细',
      { confirmButtonText: '知道了', type: 'warning' }
    )
  } catch (error) {
    if (error !== 'cancel') {
      console.error('齐套检查失败', error)
    }
  }
}

// 生成生产计划（2026-08-13：改为弹窗，可上传确认书，生成后订单进入已确认）
const generatePlanVisible = ref(false)
const generatePlanOrder = ref<any>()
const handleGeneratePlan = (row: any) => {
  generatePlanOrder.value = row
  generatePlanVisible.value = true
}

// 完成订单
const handleCompleteOrder = async (row: any) => {
  try {
    await ElMessageBox.confirm(`确定要完成订单【${row.orderNo}】吗？`, '完成订单', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'info',
    })
    await orderStatusApi.completeOrder(row.orderId)
    ElMessage.success('订单完成')
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('完成订单失败', error)
    }
  }
}

// 取消订单
const handleCancelOrder = async (row: any) => {
  try {
    const { value: reason } = await ElMessageBox.prompt('请输入取消原因', '取消订单', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputType: 'textarea',
      inputPlaceholder: '请输入取消原因',
      inputValidator: (value) => {
        if (!value) return '请填写取消原因'
        return true
      },
    })
    await orderStatusApi.cancelOrder(row.orderId, reason)
    ElMessage.success('取消订单成功')
    getList()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('取消订单失败', error)
    }
  }
}

const handleCopySelected = () => {
  if (!selectedRow.value) return
  handleCopyOrder(selectedRow.value)
}

// 复制订单（已取消等终态订单一键重新生成新草稿单）
const handleCopyOrder = async (row: any) => {
  try {
    await ElMessageBox.confirm(
      `确定复制订单【${row.orderNo}】生成一张新的草稿订单吗？`,
      '复制订单',
      { confirmButtonText: '确定', cancelButtonText: '取消', type: 'info' }
    )
    const res: any = await orderApi.copyOrder(row.orderId)
    if (res?.code === 200) {
      ElMessage.success(`复制成功，新订单已生成（草稿）`)
      getList()
    } else {
      ElMessage.error(res?.msg || '复制失败')
    }
  } catch (error) {
    if (error !== 'cancel') {
      console.error('复制订单失败', error)
    }
  }
}

// 查看详情
const handleView = (row: any) => {
  currentOrderId.value = row.orderId
  detailOpen.value = true
}

// 审核成功回调
const handleReviewSuccess = () => {
  getList()
}

// 验证成功回调
// 2026-08-18 修复：子组件 ValidationDialog 已调用 submitOrderReview 提交审核，
// 这里只刷新列表，不再重复提交（此前一次点击会触发两次接口调用，第二次必失败）
const handleValidationSuccess = async () => {
  getList()
}

// 验证取消回调
const handleValidationCancel = () => {
  // 用户取消了验证，不做任何操作
  console.log('用户取消了订单验证')
}

// 组件挂载时获取数据
onMounted(() => {
  getList()
})
// 链路追踪抽屉
const traceDrawerVisible = ref(false)
const currentTraceId = ref('')
const currentBizId = ref('')
function showTrace(row: any) {
  currentTraceId.value = row.traceId || ''
  currentBizId.value = row.orderId ? String(row.orderId) : ''
  traceDrawerVisible.value = true
}

</script>

<style scoped lang="scss">
.app-container {
  padding: 20px;
}

.search-card,
.operation-card,
.table-card {
  margin-bottom: 20px;
}

.operation-buttons {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.mb8 {
  margin-bottom: 8px;
}
</style>
