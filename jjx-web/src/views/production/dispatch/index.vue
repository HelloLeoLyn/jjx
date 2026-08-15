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
        <el-table-column label="数量" width="80" align="right">
          <template #default="{ row }">
            <span>{{ fmtQty(row.plannedQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="工序" min-width="160">
          <template #default="{ row }">
            <div>
              <el-tag v-if="row.majorCategory === 'PRINT'" size="small" type="warning" effect="plain" style="margin-right: 4px">印刷</el-tag>
              <span>{{ row.processName || '-' }}</span>
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
        <el-table-column label="执行人链" min-width="170">
          <template #default="{ row }">
            <OperatorChain
              :operators="row.operators"
              :process-name="row.processName"
              :order-no="row.orderNo"
              :team-name="row.teamName"
              :equipment-name="row.equipmentName"
              :dispatch-id="row.dispatchId"
              first-only
              @logs="openLogsById"
            />
          </template>
        </el-table-column>
        <el-table-column prop="assignedByName" label="派工主管" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.dispatchStatus)">
              {{ statusLabel(row) }}
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
        <el-table-column label="操作" width="230" fixed="right">
          <template #default="{ row }">
            <el-button v-if="!row.dispatchId" link type="primary" @click="openAssign(row)">指派</el-button>
            <template v-else>
              <el-button v-if="row.dispatchStatus === 1 || row.dispatchStatus === 2" link type="warning" @click="openTransfer(row)">转派</el-button>
              <el-button link type="primary" @click="openAssign(row)">改派</el-button>
              <el-button v-if="row.dispatchStatus === 2" link type="success" @click="handleStart(row)">开始</el-button>
              <el-button v-if="row.dispatchStatus === 3" link type="success" @click="handleComplete(row)">完成</el-button>
              <el-button v-if="row.dispatchStatus === 1 || row.dispatchStatus === 2 || row.dispatchStatus === 3" link type="danger" @click="openReject(row)">退回</el-button>
              <el-button link @click="openLogs(row)">流水</el-button>
            </template>
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

    <!-- 指派/改派弹窗（2026-08-13：第一个被选的人=第1级执行人，无级别下拉；多级靠转派） -->
    <el-dialog v-model="assignVisible" :title="assignForm.dispatchId ? '改派工序（更换第1级负责人）' : '指派工序'" width="560px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="工序">
          <span>{{ assignForm.processName || '-' }}（{{ assignForm.orderNo || '-' }}）</span>
        </el-form-item>
        <!-- 当前执行人链（追加时展示已有链） -->
        <el-form-item v-if="assignForm.dispatchId && assignForm.existingChain.length" label="当前链">
          <OperatorChain
            :operators="chainJson(assignForm.existingChain)"
            :clickable="false"
          />
        </el-form-item>
        <el-form-item label="责任班组">
          <el-tree-select
            v-model="assignForm.teamId"
            :data="deptTree"
            :props="deptProps"
            placeholder="选择班组（仅末级部门）"
            clearable
            check-strictly
            :disabled="!!assignForm.dispatchId"
            style="width: 100%"
            @change="onAssignTeamChange"
          />
          <div v-if="assignForm.dispatchId" style="font-size: 12px; color: #909399; line-height: 1.6">
            责任班组仅首次指派可修改，转派/改派保持原班组
          </div>
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
          <el-button
            :disabled="!assignForm.teamId"
            type="primary"
            plain
            @click="openOperatorPicker('assign')"
          >
            {{ (assignForm.operatorIds || []).length ? `已选 ${(assignForm.operatorIds || []).length} 人，点击修改` : '选择执行人' }}
          </el-button>
          <div v-if="assignPickerNames.length" class="op-selected">
            <el-tag v-for="(n, i) in assignPickerNames" :key="i" size="small" style="margin-right: 4px">{{ n }}</el-tag>
          </div>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="assignForm.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="链完整性">
          <el-switch v-model="assignForm.chainComplete" active-text="执行人链已完整（可开工）" inactive-text="还有下级执行人待追加" />
        </el-form-item>
        <div style="color: #909399; font-size: 12px">
          第一个被选的人=第1级执行人（部门主管/负责人）；若他手下有人，可在列表点【转派】把任务派给他的手下（第2级），以此类推，最后一级为实际干活人。班组/设备/执行人至少指定一项。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="handleAssign">保存</el-button>
      </template>
    </el-dialog>

    <!-- 执行人树形选择弹窗（2026-08-13） -->
    <OperatorPicker
      :visible="pickerVisible"
      @update:visible="pickerVisible = $event"
      :users="userOptions"
      :model-value="pickerIds"
      @confirm="onPickerConfirm"
    />

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
            placeholder="选择班组（仅末级部门）"
            @change="onBatchTeamChange"
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
          <el-button
            :disabled="!batchForm.teamId"
            type="primary"
            plain
            @click="openOperatorPicker('batch')"
          >
            {{ (batchForm.operatorIds || []).length ? `已选 ${(batchForm.operatorIds || []).length} 人，点击修改` : '选择执行人' }}
          </el-button>
          <div v-if="batchPickerNames.length" class="op-selected">
            <el-tag v-for="(n, i) in batchPickerNames" :key="i" size="small" style="margin-right: 4px">{{ n }}</el-tag>
          </div>
        </el-form-item>
        <div style="color: #909399; font-size: 12px">将批量应用到该工单所有未派工/已退回的工序；班组/设备/执行人至少指定一项</div>
      </el-form>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="handleBatchAssign">批量派工</el-button>
      </template>
    </el-dialog>

    <!-- 转派弹窗（2026-08-13：由链上执行人转派给其手下，追加下一级） -->
    <el-dialog v-model="transferVisible" title="转派给下属" width="520px" append-to-body>
      <el-form label-width="100px">
        <el-form-item label="工序">
          <span>{{ transferForm.processName || '-' }}（{{ transferForm.orderNo || '-' }}）</span>
        </el-form-item>
        <el-form-item label="当前链">
          <OperatorChain
            :operators="chainJson(transferForm.existingChain)"
            :clickable="false"
          />
        </el-form-item>
        <el-form-item label="由谁转派">
          <el-select v-model="transferForm.fromUserId" placeholder="选择转派人（链上执行人）" style="width: 100%" @change="onTransferFromChange">
            <el-option
              v-for="o in transferForm.existingChain"
              :key="o.userId"
              :label="`第${o.level ?? 1}级 ${o.userName}`"
              :value="o.userId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="转派给（手下）">
          <el-select
            v-model="transferForm.toUserIds"
            :placeholder="transferFromName ? `${transferFromName} 的手下：其负责部门及下级部门成员` : '请先选择转派人'"
            multiple
            filterable
            :loading="transferLoading"
            style="width: 100%"
          >
            <el-option
              v-for="u in transferOptions"
              :key="u.userId"
              :label="u.nickName || u.userName"
              :value="u.userId"
            />
          </el-select>
        </el-form-item>
        <div style="color: #909399; font-size: 12px">
          转派后该执行人成为第{{ transferNextLevel }}级（实际干活人），原第{{ transferFromLevel }}级保留在链上负责；转派对象只能是其负责部门及下级部门的成员。
        </div>
      </el-form>
      <template #footer>
        <el-button @click="transferVisible = false">取消</el-button>
        <el-button type="warning" :loading="transferring" @click="handleTransfer">转派</el-button>
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
import { ref, reactive, computed, onMounted } from 'vue'
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
  getPendingDispatches,
  getUnderlings,
  getTeamPersons,
  type DispatchQuery,
  type DispatchVO,
  type DispatchLog,
  type DispatchAssignPayload,
} from '@/api/production/dispatch'
import { deptApi } from '@/api/system/dept'
import { getEquipmentList } from '@/api/production/equipment'
import { getProductionOrderList } from '@/api/production/order'
import OperatorChain from '@/components/OperatorChain/index.vue'
import OperatorPicker from '@/components/OperatorPicker/index.vue'

const STATUS_LABELS: Record<number, string> = { 0: '待派工', 1: '已派班组', 2: '已派工', 3: '执行中', 4: '已完成', 5: '已退回' }
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

function statusTag(status?: number): any {
  return { 0: 'info', 1: 'primary', 2: 'success', 3: 'warning', 4: 'success', 5: 'danger' }[status ?? 0] || 'info'
}

function statusLabel(row: DispatchVO): string {
  const st = row.dispatchStatus ?? 0
  // 前端枚举优先（后端未重启时旧 label 可能错位），后端 label 兜底
  return STATUS_LABELS[st] || row.statusLabel || String(st)
}

function fmtQty(v?: number | string | null): string {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return Number.isInteger(n) ? String(n) : String(n)
}

function parseOperators(json?: string): { userId: number; userName: string; level?: number }[] {
  if (!json) return []
  try {
    const arr = JSON.parse(json)
    return (arr as any[]).sort((a, b) => (a.level ?? 1) - (b.level ?? 1))
  } catch {
    return []
  }
}

// 责任班组=末级部门（叶子）：父部门（中心/车间）过滤掉（2026-08-13）
function filterLeafNodes(nodes: any[]): any[] {
  const result: any[] = []
  for (const n of nodes || []) {
    if (n.children && n.children.length) {
      result.push(...filterLeafNodes(n.children))
    } else {
      result.push({ ...n, children: undefined })
    }
  }
  return result
}

// 递归找部门节点
function findDept(nodes: any[], id: number): any | null {
  for (const n of nodes || []) {
    if (n.id === id) return n
    const found = findDept(n.children || [], id)
    if (found) return found
  }
  return null
}

// 责任班组只取生产中心子树叶子（排除办公室/研发/市场等非生产部门，2026-08-13）
function productionTeamNodes(nodes: any[]): any[] {
  const center = findDept(nodes, 5)
  return center ? filterLeafNodes([center]) : filterLeafNodes(nodes)
}
function chainMaxLevel(row: DispatchVO): number {
  const ops = parseOperators(row.operators)
  return ops.length ? Math.max(...ops.map((o) => o.level ?? 1)) : 0
}

// 数组转 JSON 字符串（弹窗当前链传给组件）
function chainJson(ops: { userId: number; userName: string; level?: number }[]): string {
  return JSON.stringify(ops)
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
    deptTree.value = productionTeamNodes(res?.data || [])
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

// ===== 执行人树形选择（2026-08-13：按部门分组勾选，方便多人） =====
const pickerVisible = ref(false)
const pickerMode = ref<'assign' | 'batch'>('assign')
const pickerIds = ref<number[]>([])

const assignPickerNames = computed(() => {
  const ids = assignForm.operatorIds || []
  return userOptions.value.filter((u) => ids.includes(u.userId)).map((u) => u.nickName || u.userName)
})
const batchPickerNames = computed(() => {
  const ids = batchForm.operatorIds || []
  return userOptions.value.filter((u) => ids.includes(u.userId)).map((u) => u.nickName || u.userName)
})

function openOperatorPicker(mode: 'assign' | 'batch') {
  pickerMode.value = mode
  pickerIds.value = mode === 'assign' ? [...(assignForm.operatorIds || [])] : [...(batchForm.operatorIds || [])]
  pickerVisible.value = true
}

function onPickerConfirm(ids: number[]) {
  if (pickerMode.value === 'assign') {
    assignForm.operatorIds = ids
  } else {
    batchForm.operatorIds = ids
  }
}

// ===== 指派/改派 =====
const assignVisible = ref(false)
const assigning = ref(false)
const assignForm = reactive<
  DispatchAssignPayload & {
    processName?: string
    orderNo?: string
    level: number
    chainComplete: boolean
    existingChain: { userId: number; userName: string; level?: number }[]
  }
>({
  dispatchId: undefined,
  orderId: undefined,
  executionId: undefined,
  teamId: undefined,
  equipmentId: undefined,
  operatorIds: [],
  remark: '',
  level: 1,
  chainComplete: true,
  existingChain: [],
})

function openAssign(row: DispatchVO) {
  const chain = parseOperators(row.operators)
  Object.assign(assignForm, {
    dispatchId: row.dispatchId,
    orderId: row.orderId,
    executionId: row.executionId,
    teamId: row.teamId,
    equipmentId: row.equipmentId,
    operatorIds: [],
    remark: '',
    processName: row.processName,
    orderNo: row.orderNo,
    // 新建=第1级；改派=固定第1级（换负责人，链上其余级别保留）
    level: 1,
    chainComplete: !row.dispatchId,
    existingChain: chain,
  })
  assignVisible.value = true
  // 执行人按责任班组带出（改派班组锁定；新建未选班组时执行人禁用）
  if (row.teamId) {
    loadOperatorsByTeam(row.teamId)
  } else {
    userOptions.value = []
  }
}

// 选责任班组 → 执行人限定为责任班组成员（2026-08-13）
async function onAssignTeamChange(teamId?: number) {
  assignForm.operatorIds = []
  if (teamId) {
    loadOperatorsByTeam(teamId)
  }
}

async function loadOperatorsByTeam(teamId: number) {
  userLoading.value = true
  try {
    const res: any = await getTeamPersons(teamId)
    userOptions.value = res?.data || []
  } catch {
    userOptions.value = []
  } finally {
    userLoading.value = false
  }
}

async function handleAssign() {
  if (!assignForm.teamId && !assignForm.equipmentId && (!assignForm.operatorIds || !assignForm.operatorIds.length)) {
    ElMessage.warning('班组/设备/执行人至少指定一项')
    return
  }
  assigning.value = true
  try {
    // 剔除展示字段（processName/orderNo/existingChain 仅弹窗展示用，2026-08-13）
    const { processName, orderNo, existingChain, ...payload } = assignForm
    await assignDispatch(payload as DispatchAssignPayload)
    ElMessage.success(assignForm.dispatchId ? (assignForm.level > 1 ? '已追加执行人' : '改派成功') : '指派成功')
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
}

async function onBatchOrderChange(orderId: number) {
  try {
    const res: any = await getPendingDispatches(orderId)
    const rows: DispatchVO[] = res?.data || []
    batchPendingCount.value = rows.length
  } catch {
    batchPendingCount.value = 0
  }
}

// 批量弹窗：选班组 → 执行人限定班组+上级部门（2026-08-13）
async function onBatchTeamChange(teamId?: number) {
  batchForm.operatorIds = []
  if (teamId) {
    userLoading.value = true
    try {
      const res: any = await getTeamPersons(teamId)
      userOptions.value = res?.data || []
    } catch {
      userOptions.value = []
    } finally {
      userLoading.value = false
    }
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

// ===== 转派（2026-08-13：由链上执行人转派给其手下，追加下一级） =====
const transferVisible = ref(false)
const transferring = ref(false)
const transferLoading = ref(false)
const transferOptions = ref<any[]>([])
const transferForm = reactive<{
  dispatchId?: number
  orderId?: number
  processName?: string
  orderNo?: string
  existingChain: { userId: number; userName: string; level?: number }[]
  fromUserId?: number
  toUserIds: number[]
}>({
  dispatchId: undefined,
  orderId: undefined,
  processName: '',
  orderNo: '',
  existingChain: [],
  fromUserId: undefined,
  toUserIds: [],
})

const transferFromLevel = computed(() => {
  const o = transferForm.existingChain.find((x) => x.userId === transferForm.fromUserId)
  return o?.level ?? 1
})
const transferNextLevel = computed(() => Math.min(transferFromLevel.value + 1, 3))
const transferFromName = computed(() => {
  const o = transferForm.existingChain.find((x) => x.userId === transferForm.fromUserId)
  return o?.userName || ''
})

function openTransfer(row: DispatchVO) {
  const chain = parseOperators(row.operators)
  Object.assign(transferForm, {
    dispatchId: row.dispatchId,
    orderId: row.orderId,
    processName: row.processName,
    orderNo: row.orderNo,
    existingChain: chain,
    fromUserId: chain.length ? chain[chain.length - 1].userId : undefined,
    toUserIds: [],
  })
  transferOptions.value = []
  transferVisible.value = true
  if (transferForm.fromUserId) onTransferFromChange(transferForm.fromUserId)
}

async function onTransferFromChange(userId: number) {
  transferForm.toUserIds = []
  transferOptions.value = []
  if (!userId) return
  transferLoading.value = true
  try {
    const res: any = await getUnderlings(userId)
    // 排除已在链上的人
    const chainIds = new Set(transferForm.existingChain.map((o) => o.userId))
    transferOptions.value = (res?.data || []).filter((u: any) => !chainIds.has(u.userId))
  } catch {
    transferOptions.value = []
  } finally {
    transferLoading.value = false
  }
}

async function handleTransfer() {
  if (!transferForm.dispatchId || !transferForm.fromUserId) {
    ElMessage.warning('请选择转派人')
    return
  }
  if (!transferForm.toUserIds.length) {
    ElMessage.warning('请选择转派给谁（其手下）')
    return
  }
  transferring.value = true
  try {
    await assignDispatch({
      dispatchId: transferForm.dispatchId,
      operatorIds: transferForm.toUserIds,
      transferFrom: transferForm.fromUserId,
      level: transferNextLevel.value,
      chainComplete: true,
    })
    ElMessage.success(`已转派为第${transferNextLevel.value}级执行人`)
    transferVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '转派失败')
  } finally {
    transferring.value = false
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
    await rejectDispatch(rejectTarget.value!.dispatchId!, rejectReason.value.trim())
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
    await startDispatch(row.dispatchId!)
    ElMessage.success('已开始')
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

async function handleComplete(row: DispatchVO) {
  await ElMessageBox.confirm(`确定完成「${row.processName}」吗？`, '完成工序', { type: 'info' }).catch(() => Promise.reject())
  try {
    await completeDispatch(row.dispatchId!)
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
  await openLogsById(row.dispatchId!)
}

async function openLogsById(dispatchId: number) {
  logList.value = []
  logsVisible.value = true
  try {
    const res: any = await getDispatchLogs(dispatchId)
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
.op-selected {
  margin-top: 6px;
  line-height: 1.8;
}
</style>
