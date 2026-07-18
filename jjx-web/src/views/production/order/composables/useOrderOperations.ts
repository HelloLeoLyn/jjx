import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type {
  ProductionOrderVO,
  ProductionOrderCreateDTO,
  ProductionOrderUpdateDTO,
  OrderStatusUpdateDTO,
} from '@/types/production/order'

/**
 * 生产订单操作Composable
 */
export function useOrderOperations() {
  // 状态
  const saving = ref(false)
  const deleting = ref(false)

  /**
   * 创建订单
   */
  const createOrder = async (data: ProductionOrderCreateDTO): Promise<boolean> => {
    saving.value = true
    try {
      // 模拟API调用
      await new Promise((resolve) => setTimeout(resolve, 1000))
      ElMessage.success('创建订单成功')
      return true
    } catch (error) {
      console.error('创建订单失败:', error)
      ElMessage.error('创建订单失败')
      return false
    } finally {
      saving.value = false
    }
  }

  /**
   * 更新订单
   */
  const updateOrder = async (data: ProductionOrderUpdateDTO): Promise<boolean> => {
    saving.value = true
    try {
      // 模拟API调用
      await new Promise((resolve) => setTimeout(resolve, 1000))
      ElMessage.success('更新订单成功')
      return true
    } catch (error) {
      console.error('更新订单失败:', error)
      ElMessage.error('更新订单失败')
      return false
    } finally {
      saving.value = false
    }
  }

  /**
   * 更新订单状态
   */
  const updateOrderStatus = async (data: OrderStatusUpdateDTO): Promise<boolean> => {
    saving.value = true
    try {
      // 模拟API调用
      await new Promise((resolve) => setTimeout(resolve, 1000))
      ElMessage.success('更新状态成功')
      return true
    } catch (error) {
      console.error('更新状态失败:', error)
      ElMessage.error('更新状态失败')
      return false
    } finally {
      saving.value = false
    }
  }

  /**
   * 删除订单
   */
  const deleteOrder = async (orderId: string, reason?: string): Promise<boolean> => {
    deleting.value = true
    try {
      // 模拟API调用
      await new Promise((resolve) => setTimeout(resolve, 1000))
      ElMessage.success('删除订单成功')
      return true
    } catch (error) {
      console.error('删除订单失败:', error)
      ElMessage.error('删除订单失败')
      return false
    } finally {
      deleting.value = false
    }
  }

  /**
   * 批量删除订单
   */
  const batchDeleteOrders = async (orderIds: string[], reason?: string): Promise<boolean> => {
    deleting.value = true
    try {
      // 模拟API调用
      await new Promise((resolve) => setTimeout(resolve, 1500))
      ElMessage.success(`批量删除 ${orderIds.length} 个订单成功`)
      return true
    } catch (error) {
      console.error('批量删除订单失败:', error)
      ElMessage.error('批量删除订单失败')
      return false
    } finally {
      deleting.value = false
    }
  }

  return {
    // 状态
    saving,
    deleting,

    // 方法
    createOrder,
    updateOrder,
    updateOrderStatus,
    deleteOrder,
    batchDeleteOrders,
  }
}
