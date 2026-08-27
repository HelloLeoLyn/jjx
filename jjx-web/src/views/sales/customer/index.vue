<template>
  <div class="app-container">
    <!-- 搜索容器 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" label-width="80px" inline>
        <el-form-item label="客户编码">
          <el-input v-model="queryParams.customerCode" placeholder="请输入客户编码" clearable />
        </el-form-item>
        <el-form-item label="客户名称">
          <el-input v-model="queryParams.customerName" placeholder="请输入客户名称" clearable />
        </el-form-item>
        <el-form-item label="客户类型">
          <el-select
            v-model="queryParams.customerType"
            placeholder="请选择客户类型"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="option in customerTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="客户状态">
          <el-select
            v-model="queryParams.customerStatus"
            placeholder="请选择客户状态"
            clearable
            style="width: 180px"
          >
            <el-option
              v-for="option in customerStatusOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
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
          <el-button type="primary" plain icon="Plus" v-hasPermi="['sales:customer:add']" @click="handleAdd">新增</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" plain icon="Edit" v-hasPermi="['sales:customer:edit']" :disabled="single" @click="handleUpdate"
            >修改</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain icon="Delete" v-hasPermi="['sales:customer:delete']" :disabled="multiple" @click="handleDelete"
            >删除</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="Download" v-hasPermi="['sales:customer:export']" @click="handleExport">导出</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="info" plain icon="Upload" v-hasPermi="['sales:customer:import']" @click="importDialogVisible = true">导入</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" plain icon="Check" v-hasPermi="['sales:customer:edit']" :disabled="multiple" @click="handleApprove"
            >批量审核</el-button
          >
        </el-col>
      </el-row>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="customerList"
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="客户编码" align="center" prop="customerCode" width="130" />
        <el-table-column label="客户名称" align="center" prop="customerName" width="180" />
        <el-table-column label="客户简称" align="center" prop="customerShortName" width="100" />
        <el-table-column label="客户类型" align="center" prop="customerType" width="100">
          <template #default="scope">
            <el-tag :type="CustomerTypeEnum.getTagProps(scope.row.customerType ?? 1).type">
              {{ CustomerTypeEnum.getLabel(scope.row.customerType ?? 1) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="客户等级" align="center" prop="customerLevel" width="100">
          <template #default="scope">
            <el-tag :type="CustomerLevelEnum.getTagProps(scope.row.customerLevel ?? 1).type">
              {{ CustomerLevelEnum.getLabel(scope.row.customerLevel ?? 1) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="客户状态" align="center" prop="customerStatus" width="100">
          <template #default="scope">
            <el-tag type="primary">
              {{ CustomerStatusEnum.getLabel(scope.row.customerStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="联系人" align="center" prop="contactPerson" width="100" />
        <el-table-column label="联系电话" align="center" prop="contactPhone" width="120" />
        <el-table-column label="信用额度" align="center" prop="creditLimit" width="120">
          <template #default="scope">
            <span>{{ formatCurrency(scope.row.creditLimit) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="已用额度" align="center" prop="usedCreditLimit" width="120">
          <template #default="scope">
            <span>{{ formatCurrency(scope.row.usedCreditLimit) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="VIP客户" align="center" prop="vip" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.vip ? 'success' : 'info'">
              {{ scope.row.vip ? '是' : '否' }}
            </el-tag>
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
          width="200"
        >
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button
                link
                type="primary"
                icon="Edit"
                v-hasPermi="['sales:customer:edit']"
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button
                link
                type="danger"
                icon="Delete"
                v-hasPermi="['sales:customer:delete']"
                @click="handleDelete(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="状态变更" placement="top">
              <el-button
                link
                type="warning"
                icon="Refresh"
                @click="handleChangeStatus(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="详情" placement="top">
              <el-button link type="info" icon="View" @click="handleView(scope.row)"></el-button>
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

    <!-- 添加或修改客户对话框 -->
    <CustomerFormDialog
      v-model:visible="open"
      :title="title"
      :form-data="form"
      @success="handleFormSuccess"
      @cancel="handleFormCancel"
    />

    <!-- 客户详情对话框 -->
    <el-dialog title="客户详情" v-model="detailOpen" width="900px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="客户编码">{{ detail.customerCode }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ detail.customerName }}</el-descriptions-item>
        <el-descriptions-item label="客户简称">{{ detail.customerShortName }}</el-descriptions-item>
        <el-descriptions-item label="客户类型">
          <el-tag :type="CustomerTypeEnum.getTagProps(detail.customerType ?? 1).type">
            {{ CustomerTypeEnum.getLabel(detail.customerType ?? 1) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="客户等级">
          <el-tag :type="CustomerLevelEnum.getTagProps(detail.customerLevel ?? 1).type">
            {{ CustomerLevelEnum.getLabel(detail.customerLevel ?? 1) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="客户状态">
          <el-tag type="info">
            {{ CustomerStatusEnum.getLabel(detail.customerStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="行业分类">{{ detail.industryCategory }}</el-descriptions-item>
        <el-descriptions-item label="客户来源">
          <el-tag>{{ getSourceLabel(detail.customerSource) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detail.contactPerson }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detail.contactPhone }}</el-descriptions-item>
        <el-descriptions-item label="联系邮箱">{{ detail.contactEmail }}</el-descriptions-item>
        <el-descriptions-item label="传真">{{ detail.fax }}</el-descriptions-item>
        <el-descriptions-item label="所在地区" :span="2">
          {{ [detail.country, detail.province, detail.city].filter(Boolean).join(' / ') || '-' }}
          <template v-if="detail.postalCode"><span class="addr-postal">（邮编 {{ detail.postalCode }}）</span></template>
        </el-descriptions-item>
        <el-descriptions-item label="详细地址" :span="2">{{ detail.address }}</el-descriptions-item>
        <el-descriptions-item label="信用额度">{{
          formatCurrency(detail.creditLimit)
        }}</el-descriptions-item>
        <el-descriptions-item label="已用额度">{{
          formatCurrency(detail.usedCreditLimit)
        }}</el-descriptions-item>
        <el-descriptions-item label="客户评分">
          <el-rate :model-value="detail.customerScore" disabled :max="5" show-score />
        </el-descriptions-item>
        <el-descriptions-item label="付款方式">
          <el-tag>{{ getPaymentMethodLabel(detail.paymentMethod) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="是否VIP">
          <el-tag :type="detail.vip ? 'success' : 'info'">
            {{ detail.vip ? '是' : '否' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail.remark }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{
          parseTime(detail.createTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{
          parseTime(detail.updateTime)
        }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
    <!-- 操作预览器 -->
    <OperationPreviewDialog
      v-model="previewVisible"
      :operation="previewOperation"
      :biz-id="previewBizId"
      :biz-no="previewBizNo"
      :status-text-map="customerStatusTextMap"
      @success="getList"
    />

    <!-- 通用导入弹窗（2026-08-13） -->
    <ExcelImportDialog
      :visible="importDialogVisible"
      @update:visible="importDialogVisible = $event"
      title="导入客户"
      :import-api="importCustomerFile"
      :template-api="customerApi.downloadCustomerTemplate"
      template-name="客户导入模板.xlsx"
      @success="getList"
    />
  </div>

</template>

<script setup lang="ts">
defineOptions({
  name: 'Customer',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, ElLoading } from 'element-plus'
import { customerApi } from '@/api/sales/customer'
import ExcelImportDialog from '@/components/ExcelImportDialog/index.vue'
import OperationPreviewDialog from '@/components/OperationPreviewDialog/index.vue'
import { getOperation } from '@/components/OperationPreviewDialog/registry'
import { parseTime, download } from '@/utils/format'
import CustomerFormDialog from './components/CustomerFormDialog.vue'
import { CustomerTypeEnum, CustomerLevelEnum, CustomerStatusEnum } from '@/enums/sales/CustomerEnum'
import { useCustomerOptions } from './composables/useCustomerOptions'
import type {
  CustomerQueryParams,
  CustomerFormData,
  CustomerItem,
  CustomerDetail,
} from '@/types/sales/customer'

// 查询参数
const queryParams = reactive<CustomerQueryParams>({
  pageNum: 1,
  pageSize: 10,
  customerCode: undefined,
  customerName: undefined,
  customerType: undefined,
  customerStatus: undefined,
  orderByColumn: undefined,
  isAsc: undefined,
})

// 表单数据
const form = reactive<CustomerFormData>({
  customerId: undefined,
  customerCode: '',
  customerName: '',
  customerShortName: '',
  customerType: undefined,
  customerLevel: undefined,
  customerStatus: undefined,
  industryCategory: '',
  customerSource: undefined,
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  fax: '',
  country: '',
  province: '',
  city: '',
  address: '',
  postalCode: '',
  creditLimit: 0,
  usedCreditLimit: 0,
  customerScore: 3,
  paymentMethod: undefined,
  vip: false,
  remark: '',
})

// 详情数据
const detail = reactive<CustomerDetail>({
  customerId: 0,
  customerCode: '',
  customerName: '',
  customerShortName: '',
  customerType: undefined,
  customerLevel: undefined,
  customerStatus: 1,
  industryCategory: '',
  customerSource: undefined,
  contactPerson: '',
  contactPhone: '',
  contactEmail: '',
  fax: '',
  country: '',
  province: '',
  city: '',
  address: '',
  postalCode: '',
  creditLimit: 0,
  usedCreditLimit: 0,
  customerScore: 3,
  paymentMethod: undefined,
  vip: false,
  remark: '',
  createTime: '',
  updateTime: '',
})

// 响应式数据
const loading = ref(false)
const ids = ref<number[]>([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')
const open = ref(false)
const detailOpen = ref(false)

// 表格数据
const customerList = ref<CustomerItem[]>([])

// 字典选项
const {
  customerTypeOptions,
  customerLevelOptions,
  customerStatusOptions,
  customerSourceOptions,
  paymentMethodOptions,
  getSourceLabel,
  getPaymentMethodLabel,
} = useCustomerOptions()

// 获取客户列表
const getList = async () => {
  loading.value = true
  try {
    const response = await customerApi.getCustomers(queryParams)
    customerList.value = response.data?.records || []
    total.value = response.data?.total || 0
  } catch (error) {
    console.error('获取客户列表失败:', error)
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
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    customerCode: undefined,
    customerName: undefined,
    customerType: undefined,
    customerStatus: undefined,
    orderByColumn: undefined,
    isAsc: undefined,
  })
  getList()
}

// 多选框选中数据
const handleSelectionChange = (selection: CustomerItem[]) => {
  ids.value = selection.map((item) => item.customerId)
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
const handleAdd = async () => {
  resetForm()
  form.customerCode = '系统自动生成'
  open.value = true
  title.value = '新增客户'
}

// 修改按钮操作
const handleUpdate = (row?: CustomerItem | MouseEvent) => {
  resetForm()
  const customerId = (row as CustomerItem)?.customerId || ids.value[0]
  if (!customerId) return
  customerApi.getCustomer(customerId).then((response) => {
    Object.assign(form, response.data)
    open.value = true
    title.value = '修改客户'
  })
}

// 删除按钮操作
const handleDelete = (row?: CustomerItem | MouseEvent) => {
  const customerIds = (row as CustomerItem)?.customerId || ids.value
  ElMessageBox.confirm('是否确认删除客户编号为"' + customerIds + '"的数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      return customerApi.deleteCustomers(customerIds)
    })
    .then(() => {
      getList()
      ElMessage.success('删除成功')
    })
    .catch(() => {})
}

// 导出按钮操作
const handleExport = () => {
  ElMessageBox.confirm('是否确认导出所有客户数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      const loading = ElLoading.service({ text: '导出中...', lock: true })
      return customerApi
        .exportCustomers(queryParams)
        .then((response: any) => {
          download(response, '客户列表.xlsx')
        })
        .finally(() => loading.close())
    })
    .catch(() => {})
}

// 导入（2026-08-13 通用 ExcelImportDialog 组件）
const importDialogVisible = ref(false)
const importCustomerFile = (file: File) => {
  const formData = new FormData()
  formData.append('file', file)
  return customerApi.importCustomers(formData)
}

// 批量审核按钮操作
const handleApprove = () => {
  const customerIds = ids.value
  if (!customerIds.length) {
    ElMessage.warning('请选择要审核的客户')
    return
  }

  ElMessageBox.confirm('是否确认审核选中的客户？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      return customerApi.approveCustomers(customerIds)
    })
    .then(() => {
      getList()
      ElMessage.success('审核成功')
    })
    .catch(() => {})
}

// 状态变更按钮操作（操作预览器：目标状态选择器 + 事件预告）
const previewVisible = ref(false)
const previewOperation = ref<any>(null)
const previewBizId = ref<number | null>(null)
const previewBizNo = ref('')
const customerStatusTextMap: Record<number, string> = { 1: '潜在客户', 2: '正式客户', 3: '暂停合作', 4: '终止合作' }
const handleChangeStatus = (row: CustomerItem | MouseEvent) => {
  if (row instanceof MouseEvent) return
  if (!row.customerId) return
  const op = getOperation('customer.changeStatus')
  if (!op) return
  previewOperation.value = op
  previewBizId.value = row.customerId
  previewBizNo.value = row.customerName || ''
  previewVisible.value = true
}

// 查看详情按钮操作
const handleView = (row: CustomerItem | MouseEvent) => {
  if (row instanceof MouseEvent) return
  const customerId = row.customerId
  customerApi.getCustomer(customerId).then((response) => {
    Object.assign(detail, response.data)
    detailOpen.value = true
  })
}

// 表单重置
const resetForm = () => {
  Object.assign(form, {
    customerId: undefined,
    customerCode: '',
    customerName: '',
    customerShortName: '',
    customerType: undefined,
    customerLevel: undefined,
    customerStatus: undefined,
    industryCategory: '',
    customerSource: undefined,
    contactPerson: '',
    contactPhone: '',
    contactEmail: '',
    fax: '',
    country: '',
    province: '',
    city: '',
    address: '',
    postalCode: '',
    creditLimit: 0,
    usedCreditLimit: 0,
    customerScore: 3,
    paymentMethod: undefined,
    vip: false,
    remark: '',
  })
}

// 处理表单提交成功
const handleFormSuccess = () => {
  open.value = false
  getList()
}

// 处理表单取消
const handleFormCancel = () => {
  open.value = false
  resetForm()
}

// 格式化货币
const formatCurrency = (value?: number) => {
  if (value === undefined || value === null) return '0.00'
  return value.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

// 组件挂载时获取数据
onMounted(() => {
  getList()
})
</script>

<style scoped>
.addr-postal {
  color: #909399;
  font-size: 12px;
  margin-left: 4px;
}
.search-card {
  margin-bottom: 16px;
}

.operation-card {
  margin-bottom: 16px;
}

.table-card {
  margin-bottom: 16px;
}

.dialog-footer {
  text-align: right;
}
</style>
