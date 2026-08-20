<template>
  <div class="warehouse-page">
    <!-- 搜索栏 -->
    <SearchForm
      v-model="queryParams"
      :fields="searchFields"
      @search="handleQuery"
      @reset="handleReset"
    />

    <!-- 工具栏 -->
    <Toolbar
      :buttons="toolbarButtons"
      :selected-count="ids.length"
      :show-batch-bar="true"
      @click="handleToolbarClick"
      @refresh="getList"
    >
      <template #batch-actions>
        <el-button
          type="danger"
          size="small"
          @click="() => handleDelete()"
          v-hasPermi="['inventory:warehouse:remove']"
        >
          批量删除
        </el-button>
        <el-button type="info" size="small" @click="handleLocationManage"> 库位管理 </el-button>
      </template>
    </Toolbar>

    <!-- 表格 -->
    <el-card class="table-card">
      <el-table
        v-loading="loading"
        :data="warehouseList"
        @selection-change="handleSelectionChange"
        border
        stripe
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="仓库编码" prop="warehouseCode" width="120" />
        <el-table-column label="仓库名称" prop="warehouseName" width="180" />
        <el-table-column label="仓库类型" prop="warehouseType" width="100" align="center">
          <template #default="{ row }"> </template>
        </el-table-column>
        <el-table-column label="仓库位置" prop="location" min-width="150" show-overflow-tooltip />
        <el-table-column label="负责人" prop="manager" width="100" />
        <el-table-column label="联系电话" prop="contactPhone" width="120" />
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
        <el-table-column label="创建时间" prop="createTime" width="160" />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" v-hasPermi="['inventory:warehouse:edit']" @click="handleEdit(row)">编辑</el-button>
            <el-button link type="primary" @click="handleLocation(row)">库位</el-button>
            <el-button link type="danger" v-hasPermi="['inventory:warehouse:delete']" @click="handleDelete(row)">删除</el-button>
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

    <!-- 仓库表单对话框 -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="600px" append-to-body>
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="仓库编码" prop="warehouseCode">
              <el-input v-model="form.warehouseCode" placeholder="请输入仓库编码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="仓库名称" prop="warehouseName">
              <el-input v-model="form.warehouseName" placeholder="请输入仓库名称" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="仓库类型" prop="warehouseType">
              <el-select v-model="form.warehouseType" placeholder="请选择" style="width: 100%">
                <el-option label="普通仓库" value="normal" />
                <el-option label="质检仓库" value="quality" />
                <el-option label="成品仓库" value="finished" />
                <el-option label="废品仓库" value="scrap" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="排序" prop="sortOrder">
              <el-input-number v-model="form.sortOrder" :min="0" style="width: 100%" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="仓库位置" prop="location">
          <el-input v-model="form.location" placeholder="请输入仓库位置，如：A栋1楼" />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="负责人" prop="manager">
              <el-input v-model="form.manager" placeholder="请输入负责人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="contactPhone">
              <el-input v-model="form.contactPhone" placeholder="请输入联系电话" />
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
  name: 'WarehouseList',
})

import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import SearchForm from '@/components/common-ui/SearchForm.vue'
import Toolbar from '@/components/common-ui/Toolbar.vue'
import type { SearchOptions, ToolbarOptions } from '@/components/common-ui/type'
import { warehouseApi } from '@/api/inventory/warehouse'
import { searchConfig } from './config'
import type { InventoryWarehouse, WarehouseUpdateDTO } from '@/types/inventory/warehouse'
const router = useRouter()
import { WarehouseEnum } from '@/enums/inventory'
// ==================== 配置 ====================

// 搜索表单配置
const searchFields: SearchOptions[] = searchConfig

// 工具栏配置
const toolbarButtons: ToolbarOptions[] = [
  {
    key: 'add',
    label: '新增仓库',
    type: 'primary',
    icon: 'Plus',
    permission: 'inventory:warehouse:add',
  },
  {
    key: 'export',
    label: '导出',
    type: 'warning',
    icon: 'Download',
    permission: 'inventory:warehouse:export',
  },
]

// ==================== 查询参数 ====================
const queryParams = reactive({
  current: 1,
  pageSize: 10,
  warehouseCode: '',
  warehouseName: '',
  warehouseType: '',
  status: '',
})

// ==================== 响应式数据 ====================
const loading = ref(false)
const warehouseList = ref<InventoryWarehouse[]>([])
const total = ref(0)
const ids = ref<number[]>([])
const single = ref(true)
const multiple = ref(true)
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formRef = ref()

// 表单数据
const form = reactive<WarehouseUpdateDTO>({
  warehouseId: undefined as number | undefined,
  warehouseCode: '',
  warehouseName: '',
  warehouseType: 'normal',
  location: '',
  manager: '',
  contactPhone: '',
  sortOrder: 0,
  status: '1',
  remark: '',
})

// 表单验证规则
const rules = {
  warehouseCode: [
    { required: true, message: '请输入仓库编码', trigger: 'blur' },
    { min: 2, max: 50, message: '长度在 2 到 50 个字符', trigger: 'blur' },
  ],
  warehouseName: [
    { required: true, message: '请输入仓库名称', trigger: 'blur' },
    { min: 2, max: 100, message: '长度在 2 到 100 个字符', trigger: 'blur' },
  ],
  warehouseType: [{ required: true, message: '请选择仓库类型', trigger: 'change' }],
}

// ==================== 辅助方法 ====================
// const getWarehouseTypeTag = warehouseTypeHelper.getTag
// const getWarehouseTypeLabel = warehouseTypeHelper.getLabel

// ==================== API 请求 ====================
const getList = async () => {
  loading.value = true
  try {
    const res = await warehouseApi.list(queryParams)
    warehouseList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

// ==================== 事件处理 ====================
const handleQuery = () => {
  queryParams.current = 1
  getList()
}

const handleReset = () => {
  queryParams.warehouseCode = ''
  queryParams.warehouseName = ''
  queryParams.warehouseType = ''
  queryParams.status = ''
  getList()
}

const handleSelectionChange = (selection: any[]) => {
  ids.value = selection.map((item) => item.warehouseId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

const handleAdd = () => {
  dialogTitle.value = '新增仓库'
  resetForm()
  dialogVisible.value = true
}

const handleEdit = (row?: any) => {
  dialogTitle.value = '编辑仓库'
  resetForm()
  if (row) {
    Object.assign(form, row)
  } else if (ids.value.length === 1) {
    getWarehouseInfo(ids.value[0])
  }
  dialogVisible.value = true
}

const getWarehouseInfo = async (id: number) => {
  const res = await warehouseApi.getInfo(id)
  if (res.data) {
    Object.assign(form, res.data)
  }
}

const handleDelete = (row?: any) => {
  const warehouseIds = row ? [row.warehouseId] : ids.value
  ElMessageBox.confirm(
    row ? `确认删除仓库「${row.warehouseName}」吗？` : '确认删除选中的仓库吗？',
    '提示',
    { type: 'warning' }
  )
    .then(() => warehouseApi.delete(warehouseIds))
    .then(() => {
      ElMessage.success('删除成功')
      getList()
    })
    .catch(() => {})
}

const handleStatusChange = async (row: any) => {
  await warehouseApi.updateStatus(row.warehouseId, row.status)
  ElMessage.success('状态更新成功')
}

const handleExport = async () => {
  await warehouseApi.export(queryParams)
  ElMessage.success('导出成功')
}

const handleLocation = (row: any) => {
  router.push(
    `/inventory/warehouse/location?warehouseId=${row.warehouseId}&warehouseName=${row.warehouseName}`
  )
}

const handleLocationManage = () => {
  router.push('/inventory/warehouse/location')
}

// 工具栏按钮点击事件
const handleToolbarClick = (key: string) => {
  if (key === 'add') handleAdd()
  if (key === 'export') handleExport()
}

const submitForm = () => {
  formRef.value.validate(async (valid: boolean) => {
    if (valid) {
      if (form.warehouseId) {
        // Create a proper update object with warehouseId as number
        // const updateData = {
        //   warehouseId: form.warehouseId,
        //   warehouseCode: form.warehouseCode,
        //   warehouseName: form.warehouseName,
        //   warehouseType: form.warehouseType,
        //   location: form.location,
        //   manager: form.manager,
        //   contactPhone: form.contactPhone,
        //   sortOrder: form.sortOrder,
        //   status: form.status,
        //   remark: form.remark,
        // }
        await warehouseApi.update(form)
        ElMessage.success('修改成功')
      } else {
        await warehouseApi.add(form)
        ElMessage.success('新增成功')
      }
      dialogVisible.value = false
      getList()
    }
  })
}

const resetForm = () => {
  form.warehouseId = undefined
  form.warehouseCode = ''
  form.warehouseName = ''
  form.warehouseType = 'normal'
  form.location = ''
  form.manager = ''
  form.contactPhone = ''
  form.sortOrder = 0
  form.status = '0'
  form.remark = ''
}

// ==================== 生命周期 ====================
onMounted(() => {
  getList()
})
</script>

<style scoped lang="scss">
.warehouse-page {
  padding: 20px;

  .search-card,
  .operation-card,
  .table-card {
    margin-bottom: 16px;
  }
}
</style>
