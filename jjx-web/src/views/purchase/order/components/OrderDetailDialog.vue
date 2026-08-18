<template>
  <div v-loading="loading">
    <el-dialog
      :title="title"
      :model-value="props.visible"
      width="1200px"
      append-to-body
      :close-on-click-modal="false"
      @close="handleClose"
      @update:model-value="(val: boolean) => emit('update:visible', val)"
    >
      <template v-if="orderDetail">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号" :span="2">{{
            orderDetail.orderNo
          }}</el-descriptions-item>
          <el-descriptions-item label="供应商">{{ orderDetail.supplierName }}</el-descriptions-item>
          <el-descriptions-item label="订单日期">{{ orderDetail.orderDate }}</el-descriptions-item>
          <el-descriptions-item label="交货日期">{{
            orderDetail.expectedDeliveryDate
          }}</el-descriptions-item>
          <el-descriptions-item label="实际交货日期">{{
            (orderDetail as any).actualDeliveryDate || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="订单类型">
            <el-tag :type="orderDetail.orderType === 'urgent' ? 'danger' : 'info'" size="small">
              {{ orderDetail.orderType === 'urgent' ? '紧急订单' : '普通订单' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="币种">{{ orderDetail.currency }}</el-descriptions-item>
          <el-descriptions-item label="合同号">{{
            orderDetail.contractNo || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="交货方式">{{
            orderDetail.deliveryMethod || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="交货地址" :span="2">{{
            orderDetail.deliveryAddress || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="审批状态">
            <el-tag
              :type="PurchaseEnum.approvalStatus.getTagProps(orderDetail.approvalStatus).type"
              size="small"
            >
              {{ PurchaseEnum.approvalStatus.getLabel(orderDetail.approvalStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="收货状态">
            <el-tag
              :type="PurchaseEnum.receiptStatus.getTagProps(orderDetail.receiptStatus).type"
              size="small"
            >
              {{ PurchaseEnum.receiptStatus.getLabel(orderDetail.receiptStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="付款状态">
            <el-tag
              :type="PurchaseEnum.paymentStatus.getTagProps(orderDetail.paymentStatus).type"
              size="small"
            >
              {{ PurchaseEnum.paymentStatus.getLabel(orderDetail.paymentStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="紧急">
            <el-tag :type="orderDetail.urgentFlag ? 'danger' : 'info'" size="small">
              {{ orderDetail.urgentFlag ? '是' : '否' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="订单总金额">
            <span style="font-weight: bold; color: #f56c6c">
              {{ orderDetail.currency }} {{ orderDetail.orderTotalAmount?.toFixed(2) }}
            </span>
          </el-descriptions-item>
          <el-descriptions-item label="创建时间">{{
            orderDetail.createTime || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{
            orderDetail.updateTime || '-'
          }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">订单明细</el-divider>

        <el-table :data="orderDetail.items" border style="width: 100%">
          <el-table-column label="序号" type="index" width="60" align="center" />
          <el-table-column label="物料编码" prop="materialCode" width="140" />
          <el-table-column label="物料名称" prop="materialName" width="140" />
          <el-table-column label="规格型号" prop="materialSpec" width="120" />
          <el-table-column label="单位" prop="unit" width="60" />
          <el-table-column label="数量" prop="quantity" width="80" align="right" />
          <el-table-column label="单价" prop="unitPrice" width="100" align="right">
            <template #default="scope">
              {{ scope.row.unitPrice?.toFixed(2) }}
            </template>
          </el-table-column>
          <el-table-column label="金额" prop="amount" width="100" align="right">
            <template #default="scope">
              <span style="font-weight: bold">{{ scope.row.amount?.toFixed(2) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="备注" prop="remark" min-width="120" />
        </el-table>

        <el-row style="margin-top: 16px; text-align: right">
          <el-col :span="24">
            <span style="font-size: 16px; font-weight: bold">
              合计金额：{{ orderDetail.currency }} {{ totalAmount.toFixed(2) }}
            </span>
          </el-col>
        </el-row>

        <!-- 入库凭证（当前 / 历史） -->
        <el-divider content-position="left">入库凭证</el-divider>

        <!-- 入库凭证（当前 / 历史）2026-08-18：多次收货每张入库单独立展示，可区分每次收货 -->
        <el-divider content-position="left">入库凭证</el-divider>

        <!-- 待确认凭证（未完成：待审批/草稿/已驳回等，可能多张=多次收货） -->
        <div v-for="ib in currentInbounds" :key="ib.inboundId" class="voucher-block">
          <div class="voucher-header">
            <el-tag type="warning" size="small">待确认凭证</el-tag>
            <span class="voucher-no">{{ ib.inboundNo }}</span>
            <el-tag :type="getStatusTag(ib.status)" size="small">{{ ib.statusName || inboundStatusText(ib.status) }}</el-tag>
            <span class="voucher-qty">数量：{{ formatNumber(ib.totalQuantity) }}</span>
            <el-button link type="primary" @click="viewVoucher(ib)">查看详情</el-button>
            <span v-if="ib.status === 1" class="voucher-tip">待仓库在【入库管理】审批</span>
          </div>
          <el-table :data="ib.items || []" size="small" border style="width: 100%">
            <el-table-column label="物料编码" prop="materialCode" width="130" />
            <el-table-column label="物料名称" prop="materialName" min-width="140" show-overflow-tooltip />
            <el-table-column label="批次号" prop="batchNo" width="170" />
            <el-table-column label="数量" prop="quantity" width="90" align="right" />
            <el-table-column label="库位" prop="locationCode" width="90" />
            <el-table-column label="生产日期" prop="productionDate" width="110" align="center" />
            <el-table-column label="到期日期" prop="expiryDate" width="110" align="center" />
          </el-table>
        </div>
        <el-empty v-else-if="!currentInbounds.length && orderDetail.receiptStatus > 0" description="暂无待确认凭证（已全部入库或未生成）" :image-size="50" />

        <!-- 历史凭证（已完成） -->
        <div v-if="historyInbounds.length" class="voucher-block">
          <div class="voucher-header">
            <el-tag type="info" size="small">历史凭证</el-tag>
            <span class="voucher-qty">共 {{ historyInbounds.length }} 张</span>
          </div>
          <el-table :data="historyInbounds" size="small" border style="width: 100%">
            <el-table-column label="入库单号" prop="inboundNo" min-width="170" />
            <el-table-column label="仓库" prop="warehouseName" width="110" />
            <el-table-column label="数量" prop="totalQuantity" width="90" align="right" />
            <el-table-column label="入库日期" prop="inboundDate" width="110" align="center" />
            <el-table-column label="创建人" prop="createBy" width="100" />
            <el-table-column label="操作" width="80" align="center">
              <template #default="{ row }">
                <el-button link type="primary" @click="viewVoucher(row)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </template>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleClose">关 闭</el-button>
        </div>
      </template>
    </el-dialog>

    <!-- 凭证详情弹窗（复用入库单公共详情组件） -->
    <el-dialog
      :title="`入库凭证 - ${voucherNo || ''}`"
      v-model="voucherDialogVisible"
      width="1000px"
      append-to-body
      destroy-on-close
    >
      <InboundDetail v-if="voucherDialogVisible && voucherId" :inbound-id="voucherId" />
      <template #footer>
        <el-button @click="voucherDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { PurchaseEnum } from '@/enums/purchase'
import { getOrder } from '@/api/purchase/order'
import { inboundApi } from '@/api/inventory/inbound'
import InboundDetail from '@/views/inventory/inbound/components/InboundDetail.vue'
import { formatNumber } from '@/utils/format'
import type { PurchaseOrderVO } from '@/types/purchase/order'
import type { InboundVO } from '@/types/inventory/inbound'

const props = defineProps<{
  visible: boolean
  orderId?: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
}>()

const loading = ref(false)
const orderDetail = ref<PurchaseOrderVO | null>(null)

const title = computed(() => `订单详情 - ${orderDetail.value?.orderNo || ''}`)

const totalAmount = computed(() => {
  if (!orderDetail.value?.items) return 0
  return orderDetail.value.items.reduce((sum, item) => sum + (item.amount || 0), 0)
})

// ===== 入库凭证（当前/历史） =====
const inboundList = ref<InboundVO[]>([])
const currentInbounds = computed(() => inboundList.value.filter((i) => i.status !== 10))
const historyInbounds = computed(() => inboundList.value.filter((i) => i.status === 10))
const voucherDialogVisible = ref(false)
const voucherId = ref<number | null>(null)
const voucherNo = ref('')

const viewVoucher = (row: InboundVO) => {
  voucherId.value = Number(row.inboundId)
  voucherNo.value = row.inboundNo || ''
  voucherDialogVisible.value = true
}

// 加载该采购单的所有入库单（按来源单号查）
const loadInbounds = async () => {
  inboundList.value = []
  if (!orderDetail.value?.orderNo) return
  try {
    const res = await inboundApi.list({
      sourceType: 'PURCHASE',
      sourceNo: orderDetail.value.orderNo,
      pageSize: 100,
      current: 1,
    } as any)
    inboundList.value = res.data?.records || []
  } catch (error) {
    console.error('加载入库凭证失败:', error)
  }
}

const inboundStatusTextMap: Record<number, string> = {
  0: '草稿', 1: '待审批', 2: '已批准', 3: '已驳回', 4: '处理中', 5: '已确认',
  6: '已出库', 7: '已入库', 8: '已关闭', 9: '已取消', 10: '已完成', 11: '已处理', 12: '调拨中',
}
const inboundStatusText = (status?: number) =>
  status === undefined || status === null ? '-' : inboundStatusTextMap[status] || String(status)

const getStatusTag = (status?: number): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const map: Record<number, 'success' | 'warning' | 'info' | 'danger' | undefined> = {
    0: 'info', 1: 'warning', 2: 'success', 3: 'danger', 4: 'warning', 5: 'success',
    6: 'success', 7: 'success', 8: 'info', 9: 'danger', 10: 'success', 11: 'success', 12: 'warning',
  }
  return status === undefined || status === null ? undefined : map[status]
}

// 监听 visible 变化
watch(
  () => props.visible,
  async (val) => {
    if (val && props.orderId) {
      await loadDetail()
    }
  }
)

// 加载详情
const loadDetail = async () => {
  loading.value = true
  try {
    const response = await getOrder(Number(props.orderId) as any)
    orderDetail.value = response.data || null
    await loadInbounds()
  } catch (error) {
    console.error('加载订单详情失败:', error)
    ElMessage.error('加载订单详情失败')
  } finally {
    loading.value = false
  }
}

// 关闭
const handleClose = () => {
  orderDetail.value = null
  inboundList.value = []
  emit('update:visible', false)
}
</script>

<style scoped>
.voucher-block {
  margin-bottom: 14px;
  padding: 10px 12px;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
}
.voucher-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.voucher-no {
  font-weight: bold;
  color: #303133;
}
.voucher-qty {
  color: #606266;
  font-size: 13px;
}
.voucher-tip {
  color: #e6a23c;
  font-size: 12px;
}
</style>
