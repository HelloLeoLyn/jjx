import { ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import type { ProductionOrderVO, OrderType } from '@/types/production/order'
import { completeExecution } from '@/api/production/order'
import { checkBatchOperationPermission } from '../utils/orderPermissions'

/**
 * 生产订单增删改查业务逻辑Composable
 */
export function useProductionOrderCRUD(orderData: {
  selectedRows: ProductionOrderVO[]
  loadData: () => Promise<void>
  deleteOrder: (orderId: string, orderType?: OrderType) => Promise<boolean>
  batchDeleteOrders: (orderIds: string[]) => Promise<boolean>
}) {
  const router = useRouter()

  // 状态
  const saving = ref(false)
  const submitting = ref(false)
  const deleting = ref(false)

  /**
   * 跳转到创建页面
   */
  const navigateToCreate = () => {
    router.push('/production/order/create')
  }

  /**
   * 跳转到编辑页面
   */
  const navigateToEdit = (order: ProductionOrderVO) => {
    router.push(`/production/order/edit/${order.orderId}`)
  }

  /**
   * 跳转到详情页面
   */
  const navigateToDetail = (order: ProductionOrderVO) => {
    router.push(`/production/order/detail/${order.orderId}`)
  }

  /**
   * 处理查看订单
   */
  const handleView = (order: ProductionOrderVO) => {
    navigateToDetail(order)
  }

  /**
   * 处理编辑订单
   */
  const handleEdit = (order: ProductionOrderVO) => {
    if (!order.canEdit) {
      ElMessage.warning('当前订单状态不允许编辑')
      return
    }
    navigateToEdit(order)
  }

  /**
   * 处理转为工单
   */
  const handleConvertToWorkOrder = (order: ProductionOrderVO) => {
    if (!order.canConvertToWorkOrder) {
      ElMessage.warning('只有已批准的生产计划才能转为工单')
      return
    }

    ElMessageBox.confirm(`确定要将生产计划 "${order.orderNo}" 转为生产工单吗？`, '转为工单', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
      .then(() => {
        // 调用API转换
        ElMessage.success('转换功能开发中...')
      })
      .catch(() => {
        // 用户取消
      })
  }

  /**
   * 处理开始执行
   */
  const handleStart = (order: ProductionOrderVO) => {
    if (!order.canStart) {
      ElMessage.warning('只有已排程的生产工单才能开始执行')
      return
    }

    ElMessageBox.confirm(`确定要开始执行工单 "${order.orderNo}" 吗？`, '开始执行', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
      .then(() => {
        // 调用API开始执行
        ElMessage.success('开始执行功能开发中...')
      })
      .catch(() => {
        // 用户取消
      })
  }

  /**
   * 处理完成工单（P3-D：接真实 API，FQC gate 失败时明确提示）
   */
  const handleComplete = (order: ProductionOrderVO) => {
    if (!order.canComplete) {
      ElMessage.warning('只有进行中的生产工单才能完成')
      return
    }

    ElMessageBox.confirm(`确定要完成工单 "${order.orderNo}" 吗？`, '完成工单', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
      .then(async () => {
        try {
          await completeExecution(String(order.orderId), { completedQuantity: 0 })
          ElMessage.success('工单已完成')
          orderData.loadData()
        } catch (e: any) {
          const msg = e?.msg || e?.message || '完成工单失败'
          // P3-D：FQC gate 未通过时给出明确业务提示
          if (msg.includes('FQC') || msg.includes('质检') || msg.includes('完工检验')) {
            ElMessage.error('完工检验尚未通过，订单暂不能完成：' + msg)
          } else {
            ElMessage.error(msg)
          }
        }
      })
      .catch(() => {
        // 用户取消
      })
  }

  /**
   * 处理取消订单
   */
  const handleCancel = (order: ProductionOrderVO) => {
    if (!order.canCancel) {
      ElMessage.warning('已完成或已取消的订单不能再次取消')
      return
    }

    ElMessageBox.confirm(`确定要取消订单 "${order.orderNo}" 吗？`, '取消订单', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
      .then(() => {
        // 调用API取消
        ElMessage.success('取消功能开发中...')
      })
      .catch(() => {
        // 用户取消
      })
  }

  /**
   * 处理删除订单
   */
  const handleDelete = (order: ProductionOrderVO) => {
    ElMessageBox.confirm(`确定要删除订单 "${order.orderNo}" 吗？`, '删除订单', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
      .then(() => {
        deleting.value = true
        orderData
          .deleteOrder(order.orderId, order.orderType)
          .then(() => {
            ElMessage.success('删除成功')
            orderData.loadData()
          })
          .catch(() => {
            ElMessage.error('删除失败')
          })
          .finally(() => {
            deleting.value = false
          })
      })
      .catch(() => {
        // 用户取消
      })
  }

  /**
   * 处理批量删除
   */
  const handleBatchDelete = () => {
    if (orderData.selectedRows.length === 0) {
      ElMessage.warning('请选择要删除的订单')
      return
    }

    // 检查批量删除权限
    const permissionCheck = checkBatchOperationPermission(orderData.selectedRows, 'delete')
    if (!permissionCheck.allowed) {
      ElMessage.warning(permissionCheck.message || '批量删除权限检查失败')
      return
    }

    ElMessageBox.confirm(
      `确定要删除选中的 ${orderData.selectedRows.length} 个订单吗？`,
      '批量删除',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
      .then(() => {
        deleting.value = true
        const orderIds = orderData.selectedRows.map((row) => row.orderId)
        orderData
          .batchDeleteOrders(orderIds)
          .then(() => {
            ElMessage.success('批量删除成功')
            orderData.loadData()
          })
          .catch(() => {
            ElMessage.error('批量删除失败')
          })
          .finally(() => {
            deleting.value = false
          })
      })
      .catch(() => {
        // 用户取消
      })
  }

  /**
   * 处理批量操作
   */
  const handleBatchCommand = (command: string) => {
    if (orderData.selectedRows.length === 0) {
      ElMessage.warning('请选择要操作的订单')
      return
    }

    // 检查批量操作权限
    const permissionCheck = checkBatchOperationPermission(orderData.selectedRows, command)
    if (!permissionCheck.allowed) {
      ElMessage.warning(permissionCheck.message || '批量操作权限检查失败')
      return
    }

    switch (command) {
      case 'approve':
        ElMessage.info('批量审批功能开发中...')
        break
      case 'start':
        ElMessage.info('批量开始功能开发中...')
        break
      case 'complete':
        ElMessage.info('批量完成功能开发中...')
        break
      case 'cancel':
        ElMessage.info('批量取消功能开发中...')
        break
    }
  }

  /**
   * 处理更多操作
   */
  const handleMoreAction = (order: ProductionOrderVO, command: string) => {
    switch (command) {
      case 'copy':
        ElMessage.info('复制功能开发中...')
        break
      case 'export':
        ElMessage.info('导出功能开发中...')
        break
      case 'print':
        ElMessage.info('打印功能开发中...')
        break
      case 'history':
        ElMessage.info('操作历史功能开发中...')
        break
      case 'delete':
        handleDelete(order)
        break
    }
  }

  return {
    // 状态
    saving,
    submitting,
    deleting,

    // 导航方法
    navigateToCreate,
    navigateToEdit,
    navigateToDetail,

    // 单个订单操作方法
    handleView,
    handleEdit,
    handleConvertToWorkOrder,
    handleStart,
    handleComplete,
    handleCancel,
    handleDelete,
    handleMoreAction,

    // 批量操作方法
    handleBatchDelete,
    handleBatchCommand,
  }
}

export type UseProductionOrderCRUDReturn = ReturnType<typeof useProductionOrderCRUD>
