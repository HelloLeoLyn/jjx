<template>
  <div class="alert-list">
    <!-- 统计卡片 -->
    <el-row :gutter="16" class="stats-row">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #f56c6c">
              <el-icon><Warning /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ alertStats.urgentCount || 0 }}</div>
              <div class="stat-label">紧急预警</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #e6a23c">
              <el-icon><Bell /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ alertStats.warningCount || 0 }}</div>
              <div class="stat-label">警告预警</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #409eff">
              <el-icon><InfoFilled /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">{{ alertStats.infoCount || 0 }}</div>
              <div class="stat-label">提示预警</div>
            </div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" style="background-color: #909399">
              <el-icon><Clock /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-value">
                {{ alertStats.unprocessedCount || 0 }}
              </div>
              <div class="stat-label">未处理预警</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="预警类型">
          <el-select
            v-model="queryParams.alertType"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="低库存" value="low_stock" />
            <el-option label="临期" value="expiring" />
            <el-option label="呆滞" value="obsolete" />
            <el-option label="超储" value="overstock" />
          </el-select>
        </el-form-item>
        <el-form-item label="预警级别">
          <el-select
            v-model="queryParams.alertLevel"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="紧急" value="urgent" />
            <el-option label="警告" value="warning" />
            <el-option label="提示" value="info" />
          </el-select>
        </el-form-item>
        <el-form-item label="处理状态">
          <el-select
            v-model="queryParams.processed"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="未处理" value="false" />
            <el-option label="已处理" value="true" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏 -->
    <el-card class="operation-card">
      <el-row :gutter="10">
        <el-col :span="1.5">
          <el-button type="primary" @click="handleCheckAlert">
            <el-icon><Refresh /></el-icon>执行预警检查
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button @click="handleBatchMarkRead">
            <el-icon><Check /></el-icon>批量标记已读
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button @click="handleExport">
            <el-icon><Download /></el-icon>导出
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 预警列表 -->
    <el-card class="table-card">
      <el-table
        v-loading="loading"
        :data="alertList"
        @selection-change="handleSelectionChange"
        border
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="预警类型" prop="alertTypeName" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getAlertTypeTag(row.alertType)" size="small">
              {{ row.alertTypeName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预警级别" prop="alertLevelName" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getAlertLevelTag(row.alertLevel)" size="small">
              {{ row.alertLevelName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="物料编码" prop="materialCode" width="120" />
        <el-table-column label="物料名称" prop="materialName" width="150" show-overflow-tooltip />
        <el-table-column label="仓库" prop="warehouseName" width="120" />
        <el-table-column label="批次号" prop="batchNo" width="120" />
        <el-table-column label="当前库存" prop="currentStock" width="100" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.currentStock) }}
          </template>
        </el-table-column>
        <el-table-column label="安全库存" prop="safeStock" width="100" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.safeStock) }}
          </template>
        </el-table-column>
        <el-table-column
          label="预警内容"
          prop="alertContent"
          min-width="200"
          show-overflow-tooltip
        />
        <el-table-column label="预警时间" prop="alertTime" width="150" align="center" />
        <el-table-column label="处理状态" prop="processed" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.processed ? 'success' : 'warning'" size="small">
              {{ row.processed ? '已处理' : '未处理' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.processed" link type="primary" @click="handleMarkRead(row)"
              >标记已读</el-button
            >
            <el-button v-if="!row.processed" link type="success" @click="handleProcess(row)"
              >处理</el-button
            >
            <el-button link type="info" @click="handleViewDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.current"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'AlertList',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Warning, Bell, InfoFilled, Clock, Refresh, Check, Download } from '@element-plus/icons-vue'
import { formatNumber, formatCurrency } from '@/utils/format'

// 查询参数
const queryParams = reactive({
  current: 1,
  pageSize: 10,
  alertType: '',
  alertLevel: '',
  processed: '',
})

// 响应式数据
const loading = ref(false)
const alertList = ref<any[]>([])
const total = ref(0)
const selectedAlerts = ref<any[]>([])
const alertStats = ref({
  urgentCount: 5,
  warningCount: 12,
  infoCount: 8,
  unprocessedCount: 15,
})

// 模拟数据
const mockAlertData = [
  {
    alertId: '1',
    alertType: 'low_stock',
    alertTypeName: '低库存',
    alertLevel: 'urgent',
    alertLevelName: '紧急',
    materialId: 'MAT001',
    materialCode: 'MAT001',
    materialName: '螺丝钉',
    warehouseId: '1',
    warehouseName: '原材料仓库',
    batchNo: 'BATCH20240301',
    currentStock: 50,
    safeStock: 200,
    alertContent: '库存低于安全库存，请及时采购',
    alertTime: '2024-03-28 10:00:00',
    processed: false,
    processedBy: '',
    processedTime: '',
    remark: '',
  },
  {
    alertId: '2',
    alertType: 'expiring',
    alertTypeName: '临期',
    alertLevel: 'warning',
    alertLevelName: '警告',
    materialId: 'MAT002',
    materialCode: 'MAT002',
    materialName: '润滑油',
    warehouseId: '2',
    warehouseName: '成品仓库',
    batchNo: 'BATCH20231201',
    currentStock: 100,
    safeStock: 50,
    alertContent: '物料即将过期，剩余30天',
    alertTime: '2024-03-28 09:30:00',
    processed: false,
    processedBy: '',
    processedTime: '',
    remark: '',
  },
  {
    alertId: '3',
    alertType: 'obsolete',
    alertTypeName: '呆滞',
    alertLevel: 'info',
    alertLevelName: '提示',
    materialId: 'MAT003',
    materialCode: 'MAT003',
    materialName: '旧型号零件',
    warehouseId: '3',
    warehouseName: '半成品仓库',
    batchNo: 'BATCH20230101',
    currentStock: 500,
    safeStock: 100,
    alertContent: '物料超过180天未使用',
    alertTime: '2024-03-28 09:00:00',
    processed: true,
    processedBy: '张三',
    processedTime: '2024-03-28 10:00:00',
    remark: '已安排处理',
  },
  {
    alertId: '4',
    alertType: 'overstock',
    alertTypeName: '超储',
    alertLevel: 'warning',
    alertLevelName: '警告',
    materialId: 'MAT004',
    materialCode: 'MAT004',
    materialName: '包装箱',
    warehouseId: '1',
    warehouseName: '原材料仓库',
    batchNo: 'BATCH20240201',
    currentStock: 1000,
    safeStock: 300,
    maxStock: 800,
    alertContent: '库存超过最高库存限制',
    alertTime: '2024-03-28 08:30:00',
    processed: false,
    processedBy: '',
    processedTime: '',
    remark: '',
  },
]

// 获取预警列表
const getList = async () => {
  loading.value = true
  try {
    // 模拟API调用
    await new Promise((resolve) => setTimeout(resolve, 500))

    // 过滤数据
    let filteredData = [...mockAlertData]

    if (queryParams.alertType) {
      filteredData = filteredData.filter((item) => item.alertType === queryParams.alertType)
    }

    if (queryParams.alertLevel) {
      filteredData = filteredData.filter((item) => item.alertLevel === queryParams.alertLevel)
    }

    if (queryParams.processed !== '') {
      const processed = queryParams.processed === 'true'
      filteredData = filteredData.filter((item) => item.processed === processed)
    }

    // 模拟分页
    const start = (queryParams.current - 1) * queryParams.pageSize
    const end = start + queryParams.pageSize
    alertList.value = filteredData.slice(start, end)
    total.value = filteredData.length
  } catch (error) {
    console.error('获取预警列表失败:', error)
    ElMessage.error('获取预警列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleQuery = () => {
  queryParams.current = 1
  getList()
}

// 重置
const handleReset = () => {
  queryParams.current = 1
  queryParams.alertType = ''
  queryParams.alertLevel = ''
  queryParams.processed = ''
  getList()
}

// 多选框选中
const handleSelectionChange = (selection: any[]) => {
  selectedAlerts.value = selection
}

// 执行预警检查
const handleCheckAlert = () => {
  ElMessageBox.confirm('确认执行预警检查吗？', '提示', { type: 'warning' })
    .then(() => {
      // TODO: 调用预警检查API
      ElMessage.success('预警检查执行成功')
      getList()
    })
    .catch(() => {})
}

// 批量标记已读
const handleBatchMarkRead = () => {
  if (selectedAlerts.value.length === 0) {
    ElMessage.warning('请选择要标记已读的预警')
    return
  }

  ElMessageBox.confirm('确认批量标记已读吗？', '提示', { type: 'warning' })
    .then(() => {
      // TODO: 调用批量标记已读API
      ElMessage.success('批量标记已读成功')
      getList()
    })
    .catch(() => {})
}

// 导出
const handleExport = () => {
  ElMessage.info('导出功能开发中')
}

// 标记已读
const handleMarkRead = (row: any) => {
  // TODO: 调用标记已读API
  row.processed = true
  row.processedBy = '当前用户'
  row.processedTime = new Date().toLocaleString()
  ElMessage.success('标记已读成功')
}

// 处理预警
const handleProcess = (row: any) => {
  ElMessageBox.prompt('请输入处理备注', '处理预警', {
    confirmButtonText: '确认处理',
    cancelButtonText: '取消',
    inputPlaceholder: '请输入处理备注',
  })
    .then(({ value }) => {
      // TODO: 调用处理预警API
      row.processed = true
      row.processedBy = '当前用户'
      row.processedTime = new Date().toLocaleString()
      row.remark = value
      ElMessage.success('处理成功')
    })
    .catch(() => {})
}

// 查看详情
const handleViewDetail = (row: any) => {
  ElMessage.info(`查看预警详情: ${row.materialName} - ${row.alertContent}`)
}

// 获取预警类型标签样式
const getAlertTypeTag = (type: string): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const typeMap: Record<string, 'success' | 'warning' | 'info' | 'danger' | undefined> = {
    low_stock: 'danger',
    expiring: 'warning',
    obsolete: 'info',
    overstock: 'success',
  }
  return typeMap[type]
}

// 获取预警级别标签样式
const getAlertLevelTag = (level: string): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const levelMap: Record<string, 'success' | 'warning' | 'info' | 'danger' | undefined> = {
    urgent: 'danger',
    warning: 'warning',
    info: 'info',
  }
  return levelMap[level]
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.alert-list {
  padding: 20px;
}

.stats-row {
  margin-bottom: 16px;
}

.stat-card {
  height: 100px;
}

.stat-content {
  display: flex;
  align-items: center;
}

.stat-icon {
  width: 48px;
  height: 48px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
}

.stat-icon .el-icon {
  font-size: 24px;
  color: white;
}

.stat-info {
  flex: 1;
}

.stat-value {
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 14px;
  color: #666;
}

.search-card,
.operation-card,
.table-card {
  margin-bottom: 16px;
}
</style>
