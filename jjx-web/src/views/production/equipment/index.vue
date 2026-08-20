<template>
  <div class="production-equipment">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">设备管理</h1>
      <div class="page-actions">
        <el-button type="primary" icon="Plus" v-hasPermi="['production:equipment:add']" @click="handleCreate">新增设备</el-button>
        <el-button icon="Tools" v-hasPermi="['production:equipment:edit']" @click="handleMaintenance">维护计划</el-button>
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
                <div class="stat-value">{{ stats.running }}</div>
                <div class="stat-label">运行中</div>
                <div class="stat-subtext">{{ total > 0 ? Math.round((stats.running / total) * 100) : 0 }}%设备在线</div>
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
                <div class="stat-value">{{ stats.idle }}</div>
                <div class="stat-label">待机中</div>
                <div class="stat-subtext">{{ total > 0 ? Math.round((stats.idle / total) * 100) : 0 }}%设备待机</div>
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
                <div class="stat-value">{{ stats.fault }}</div>
                <div class="stat-label">故障中</div>
                <div class="stat-subtext">{{ total > 0 ? Math.round((stats.fault / total) * 100) : 0 }}%设备故障</div>
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
                <div class="stat-value">{{ avgUtilization }}%</div>
                <div class="stat-label">综合利用率</div>
                <div class="stat-subtext">全部设备平均</div>
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
                v-model="query.equipmentNo"
                placeholder="搜索设备编号/名称"
                style="width: 200px; margin-right: 10px"
                clearable
                @input="handleSearch"
              />
              <el-select v-model="query.status" placeholder="设备状态" clearable style="width: 130px" @change="handleSearch">
                <el-option label="运行中" :value="1" />
                <el-option label="待机中" :value="0" />
                <el-option label="故障中" :value="3" />
                <el-option label="维护中" :value="2" />
              </el-select>
            </div>
          </div>
        </template>

        <el-table v-loading="loading" :data="equipmentList" style="width: 100%">
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
                :percentage="Number(row.utilization) || 0"
                :stroke-width="10"
                :show-text="false"
                :color="getUtilizationColor(Number(row.utilization) || 0)"
              />
              <span style="margin-left: 8px; font-size: 12px">{{ row.utilization ?? 0 }}%</span>
            </template>
          </el-table-column>
          <el-table-column label="上次维护" width="120">
            <template #default="{ row }">{{ formatDate(row.lastMaintenance) }}</template>
          </el-table-column>
          <el-table-column label="下次维护" width="120">
            <template #default="{ row }">{{ formatDate(row.nextMaintenance) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="180" fixed="right">
            <template #default="{ row }">
              <el-button link size="small" @click="viewEquipmentDetail(row)"> 详情 </el-button>
              <el-button link size="small" v-hasPermi="['production:equipment:edit']" @click="editEquipment(row)"> 编辑 </el-button>
              <el-button link size="small" type="danger" v-hasPermi="['production:equipment:delete']" @click="removeEquipment(row)"> 删除 </el-button>
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
            @size-change="loadList"
            @current-change="loadList"
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
                <el-button link @click="loadList">刷新</el-button>
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
                  <el-button link @click="loadList">刷新</el-button>
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
              </div>
              <div v-if="maintenanceAlerts.length === 0" class="no-alerts">
                <el-empty description="暂无维护提醒" />
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="form.equipmentId ? '编辑设备' : '新增设备'" width="560px" append-to-body>
      <el-form ref="formRef" :model="form" label-width="100px" :rules="formRules">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="设备编号" prop="equipmentNo">
              <el-input v-model="form.equipmentNo" placeholder="如 EQ-001" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备名称" prop="equipmentName">
              <el-input v-model="form.equipmentName" placeholder="如 丝网印刷机" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备类型">
              <el-input v-model="form.equipmentType" placeholder="如 印刷设备" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="型号规格">
              <el-input v-model="form.model" placeholder="如 SP-2000" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属部门">
              <el-input v-model="form.department" placeholder="如 印刷车间" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="安装位置">
              <el-input v-model="form.location" placeholder="如 A区-01号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="运行状态">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="运行中" :value="1" />
                <el-option label="待机中" :value="0" />
                <el-option label="故障中" :value="3" />
                <el-option label="维护中" :value="2" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="利用率(%)">
              <el-input-number v-model="form.utilization" :min="0" :max="100" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="上次维护">
              <el-date-picker v-model="form.lastMaintenance" type="date" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="下次维护">
              <el-date-picker v-model="form.nextMaintenance" type="date" value-format="YYYY-MM-DD HH:mm:ss" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="设备详情" width="560px" append-to-body>
      <el-descriptions v-if="currentDetail" :column="2" border>
        <el-descriptions-item label="设备编号">{{ currentDetail.equipmentNo }}</el-descriptions-item>
        <el-descriptions-item label="设备名称">{{ currentDetail.equipmentName }}</el-descriptions-item>
        <el-descriptions-item label="设备类型">{{ currentDetail.equipmentType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="型号规格">{{ currentDetail.model || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所属部门">{{ currentDetail.department || '-' }}</el-descriptions-item>
        <el-descriptions-item label="安装位置">{{ currentDetail.location || '-' }}</el-descriptions-item>
        <el-descriptions-item label="运行状态">
          <el-tag :type="getStatusType(currentDetail.status)" size="small">{{ getStatusText(currentDetail.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="利用率">{{ currentDetail.utilization ?? 0 }}%</el-descriptions-item>
        <el-descriptions-item label="上次维护">{{ formatDate(currentDetail.lastMaintenance) }}</el-descriptions-item>
        <el-descriptions-item label="下次维护">{{ formatDate(currentDetail.nextMaintenance) }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ currentDetail.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Monitor,
  Clock,
  Warning,
  TrendCharts,
  Plus,
  Tools,
  WarningFilled,
  InfoFilled,
} from '@element-plus/icons-vue'
import {
  getEquipmentPage,
  getEquipmentList,
  createEquipment,
  updateEquipment,
  deleteEquipment,
  type EquipmentQuery,
  type ProductionEquipment,
} from '@/api/production/equipment'

interface MaintenanceAlert {
  id: string
  title: string
  description: string
  time: string
  priority: 'high' | 'normal'
}

// 响应式数据
const loading = ref(false)
const saving = ref(false)
const query = reactive<EquipmentQuery>({})
const pagination = reactive({
  current: 1,
  size: 10,
  total: 0,
})
const equipmentList = ref<ProductionEquipment[]>([])

// 统计
const stats = reactive({ running: 0, idle: 0, fault: 0, maintenance: 0 })
const avgUtilization = ref(0)
const total = computed(() => stats.running + stats.idle + stats.fault + stats.maintenance)

// 维护提醒（从设备列表计算：故障中/维护中优先，其次下次维护临近）
const maintenanceAlerts = computed<MaintenanceAlert[]>(() => {
  const alerts: MaintenanceAlert[] = []
  const today = new Date()
  for (const eq of equipmentList.value) {
    if (eq.status === 3) {
      alerts.push({
        id: `fault-${eq.equipmentId}`,
        title: '设备故障报警',
        description: `${eq.equipmentName}(${eq.equipmentNo}) 处于故障状态，需要检修`,
        time: eq.updateTime ? formatDate(eq.updateTime) : '--',
        priority: 'high',
      })
    } else if (eq.nextMaintenance) {
      const next = new Date(eq.nextMaintenance)
      const days = Math.ceil((next.getTime() - today.getTime()) / 86400000)
      if (days <= 30) {
        alerts.push({
          id: `maint-${eq.equipmentId}`,
          title: days < 0 ? '维护已到期' : '设备维护提醒',
          description: `${eq.equipmentName}(${eq.equipmentNo}) 下次维护日期 ${formatDate(eq.nextMaintenance)}`,
          time: formatDate(eq.nextMaintenance),
          priority: days < 0 ? 'high' : 'normal',
        })
      }
    }
  }
  return alerts.slice(0, 10)
})

// 对话框
const dialogVisible = ref(false)
const detailVisible = ref(false)
const formRef = ref()
const currentDetail = ref<ProductionEquipment | null>(null)
const form = reactive<Partial<ProductionEquipment>>({
  equipmentNo: '',
  equipmentName: '',
  equipmentType: '',
  model: '',
  department: '',
  location: '',
  status: 1,
  utilization: 0,
  remark: '',
})
const formRules = {
  equipmentNo: [{ required: true, message: '设备编号不能为空', trigger: 'blur' }],
  equipmentName: [{ required: true, message: '设备名称不能为空', trigger: 'blur' }],
}

// 加载列表
const loadList = async () => {
  loading.value = true
  try {
    const res: any = await getEquipmentPage({
      pageNum: pagination.current,
      pageSize: pagination.size,
      equipmentNo: query.equipmentNo || undefined,
      status: query.status,
    })
    const data = res.data || {}
    equipmentList.value = data.records || []
    pagination.total = data.total || 0
  } catch (e: any) {
    ElMessage.error(e?.message || '加载设备列表失败')
  } finally {
    loading.value = false
  }
}

// 加载统计（全量列表）
const loadStats = async () => {
  try {
    const res: any = await getEquipmentList()
    const list: ProductionEquipment[] = res.data || []
    stats.running = list.filter((e) => e.status === 1).length
    stats.idle = list.filter((e) => e.status === 0).length
    stats.fault = list.filter((e) => e.status === 3).length
    stats.maintenance = list.filter((e) => e.status === 2).length
    const valid = list.filter((e) => e.utilization != null)
    avgUtilization.value = valid.length > 0
      ? Math.round((valid.reduce((s, e) => s + Number(e.utilization), 0) / valid.length) * 10) / 10
      : 0
  } catch (e: any) {
    console.error('加载设备统计失败:', e)
  }
}

const handleSearch = () => {
  pagination.current = 1
  loadList()
}

// 生命周期
onMounted(() => {
  loadList()
  loadStats()
})

// 方法
const getStatusType = (status: number): 'success' | 'warning' | 'danger' | 'info' => {
  const map: Record<number, 'success' | 'warning' | 'danger' | 'info'> = {
    1: 'success', // 运行中
    0: 'warning', // 待机中
    3: 'danger', // 故障中
    2: 'info', // 维护中
  }
  return map[status] || 'info'
}

const getStatusText = (status: number) => {
  const map: Record<number, string> = {
    1: '运行中',
    0: '待机中',
    3: '故障中',
    2: '维护中',
  }
  return map[status] ?? `未知(${status})`
}

const getUtilizationColor = (utilization: number) => {
  if (utilization >= 90) return '#67c23a'
  if (utilization >= 70) return '#e6a23c'
  return '#f56c6c'
}

const formatDate = (d?: string) => {
  if (!d) return '-'
  return String(d).slice(0, 10)
}

// 新增
const handleCreate = () => {
  Object.assign(form, {
    equipmentId: undefined,
    equipmentNo: '',
    equipmentName: '',
    equipmentType: '',
    model: '',
    department: '',
    location: '',
    status: 1,
    utilization: 0,
    lastMaintenance: undefined,
    nextMaintenance: undefined,
    remark: '',
  })
  dialogVisible.value = true
}

// 编辑
const editEquipment = (row: ProductionEquipment) => {
  Object.assign(form, {
    equipmentId: row.equipmentId,
    equipmentNo: row.equipmentNo,
    equipmentName: row.equipmentName,
    equipmentType: row.equipmentType || '',
    model: row.model || '',
    department: row.department || '',
    location: row.location || '',
    status: row.status,
    utilization: Number(row.utilization) || 0,
    lastMaintenance: row.lastMaintenance,
    nextMaintenance: row.nextMaintenance,
    remark: row.remark || '',
  })
  dialogVisible.value = true
}

// 保存
const handleSave = async () => {
  await formRef.value?.validate()
  saving.value = true
  try {
    if (form.equipmentId) {
      await updateEquipment(form as any)
      ElMessage.success('设备已更新')
    } else {
      await createEquipment(form as any)
      ElMessage.success('设备已创建')
    }
    dialogVisible.value = false
    loadList()
    loadStats()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// 删除
const removeEquipment = async (row: ProductionEquipment) => {
  try {
    await ElMessageBox.confirm(`确定删除设备「${row.equipmentName}」吗？`, '删除确认', {
      type: 'warning',
    })
    await deleteEquipment(row.equipmentId)
    ElMessage.success('设备已删除')
    loadList()
    loadStats()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

// 详情
const viewEquipmentDetail = (row: ProductionEquipment) => {
  currentDetail.value = row
  detailVisible.value = true
}

// 维护计划（DEV-686：暂无独立页面，提示）
const handleMaintenance = () => {
  ElMessage.info('维护计划功能待开发（当前设备台账维护日期可在编辑中维护）')
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
