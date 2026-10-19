<!-- components/OrderDetailDrawer.vue -->
<template>
  <el-dialog
    v-model="drawerVisible"
    :title="`订单详情 - ${orderDetail?.orderNo || ''}`"
    width="70%"
    max-width="1200px"
    :close-on-click-modal="false"
    destroy-on-close
    @close="handleClose"
  >
    <div v-loading="loading" class="order-detail">
      <template v-if="orderDetail">
        <!-- 基本信息 -->
        <el-card class="info-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span>基本信息</span>
              <el-tag :type="SalesOrderStatusEnum.getTagProps(orderDetail.orderStatus).type">
                {{ SalesOrderStatusEnum.getLabel(orderDetail.orderStatus) }}
              </el-tag>
            </div>
          </template>

          <el-descriptions :column="3" border>
            <el-descriptions-item label="订单编号">
              {{ orderDetail.orderNo }}
            </el-descriptions-item>
            <el-descriptions-item label="订单类型">
              {{ OrderTypeEnum.getLabel(orderDetail.orderType) }}
            </el-descriptions-item>
            <el-descriptions-item label="客户名称">
              {{ orderDetail.customerName }}
            </el-descriptions-item>
            <el-descriptions-item label="联系人">
              {{ orderDetail.contactPerson || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="联系电话">
              {{ orderDetail.contactPhone || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="是否急单">
              <el-tag v-if="orderDetail.isUrgent === 1" type="danger" size="small"> 是 </el-tag>
              <span v-else>否</span>
            </el-descriptions-item>
            <el-descriptions-item label="订单日期">
              {{ parseDate(orderDetail.orderDate) }}
            </el-descriptions-item>
            <el-descriptions-item label="交货日期">
              {{ parseDate(orderDetail.deliveryDate) }}
            </el-descriptions-item>
            <el-descriptions-item label="币种">
              {{ orderDetail.currency }}
            </el-descriptions-item>
            <el-descriptions-item label="汇率">
              {{ orderDetail.exchangeRate }}
            </el-descriptions-item>
            <el-descriptions-item label="付款条件">
              {{ orderDetail.paymentTerms || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="交货条件">
              {{ orderDetail.deliveryTerms || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="交货地址" :span="3">
              {{ orderDetail.deliveryAddress || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="备注" :span="3">
              {{ orderDetail.remark || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 金额信息 -->
        <el-card class="info-card" shadow="never">
          <template #header>
            <span>金额信息</span>
          </template>

          <el-alert
            v-if="isForeignOrder"
            class="money-note"
            type="info"
            :closable="false"
            show-icon
            title="金额按人民币（CNY）口径存储；折合原币按 1 外币 = N 人民币计算，仅作溯源展示，不改变已存金额。"
          />
          <el-alert
            v-if="isForeignOrder && !rateValid"
            class="money-note"
            type="warning"
            :closable="false"
            show-icon
            title="该单汇率缺失或无效，无法折算原币；系统未按 1 冒充汇率。"
          />
          <el-descriptions :column="3" border>
            <el-descriptions-item label="含税总额（折前）">
              <strong class="text-primary">{{ cnyText(orderDetail.totalAmount) }}</strong>
              <div v-if="isForeignOrder" class="money-sub">
                折合原币：{{ originalText(orderDetail.totalAmount) }}
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="运费（不计税）">
              <span v-if="orderDetail.shippingFee == null">金额组成待确认</span>
              <template v-else>
                <span>{{ cnyText(orderDetail.shippingFee) }}</span>
                <div v-if="isForeignOrder" class="money-sub">
                  折合原币：{{ originalText(orderDetail.shippingFee) }}
                </div>
              </template>
            </el-descriptions-item>
            <el-descriptions-item label="税率">
              {{ (Number(orderDetail.taxRate) || 0).toFixed(2) }}%
            </el-descriptions-item>
            <el-descriptions-item label="税额">
              <span>{{ cnyText(orderDetail.taxAmount) }}</span>
              <div v-if="isForeignOrder" class="money-sub">
                折合原币：{{ originalText(orderDetail.taxAmount) }}
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="折扣率">
              {{ (Number(orderDetail.discountRate) * 100).toFixed(2) }}%
            </el-descriptions-item>
            <el-descriptions-item label="优惠金额">
              <span>{{ cnyText(orderDetail.discountAmount) }}</span>
              <div v-if="isForeignOrder" class="money-sub">
                折合原币：{{ originalText(orderDetail.discountAmount) }}
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="实际应收" :span="3">
              <strong class="text-success" style="font-size: 18px">
                {{ cnyText(orderDetail.finalAmount) }}
              </strong>
              <div v-if="isForeignOrder" class="money-sub">
                折合原币：{{ originalText(orderDetail.finalAmount) }}
              </div>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 状态信息 -->
        <el-card class="info-card" shadow="never">
          <template #header>
            <span>状态信息</span>
          </template>

          <el-descriptions :column="3" border>
            <el-descriptions-item label="订单状态">
              <el-tag :type="SalesOrderStatusEnum.getTagProps(orderDetail.orderStatus).type">
                {{ SalesOrderStatusEnum.getLabel(orderDetail.orderStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="生产状态">
              <el-tag :type="ProdStatusEnum.getTagProps(orderDetail.prodStatus).type">
                {{ ProdStatusEnum.getLabel(orderDetail.prodStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="支付状态">
              <el-tag :type="PaymentStatusEnum.getTagProps(orderDetail.paymentStatus).type">
                {{ PaymentStatusEnum.getLabel(orderDetail.paymentStatus) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="已付金额">
              <span>{{ cnyText(orderDetail.paidAmount) }}</span>
              <div v-if="isForeignOrder" class="money-sub">
                折合原币：{{ originalText(orderDetail.paidAmount) }}
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="未付金额">
              <span>{{ cnyText(orderDetail.unpaidAmount) }}</span>
              <div v-if="isForeignOrder" class="money-sub">
                折合原币：{{ originalText(orderDetail.unpaidAmount) }}
              </div>
            </el-descriptions-item>
            <el-descriptions-item label="已生产数量">
              {{ orderDetail.producedQuantity || 0 }} / {{ orderDetail.totalQuantity }}
            </el-descriptions-item>
            <el-descriptions-item label="已发货数量">
              {{ orderDetail.shippedQuantity || 0 }} / {{ orderDetail.totalQuantity }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 审核信息 -->
        <el-card v-if="orderDetail.reviewerName" class="info-card" shadow="never">
          <template #header>
            <span>审核信息</span>
          </template>

          <el-descriptions :column="3" border>
            <el-descriptions-item label="审核人">
              {{ orderDetail.reviewerName }}
            </el-descriptions-item>
            <el-descriptions-item label="审核开始时间">
              {{ parseDateTime(orderDetail.reviewStartTime) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="orderDetail.reviewEndTime" label="审核结束时间">
              {{ parseDateTime(orderDetail.reviewEndTime) }}
            </el-descriptions-item>
            <el-descriptions-item v-if="orderDetail.reviewRemark" label="审核备注" :span="3">
              {{ orderDetail.reviewRemark }}
            </el-descriptions-item>
            <el-descriptions-item v-if="orderDetail.rejectReason" label="驳回原因" :span="3">
              <span class="text-danger">{{ orderDetail.rejectReason }}</span>
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 销售信息 -->
        <el-card class="info-card" shadow="never">
          <template #header>
            <span>销售信息</span>
          </template>

          <el-descriptions :column="2" border>
            <el-descriptions-item label="销售负责人">
              {{ orderDetail.salesManagerName || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="创建人">
              {{ orderDetail.createBy || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="创建时间">
              {{ parseDateTime(orderDetail.createTime) }}
            </el-descriptions-item>
            <el-descriptions-item label="更新时间">
              {{ parseDateTime(orderDetail.updateTime) }}
            </el-descriptions-item>
          </el-descriptions>
        </el-card>

        <!-- 订单明细 -->
        <el-card v-if="orderItems.length > 0" class="info-card" shadow="never">
          <template #header>
            <span>订单明细</span>
          </template>

          <el-table :data="orderItems" border stripe size="small">
            <el-table-column label="序号" type="index" width="50" align="center" />
            <el-table-column
              label="产品名称"
              prop="productName"
              min-width="150"
              show-overflow-tooltip
            />
            <el-table-column label="作业规范版本" width="165">
              <template #default="{ row }"
                ><WorkSpecVersionLink :version-id="row.workSpecVersionId" empty-text="未指定"
              /></template>
            </el-table-column>
            <el-table-column label="产品编码" prop="productCode" width="120" />
            <el-table-column
              label="规格型号"
              prop="specification"
              width="120"
              show-overflow-tooltip
            />
            <el-table-column label="单位" prop="unit" width="60" align="center" />
            <el-table-column label="数量" prop="quantity" width="80" align="right" />
            <el-table-column label="单价（元）" prop="unitPrice" width="110" align="right">
              <template #default="{ row }">
                {{ formatCurrency(row.unitPrice) }}
              </template>
            </el-table-column>
            <el-table-column label="金额（元）" prop="amount" width="130" align="right">
              <template #default="{ row }">
                <strong>{{ formatCurrency(row.amount) }}</strong>
              </template>
            </el-table-column>
            <el-table-column label="交货天数" prop="deliveryDays" width="80" align="center" />
            <el-table-column
              label="定制要求"
              prop="customRequirements"
              width="150"
              show-overflow-tooltip
            />
          </el-table>

          <div class="items-total">
            <span>合计：</span>
            <strong>{{ orderItems.length }} 种商品</strong>
            <span style="margin-left: 20px">总数量：</span>
            <strong>{{ totalQuantity }}</strong>
            <span style="margin-left: 20px">明细金额合计（未税）：</span>
            <strong class="text-success">{{ cnyText(itemsSubtotal) }}</strong>
          </div>
        </el-card>

        <!-- 相关文档 -->
        <el-card class="info-card" shadow="never">
          <template #header>
            <span>相关文档</span>
          </template>
          <AttachmentDocList
            v-if="props.orderId"
            :scopes="[{ kind: 'own', sourceType: 'sales_order', sourceId: props.orderId }]"
            title="相关文档"
          />
        </el-card>
      </template>

      <template v-else-if="!loading">
        <el-empty description="未找到订单信息" />
      </template>
    </div>

    <template #footer>
      <el-button type="primary" @click="drawerVisible = false">关 闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import WorkSpecVersionLink from '@/components/product/WorkSpecVersionLink.vue'
import { ref, computed, watch } from 'vue'
import AttachmentDocList from '@/components/attachment/AttachmentDocList.vue'
import { ElMessage } from 'element-plus'
import { orderApi } from '@/api/sales/order'
import { salesLogApi } from '@/api/sales/log'
import {
  SalesOrderStatusEnum,
  OrderTypeEnum,
  PaymentStatusEnum,
  ProdStatusEnum,
} from '@/enums/sales/OrderEnum'
import { parseDate, parseTime as parseDateTime, formatCurrency } from '@/utils/format'
import { formatCny, formatOriginal, orderMoneyView } from '../composables/useOrderForm'

const props = defineProps<{
  modelValue: boolean
  orderId: number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
}>()

const drawerVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

const loading = ref(false)
const orderDetail = ref<any>(null)
const orderItems = ref<any[]>([])
const reviewHistory = ref<any[]>([])

// 计算总数量
const totalQuantity = computed(() => {
  return orderItems.value.reduce((sum, item) => sum + (item.quantity || 0), 0)
})

// 明细金额合计（未税，人民币口径）。注意：不是订单「含税总额/实际应收」。
const itemsSubtotal = computed(() => {
  return orderItems.value.reduce((sum, item) => sum + (item.amount || 0), 0)
})

// 金额口径（dev-20261019-020）：系统金额字段按人民币存储；外币只做折合展示。
const moneyView = (value: number | string | null | undefined) =>
  orderMoneyView(value, orderDetail.value?.currency, orderDetail.value?.exchangeRate)
const cnyText = (value: number | string | null | undefined) => formatCny(moneyView(value))
const originalText = (value: number | string | null | undefined) => formatOriginal(moneyView(value))
const isForeignOrder = computed(
  () => String(orderDetail.value?.currency || 'CNY').toUpperCase() !== 'CNY',
)
const rateValid = computed(() => moneyView(orderDetail.value?.finalAmount).rateValid)

// 获取时间线类型
const getTimelineType = (result: string) => {
  if (result === 'success') return 'success'
  if (result === 'failure') return 'danger'
  return 'primary'
}

// 获取订单详情
const fetchOrderDetail = async () => {
  if (!props.orderId) return

  loading.value = true
  try {
    const response = await orderApi.getOrder(props.orderId)
    orderDetail.value = response.data

    // 获取订单明细
    if ((response as any).data.items) {
      orderItems.value = (response as any).data.items
    }
  } catch (error) {
    console.error('获取订单详情失败', error)
    ElMessage.error('获取订单详情失败')
  } finally {
    loading.value = false
  }
}

// 获取操作历史
const fetchReviewHistory = async () => {
  if (!props.orderId) return

  try {
    const response = await salesLogApi.getLogsByOrderId(props.orderId)
    reviewHistory.value = response.data || []
  } catch (error) {
    console.error('获取操作历史失败', error)
    // 不显示错误提示，历史记录非关键信息
  }
}

// 关闭抽屉
const handleClose = () => {
  drawerVisible.value = false
  // 重置数据
  setTimeout(() => {
    orderDetail.value = null
    orderItems.value = []
    reviewHistory.value = []
  }, 300)
}

// 监听抽屉打开
watch(
  () => props.modelValue,
  async (val) => {
    if (val && props.orderId) {
      await fetchOrderDetail()
      await fetchReviewHistory()
    }
  }
)
</script>

<style scoped lang="scss">
.order-detail {
  padding: 0 16px;

  .info-card {
    margin-bottom: 20px;

    &:last-child {
      margin-bottom: 0;
    }

    .card-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }
  }

  .text-primary {
    color: #409eff;
  }

  .text-success {
    color: #67c23a;
  }

  .text-danger {
    color: #f56c6c;
  }

  .items-total {
    margin-top: 16px;
    padding-top: 12px;
    text-align: right;
    border-top: 1px solid #ebeef5;
    font-size: 14px;

    strong {
      font-size: 16px;
    }
  }

  .history-item {
    .history-header {
      display: flex;
      justify-content: space-between;
      margin-bottom: 8px;

      .history-action {
        font-weight: bold;
        color: #303133;
      }

      .history-operator {
        color: #909399;
        font-size: 12px;
      }
    }

    .history-remark {
      color: #606266;
      font-size: 14px;
      margin-top: 8px;
      padding-top: 8px;
      border-top: 1px solid #ebeef5;
    }

    .history-result {
      margin-top: 8px;
      text-align: right;
    }
  }
}

:deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 16px 20px;
  border-bottom: 1px solid #ebeef5;
}

:deep(.el-drawer__body) {
  padding: 20px 0;
}

:deep(.el-descriptions__label) {
  width: 120px;
  background-color: #fafafa;
}

:deep(.el-card__header) {
  padding: 12px 16px;
  background-color: #fafafa;
  border-bottom: 1px solid #ebeef5;
  font-weight: 600;
}

/* 金额口径（dev-20261019-020）：人民币主值 + 折合原币副行 */
.money-sub {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  line-height: 1.6;
}
.money-note {
  margin-bottom: 12px;
}
</style>
