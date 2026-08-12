<template>
  <div class="dispatch-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">派工管理</h1>
      <div class="page-actions">
        <el-button type="primary" icon="Grid" @click="openBatchDialog">批量派工</el-button>
      </div>
    </div>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <div class="filter-bar">
        <el-input v-model="query.orderNo" placeholder="工单编号" clearable style="width: 160px" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="query.keyword" placeholder="工序/设备关键字" clearable style="width: 160px" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-tree-select
          v-model="query.teamId"
          :data="deptTree"
          :props="deptProps"
          placeholder="责任班组"
          clearable
          check-strictly
          style="width: 180px"
          @change="handleSearch"
        />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 130px" @change="handleSearch">
          <el-option v-for="s in STATUS_ITEMS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" icon="Search" @click="handleSearch">查询</el-button>
        <el-button icon="Refresh" @click="handleReset">重置</el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card class="list-card" shadow="never">
      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="orderNo" label="工单编号" width="140" show-overflow-tooltip />
        <el-table-column label="工序" min-width="140">
          <template #default="{ row }">
            <div>
              <div>{{ row.processName || '-' }}</div>
              <div v-if="row.processOrder" style="font-size: 12px; color: #909399">序 {{ row.processOrder }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="责任班组" width="130">
          <template #default="{ row }">
            <el-tag v-if="row.teamName" size="small" type="primary" effect="plain">{{ row.teamName }}</el-tag>
            <span v-else style="color: #c0c4cc">未指定</span>
          </template>
        </el-table-column>
        <el-table-column label="设备" width="130" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.equipmentName">{{ row.equipmentName }}</span>
            <span v-else style="color: #c0c4cc">不限</span>
          </template>
        </el-table-column>
        <el-table-column label="执行人" width="150">
          <template #default="{ row }">
            <el-tag v-for="(o, i) in parseOperators(row.operators)" :key="i" size="small" style="margin-right: 4px">{{ o.userName }}</el-tag>
            <span v-if="!parseOperators(row.operators).length" style="color: #c0c4cc">未指定</span>
          </template>
        </el-table-column>
        <el-table-column prop="assignedByName" label="派工主管" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">
              {{ row.statusLabel || STATUS_LABELS[row.status] || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="指派时间" width="150">
          <template #default="{ row }">{{ row.assignTime ? row.assignTime.replace('T', ' ').slice(0, 16) : '-' }}</template>
        </el-table-column>
        <el-table-column label="改派" width="70" align="center">
          <template #default="{ row }">
            <el-badge v-if="row.reDispatchCount > 0" :value="row.reDispatchCount" type="warning">
              <span style="padding: 0 6px">次</span>
            </el-badge>
            <span v-else style="color: #c0c4cc">0</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 0 || row.status === 4" link type="primary" @click="openAssign(row)">指派</el-button>
            <el-button v-else link type="primary" @click="openAssign(row)">改派</el-button>
            <el-button v-if="row.status === 1" link type="success" @click="handleStart(row)">开始</el-button>
            <el-button v-if="row.status === 2" link type="success" @click="handleComplete(row)">完成</el-button>
            <el-button v-if="row.status === 1 || row.status === 2" link type="danger" @click="openReject(row)">退回</el-button>
            <el-button link @click="openLogs(row)">流水</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="query.pageNum"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="loadList"
          @current-change="loadList"
        />
      </div>
    </el-card>

    <!-- 指派/改派弹窗 -->
    <el-dialog v-model="assignVisible" :title="assignForm.dispatchId ? '改派工序' : '指派工序'" width="560px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="工序">
          <span>{{ assignForm.processName || '-' }}（{{ assignForm.orderNo || '-' }}）</span>
        </el-form-item>
        <el-form-item label="责任班组">
          <el-tree-select
            v-model="assignForm.teamId"
            :data="deptTree"
            :props="deptProps"
            placeholder="选择班组（可空）"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="设备">
          <el-select v-model="assignForm.equipmentId" placeholder="选择设备（可空=不限）" clearable filterable style="width: 100%">
            <el-option
              v-for="eq in equipmentList"
              :key="eq.equipmentId"
              :label="`${eq.equipmentName}（${eq.equipmentNo}）`"
              :value="eq.equipmentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="执行人">
          <el-select
            v-model="assignForm.operatorIds"
            placeholder="选择执行人（可多选，可空）"
            multiple
            filterable
            remote
            :remote-method="searchUsers"
            :loading="userLoading"
            style="width: 100%"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.userId"
              :label="u.nickName || u.userName"
              :value="u.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="assignForm.remark" type="textarea" :rows="2" />
        </el-form-item>
        <div style="color: #909399; font-size: 12px">班组/设备/执行人至少指定一项；改派会记录变更流水</div>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="handleAssign">保存</el-button>
      </template>
    </el-dialog>

    <!-- 批量派工弹窗 -->
    <el-dialog v-model="batchVisible" title="批量派工（整单工序）" width="560px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="工单">
          <el-select v-model="batchForm.orderId" placeholder="选择工单" filterable style="width: 100%" @change="onBatchOrderChange">
            <el-option
              v-for="o in orderOptions"
              :key="o.orderId"
              :label="`${o.orderNo}（${o.productName}）`"
              :value="o.orderId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="待派工序">
          <span style="color: #606266">{{ batchPendingCount }} 道（未派工/已退回）</span>
        </el-form-item>
        <el-form-item label="责任班组">
          <el-tree-select
            v-model="batchForm.teamId"
            :data="deptTree"
            :props="deptProps"
            placeholder="选择班组（可空）"
            clearable
            check-strictly
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="设备">
          <el-select v-model="batchForm.equipmentId" placeholder="选择设备（可空=不限）" clearable filterable style="width: 100%">
            <el-option
              v-for="eq in equipmentList"
              :key="eq.equipmentId"
              :label="`${eq.equipmentName}（${eq.equipmentNo}）`"
              :value="eq.equipmentId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="执行人">
          <el-select
            v-model="batchForm.operatorIds"
            placeholder="选择执行人（可多选，可空）"
            multiple
            filterable
            remote
            :remote-method="searchUsers"
            :loading="userLoading"
            style="width: 100%"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.userId"
              :label="u.nickName || u.userName"
              :value="u.userId"
            />
          </el-select>
        </el-form-item>
        <div style="color: #909399; font-size: 12px">将批量应用到该工单所有未派工/已退回的工序；班组/设备/执行人至少指定一项</div>
      </el-form>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="handleBatchAssign">批量派工</el-button>
      </template>
    </el-dialog>

    <!-- 退回弹窗 -->
    <el-dialog v-model="rejectVisible" title="退回派工" width="440px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="工序">{{ rejectTarget?.processName || '-' }}</el-form-item>
        <el-form-item label="退回原因" required>
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="必填" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="assigning" @click="handleReject">退回</el-button>
      </template>
    </el-dialog>

    <!-- 流水时间线弹窗 -->
    <el-dialog v-model="logsVisible" title="派工流水" width="520px" append-to-body>
      <el-timeline v-if="logList.length">
        <el-timeline-item
          v-for="lg in logList"
          :key="lg.logId"
          :timestamp="lg.createTime ? lg.createTime.replace('T', ' ').slice(0, 19) : ''"
          :type="logType(lg.action)"
        >
          <div style="font-size: 13px">
            <el-tag size="small" :type="logType(lg.action)" effect="plain">{{ ACTION_LABELS[lg.action] || lg.action }}</el-tag>
            <span style="margin-left: 6px">{{ lg.content }}</span>
          </div>
          <div style="font-size: 12px; color: #909399; margin-top: 2px">操作人：{{ lg.operatorName || '-' }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无流水记录" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute } from 'vue-router'
import {
  getDispatchPage,
  assignDispatch,
  batchAssignDispatch,
  rejectDispatch,
  startDispatch,
  completeDispatch,
  getDispatchLogs,
  getDispatchByOrder,
  type DispatchQuery,
  type DispatchVO,
  type DispatchLog,
  type DispatchAssignPayload,
} from '@/api/production/dispatch'
import { deptApi } from '@/api/system/dept'
import { userApi } from '@/api/system/user'
import { getEquipmentList } from '@/api/production/equipment'
import { getProductionOrderList } from '@/api/production/order'

const STATUS_LABELS: Record<number, string> = { 0: '待派工', 1: '已派工', 2: '执行中', 3: '已完成', 4: '已退回' }
const STATUS_ITEMS = Object.entries(STATUS_LABELS).map(([v, label]) => ({ value: Number(v), label }))
const ACTION_LABELS: Record<string, string> = {
  ASSIGN: '指派',
  REASSIGN: '改派',
  REJECT: '退回',
  START: '开始',
  COMPLETE: '完成',
}

const loading = ref(false)
const list = ref<DispatchVO[]>([])
const total = ref(0)
const query = reactive<DispatchQuery>({ pageNum: 1, pageSize: 10, orderNo: '', keyword: '', teamId: undefined, status: undefined })

const deptTree = ref<any[]>([])
const deptProps = { label: 'deptName', value: 'id', children: 'children' }
const equipmentList = ref<any[]>([])
const userOptions = ref<any[]>([])
const userLoading = ref(false)

function statusTag(status: number): any {
  return { 0: 'info', 1: 'primary', 2: 'warning', 3: 'success', 4: 'danger' }[status] || 'info'
}

function parseOperators(json?: string): { userId: number; userName: string }[] {
  if (!json) return []
  try {
    return JSON.parse(json)
  } catch {
    return []
  }
}

async function loadList() {
  loading.value = true
  try {
    const res: any = await getDispatchPage(query)
    const data = res?.data || res
    list.value = data?.records || []
    total.value = data?.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  query.pageNum = 1
  loadList()
}
const handleReset = () => {
  query.orderNo = ''
  query.keyword = ''
  query.teamId = undefined
  query.status = undefined
  query.pageNum = 1
  loadList()
}

// ===== 基础数据 =====
async function loadBaseData() {
  try {
    const res: any = await deptApi.treeselect({} as any)
    deptTree.value = res?.data || []
  } catch {
    deptTree.value = []
  }
  try {
    const res: any = await getEquipmentList({})
    equipmentList.value = res?.data || []
  } catch {
    equipmentList.value = []
  }
}

async function searchUsers(keyword: string) {
  userLoading.value = true
  try {
    const params: any = { pageNum: 1, pageSize: 50 }
    if (keyword?.trim()) params.userName = keyword.trim()
    const res: any = await userApi.list(params)
    userOptions.value = res?.data?.records || res?.data || []
  } catch {
    userOptions.value = []
  } finally {
    userLoading.value = false
  }
}

// ===== 指派/改派 =====
const assignVisible = ref(false)
const assigning = ref(false)
const assignForm = reactive<DispatchAssignPayload & { processName?: string; orderNo?: string }>({
  dispatchId: undefined,
  orderId: undefined,
  executionId: undefined,
  teamId: undefined,
  equipmentId: undefined,
  operatorIds: [],
  remark: '',
})

function openAssign(row: DispatchVO) {
  Object.assign(assignForm, {
    dispatchId: row.dispatchId,
    orderId: row.orderId,
    executionId: row.executionId,
    teamId: row.teamId,
    equipmentId: row.equipmentId,
    operatorIds: parseOperators(row.operators).map((o) => o.userId),
    remark: row.remark || '',
    processName: row.processName,
    orderNo: row.orderNo,
  })
  assignVisible.value = true
  if (!userOptions.value.length) searchUsers('')
}

async function handleAssign() {
  if (!assignForm.teamId && !assignForm.equipmentId && (!assignForm.operatorIds || !assignForm.operatorIds.length)) {
    ElMessage.warning('班组/设备/执行人至少指定一项')
    return
  }
  assigning.value = true
  try {
    await assignDispatch({ ...assignForm } as DispatchAssignPayload)
    ElMessage.success(assignForm.dispatchId ? '改派成功' : '指派成功')
    assignVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    assigning.value = false
  }
}

// ===== 批量派工 =====
const batchVisible = ref(false)
const batchForm = reactive<DispatchAssignPayload>({
  orderId: undefined,
  teamId: undefined,
  equipmentId: undefined,
  operatorIds: [],
  batch: true,
})
const orderOptions = ref<any[]>([])
const batchPendingCount = ref(0)

async function openBatchDialog() {
  Object.assign(batchForm, { orderId: undefined, teamId: undefined, equipmentId: undefined, operatorIds: [], batch: true })
  batchPendingCount.value = 0
  batchVisible.value = true
  if (!orderOptions.value.length) {
    try {
      const res: any = await getProductionOrderList({ pageNum: 1, pageSize: 200, orderType: 'all' } as any)
      orderOptions.value = res?.data?.records || res?.data || []
    } catch {
      orderOptions.value = []
    }
  }
  if (!userOptions.value.length) searchUsers('')
}

async function onBatchOrderChange(orderId: number) {
  try {
    const res: any = await getDispatchByOrder(orderId)
    const rows: DispatchVO[] = res?.data || []
    batchPendingCount.value = rows.filter((r) => r.status === 0 || r.status === 4).length
  } catch {
    batchPendingCount.value = 0
  }
}

async function handleBatchAssign() {
  if (!batchForm.orderId) {
    ElMessage.warning('请选择工单')
    return
  }
  if (!batchForm.teamId && !batchForm.equipmentId && (!batchForm.operatorIds || !batchForm.operatorIds.length)) {
    ElMessage.warning('班组/设备/执行人至少指定一项')
    return
  }
  assigning.value = true
  try {
    const res: any = await batchAssignDispatch(batchForm)
    ElMessage.success(`批量派工完成，共派 ${res?.data || 0} 道工序`)
    batchVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '批量派工失败')
  } finally {
    assigning.value = false
  }
}

// ===== 退回 =====
const rejectVisible = ref(false)
const rejectTarget = ref<DispatchVO | null>(null)
const rejectReason = ref('')

function openReject(row: DispatchVO) {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

async function handleReject() {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('退回原因必填')
    return
  }
  assigning.value = true
  try {
    await rejectDispatch(rejectTarget.value!.dispatchId, rejectReason.value.trim())
    ElMessage.success('已退回，可重新指派')
    rejectVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '退回失败')
  } finally {
    assigning.value = false
  }
}

// ===== 开始/完成 =====
async function handleStart(row: DispatchVO) {
  await ElMessageBox.confirm(`确定开始「${row.processName}」吗？`, '开始工序', { type: 'info' }).catch(() => Promise.reject())
  try {
    await startDispatch(row.dispatchId)
    ElMessage.success('已开始')
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function handleComplete(row: DispatchVO) {
  await ElMessageBox.confirm(`确定完成「${row.processName}」吗？`, '完成工序', { type: 'info' }).catch(() => Promise.reject())
  try {
    await completeDispatch(row.dispatchId)
    ElMessage.success('已完成')
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

// ===== 流水 =====
const logsVisible = ref(false)
const logList = ref<DispatchLog[]>([])

function logType(action: string): any {
  return { ASSIGN: 'primary', REASSIGN: 'warning', REJECT: 'danger', START: 'primary', COMPLETE: 'success' }[action] || 'info'
}

async function openLogs(row: DispatchVO) {
  logList.value = []
  logsVisible.value = true
  try {
    const res: any = await getDispatchLogs(row.dispatchId)
    logList.value = res?.data || []
  } catch {
    logList.value = []
  }
}

onMounted(() => {
  // 从工单列表“派工”入口进入时，自动按工单编号筛选（2026-08-12）
  const route = useRoute()
  const orderNo = route.query.orderNo as string | undefined
  if (orderNo) {
    query.orderNo = orderNo
  }
  loadList()
  loadBaseData()
})
</script>

<style scoped>
.dispatch-page {
  padding: 16px;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
}
.filter-card {
  margin-bottom: 16px;
}
.filter-bar {
  display: flex;
  gap: 10px;
  align-items: center;
  padding-bottom: 8px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
