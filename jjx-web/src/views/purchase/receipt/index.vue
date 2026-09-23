<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="query" inline>
        <el-form-item label="订单号"><el-input v-model="query.orderNo" clearable placeholder="订单号" style="width: 180px" /></el-form-item>
        <el-form-item label="供应商"><el-input v-model="query.supplierName" clearable placeholder="供应商名称" style="width: 180px" /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="filteredRows" border row-key="orderId" @expand-change="handleExpand">
        <el-table-column type="expand">
          <template #default="{ row }">
            <div class="expand-wrap" v-loading="detailLoading[row.orderId]">
              <!-- 明细懒加载：/purchase/receipt/list 只返回订单（后端 VO 的 items 被 ignore），
                   明细走 /purchase/receipt/order/{orderId}（dev-20260923-004 修复：原来展开永远是空的） -->
              <el-table :data="detailMap[row.orderId] || []" border size="small" class="detail-table">
                <el-table-column prop="materialCode" label="物料编码" min-width="120" />
                <el-table-column prop="materialName" label="物料名称" min-width="140" />
                <el-table-column prop="materialSpec" label="规格" min-width="100" />
                <el-table-column prop="unit" label="单位" width="60" />
                <el-table-column label="订购数量" width="90" align="right">
                  <template #default="{ row: it }">{{ fmtQty(it.quantity) }}</template>
                </el-table-column>
                <el-table-column label="已收数量" width="90" align="right">
                  <template #default="{ row: it }">{{ fmtQty(it.receivedQuantity) }}</template>
                </el-table-column>
                <el-table-column label="未收" width="90" align="right">
                  <template #default="{ row: it }">{{ fmtQty(remainOf(it)) }}</template>
                </el-table-column>
                <el-table-column label="收货状态" width="100">
                  <template #default="{ row: it }">
                    <el-tag :type="ReceiptStatusEnum.getTagProps(it.receiptStatus).type">{{ ReceiptStatusEnum.getLabel(it.receiptStatus) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="检验结果" width="100">
                  <template #default="{ row: it }">
                    <el-tag v-if="it.inspectionResult" :type="InspectionResultEnum.getTagProps(it.inspectionResult).type">{{ InspectionResultEnum.getLabel(it.inspectionResult) }}</el-tag>
                    <span v-else>-</span>
                  </template>
                </el-table-column>
              </el-table>

              <!-- 收货生成的入库单（收货≠入库：仓库确认后才加库存；来料检验按入库单建） -->
              <div class="inbound-line">
                <span class="inbound-label">收货入库单：</span>
                <template v-if="(inboundMap[row.orderId] || []).length">
                  <el-tag
                    v-for="ib in inboundMap[row.orderId]"
                    :key="ib.inboundId"
                    class="inbound-tag"
                    type="info"
                    @click="gotoIqc(ib.inboundNo)"
                  >{{ ib.inboundNo }}</el-tag>
                  <span class="inbound-hint">（点击可去来料检验）</span>
                </template>
                <span v-else class="inbound-hint">尚未收货</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="orderNo" label="订单号" min-width="160" />
        <el-table-column prop="supplierName" label="供应商" min-width="150" />
        <el-table-column prop="orderDate" label="订单日期" width="110" />
        <el-table-column label="订单金额" width="120" align="right">
          <template #default="{ row }">{{ money(row.orderTotalAmount) }}</template>
        </el-table-column>
        <el-table-column label="收货进度" width="120">
          <template #default="{ row }">
            <el-tag :type="ReceiptStatusEnum.getTagProps(row.receiptStatus).type">{{ ReceiptStatusEnum.getLabel(row.receiptStatus) }}</el-tag>
          </template>
        </el-table-column>
        <TableActionColumn
          :actions="rowActions"
          width="190"
          @action="(key, row) => handleRowAction(key, row)"
        />
      </el-table>
    </el-card>

    <!-- 收货（共享组件：与采购订单页同一实现，dev-20260923-004） -->
    <PurchaseReceiveDialog
      v-model:visible="receiveVisible"
      :orderId="currentOrderId"
      :orderNo="currentOrderNo"
      @success="load"
      @goto-iqc="gotoIqc"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { listReceipt, getReceiptsByOrder, getReceiptInboundOrders } from '@/api/purchase/receipt'
import { ReceiptStatusEnum, InspectionResultEnum } from '@/enums/purchase/receipt'
import TableActionColumn from '@/components/common-ui/TableActionColumn/index.vue'
import type { TableAction } from '@/components/common-ui/TableActionColumn/types'
import PurchaseReceiveDialog from '@/views/purchase/components/PurchaseReceiveDialog.vue'

/**
 * 采购收货（dev-20260923-004 改造）
 *
 * 口径：
 *  - 列表 = 待收货订单（receipt_status ∈ {0,1} 且 approval_status ∈ {3,4}）；订单行可直接「收货」（不再依赖展开明细，明细懒加载展示进度）
 *  - 收货 = 共享弹窗 → POST /purchase/receipt/confirm-batch（收货域权限 purchase:receipt:add）
 *  - 收货只生成入库单（待审批/待检验），库存由 库存管理→入库作业 确认后才增加
 *  - 「来料检验」不在本页录入：跳转 质量管理→来料检验（IQC 检验批按入库单建），故按钮按 quality:lot:view 显示
 */
defineOptions({ name: 'PurchaseReceipt' })

const router = useRouter()

const loading = ref(false)
const rows = ref<any[]>([])
const query = reactive({ orderNo: '', supplierName: '' })

/** 展开行明细（懒加载）+ 收货入库单 */
const detailMap = reactive<Record<number, any[]>>({})
const inboundMap = reactive<Record<number, any[]>>({})
const detailLoading = reactive<Record<number, boolean>>({})

const receiveVisible = ref(false)
const currentOrderId = ref<number>()
const currentOrderNo = ref('')

const rowActions: TableAction<any>[] = [
  {
    key: 'receive',
    label: '收货',
    type: 'primary',
    permission: 'purchase:receipt:add',
    visible: ({ row }) => row.receiptStatus !== ReceiptStatusEnum.RECEIVED.value,
    order: 10,
  },
  {
    key: 'inspect',
    label: '来料检验',
    type: 'warning',
    // 检验在质量域（IQC 检验批按入库单建）→ 用 IQC 读权限控制，不再借用收货权限
    permission: 'quality:lot:view',
    order: 20,
  },
]

function handleRowAction(key: string, row: any) {
  if (key === 'receive') openReceive(row)
  if (key === 'inspect') inspectOrder(row)
}

const money = (v?: number) => (v == null ? '-' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 }))
function fmtQty(v?: number | string | null): string {
  const n = Number(v ?? 0)
  return Number.isFinite(n) ? String(Math.round(n * 100) / 100) : '0'
}
function remainOf(item: any): number {
  return Math.round((Number(item.quantity || 0) - Number(item.receivedQuantity || 0)) * 100) / 100
}

const filteredRows = computed(() => {
  const q = query
  if (!q.orderNo && !q.supplierName) return rows.value
  return rows.value.filter(
    (r) =>
      (!q.orderNo || (r.orderNo || '').includes(q.orderNo)) &&
      (!q.supplierName || (r.supplierName || '').includes(q.supplierName))
  )
})

async function load() {
  loading.value = true
  try {
    const res: any = await listReceipt()
    rows.value = res.data || []
    // 收货后刷新已展开行的明细/入库单
    Object.keys(detailMap).forEach((k) => delete detailMap[Number(k)])
    Object.keys(inboundMap).forEach((k) => delete inboundMap[Number(k)])
  } finally {
    loading.value = false
  }
}
function search() {
  /* 前端过滤由 computed 响应 query 变化 */
}
function reset() {
  Object.assign(query, { orderNo: '', supplierName: '' })
}

/** 展开行：懒加载明细 + 收货入库单 */
async function handleExpand(row: any, expandedRows: any[]) {
  const opened = Array.isArray(expandedRows) ? expandedRows.length > 0 : !!expandedRows
  if (!opened) return
  if (!detailMap[row.orderId]) {
    detailLoading[row.orderId] = true
    try {
      const [items, inbounds] = await Promise.all([
        getReceiptsByOrder(row.orderId),
        getReceiptInboundOrders(row.orderId).catch(() => ({ data: [] } as any)),
      ])
      detailMap[row.orderId] = (items as any)?.data || []
      inboundMap[row.orderId] = (inbounds as any)?.data || []
    } catch (e: any) {
      ElMessage.error(e?.message || '加载明细失败')
    } finally {
      detailLoading[row.orderId] = false
    }
  }
}

function openReceive(row: any) {
  currentOrderId.value = Number(row.orderId)
  currentOrderNo.value = row.orderNo
  receiveVisible.value = true
}

/** 来料检验：取该订单最新的收货入库单 → 跳到 IQC（带 inboundNo） */
async function inspectOrder(row: any) {
  try {
    const res: any = await getReceiptInboundOrders(row.orderId)
    const list = res.data || []
    if (!list.length) {
      ElMessage.warning('该订单还没有收货入库单，请先收货')
      return
    }
    gotoIqc(String(list[0].inboundNo))
  } catch (e: any) {
    ElMessage.error(e?.message || '查询收货入库单失败')
  }
}

function gotoIqc(inboundNo: string) {
  if (!inboundNo) return
  router.push({ path: '/inventory/iqc', query: { inboundNo } })
}

onMounted(load)
</script>

<style scoped>
.search-card {
  margin-bottom: 16px;
}
.detail-table {
  margin: 8px 16px 4px;
  width: calc(100% - 32px);
}
.expand-wrap {
  padding-bottom: 6px;
}
.inbound-line {
  margin: 0 16px 8px;
  font-size: 12px;
  color: #909399;
}
.inbound-label {
  color: #909399;
}
.inbound-tag {
  margin-right: 6px;
  cursor: pointer;
}
.inbound-hint {
  color: #a8abb2;
}
</style>
