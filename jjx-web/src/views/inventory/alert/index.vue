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
            style="width: 140px"
          >
            <el-option
              v-for="opt in alertTypeOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="预警级别">
          <el-select
            v-model="queryParams.alertLevel"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option
              v-for="opt in alertLevelOptions"
              :key="opt.value"
              :label="opt.label"
              :value="opt.value"
            />
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
          <el-button type="primary" v-hasPermi="['inventory:alert:edit']" @click="handleCheckAlert">
            <el-icon><Refresh /></el-icon>执行预警检查
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button v-hasPermi="['inventory:alert:edit']" @click="handleBatchMarkRead">
            <el-icon><Check /></el-icon>批量标记已读
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="warning"
            v-hasPermi="['purchase:plan:add']"
            :disabled="selectedAlerts.length === 0"
            :loading="toPurchaseLoading"
            @click="handleBatchToPurchase"
          >
            <el-icon><ShoppingCart /></el-icon>转采购
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
        <el-table-column label="预警类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="getAlertTypeTag(row.alertType)" size="small">
              {{ row.alertTypeName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预警级别" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getAlertLevelTag(row.alertLevel)" size="small">
              {{ row.alertLevelName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="物料编码" prop="materialCode" width="120" />
        <el-table-column label="物料名称" prop="materialName" width="150" show-overflow-tooltip />
        <el-table-column label="关联订单" width="150" align="center">
          <template #default="{ row }">
            <span v-if="row.orderNo">{{ row.orderNo }}</span>
            <span v-else-if="row.involvedOrders" style="color:#e6a23c">涉及 {{ row.involvedOrders }} 个订单</span>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="当前库存" prop="currentStock" width="100" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.currentStock) }}
          </template>
        </el-table-column>
        <el-table-column label="预警内容"
          prop="alertMessage"
          min-width="220"
          show-overflow-tooltip
        />
        <el-table-column label="预警时间" prop="alertTime" width="150" align="center" />
        <el-table-column label="处理状态" prop="status" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)" size="small">
              {{ getStatusName(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="处理人" width="90" align="center">
          <template #default="{ row }">
            <span>{{ row.processedBy || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="处理时间" width="150" align="center">
          <template #default="{ row }">
            <span>{{ row.processedTime || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="关联采购订单" width="140" align="center">
          <template #default="{ row }">
            <el-link
              v-if="extractPurchaseOrderNo(row.processRemark)"
              type="primary"
              :underline="false"
              @click="goPurchaseOrders"
            >{{ extractPurchaseOrderNo(row.processRemark) }}</el-link>
            <span v-else style="color:#c0c4cc">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="170" fixed="right">
          <template #default="{ row }">
            <!-- DEV-995：标记已读后(status=1)仍可继续处理/转采购，已处理(2)才是终态 -->
            <el-button v-if="row.status === 0" v-hasPermi="['inventory:alert:edit']" link type="primary" @click="handleMarkRead(row)"
              >标记已读</el-button
            >
            <el-button v-if="row.status === 0 || row.status === 1" v-hasPermi="['inventory:alert:edit']" link type="success" @click="handleProcess(row)"
              >处理</el-button
            >
            <el-button v-if="row.status === 0 || row.status === 1" link type="warning" @click="goPurchasePlan(row)"
              >去采购计划</el-button
            >
            <el-button
              v-if="row.status === 0 || row.status === 1"
              v-hasPermi="['purchase:plan:add']"
              link
              type="success"
              :loading="row._toPurchaseLoading"
              @click="handleToPurchase(row)"
              >转采购</el-button
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
import { Warning, Bell, InfoFilled, Clock, Refresh, Check, Download, ShoppingCart } from '@element-plus/icons-vue'
import { formatNumber } from '@/utils/format'
import { alertApi } from '@/api/inventory/alert'
import { AlertEnum } from '@/enums/inventory/AlertEnum'
import { useUserStore } from '@/store/modules/user'
import { useRouter } from 'vue-router'

// 解析处理备注里的关联采购订单号（如"生成采购订单 PO-xxx"）
function extractPurchaseOrderNo(remark: string | null | undefined): string {
  if (!remark) return ''
  const m = String(remark).match(/生成采购订单\s*([A-Za-z0-9\-]+)/)
  return m ? m[1] : ''
}

const router = useRouter()

// 去采购计划（未处理预警的处置入口，DEV-998：携带物料/预警溯源参数）
function goPurchasePlan(row: any) {
  const query: Record<string, string> = {}
  if (row?.materialId) query.materialId = String(row.materialId)
  if (row?.alertId) query.alertId = String(row.alertId)
  if (row?.materialCode) query.materialCode = row.materialCode
  router.push({ path: '/purchase/plan', query })
}

// 去采购订单列表（查看关联订单）
function goPurchaseOrders() {
  router.push('/purchase/order')
}

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
  urgentCount: 0,
  warningCount: 0,
  infoCount: 0,
  unprocessedCount: 0,
})

// 预警类型/级别选项（真实枚举，8-04 接真实接口）
const alertTypeOptions = AlertEnum.type.items
const alertLevelOptions = AlertEnum.level.items

// 获取预警列表（真实接口）
const getList = async () => {
  loading.value = true
  try {
    const res: any = await alertApi.list(queryParams)
    const data = res.data || {}
    const records = data.records || []
    alertList.value = records.map((item: any) => ({
      ...item,
      alertTypeName: AlertEnum.type.getLabel(item.alertType),
      alertLevelName: AlertEnum.level.getLabel(item.alertLevel),
    }))
    total.value = data.total || 0
  } catch (error) {
    console.error('获取预警列表失败:', error)
    ElMessage.error('获取预警列表失败')
  } finally {
    loading.value = false
  }
}

// 获取统计卡片（真实接口：未处理预警按级别统计）
const getStats = async () => {
  try {
    const res: any = await alertApi.unprocessed()
    const list: any[] = res.data || []
    alertStats.value = {
      urgentCount: list.filter((i) => i.alertLevel === 'urgent').length,
      warningCount: list.filter((i) => i.alertLevel === 'warning').length,
      infoCount: list.filter((i) => i.alertLevel === 'info').length,
      unprocessedCount: list.length,
    }
  } catch (error) {
    console.error('获取未处理预警失败:', error)
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
    .then(async () => {
      await alertApi.executeCheck()
      ElMessage.success('预警检查执行成功')
      getList()
      getStats()
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
    .then(async () => {
      await alertApi.batchMarkRead(selectedAlerts.value.map((i: any) => i.alertId))
      ElMessage.success('批量标记已读成功')
      getList()
      getStats()
    })
    .catch(() => {})
}

// 转采购 loading（批量）
const toPurchaseLoading = ref(false)

// 单条预警转采购（DEV-996：一键生成采购计划单 + 自动回写预警）
const handleToPurchase = async (row: any) => {
  try {
    row._toPurchaseLoading = true
    const res: any = await alertApi.createPlanFromAlerts([row.alertId])
    if (res?.code === 200) {
      ElMessage.success(`已生成采购计划单（计划单ID ${res.data}），预警已处理`)
      getList()
      getStats()
    } else {
      ElMessage.error(res?.msg || '转采购失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '转采购失败')
  } finally {
    row._toPurchaseLoading = false
  }
}

// 批量转采购（选中多条预警）
const handleBatchToPurchase = async () => {
  if (selectedAlerts.value.length === 0) {
    ElMessage.warning('请先选择要转采购的预警')
    return
  }
  try {
    toPurchaseLoading.value = true
    const ids = selectedAlerts.value.map((i: any) => i.alertId)
    const res: any = await alertApi.createPlanFromAlerts(ids)
    if (res?.code === 200) {
      ElMessage.success(`已生成采购计划单（计划单ID ${res.data}），${ids.length} 条预警已处理`)
      getList()
      getStats()
    } else {
      ElMessage.error(res?.msg || '转采购失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '转采购失败')
  } finally {
    toPurchaseLoading.value = false
  }
}

// 导出（预留：接后端导出接口）
const handleExport = () => {
  ElMessage.info('导出功能开发中')
}

// 标记已读
const handleMarkRead = async (row: any) => {
  await alertApi.markRead(row.alertId)
  row.status = 1
  ElMessage.success('标记已读成功')
  getStats()
}

// 处理预警
const handleProcess = (row: any) => {
  const userStore = useUserStore()
  ElMessageBox.prompt('请输入处理备注', '处理预警', {
    confirmButtonText: '确认处理',
    cancelButtonText: '取消',
    inputPlaceholder: '请输入处理备注',
  })
    .then(async ({ value }) => {
      await alertApi.process(row.alertId, userStore.userName || '当前用户', value)
      row.status = 2
      row.processedBy = userStore.userName || '当前用户'
      row.processedTime = new Date().toLocaleString()
      row.processRemark = value
      ElMessage.success('处理成功')
      getList()
      getStats()
    })
    .catch(() => {})
}

// 查看详情
const handleViewDetail = (row: any) => {
  ElMessageBox.alert(
    `物料：${row.materialName || '-'}${row.orderNo ? `\n关联订单：${row.orderNo}` : ''}\n当前库存：${formatNumber(row.currentStock)}\n建议：${row.suggestion || '-'}\n预警内容：${row.alertMessage || '-'}`,
    `预警详情 - ${row.materialName || row.materialCode || ''}`,
    { confirmButtonText: '关闭' }
  )
}

// 获取预警类型标签样式
const getAlertTypeTag = (type: string): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const t = AlertEnum.type.getTagProps(type).type
  return t === 'primary' ? 'info' : t
}

// 获取预警级别标签样式
const getAlertLevelTag = (level: string): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const t = AlertEnum.level.getTagProps(level).type
  return t === 'primary' ? 'info' : t
}

// 处理状态：0未处理 1已读 2已处理 3已忽略
const getStatusName = (status: number): string => {
  const map: Record<number, string> = { 0: '未处理', 1: '已读', 2: '已处理', 3: '已忽略' }
  return map[status] ?? '未知'
}

const getStatusTag = (status: number): 'success' | 'warning' | 'info' | 'danger' => {
  const map: Record<number, 'success' | 'warning' | 'info' | 'danger'> = {
    0: 'warning',
    1: 'info',
    2: 'success',
    3: 'danger',
  }
  return map[status] ?? 'info'
}

onMounted(() => {
  getList()
  getStats()
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
