<template>
  <div class="inbound-list">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="入库单号">
          <el-input
            v-model="queryParams.inboundNo"
            placeholder="请输入入库单号"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="入库类型">
          <el-select
            v-model="queryParams.inboundType"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="采购入库" value="purchase" />
            <el-option label="生产入库" value="production" />
            <el-option label="退货入库" value="return" />
            <el-option label="调拨入库" value="transfer" />
            <el-option label="其他入库" value="other" />
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
            v-model="queryParams.orderStatus"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="草稿" :value="0" />
            <el-option label="待审批" :value="1" />
            <el-option label="已批准" :value="2" />
            <el-option label="已驳回" :value="3" />
            <el-option label="已入库" :value="7" />
            <el-option label="已取消" :value="9" />
            <el-option label="已完成" :value="10" />
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
            <el-icon><Plus /></el-icon>新建入库单
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button :disabled="single" v-hasPermi="['inventory:inbound:edit']" @click="() => handleEdit()">
            <el-icon><Edit /></el-icon>编辑
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button :disabled="multiple" type="danger" v-hasPermi="['inventory:inbound:edit']" @click="() => handleDelete()">
            <el-icon><Delete /></el-icon>删除
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button @click="handleExport">
            <el-icon><Download /></el-icon>导出
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button :disabled="single" @click="handleExportPdf">
            <el-icon><Document /></el-icon>导出PDF
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
      <el-table
        v-loading="loading"
        :data="inboundList"
        @selection-change="handleSelectionChange"
        border
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="入库单号" prop="inboundNo" min-width="150" />
        <el-table-column label="入库类型" width="100" align="center">
          <template #default="{ row }">
            {{ row.inboundTypeName || inboundTypeText(row.inboundType) }}
          </template>
        </el-table-column>
        <el-table-column label="仓库" prop="warehouseName" width="120" />
        <el-table-column label="供应商" prop="supplierName" width="150" show-overflow-tooltip />
        <el-table-column label="总数量" prop="totalQuantity" width="100" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.totalQuantity) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)" size="small">{{
              row.statusName || inboundStatusText(row.status)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建人" prop="createBy" width="100" />
        <el-table-column label="创建时间" prop="createTime" width="180" align="center" />
        <el-table-column label="操作" min-width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="info" @click="showTrace(row)">流水</el-button>
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <el-button link type="info" @click="handlePrint(row)">打印</el-button>
            <el-button v-if="row.supplierId" link type="primary" @click="handleIqcPrint(row)">打印进料检验报告</el-button>
            <el-button link type="primary" v-hasPermi="['inventory:inbound:edit']" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 0" link type="primary" v-hasPermi="['inventory:inbound:edit']" @click="handleSubmit(row)"
              >提交</el-button
            >
            <el-button
              v-if="row.status === 1"
              link
              type="success"
              v-hasPermi="['inventory:inbound:approve']"
              @click="handleApprove(row)"
              >审批</el-button
            >
            <el-button
              v-if="row.status === 0 || row.status === 1"
              link
              type="danger"
              v-hasPermi="['inventory:inbound:edit']"
              @click="handleCancel(row)"
              >取消</el-button
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

    <!-- 查看流水（DEV-569） -->
    <TraceTimeline v-model="traceDrawerVisible" :traceId="currentTraceId" />

    <!-- 入库单详情对话框（公共组件） -->
    <el-dialog :title="dialogTitle" v-model="detailDialogVisible" width="1000px" append-to-body destroy-on-close>
      <InboundDetail v-if="detailDialogVisible && detailInboundId" :inbound-id="detailInboundId" />
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 审核弹窗（公共详情 + 审核操作） -->
    <InboundApproveDialog
      v-model:visible="approveDialogVisible"
      :inbound-id="approveInboundId"
      :inbound-no="approveInboundNo"
      @success="getList"
    />
    <!-- 操作预览器 -->
    <OperationPreviewDialog
      v-model="previewVisible"
      :operation="previewOperation"
      :biz-id="previewBizId"
      :biz-no="previewBizNo"
      :status-text-map="inboundStatusTextMap"
      @success="getList"
    />

  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'InboundList',
})

import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Download, Refresh } from '@element-plus/icons-vue'
import { inboundApi } from '@/api/inventory/inbound'
import OperationPreviewDialog from '@/components/OperationPreviewDialog/index.vue'
import { getOperation } from '@/components/OperationPreviewDialog/registry'
import { formatNumber, download } from '@/utils/format'
import TraceTimeline from '@/components/TraceTimeline/index.vue'
import InboundDetail from './components/InboundDetail.vue'
import InboundApproveDialog from './components/InboundApproveDialog.vue'
import type { InboundQueryParams, InboundVO } from '@/types/inventory/inbound'

const router = useRouter()

// 查询参数
const queryParams = reactive<InboundQueryParams>({
  current: 1,
  pageSize: 10,
  inboundNo: '',
  inboundType: '',
  warehouseId: '',
  orderStatus: '',
})

// 响应式数据
const loading = ref(false)
const inboundList = ref<InboundVO[]>([])
const total = ref(0)
const ids = ref<string[]>([])
const single = ref(true)
const multiple = ref(true)
const detailDialogVisible = ref(false)
const detailInboundId = ref<number | null>(null)
const dialogTitle = ref('')

// 审核弹窗状态
const approveDialogVisible = ref(false)
const approveInboundId = ref<number | undefined>(undefined)
const approveInboundNo = ref('')

// 获取入库单列表
const getList = async () => {
  loading.value = true
  try {
    const res = await inboundApi.list(queryParams)
    inboundList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('获取入库单列表失败:', error)
    ElMessage.error('获取入库单列表失败')
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
  queryParams.inboundNo = ''
  queryParams.inboundType = ''
  queryParams.warehouseId = ''
  queryParams.orderStatus = ''
  getList()
}

// 多选框选中
const handleSelectionChange = (selection: InboundVO[]) => {
  ids.value = selection.map((item) => item.inboundId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

// 新建入库单
const handleCreate = () => {
  router.push('/inventory/io/inbound/create')
}

// 编辑入库单
const handleEdit = (row?: InboundVO) => {
  const inboundId = row ? row.inboundId : ids.value[0]
  if (inboundId) {
    router.push(`/inventory/io/inbound/edit/${inboundId}`)
  }
}

// 删除入库单
const handleDelete = (row?: InboundVO) => {
  const inboundIds = row ? [row.inboundId] : ids.value
  if (inboundIds.length === 0) {
    ElMessage.warning('请选择要删除的入库单')
    return
  }

  ElMessageBox.confirm('确认删除选中的入库单吗？', '提示', { type: 'warning' })
    .then(() => {
      ElMessage.success('删除功能开发中')
      // TODO: 调用删除API
      // await inboundApi.delete(inboundIds)
      getList()
    })
    .catch(() => {})
}

// 导出
const handleExport = () => {
  ElMessage.info('导出功能开发中')
}

// 导出PDF（单张表单，需选中一行）
const handleExportPdf = () => {
  const id = ids.value[0]
  if (!id) {
    ElMessage.warning('请先选中一行入库单')
    return
  }
  inboundApi.exportPdf(Number(id)).then((response: any) => {
    download(response, `入库单_${id}.pdf`)
  })
}

// 刷新
const handleRefresh = () => {
  getList()
  ElMessage.success('数据已刷新')
}

// 打印入库单（跳转独立打印页）
function handlePrint(row: InboundVO) {
  window.open(`/print/inbound/${row.inboundId}`, '_blank')
}
function handleIqcPrint(row: InboundVO) {
  window.open(router.resolve({ path: '/production/quality-print/iqc-report', query: { inboundId: row.inboundId } }).href, '_blank')
}
// 查看详情
// 查看详情（公共组件弹窗）
const handleView = (row: InboundVO) => {
  dialogTitle.value = '入库单详情'
  detailInboundId.value = Number(row.inboundId)
  detailDialogVisible.value = true
}

// 提交审批
// ===== 操作预览器（Phase 2：库存模块）=====
const previewVisible = ref(false)
const previewOperation = ref<any>(null)
const previewBizId = ref<number | null>(null)
const previewBizNo = ref('')
const inboundStatusTextMap: Record<number, string> = {
  0: '草稿',
  1: '待审批',
  2: '已批准',
  3: '已驳回',
  4: '处理中',
  5: '已确认',
  6: '已出库',
  7: '已入库',
  8: '已关闭',
  9: '已取消',
  10: '已完成',
  11: '已处理',
  12: '调拨中',
}
const inboundStatusText = (status?: number) =>
  status === undefined || status === null ? '-' : inboundStatusTextMap[status] || String(status)
const inboundTypeText = (type?: string) => {
  const map: Record<string, string> = {
    purchase: '采购入库',
    production: '生产入库',
    return: '退货入库',
    transfer: '调拨入库',
    other: '其他入库',
    PURCHASE: '采购入库',
    PRODUCTION_FINISH: '生产入库',
    RETURN: '退货入库',
    TRANSFER: '调拨入库',
    OTHER: '其他入库',
  }
  return (type && map[type]) || type || '-'
}
function openPreview(opKey: string, row: InboundVO) {
  if (!row?.inboundId) return
  const op = getOperation(opKey)
  if (!op) return
  previewOperation.value = op
  previewBizId.value = Number(row.inboundId)
  previewBizNo.value = row.inboundNo || ''
  previewVisible.value = true
}

const handleSubmit = async (row: InboundVO) => openPreview('inbound.submit', row)

// 审批（打开审核弹窗：公共详情 + 通过/驳回）
const handleApprove = async (row: InboundVO) => {
  approveInboundId.value = Number(row.inboundId)
  approveInboundNo.value = row.inboundNo || ''
  approveDialogVisible.value = true
}

// 取消入库单
const handleCancel = async (row: InboundVO) => openPreview('inbound.cancel', row)
import { InboundEnum } from '@/enums/inventory'

// 查看流水（DEV-569）
const traceDrawerVisible = ref(false)
const currentTraceId = ref('')
function showTrace(row: InboundVO) {
  currentTraceId.value = (row as any).traceId || ''
  traceDrawerVisible.value = true
}

// 获取状态标签样式
const getStatusTag = (status?: number): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const statusMap: Record<number, 'success' | 'warning' | 'info' | 'danger' | undefined> = {
    0: 'info',    // draft
    1: 'warning', // pending
    2: 'success', // approved
    3: 'danger',  // rejected
    4: 'warning', // processing
    5: 'success', // confirmed
    6: 'success', // out_confirm
    7: 'success', // in_confirm
    8: 'info',    // closed
    9: 'danger',  // cancelled
    10: 'success', // completed
    11: 'success', // processed
    12: 'warning', // in_progress
  }
  return status === undefined || status === null ? undefined : statusMap[status]
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.inbound-list {
  padding: 20px;
}

.search-card,
.operation-card,
.table-card {
  margin-bottom: 16px;
}
</style>
