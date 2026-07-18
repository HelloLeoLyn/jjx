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
            v-model="queryParams.status"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="待提交" value="draft" />
            <el-option label="待审批" value="pending" />
            <el-option label="已审批" value="approved" />
            <el-option label="已入库" value="completed" />
            <el-option label="已取消" value="cancelled" />
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
          <el-button :disabled="single" @click="() => handleEdit()">
            <el-icon><Edit /></el-icon>编辑
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button :disabled="multiple" type="danger" @click="() => handleDelete()">
            <el-icon><Delete /></el-icon>删除
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button @click="handleExport">
            <el-icon><Download /></el-icon>导出
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
        <el-table-column label="入库单号" prop="inboundNo" width="150" />
        <el-table-column label="入库类型" prop="inboundTypeName" width="100" align="center">
          <template #default="{ row }"> </template>
        </el-table-column>
        <el-table-column label="仓库" prop="warehouseName" width="120" />
        <el-table-column label="供应商" prop="supplierName" width="150" show-overflow-tooltip />
        <el-table-column label="总数量" prop="totalQuantity" width="100" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.totalQuantity) }}
          </template>
        </el-table-column>
        <el-table-column label="总金额" prop="totalAmount" width="120" align="right">
          <template #default="{ row }"> ¥ {{ formatCurrency(row.totalAmount) }} </template>
        </el-table-column>
        <el-table-column label="状态" prop="statusName" width="100" align="center">
          <template #default="{ row }"> </template>
        </el-table-column>
        <el-table-column label="审核状态" prop="approveStatus" width="100" align="center">
          <template #default="{ row }"> </template>
        </el-table-column>
        <el-table-column label="创建人" prop="createBy" width="100" />
        <el-table-column label="创建时间" prop="createTime" width="150" align="center" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <el-button link type="primary" @click="handleEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'draft'" link type="primary" @click="handleSubmit(row)"
              >提交</el-button
            >
            <el-button
              v-if="row.status === 'pending'"
              link
              type="success"
              @click="handleApprove(row)"
              >审批</el-button
            >
            <el-button
              v-if="row.status === 'approved'"
              link
              type="warning"
              @click="handleConfirm(row)"
              >确认入库</el-button
            >
            <el-button
              v-if="row.status === 'draft' || row.status === 'pending'"
              link
              type="danger"
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

    <!-- 入库单详情对话框 -->
    <el-dialog :title="dialogTitle" v-model="detailDialogVisible" width="900px" append-to-body>
      <div v-if="currentInbound">
        <!-- 基本信息 -->
        <el-descriptions :column="3" border>
          <el-descriptions-item label="入库单号">{{
            currentInbound.inboundNo
          }}</el-descriptions-item>
          <el-descriptions-item label="入库类型">{{
            currentInbound.inboundTypeName
          }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{
            currentInbound.warehouseName
          }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{
            currentInbound.supplierName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="总数量">{{
            formatNumber(currentInbound.totalQuantity)
          }}</el-descriptions-item>
          <el-descriptions-item label="总金额"
            >¥ {{ formatCurrency(currentInbound.totalAmount) }}</el-descriptions-item
          >
          <el-descriptions-item label="状态">
            <el-tag :type="getStatusTag(currentInbound.status)" size="small">
              {{ currentInbound.statusName }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="创建人">{{ currentInbound.createBy }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{
            currentInbound.createTime
          }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="3">{{
            currentInbound.remark || '-'
          }}</el-descriptions-item>
        </el-descriptions>

        <!-- 明细表格 -->
        <el-divider content-position="left">入库明细</el-divider>
        <el-table :data="currentInbound.items" border style="width: 100%">
          <el-table-column label="物料编码" prop="materialCode" width="120" />
          <el-table-column label="物料名称" prop="materialName" width="150" show-overflow-tooltip />
          <el-table-column
            label="规格型号"
            prop="specification"
            width="120"
            show-overflow-tooltip
          />
          <el-table-column label="单位" prop="unit" width="80" align="center" />
          <el-table-column label="批次号" prop="batchNo" width="120" />
          <el-table-column label="数量" prop="quantity" width="100" align="right">
            <template #default="{ row }">
              {{ formatNumber(row.quantity) }}
            </template>
          </el-table-column>
          <el-table-column label="单价" prop="unitPrice" width="100" align="right">
            <template #default="{ row }"> ¥ {{ formatCurrency(row.unitPrice) }} </template>
          </el-table-column>
          <el-table-column label="金额" prop="amount" width="120" align="right">
            <template #default="{ row }"> ¥ {{ formatCurrency(row.amount) }} </template>
          </el-table-column>
          <el-table-column label="库位" prop="locationCode" width="100" />
          <el-table-column label="生产日期" prop="productionDate" width="110" align="center" />
          <el-table-column label="到期日期" prop="expiryDate" width="110" align="center" />
        </el-table>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
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
import { formatCurrency, formatNumber } from '@/utils/format'
import type { InboundQueryParams, InboundVO } from '@/types/inventory/inbound'

const router = useRouter()

// 查询参数
const queryParams = reactive<InboundQueryParams>({
  current: 1,
  pageSize: 10,
  inboundNo: '',
  inboundType: '',
  warehouseId: '',
  status: '',
})

// 响应式数据
const loading = ref(false)
const inboundList = ref<InboundVO[]>([])
const total = ref(0)
const ids = ref<string[]>([])
const single = ref(true)
const multiple = ref(true)
const detailDialogVisible = ref(false)
const currentInbound = ref<InboundVO | null>(null)
const dialogTitle = ref('')

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
  queryParams.status = ''
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
  router.push('/inventory/inbound/create')
}

// 编辑入库单
const handleEdit = (row?: InboundVO) => {
  const inboundId = row ? row.inboundId : ids.value[0]
  if (inboundId) {
    router.push(`/inventory/inbound/edit/${inboundId}`)
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

// 刷新
const handleRefresh = () => {
  getList()
  ElMessage.success('数据已刷新')
}

// 查看详情
const handleView = (row: InboundVO) => {
  router.push(`/inventory/inbound/detail/${row.inboundId}`)
}

// 提交审批
const handleSubmit = async (row: InboundVO) => {
  try {
    await ElMessageBox.confirm('确认提交审批吗？', '提示', { type: 'warning' })
    const res = await inboundApi.submitApprove(row.inboundId)
    if (res.data) {
      ElMessage.success('提交审批成功')
      getList()
    } else {
      ElMessage.error('提交审批失败')
    }
  } catch (error) {
    console.error('提交审批失败:', error)
    ElMessage.error('提交审批失败')
  }
}

// 审批通过
const handleApprove = async (row: InboundVO) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入审批备注', '审批通过', {
      confirmButtonText: '通过',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入备注（可选）',
    })

    // 这里需要获取当前用户信息，暂时使用模拟数据
    const currentUser = {
      id: '1',
      name: '当前用户',
    }

    const res = await inboundApi.approve({
      inboundId: row.inboundId,
      approverId: currentUser.id,
      approverName: currentUser.name,
      remark: value || '',
    })

    if (res.data) {
      ElMessage.success('审批通过成功')
      getList()
    } else {
      ElMessage.error('审批通过失败')
    }
  } catch (error) {
    console.error('审批通过失败:', error)
    ElMessage.error('审批通过失败')
  }
}

// 确认入库
const handleConfirm = async (row: InboundVO) => {
  try {
    await ElMessageBox.confirm('确认入库吗？', '提示', { type: 'warning' })

    // 这里需要获取当前用户信息，暂时使用模拟数据
    const currentUser = {
      id: '1',
      name: '当前用户',
    }

    const res = await inboundApi.confirm(row.inboundId, currentUser.id, currentUser.name)
    if (res.data) {
      ElMessage.success('确认入库成功')
      getList()
    } else {
      ElMessage.error('确认入库失败')
    }
  } catch (error) {
    console.error('确认入库失败:', error)
    ElMessage.error('确认入库失败')
  }
}

// 取消入库单
const handleCancel = async (row: InboundVO) => {
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因', '取消入库单', {
      confirmButtonText: '确认取消',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入取消原因',
      inputValidator: (value) => {
        if (!value) {
          return '取消原因不能为空'
        }
        return true
      },
    })

    const res = await inboundApi.cancel(row.inboundId, value)
    if (res.data) {
      ElMessage.success('取消成功')
      getList()
    } else {
      ElMessage.error('取消失败')
    }
  } catch (error) {
    console.error('取消失败:', error)
    ElMessage.error('取消失败')
  }
}
import { InboundEnum } from '@/enums/inventory'

// 获取状态标签样式
const getStatusTag = (status: string): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const statusMap: Record<string, 'success' | 'warning' | 'info' | 'danger' | undefined> = {
    draft: 'info',
    pending: 'warning',
    approved: 'success',
    completed: 'success',
    cancelled: 'danger',
  }
  return statusMap[status]
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
