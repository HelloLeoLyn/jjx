<template>
  <div class="production-equipment">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">设备管理</h1>
      <div class="page-actions">
        <el-button type="primary" icon="Plus" @click="handleCreate">新增设备</el-button>
        <el-button icon="Tools" @click="handleMaintenance">维护计划</el-button>
        <el-button icon="Histogram" @click="handleAnalysis">效率分析</el-button>
      </div>
    </div>

    <!-- 设备状态概览 -->
    <div class="equipment-overview">
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #67c23a">
                <el-icon><Monitor /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">48</div>
                <div class="stat-label">运行中</div>
                <div class="stat-subtext">85%设备在线</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #e6a23c">
                <el-icon><Clock /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">5</div>
                <div class="stat-label">待机中</div>
                <div class="stat-subtext">9%设备待机</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #f56c6c">
                <el-icon><Warning /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">3</div>
                <div class="stat-label">故障中</div>
                <div class="stat-subtext">5%设备故障</div>
              </div>
            </div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card" shadow="hover">
            <div class="stat-content">
              <div class="stat-icon" style="background-color: #409eff">
                <el-icon><TrendCharts /></el-icon>
              </div>
              <div class="stat-info">
                <div class="stat-value">92.5%</div>
                <div class="stat-label">综合利用率</div>
                <div class="stat-subtext">+2.3%较上月</div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 设备列表 -->
    <div class="equipment-list">
      <el-card class="section-card" shadow="never">
        <template #header>
          <div class="card-header">
            <span class="card-title">设备台账</span>
            <div class="card-actions">
              <el-input
                v-model="searchKeyword"
                placeholder="搜索设备编号/名称"
                style="width: 200px; margin-right: 10px"
                clearable
              />
              <el-select v-model="filterStatus" placeholder="设备状态" clearable>
                <el-option label="运行中" :value="1" />
                <el-option label="待机中" :value="0" />
                <el-option label="故障中" :value="3" />
                <el-option label="维护中" :value="2" />
              </el-select>
            </div>
          </div>
        </template>

        <el-table :data="filteredEquipment" style="width: 100%">
          <el-table-column prop="equipmentNo" label="设备编号" width="150" />
          <el-table-column prop="equipmentName" label="设备名称" width="200" />
          <el-table-column prop="equipmentType" label="设备类型" width="120" />
          <el-table-column prop="model" label="型号规格" width="150" />
          <el-table-column prop="department" label="所属部门" width="120" />
          <el-table-column prop="location" label="安装位置" width="150" />
          <el-table-column prop="status" label="运行状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)" size="small">
                {{ getStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="utilization" label="利用率" width="100">
            <template #default="{ row }">
              <el-progress
                :percentage="row.utilization"
                :stroke-width="10"
                :show-text="false"
                :color="getUtilizationColor(row.utilization)"
              />
              <span style="margin-left: 8px; font-size: 12px">{{ row.utilization }}%</span>
            </template>
          </el-table-column>
          <el-table-column prop="lastMaintenance" label="上次维护" width="120" />
          <el-table-column prop="nextMaintenance" label="下次维护" width="120" />
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" @click="viewEquipmentDetail(row)"> 详情 </el-button>
              <el-button link size="small" @click="editEquipment(row)"> 编辑 </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 分页 -->
        <div class="pagination">
          <el-pagination
            v-model:current-page="pagination.current"
            v-model:page-size="pagination.size"
            :total="pagination.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handleCurrentChange"
          />
        </div>
      </el-card>
    </div>

    <!-- 设备监控 -->
    <div class="equipment-monitoring">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-card class="section-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span class="card-title">实时监控</span>
                <el-button link @click="refreshMonitoring">刷新</el-button>
              </div>
            </template>

            <div class="monitoring-placeholder">
              <el-empty description="设备实时监控图表待开发" />
            </div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card class="section-card" shadow="never">
            <template #header>
              <div class="card-header">
                <span class="card-title">维护提醒</span>
                <el-badge :value="maintenanceAlerts.length" class="badge">
                  <el-button link @click="viewAllAlerts">查看全部</el-button>
                </el-badge>
              </div>
            </template>

            <div class="maintenance-alerts">
              <div
                v-for="alert in maintenanceAlerts"
                :key="alert.id"
                class="alert-item"
                :class="{ 'alert-urgent': alert.priority === 'high' }"
              >
                <div class="alert-icon">
                  <el-icon v-if="alert.priority === 'high'"><WarningFilled /></el-icon>
                  <el-icon v-else><InfoFilled /></el-icon>
                </div>
                <div class="alert-content">
                  <div class="alert-title">{{ alert.title }}</div>
                  <div class="alert-desc">{{ alert.description }}</div>
                  <div class="alert-time">{{ alert.time }}</div>
                </div>
                <div class="alert-actions">
                  <el-button link size="small" @click="handleAlertAction(alert)"> 处理 </el-button>
                </div>
              </div>
              <div v-if="maintenanceAlerts.length === 0" class="no-alerts">
                <el-empty description="暂无维护提醒" />
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import {
  Monitor,
  Clock,
  Warning,
  TrendCharts,
  Plus,
  Tools,
  Histogram,
  WarningFilled,
  InfoFilled,
} from '@element-plus/icons-vue'

interface Equipment {
  id: string
  equipmentNo: string
  equipmentName: string
  equipmentType: string
  model: string
  department: string
  location: string
  status: number
  utilization: number
  lastMaintenance: string
  nextMaintenance: string
}

interface MaintenanceAlert {
  id: string
  title: string
  description: string
  time: string
  priority: 'high' | 'normal'
}

// 响应式数据
const searchKeyword = ref('')
const filterStatus = ref('')

const pagination = reactive({
  current: 1,
  size: 10,
  total: 0,
})

const equipmentList = ref<Equipment[]>([
  {
    id: '1',
    equipmentNo: 'EQ-001',
    equipmentName: '丝网印刷机',
    equipmentType: '印刷设备',
    model: 'SP-2000',
    department: '印刷车间',
    location: 'A区-01号',
    status: 1,
    utilization: 92,
    lastMaintenance: '2024-03-15',
    nextMaintenance: '2024-05-15',
  },
  {
    id: '2',
    equipmentNo: 'EQ-002',
    equipmentName: '模切机',
    equipmentType: '成型设备',
    model: 'MC-1500',
    department: '成型车间',
    location: 'B区-03号',
    status: 1,
    utilization: 88,
    lastMaintenance: '2024-03-20',
    nextMaintenance: '2024-05-20',
  },
  {
    id: '3',
    equipmentNo: 'EQ-003',
    equipmentName: '热压贴合机',
    equipmentType: '贴合设备',
    model: 'HP-1800',
    department: '贴合车间',
    location: 'C区-02号',
    status: 0,
    utilization: 65,
    lastMaintenance: '2024-03-10',
    nextMaintenance: '2024-05-10',
  },
  {
    id: '4',
    equipmentNo: 'EQ-004',
    equipmentName: '测试仪',
    equipmentType: '测试设备',
    model: 'TEST-500',
    department: '测试车间',
    location: 'D区-01号',
    status: 3,
    utilization: 0,
    lastMaintenance: '2024-02-28',
    nextMaintenance: '2024-04-28',
  },
  {
    id: '5',
    equipmentNo: 'EQ-005',
    equipmentName: '包装机',
    equipmentType: '包装设备',
    model: 'PK-1000',
    department: '包装车间',
    location: 'E区-01号',
    status: 2,
    utilization: 0,
    lastMaintenance: '2024-04-05',
    nextMaintenance: '2024-06-05',
  },
  {
    id: '6',
    equipmentNo: 'EQ-006',
    equipmentName: '激光切割机',
    equipmentType: '切割设备',
    model: 'LC-3000',
    department: '切割车间',
    location: 'A区-02号',
    status: 1,
    utilization: 95,
    lastMaintenance: '2024-03-25',
    nextMaintenance: '2024-05-25',
  },
])

const maintenanceAlerts = ref<MaintenanceAlert[]>([
  {
    id: '1',
    title: '设备定期维护提醒',
    description: '丝网印刷机(EQ-001)需要定期维护',
    time: '2024-04-15 09:30',
    priority: 'high',
  },
  {
    id: '2',
    title: '设备保养到期',
    description: '模切机(EQ-002)保养周期已到',
    time: '2024-04-12 14:20',
    priority: 'normal',
  },
  {
    id: '3',
    title: '设备故障报警',
    description: '测试仪(EQ-004)出现故障需要检修',
    time: '2024-04-10 11:15',
    priority: 'high',
  },
])

// 计算属性
const filteredEquipment = computed(() => {
  let filtered = equipmentList.value

  // 按关键词搜索
  if (searchKeyword.value) {
    const keyword = searchKeyword.value.toLowerCase()
    filtered = filtered.filter(
      (item) =>
        item.equipmentNo.toLowerCase().includes(keyword) ||
        item.equipmentName.toLowerCase().includes(keyword) ||
        item.model.toLowerCase().includes(keyword)
    )
  }

  // 按状态筛选
  if (filterStatus.value) {
    filtered = filtered.filter((item) => item.status === Number(filterStatus.value))
  }

  // 分页
  const start = (pagination.current - 1) * pagination.size
  const end = start + pagination.size
  pagination.total = filtered.length

  return filtered.slice(start, end)
})

// 生命周期
onMounted(() => {
  pagination.total = equipmentList.value.length
})

// 方法
const getStatusType = (status: string): 'success' | 'warning' | 'danger' | 'info' => {
  const map: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
    running: 'success',
    idle: 'warning',
    fault: 'danger',
    maintenance: 'info',
  }
  return map[status] || 'info'
}

const getStatusText = (status: string) => {
  const map: Record<string, string> = {
    running: '运行中',
    idle: '待机中',
    fault: '故障中',
    maintenance: '维护中',
  }
  return map[status] || status
}

const getUtilizationColor = (utilization: number) => {
  if (utilization >= 90) return '#67c23a'
  if (utilization >= 70) return '#e6a23c'
  return '#f56c6c'
}

// 事件处理
const handleCreate = () => {
  console.log('新增设备')
  // TODO: 跳转到新增设备页面
}

const handleMaintenance = () => {
  console.log('维护计划')
  // TODO: 跳转到维护计划页面
}

const handleAnalysis = () => {
  console.log('效率分析')
  // TODO: 跳转到效率分析页面
}

const viewEquipmentDetail = (equipment: Equipment) => {
  console.log('查看设备详情:', equipment)
  // TODO: 跳转到设备详情页面
}

const editEquipment = (equipment: Equipment) => {
  console.log('编辑设备:', equipment)
  // TODO: 跳转到编辑设备页面
}

const refreshMonitoring = () => {
  console.log('刷新监控数据')
  // TODO: 刷新监控数据
}

const viewAllAlerts = () => {
  console.log('查看全部提醒')
  // TODO: 跳转到提醒列表页面
}

const handleAlertAction = (alert: MaintenanceAlert) => {
  console.log('处理提醒:', alert)
  // TODO: 处理提醒
}

const handleSizeChange = (size: number) => {
  pagination.size = size
}

const handleCurrentChange = (current: number) => {
  pagination.current = current
}
</script>

<style scoped>
.production-equipment {
  padding: 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.equipment-overview {
  margin-bottom: 24px;
}

.stat-card {
  border-radius: 8px;
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
  font-weight: 600;
  color: #303133;
  line-height: 1.2;
}

.stat-label {
  font-size: 14px;
  color: #909399;
  margin-top: 4px;
}

.stat-subtext {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 2px;
}

.equipment-list {
  margin-bottom: 20px;
}

.section-card {
  border-radius: 8px;
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}
.card-actions {
  display: flex;
  align-items: center;
}
.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
.equipment-monitoring .monitoring-placeholder {
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.maintenance-alerts .alert-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
}
.maintenance-alerts .alert-item:last-child {
  border-bottom: none;
}
.alert-item .alert-icon {
  margin-right: 12px;
}
.alert-item .alert-content {
  flex: 1;
}
.alert-item .alert-title {
  font-size: 16px;
  font-weight: 500;
  color: #303133;
}
.alert-item .alert-desc {
  font-size: 14px;
  color: #606266;
  margin-top: 4px;
}
.alert-item .alert-time {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
.alert-item .alert-actions {
  margin-left: 12px;
}
.alert-urgent {
  background-color: #fff0f0;
}
.no-alerts {
  padding: 40px;
}
</style>
