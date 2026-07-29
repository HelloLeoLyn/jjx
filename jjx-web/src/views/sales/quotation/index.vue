<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" :inline="true" label-width="80px">
        <el-form-item label="报价单号" prop="quotationNo">
          <el-input
            v-model="queryParams.quotationNo"
            placeholder="请输入报价单号"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="客户名称" prop="customerName">
          <el-input
            v-model="queryParams.customerName"
            placeholder="请输入客户名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="报价状态" prop="quotationStatus">
          <el-select
            v-model="queryParams.quotationStatus"
            placeholder="请选择报价状态"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="dict in quotationStatusOptions"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="报价日期">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
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
          <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" plain icon="Edit" :disabled="single" @click="handleUpdate"
            >修改</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button type="danger" plain icon="Delete" :disabled="multiple" @click="handleDelete"
            >删除</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="Download" @click="handleExport">导出</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="info" plain icon="Send" :disabled="single" @click="handleSend"
            >发送报价</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button type="success" plain icon="Switch" :disabled="single" @click="handleConvert"
            >转为订单</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="Collection" :disabled="single" @click="handleConvertToSample"
            >转为样品单</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="primary"
            plain
            icon="Document"
            :disabled="single"
            @click="handleExportPdf"
            >导出PDF</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button type="info" plain icon="CopyDocument" :disabled="single" @click="handleCopy"
            >复制报价</el-button
          >
        </el-col>
      </el-row>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="quotationList"
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="报价单号" align="center" prop="quotationNo" width="160" />
        <el-table-column label="客户名称" align="center" prop="customerName" width="180" />
        <el-table-column label="报价日期" align="center" prop="quotationDate" width="120">
          <template #default="scope">
            <span>{{ parseTime(scope.row.quotationDate, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="有效期至" prop="validUntil" width="120">
          <template #default="scope">
            <span v-if="scope.row.validUntil">{{
              parseTime(scope.row.validUntil, '{y}-{m}-{d}')
            }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="报价状态" prop="quotationStatus" width="100">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.quotationStatus)">
              {{ getStatusLabel(scope.row.quotationStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="币种" align="center" prop="currency" width="80" />
        <el-table-column label="总金额" align="center" prop="totalAmount" width="120">
          <template #default="scope">
            <span>{{ formatCurrency(scope.row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="销售员" align="center" prop="salesPersonName" width="100" />
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
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-button link type="info" icon="Connection" @click="showTrace(scope.row)">查看流水</el-button>
            <el-tooltip content="删除" placement="top">
              <el-button
                link
                type="danger"
                icon="Delete"
                @click="handleDelete(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="发送报价" placement="top">
              <el-button link type="info" icon="Send" @click="handleSend(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="转为订单" placement="top">
              <el-button
                link
                type="success"
                icon="Switch"
                @click="handleConvert(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="转为样品单" placement="top">
              <el-button
                link
                type="warning"
                icon="Collection"
                @click="handleConvertToSample(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="详情" placement="top">
              <el-button link type="warning" icon="View" @click="handleView(scope.row)"></el-button>
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

    <!-- 添加或修改报价单对话框 -->
    <el-dialog :title="title" v-model="open" width="1000px" append-to-body>
      <el-form ref="quotationFormRef" :model="form" :rules="rules" label-width="100px">
        <el-row>
          <el-col :span="12">
            <el-form-item label="报价单号" prop="quotationNo">
              <el-input
                v-model="form.quotationNo"
                placeholder="系统自动生成"
                maxlength="50"
                :readonly="true"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="客户" prop="customerId">
              <el-select
                v-model="form.customerId"
                placeholder="请选择客户"
                filterable
                remote
                :remote-method="searchCustomer"
                :loading="customerLoading"
                style="width: 100%"
              >
                <el-option
                  v-for="item in customerOptions"
                  :key="item.customerId"
                  :label="item.customerName"
                  :value="item.customerId"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="报价日期" prop="quotationDate">
              <el-date-picker
                v-model="form.quotationDate"
                type="date"
                placeholder="请选择报价日期"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="有效期至" prop="validUntil">
              <el-date-picker
                v-model="form.validUntil"
                type="date"
                placeholder="请选择有效期至"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="12">
            <el-form-item label="币种" prop="currency">
              <el-select v-model="form.currency" placeholder="请选择币种" style="width: 100%">
                <el-option
                  v-for="dict in currencyOptions"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="汇率" prop="exchangeRate">
              <el-input-number
                v-model="form.exchangeRate"
                :min="0"
                :precision="4"
                :step="0.0001"
                placeholder="请输入汇率"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 报价明细表格 -->
        <el-divider content-position="left">报价明细</el-divider>
        <el-table :data="form.items" border style="width: 100%; margin-bottom: 20px">
          <el-table-column label="序号" type="index" width="60" align="center" />
          <el-table-column label="产品编码" prop="productCode" width="120">
            <template #default="scope">
              <el-select
                v-model="scope.row.productCode"
                placeholder="请选择产品"
                filterable
                remote
                :remote-method="(query) => searchProduct(query, scope.row)"
                :loading="productLoading"
                style="width: 100%"
                @change="handleProductChange(scope.row)"
              >
                <el-option
                  v-for="item in productOptions"
                  :key="item.productCode"
                  :label="item.productCode"
                  :value="item.productCode"
                />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="产品名称" prop="productName" width="180">
            <template #default="scope">
              <el-input v-model="scope.row.productName" placeholder="产品名称" readonly />
            </template>
          </el-table-column>
          <el-table-column label="数量" prop="quantity" width="100">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.quantity"
                :min="1"
                :precision="0"
                @change="calculateItemAmount(scope.row)"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="单价" prop="unitPrice" width="120">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.unitPrice"
                :min="0"
                :precision="2"
                @change="calculateItemAmount(scope.row)"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="金额" prop="amount" width="120">
            <template #default="scope">
              <span>{{ formatCurrency(scope.row.amount) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80" align="center">
            <template #default="scope">
              <el-button
                link
                type="danger"
                icon="Delete"
                @click="removeItem(scope.$index)"
              ></el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-row>
          <el-col :span="24" style="text-align: right">
            <el-button type="primary" icon="Plus" @click="addItem">添加明细</el-button>
          </el-col>
        </el-row>

        <!-- 金额汇总 -->
        <el-divider content-position="left">金额汇总</el-divider>
        <el-row>
          <el-col :span="8">
            <el-form-item label="小计金额">
              <el-input v-model="form.subtotalAmount" readonly style="width: 100%">
                <template #append>元</template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="税率(%)">
              <el-input-number
                v-model="form.taxRate"
                :min="0"
                :max="100"
                :precision="2"
                @change="calculateTotalAmount"
                style="width: 100%"
              >
                <template #append>%</template>
              </el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="税额">
              <el-input v-model="form.taxAmount" readonly style="width: 100%">
                <template #append>元</template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row>
          <el-col :span="8">
            <el-form-item label="折扣金额">
              <el-input-number
                v-model="form.discountAmount"
                :min="0"
                :precision="2"
                @change="calculateTotalAmount"
                style="width: 100%"
              >
                <template #append>元</template>
              </el-input-number>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="总金额">
              <el-input v-model="form.totalAmount" readonly style="width: 100%">
                <template #append>元</template>
              </el-input>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="最终金额">
              <el-input v-model="form.finalAmount" readonly style="width: 100%">
                <template #append>元</template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row>
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

    <!-- 报价单详情对话框 -->
    <el-dialog title="报价单详情" v-model="detailOpen" width="1200px" append-to-body>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="报价单号">{{ detail.quotationNo }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ detail.customerName }}</el-descriptions-item>
        <el-descriptions-item label="报价日期">
          {{ parseTime(detail.quotationDate, '{y}-{m}-{d}') }}
        </el-descriptions-item>
        <el-descriptions-item label="有效期至">
          <span v-if="detail.validUntil">{{ parseTime(detail.validUntil, '{y}-{m}-{d}') }}</span>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="报价状态">
          <el-tag :type="getStatusTagType(detail.quotationStatus)">
            {{ getStatusLabel(detail.quotationStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="币种">
          {{ detail.currency || 'CNY' }}
        </el-descriptions-item>
        <el-descriptions-item label="汇率">
          {{ detail.exchangeRate || '1.0000' }}
        </el-descriptions-item>
        <el-descriptions-item label="小计金额">
          {{ formatCurrency(detail.subtotalAmount || 0) }}
        </el-descriptions-item>
        <el-descriptions-item label="税率"> {{ detail.taxRate || 0 }}% </el-descriptions-item>
        <el-descriptions-item label="税额">
          {{ formatCurrency(detail.taxAmount || 0) }}
        </el-descriptions-item>
        <el-descriptions-item label="折扣金额">
          {{ formatCurrency(detail.discountAmount || 0) }}
        </el-descriptions-item>
        <el-descriptions-item label="总金额">
          {{ formatCurrency(detail.totalAmount || 0) }}
        </el-descriptions-item>
        <el-descriptions-item label="最终金额">
          {{ formatCurrency(detail.finalAmount || 0) }}
        </el-descriptions-item>
        <el-descriptions-item label="销售员">
          {{ detail.salesPersonName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">
          {{ detail.remark || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 报价明细表格 -->
      <el-divider content-position="left">报价明细</el-divider>
      <el-table :data="detail.items" border style="width: 100%">
        <el-table-column label="序号" type="index" width="60" align="center" />
        <el-table-column label="产品编码" prop="productCode" width="120" />
        <el-table-column label="产品名称" prop="productName" width="180" />
        <el-table-column label="数量" prop="quantity" width="80" align="right" />
        <el-table-column label="单价" prop="unitPrice" width="100" align="right">
          <template #default="scope">
            {{ formatCurrency(scope.row.unitPrice) }}
          </template>
        </el-table-column>
        <el-table-column label="金额" prop="amount" width="120" align="right">
          <template #default="scope">
            {{ formatCurrency(scope.row.amount) }}
          </template>
        </el-table-column>
        <el-table-column label="单位" prop="unit" width="80" />
        <el-table-column label="交期(天)" prop="deliveryDays" width="100" />
        <el-table-column label="定制要求" prop="customRequirements" />
      </el-table>
    </el-dialog>
  </div>
  <TraceTimeline v-model="traceDrawerVisible" :traceId="currentTraceId" />

</template>

<script setup lang="ts">
defineOptions({
  name: 'Quotation',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import TraceTimeline from '@/components/TraceTimeline/index.vue'
import type { FormInstance, FormRules } from 'element-plus'
import { quotationApi } from '@/api/sales/quotation'
import { customerApi } from '@/api/sales/customer'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { parseTime, download, formatCurrency } from '@/utils/format'

// 查询参数
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  quotationNo: undefined as string | undefined,
  customerName: undefined as string | undefined,
  quotationStatus: undefined as string | undefined,
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined,
  orderByColumn: undefined as string | undefined,
  isAsc: undefined as 'asc' | 'desc' | undefined,
})

// 表单数据
const form = reactive({
  quotationId: undefined as number | undefined,
  quotationNo: '',
  customerId: undefined as number | undefined,
  customerName: '',
  quotationDate: '',
  validUntil: '',
  currency: 'CNY',
  exchangeRate: 1.0,
  subtotalAmount: 0,
  taxRate: 0,
  taxAmount: 0,
  totalAmount: 0,
  discountAmount: 0,
  finalAmount: 0,
  quotationStatus: 'draft',
  salesPersonId: undefined as number | undefined,
  salesPersonName: '',
  remark: '',
  items: [] as Array<{
    productCode: string
    productName: string
    quantity: number
    unitPrice: number
    amount: number
    unit: string
  }>,
})

// 详情数据
const detail = reactive({
  quotationId: undefined as number | undefined,
  quotationNo: '',
  customerId: undefined as number | undefined,
  customerName: '',
  quotationDate: '',
  validUntil: '',
  currency: 'CNY',
  exchangeRate: 1.0,
  subtotalAmount: 0,
  taxRate: 0,
  taxAmount: 0,
  totalAmount: 0,
  discountAmount: 0,
  finalAmount: 0,
  quotationStatus: 'draft',
  salesPersonId: undefined as number | undefined,
  salesPersonName: '',
  remark: '',
  items: [] as Array<{
    productCode: string
    productName: string
    quantity: number
    unitPrice: number
    amount: number
    unit: string
    deliveryDays?: number
    customRequirements?: string
  }>,
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
const dateRange = ref<string[]>([])
const customerLoading = ref(false)
const customerOptions = ref<Array<{ customerId: number; customerName: string }>>([])
const productLoading = ref(false)
const productOptions = ref<Array<{ productCode: string; productName: string }>>([])

// 表格数据
const quotationList = ref<any[]>([])

// 表单引用
const quotationFormRef = ref<FormInstance>()

// 字典选项
const quotationStatusOptions = ref([
  { value: 'draft', label: '草稿' },
  { value: 'sent', label: '已发送' },
  { value: 'accepted', label: '已接受' },
  { value: 'rejected', label: '已拒绝' },
  { value: 'expired', label: '已过期' },
])

const currencyOptions = ref([
  { value: 'CNY', label: '人民币' },
  { value: 'USD', label: '美元' },
  { value: 'EUR', label: '欧元' },
  { value: 'JPY', label: '日元' },
  { value: 'HKD', label: '港币' },
])

// 表单验证规则
const rules = reactive<FormRules>({
  customerId: [{ required: true, message: '请选择客户', trigger: 'change' }],
  quotationDate: [{ required: true, message: '请选择报价日期', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币种', trigger: 'change' }],
})

// 获取报价单列表
const getList = async () => {
  loading.value = true
  try {
    // 处理日期范围
    if (dateRange.value && dateRange.value.length === 2) {
      queryParams.startDate = dateRange.value[0]
      queryParams.endDate = dateRange.value[1]
    } else {
      queryParams.startDate = undefined
      queryParams.endDate = undefined
    }

    const response = await quotationApi.list(queryParams)
    quotationList.value = response.data ? response.data.records : []
    total.value = response.data ? response.data.total : 0
  } catch (error) {
    console.error('获取报价单列表失败:', error)
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
  dateRange.value = []
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    quotationNo: undefined,
    customerName: undefined,
    quotationStatus: undefined,
    startDate: undefined,
    endDate: undefined,
    orderByColumn: undefined,
    isAsc: undefined,
  })
  getList()
}

// 多选框选中数据
const handleSelectionChange = (selection: any[]) => {
  ids.value = selection.map((item) => item.quotationId)
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
  title.value = '新增报价单'
}

// 修改按钮操作
const handleUpdate = (row?: any) => {
  resetForm()
  const quotationId = row?.quotationId || ids.value[0]
  quotationApi.getInfo(quotationId).then((response: any) => {
    Object.assign(form, response.data)
    open.value = true
    title.value = '修改报价单'
  })
}

// 删除按钮操作
const handleDelete = (row?: any) => {
  const quotationIds = row?.quotationId || ids.value
  ElMessageBox.confirm('是否确认删除报价单号为"' + quotationIds + '"的数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      return quotationApi.remove(quotationIds)
    })
    .then(() => {
      getList()
      ElMessage.success('删除成功')
    })
    .catch(() => {})
}

// 导出按钮操作
const handleExport = () => {
  ElMessageBox.confirm('是否确认导出所有报价单数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      return quotationApi.export(queryParams)
    })
    .then((response: any) => {
      download(response, '报价单列表.xlsx')
    })
    .catch(() => {})
}

// 转为样品单按钮操作
const handleConvertToSample = async (row?: any) => {
  const quotationId = row?.quotationId || ids.value[0]
  if (!quotationId) return
  
  const { value } = await ElMessageBox.prompt('打样数量', '转为样品单', {
    inputValue: '10',
    confirmButtonText: '转为样品单',
  })
  const qty = parseInt(value || '0')
  if (qty <= 0) { ElMessage.warning('请输入有效数量'); return }
  
  try {
    await sampleOrderApi.createFromQuotation(quotationId, { sampleQty: qty })
    ElMessage.success('报价单已成功转为样品单')
    getList()
  } catch (e: any) { ElMessage.error(e.message || '转换失败') }
}

// 发送报价按钮操作
const handleSend = (row?: any) => {
  const quotationId = row?.quotationId || ids.value[0]
  ElMessageBox.confirm('是否确认发送报价单给客户？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
  })
    .then(() => {
      return quotationApi.send(quotationId)
    })
    .then(() => {
      getList()
      ElMessage.success('发送成功')
    })
    .catch(() => {})
}

// 转为订单按钮操作
const handleConvert = (row?: any) => {
  const quotationId = row?.quotationId || ids.value[0]
  ElMessageBox.confirm('是否确认将报价单转为销售订单？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
  })
    .then(() => {
      return quotationApi.convert(quotationId)
    })
    .then((response: any) => {
      ElMessage.success(`报价单已成功转为订单，订单号：${response.data.orderNo}`)
      getList()
    })
    .catch(() => {})
}

// 导出PDF按钮操作
const handleExportPdf = (row?: any) => {
  const quotationId = row?.quotationId || ids.value[0]
  quotationApi.exportPdf(quotationId).then((response: any) => {
    download(response, `报价单_${quotationId}.pdf`)
  })
}

// 复制报价按钮操作
const handleCopy = (row?: any) => {
  const quotationId = row?.quotationId || ids.value[0]
  quotationApi.copy(quotationId).then((response: any) => {
    Object.assign(form, response.data)
    form.quotationNo = `COPY_${form.quotationNo}`
    open.value = true
    title.value = '复制报价单'
    ElMessage.success('复制成功，请修改报价单号后保存')
  })
}

// 查看详情按钮操作
const handleView = (row: any) => {
  const quotationId = row.quotationId as number
  quotationApi.getInfo(quotationId).then((response: any) => {
    Object.assign(detail, response.data)
    detailOpen.value = true
  })
}

// 搜索客户
const searchCustomer = (query: string) => {
  if (query) {
    customerLoading.value = true
    (customerApi as any).list({ customerName: query, pageSize: 10 }).then((response: any) => {
      customerOptions.value = response.rows.map((item: any) => ({
        customerId: item.customerId,
        customerName: item.customerName,
      }))
      customerLoading.value = false
    })
  } else {
    customerOptions.value = []
  }
}

// 搜索产品
const searchProduct = (query: string, row: any) => {
  if (query) {
    productLoading.value = true
    // 这里应该调用产品API进行搜索
    // 暂时使用模拟数据
    setTimeout(() => {
      productOptions.value = [
        { productCode: 'P001', productName: '产品A' },
        { productCode: 'P002', productName: '产品B' },
        { productCode: 'P003', productName: '产品C' },
        { productCode: 'P004', productName: '产品D' },
        { productCode: 'P005', productName: '产品E' },
      ].filter((item) => item.productCode.includes(query) || item.productName.includes(query))
      productLoading.value = false
    }, 300)
  } else {
    productOptions.value = []
  }
}

// 处理产品选择变化
const handleProductChange = (item: any) => {
  // 根据选择的产品编码自动填充产品名称
  const selectedProduct = productOptions.value.find(
    (product) => product.productCode === item.productCode
  )
  if (selectedProduct) {
    item.productName = selectedProduct.productName
  } else if (item.productCode) {
    // 如果没有找到匹配的产品，使用默认名称
    item.productName = `产品_${item.productCode}`
  } else {
    item.productName = ''
  }
}

// 处理产品选择
const handleProductSelect = (item: any) => {
  // 这里可以添加产品搜索逻辑
  if (item.productCode && !item.productName) {
    item.productName = `产品_${item.productCode}`
  }
}

// 计算明细金额
const calculateItemAmount = (item: any) => {
  item.amount = (item.quantity || 0) * (item.unitPrice || 0)
  calculateTotalAmount()
}

// 计算总金额
const calculateTotalAmount = () => {
  // 计算小计金额
  form.subtotalAmount = form.items.reduce((sum, item) => sum + (item.amount || 0), 0)

  // 计算税额
  form.taxAmount = (form.subtotalAmount * (form.taxRate || 0)) / 100

  // 计算总金额
  form.totalAmount = form.subtotalAmount + form.taxAmount

  // 计算最终金额
  form.finalAmount = form.totalAmount - (form.discountAmount || 0)
}

// 添加明细
const addItem = () => {
  form.items.push({
    productCode: '',
    productName: '',
    quantity: 1,
    unitPrice: 0,
    amount: 0,
    unit: 'PCS',
  })
}

// 删除明细
const removeItem = (index: number) => {
  form.items.splice(index, 1)
  calculateTotalAmount()
}

// 表单重置
const resetForm = () => {
  if (quotationFormRef.value) {
    quotationFormRef.value.resetFields()
  }
  Object.assign(form, {
    quotationId: undefined,
    quotationNo: '',
    customerId: undefined,
    customerName: '',
    quotationDate: '',
    validUntil: '',
    currency: 'CNY',
    exchangeRate: 1.0,
    subtotalAmount: 0,
    taxRate: 0,
    taxAmount: 0,
    totalAmount: 0,
    discountAmount: 0,
    finalAmount: 0,
    quotationStatus: 'draft',
    salesPersonId: undefined,
    salesPersonName: '',
    remark: '',
    items: [],
  })
}

// 提交表单
const submitForm = () => {
  if (!quotationFormRef.value) return

  quotationFormRef.value.validate((valid) => {
    if (valid) {
      // 验证明细
      if (form.items.length === 0) {
        ElMessage.warning('请至少添加一条报价明细')
        return
      }

      // 验证明细数据
      for (const item of form.items) {
        if (!item.productCode || !item.productName) {
          ElMessage.warning('请填写完整的产品信息')
          return
        }
        if (item.quantity <= 0) {
          ElMessage.warning('数量必须大于0')
          return
        }
        if (item.unitPrice < 0) {
          ElMessage.warning('单价不能为负数')
          return
        }
      }

      if (form.quotationId !== undefined) {
        quotationApi.edit(form as any).then(() => {
          ElMessage.success('修改成功')
          open.value = false
          getList()
        })
      } else {
        quotationApi.add(form as any).then(() => {
          ElMessage.success('新增成功')
          open.value = false
          getList()
        })
      }
    }
  })
}

// 取消按钮
const cancel = () => {
  open.value = false
  resetForm()
}

// 获取状态标签类型
const getStatusTagType = (status: string) => {
  switch (status) {
    case 'draft':
      return 'info'
    case 'sent':
      return 'warning'
    case 'accepted':
      return 'success'
    case 'rejected':
      return 'danger'
    case 'expired':
      return 'info'
    default:
      return 'info'
  }
}

// 获取状态标签文本
const getStatusLabel = (status: string) => {
  const option = quotationStatusOptions.value.find((opt) => opt.value === status)
  return option ? option.label : '未知状态'
}

// 组件挂载时获取数据
onMounted(() => {
  getList()
})
// 链路追踪抽屉
const traceDrawerVisible = ref(false)
const currentTraceId = ref('')
function showTrace(row: any) {
  currentTraceId.value = row.traceId || ''
  traceDrawerVisible.value = true
}

</script>

<style scoped>
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
