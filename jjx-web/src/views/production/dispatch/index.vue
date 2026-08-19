<template>
  <div class="dispatch-page">
    <!-- 页面标题 -->
    <div class="page-header">
      <h1 class="page-title">派工管理</h1>
      <div class="page-actions">
        <el-button v-if="canAssign" type="primary" icon="Grid" @click="openBatchDialog">批量派工</el-button>
      </div>
    </div>

    <!-- 筛选区 -->
    <el-card class="filter-card" shadow="never">
      <div class="filter-bar">
        <el-input v-model="query.orderNo" placeholder="工单编号" clearable style="width: 150px" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-input v-model="query.keyword" placeholder="工序/设备关键字" clearable style="width: 150px" @keyup.enter="handleSearch" @clear="handleSearch" />
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="handleSearch">
          <el-option v-for="s in STATUS_ITEMS" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <!-- P1-D：全部相关 / 我的当前任务（区分“历史参与过”与“当前待办”） -->
        <el-radio-group v-model="query.scope" style="margin-left: 4px" @change="handleSearch">
          <el-radio-button value="">全部相关</el-radio-button>
          <el-radio-button value="mine">我的当前任务</el-radio-button>
        </el-radio-group>
        <el-button type="primary" icon="Search" @click="handleSearch">查询</el-button>
        <el-button icon="Refresh" @click="handleReset">重置</el-button>
      </div>
    </el-card>

    <!-- 列表 -->
    <el-card class="list-card" shadow="never">
      <el-table v-loading="loading" :data="list" style="width: 100%">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="orderNo" label="工单编号" width="130" show-overflow-tooltip />
        <el-table-column label="数量" width="70" align="right">
          <template #default="{ row }">
            <span>{{ fmtQty(row.plannedQuantity) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="工序" min-width="140">
          <template #default="{ row }">
            <div>
              <el-tag v-if="row.majorCategory === 'PRINT'" size="small" type="warning" effect="plain" style="margin-right: 4px">印刷</el-tag>
              <span>{{ row.processName || '-' }}</span>
              <div v-if="row.processOrder" style="font-size: 12px; color: #909399">序 {{ row.processOrder }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="设备" width="110" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.equipmentName">{{ row.equipmentName }}</span>
            <span v-else style="color: #c0c4cc">不限</span>
          </template>
        </el-table-column>
        <!-- P1-D：当前责任人（来自 Node projection，不 parse operators） -->
        <el-table-column label="当前责任人" min-width="130">
          <template #default="{ row }">
            <span v-if="row.currentAssigneeName" class="cur-assignee">
              {{ row.currentAssigneeName }}
              <el-tag v-if="row.currentOrgName" size="small" type="info" effect="plain" style="margin-left: 4px">{{ row.currentOrgName }}</el-tag>
            </span>
            <span v-else style="color: #c0c4cc">未派工</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.dispatchStatus)">{{ statusLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="指派时间" width="140">
          <template #default="{ row }">{{ row.assignTime ? row.assignTime.replace('T', ' ').slice(0, 16) : '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <!-- P1-D：按 allowedActions（后端投影）渲染按钮；无 dispatchId 且有权限 → 初始派工 -->
            <el-button v-if="!row.dispatchId && hasAction(row, 'ASSIGN')" link type="primary" @click="openAssign(row)">初始派工</el-button>
            <template v-else-if="row.dispatchId">
              <el-button v-if="hasAction(row, 'DELEGATE')" link type="primary" @click="openDelegate(row)">继续派工</el-button>
              <el-button v-if="hasAction(row, 'REASSIGN')" link type="warning" @click="openReassign(row)">改派</el-button>
              <el-button v-if="hasAction(row, 'RETURN')" link type="danger" @click="openReturn(row)">退回上级</el-button>
              <el-button v-if="row.dispatchStatus === 2" link type="success" @click="handleStart(row)">开始</el-button>
              <el-button v-if="row.dispatchStatus === 3" link type="success" @click="handleComplete(row)">完成</el-button>
              <el-button v-if="canAssign && [1, 2, 3].includes(row.dispatchStatus)" link type="danger" @click="openReject(row)">拒绝派工</el-button>
              <el-button link @click="openDetail(row)">责任链</el-button>
            </template>
            <el-button v-else link @click="openLogs(row)">流水</el-button>
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

    <!-- ============ P1-D 初始派工（ASSIGN V1） ============ -->
    <el-dialog v-model="assignVisible" title="初始派工" width="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="工序">
          <span>{{ assignForm.processName || '-' }}（{{ assignForm.orderNo || '-' }}）</span>
        </el-form-item>
        <el-form-item label="设备">
          <el-select v-model="assignForm.equipmentId" placeholder="选择设备（可空=不限）" clearable filterable style="width: 100%">
            <el-option v-for="eq in equipmentList" :key="eq.equipmentId" :label="`${eq.equipmentName}（${eq.equipmentNo}）`" :value="eq.equipmentId" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任人">
          <el-button type="primary" plain @click="openOperatorPicker('assign')">
            {{ assignForm.targetUserId ? selectedAssigneeName('assign') : '选择责任人' }}
          </el-button>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="assignForm.remark" type="textarea" :rows="2" />
        </el-form-item>
        <div style="color: #909399; font-size: 12px">初始派工 = 确定该工序的第一责任人；后续可继续派工/改派/退回。</div>
      </el-form>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleAssign">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ P1-D 继续派工（DELEGATE） ============ -->
    <el-dialog v-model="delegateVisible" title="继续派工（交给下一责任人）" width="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="工序">
          <span>{{ actionForm.processName || '-' }}（{{ actionForm.orderNo || '-' }}）</span>
        </el-form-item>
        <el-form-item label="当前责任人">
          <span class="cur-assignee">{{ actionForm.currentAssigneeName || '-' }}</span>
        </el-form-item>
        <el-form-item label="派给">
          <el-button type="primary" plain @click="openOperatorPicker('delegate')">
            {{ delegateForm.targetUserId ? selectedAssigneeName('delegate') : '选择责任人' }}
          </el-button>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="delegateForm.remark" type="textarea" :rows="2" />
        </el-form-item>
        <div style="color: #909399; font-size: 12px">继续派工 = 当前责任人把任务交给下一责任人（系统自动记录责任来源）。</div>
      </el-form>
      <template #footer>
        <el-button @click="delegateVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleDelegate">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ P1-D 改派（REASSIGN） ============ -->
    <el-dialog v-model="reassignVisible" title="改派（当前责任层换人）" width="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="工序">
          <span>{{ actionForm.processName || '-' }}（{{ actionForm.orderNo || '-' }}）</span>
        </el-form-item>
        <el-form-item label="当前责任人">
          <span class="cur-assignee">{{ actionForm.currentAssigneeName || '-' }}</span>
        </el-form-item>
        <el-form-item label="新责任人">
          <el-button type="primary" plain @click="openOperatorPicker('reassign')">
            {{ reassignForm.targetUserId ? selectedAssigneeName('reassign') : '选择责任人' }}
          </el-button>
        </el-form-item>
        <el-form-item label="原因/备注">
          <el-input v-model="reassignForm.reason" type="textarea" :rows="2" />
        </el-form-item>
        <div style="color: #909399; font-size: 12px">改派 = 当前责任层更换责任人，历史责任记录保留。</div>
      </el-form>
      <template #footer>
        <el-button @click="reassignVisible = false">取消</el-button>
        <el-button type="warning" :loading="submitting" @click="handleReassign">保存</el-button>
      </template>
    </el-dialog>

    <!-- ============ P1-D 退回（RETURN） ============ -->
    <el-dialog v-model="returnVisible" title="退回上级" width="460px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="工序">
          <span>{{ actionForm.processName || '-' }}（{{ actionForm.orderNo || '-' }}）</span>
        </el-form-item>
        <el-form-item label="当前责任人">
          <span class="cur-assignee">{{ actionForm.currentAssigneeName || '-' }}</span>
        </el-form-item>
        <el-form-item label="退回原因" required>
          <el-input v-model="returnForm.reason" type="textarea" :rows="3" placeholder="必填" />
        </el-form-item>
        <div style="color: #909399; font-size: 12px">退回上级 = 责任回到上一责任层（系统按历史责任自动确定，不能自选目标人）。</div>
      </el-form>
      <template #footer>
        <el-button @click="returnVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="handleReturn">退回上级</el-button>
      </template>
    </el-dialog>

    <!-- ============ 批量派工（保留旧入口） ============ -->
    <el-dialog v-model="batchVisible" title="批量派工（整单工序）" width="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="工单">
          <el-select v-model="batchForm.orderId" placeholder="选择工单" filterable style="width: 100%" @change="onBatchOrderChange">
            <el-option v-for="o in orderOptions" :key="o.orderId" :label="`${o.orderNo}（${o.productName}）`" :value="o.orderId" />
          </el-select>
        </el-form-item>
        <el-form-item label="待派工序">
          <span style="color: #606266">{{ batchPendingCount }} 道（未派工/已退回）</span>
        </el-form-item>
        <el-form-item label="设备">
          <el-select v-model="batchForm.equipmentId" placeholder="选择设备（可空=不限）" clearable filterable style="width: 100%">
            <el-option v-for="eq in equipmentList" :key="eq.equipmentId" :label="`${eq.equipmentName}（${eq.equipmentNo}）`" :value="eq.equipmentId" />
          </el-select>
        </el-form-item>
        <el-form-item label="责任人">
          <el-button type="primary" plain @click="openOperatorPicker('batch')">
            {{ batchForm.operatorIds && batchForm.operatorIds.length ? `已选 ${batchForm.operatorIds.length} 人` : '选择责任人' }}
          </el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="batchVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleBatchAssign">批量派工</el-button>
      </template>
    </el-dialog>

    <!-- ============ 责任链详情（P1-D Timeline） ============ -->
    <el-drawer v-model="detailVisible" title="派工责任链" size="480px">
      <template v-if="detailDispatch">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="工单">{{ detailDispatch.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="工序">{{ detailDispatch.processName }}（序 {{ detailDispatch.processOrder }}）</el-descriptions-item>
          <el-descriptions-item label="设备">{{ detailDispatch.equipmentName || '不限' }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ statusLabel(detailDispatch) }}</el-descriptions-item>
        </el-descriptions>

        <div class="cur-section">
          <div class="cur-title">当前责任</div>
          <div v-if="detailCurrent" class="cur-card">
            <span class="cur-assignee">{{ detailCurrent.assigneeName }}</span>
            <el-tag v-if="detailCurrent.orgName" size="small" type="info" effect="plain" style="margin-left: 6px">{{ detailCurrent.orgName }}</el-tag>
            <el-tag size="small" type="success" effect="plain" style="margin-left: 6px">当前负责</el-tag>
            <div style="font-size: 12px; color: #909399; margin-top: 4px">
              开始：{{ fmtTime(detailCurrent.assignedAt) }}<template v-if="detailCurrent.assignedByName">　由 {{ detailCurrent.assignedByName }} 指派</template>
            </div>
          </div>
          <div v-else style="color: #c0c4cc; font-size: 13px">未派工</div>
        </div>

        <div class="cur-title">责任历史</div>
        <el-timeline v-if="nodeList.length">
          <el-timeline-item
            v-for="n in nodeList"
            :key="n.nodeId || `${n.assigneeId}-${n.assignedAt}`"
            :timestamp="`${fmtTime(n.assignedAt)}${n.closedAt ? ' - ' + fmtTime(n.closedAt) : ''}`"
            :type="nodeTimelineType(n.nodeStatus)"
          >
            <div style="font-size: 13px">
              <span class="cur-assignee">{{ n.assigneeName }}</span>
              <el-tag v-if="n.orgName" size="small" type="info" effect="plain" style="margin-left: 6px">{{ n.orgName }}</el-tag>
              <el-tag size="small" :type="nodeTagType(n.nodeStatus)" effect="plain" style="margin-left: 6px">{{ nodeStatusLabel(n.nodeStatus) }}</el-tag>
            </div>
            <div style="font-size: 12px; color: #909399; margin-top: 2px">
              <template v-if="n.assignedByName">由 {{ n.assignedByName }} 指派</template>
              <template v-if="n.remark">　{{ n.remark }}</template>
            </div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无责任记录" :image-size="60" />

        <div class="detail-actions">
          <el-button size="small" @click="openLogs(detailDispatch)">查看流水</el-button>
        </div>
      </template>
    </el-drawer>

    <!-- 执行人选择弹窗（复用 OperatorPicker） -->
    <OperatorPicker
      :visible="pickerVisible"
      @update:visible="pickerVisible = $event"
      :users="userOptions"
      :model-value="pickerIds"
      :dept-tree="deptTree"
      @confirm="onPickerConfirm"
    />

    <!-- 拒绝派工（旧 REJECT：整单退回，与 RETURN 区分） -->
    <el-dialog v-model="rejectVisible" title="拒绝派工（整单退回）" width="440px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="工序">{{ rejectTarget?.processName || '-' }}</el-form-item>
        <el-form-item label="退回原因" required>
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="必填" />
        </el-form-item>
        <div style="color: #f56c6c; font-size: 12px">注意：这是整单退回（回到未派工），不是退回上一级。退回上一级请使用「退回上级」。</div>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="handleReject">整单退回</el-button>
      </template>
    </el-dialog>

    <!-- 流水时间线 -->
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
  assignDispatchV1,
  delegateDispatch,
  reassignDispatch,
  returnDispatch,
  rejectDispatch,
  startDispatch,
  completeDispatch,
  getDispatchLogs,
  getDispatchNodes,
  getDispatchCurrentNode,
  getPendingDispatches,
  getMyDepts,
  getCanAssign,
  getMyPersons,
  type DispatchQuery,
  type DispatchVO,
  type DispatchLog,
  type DispatchNodeVO,
  type DispatchAssignV1Payload,
  type DispatchDelegatePayload,
  type DispatchReassignPayload,
  type DispatchReturnPayload,
} from '@/api/production/dispatch'
import { getEquipmentList } from '@/api/production/equipment'
import { getProductionOrderList } from '@/api/production/order'
import OperatorPicker from '@/components/OperatorPicker/index.vue'

// ============ 常量映射（NodeStatus != DispatchStatus，独立 mapper） ============
const STATUS_LABELS: Record<number, string> = { 0: '待派工', 1: '已派工', 2: '已派工', 3: '执行中', 4: '已完成', 5: '已退回' }
const STATUS_ITEMS = Object.entries(STATUS_LABELS).map(([v, label]) => ({ value: Number(v), label }))
const ACTION_LABELS: Record<string, string> = {
  ASSIGN: '指派', DELEGATE: '下派', REASSIGN: '改派', RETURN: '退回上级',
  REJECT: '拒绝派工', START: '开始', COMPLETE: '完成',
}
const NODE_STATUS_LABELS: Record<string, string> = {
  ACTIVE: '当前负责', DELEGATED: '已下派', REASSIGNED: '已改派',
  RETURNED: '已退回', COMPLETED: '已完成', CANCELLED: '已取消',
}

const loading = ref(false)
const list = ref<DispatchVO[]>([])
const total = ref(0)
const canAssign = ref(false)
const query = reactive<DispatchQuery & { scope?: string }>({
  pageNum: 1, pageSize: 10, orderNo: '', keyword: '', teamId: undefined, status: undefined, scope: '',
})

const deptTree = ref<any[]>([])
const equipmentList = ref<any[]>([])
const userOptions = ref<any[]>([])
const submitting = ref(false)

// ============ 基础 ============
function statusTag(status?: number): any {
  return { 0: 'info', 1: 'primary', 2: 'success', 3: 'warning', 4: 'success', 5: 'danger' }[status ?? 0] || 'info'
}
function statusLabel(row: DispatchVO): string {
  const st = row.dispatchStatus ?? 0
  return STATUS_LABELS[st] || row.statusLabel || String(st)
}
function fmtQty(v?: number | string | null): string {
  if (v === null || v === undefined || v === '') return '-'
  return String(Number(v))
}
function fmtTime(t?: string | null): string {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 16)
}
function hasAction(row: DispatchVO, action: string): boolean {
  return (row.allowedActions || []).includes(action)
}
function nodeStatusLabel(s?: string): string {
  return NODE_STATUS_LABELS[s || ''] || s || '-'
}
function nodeTagType(s?: string): any {
  return { ACTIVE: 'success', DELEGATED: 'primary', REASSIGNED: 'warning', RETURNED: 'danger', COMPLETED: 'info', CANCELLED: 'info' }[s || ''] || 'info'
}
function nodeTimelineType(s?: string): any {
  return { ACTIVE: 'success', DELEGATED: 'primary', REASSIGNED: 'warning', RETURNED: 'danger' }[s || ''] || 'info'
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
const handleSearch = () => { query.pageNum = 1; loadList() }
const handleReset = () => {
  Object.assign(query, { orderNo: '', keyword: '', teamId: undefined, status: undefined, scope: '', pageNum: 1 })
  loadList()
}

async function loadBaseData() {
  try { const res: any = await getMyDepts(); deptTree.value = res?.data || [] } catch { deptTree.value = [] }
  try { const res: any = await getCanAssign(); canAssign.value = res?.data === true } catch { canAssign.value = false }
  try { const res: any = await getEquipmentList({}); equipmentList.value = res?.data || [] } catch { equipmentList.value = [] }
}

// ============ 执行人选择（复用 OperatorPicker；文案 = 选择责任人/派给人员） ============
const pickerVisible = ref(false)
const pickerMode = ref<'assign' | 'delegate' | 'reassign' | 'batch'>('assign')
const pickerIds = ref<number[]>([])

function openOperatorPicker(mode: 'assign' | 'delegate' | 'reassign' | 'batch') {
  pickerMode.value = mode
  if (mode === 'assign') pickerIds.value = assignForm.targetUserId ? [assignForm.targetUserId] : []
  else if (mode === 'delegate') pickerIds.value = delegateForm.targetUserId ? [delegateForm.targetUserId] : []
  else if (mode === 'reassign') pickerIds.value = reassignForm.targetUserId ? [reassignForm.targetUserId] : []
  else pickerIds.value = [...(batchForm.operatorIds || [])]
  pickerVisible.value = true
}

function onPickerConfirm(ids: number[]) {
  const first = ids[0]
  if (pickerMode.value === 'assign') assignForm.targetUserId = first
  else if (pickerMode.value === 'delegate') delegateForm.targetUserId = first
  else if (pickerMode.value === 'reassign') reassignForm.targetUserId = first
  else batchForm.operatorIds = ids
}

function selectedAssigneeName(mode: 'assign' | 'delegate' | 'reassign'): string {
  const id = mode === 'assign' ? assignForm.targetUserId : mode === 'delegate' ? delegateForm.targetUserId : reassignForm.targetUserId
  const u = userOptions.value.find((x) => x.userId === id)
  return u ? (u.nickName || u.userName) : '已选择'
}

async function loadMyPersons() {
  try { const res: any = await getMyPersons(); userOptions.value = res?.data || [] } catch { userOptions.value = [] }
}

// ============ 初始派工（ASSIGN V1） ============
const assignVisible = ref(false)
const assignForm = reactive<DispatchAssignV1Payload & { processName?: string; orderNo?: string }>({
  executionId: 0, orderId: 0, targetUserId: 0, equipmentId: undefined, remark: '',
  processName: '', orderNo: '',
})

function openAssign(row: DispatchVO) {
  Object.assign(assignForm, {
    executionId: row.executionId, orderId: row.orderId, targetUserId: 0,
    equipmentId: row.equipmentId || undefined, remark: '', processName: row.processName, orderNo: row.orderNo,
  })
  assignVisible.value = true
  loadMyPersons()
}

async function handleAssign() {
  if (!assignForm.targetUserId) { ElMessage.warning('请选择责任人'); return }
  submitting.value = true
  try {
    await assignDispatchV1({
      executionId: assignForm.executionId,
      orderId: assignForm.orderId,
      targetUserId: assignForm.targetUserId,
      equipmentId: assignForm.equipmentId,
      remark: assignForm.remark,
    })
    ElMessage.success('初始派工成功')
    assignVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '派工失败')
  } finally { submitting.value = false }
}

// ============ 动作通用上下文 ============
const actionForm = reactive<{ dispatchId?: number; processName?: string; orderNo?: string; currentAssigneeName?: string }>({})

// ============ 继续派工（DELEGATE） ============
const delegateVisible = ref(false)
const delegateForm = reactive<DispatchDelegatePayload>({ targetUserId: 0, remark: '' })

function openDelegate(row: DispatchVO) {
  Object.assign(actionForm, { dispatchId: row.dispatchId, processName: row.processName, orderNo: row.orderNo, currentAssigneeName: row.currentAssigneeName })
  Object.assign(delegateForm, { targetUserId: 0, remark: '' })
  delegateVisible.value = true
  loadMyPersons()
}

async function handleDelegate() {
  if (!delegateForm.targetUserId) { ElMessage.warning('请选择责任人'); return }
  submitting.value = true
  try {
    await delegateDispatch(actionForm.dispatchId!, { targetUserId: delegateForm.targetUserId, remark: delegateForm.remark })
    ElMessage.success('已继续派工')
    delegateVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '继续派工失败')
  } finally { submitting.value = false }
}

// ============ 改派（REASSIGN） ============
const reassignVisible = ref(false)
const reassignForm = reactive<DispatchReassignPayload>({ targetUserId: 0, reason: '' })

function openReassign(row: DispatchVO) {
  Object.assign(actionForm, { dispatchId: row.dispatchId, processName: row.processName, orderNo: row.orderNo, currentAssigneeName: row.currentAssigneeName })
  Object.assign(reassignForm, { targetUserId: 0, reason: '' })
  reassignVisible.value = true
  loadMyPersons()
}

async function handleReassign() {
  if (!reassignForm.targetUserId) { ElMessage.warning('请选择责任人'); return }
  submitting.value = true
  try {
    await reassignDispatch(actionForm.dispatchId!, { targetUserId: reassignForm.targetUserId, reason: reassignForm.reason })
    ElMessage.success('改派成功')
    reassignVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '改派失败')
  } finally { submitting.value = false }
}

// ============ 退回上级（RETURN） ============
const returnVisible = ref(false)
const returnForm = reactive<DispatchReturnPayload>({ reason: '' })

function openReturn(row: DispatchVO) {
  Object.assign(actionForm, { dispatchId: row.dispatchId, processName: row.processName, orderNo: row.orderNo, currentAssigneeName: row.currentAssigneeName })
  returnForm.reason = ''
  returnVisible.value = true
}

async function handleReturn() {
  if (!returnForm.reason.trim()) { ElMessage.warning('退回原因必填'); return }
  submitting.value = true
  try {
    await returnDispatch(actionForm.dispatchId!, { reason: returnForm.reason.trim() })
    ElMessage.success('已退回上级')
    returnVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '退回失败')
  } finally { submitting.value = false }
}

// ============ 责任链详情（Timeline，来自 /nodes） ============
const detailVisible = ref(false)
const detailDispatch = ref<DispatchVO | null>(null)
const detailCurrent = ref<DispatchNodeVO | null>(null)
const nodeList = ref<DispatchNodeVO[]>([])

async function openDetail(row: DispatchVO) {
  detailDispatch.value = row
  detailCurrent.value = null
  nodeList.value = []
  detailVisible.value = true
  try {
    const [nodesRes, curRes]: any = await Promise.all([
      getDispatchNodes(row.dispatchId!),
      getDispatchCurrentNode(row.dispatchId!),
    ])
    nodeList.value = nodesRes?.data || []
    detailCurrent.value = curRes?.data || null
  } catch {
    nodeList.value = []
    detailCurrent.value = null
  }
}

// ============ 批量派工（保留旧入口，调 batch-assign legacy API） ============
const batchVisible = ref(false)
const batchForm = reactive<{ orderId?: number; equipmentId?: number; operatorIds: number[]; batch: boolean }>({
  orderId: undefined, equipmentId: undefined, operatorIds: [], batch: true,
})
const orderOptions = ref<any[]>([])
const batchPendingCount = ref(0)

async function openBatchDialog() {
  Object.assign(batchForm, { orderId: undefined, equipmentId: undefined, operatorIds: [], batch: true })
  batchPendingCount.value = 0
  batchVisible.value = true
  loadMyPersons()
  if (!orderOptions.value.length) {
    try {
      const res: any = await getProductionOrderList({ pageNum: 1, pageSize: 200, orderType: 'all' } as any)
      orderOptions.value = res?.data?.records || res?.data || []
    } catch { orderOptions.value = [] }
  }
}

async function onBatchOrderChange(orderId: number) {
  try {
    const res: any = await getPendingDispatches(orderId)
    batchPendingCount.value = (res?.data || []).length
  } catch { batchPendingCount.value = 0 }
}

async function handleBatchAssign() {
  if (!batchForm.orderId) { ElMessage.warning('请选择工单'); return }
  if (!batchForm.equipmentId && (!batchForm.operatorIds || !batchForm.operatorIds.length)) {
    ElMessage.warning('设备/责任人至少指定一项'); return
  }
  submitting.value = true
  try {
    const { batchAssignDispatch } = await import('@/api/production/dispatch')
    const res: any = await batchAssignDispatch({
      orderId: batchForm.orderId, equipmentId: batchForm.equipmentId,
      operatorIds: batchForm.operatorIds, batch: true,
    } as any)
    ElMessage.success(`批量派工完成，共派 ${res?.data || 0} 道工序`)
    batchVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '批量派工失败')
  } finally { submitting.value = false }
}

// ============ 拒绝派工（旧 REJECT 整单退回，与 RETURN 区分） ============
const rejectVisible = ref(false)
const rejectTarget = ref<DispatchVO | null>(null)
const rejectReason = ref('')

function openReject(row: DispatchVO) {
  rejectTarget.value = row
  rejectReason.value = ''
  rejectVisible.value = true
}

async function handleReject() {
  if (!rejectReason.value.trim()) { ElMessage.warning('退回原因必填'); return }
  submitting.value = true
  try {
    await rejectDispatch(rejectTarget.value!.dispatchId!, rejectReason.value.trim())
    ElMessage.success('已整单退回，可重新派工')
    rejectVisible.value = false
    loadList()
  } catch (e: any) {
    ElMessage.error(e?.message || '退回失败')
  } finally { submitting.value = false }
}

// ============ 开始/完成（保留现状） ============
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

// ============ 流水 ============
const logsVisible = ref(false)
const logList = ref<DispatchLog[]>([])

function logType(action: string): any {
  return { ASSIGN: 'primary', DELEGATE: 'primary', REASSIGN: 'warning', RETURN: 'danger', REJECT: 'danger', START: 'primary', COMPLETE: 'success' }[action] || 'info'
}

async function openLogs(row: DispatchVO) {
  if (!row.dispatchId) return
  logList.value = []
  logsVisible.value = true
  try {
    const res: any = await getDispatchLogs(row.dispatchId)
    logList.value = res?.data || []
  } catch { logList.value = [] }
}

onMounted(() => {
  const route = useRoute()
  const orderNo = route.query.orderNo as string | undefined
  if (orderNo) query.orderNo = orderNo
  loadList()
  loadBaseData()
})
</script>

<style scoped>
.dispatch-page { padding: 16px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 20px; font-weight: 600; }
.filter-card { margin-bottom: 16px; }
.filter-bar { display: flex; gap: 10px; align-items: center; padding-bottom: 8px; flex-wrap: wrap; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.cur-assignee { font-weight: 500; color: #303133; }
.cur-section { margin: 14px 0 10px; }
.cur-title { font-size: 13px; font-weight: 600; color: #606266; margin-bottom: 8px; }
.cur-card { border: 1px solid #b3e19d; background: #f0f9eb; border-radius: 6px; padding: 8px 12px; }
.detail-actions { margin-top: 16px; }
</style>
