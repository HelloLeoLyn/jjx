<template>
  <div class="product-route-container">
    <!-- 搜索区域 -->
    <RouteSearch @search="handleSearch" @reset="handleReset" />

    <!-- 操作按钮区域 -->
    <el-card class="operation-card" shadow="never">
      <div class="operation-bar">
        <el-button v-hasPermi="['engineering:routing:add']" type="primary" icon="Plus" @click="handleAdd">新增工艺路线</el-button>
      </div>
    </el-card>

    <!-- 表格区域 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe style="width: 100%">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column prop="routingCode" label="路线编码" width="140">
          <template #default="scope">
            <el-button link type="primary" @click="handleDetail(scope.row)">{{
              scope.row.routingCode
            }}</el-button>
          </template></el-table-column
        >
        <el-table-column
          prop="routingName"
          label="路线名称"
          min-width="160"
          show-overflow-tooltip
        />
        <el-table-column prop="productCode" label="产品编码" width="120"> </el-table-column>
        <el-table-column prop="productName" label="产品名称" width="150" show-overflow-tooltip />
        <el-table-column prop="routingVersion" label="版本" width="80" align="center" />
        <el-table-column prop="processCount" label="工序数" width="70" align="center" />
        <el-table-column prop="totalLaborHours" label="总人工工时" width="100" align="right" />
        <el-table-column prop="totalMachineHours" label="总机器工时" width="100" align="right" />
        <el-table-column prop="isCurrent" label="当前版本" width="80" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.isCurrent === 1 ? 'success' : 'info'" size="small">
              {{ scope.row.isCurrentName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="approveStatus" label="审核状态" width="90" align="center">
          <template #default="scope">
            <el-tag :type="RouteStatusEnum.getTagProps(scope.row.approveStatus).type" size="small">
              {{ RouteStatusEnum.getLabel(scope.row.approveStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createBy" label="创建人" width="100" />
        <el-table-column prop="createTime" label="创建时间" width="170" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="scope">
            <el-button
              v-hasPermi="['engineering:routing:edit']"
              link
              type="primary"
              size="small"
              :disabled="!RouteStatusEnum.canDo(scope.row.approveStatus, ProductActions.EDIT)"
              @click="handleEdit(scope.row)"
            >
              编辑
            </el-button>
            <el-button
              link
              type="primary"
              size="small"
              :disabled="!RouteStatusEnum.canDo(scope.row.approveStatus, ProductActions.EDIT)"
              @click="handleCopy(scope.row)"
            >
              复制版本
            </el-button>
            <el-button
              link
              type="warning"
              size="small"
              @click="handleVersionCompare(scope.row)"
            >
              版本对比
            </el-button>
            <el-button
              link
              type="primary"
              size="small"
              :disabled="!RouteStatusEnum.canDo(scope.row.approveStatus, ProductActions.SUBMIT)"
              @click="handleSubmitApprove(scope.row)"
            >
              提交审批
            </el-button>
            <el-button
              link
              type="primary"
              size="small"
              :disabled="!RouteStatusEnum.canDo(scope.row.approveStatus, ProductActions.APPROVE)"
              @click="handleApprove(scope.row)"
            >
              审批
            </el-button>
            <el-button
              link
              type="danger"
              size="small"
              :disabled="!RouteStatusEnum.canDo(scope.row.approveStatus, ProductActions.DELETE)"
              @click="handleDelete(scope.row)"
            >
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 详情对话框 -->
    <RouteDetailDialog v-model="detailDialogVisible" :routing-id="currentRoutingId" />

    <!-- 版本对比对话框（DEV-768） -->
    <RouteVersionCompareDialog
      v-model="versionCompareVisible"
      :product-id="compareProductId"
      :product-name="compareProductName"
    />

    <!-- 复制版本对话框 -->
    <RouteCopyDialog
      v-model="copyDialogVisible"
      :current-version="currentVersion"
      @confirm="handleCopyConfirm"
    />

    <!-- 审批对话框 -->
    <RouteApproveDialog
      v-model="approveDialogVisible"
      :routing-id="currentRoutingId"
      @approve="handleApprovePass"
      @reject="handleApproveReject"
    />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'ProductRoute',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import { productRouteApi } from '@/api/product/routing'

import type { StandardProcessOption } from '@/types/product'
import type { ProductRouteQueryParams, EngineeringRoutingVO } from '@/types/product/routing'

import RouteSearch from './components/RouteSearch.vue'
import RouteDetailDialog from './components/RouteDetailDialog.vue'
import RouteCopyDialog from './components/RouteCopyDialog.vue'
import RouteApproveDialog from './components/RouteApproveDialog.vue'
import RouteVersionCompareDialog from './components/RouteVersionCompareDialog.vue'
import { useRouter } from 'vue-router'
import { RouteStatusEnum, ProductActions } from '@/enums/product'
const router = useRouter()
// ==================== 查询参数 ====================
const queryParams = reactive<ProductRouteQueryParams>({
  pageNum: 1,
  pageSize: 10,
  routingCode: undefined,
  routingName: undefined,
  productId: undefined,
  productCode: undefined,
  approveStatus: undefined,
  isCurrent: undefined,
  orderByColumn: 'createTime',
  isAsc: 'desc',
})

// ==================== 表格数据 ====================
const tableData = ref<EngineeringRoutingVO[]>([])
const total = ref(0)
const loading = ref(false)

// ==================== 产品选项 ====================
interface ProductOption {
  productId: number
  productCode: string
  productName: string
}

// ==================== 标准工序选项 ====================
const standardProcesses = ref<StandardProcessOption[]>([])

// ==================== 对话框状态 ====================
const detailDialogVisible = ref(false)
const copyDialogVisible = ref(false)
const approveDialogVisible = ref(false)

const currentRoutingId = ref<number | undefined>(undefined)
const currentVersion = ref('')

// ==================== 数据加载 ====================
const loadData = async () => {
  loading.value = true
  try {
    const response = await productRouteApi.listProductRoute(queryParams)
    const result = response.data
    if (result) {
      tableData.value = result.records || []
      total.value = result.total || 0
    }
  } catch (error) {
    console.error('加载工艺路线列表失败:', error)
    ElMessage.error('加载工艺路线列表失败')
  } finally {
    loading.value = false
  }
}

const loadStandardProcesses = async () => {
  try {
    const response = await productRouteApi.getEnabledProcesses()
    standardProcesses.value = response.data || []
  } catch (error) {
    console.error('加载标准工序失败:', error)
  }
}

// ==================== 搜索 ====================
const handleSearch = (params: ProductRouteQueryParams) => {
  Object.assign(queryParams, params)
  loadData()
}

const handleReset = () => {
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    routingCode: undefined,
    routingName: undefined,
    productId: undefined,
    productCode: undefined,
    approveStatus: undefined,
    isCurrent: undefined,
    orderByColumn: 'createTime',
    isAsc: 'desc',
  })
  loadData()
}

// ==================== 分页 ====================
const handleSizeChange = (val: number) => {
  queryParams.pageSize = val
  loadData()
}

const handleCurrentChange = (val: number) => {
  queryParams.pageNum = val
  loadData()
}

// ==================== 新增 ====================
const handleAdd = () => {
  router.push(`/product/route/add`)
}

// ==================== 编辑 ====================
const handleEdit = (row: EngineeringRoutingVO) => {
  router.push(`/product/route/edit/${row.routingId}`)
}

// ==================== 删除 ====================
const handleDelete = (row: EngineeringRoutingVO) => {
  ElMessageBox.confirm(
    `确定要删除工艺路线 "${row.routingName}" (${row.routingVersion}) 吗？`,
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
    .then(async () => {
      try {
        await productRouteApi.removeProductRoute(row.routingId)
        ElMessage.success('删除成功')
        loadData()
      } catch (error) {
        console.error('删除工艺路线失败:', error)
      }
    })
    .catch(() => {
      // 取消删除
    })
}

// ==================== 详情 ====================
const handleDetail = (row: EngineeringRoutingVO) => {
  currentRoutingId.value = row.routingId
  detailDialogVisible.value = true
}

// 版本对比（DEV-768）
const versionCompareVisible = ref(false)
const compareProductId = ref<number | null>(null)
const compareProductName = ref('')
function handleVersionCompare(row: EngineeringRoutingVO) {
  compareProductId.value = row.productId ?? null
  compareProductName.value = row.productName || ''
  versionCompareVisible.value = true
}

// ==================== 复制版本 ====================
const handleCopy = (row: EngineeringRoutingVO) => {
  currentRoutingId.value = row.routingId
  currentVersion.value = row.routingVersion
  copyDialogVisible.value = true
}

const handleCopyConfirm = async (newVersion: string) => {
  try {
    await productRouteApi.copyProductRoute(currentRoutingId.value!, newVersion)
    ElMessage.success('复制成功')
    copyDialogVisible.value = false
    loadData()
  } catch (error) {
    console.error('复制工艺路线失败:', error)
  }
}

// ==================== 提交审批 ====================
const handleSubmitApprove = async (row: EngineeringRoutingVO) => {
  try {
    await productRouteApi.submitProductRoute(row.routingId)
    ElMessage.success('已提交审批')
    loadData()
  } catch (error) {
    console.error('提交审批失败:', error)
  }
}

// ==================== 审批 ====================
const handleApprove = (row: EngineeringRoutingVO) => {
  currentRoutingId.value = row.routingId
  approveDialogVisible.value = true
}

const handleApprovePass = async (remark?: string) => {
  try {
    await productRouteApi.approveProductRoute(currentRoutingId.value!, remark)
    ElMessage.success('审批通过')
    approveDialogVisible.value = false
    loadData()
  } catch (error) {
    console.error('审批失败:', error)
  }
}

const handleApproveReject = async (remark: string) => {
  try {
    await productRouteApi.rejectProductRoute(currentRoutingId.value!, remark)
    ElMessage.success('已驳回')
    approveDialogVisible.value = false
    loadData()
  } catch (error) {
    console.error('驳回失败:', error)
  }
}

// ==================== 初始化 ====================
onMounted(() => {
  loadData()
  loadStandardProcesses()
})
</script>

<style scoped>
.product-route-container {
  padding: 20px;
}

.operation-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}

.operation-bar {
  display: flex;
  justify-content: flex-start;
  align-items: center;
}

.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
