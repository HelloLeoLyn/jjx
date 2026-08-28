<template>
  <div class="outbound-list">
    <!-- 页面标题（2026-08-18：统一出库管理视图，含生产领料单，按类型筛选/标签区分） -->
    <div class="page-title" style="font-size:16px;font-weight:600;margin-bottom:12px">
      出库管理
    </div>
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="出库单号">
          <el-input
            v-model="queryParams.outboundNo"
            placeholder="请输入出库单号"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="出库类型">
          <el-select
            v-model="queryParams.outboundType"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="销售出库" value="sales" />
            <el-option label="生产领料" value="production" />
            <el-option label="退货出库" value="return" />
            <el-option label="调拨出库" value="transfer" />
            <el-option label="其他出库" value="other" />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库">
          <el-select
            v-model="queryParams.warehouseId"
            placeholder="请选择仓库"
            clearable
            style="width: 150px"
          >
            <el-option label="原材料仓库" value="1" />
            <el-option label="成品仓库" value="2" />
            <el-option label="半成品仓库" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="queryParams.status"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="待提交" :value="0" />
            <el-option label="待审批" :value="1" />
            <el-option label="已审批" :value="2" />
            <el-option label="已出库" :value="6" />
            <el-option label="已取消" :value="9" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏 -->
    <el-card class="operation-card">
      <el-row :gutter="10">
        <el-col :span="1.5">
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>新建出库单
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button @click="handleExport">
            <el-icon><Download /></el-icon>导出
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button :disabled="selectedRows.length !== 1" @click="handleExportPdf">
            <el-icon><Document /></el-icon>导出PDF
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button :disabled="selectedRows.length !== 1" @click="handlePrintLabel">
            <el-icon><Printer /></el-icon>打印标签
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button @click="handleRefresh">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 表格 -->
    <el-card class="table-card">
      <el-table v-loading="loading" :data="outboundList" border style="width: 100%" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="50" align="center" />
        <el-table-column label="出库单号" prop="outboundNo" min-width="150" />
        <el-table-column label="出库类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getOutboundTypeTag(row.outboundType)" size="small">
              {{ row.outboundTypeName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="仓库" prop="warehouseName" width="120" />
        <!-- 2026-08-18：统一视图——生产领料显示来源工单，其他显示客户 -->
        <el-table-column label="来源" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            {{ row.outboundType === 'production' ? (row.sourceNo || '-') : (row.customerName || '-') }}
          </template>
        </el-table-column>
        <el-table-column label="总数量" prop="totalQuantity" width="100" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.totalQuantity) }}
          </template>
        </el-table-column>
        <el-table-column label="总金额" prop="totalAmount" width="120" align="right">
          <template #default="{ row }"> ¥ {{ formatCurrency(row.totalAmount) }} </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)" size="small">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建人" prop="createBy" width="100" />
        <el-table-column label="创建时间" prop="createTime" width="150" align="center" />
        <el-table-column label="操作" min-width="250" fixed="right">
          <template #default="{ row }">
            <el-button link type="info" @click="showTrace(row)">流水</el-button>
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <el-button link type="info" @click="handlePrint(row)">打印</el-button>
            <el-button v-if="row.status === 0" link type="primary" v-hasPermi="['inventory:outbound:edit']" @click="handleEdit(row)"
              >编辑</el-button
            >
            <el-button
              v-if="row.status === 1 || row.status === 2"
              link
              type="warning"
              v-hasPermi="['inventory:outbound:approve']"
              @click="handleConfirm(row)"
              >{{ row.outboundType === 'production' ? '确认发料' : '确认出库' }}</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.current"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <!-- 查看流水（DEV-569） -->
    <TraceTimeline v-model="traceDrawerVisible" :traceId="currentTraceId" />

    <!-- 出库单详情抽屉（DEV-661：含基本信息/明细/库存流水） -->
    <el-drawer v-model="detailVisible" :title="`出库单详情 - ${detailNo}`" size="720px">
      <template v-if="currentDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="单据号">{{ currentDetail.outboundNo }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ currentDetail.outboundTypeName }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{ currentDetail.warehouseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ currentDetail.statusName }}</el-descriptions-item>
          <el-descriptions-item label="来源单号">{{ currentDetail.sourceNo || '-' }}</el-descriptions-item>
          <el-descriptions-item label="总数量">{{ formatNumber(currentDetail.totalQuantity) }}</el-descriptions-item>
          <el-descriptions-item label="总金额">¥ {{ formatCurrency(currentDetail.totalAmount) }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ currentDetail.createBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ currentDetail.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ currentDetail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">出库明细</el-divider>
        <el-table :data="currentDetail.items || []" border size="small" style="width: 100%">
          <el-table-column label="物料编码" prop="materialCode" width="110" />
          <el-table-column label="物料名称" prop="materialName" min-width="140" show-overflow-tooltip />
          <el-table-column label="规格" prop="specification" min-width="100" show-overflow-tooltip />
          <el-table-column label="数量" prop="quantity" width="80" align="right" />
          <el-table-column label="单位" prop="unit" width="60" align="center" />
          <el-table-column label="批次" prop="batchNo" width="110" />
          <el-table-column label="库位" prop="locationName" width="110">
            <template #default="{ row }">
              <span v-if="row.locationName">{{ row.locationName }}</span>
              <span v-else style="color: #999">-</span>
            </template>
          </el-table-column>
        </el-table>

        <el-divider content-position="left">库存流水（DEV-661）</el-divider>
        <el-table v-loading="txLoading" :data="detailTransactions" border size="small" style="width: 100%">
          <el-table-column label="物料" prop="materialCode" width="110" />
          <el-table-column label="名称" prop="materialName" min-width="120" show-overflow-tooltip />
          <el-table-column label="变动" prop="quantity" width="90" align="right">
            <template #default="{ row }">
              <span :style="{ color: row.quantity < 0 ? '#f56c6c' : '#67c23a' }">
                {{ row.quantity > 0 ? '+' : '' }}{{ formatNumber(row.quantity) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="变动前" prop="beforeQuantity" width="80" align="right" />
          <el-table-column label="变动后" prop="afterQuantity" width="80" align="right" />
          <el-table-column label="批次" prop="batchNo" width="110" />
          <el-table-column label="操作人" prop="operatorName" width="90" />
          <el-table-column label="时间" prop="transactionTime" width="150" />
          <el-table-column label="备注" prop="remark" min-width="100" show-overflow-tooltip />
        </el-table>
        <el-empty v-if="!txLoading && detailTransactions.length === 0" description="暂无库存流水" :image-size="60" />
      </template>
    </el-drawer>

    <!-- 操作预览器 -->
    <OperationPreviewDialog
      v-model="previewVisible"
      :operation="previewOperation"
      :biz-id="previewBizId"
      :biz-no="previewBizNo"
      :status-text-map="outboundStatusTextMap"
      @success="getList"
    />

    <!-- 打印对话框（DEV-662：PDF 预览 + 打印 + 下载） -->
    <PrintDialog v-model="printDialogVisible" :pdf-blob="printPdfBlob" :file-name="printFileName" />

  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'OutboundList',
})

import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Download, Refresh, Printer } from '@element-plus/icons-vue'
import { outboundApi } from '@/api/inventory/outbound'
import { getTransactionsByDocNo } from '@/api/inventory/transaction'
import type { TransactionVO } from '@/api/inventory/transaction'
import OperationPreviewDialog from '@/components/OperationPreviewDialog/index.vue'
import PrintDialog from '@/components/PrintDialog/index.vue'
import { getOperation } from '@/components/OperationPreviewDialog/registry'
import { formatCurrency, formatNumber, download } from '@/utils/format'
import TraceTimeline from '@/components/TraceTimeline/index.vue'
import type { OutboundQueryParams, OutboundVO } from '@/types/inventory/outbound'
import { openLabelPrint } from '@/utils/labelPrint'

// 查看流水（DEV-569）
const traceDrawerVisible = ref(false)
const currentTraceId = ref('')
function showTrace(row: OutboundVO) {
  currentTraceId.value = (row as any).traceId || ''
  traceDrawerVisible.value = true
}

const router = useRouter()

// 2026-08-18：统一出库管理视图（含生产领料单，按出库类型筛选/标签区分）
// 查询参数
const queryParams = reactive<OutboundQueryParams>({
  current: 1,
  pageSize: 10,
  outboundNo: '',
  outboundType: '',
  warehouseId: '',
  status: '',
})

// 响应式数据
const loading = ref(false)
const outboundList = ref<OutboundVO[]>([])
const total = ref(0)

// 获取出库单列表
const getList = async () => {
  loading.value = true
  try {
    const res = await outboundApi.list(queryParams)
    outboundList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('获取出库单列表失败:', error)
    ElMessage.error('获取出库单列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleQuery = () => {
  queryParams.current = 1
  getList()
}

// 重置
const handleReset = () => {
  queryParams.current = 1
  queryParams.outboundNo = ''
  queryParams.outboundType = ''
  queryParams.warehouseId = ''
  queryParams.status = ''
  getList()
}

// 新建出库单
const handleCreate = () => {
  router.push('/inventory/outbound/create')
}

// 导出
const handleExport = () => {
  ElMessage.info('导出功能开发中')
}

// 行选中
const selectedRows = ref<any[]>([])
const handleSelectionChange = (selection: any[]) => {
  selectedRows.value = selection
}

// 导出PDF（单张表单，需选中一行）——DEV-662：改为预览+打印+下载（PrintDialog）
const printDialogVisible = ref(false)
const printPdfBlob = ref<Blob | null>(null)
const printFileName = ref('')

const handleExportPdf = () => {
  const row = selectedRows.value[0]
  if (!row?.outboundId) {
    ElMessage.warning('请先选中一行出库单')
    return
  }
  outboundApi.exportPdf(row.outboundId).then((response: any) => {
    printPdfBlob.value = response as Blob
    printFileName.value = `出库单_${row.outboundNo || row.outboundId}.pdf`
    printDialogVisible.value = true
  })
}

const handlePrintLabel = () => {
  const row = selectedRows.value[0] as OutboundVO | undefined
  if (!row?.outboundId) {
    ElMessage.warning('请先选中一行出库单')
    return
  }
  openLabelPrint(router, { type: 'box', outboundId: String(row.outboundId) })
}

// 刷新
const handleRefresh = () => {
  getList()
  ElMessage.success('数据已刷新')
}

// 查看详情（DEV-661：抽屉展示基本信息/明细/库存流水）
const detailVisible = ref(false)
const detailNo = ref('')
const currentDetail = ref<OutboundVO | null>(null)
const detailTransactions = ref<TransactionVO[]>([])
const txLoading = ref(false)

// 打印出库单（跳转独立打印页）
function handlePrint(row: OutboundVO) {
  window.open(`/print/outbound/${row.outboundId}`, '_blank')
}

const handleView = async (row: OutboundVO) => {
  detailNo.value = row.outboundNo || ''
  detailVisible.value = true
  currentDetail.value = null
  detailTransactions.value = []
  try {
    const res = await outboundApi.getById(String(row.outboundId))
    currentDetail.value = res.data || null
  } catch (e) {
    ElMessage.error('获取出库单详情失败')
  }
  if (row.outboundNo) {
    txLoading.value = true
    try {
      const txRes = await getTransactionsByDocNo(row.outboundNo)
      detailTransactions.value = txRes.data || []
    } catch (e) {
      ElMessage.error('获取库存流水失败')
    } finally {
      txLoading.value = false
    }
  }
}

// 编辑出库单
const handleEdit = (row: OutboundVO) => {
  router.push(`/inventory/outbound/edit/${row.outboundId}`)
}

// 确认出库
// 确认出库（操作预览器，Phase 2）
const previewVisible = ref(false)
const previewOperation = ref<any>(null)
const previewBizId = ref<number | null>(null)
const previewBizNo = ref('')
const outboundStatusTextMap: Record<number, string> = { 0: '草稿', 1: '待审批', 2: '已审批', 3: '已出库' }
function openPreview(opKey: string, row: OutboundVO) {
  if (!row?.outboundId) return
  const op = getOperation(opKey)
  if (!op) return
  previewOperation.value = op
  previewBizId.value = Number(row.outboundId)
  previewBizNo.value = row.outboundNo || ''
  previewVisible.value = true
}

const handleConfirm = (row: OutboundVO) => openPreview('outbound.confirm', row)
// 获取出库类型标签样式
const getOutboundTypeTag = (
  type: string
): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const typeMap: Record<string, 'success' | 'warning' | 'info' | 'danger' | undefined> = {
    sales: 'success',
    production: 'warning',
    return: 'info',
    transfer: 'danger',
    other: undefined,
  }
  return typeMap[type]
}

// 获取状态标签样式
const getStatusTag = (status: number): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const statusMap: Record<number, 'success' | 'warning' | 'info' | 'danger' | undefined> = {
    0: 'info',    // draft
    1: 'warning', // pending
    2: 'success', // approved
    4: 'warning', // processing
    6: 'success', // out_confirm
    9: 'danger',  // cancelled
    10: 'success', // completed
  }
  return statusMap[status]
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.outbound-list {
  padding: 20px;
}

.search-card,
.operation-card,
.table-card {
  margin-bottom: 16px;
}
</style>
