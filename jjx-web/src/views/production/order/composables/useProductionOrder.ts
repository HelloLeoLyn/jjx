import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getProductionOrderList,
  getProductionOrderDetail,
  createProductionOrder,
  updateProductionOrder,
  deleteProductionOrder,
  batchDeleteProductionOrders,
} from '@/api/production/order'
import type {
  ProductionOrderVO,
  ProductionOrderQuery,
  ProductionOrderCreateDTO,
  ProductionOrderUpdateDTO,
  OrderType,
} from '@/types/production/order'
import {
  getStatusLabel,
  getStatusType,
  getPriorityLabel,
  getMaterialStatusLabel,
  calculateProgress,
  formatDateRange,
} from '../utils/orderFormatters'
import {
  canConvertToWorkOrder,
  canStart,
  canComplete,
  canCancel,
  canEdit,
} from '../utils/orderPermissions'

/**
 * 生产订单数据管理Composable
 */
export function useProductionOrder() {
  // 响应式数据
  const loading = ref(false)
  const orderList = ref<ProductionOrderVO[]>([])
  const selectedRows = ref<ProductionOrderVO[]>([])
  const total = ref(0)

  // 搜索表单
  const searchForm = reactive<ProductionOrderQuery>({
    pageNum: 1,
    pageSize: 20,
    orderType: 'all',
    orderStatus: '',
    approvalStatus: '',
    executionStatus: '',
    planType: undefined,
    orderNo: '',
    productName: '',
    productCode: '',
    salesOrderNo: '',
    planDateStart: '',
    planDateEnd: '',
    createTimeStart: '',
    createTimeEnd: '',
    sortField: '',
    sortOrder: undefined,
  })

  /**
   * 加载订单列表
   */
  const loadData = async (viewType: 'plan' | 'work_order' | 'all' = 'all') => {
    loading.value = true

    try {
      const queryParams: ProductionOrderQuery = {
        ...searchForm,
        orderType: (viewType === 'plan'
          ? 'plan'
          : viewType === 'work_order'
            ? 'work_order'
            : 'all') as OrderType | 'all',
      }

      const response = await getProductionOrderList(queryParams)
      const list = (response && response.data) || []

      if (list.length > 0) {
        // 转换API返回的数据为前端需要的格式（后端返回数组，无 .list 字段）
        orderList.value = list.map((order: any) => {
          // 2026-08-11 修复：后端存大写(PLAN/WORK_ORDER/HIGH)，前端判断用小写——统一小写化后，
          // 展示字段和权限函数(转工单/开始/完成等按钮)必须都用小写化后的对象，否则按钮不显示
          const normalized = {
            ...order,
            orderType: (order.orderType || '').toLowerCase(),
            priority: (order.priority || '').toLowerCase(),
          }
          return {
            ...normalized,
            // 计算显示字段
            statusLabel: getStatusLabel(
              order.orderStatus,
              order.approvalStatus,
              order.executionStatus
            ),
            statusType: getStatusType(order.orderStatus, order.approvalStatus, order.executionStatus),
            priorityLabel: getPriorityLabel(normalized.priority),
            materialStatusLabel: getMaterialStatusLabel(order.materialStatus),
            planDateRange: formatDateRange(order.planStartDate, order.planEndDate),
            actualTimeRange: formatDateRange(order.actualStartDate, order.actualEndDate),
            progress: calculateProgress(order.completedQuantity, order.plannedQuantity),
            progressLabel: `${order.completedQuantity}/${order.plannedQuantity}`,
            remainingQuantity: order.plannedQuantity - order.completedQuantity,
            // 权限控制字段（用 normalized：小写 orderType 判断才生效）
            canConvertToWorkOrder: canConvertToWorkOrder(normalized),
            canStart: canStart(normalized),
            canComplete: canComplete(normalized),
            canCancel: canCancel(normalized),
            canEdit: canEdit(normalized),
          }
        })

        total.value = list.length || 0
      } else {
        orderList.value = []
        total.value = 0
      }
    } catch (error) {
      console.error('加载生产订单数据失败:', error)
      ElMessage.error('加载数据失败，请稍后重试')
      orderList.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  /**
   * 获取订单详情
   */
  const getOrderDetail = async (orderId: string, orderType?: OrderType) => {
    try {
      const response = await getProductionOrderDetail(orderId, orderType)
      if (response && response.data) {
        return response.data
      }
      return null
    } catch (error) {
      console.error('获取订单详情失败:', error)
      ElMessage.error('获取订单详情失败')
      return null
    }
  }

  /**
   * 创建订单
   */
  const createOrder = async (data: ProductionOrderCreateDTO) => {
    try {
      const response = await createProductionOrder(data)
      if (response && response.data) {
        ElMessage.success('创建订单成功')
        return response.data
      }
      return null
    } catch (error) {
      console.error('创建订单失败:', error)
      ElMessage.error('创建订单失败')
      throw error
    }
  }

  /**
   * 更新订单
   */
  const updateOrder = async (data: ProductionOrderUpdateDTO) => {
    try {
      const response = await updateProductionOrder(data)
      if (response && response.data) {
        ElMessage.success('更新订单成功')
        return response.data
      }
      return null
    } catch (error) {
      console.error('更新订单失败:', error)
      ElMessage.error('更新订单失败')
      throw error
    }
  }

  /**
   * 删除订单
   */
  const deleteOrder = async (orderId: string, orderType?: OrderType) => {
    try {
      await deleteProductionOrder(orderId, orderType)
      ElMessage.success('删除订单成功')
      return true
    } catch (error) {
      console.error('删除订单失败:', error)
      ElMessage.error('删除订单失败')
      throw error
    }
  }

  /**
   * 批量删除订单
   */
  const batchDeleteOrders = async (orderIds: string[]) => {
    try {
      await batchDeleteProductionOrders(orderIds)
      ElMessage.success('批量删除成功')
      selectedRows.value = []
      return true
    } catch (error) {
      console.error('批量删除失败:', error)
      ElMessage.error('批量删除失败')
      throw error
    }
  }

  /**
   * 重置搜索表单
   */
  const resetSearch = () => {
    searchForm.orderNo = ''
    searchForm.productName = ''
    searchForm.productCode = ''
    searchForm.salesOrderNo = ''
    searchForm.orderStatus = ''
    searchForm.approvalStatus = ''
    searchForm.executionStatus = ''
    searchForm.planType = undefined
    searchForm.planDateStart = ''
    searchForm.planDateEnd = ''
    searchForm.createTimeStart = ''
    searchForm.createTimeEnd = ''
  }

  /**
   * 处理分页变化
   */
  const handlePaginationChange = (page: number, size: number) => {
    searchForm.pageNum = page
    searchForm.pageSize = size
  }

  /**
   * 处理排序变化
   */
  const handleSortChange = (prop: string, order: 'ascending' | 'descending' | null) => {
    searchForm.sortField = prop
    searchForm.sortOrder =
      order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : undefined
  }

  /**
   * 处理选择变化
   */
  const handleSelectionChange = (selection: ProductionOrderVO[]) => {
    selectedRows.value = selection
  }

  return {
    // 状态
    loading,
    orderList,
    selectedRows,
    total,
    searchForm,

    // 方法
    loadData,
    getOrderDetail,
    createOrder,
    updateOrder,
    deleteOrder,
    batchDeleteOrders,
    resetSearch,
    handlePaginationChange,
    handleSortChange,
    handleSelectionChange,
  }
}

export type UseProductionOrderReturn = ReturnType<typeof useProductionOrder>
