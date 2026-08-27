<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :inline="true" :model="queryParams" label-width="80px">
        <el-form-item label="样品单号">
          <el-input
            v-model="queryParams.orderNo"
            placeholder="请输入样品单号"
            clearable
            style="width: 200px"
            @keyup.enter="getList"
          />
        </el-form-item>
        <el-form-item label="客户名称">
          <el-input
            v-model="queryParams.customerName"
            placeholder="请输入客户名称"
            clearable
            style="width: 200px"
            @keyup.enter="getList"
          />
        </el-form-item>
        <el-form-item label="分组">
          <el-select v-model="queryParams.group" style="width: 140px">
            <el-option label="待接单" value="pending" />
            <el-option label="打样中" value="accepted" />
            <el-option label="全部" value="all" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="getList">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 统计角标 -->
    <el-card class="stat-card" shadow="never">
      <div class="stat-row">
        <div class="stat-item">
          <div class="stat-num">{{ pendingCount }}</div>
          <div class="stat-label">待接单</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">{{ workingCount }}</div>
          <div class="stat-label">打样中</div>
        </div>
        <div class="stat-item">
          <div class="stat-num">{{ totalCount }}</div>
          <div class="stat-label">全部</div>
        </div>
      </div>
    </el-card>

    <!-- 样品单列表 -->
    <el-card class="table-card" shadow="never">
      <el-table :data="tableData" v-loading="loading" border stripe>
        <el-table-column label="样品单号" prop="orderNo" width="150">
          <template #default="scope">
            <el-button link type="primary" @click="openWorkbench(scope.row)">{{
              scope.row.orderNo
            }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="客户" prop="customerName" min-width="130" show-overflow-tooltip />
        <el-table-column label="轮次" width="100" align="center">
          <template #default="scope">Round {{ scope.row.sampleRound || 1 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="scope">
            <el-tag :type="sampleStatusTag(scope.row.sampleStatus)" size="small">
              {{ sampleStatusText(scope.row.sampleStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <!-- 进度可视化 -->
        <el-table-column label="当前工序" width="110" align="center">
          <template #default="scope">{{ scope.row.currentProcess || '-' }}</template>
        </el-table-column>
        <el-table-column label="工序数" width="100" align="center">
          <template #default="scope">
            <span v-if="doneCountMap[scope.row.orderId] !== undefined">
              {{ doneCountMap[scope.row.orderId] }} / {{ processCountMap[scope.row.orderId] ?? 0 }}
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="工时(h)" width="90" align="center">
          <template #default="scope">
            <span v-if="summaryMap[scope.row.orderId]">{{
              summaryMap[scope.row.orderId].totalHours ?? '-'
            }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="材料成本(¥)" width="110" align="center">
          <template #default="scope">
            <span v-if="summaryMap[scope.row.orderId]">{{
              summaryMap[scope.row.orderId].materialCost ?? '-'
            }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <!-- 操作人展示 -->
        <el-table-column label="接单人" width="100" align="center">
          <template #default="scope">{{ scope.row.engineeringAcceptor || '-' }}</template>
        </el-table-column>
        <el-table-column label="最近操作" width="150" align="center">
          <template #default="scope">
            <span v-if="lastOperatorMap[scope.row.orderId]">
              {{ lastOperatorMap[scope.row.orderId].operator }}
              <div style="font-size: 11px; color: #909399">
                {{ lastOperatorMap[scope.row.orderId].time }}
              </div>
            </span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" align="center" fixed="right">
          <template #default="scope">
            <template v-if="canTransfer(scope.row)">
              <el-button
                v-hasPermi="['sales:sample:convert']"
                type="warning"
                link
                size="small"
                @click="handleTransfer(scope.row)"
                >资料转移</el-button
              >
            </template>
            <template v-else-if="isTransferred(scope.row)">
              <el-tag size="small" type="success">已转量产</el-tag>
            </template>
            <template v-else>
              <el-button
                v-if="canEnterWorkbench(scope.row)"
                type="primary"
                link
                size="small"
                :loading="acceptingOrderId === scope.row.orderId"
                @click="openWorkbench(scope.row)"
              >
                进入打样
              </el-button>
              <el-button
                v-if="canAccept(scope.row)"
                type="primary"
                link
                size="small"
                :loading="acceptingOrderId === scope.row.orderId"
                @click="handleAcceptClick(scope.row)"
              >
                接单打样
              </el-button>
            </template>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 工程打样工作台（独立路由页，标签页打开） -->

    <!-- 资料转移 · 轻量版弹窗（2026-08-12：打样成功后在此操作） -->
    <SampleTransferDialog
      v-model="transferDialogVisible"
      :order-id="transferRow?.orderId"
      @success="onTransferSuccess"
    />
  </div>
</template>
gi

<script setup lang="ts">
import { ref, reactive, onMounted, onActivated } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { SampleOrderStatus, SampleOrderStatusEnum } from '@/enums/sales'
import SampleTransferDialog from '@/views/sales/sample-order/components/SampleTransferDialog.vue'
import { useSampleWorkbench } from './composables/useSampleWorkbench'
const router = useRouter()

defineOptions({ name: 'SampleWorkbench' })

// 全部状态与逻辑来自 composable（dev-20260811-008 组件化）
const {
  handleAccept,
  handleReject,
  // handleTransfer, transferDialogVisible, onTransferSuccess,
} = useSampleWorkbench()

// 防止重复点击导致重复接单
const acceptingOrderId = ref<number | null>(null)

function sampleStatusText(status: number | undefined | null): string {
  return status == null ? '未知' : SampleOrderStatusEnum.getLabel(status)
}
function sampleStatusTag(status: number | undefined | null): any {
  return status == null ? 'info' : SampleOrderStatusEnum.getTagProps(status).type || 'info'
}

// ===== 操作谓词（canXXX）：状态机判断统一入口，仅引用枚举成员 =====
// 资料转移：仅样品确认(6)可转移
function canTransfer(row: any): boolean {
  return row?.sampleStatus === SampleOrderStatus.CONFIRMED.value
}
// 已转量产展示
function isTransferred(row: any): boolean {
  return row?.sampleStatus === SampleOrderStatus.TRANSFERRED.value
}
// 已接单：进入打样平台
function canEnterWorkbench(row: any): boolean {
  return !!row?.engineeringAcceptor
}
// 接单打样：待打样(2)或打样中(3)且未接单可接单（新模型：无审核环节，工程直接接单）
function canAccept(row: any): boolean {
  return (
    [SampleOrderStatus.REQUEST.value, SampleOrderStatus.ENGINEERING.value].includes(row?.sampleStatus) &&
    !row?.engineeringAcceptor
  )
}

// 资料转移（轻量版弹窗，2026-08-12 入口移至打样平台）
const transferDialogVisible = ref(false)
const transferRow = ref<any>(null)
function handleTransfer(row: any) {
  if (!row?.orderId) return
  transferRow.value = row
  transferDialogVisible.value = true
}
function onTransferSuccess() {
  getList()
}

const loading = ref(false)
const tableData = ref<any[]>([])
const totalCount = ref(0)
const pendingCount = ref(0)
const workingCount = ref(0)

const queryParams = reactive({
  orderNo: '',
  customerName: '',
  group: 'all',
})

// 进度/汇总/最近操作缓存
const processCountMap = ref<Record<number, number>>({})
const doneCountMap = ref<Record<number, number>>({})
const summaryMap = ref<Record<number, any>>({})
const lastOperatorMap = ref<Record<number, any>>({})

function openWorkbench(row: any) {
  // 独立路由页打开（标签页），不在侧边栏显示
  router.push({ path: '/engineering-workbench/workbench', query: { orderId: row.orderId } })
}

// 点击接单，弹窗确认框，确认接单调用composable的接单接口，成功后打开打样工作台
function handleAcceptClick(row: any) {
  if (!row?.orderId || acceptingOrderId.value !== null) return
  ElMessageBox.confirm('确认接单打样吗？', '提示', { type: 'warning' })
    .then(async () => {
      acceptingOrderId.value = row.orderId
      try {
        const accepted = await handleAccept(row.orderId)
        // 后台确认接单成功后才刷新列表并打开打样工作台
        if (!accepted) return
        ElMessage.success('接单成功')
        await getList()
        openWorkbench(row)
      } catch (e: any) {
        ElMessage.error(e?.message || '接单失败')
      } finally {
        acceptingOrderId.value = null
      }
    })
    .catch(() => {})
}

async function getList() {
  loading.value = true
  try {
    // 2026-08-12：查全部状态（含打样成功/已确认，供资料转移入口），分组筛选保留
    const params: any = {}
    if (queryParams.group === 'pending') params.hasAcceptor = false
    else if (queryParams.group === 'accepted') params.hasAcceptor = true
    const res = await sampleOrderApi.list(params)
    let rows: any[] = res.data || []
    // 本地过滤：单号/客户名（列表接口无此参数）
    if (queryParams.orderNo)
      rows = rows.filter((r) => (r.orderNo || '').includes(queryParams.orderNo))
    if (queryParams.customerName)
      rows = rows.filter((r) => (r.customerName || '').includes(queryParams.customerName))
    tableData.value = rows
    totalCount.value = rows.length
    pendingCount.value = rows.filter((r) => !r.engineeringAcceptor).length
    workingCount.value = rows.filter((r) => r.engineeringAcceptor).length
    loadExtras(rows)
  } catch (e: any) {
    ElMessage.error(e?.message || '加载打样平台失败')
  } finally {
    loading.value = false
  }
}

// 并行加载每单工序数/汇总/最近操作
async function loadExtras(rows: any[]) {
  processCountMap.value = {}
  doneCountMap.value = {}
  summaryMap.value = {}
  lastOperatorMap.value = {}
  const orderIds = rows.map((r) => r.orderId).filter(Boolean)
  await Promise.all(
    orderIds.map(async (oid: number) => {
      try {
        const procs: any = await sampleOrderApi.listProcesses(oid)
        const list: any[] = procs.data || []
        processCountMap.value[oid] = list.length
        doneCountMap.value[oid] = list.filter((p) => p.status === 2).length
        const sum = await sampleOrderApi.getSummary(oid)
        if (sum.data) summaryMap.value[oid] = sum.data
        if (list.length) {
          const last = list[list.length - 1]
          lastOperatorMap.value[oid] = {
            operator: last.operator || '-',
            time: (last.startTime || '').replace('T', ' ').slice(0, 16),
          }
        }
      } catch {
        /* 单条失败不阻塞列表 */
      }
    })
  )
}

function resetQuery() {
  queryParams.orderNo = ''
  queryParams.customerName = ''
  queryParams.group = 'all'
  getList()
}

onMounted(() => getList())
onActivated(() => getList())
</script>

<style scoped>
.search-card,
.stat-card,
.table-card {
  margin-bottom: 12px;
}
.stat-row {
  display: flex;
  gap: 40px;
}
.stat-item {
  text-align: center;
}
.stat-num {
  font-size: 24px;
  font-weight: 700;
  color: #409eff;
}
.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
</style>
