import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type {
  PurchaseOrderVO,
  PurchaseOrderCreateDTO,
  PurchaseOrderUpdateDTO,
  OrderStatusUpdateDTO,
  ApprovalStatusUpdateDTO,
  ReceiptStatusUpdateDTO,
  PaymentStatusUpdateDTO,
} from '@/types/purchase/order'
import {
  addOrder,
  updateOrder as apiUpdateOrder,
  submitOrder,
  batchSubmitOrders,
  approveOrder,
  changeOrderStatus,
  changeReceiptStatus,
  updatePaymentInfo,
  copyOrder as apiCopyOrder,
  exportOrder as apiExportOrder,
  cancleOrder as apiCancleOrder,
} from '@/api/purchase/order'

/**
 * 采购订单操作Composable
 */
export function usePurchaseOrderOperations() {
  // 状态
  const saving = ref(false)
  const deleting = ref(false)

  /**
   * 创建订单
   */
  const createOrder = async (data: PurchaseOrderCreateDTO): Promise<boolean> => {
    saving.value = true
    try {
      await addOrder(data as any)
      ElMessage.success('创建采购订单成功')
      return true
    } catch (error) {
      console.error('创建采购订单失败:', error)
      ElMessage.error('创建采购订单失败')
      return false
    } finally {
      saving.value = false
    }
  }

  /**
   * 更新订单
   */
  const updateOrder = async (data: PurchaseOrderUpdateDTO): Promise<boolean> => {
    saving.value = true
    try {
      await apiUpdateOrder(data as any)
      ElMessage.success('更新采购订单成功')
      return true
    } catch (error) {
      console.error('更新采购订单失败:', error)
      ElMessage.error('更新采购订单失败')
      return false
    } finally {
      saving.value = false
    }
  }

  /**
   * 更新订单审批状态
   */
  const updateOrderStatus = async (data: OrderStatusUpdateDTO): Promise<boolean> => {
    saving.value = true
    try {
      await changeOrderStatus(Number(data.orderId), data.approvalStatus as any)
      ElMessage.success('更新订单状态成功')
      return true
    } catch (error) {
      console.error('更新订单状态失败:', error)
      ElMessage.error('更新订单状态失败')
      return false
    } finally {
      saving.value = false
    }
  }

  /**
   * 更新审批状态
   */
  const updateApprovalStatus = async (data: ApprovalStatusUpdateDTO): Promise<boolean> => {
    saving.value = true
    try {
      await approveOrder({
        orderId: Number(data.orderId as any),
        approverId: 0, // 需要从当前用户获取
        approverName: '', // 需要从当前用户获取
        approvalComment: data.approvalComment,
        approvalStatus: data.approvalStatus,
      })
      ElMessage.success('更新审批状态成功')
      return true
    } catch (error) {
      console.error('更新审批状态失败:', error)
      ElMessage.error('更新审批状态失败')
      return false
    } finally {
      saving.value = false
    }
  }

  /**
   * 更新收货状态
   */
  const updateReceiptStatus = async (data: ReceiptStatusUpdateDTO): Promise<boolean> => {
    saving.value = true
    try {
      await changeReceiptStatus(Number(data.orderId), data.receiptStatus as any)
      ElMessage.success('更新收货状态成功')
      return true
    } catch (error) {
      console.error('更新收货状态失败:', error)
      ElMessage.error('更新收货状态失败')
      return false
    } finally {
      saving.value = false
    }
  }

  /**
   * 更新付款状态
   */
  const updatePaymentStatus = async (data: PaymentStatusUpdateDTO): Promise<boolean> => {
    saving.value = true
    try {
      await updatePaymentInfo(Number(data.orderId), data.paymentAmount || 0, data.paymentStatus as any)
      ElMessage.success('更新付款状态成功')
      return true
    } catch (error) {
      console.error('更新付款状态失败:', error)
      ElMessage.error('更新付款状态失败')
      return false
    } finally {
      saving.value = false
    }
  }

  /**
   * 提交审批
   */
  const submitForApproval = async (orderId: string): Promise<boolean> => {
    saving.value = true
    try {
      await submitOrder(Number(orderId) as any)
      ElMessage.success('提交审批成功')
      return true
    } catch (error) {
      console.error('提交审批失败:', error)
      ElMessage.error('提交审批失败')
      return false
    } finally {
      saving.value = false
    }
  }

  /**
   * 批量提交审批
   */
  const batchSubmitForApproval = async (orderIds: string[]): Promise<boolean> => {
    saving.value = true
    try {
      const ids = orderIds.map((id) => Number(id) as any)
      await batchSubmitOrders(ids)
      ElMessage.success(`批量提交 ${orderIds.length} 个订单审批成功`)
      return true
    } catch (error) {
      console.error('批量提交审批失败:', error)
      ElMessage.error('批量提交审批失败')
      return false
    } finally {
      saving.value = false
    }
  }

  /**
   * 取消订单
   */
  const cancleOrder = async (orderId: string): Promise<boolean> => {
    saving.value = true
    try {
      await apiCancleOrder(Number(orderId) as any)
      ElMessage.success('取消订单成功')
      return true
    } catch (error) {
      console.error('取消订单失败:', error)
      ElMessage.error('取消订单失败')
      return false
    } finally {
      saving.value = false
    }
  }

  /**
   * 复制订单
   */
  const copyOrder = async (orderId: string): Promise<boolean> => {
    saving.value = true
    try {
      await apiCopyOrder(Number(orderId) as any)
      ElMessage.success('复制订单成功')
      return true
    } catch (error) {
      console.error('复制订单失败:', error)
      ElMessage.error('复制订单失败')
      return false
    } finally {
      saving.value = false
    }
  }

  /**
   * 导出订单
   */
  const exportOrder = async (
    orderId: string,
    format: 'excel' | 'pdf' = 'excel'
  ): Promise<boolean> => {
    saving.value = true
    try {
      await apiExportOrder({ orderId, format })
      ElMessage.success(`导出${format === 'excel' ? 'Excel' : 'PDF'}成功`)
      return true
    } catch (error) {
      console.error('导出订单失败:', error)
      ElMessage.error('导出订单失败')
      return false
    } finally {
      saving.value = false
    }
  }

  /**
   * 批量导出订单
   */
  const batchExportOrders = async (
    orderIds: string[],
    format: 'excel' | 'pdf' = 'excel'
  ): Promise<boolean> => {
    saving.value = true
    try {
      await apiExportOrder({ orderIds: orderIds.join(','), format })
      ElMessage.success(
        `批量导出 ${orderIds.length} 个订单${format === 'excel' ? 'Excel' : 'PDF'}成功`
      )
      return true
    } catch (error) {
      console.error('批量导出订单失败:', error)
      ElMessage.error('批量导出订单失败')
      return false
    } finally {
      saving.value = false
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
    updateApprovalStatus,
    updateReceiptStatus,
    updatePaymentStatus,
    submitForApproval,
    batchSubmitForApproval,
    cancleOrder,
    copyOrder,
    exportOrder,
    batchExportOrders,
  }
}

export type UsePurchaseOrderOperationsReturn = ReturnType<typeof usePurchaseOrderOperations>
