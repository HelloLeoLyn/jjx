import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type {
  PurchaseOrderVO,
  PurchaseOrderQuery,
  PurchaseOrderStats,
} from '@/types/purchase/order'
import { listOrder, getOrder } from '@/api/purchase/order'

/**
 * 采购订单数据加载Composable
 */
export function usePurchaseOrder() {
  // 状态
  const orderList = ref<PurchaseOrderVO[]>([])
  const total = ref(0)
  const loading = ref(false)

  /**
   * 加载订单数据
   */
  const loadData = async (query: PurchaseOrderQuery): Promise<void> => {
    loading.value = true
    try {
      const response = await listOrder({
        pageNum: query.pageNum,
        pageSize: query.pageSize,
        orderNo: query.orderNo || undefined,
        supplierName: query.supplierName || undefined,
        approvalStatus: query.approvalStatus,
        receiptStatus: query.receiptStatus,
        paymentStatus: query.paymentStatus,
        urgentFlag: query.urgentFlag,
        orderType: query.orderType,
        orderDateStart: query.orderDateStart,
        orderDateEnd: query.orderDateEnd,
        createTimeStart: query.createTimeStart,
        createTimeEnd: query.createTimeEnd,
        sortField: query.sortField,
        sortOrder: query.sortOrder,
      })

      const { records, total: totalCount } = response.data
      orderList.value = records || []
      total.value = totalCount || 0
    } catch (error) {
      console.error('加载订单数据失败:', error)
      ElMessage.error('加载订单数据失败')
      orderList.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  /**
   * 重置数据
   */
  const resetData = () => {
    orderList.value = []
    total.value = 0
  }

  /**
   * 获取订单详情
   */
  const getOrderDetail = async (orderId: string): Promise<PurchaseOrderVO | null> => {
    try {
      const response = await getOrder(Number(orderId))
      return response.data || null
    } catch (error) {
      console.error('获取订单详情失败:', error)
      ElMessage.error('获取订单详情失败')
      return null
    }
  }

  return {
    // 状态
    orderList,
    total,
    loading,

    // 方法
    loadData,
    resetData,
    getOrderDetail,
  }
}

export type UsePurchaseOrderReturn = ReturnType<typeof usePurchaseOrder>
