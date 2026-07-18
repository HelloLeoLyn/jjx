import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { PurchaseOrderStats } from '@/types/purchase/order'
import { getOrderStatistics } from '@/api/purchase/order'

/**
 * 采购订单统计Composable
 */
export function usePurchaseOrderStats() {
  // 状态
  const stats = ref<PurchaseOrderStats>({
    totalCount: 0,
    draftCount: 0,
    pendingApprovalCount: 0,
    approvedCount: 0,
    rejectedCount: 0,
    cancelledCount: 0,
    urgentCount: 0,
    overdueCount: 0,
    todayCount: 0,
    weekCount: 0,
    monthCount: 0,
    totalAmount: 0,
    avgAmount: 0,
    maxAmount: 0,
    minAmount: 0,
  })

  const loading = ref(false)

  /**
   * 加载统计信息
   */
  const loadStats = async (): Promise<void> => {
    loading.value = true
    try {
      const response = await getOrderStatistics()
      stats.value = response.data || stats.value
    } catch (error) {
      console.error('加载统计信息失败:', error)
      ElMessage.error('加载统计信息失败')
      resetStats()
    } finally {
      loading.value = false
    }
  }

  /**
   * 重置统计信息
   */
  const resetStats = () => {
    stats.value = {
      totalCount: 0,
      draftCount: 0,
      pendingApprovalCount: 0,
      approvedCount: 0,
      rejectedCount: 0,
      cancelledCount: 0,
      urgentCount: 0,
      overdueCount: 0,
      todayCount: 0,
      weekCount: 0,
      monthCount: 0,
      totalAmount: 0,
      avgAmount: 0,
      maxAmount: 0,
      minAmount: 0,
    }
  }

  /**
   * 获取统计卡片数据
   */
  const getStatCards = () => {
    return [
      {
        title: '总订单数',
        value: stats.value.totalCount,
        icon: 'Document',
        color: '#409eff',
        description: '全部采购订单数量',
      },
      {
        title: '待审批',
        value: stats.value.pendingApprovalCount,
        icon: 'Clock',
        color: '#e6a23c',
        description: '等待审批的订单',
      },
      {
        title: '已审批',
        value: stats.value.approvedCount,
        icon: 'CircleCheck',
        color: '#67c23a',
        description: '审批通过的订单',
      },
      {
        title: '紧急订单',
        value: stats.value.urgentCount,
        icon: 'Warning',
        color: '#f56c6c',
        description: '标记为紧急的订单',
      },
      {
        title: '逾期订单',
        value: stats.value.overdueCount,
        icon: 'Timer',
        color: '#f56c6c',
        description: '已超过交货日期的订单',
      },
      {
        title: '今日新增',
        value: stats.value.todayCount,
        icon: 'Calendar',
        color: '#909399',
        description: '今日创建的订单',
      },
      {
        title: '本周新增',
        value: stats.value.weekCount,
        icon: 'Calendar',
        color: '#909399',
        description: '本周创建的订单',
      },
      {
        title: '本月新增',
        value: stats.value.monthCount,
        icon: 'Calendar',
        color: '#909399',
        description: '本月创建的订单',
      },
    ]
  }

  /**
   * 获取状态分布
   */
  const getStatusDistribution = () => {
    return {
      draft: stats.value.draftCount,
      pending_approval: stats.value.pendingApprovalCount,
      approved: stats.value.approvedCount,
      rejected: stats.value.rejectedCount,
      cancelled: stats.value.cancelledCount,
    }
  }

  /**
   * 获取金额统计
   */
  const getAmountStats = () => {
    return {
      total: stats.value.totalAmount,
      avg: stats.value.avgAmount,
      max: stats.value.maxAmount,
      min: stats.value.minAmount,
    }
  }

  /**
   * 获取时间分布
   */
  const getTimeDistribution = () => {
    return {
      today: stats.value.todayCount,
      week: stats.value.weekCount,
      month: stats.value.monthCount,
    }
  }

  return {
    // 状态
    stats,
    loading,

    // 方法
    loadStats,
    resetStats,
    getStatCards,
    getStatusDistribution,
    getAmountStats,
    getTimeDistribution,
  }
}

export type UsePurchaseOrderStatsReturn = ReturnType<typeof usePurchaseOrderStats>
