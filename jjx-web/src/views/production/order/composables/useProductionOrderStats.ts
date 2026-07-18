import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getProductionOrderStats } from '@/api/production/order'
import type { ProductionOrderStats, OrderType } from '@/types/production/order'

/**
 * 生产订单统计数据管理Composable
 */
export function useProductionOrderStats() {
  // 响应式数据
  const stats = ref<ProductionOrderStats>({
    totalCount: 0,
    planCount: 0,
    workOrderCount: 0,
    draftCount: 0,
    pendingApprovalCount: 0,
    approvedCount: 0,
    scheduledCount: 0,
    inProgressCount: 0,
    completedCount: 0,
    cancelledCount: 0,
    totalPlannedQuantity: 0,
    totalCompletedQuantity: 0,
    overallCompletionRate: 0,
    todayCount: 0,
    weekCount: 0,
    monthCount: 0,
    overdueCount: 0,
    qualityPassRate: 0,
    onTimeCompletionRate: 0,
    totalMaterialCost: 0,
    totalLaborCost: 0,
    totalCost: 0,
    avgCostPerUnit: 0,
  })

  const loading = ref(false)

  /**
   * 加载统计数据
   */
  const loadStats = async (orderType?: OrderType) => {
    loading.value = true

    try {
      const statsParams = {
        orderType,
      }

      const response = await getProductionOrderStats(statsParams)

      if (response && response.data) {
        stats.value = response.data
      } else {
        // 如果API返回空数据，使用默认值
        resetStats()
      }
    } catch (error) {
      console.error('加载生产订单统计失败:', error)
      // 统计失败不影响主功能，使用默认值
      resetStats()
    } finally {
      loading.value = false
    }
  }

  /**
   * 重置统计数据
   */
  const resetStats = () => {
    stats.value = {
      totalCount: 0,
      planCount: 0,
      workOrderCount: 0,
      draftCount: 0,
      pendingApprovalCount: 0,
      approvedCount: 0,
      scheduledCount: 0,
      inProgressCount: 0,
      completedCount: 0,
      cancelledCount: 0,
      totalPlannedQuantity: 0,
      totalCompletedQuantity: 0,
      overallCompletionRate: 0,
      todayCount: 0,
      weekCount: 0,
      monthCount: 0,
      overdueCount: 0,
      qualityPassRate: 0,
      onTimeCompletionRate: 0,
      totalMaterialCost: 0,
      totalLaborCost: 0,
      totalCost: 0,
      avgCostPerUnit: 0,
    }
  }

  /**
   * 获取统计卡片数据
   */
  const getStatCards = () => {
    return [
      {
        title: '总订单数',
        value: stats.value.totalCount || 0,
        icon: 'Document',
        color: '#409eff',
        description: '全部生产订单数量',
      },
      {
        title: '生产计划',
        value: stats.value.planCount || 0,
        icon: 'Calendar',
        color: '#67c23a',
        description: '待审批和已批准的计划',
      },
      {
        title: '生产工单',
        value: stats.value.workOrderCount || 0,
        icon: 'Tools',
        color: '#e6a23c',
        description: '执行中的工单',
      },
      {
        title: '逾期订单',
        value: stats.value.overdueCount || 0,
        icon: 'Clock',
        color: '#f56c6c',
        description: '已超过计划完成日期的订单',
      },
    ]
  }

  /**
   * 获取进度统计
   */
  const getProgressStats = () => {
    return {
      completionRate: stats.value.overallCompletionRate || 0,
      qualityPassRate: stats.value.qualityPassRate || 0,
      onTimeCompletionRate: stats.value.onTimeCompletionRate || 0,
      totalPlanned: stats.value.totalPlannedQuantity || 0,
      totalCompleted: stats.value.totalCompletedQuantity || 0,
    }
  }

  /**
   * 获取成本统计
   */
  const getCostStats = () => {
    return {
      totalMaterialCost: stats.value.totalMaterialCost || 0,
      totalLaborCost: stats.value.totalLaborCost || 0,
      totalCost: stats.value.totalCost || 0,
      avgCostPerUnit: stats.value.avgCostPerUnit || 0,
    }
  }

  /**
   * 获取状态分布
   */
  const getStatusDistribution = () => {
    return {
      draft: stats.value.draftCount || 0,
      pendingApproval: stats.value.pendingApprovalCount || 0,
      approved: stats.value.approvedCount || 0,
      scheduled: stats.value.scheduledCount || 0,
      inProgress: stats.value.inProgressCount || 0,
      completed: stats.value.completedCount || 0,
      cancelled: stats.value.cancelledCount || 0,
    }
  }

  /**
   * 获取时间分布
   */
  const getTimeDistribution = () => {
    return {
      today: stats.value.todayCount || 0,
      week: stats.value.weekCount || 0,
      month: stats.value.monthCount || 0,
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
    getProgressStats,
    getCostStats,
    getStatusDistribution,
    getTimeDistribution,
  }
}

export type UseProductionOrderStatsReturn = ReturnType<typeof useProductionOrderStats>
