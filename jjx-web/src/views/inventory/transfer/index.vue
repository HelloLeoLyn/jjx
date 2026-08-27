<template>
  <div class="transfer-list">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="调拨单号">
          <el-input
            v-model="queryParams.transferNo"
            placeholder="请输入调拨单号"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="调拨类型">
          <el-select v-model="queryParams.transferType" placeholder="请选择" clearable style="width: 120px">
            <el-option label="普通调拨" value="normal" />
            <el-option label="库位调拨" value="location" />
            <el-option label="紧急调拨" value="urgent" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.orderStatus" placeholder="请选择" clearable style="width: 120px">
            <el-option label="草稿" :value="0" />
            <el-option label="待审批" :value="1" />
            <el-option label="已批准" :value="2" />
            <el-option label="已出库" :value="6" />
            <el-option label="已完成" :value="10" />
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
            <el-icon><Plus /></el-icon>新建调拨单
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
      <el-table v-loading="loading" :data="transferList" border style="width: 100%">
        <el-table-column label="调拨单号" prop="transferNo" width="160" />
        <el-table-column label="调拨类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getTransferTypeTag(row.transferType)" size="small">
              {{ getTransferTypeName(row.transferType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="源仓库" prop="fromWarehouseName" width="120" />
        <el-table-column label="目标仓库" prop="toWarehouseName" width="120" />
        <el-table-column label="总数量" width="100" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.totalQuantity) }}
          </template>
        </el-table-column>
        <el-table-column label="总金额" width="120" align="right">
          <template #default="{ row }"> ¥ {{ formatCurrency(row.totalAmount) }} </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.orderStatus)" size="small">
              {{ getStatusName(row.orderStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建人" prop="createBy" width="100" />
        <el-table-column label="创建时间" prop="createTime" width="150" align="center" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <el-button link type="info" @click="handlePrint(row)">打印</el-button>
            <el-button v-if="row.orderStatus === 0" link type="primary" @click="handleSubmitApprove(row)">提交审批</el-button>
            <el-button v-if="row.approveStatus === 1 && row.orderStatus === 1" link type="success" @click="handleApprove(row)">审批</el-button>
            <el-button v-if="row.orderStatus === 2" link type="warning" v-hasPermi="['inventory:transfer:approve']" @click="handleConfirmOut(row)">确认调出</el-button>
            <el-button v-if="row.orderStatus === 6" link type="success" v-hasPermi="['inventory:transfer:approve']" @click="handleConfirmIn(row)">确认调入</el-button>
            <el-button v-if="row.orderStatus === 0 || row.orderStatus === 1" link type="danger" @click="handleCancel(row)">取消</el-button>
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

    <!-- 新建调拨单弹窗 -->
    <el-dialog v-model="createVisible" title="新建调拨单" width="720px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="110px">
        <el-form-item label="调拨类型" prop="transferType">
          <el-select v-model="createForm.transferType" style="width: 100%">
            <el-option label="普通调拨" value="normal" />
            <el-option label="库位调拨" value="location" />
            <el-option label="紧急调拨" value="urgent" />
          </el-select>
        </el-form-item>
        <el-form-item label="调出仓库" prop="fromWarehouseId">
          <el-select v-model="createForm.fromWarehouseId" placeholder="请选择" style="width: 100%" @change="handleFromWarehouseChange">
            <el-option v-for="w in warehouseOptions" :key="w.warehouseId" :label="w.warehouseName" :value="String(w.warehouseId)" />
          </el-select>
        </el-form-item>
        <el-form-item label="调入仓库" prop="toWarehouseId">
          <el-select v-model="createForm.toWarehouseId" placeholder="请选择" style="width: 100%" @change="handleToWarehouseChange">
            <el-option v-for="w in warehouseOptions" :key="w.warehouseId" :label="w.warehouseName" :value="String(w.warehouseId)" />
          </el-select>
        </el-form-item>
        <el-form-item label="调拨日期" prop="transferDate">
          <el-date-picker v-model="createForm.transferDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
        <el-form-item label="调拨明细">
          <el-table :data="createForm.items" border size="small" style="width: 100%">
            <el-table-column label="物料" min-width="180">
              <template #default="{ row }">
                <el-select v-model="row.materialId" filterable placeholder="选择物料" style="width: 100%" @change="(val: string) => handleMaterialChange(row, val)">
                  <el-option
                    v-for="m in materialOptions"
                    :key="m.materialId"
                    :label="`${m.materialName}${m.specification ? ' / ' + m.specification : ''}`"
                    :value="String(m.materialId)"
                  />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="120">
              <template #default="{ row }">
                <el-input-number v-model="row.quantity" :min="0" :precision="2" style="width: 100%" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60" align="center">
              <template #default="{ $index }">
                <el-button link type="danger" @click="createForm.items.splice($index, 1)">删</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-button size="small" type="primary" plain style="margin-top: 8px" @click="addMaterialRow">+ 添加物料</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreateSubmit">确定创建</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="调拨单详情" size="640px">
      <template v-if="currentDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="调拨单号">{{ currentDetail.transferNo }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ getTransferTypeName(currentDetail.transferType) }}</el-descriptions-item>
          <el-descriptions-item label="源仓库">{{ currentDetail.fromWarehouseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="目标仓库">{{ currentDetail.toWarehouseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="源库位">{{ currentDetail.fromLocationName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="目标库位">{{ currentDetail.toLocationName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ getStatusName(currentDetail.orderStatus) }}</el-descriptions-item>
          <el-descriptions-item label="审批状态">{{ getApproveStatusName(currentDetail.approveStatus) }}</el-descriptions-item>
          <el-descriptions-item label="调拨日期">{{ currentDetail.transferDate || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ currentDetail.createBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ currentDetail.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ currentDetail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">调拨明细</el-divider>
        <el-table :data="currentDetail.items || []" border size="small" style="width: 100%">
          <el-table-column label="物料编码" prop="materialCode" width="110" />
          <el-table-column label="物料名称" prop="materialName" min-width="140" show-overflow-tooltip />
          <el-table-column label="数量" prop="quantity" width="80" align="right" />
          <el-table-column label="单位" prop="unit" width="60" align="center" />
          <el-table-column label="批次" prop="batchNo" width="100" />
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'TransferList',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { transferApi, type TransferVO } from '@/api/inventory/transfer'
import { warehouseApi } from '@/api/inventory/warehouse'
import { materialApi } from '@/api/inventory/material'
import { formatCurrency, formatNumber } from '@/utils/format'

// 查询参数
const queryParams = reactive({
  current: 1,
  pageSize: 10,
  transferNo: '',
  transferType: '',
  orderStatus: '',
})

// 响应式数据
const loading = ref(false)
const transferList = ref<TransferVO[]>([])
const total = ref(0)

// 新建弹窗
const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref()
const warehouseOptions = ref<any[]>([])
const materialOptions = ref<any[]>([])

const createForm = reactive({
  transferType: 'normal',
  fromWarehouseId: '',
  toWarehouseId: '',
  transferDate: '',
  remark: '',
  items: [] as any[],
})

const createRules = {
  transferType: [{ required: true, message: '请选择调拨类型', trigger: 'change' }],
  fromWarehouseId: [{ required: true, message: '请选择调出仓库', trigger: 'change' }],
  toWarehouseId: [{ required: true, message: '请选择调入仓库', trigger: 'change' }],
}

// 详情抽屉
const detailVisible = ref(false)
const currentDetail = ref<TransferVO | null>(null)

// 获取调拨单列表
const getList = async () => {
  loading.value = true
  try {
    const res = await transferApi.list({
      current: queryParams.current,
      pageSize: queryParams.pageSize,
      transferNo: queryParams.transferNo || undefined,
      transferType: queryParams.transferType || undefined,
      orderStatus: queryParams.orderStatus === '' ? undefined : String(queryParams.orderStatus),
    })
    transferList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('获取调拨单列表失败:', error)
    ElMessage.error('获取调拨单列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索/重置/刷新
const handleQuery = () => {
  queryParams.current = 1
  getList()
}
const handleReset = () => {
  queryParams.current = 1
  queryParams.transferNo = ''
  queryParams.transferType = ''
  queryParams.orderStatus = ''
  getList()
}
const handleRefresh = () => {
  getList()
  ElMessage.success('数据已刷新')
}

// 新建
const handleCreate = async () => {
  createForm.transferType = 'normal'
  createForm.fromWarehouseId = ''
  createForm.toWarehouseId = ''
  createForm.transferDate = ''
  createForm.remark = ''
  createForm.items = [{ materialId: '', quantity: 0 }]
  if (warehouseOptions.value.length === 0) {
    await loadWarehouseOptions()
  }
  if (materialOptions.value.length === 0) {
    await loadMaterialOptions()
  }
  createVisible.value = true
}

const loadWarehouseOptions = async () => {
  try {
    const res = await warehouseApi.getAllEnabled()
    warehouseOptions.value = res.data || []
  } catch (error) {
    console.error('加载仓库失败:', error)
  }
}

const loadMaterialOptions = async () => {
  try {
    const res = await materialApi.page({ pageNum: 1, pageSize: 500 } as any)
    materialOptions.value = (res.data as any)?.records || res.data || []
  } catch (error) {
    console.error('加载物料失败:', error)
  }
}

const handleFromWarehouseChange = () => {}
const handleToWarehouseChange = () => {}

const addMaterialRow = () => {
  createForm.items.push({ materialId: '', quantity: 0 })
}

const handleMaterialChange = (row: any, materialId: string) => {
  const mat = materialOptions.value.find((m) => String(m.materialId) === materialId)
  if (mat) {
    row.materialName = mat.materialName
    row.materialCode = mat.materialCode
  }
}

const handleCreateSubmit = async () => {
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  if (createForm.items.length === 0 || createForm.items.some((i) => !i.materialId || !i.quantity)) {
    ElMessage.warning('请填写完整的调拨明细')
    return
  }
  creating.value = true
  try {
    const data = {
      transferType: createForm.transferType,
      fromWarehouseId: Number(createForm.fromWarehouseId),
      toWarehouseId: Number(createForm.toWarehouseId),
      transferDate: createForm.transferDate || undefined,
      remark: createForm.remark || undefined,
      items: createForm.items.map((i: any) => ({
        materialId: Number(i.materialId),
        materialCode: i.materialCode,
        materialName: i.materialName,
        quantity: i.quantity,
      })),
    }
    await transferApi.create(data)
    ElMessage.success('调拨单创建成功')
    createVisible.value = false
    getList()
  } catch (error) {
    console.error('创建调拨单失败:', error)
    ElMessage.error('创建调拨单失败')
  } finally {
    creating.value = false
  }
}

// 详情
const handleView = async (row: TransferVO) => {
  try {
    const res = await transferApi.getById(row.transferId)
    currentDetail.value = res.data
    detailVisible.value = true
  } catch (error) {
    console.error('获取调拨单详情失败:', error)
    ElMessage.error('获取调拨单详情失败')
  }
}

// 打印调拨单（跳转独立打印页）
const handlePrint = (row: TransferVO) => {
  window.open(`/print/transfer/${row.transferId}`, '_blank')
}

// 状态流转
const handleSubmitApprove = (row: TransferVO) => {
  ElMessageBox.confirm(`确定提交调拨单 ${row.transferNo} 审批吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await transferApi.submitApprove(row.transferId)
      ElMessage.success('已提交审批')
      getList()
    })
    .catch(() => {})
}

const handleApprove = (row: TransferVO) => {
  ElMessageBox.confirm(`确定审批通过调拨单 ${row.transferNo} 吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await transferApi.approve(row.transferId, '审批通过')
      ElMessage.success('审批通过')
      getList()
    })
    .catch(() => {})
}

const handleConfirmOut = (row: TransferVO) => {
  ElMessageBox.confirm(
    `确认调出将扣减源仓库库存，确定对调拨单 ${row.transferNo} 确认调出吗？`,
    '确认调出',
    { type: 'warning' }
  )
    .then(async () => {
      await transferApi.confirmOut(row.transferId)
      ElMessage.success('调出确认成功')
      getList()
    })
    .catch(() => {})
}

const handleConfirmIn = (row: TransferVO) => {
  ElMessageBox.confirm(
    `确认调入将增加目标仓库库存，确定对调拨单 ${row.transferNo} 确认调入吗？`,
    '确认调入',
    { type: 'warning' }
  )
    .then(async () => {
      await transferApi.confirmIn(row.transferId)
      ElMessage.success('调入确认成功')
      getList()
    })
    .catch(() => {})
}

const handleCancel = (row: TransferVO) => {
  ElMessageBox.confirm(`确定取消调拨单 ${row.transferNo} 吗？`, '取消确认', { type: 'warning' })
    .then(async () => {
      await transferApi.cancel(row.transferId, '用户取消')
      ElMessage.success('已取消')
      getList()
    })
    .catch(() => {})
}

// 类型/状态映射
const getTransferTypeName = (type?: string) => {
  const map: Record<string, string> = { normal: '普通调拨', location: '库位调拨', urgent: '紧急调拨' }
  return type ? map[type] || type : '-'
}

const getTransferTypeTag = (type?: string): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const map: Record<string, any> = { normal: 'success', location: 'warning', urgent: 'danger' }
  return type ? map[type] : undefined
}

const STATUS_NAMES: Record<number, string> = {
  0: '草稿',
  1: '待审批',
  2: '已批准',
  3: '已驳回',
  6: '已出库',
  9: '已取消',
  10: '已完成',
  12: '调拨中',
}

const getStatusName = (status?: number) => (status !== undefined ? STATUS_NAMES[status] || String(status) : '-')

const getStatusTag = (status?: number): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const map: Record<number, any> = {
    0: 'info',
    1: 'warning',
    2: 'success',
    6: 'warning',
    9: 'danger',
    10: 'success',
  }
  return status !== undefined ? map[status] : undefined
}

const getApproveStatusName = (status?: number) => {
  const map: Record<number, string> = { 0: '未提交', 1: '待审批', 2: '已通过', 3: '已驳回' }
  return status !== undefined ? map[status] || String(status) : '-'
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.transfer-list {
  padding: 20px;
}

.search-card,
.operation-card,
.table-card {
  margin-bottom: 16px;
}
</style>
