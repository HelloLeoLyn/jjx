<template>
  <div class="location-page">
    <!-- 页面头部 -->
    <el-page-header @back="goBack" content="返回仓库列表">
      <template #title>
        <span class="page-title">库位管理</span>
        <span v-if="currentWarehouseName" class="warehouse-name">
          （当前仓库：{{ currentWarehouseName }}）
        </span>
      </template>
    </el-page-header>

    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="库位编码">
          <el-input
            v-model="queryParams.locationCode"
            placeholder="请输入库位编码"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="库位名称">
          <el-input
            v-model="queryParams.locationName"
            placeholder="请输入库位名称"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="库位类型">
          <el-select
            v-model="queryParams.locationType"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="普通库位" value="normal" />
            <el-option label="冷冻库位" value="frozen" />
            <el-option label="易燃库位" value="flammable" />
            <el-option label="贵重库位" value="valuable" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="queryParams.status"
            placeholder="请选择"
            clearable
            style="width: 100px"
          >
            <el-option label="正常" value="0" />
            <el-option label="停用" value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 仓库切换栏 -->
    <el-card class="warehouse-card">
      <div class="warehouse-selector">
        <span class="label">选择仓库：</span>
        <el-select
          v-model="currentWarehouseId"
          placeholder="请选择仓库"
          filterable
          clearable
          style="width: 250px"
          @change="handleWarehouseChange"
        >
          <el-option
            v-for="item in warehouseList"
            :key="item.warehouseId"
            :label="`${item.warehouseCode} - ${item.warehouseName}`"
            :value="item.warehouseId"
          />
        </el-select>
        <el-tag v-if="currentWarehouseName" type="info" size="large" class="capacity-info">
          总容量: {{ formatNumber(warehouseCapacity) }} | 已用:
          {{ formatNumber(warehouseUsedCapacity) }} | 可用:
          {{ formatNumber(warehouseAvailableCapacity) }}
        </el-tag>
      </div>
    </el-card>

    <!-- 操作栏 -->
    <el-card class="operation-card">
      <el-row :gutter="10">
        <el-col :span="1.5">
          <el-button type="primary" @click="handleAdd">
            <el-icon><Plus /></el-icon>新增库位
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button @click="handleImport">
            <el-icon><Upload /></el-icon>导入
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button @click="handleExport">
            <el-icon><Download /></el-icon>导出
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 库位表格 -->
    <el-card class="table-card">
      <el-table
        v-loading="loading"
        :data="locationList"
        @selection-change="handleSelectionChange"
        border
        stripe
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="库位编码" prop="locationCode" width="150" />
        <el-table-column label="库位名称" prop="locationName" width="180" />
        <el-table-column label="库位类型" prop="locationType" width="100" align="center">
          <template #default="{ row }"> </template>
        </el-table-column>
        <el-table-column label="容量" prop="capacity" width="100" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.capacity) }}
          </template>
        </el-table-column>
        <el-table-column label="已用容量" prop="usedCapacity" width="100" align="right">
          <template #default="{ row }">
            <span :class="{ 'capacity-warning': getCapacityRate(row) > 80 }">
              {{ formatNumber(row.usedCapacity) }}
              ({{ getCapacityRate(row) }}%)
            </span>
          </template>
        </el-table-column>
        <el-table-column label="剩余容量" prop="availableCapacity" width="100" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.capacity - row.usedCapacity) }}
          </template>
        </el-table-column>
        <el-table-column label="尺寸(W×H×D cm)" width="150" align="center">
          <template #default="{ row }">
            {{ row.width || '-' }} × {{ row.height || '-' }} ×
            {{ row.depth || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="排序" prop="sortOrder" width="80" align="center" />
        <el-table-column label="状态" prop="status" width="80" align="center">
          <template #default="{ row }">
            <el-switch
              v-model="row.status"
              active-value="1"
              inactive-value="0"
              @change="handleStatusChange(row)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="['inventory:warehouse:edit']" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="danger" v-hasPermi="['inventory:warehouse:delete']" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <!-- 库位导入对话框 -->
    <LocationImportDialog
      v-model:visible="importDialogVisible"
      :warehouse-id="currentWarehouseId!"
      @success="handleImportSuccess"
    />

    <!-- 库位表单对话框 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="1200px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="所属仓库" prop="warehouseId">
          <el-input :value="currentWarehouseName" disabled />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="库位编码" prop="locationCode">
              <el-input v-model="form.locationCode" placeholder="请输入库位编码，如：A-01-01" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="库位名称" prop="locationName">
              <el-input v-model="form.locationName" placeholder="请输入库位名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="库位类型" prop="locationType">
              <el-select v-model="form.locationType" placeholder="请选择" style="width: 100%">
                <el-option label="普通库位" value="normal" />
                <el-option label="冷冻库位" value="frozen" />
                <el-option label="易燃库位" value="flammable" />
                <el-option label="贵重库位" value="valuable" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序" prop="sortOrder">
              <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="容量" prop="capacity">
          <el-input-number v-model="form.capacity" :min="0" :precision="2" style="width: 100%" />
          <div class="form-tip">容量单位与物料基本单位一致</div>
        </el-form-item>

        <el-divider content-position="left">尺寸信息（cm）</el-divider>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="宽度" prop="width">
              <el-input-number v-model="form.width" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="高度" prop="height">
              <el-input-number v-model="form.height" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="深度" prop="depth">
              <el-input-number v-model="form.depth" :min="0" :precision="2" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="1">正常</el-radio>
            <el-radio value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'WarehouseLocation',
})

import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Upload, Download } from '@element-plus/icons-vue'
import { warehouseApi } from '@/api/inventory/warehouse'
import { locationApi } from '@/api/inventory/location'
import { formatNumber } from '@/utils/format'
import type { InventoryStorageLocationQueryParams } from '@/types/inventory/location'
import { LocationEnum } from '@/enums/inventory'
import LocationImportDialog from '@/components/inventory/LocationImportDialog.vue'
const route = useRoute()
const router = useRouter()

// ==================== 查询参数 ====================
const queryParams = reactive<InventoryStorageLocationQueryParams>({
  pageNum: 1,
  pageSize: 10,
  warehouseId: undefined,
  locationCode: '',
  locationName: '',
  locationType: '',
  status: '',
})

// ==================== 响应式数据 ====================
const loading = ref(false)
const locationList = ref<any[]>([])
const total = ref(0)
const warehouseList = ref<any[]>([])
const ids = ref<number[]>([])
const single = ref(true)
const multiple = ref(true)
const dialogVisible = ref(false)
const importDialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()

// 当前选中的仓库
const currentWarehouseId = ref<number | null>(1)
const currentWarehouseName = ref('')

// 仓库容量统计
const warehouseCapacity = ref(0)
const warehouseUsedCapacity = ref(0)
const warehouseAvailableCapacity = computed(
  () => warehouseCapacity.value - warehouseUsedCapacity.value
)

// 表单数据
const form = reactive({
  locationId: undefined as number | undefined,
  warehouseId: 0 as number,
  locationCode: '',
  locationName: '',
  areaCode: '',
  shelfCode: '',
  levelCode: '',
  positionCode: '',
  locationType: 'normal',
  capacity: 0 as number,
  width: null as number | null,
  height: null as number | null,
  depth: null as number | null,
  sortOrder: 0,
  status: '1',
  remark: '',
})

// 表单验证规则
const rules = {
  locationCode: [
    { required: true, message: '请输入库位编码', trigger: 'blur' },
    { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' },
  ],
  locationName: [
    { required: true, message: '请输入库位名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' },
  ],
  capacity: [{ required: true, message: '请输入容量', trigger: 'blur' }],
}

const getCapacityRate = (row: any) => {
  if (!row.capacity || row.capacity === 0) return 0
  return Math.round((row.usedCapacity / row.capacity) * 100)
}

// ==================== 加载仓库列表 ====================
const loadWarehouseList = async () => {
  // Use getOptions to get all warehouses for dropdown
  const res = await warehouseApi.getOptions()
  warehouseList.value = res.data || []

  // 从 URL 参数获取仓库ID
  const warehouseIdParam = route.query.warehouseId

  if (warehouseIdParam) {
    currentWarehouseId.value = Number(warehouseIdParam)
    const warehouse = warehouseList.value.find((w) => w.warehouseId === currentWarehouseId.value)
    if (warehouse) {
      currentWarehouseName.value = warehouse.warehouseName
    }
  } else if (warehouseList.value.length > 0 && !currentWarehouseId.value) {
    // 默认选择第一个仓库
    currentWarehouseId.value = warehouseList.value[0].warehouseId
    currentWarehouseName.value = warehouseList.value[0].warehouseName
  } else {
    ElMessage.warning('请先添加仓库信息')
  }
}

// ==================== 加载仓库容量统计 ====================
const loadWarehouseCapacity = async () => {
  if (!currentWarehouseId.value) return
  // TODO: Implement getCapacity if the API endpoint exists
  // For now, set default values
  warehouseCapacity.value = 0
  warehouseUsedCapacity.value = 0
}

// ==================== API 请求 ====================
const getList = async () => {
  if (!currentWarehouseId.value) return
  loading.value = true
  try {
    const params = {
      ...queryParams,
      warehouseId: currentWarehouseId.value,
    }
    const res = await locationApi.list(params)
    locationList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

// ==================== 事件处理 ====================
const handleWarehouseChange = () => {
  queryParams.pageNum = 1
  const warehouse = warehouseList.value.find((w) => w.warehouseId === currentWarehouseId.value)
  currentWarehouseName.value = warehouse?.warehouseName || ''
  loadWarehouseCapacity()
  getList()
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const handleReset = () => {
  queryParams.locationCode = ''
  queryParams.locationName = ''
  queryParams.locationType = ''
  queryParams.status = ''
  getList()
}

const handleSelectionChange = (selection: any[]) => {
  ids.value = selection.map((item) => item.locationId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

const handleAdd = () => {
  if (!currentWarehouseId.value) {
    ElMessage.warning('请先选择仓库')
    return
  }
  dialogTitle.value = '新增库位'
  resetForm()
  form.warehouseId = currentWarehouseId.value
  dialogVisible.value = true
}

const handleEdit = (row?: any) => {
  dialogTitle.value = '编辑库位'
  resetForm()
  if (row) {
    Object.assign(form, row)
  } else if (ids.value.length === 1) {
    getLocationInfo(ids.value[0])
  }
  dialogVisible.value = true
}

const getLocationInfo = async (id: number) => {
  const res = await locationApi.getInfo(id)
  if (res.data) {
    Object.assign(form, res.data)
  }
}

const handleDelete = (row?: any) => {
  ElMessageBox.confirm(
    row ? `确认删除库位「${row.locationName}」吗？` : '确认删除选中的库位吗？',
    '提示',
    { type: 'warning' }
  )
    .then(() => locationApi.delete(row.locationId))
    .then(() => {
      ElMessage.success('删除成功')
      getList()
      loadWarehouseCapacity()
    })
    .catch(() => {})
}

const handleStatusChange = async (row: any) => {
  await locationApi.updateStatus(row.locationId, row.status)
  ElMessage.success('状态更新成功')
}

const handleImport = () => {
  if (!currentWarehouseId.value) {
    ElMessage.warning('请先选择仓库')
    return
  }
  importDialogVisible.value = true
}

const handleImportSuccess = () => {
  getList()
  loadWarehouseCapacity()
  ElMessage.success('库位导入成功')
}

const handleExport = async () => {
  if (!currentWarehouseId.value) return
  await locationApi.export({
    ...queryParams,
    warehouseId: currentWarehouseId.value,
  })
  ElMessage.success('导出成功')
}

const submitForm = () => {
  formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      if (form.locationId) {
        await locationApi.update(form)
        ElMessage.success('修改成功')
      } else {
        await locationApi.add(form)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      getList()
      loadWarehouseCapacity()
    }
  })
}

const resetForm = () => {
  form.locationId = undefined
  form.warehouseId = currentWarehouseId.value || 0
  form.locationCode = ''
  form.locationName = ''
  form.areaCode = ''
  form.shelfCode = ''
  form.levelCode = ''
  form.positionCode = ''
  form.locationType = 'normal'
  form.capacity = 0
  form.width = null
  form.height = null
  form.depth = null
  form.sortOrder = 0
  form.status = '0'
  form.remark = ''
}

const goBack = () => {
  router.push('/inventory/warehouse')
}

// ==================== 生命周期 ====================
onMounted(async () => {
  await loadWarehouseList()
  if (currentWarehouseId.value) {
    await loadWarehouseCapacity()
    await getList()
  }
})
</script>

<style scoped lang="scss">
.location-page {
  padding: 20px;

  .search-card,
  .warehouse-card,
  .operation-card,
  .table-card {
    margin-bottom: 16px;
  }

  .page-title {
    font-size: 18px;
    font-weight: 500;
  }

  .warehouse-name {
    font-size: 14px;
    color: #909399;
    margin-left: 12px;
  }

  .warehouse-selector {
    display: flex;
    align-items: center;
    gap: 16px;
    flex-wrap: wrap;

    .label {
      font-size: 14px;
      color: #606266;
    }

    .capacity-info {
      font-size: 14px;
    }
  }

  .capacity-warning {
    color: #e6a23c;
    font-weight: bold;
  }

  .form-tip {
    font-size: 12px;
    color: #909399;
    margin-top: 4px;
  }
}
</style>
