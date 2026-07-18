<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" :inline="true" label-width="80px">
        <el-form-item label="订单号" prop="orderNo">
          <el-input
            v-model="queryParams.orderNo"
            placeholder="请输入订单号"
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
        <el-form-item label="订单状态" prop="orderStatus">
          <el-select
            v-model="queryParams.orderStatus"
            placeholder="请选择订单状态"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="dict in orderStatusOptions"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
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

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="orderList"
        @selection-change="handleSelectionChange"
        @sort-change="handleSortChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="订单号" align="center" prop="orderNo" width="160" />
        <el-table-column label="客户名称" align="center" prop="customerName" width="180" />
        <el-table-column label="订单日期" align="center" prop="orderDate" width="120">
          <template #default="scope">
            <span>{{ parseTime(scope.row.orderDate, '{y}-{m}-{d}') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="交货日期" prop="deliveryDate" width="120">
          <template #default="scope">
            <span v-if="scope.row.deliveryDate">{{
              parseTime(scope.row.deliveryDate, '{y}-{m}-{d}')
            }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="订单状态" prop="orderStatus" width="100">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.orderStatus)">
              {{ getStatusLabel(scope.row.orderStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="总金额" align="center" prop="totalAmount" width="120">
          <template #default="scope">
            <span>{{ formatCurrency(scope.row.totalAmount) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="生产状态" align="center" prop="productionStatus" width="100">
          <template #default="scope">
            <el-tag :type="getProductionStatusTagType(scope.row.productionStatus)">
              {{ getProductionStatusLabel(scope.row.productionStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发货状态" align="center" prop="deliveryStatus" width="100">
          <template #default="scope">
            <el-tag :type="getDeliveryStatusTagType(scope.row.deliveryStatus)">
              {{ getDeliveryStatusLabel(scope.row.deliveryStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          align="center"
          class-name="small-padding fixed-width"
          width="200"
        >
          <template #default="scope">
            <el-tooltip content="跟踪详情" placement="top">
              <el-button link type="primary" icon="View" @click="handleView(scope.row)"></el-button>
            </el-tooltip>
            <el-tooltip content="生产进度" placement="top">
              <el-button
                link
                type="success"
                icon="Operation"
                @click="handleProductionProgress(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="发货跟踪" placement="top">
              <el-button
                link
                type="warning"
                icon="Truck"
                @click="handleDeliveryTracking(scope.row)"
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

    <!-- 订单跟踪详情对话框 -->
    <el-dialog title="订单跟踪详情" v-model="detailOpen" width="1000px" append-to-body>
      <el-tabs v-model="activeTab">
        <el-tab-pane label="基本信息" name="basic">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
            <el-descriptions-item label="客户名称">{{ detail.customerName }}</el-descriptions-item>
            <el-descriptions-item label="订单日期">
              {{ parseTime(detail.orderDate, '{y}-{m}-{d}') }}
            </el-descriptions-item>
            <el-descriptions-item label="交货日期">
              <span v-if="detail.deliveryDate">{{
                parseTime(detail.deliveryDate, '{y}-{m}-{d}')
              }}</span>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="订单状态">
              <el-tag :type="getStatusTagType(detail.orderStatus)">
                {{ getStatusLabel(detail.orderStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="总金额">
              {{ formatCurrency(detail.totalAmount || 0) }}
            </el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">
              {{ detail.remark || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="生产进度" name="production">
          <el-timeline>
            <el-timeline-item
              v-for="(item, index) in productionProgress"
              :key="index"
              :timestamp="item.time"
              :type="item.type"
              :color="item.color"
            >
              {{ item.content }}
              <div v-if="item.operator" style="font-size: 12px; color: #999">
                操作人：{{ item.operator }}
              </div>
            </el-timeline-item>
          </el-timeline>
        </el-tab-pane>

        <el-tab-pane label="发货跟踪" name="delivery">
          <el-timeline>
            <el-timeline-item
              v-for="(item, index) in deliveryTracking"
              :key="index"
              :timestamp="item.time"
              :type="item.type"
              :color="item.color"
            >
              {{ item.content }}
              <div v-if="item.location" style="font-size: 12px; color: #999">
                位置：{{ item.location }}
              </div>
            </el-timeline-item>
          </el-timeline>
        </el-tab-pane>

        <el-tab-pane label="订单明细" name="items">
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
            <el-table-column label="生产状态" prop="productionStatus" width="100">
              <template #default="scope">
                <el-tag size="small" :type="getProductionStatusTagType(scope.row.productionStatus)">
                  {{ getProductionStatusLabel(scope.row.productionStatus) }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'SalesOrderTracking',
})

import { ref, reactive, onMounted } from 'vue'
import { parseTime, formatCurrency } from '@/utils/format'

// 查询参数
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  orderNo: undefined as string | undefined,
  customerName: undefined as string | undefined,
  orderStatus: undefined as string | undefined,
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined,
  orderByColumn: undefined as string | undefined,
  isAsc: undefined as 'asc' | 'desc' | undefined,
})

// 详情数据
const detail = reactive({
  orderId: undefined as number | undefined,
  orderNo: '',
  customerId: undefined as number | undefined,
  customerName: '',
  orderDate: '',
  deliveryDate: '',
  orderStatus: 'draft',
  totalAmount: 0,
  remark: '',
  items: [] as Array<{
    productCode: string
    productName: string
    quantity: number
    unitPrice: number
    amount: number
    productionStatus: string
  }>,
})

// 响应式数据
const loading = ref(false)
const total = ref(0)
const dateRange = ref<string[]>([])
const detailOpen = ref(false)
const activeTab = ref('basic')

// 表格数据
const orderList = ref<any[]>([])

// 生产进度数据
const productionProgress = ref([
  {
    time: '2026-03-20 09:00',
    content: '订单已确认，等待生产计划',
    type: 'primary' as const,
    color: '#409EFF',
    operator: '张三',
  },
  {
    time: '2026-03-21 14:30',
    content: '生产计划已下达',
    type: 'success' as const,
    color: '#67C23A',
    operator: '李四',
  },
  {
    time: '2026-03-22 10:15',
    content: '开始生产',
    type: 'info' as const,
    color: '#909399',
    operator: '王五',
  },
  {
    time: '2026-03-23 16:45',
    content: '生产完成50%',
    type: 'warning' as const,
    color: '#E6A23C',
    operator: '赵六',
  },
])

// 发货跟踪数据
const deliveryTracking = ref([
  {
    time: '2026-03-23 09:00',
    content: '订单已打包完成',
    type: 'primary' as const,
    color: '#409EFF',
    location: '仓库A区',
  },
  {
    time: '2026-03-23 14:30',
    content: '已发货',
    type: 'success' as const,
    color: '#67C23A',
    location: '物流中心',
  },
  {
    time: '2026-03-24 10:15',
    content: '运输中',
    type: 'info' as const,
    color: '#909399',
    location: '途中',
  },
])

// 字典选项
const orderStatusOptions = ref([
  { value: 'draft', label: '草稿' },
  { value: 'confirmed', label: '已确认' },
  { value: 'in_production', label: '生产中' },
  { value: 'completed', label: '已完成' },
  { value: 'shipped', label: '已发货' },
  { value: 'delivered', label: '已送达' },
  { value: 'cancelled', label: '已取消' },
])

const productionStatusOptions = ref([
  { value: 'not_started', label: '未开始' },
  { value: 'planned', label: '已计划' },
  { value: 'in_progress', label: '进行中' },
  { value: 'completed', label: '已完成' },
  { value: 'delayed', label: '已延迟' },
])

const deliveryStatusOptions = ref([
  { value: 'not_shipped', label: '未发货' },
  { value: 'packed', label: '已打包' },
  { value: 'shipped', label: '已发货' },
  { value: 'in_transit', label: '运输中' },
  { value: 'delivered', label: '已送达' },
])

// 获取订单列表
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

    // 模拟API调用
    await new Promise((resolve) => setTimeout(resolve, 500))

    // 模拟数据
    orderList.value = [
      {
        orderId: 1,
        orderNo: 'SO20260001',
        customerName: '测试客户A',
        orderDate: '2026-03-20',
        deliveryDate: '2026-03-30',
        orderStatus: 'in_production',
        totalAmount: 15000.0,
        productionStatus: 'in_progress',
        deliveryStatus: 'not_shipped',
      },
      {
        orderId: 2,
        orderNo: 'SO20260002',
        customerName: '测试客户B',
        orderDate: '2026-03-21',
        deliveryDate: '2026-03-31',
        orderStatus: 'confirmed',
        totalAmount: 8500.0,
        productionStatus: 'planned',
        deliveryStatus: 'not_shipped',
      },
      {
        orderId: 3,
        orderNo: 'SO20260003',
        customerName: '测试客户C',
        orderDate: '2026-03-22',
        deliveryDate: '2026-04-01',
        orderStatus: 'shipped',
        totalAmount: 22000.0,
        productionStatus: 'completed',
        deliveryStatus: 'in_transit',
      },
    ]

    total.value = orderList.value.length
  } catch (error) {
    console.error('获取订单列表失败:', error)
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
    orderNo: undefined,
    customerName: undefined,
    orderStatus: undefined,
    startDate: undefined,
    endDate: undefined,
    orderByColumn: undefined,
    isAsc: undefined,
  })
  getList()
}

// 多选框选中数据
const handleSelectionChange = (selection: any[]) => {
  // 可以在这里处理选中的数据
  console.log('选中数据:', selection)
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

// 查看详情
const handleView = (row: any) => {
  Object.assign(detail, {
    orderId: row.orderId,
    orderNo: row.orderNo,
    customerName: row.customerName,
    orderDate: row.orderDate,
    deliveryDate: row.deliveryDate,
    orderStatus: row.orderStatus,
    totalAmount: row.totalAmount,
    remark: row.remark || '',
    items: [
      {
        productCode: 'P001',
        productName: '产品A',
        quantity: 10,
        unitPrice: 1500,
        amount: 15000,
        productionStatus: row.productionStatus,
      },
    ],
  })
  detailOpen.value = true
  activeTab.value = 'basic'
}

// 查看生产进度
const handleProductionProgress = (row: any) => {
  handleView(row)
  activeTab.value = 'production'
}

// 查看发货跟踪
const handleDeliveryTracking = (row: any) => {
  handleView(row)
  activeTab.value = 'delivery'
}

// 获取状态标签类型
const getStatusTagType = (status: string) => {
  switch (status) {
    case 'draft':
      return 'info'
    case 'confirmed':
      return 'primary'
    case 'in_production':
      return 'warning'
    case 'completed':
      return 'success'
    case 'shipped':
      return 'success'
    case 'delivered':
      return 'success'
    case 'cancelled':
      return 'danger'
    default:
      return 'info'
  }
}

// 获取状态标签文本
const getStatusLabel = (status: string) => {
  const option = orderStatusOptions.value.find((opt) => opt.value === status)
  return option ? option.label : '未知状态'
}

// 获取生产状态标签类型
const getProductionStatusTagType = (status: string) => {
  switch (status) {
    case 'not_started':
      return 'info'
    case 'planned':
      return 'primary'
    case 'in_progress':
      return 'warning'
    case 'completed':
      return 'success'
    case 'delayed':
      return 'danger'
    default:
      return 'info'
  }
}

// 获取生产状态标签文本
const getProductionStatusLabel = (status: string) => {
  const option = productionStatusOptions.value.find((opt) => opt.value === status)
  return option ? option.label : '未知状态'
}

// 获取发货状态标签类型
const getDeliveryStatusTagType = (status: string) => {
  switch (status) {
    case 'not_shipped':
      return 'info'
    case 'packed':
      return 'primary'
    case 'shipped':
      return 'warning'
    case 'in_transit':
      return 'warning'
    case 'delivered':
      return 'success'
    default:
      return 'info'
  }
}

// 获取发货状态标签文本
const getDeliveryStatusLabel = (status: string) => {
  const option = deliveryStatusOptions.value.find((opt) => opt.value === status)
  return option ? option.label : '未知状态'
}

// 组件挂载时获取数据
onMounted(() => {
  getList()
})
</script>

<style scoped>
.search-card {
  margin-bottom: 16px;
}

.table-card {
  margin-bottom: 16px;
}
</style>
