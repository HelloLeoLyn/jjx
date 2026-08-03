<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryFormRef" :inline="true" label-width="80px">
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
              v-for="item in orderStatusOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
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
            <span>{{ parseTime(scope.row.orderDate, 'yyyy-MM-dd') }}</span>
          </template>
        </el-table-column>
        <el-table-column label="交货日期" prop="deliveryDate" width="120">
          <template #default="scope">
            <span v-if="scope.row.deliveryDate">{{
              parseTime(scope.row.deliveryDate, 'yyyy-MM-dd')
            }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="订单状态" prop="orderStatusDesc" width="140">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.orderStatus)">
              {{ scope.row.orderStatusDesc || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="总金额" align="center" prop="finalAmount" width="130">
          <template #default="scope">
            <span>{{ formatCurrency(scope.row.finalAmount || 0) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="生产状态" align="center" prop="prodStatusDesc" width="100">
          <template #default="scope">
            <el-tag :type="getProdStatusTagType(scope.row.prodStatus)">
              {{ scope.row.prodStatusDesc || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="发货状态" align="center" width="100">
          <template #default="scope">
            <el-tag :type="getDeliveryStatusTagType(scope.row)">
              {{ getDeliveryStatusLabel(scope.row) }}
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
              {{ parseTime(detail.orderDate, 'yyyy-MM-dd') }}
            </el-descriptions-item>
            <el-descriptions-item label="交货日期">
              <span v-if="detail.deliveryDate">{{
                parseTime(detail.deliveryDate, 'yyyy-MM-dd')
              }}</span>
              <span v-else>-</span>
            </el-descriptions-item>
            <el-descriptions-item label="订单状态">
              <el-tag :type="getStatusTagType(detail.orderStatus)">
                {{ detail.orderStatusDesc || '未知' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="总金额">
              {{ formatCurrency(detail.finalAmount || 0) }}
            </el-descriptions-item>
            <el-descriptions-item label="生产状态">
              <el-tag :type="getProdStatusTagType(detail.prodStatus)">
                {{ detail.prodStatusDesc || '未知' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="销售负责人">
              {{ detail.salesManagerName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="交货地址" :span="2">
              {{ detail.deliveryAddress || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="备注" :span="2">
              {{ detail.remark || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-tab-pane>

        <el-tab-pane label="生产进度" name="production">
          <div v-if="productionLoading" style="text-align: center; padding: 40px">加载中...</div>
          <template v-else-if="productionOrders.length > 0">
            <el-table :data="productionOrders" border style="width: 100%; margin-bottom: 16px">
              <el-table-column label="生产单号" prop="orderNo" width="160" />
              <el-table-column label="产品名称" prop="productName" width="160" />
              <el-table-column label="计划数量" prop="plannedQuantity" width="90" align="right" />
              <el-table-column label="完成数量" prop="completedQuantity" width="90" align="right" />
              <el-table-column label="生产状态" prop="orderStatus" width="100">
                <template #default="scope">
                  <el-tag size="small" :type="getProdOrderStatusTagType(scope.row.orderStatus)">
                    {{ getProdOrderStatusLabel(scope.row.orderStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="计划开始" prop="planStartDate" width="110">
                <template #default="scope">{{ scope.row.planStartDate || '-' }}</template>
              </el-table-column>
              <el-table-column label="计划结束" prop="planEndDate" width="110">
                <template #default="scope">{{ scope.row.planEndDate || '-' }}</template>
              </el-table-column>
            </el-table>
            <el-card shadow="never" style="background: #0f172a">
              <template #header>
                <span>📊 生产进度概览</span>
              </template>
              <div v-if="totalProduced !== null" style="display: flex; gap: 24px; flex-wrap: wrap">
                <div>
                  <div style="font-size: 13px; color: #94a3b8; margin-bottom: 4px">计划总数</div>
                  <div style="font-size: 24px; font-weight: 700">{{ totalPlanned }}</div>
                </div>
                <div>
                  <div style="font-size: 13px; color: #94a3b8; margin-bottom: 4px">已完成</div>
                  <div style="font-size: 24px; font-weight: 700; color: #10b981">
                    {{ totalCompleted }}
                  </div>
                </div>
                <div>
                  <div style="font-size: 13px; color: #94a3b8; margin-bottom: 4px">进度</div>
                  <div style="font-size: 24px; font-weight: 700; color: #3b82f6">
                    {{ totalPlanned > 0 ? Math.round((totalCompleted / totalPlanned) * 100) : 0 }}%
                  </div>
                </div>
              </div>
            </el-card>
          </template>
          <el-empty v-else description="暂无生产订单数据" />
        </el-tab-pane>

        <el-tab-pane label="发货跟踪" name="delivery">
          <div
            v-if="deliveryRecords.length === 0 && !detail.deliveryAddress"
            style="text-align: center; padding: 40px"
          >
            <el-empty description="暂无发货信息" />
          </div>
          <div v-else>
            <el-timeline>
              <el-timeline-item
                v-for="(item, index) in deliveryTimeline"
                :key="index"
                :timestamp="item.time"
                :type="item.type"
                :color="item.color"
                :hollow="item.hollow"
              >
                {{ item.content }}
                <div v-if="item.detail" style="font-size: 12px; color: #94a3b8; margin-top: 4px">
                  {{ item.detail }}
                </div>
              </el-timeline-item>
            </el-timeline>
            <div v-if="deliveryRecords.length > 1" style="margin-top: 16px">
              <el-divider>发货单列表</el-divider>
              <el-table :data="deliveryRecords" border size="small">
                <el-table-column label="发货单号" prop="deliveryNo" width="150" />
                <el-table-column label="发货日期" prop="deliveryDate" width="100" />
                <el-table-column label="状态" prop="deliveryStatusDesc" width="80" />
                <el-table-column label="数量" prop="totalQuantity" width="60" align="right" />
                <el-table-column label="物流单号" prop="trackingNo" width="140" />
                <el-table-column label="承运商" prop="carrier" width="100" />
                <el-table-column label="收货人" prop="receiverName" width="100" />
              </el-table>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="订单明细" name="items">
          <el-table :data="detailItems" border style="width: 100%">
            <el-table-column label="序号" type="index" width="60" align="center" />
            <el-table-column label="产品编码" prop="productCode" width="120" />
            <el-table-column label="产品名称" prop="productName" width="180" />
            <el-table-column label="规格" prop="spec" width="150" />
            <el-table-column label="数量" prop="quantity" width="80" align="right" />
            <el-table-column label="单价" prop="unitPrice" width="100" align="right">
              <template #default="scope">
                {{ formatCurrency(scope.row.unitPrice) }}
              </template>
            </el-table-column>
            <el-table-column label="金额" prop="amount" width="120" align="right">
              <template #default="scope">
                {{
                  formatCurrency(
                    scope.row.amount || (scope.row.unitPrice || 0) * (scope.row.quantity || 0)
                  )
                }}
              </template>
            </el-table-column>
            <el-table-column label="生产状态" prop="prodStatus" width="100">
              <template #default="scope">
                <el-tag size="small" :type="getProdStatusTagType(scope.row.prodStatus)">
                  {{ scope.row.prodStatusDesc || getProdStatusLabelByCode(scope.row.prodStatus) }}
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

import { ref, reactive, computed, onMounted } from 'vue'
import type { TagType } from '@/types'
import { parseTime, formatCurrency } from '@/utils/format'
import { orderApi } from '@/api/sales/order'
import { deliveryApi, type SalesDeliveryVO } from '@/api/sales/delivery'
import { getProductionOrderList } from '@/api/production/order'
import type { SalesOrderQueryDTO } from '@/types/sales/order'

// ==================== 类型定义 ====================
interface ProductionOrderItem {
  orderId: number
  orderNo: string
  productName: string
  plannedQuantity: number
  completedQuantity: number
  orderStatus: number
  orderStatusDesc?: string
  planStartDate: string
  planEndDate: string
}

// ==================== 查询参数 ====================
const queryParams = reactive<SalesOrderQueryDTO>({
  pageNum: 1,
  pageSize: 10,
})

const dateRange = ref<string[]>([])

// ==================== 响应式数据 ====================
const loading = ref(false)
const productionLoading = ref(false)
const deliveryRecords = ref<SalesDeliveryVO[]>([])
const total = ref(0)
const detailOpen = ref(false)
const activeTab = ref('basic')
const orderList = ref<any[]>([])
const productionOrders = ref<ProductionOrderItem[]>([])
const detail = reactive<any>({
  orderId: undefined,
  orderNo: '',
  customerName: '',
  orderDate: '',
  deliveryDate: '',
  orderStatus: undefined,
  orderStatusDesc: '',
  prodStatus: undefined,
  prodStatusDesc: '',
  totalAmount: 0,
  finalAmount: 0,
  totalQuantity: 0,
  shippedQuantity: 0,
  salesManagerName: '',
  deliveryAddress: '',
  deliveryTerms: '',
  remark: '',
})
const detailItems = ref<any[]>([])
const totalPlanned = ref(0)
const totalCompleted = ref(0)
const totalProduced = ref(0)

// ==================== 状态字典 ====================
/** 订单状态 - 用于搜索下拉 */
const orderStatusOptions = [
  { value: 1, label: '草稿' },
  { value: 2, label: '待审核' },
  { value: 3, label: '审核中' },
  { value: 4, label: '已审核' },
  { value: 5, label: '已驳回' },
  { value: 6, label: '已确认' },
  { value: 7, label: '生产中' },
  { value: 8, label: '已发货' },
  { value: 9, label: '已完成' },
  { value: 10, label: '已取消' },
]

/** 生产订单状态标签映射 */
const prodOrderStatusMap: Record<number, { type: string; label: string }> = {
  0: { type: 'info', label: '草稿' },
  1: { type: 'primary', label: '待审核' },
  2: { type: 'success', label: '已审核' },
  3: { type: 'danger', label: '已驳回' },
  4: { type: 'primary', label: '已计划' },
  5: { type: 'warning', label: '待开始' },
  6: { type: 'warning', label: '进行中' },
  7: { type: 'danger', label: '已暂停' },
  8: { type: 'success', label: '已完成' },
  9: { type: 'danger', label: '已取消' },
  10: { type: 'info', label: '已关闭' },
  11: { type: 'danger', label: '已超期' },
}

// ==================== 数据加载 ====================
/** 获取订单列表 */
const getList = async () => {
  loading.value = true
  try {
    // 处理日期范围
    if (dateRange.value && dateRange.value.length === 2) {
      queryParams.createTimeStart = dateRange.value[0] as any
      queryParams.createTimeEnd = dateRange.value[1] as any
    } else {
      delete queryParams.createTimeStart
      delete queryParams.createTimeEnd
    }

    const res = await orderApi.getOrders(queryParams)
    if (res.code === 200) {
      const pageData = res.data
      orderList.value = pageData?.records || []
      total.value = pageData?.total || 0
    } else {
      orderList.value = []
      total.value = 0
    }
  } catch (error) {
    console.error('获取订单列表失败:', error)
    orderList.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

/** 获取生产订单进度 */
const loadProductionProgress = async (salesOrderId: number) => {
  productionLoading.value = true
  try {
    const res = await getProductionOrderList({ salesOrderId, pageSize: 50, pageNum: 1 })
    const records = res?.data?.records || (res?.data && Array.isArray(res.data) ? res.data : [])
    productionOrders.value = records.map((r: any) => ({
      orderId: r.orderId,
      orderNo: r.orderNo,
      productName: r.productName,
      plannedQuantity: r.plannedQuantity || 0,
      completedQuantity: r.completedQuantity || 0,
      orderStatus: r.orderStatus,
      orderStatusDesc: r.orderStatusDesc,
      planStartDate: r.planStartDate,
      planEndDate: r.planEndDate,
    }))
    totalPlanned.value = productionOrders.value.reduce(
      (s: number, o: ProductionOrderItem) => s + o.plannedQuantity,
      0
    )
    totalCompleted.value = productionOrders.value.reduce(
      (s: number, o: ProductionOrderItem) => s + o.completedQuantity,
      0
    )
    totalProduced.value = totalCompleted.value
  } catch (error) {
    console.error('获取生产进度失败:', error)
    productionOrders.value = []
    totalPlanned.value = 0
    totalCompleted.value = 0
    totalProduced.value = 0
  } finally {
    productionLoading.value = false
  }
}

// ==================== 事件处理 ====================
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const resetQuery = () => {
  dateRange.value = []
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    orderNo: undefined,
    customerName: undefined,
    orderStatus: undefined,
    createTimeStart: undefined,
    createTimeEnd: undefined,
    orderByColumn: undefined,
    isAsc: undefined,
  })
  getList()
}

const handleSelectionChange = (selection: any[]) => {
  // 选中的行
}

const handleSortChange = (column: any) => {
  if (column.prop && column.order) {
    queryParams.orderByColumn = column.prop
    queryParams.isAsc = column.order === 'ascending' ? 'asc' : 'desc'
  } else {
    delete queryParams.orderByColumn
    delete queryParams.isAsc
  }
  getList()
}

/** 查看订单跟踪详情 */
const handleView = (row: any) => {
  loadOrderDetail(row.orderId, 'basic')
}

/** 生产进度 */
const handleProductionProgress = (row: any) => {
  loadOrderDetail(row.orderId, 'production')
}

/** 发货跟踪 */
const handleDeliveryTracking = (row: any) => {
  loadOrderDetail(row.orderId, 'delivery')
}

/** 加载发货记录 */
const loadDeliveryRecords = async (orderId: number) => {
  try {
    const res = await deliveryApi.listByOrderId(orderId)
    if (res.code === 200) {
      deliveryRecords.value = res.data || []
    }
  } catch (error) {
    console.error('获取发货记录失败:', error)
    deliveryRecords.value = []
  }
}

/** 构建发货时间线 */
const deliveryTimeline = computed(() => {
  const timeline: Array<{
    time: string
    content: string
    type: 'primary' | 'success' | 'warning' | 'danger' | 'info'
    color: string
    hollow?: boolean
    detail?: string
  }> = []

  const records = deliveryRecords.value
  if (records.length === 0) return timeline

  // 按发货日期排序（旧->新）
  const sorted = [...records].sort((a, b) =>
    (a.deliveryDate || '').localeCompare(b.deliveryDate || '')
  )

  for (const r of sorted) {
    const statusText = r.deliveryStatusDesc || '已发货'
    timeline.push({
      time: r.deliveryDate || '',
      content: `${statusText} - ${r.deliveryNo}`,
      type: r.deliveryStatus === 4 ? 'success' : r.deliveryStatus === 5 ? 'danger' : 'primary',
      color: r.deliveryStatus === 4 ? '#10b981' : r.deliveryStatus === 5 ? '#ef4444' : '#3b82f6',
      detail: [
        r.carrier && `承运商: ${r.carrier}`,
        r.trackingNo && `物流单号: ${r.trackingNo}`,
        r.totalQuantity && `数量: ${r.totalQuantity}`,
        r.receiverName && `收货人: ${r.receiverName}`,
      ]
        .filter(Boolean)
        .join(' | '),
    })

    // 如果有签收记录，加一条
    if (r.receiveTime) {
      timeline.push({
        time: r.receiveTime,
        content: '已签收',
        type: 'success',
        color: '#10b981',
        detail: r.receiveRemark ? `备注: ${r.receiveRemark}` : undefined,
      })
    }
  }

  return timeline
})

/** 加载订单详情 */
const loadOrderDetail = async (orderId: number, tab: string) => {
  detailOpen.value = true
  activeTab.value = tab
  try {
    const res = await orderApi.getOrder(orderId)
    const data = (res.code === 200 ? res.data : res.data) || (res as any)
    Object.assign(detail, {
      orderId: data.orderId,
      orderNo: data.orderNo,
      customerName: data.customerName,
      orderDate: data.orderDate,
      deliveryDate: data.deliveryDate,
      orderStatus: data.orderStatus,
      orderStatusDesc: data.orderStatusDesc,
      prodStatus: data.prodStatus,
      prodStatusDesc: data.prodStatusDesc,
      totalAmount: data.totalAmount,
      finalAmount: data.finalAmount,
      totalQuantity: data.totalQuantity || 0,
      shippedQuantity: data.shippedQuantity || 0,
      salesManagerName: data.salesManagerName,
      deliveryAddress: data.deliveryAddress,
      deliveryTerms: data.deliveryTerms,
      remark: data.remark,
    })
    detailItems.value = data.items || []

    if (tab === 'production' && orderId) {
      loadProductionProgress(orderId)
    }
    if (tab === 'delivery' && orderId) {
      loadDeliveryRecords(orderId)
    }
  } catch (error) {
    console.error('获取订单详情失败:', error)
  }
}

// ==================== 状态标签辅助函数 ====================
/** 订单状态标签类型 */
const getStatusTagType = (status: number | undefined) => {
  switch (status) {
    case 1:
      return 'info' // 草稿
    case 2:
      return 'primary' // 待审核
    case 3:
      return 'warning' // 审核中
    case 4:
      return 'success' // 已审核
    case 5:
      return 'danger' // 已驳回
    case 6:
      return 'primary' // 已确认
    case 7:
      return 'warning' // 生产中
    case 8:
      return 'success' // 已发货
    case 9:
      return 'success' // 已完成
    case 10:
      return 'danger' // 已取消
    default:
      return 'info'
  }
}

/** 生产状态标签类型（订单级 prodStatus） */
const getProdStatusTagType = (status: number | undefined) => {
  switch (status) {
    case 1:
      return 'info' // 无生产
    case 2:
      return 'warning' // 部分生产中
    case 3:
      return 'warning' // 全部生产中
    case 4:
      return 'success' // 生产完成
    default:
      return 'info'
  }
}

/** 生产状态标签文本（按代码） */
const getProdStatusLabelByCode = (code: number | undefined) => {
  const map: Record<number, string> = {
    1: '无生产',
    2: '部分生产中',
    3: '全部生产中',
    4: '生产完成',
  }
  return code !== undefined ? map[code] || '未知' : '未知'
}

/** 生产订单(ProductionOrder)状态标签类型 */
const getProdOrderStatusTagType = (status: number | undefined): TagType => {
  return (prodOrderStatusMap[status ?? -1]?.type || 'info') as TagType
}

/** 生产订单(ProductionOrder)状态标签文本 */
const getProdOrderStatusLabel = (status: number | undefined) => {
  return prodOrderStatusMap[status ?? -1]?.label || '未知'
}

/** 发货状态标签类型 - 根据订单字段推断 */
const getDeliveryStatusTagType = (row: any) => {
  if (!row.shippedQuantity || row.shippedQuantity === 0) {
    if (row.prodStatus === 4) return 'warning' // 生产完成，待发货
    return 'info' // 未发货
  }
  if (row.shippedQuantity >= (row.totalQuantity || 1)) return 'success' // 全部发货
  return 'warning' // 部分发货
}

/** 发货状态标签文本 - 根据订单字段推断 */
const getDeliveryStatusLabel = (row: any) => {
  if (!row.shippedQuantity || row.shippedQuantity === 0) {
    if (row.prodStatus === 4) return '待发货'
    return '未发货'
  }
  if (row.shippedQuantity >= (row.totalQuantity || 1)) return '已全部发货'
  return '部分发货'
}

// ==================== 初始化 ====================
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
