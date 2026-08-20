<template>
  <div class="stocktake-list">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="盘点单号">
          <el-input
            v-model="queryParams.stocktakeNo"
            placeholder="请输入盘点单号"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="盘点类型">
          <el-select v-model="queryParams.stocktakeType" placeholder="请选择" clearable style="width: 120px">
            <el-option label="全盘" value="full" />
            <el-option label="抽盘" value="partial" />
            <el-option label="循环盘点" value="cycle" />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库">
          <el-select v-model="queryParams.warehouseId" placeholder="请选择" clearable style="width: 140px">
            <el-option v-for="w in warehouseOptions" :key="w.warehouseId" :label="w.warehouseName" :value="String(w.warehouseId)" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="queryParams.orderStatus" placeholder="请选择" clearable style="width: 120px">
            <el-option label="草稿" :value="0" />
            <el-option label="盘点中" :value="4" />
            <el-option label="已确认" :value="5" />
            <el-option label="已处理" :value="11" />
            <el-option label="已关闭" :value="8" />
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
            <el-icon><Plus /></el-icon>新建盘点单
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
      <el-table v-loading="loading" :data="stocktakeList" border style="width: 100%">
        <el-table-column label="盘点单号" prop="stocktakeNo" width="160" />
        <el-table-column label="类型" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStocktakeTypeTag(row.stocktakeType)" size="small">
              {{ getStocktakeTypeName(row.stocktakeType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="仓库" prop="warehouseName" width="120" />
        <el-table-column label="物料数" prop="materialCount" width="90" align="right" />
        <el-table-column label="差异数" width="90" align="right">
          <template #default="{ row }">
            <span :style="{ color: (row.totalDiffQuantity ?? 0) !== 0 ? '#f56c6c' : '' }">
              {{ formatNumber(row.totalDiffQuantity ?? 0) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="差异金额" width="110" align="right">
          <template #default="{ row }">
            <span :style="{ color: (row.totalDiffAmount ?? 0) !== 0 ? '#f56c6c' : '' }">
              ¥ {{ formatCurrency(row.totalDiffAmount ?? 0) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.orderStatus)" size="small">
              {{ getStatusName(row.orderStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建人" prop="createByName || createBy" width="100" />
        <el-table-column label="创建时间" prop="createTime" width="150" align="center" />
        <el-table-column label="操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <el-button link type="info" @click="handlePrint(row)">打印</el-button>
            <el-button v-if="row.orderStatus === 0" link type="success" @click="handleStart(row)">开始盘点</el-button>
            <el-button v-if="row.orderStatus === 4" link type="primary" @click="handleInput(row)">录入实盘</el-button>
            <el-button v-if="row.orderStatus === 4" link type="success" v-hasPermi="['inventory:stocktake:approve']" @click="handleConfirmResult(row)">确认结果</el-button>
            <el-button v-if="row.orderStatus === 5" link type="warning" @click="handleProcessDiff(row)">处理差异</el-button>
            <el-button v-if="row.orderStatus === 11" link type="info" @click="handleClose(row)">关闭</el-button>
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

    <!-- 新建盘点单弹窗 -->
    <el-dialog v-model="createVisible" title="新建盘点单" width="520px" destroy-on-close>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="盘点类型" prop="stocktakeType">
          <el-select v-model="createForm.stocktakeType" style="width: 100%">
            <el-option label="全盘" value="full" />
            <el-option label="抽盘" value="partial" />
            <el-option label="循环盘点" value="cycle" />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="createForm.warehouseId" placeholder="请选择仓库" style="width: 100%">
            <el-option v-for="w in warehouseOptions" :key="w.warehouseId" :label="w.warehouseName" :value="String(w.warehouseId)" />
          </el-select>
        </el-form-item>
        <el-form-item label="计划开始">
          <el-date-picker v-model="createForm.planStartTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="计划结束">
          <el-date-picker v-model="createForm.planEndTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
        </el-form-item>
        <el-form-item label="盘点人">
          <el-input v-model="createForm.stocktakerName" placeholder="请输入盘点人" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreateSubmit">确定创建</el-button>
      </template>
    </el-dialog>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="盘点单详情" size="680px">
      <template v-if="currentDetail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="盘点单号">{{ currentDetail.stocktakeNo }}</el-descriptions-item>
          <el-descriptions-item label="类型">{{ getStocktakeTypeName(currentDetail.stocktakeType) }}</el-descriptions-item>
          <el-descriptions-item label="仓库">{{ currentDetail.warehouseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ getStatusName(currentDetail.orderStatus) }}</el-descriptions-item>
          <el-descriptions-item label="系统总量">{{ formatNumber(currentDetail.totalSystemQuantity ?? 0) }}</el-descriptions-item>
          <el-descriptions-item label="实盘总量">{{ formatNumber(currentDetail.totalActualQuantity ?? 0) }}</el-descriptions-item>
          <el-descriptions-item label="差异数量">{{ formatNumber(currentDetail.totalDiffQuantity ?? 0) }}</el-descriptions-item>
          <el-descriptions-item label="差异金额">¥ {{ formatCurrency(currentDetail.totalDiffAmount ?? 0) }}</el-descriptions-item>
          <el-descriptions-item label="盘点人">{{ currentDetail.stocktakerName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建人">{{ currentDetail.createByName || currentDetail.createBy || '-' }}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{ currentDetail.createTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ currentDetail.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <el-divider content-position="left">盘点明细</el-divider>
        <el-table :data="currentDetail.items || []" border size="small" style="width: 100%">
          <el-table-column label="物料编码" prop="materialCode" width="110" />
          <el-table-column label="物料名称" prop="materialName" min-width="130" show-overflow-tooltip />
          <el-table-column label="批次" prop="batchNo" width="100" />
          <el-table-column label="库位" prop="locationName" width="90">
            <template #default="{ row }">
              <span v-if="row.locationName">{{ row.locationName }}</span>
              <span v-else style="color: #999">-</span>
            </template>
          </el-table-column>
          <el-table-column label="系统数" prop="systemQuantity" width="80" align="right" />
          <el-table-column label="实盘数" prop="actualQuantity" width="80" align="right" />
          <el-table-column label="差异" width="80" align="right">
            <template #default="{ row }">
              <span :style="{ color: (row.diffQuantity || 0) !== 0 ? '#f56c6c' : '' }">
                {{ formatNumber(row.diffQuantity) }}
              </span>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>

    <!-- 录入实盘数弹窗 -->
    <el-dialog v-model="inputVisible" title="录入实盘数" width="680px" destroy-on-close>
      <el-table :data="inputItems" border size="small" style="width: 100%" max-height="420">
        <el-table-column label="物料编码" prop="materialCode" width="110" />
        <el-table-column label="物料名称" prop="materialName" min-width="120" show-overflow-tooltip />
        <el-table-column label="系统数" prop="systemQuantity" width="80" align="right" />
        <el-table-column label="实盘数" width="130">
          <template #default="{ row }">
            <el-input-number v-model="row.actualQuantity" :min="0" :precision="4" style="width: 100%" />
          </template>
        </el-table-column>
        <el-table-column label="差异" width="90" align="right">
          <template #default="{ row }">
            {{ formatNumber((row.actualQuantity ?? row.systemQuantity ?? 0) - (row.systemQuantity ?? 0)) }}
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="inputVisible = false">取消</el-button>
        <el-button type="primary" :loading="inputing" @click="handleInputSubmit">保存实盘数</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'StocktakeList',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import { stocktakeApi, type StocktakeVO, type StocktakeItemVO } from '@/api/inventory/stocktake'
import { warehouseApi } from '@/api/inventory/warehouse'
import { formatCurrency, formatNumber } from '@/utils/format'

// 查询参数
const queryParams = reactive({
  current: 1,
  pageSize: 10,
  stocktakeNo: '',
  stocktakeType: '',
  warehouseId: '',
  orderStatus: '',
})

// 响应式数据
const loading = ref(false)
const stocktakeList = ref<StocktakeVO[]>([])
const total = ref(0)
const warehouseOptions = ref<any[]>([])

// 新建弹窗
const createVisible = ref(false)
const creating = ref(false)
const createFormRef = ref()

const createForm = reactive({
  stocktakeType: 'full',
  warehouseId: '',
  planStartTime: '',
  planEndTime: '',
  stocktakerName: '',
  remark: '',
})

const createRules = {
  stocktakeType: [{ required: true, message: '请选择盘点类型', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
}

// 详情抽屉
const detailVisible = ref(false)
const currentDetail = ref<StocktakeVO | null>(null)

// 录入实盘
const inputVisible = ref(false)
const inputing = ref(false)
const inputItems = ref<StocktakeItemVO[]>([])
const inputStocktakeId = ref('')

// 获取盘点单列表
const getList = async () => {
  loading.value = true
  try {
    const res = await stocktakeApi.list({
      current: queryParams.current,
      pageSize: queryParams.pageSize,
      stocktakeNo: queryParams.stocktakeNo || undefined,
      stocktakeType: queryParams.stocktakeType || undefined,
      warehouseId: queryParams.warehouseId || undefined,
      orderStatus: queryParams.orderStatus === '' ? undefined : String(queryParams.orderStatus),
    })
    stocktakeList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch (error) {
    console.error('获取盘点单列表失败:', error)
    ElMessage.error('获取盘点单列表失败')
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
  queryParams.stocktakeNo = ''
  queryParams.stocktakeType = ''
  queryParams.warehouseId = ''
  queryParams.orderStatus = ''
  getList()
}
const handleRefresh = () => {
  getList()
  ElMessage.success('数据已刷新')
}

// 新建
const handleCreate = async () => {
  createForm.stocktakeType = 'full'
  createForm.warehouseId = ''
  createForm.planStartTime = ''
  createForm.planEndTime = ''
  createForm.stocktakerName = ''
  createForm.remark = ''
  if (warehouseOptions.value.length === 0) {
    await loadWarehouseOptions()
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

const handleCreateSubmit = async () => {
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    const data: Record<string, unknown> = {
      stocktakeType: createForm.stocktakeType,
      warehouseId: Number(createForm.warehouseId),
      remark: createForm.remark || undefined,
      stocktakerName: createForm.stocktakerName || undefined,
    }
    if (createForm.planStartTime) data.planStartTime = createForm.planStartTime
    if (createForm.planEndTime) data.planEndTime = createForm.planEndTime
    await stocktakeApi.create(data)
    ElMessage.success('盘点单创建成功')
    createVisible.value = false
    getList()
  } catch (error) {
    console.error('创建盘点单失败:', error)
    ElMessage.error('创建盘点单失败')
  } finally {
    creating.value = false
  }
}

// 详情
const handleView = async (row: StocktakeVO) => {
  try {
    const res = await stocktakeApi.getById(row.stocktakeId)
    currentDetail.value = res.data
    detailVisible.value = true
  } catch (error) {
    console.error('获取盘点单详情失败:', error)
    ElMessage.error('获取盘点单详情失败')
  }
}

// 打印盘点单（跳转独立打印页）
const handlePrint = (row: StocktakeVO) => {
  window.open(`/print/stocktake/${row.stocktakeId}`, '_blank')
}

// 开始盘点
const handleStart = (row: StocktakeVO) => {
  ElMessageBox.confirm(`确定开始盘点单 ${row.stocktakeNo} 吗？`, '提示', { type: 'warning' })
    .then(async () => {
      await stocktakeApi.start(row.stocktakeId)
      ElMessage.success('已开始盘点')
      getList()
    })
    .catch(() => {})
}

// 录入实盘数
const handleInput = async (row: StocktakeVO) => {
  try {
    const res = await stocktakeApi.getById(row.stocktakeId)
    const detail = res.data
    inputItems.value = (detail?.items || []).map((item) => ({
      ...item,
      actualQuantity: item.actualQuantity ?? item.systemQuantity ?? 0,
    }))
    inputStocktakeId.value = row.stocktakeId
    inputVisible.value = true
  } catch (error) {
    console.error('加载盘点明细失败:', error)
    ElMessage.error('加载盘点明细失败')
  }
}

const handleInputSubmit = async () => {
  inputing.value = true
  try {
    const items = inputItems.value.map((item) => ({
      itemId: item.itemId,
      materialId: item.materialId,
      materialCode: item.materialCode,
      materialName: item.materialName,
      batchNo: item.batchNo,
      locationId: item.locationId,
      systemQuantity: item.systemQuantity,
      actualQuantity: item.actualQuantity ?? item.systemQuantity ?? 0,
    }))
    await stocktakeApi.inputData(inputStocktakeId.value, items)
    ElMessage.success('实盘数已保存')
    inputVisible.value = false
    getList()
  } catch (error) {
    console.error('保存实盘数失败:', error)
    ElMessage.error('保存实盘数失败')
  } finally {
    inputing.value = false
  }
}

// 确认结果
const handleConfirmResult = (row: StocktakeVO) => {
  ElMessageBox.confirm(`确定确认盘点单 ${row.stocktakeNo} 的结果吗？`, '确认结果', { type: 'warning' })
    .then(async () => {
      await stocktakeApi.calculateDiff(row.stocktakeId)
      await stocktakeApi.confirmResult(row.stocktakeId)
      ElMessage.success('盘点结果已确认')
      getList()
    })
    .catch(() => {})
}

// 处理差异
const handleProcessDiff = (row: StocktakeVO) => {
  ElMessageBox.confirm(
    `处理差异将自动生成出入库调整单，确定处理盘点单 ${row.stocktakeNo} 的差异吗？`,
    '处理差异',
    { type: 'warning' }
  )
    .then(async () => {
      await stocktakeApi.processDiff(row.stocktakeId, '盘点差异调整')
      ElMessage.success('差异已处理')
      getList()
    })
    .catch(() => {})
}

// 关闭
const handleClose = (row: StocktakeVO) => {
  ElMessageBox.confirm(`确定关闭盘点单 ${row.stocktakeNo} 吗？`, '关闭确认', { type: 'warning' })
    .then(async () => {
      await stocktakeApi.close(row.stocktakeId)
      ElMessage.success('盘点单已关闭')
      getList()
    })
    .catch(() => {})
}

// 类型/状态映射
const getStocktakeTypeName = (type?: string) => {
  const map: Record<string, string> = { full: '全盘', partial: '抽盘', cycle: '循环盘点' }
  return type ? map[type] || type : '-'
}

const getStocktakeTypeTag = (type?: string): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const map: Record<string, any> = { full: 'success', partial: 'warning', cycle: 'info' }
  return type ? map[type] : undefined
}

const STATUS_NAMES: Record<number, string> = {
  0: '草稿',
  1: '待审批',
  2: '已批准',
  4: '盘点中',
  5: '已确认',
  8: '已关闭',
  9: '已取消',
  11: '已处理',
}

const getStatusName = (status?: number) => (status !== undefined ? STATUS_NAMES[status] || String(status) : '-')

const getStatusTag = (status?: number): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const map: Record<number, any> = {
    0: 'info',
    1: 'warning',
    4: 'warning',
    5: 'success',
    8: 'info',
    9: 'danger',
    11: 'success',
  }
  return status !== undefined ? map[status] : undefined
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.stocktake-list {
  padding: 20px;
}

.search-card,
.operation-card,
.table-card {
  margin-bottom: 16px;
}
</style>
