<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" :inline="true" label-width="80px">
        <el-form-item label="实例编码" prop="instanceCode">
          <el-input
            v-model="queryParams.instanceCode"
            placeholder="请输入实例编码"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="产品编码" prop="productCode">
          <el-input
            v-model="queryParams.productCode"
            placeholder="请输入产品编码"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="产品名称" prop="productName">
          <el-input
            v-model="queryParams.productName"
            placeholder="请输入产品名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="实例状态" prop="instanceStatus">
          <el-select
            v-model="queryParams.instanceStatus"
            placeholder="请选择实例状态"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="dict in instanceStatusOptions"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
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
          <el-button type="primary" plain icon="Plus" v-hasPermi="['product:instance:edit']" @click="handleAdd">新增</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="success"
            plain
            icon="Edit"
            v-hasPermi="['product:instance:edit']"
            :disabled="single"
            @click="() => handleUpdate()"
            >修改</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="danger"
            plain
            icon="Delete"
            v-hasPermi="['product:instance:delete']"
            :disabled="multiple"
            @click="() => handleDelete()"
            >删除</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="Download" @click="handleExport">导出</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="info"
            plain
            icon="Check"
            v-hasPermi="['product:instance:edit']"
            :disabled="single"
            @click="() => handleActivate()"
            >激活</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="warning"
            plain
            icon="Close"
            v-hasPermi="['product:instance:edit']"
            :disabled="single"
            @click="() => handleDeactivate()"
            >停用</el-button
          >
        </el-col>
      </el-row>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="instanceList"
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="实例编码" align="center" prop="instanceCode" width="160" />
        <el-table-column label="产品编码" align="center" prop="productCode" width="160" />
        <el-table-column label="产品名称" align="center" prop="productName" width="180" />
        <el-table-column label="序列号" align="center" prop="serialNumber" width="160" />
        <el-table-column label="实例状态" prop="instanceStatus" width="100">
          <template #default="scope">
            <el-tag :type="getInstanceStatusTagType(scope.row.instanceStatus)">
              {{ getInstanceStatusLabel(scope.row.instanceStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生产日期" align="center" prop="productionDate" width="120">
          <template #default="scope">
            <span>{{ parseTime(scope.row.productionDate, 'yyyy-MM-dd') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="出厂日期" align="center" prop="shipmentDate" width="120">
          <template #default="scope">
            <span>{{ parseTime(scope.row.shipmentDate, 'yyyy-MM-dd') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          align="center"
          class-name="small-padding fixed-width"
          width="250"
        >
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button
                link
                type="primary"
                icon="Edit"
                v-hasPermi="['product:instance:edit']"
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button
                link
                type="danger"
                icon="Delete"
                v-hasPermi="['product:instance:delete']"
                @click="handleDelete(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="详情" placement="top">
              <el-button link type="info" icon="View" @click="handleView(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="跟踪" placement="top">
              <el-button
                link
                type="success"
                icon="Location"
                @click="handleTrack(scope.row)"
              ></el-button>
            </el-tooltip>
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

    <!-- 添加或修改实例对话框 -->
    <el-dialog :title="title" v-model="open" width="800px" append-to-body>
      <el-form ref="instanceFormRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="实例编码" prop="instanceCode">
              <el-input
                v-model="form.instanceCode"
                placeholder="系统自动生成"
                maxlength="50"
                :readonly="true"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="产品" prop="productId">
              <el-select
                v-model="form.productId"
                placeholder="请选择产品"
                filterable
                remote
                :remote-method="searchProduct"
                :loading="productLoading"
                style="width: 100%"
                @change="handleProductChange"
              >
                <el-option
                  v-for="item in productOptions"
                  :key="item.productId"
                  :label="item.productName"
                  :value="item.productId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="序列号" prop="serialNumber">
              <el-input v-model="form.serialNumber" placeholder="请输入序列号" maxlength="100" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="实例状态" prop="instanceStatus">
              <el-select
                v-model="form.instanceStatus"
                placeholder="请选择实例状态"
                style="width: 100%"
              >
                <el-option
                  v-for="dict in instanceStatusOptions"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="生产日期" prop="productionDate">
              <el-date-picker
                v-model="form.productionDate"
                type="date"
                placeholder="请选择生产日期"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="出厂日期" prop="shipmentDate">
              <el-date-picker
                v-model="form.shipmentDate"
                type="date"
                placeholder="请选择出厂日期"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="备注" prop="remark">
              <el-input
                v-model="form.remark"
                type="textarea"
                placeholder="请输入备注"
                :rows="3"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 实例详情对话框 -->
    <el-dialog title="产品实例详情" v-model="detailOpen" width="800px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="实例编码">{{ detail.instanceCode }}</el-descriptions-item>
        <el-descriptions-item label="产品编码">{{ detail.productCode }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{ detail.productName }}</el-descriptions-item>
        <el-descriptions-item label="序列号">{{ detail.serialNumber }}</el-descriptions-item>
        <el-descriptions-item label="实例状态">
          <el-tag :type="getInstanceStatusTagType(detail.instanceStatus)">
            {{ getInstanceStatusLabel(detail.instanceStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="生产日期">
          {{ parseTime(detail.productionDate, 'yyyy-MM-dd') }}
        </el-descriptions-item>
        <el-descriptions-item label="出厂日期">
          {{ parseTime(detail.shipmentDate, 'yyyy-MM-dd') }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">{{
          parseTime(detail.createTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{
          parseTime(detail.updateTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ detail.createBy }}</el-descriptions-item>
        <el-descriptions-item label="更新人">{{ detail.updateBy }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{
          detail.remark || '-'
        }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'ProductInstance',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { productInstanceApi } from '@/api/product'
import { parseTime } from '@/utils/format'

// 查询参数
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  instanceCode: undefined as string | undefined,
  productCode: undefined as string | undefined,
  productName: undefined as string | undefined,
  instanceStatus: undefined as number | undefined,
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined,
  orderByColumn: undefined as string | undefined,
  isAsc: undefined as 'asc' | 'desc' | undefined,
})

// 响应式数据
const loading = ref(false)
const showSearch = ref(true)
const ids = ref<number[]>([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')
const open = ref(false)
const detailOpen = ref(false)
const productLoading = ref(false)
const productOptions = ref<Array<{ productId: number; productCode: string; productName: string }>>(
  []
)

// 表单数据
const form = reactive({
  instanceId: undefined as number | undefined,
  instanceCode: '',
  productId: 0,
  productCode: '',
  productName: '',
  serialNumber: '',
  instanceStatus: 1,
  productionDate: '',
  shipmentDate: '',
  remark: '',
})

// 详情数据
const detail = reactive({
  instanceId: 0,
  instanceCode: '',
  productId: 0,
  productCode: '',
  productName: '',
  serialNumber: '',
  instanceStatus: undefined as number | undefined,
  productionDate: '',
  shipmentDate: '',
  remark: '',
  createTime: '',
  updateTime: '',
  createBy: '',
  updateBy: '',
})

// 表单引用
const instanceFormRef = ref<FormInstance>()

// 表单验证规则
const rules = reactive<FormRules>({
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  serialNumber: [{ required: true, message: '请输入序列号', trigger: 'blur' }],
  instanceStatus: [{ required: true, message: '请选择实例状态', trigger: 'change' }],
  productionDate: [{ required: true, message: '请选择生产日期', trigger: 'change' }],
})

// 表格数据
const instanceList = ref<any[]>([])

// 字典选项
const instanceStatusOptions = ref([
  { value: 1, label: '已创建' },
  { value: 2, label: '已计划' },
  { value: 3, label: '生产中' },
  { value: 4, label: '已暂停' },
  { value: 5, label: '已完成' },
  { value: 6, label: '已发货' },
  { value: 9, label: '已交付' },
  { value: 12, label: '维护中' },
  { value: 16, label: '已报废' },
  { value: 17, label: '已取消' },
])

// 获取实例列表
const getList = async () => {
  loading.value = true
  try {
    // 这里应该调用产品实例API
    const res = await productInstanceApi.listProductInstance(queryParams)
    instanceList.value = res.data?.records || []
    total.value = res.data?.total || 0
    loading.value = false
  } catch (error) {
    console.error('获取产品实例列表失败:', error)
    loading.value = false
  }
}

// 状态标签类型/文本
const getInstanceStatusTagType = (status: number | undefined) => {
  switch (status) {
    case 3:  // 生产中
      return 'warning'
    case 5:  // 已完成
      return 'success'
    case 16: // 已报废
      return 'danger'
    case 12: // 维护中
      return 'info'
    case 17: // 已取消
      return 'danger'
    default:
      return 'info'
  }
}

const getInstanceStatusLabel = (status: number | undefined) => {
  const option = instanceStatusOptions.value.find((opt) => opt.value === status)
  return option ? option.label : '未知'
}

// 搜索按钮操作
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置按钮操作
const resetQuery = () => {
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    instanceCode: undefined,
    productCode: undefined,
    productName: undefined,
    instanceStatus: undefined,
    startDate: undefined,
    endDate: undefined,
    orderByColumn: undefined,
    isAsc: undefined,
  })
  getList()
}

// 多选框选中数据
const handleSelectionChange = (selection: any[]) => {
  ids.value = selection.map((item) => item.instanceId)
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

// 新增按钮操作
const handleAdd = () => {
  resetForm()
  open.value = true
  title.value = '新增产品实例'
}

// 修改按钮操作
const handleUpdate = (row?: any) => {
  resetForm()
  const instanceId = row?.instanceId || ids.value[0]
  // 这里应该调用获取实例详情API
  // 暂时使用模拟数据
  setTimeout(() => {
    Object.assign(form, {
      instanceId: instanceId,
      instanceCode: 'INST001',
      productId: 1,
      productCode: 'P001',
      productName: '产品A',
      serialNumber: 'SN001',
      instanceStatus: 1,
      productionDate: '2024-01-15',
      shipmentDate: '2024-01-20',
      remark: '测试实例',
    })
    open.value = true
    title.value = '修改产品实例'
  }, 100)
}

// 删除按钮操作
const handleDelete = (row?: any) => {
  const instanceIds = row?.instanceId || ids.value[0]
  ElMessageBox.confirm('是否确认删除实例编码为"' + instanceIds + '"的数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      // 这里应该调用删除实例API
      return Promise.resolve()
    })
    .then(() => {
      getList()
      ElMessage.success('删除成功')
    })
    .catch(() => {})
}

// 导出按钮操作
const handleExport = () => {
  ElMessageBox.confirm('是否确认导出所有产品实例数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      // 这里应该调用导出实例API
      return Promise.resolve()
    })
    .then(() => {
      ElMessage.success('导出成功')
    })
    .catch(() => {})
}

// 激活按钮操作
const handleActivate = (row?: any) => {
  const instanceId = row?.instanceId || ids.value[0]
  ElMessageBox.confirm('是否确认激活产品实例？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
  })
    .then(() => {
      // 这里应该调用激活实例API
      return Promise.resolve()
    })
    .then(() => {
      getList()
      ElMessage.success('激活成功')
    })
    .catch(() => {})
}

// 停用按钮操作
const handleDeactivate = (row?: any) => {
  const instanceId = row?.instanceId || ids.value[0]
  ElMessageBox.confirm('是否确认停用产品实例？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      // 这里应该调用停用实例API
      return Promise.resolve()
    })
    .then(() => {
      getList()
      ElMessage.success('停用成功')
    })
    .catch(() => {})
}

// 查看详情按钮操作
const handleView = (row: any) => {
  const instanceId = row.instanceId
  // 这里应该调用获取实例详情API
  // 暂时使用模拟数据
  setTimeout(() => {
    Object.assign(detail, {
      instanceId: instanceId,
      instanceCode: 'INST001',
      productId: 1,
      productCode: 'P001',
      productName: '产品A',
      serialNumber: 'SN001',
      instanceStatus: 1,
      productionDate: '2024-01-15',
      shipmentDate: '2024-01-20',
      remark: '测试实例',
      createTime: '2024-01-15 10:30:00',
      updateTime: '2024-01-15 10:30:00',
      createBy: 'admin',
      updateBy: 'admin',
    })
    detailOpen.value = true
  }, 100)
}

// 跟踪按钮操作
const handleTrack = (row: any) => {
  const instanceId = row.instanceId
  ElMessageBox.alert(`跟踪产品实例 ${instanceId}`, '实例跟踪', {
    confirmButtonText: '确定',
  })
}

// 搜索产品
const searchProduct = (query: string) => {
  if (query) {
    productLoading.value = true
    // 这里应该调用产品搜索API
    setTimeout(() => {
      productOptions.value = [
        { productId: 1, productCode: 'P001', productName: '产品A' },
        { productId: 2, productCode: 'P002', productName: '产品B' },
        { productId: 3, productCode: 'P003', productName: '产品C' },
      ].filter((item) => item.productCode.includes(query) || item.productName.includes(query))
      productLoading.value = false
    }, 300)
  } else {
    productOptions.value = []
  }
}

// 处理产品选择变化
const handleProductChange = (productId: number) => {
  const selectedProduct = productOptions.value.find((item) => item.productId === productId)
  if (selectedProduct) {
    form.productCode = selectedProduct.productCode
    form.productName = selectedProduct.productName
  }
}

// 表单重置
const resetForm = () => {
  if (instanceFormRef.value) {
    instanceFormRef.value.resetFields()
  }
  Object.assign(form, {
    instanceId: undefined,
    instanceCode: '',
    productId: 0,
    productCode: '',
    productName: '',
    serialNumber: '',
    instanceStatus: 1,
    productionDate: '',
    shipmentDate: '',
    remark: '',
  })
}

// 提交表单
const submitForm = () => {
  if (!instanceFormRef.value) return

  instanceFormRef.value.validate((valid) => {
    if (valid) {
      if (form.instanceId !== undefined) {
        // 这里应该调用修改实例API
        setTimeout(() => {
          ElMessage.success('修改成功')
          open.value = false
          getList()
        }, 500)
      } else {
        // 这里应该调用新增实例API
        setTimeout(() => {
          ElMessage.success('新增成功')
          open.value = false
          getList()
        }, 500)
      }
    }
  })
}

// 取消按钮
const cancel = () => {
  open.value = false
  resetForm()
}

// 组件挂载时获取数据
onMounted(() => {
  getList()
})
</script>

<style scoped>
.search-card {
  margin-bottom: 20px;
}

.operation-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}
</style>
