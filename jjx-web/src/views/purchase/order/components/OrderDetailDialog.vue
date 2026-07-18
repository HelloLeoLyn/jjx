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
            <el-tag :type="orderDetail.orderType === 1 ? 'danger' : 'info'" size="small">
              {{ orderDetail.orderType === 1 ? '紧急订单' : '普通订单' }}
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
      </template>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleClose">关 闭</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { PurchaseEnum } from '@/enums/purchase'
import { getOrder } from '@/api/purchase/order'
import type { PurchaseOrderVO } from '@/types/purchase/order'

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
    const response = await getOrder(Number(props.orderId))
    orderDetail.value = response.data || null
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
  emit('update:visible', false)
}
</script>
