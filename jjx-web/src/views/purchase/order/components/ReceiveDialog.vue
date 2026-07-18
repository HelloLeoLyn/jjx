<template>
  <el-dialog :title="title" v-model="visible" width="800px" append-to-body @close="handleClose">
    <el-tabs v-model="activeTab">
      <!-- 订单基本信息 -->
      <el-tab-pane label="订单信息" name="order">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">
            {{ form.orderNo }}
          </el-descriptions-item>
          <el-descriptions-item label="供应商">
            {{ form.supplierName }}
          </el-descriptions-item>
          <el-descriptions-item label="订单日期">
            {{ form.orderDate }}
          </el-descriptions-item>
          <el-descriptions-item label="交货日期">
            {{ form.expectedDeliveryDate }}
          </el-descriptions-item>
          <el-descriptions-item label="订单金额">
            {{ formatCurrency(form.orderTotalAmount) }} {{ form.currency }}
          </el-descriptions-item>
          <el-descriptions-item label="收货状态">
            <el-tag :type="PurchaseEnum.receiptStatus.getTagProps(form.receiptStatus).type">
              {{ PurchaseEnum.receiptStatus.getLabel(form.receiptStatus) }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>

      <!-- 收货明细 -->
      <el-tab-pane label="收货明细" name="items">
        <el-table :data="form.items" border style="width: 100%">
          <el-table-column label="物料编码" prop="materialCode" width="120" />
          <el-table-column label="物料名称" prop="materialName" width="150" />
          <el-table-column label="规格型号" prop="materialSpec" width="120" />
          <el-table-column label="单位" prop="unit" width="80" />
          <el-table-column label="订单数量" prop="quantity" width="100">
            <template #default="scope">
              {{ scope.row.quantity }}
            </template>
          </el-table-column>
          <el-table-column label="已收数量" prop="receivedQuantity" width="100">
            <template #default="scope">
              {{ scope.row.receivedQuantity || 0 }}
            </template>
          </el-table-column>
          <el-table-column label="本次收货" width="120">
            <template #default="scope">
              <el-input-number
                v-model="scope.row.currentReceiveQuantity"
                :min="0"
                :max="getMaxReceiveQuantity(scope.row)"
                :precision="2"
                :step="1"
                size="small"
                style="width: 100%"
              />
            </template>
          </el-table-column>
          <el-table-column label="检验结果" width="120">
            <template #default="scope">
              <el-select
                v-model="scope.row.inspectionResult"
                placeholder="请选择"
                size="small"
                style="width: 100%"
              >
                <el-option label="合格" value="qualified" />
                <el-option label="不合格" value="unqualified" />
                <el-option label="待检" value="pending" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="检验备注" width="150">
            <template #default="scope">
              <el-input v-model="scope.row.inspectionRemark" placeholder="备注" size="small" />
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="loading"> 确认收货 </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, nextTick, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { PurchaseEnum } from '@/enums/purchase'
import { receiveOrderItem, getOrderItems } from '@/api/purchase/order'
import { formatCurrency } from '@/utils/format'

const props = defineProps<{
  modelValue: boolean
  orderData: any
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const visible = ref(false)
const loading = ref(false)
const activeTab = ref('order')

const title = ref('订单收货')

const form = reactive({
  orderId: undefined as number | undefined,
  orderNo: '',
  supplierName: '',
  orderDate: '',
  expectedDeliveryDate: '',
  orderTotalAmount: 0,
  currency: 'CNY',
  receiptStatus: 0,
  items: [] as any[],
})

// 监听props变化
watch(
  () => props.modelValue,
  (val) => {
    visible.value = val
    if (val) {
      initForm()
    }
  }
)

// 监听visible变化
watch(visible, (val) => {
  emit('update:modelValue', val)
})

// 初始化表单
const initForm = async () => {
  if (props.orderData) {
    form.orderId = props.orderData.orderId
    form.orderNo = props.orderData.orderNo
    form.supplierName = props.orderData.supplierName
    form.orderDate = props.orderData.orderDate
    form.expectedDeliveryDate = props.orderData.expectedDeliveryDate
    form.orderTotalAmount = props.orderData.orderTotalAmount
    form.currency = props.orderData.currency || 'CNY'
    form.receiptStatus = props.orderData.receiptStatus

    // 加载订单明细
    try {
      const res = await getOrderItems(form.orderId!)
      form.items = (res.data?.rows || []).map((item: any) => ({
        ...item,
        currentReceiveQuantity: 0,
        inspectionResult: 'pending',
        inspectionRemark: '',
      }))
    } catch (error) {
      console.error('加载订单明细失败:', error)
    }
  }
}

// 计算最大可收货数量
const getMaxReceiveQuantity = computed(() => (row: any) => {
  const ordered = row.quantity || 0
  const received = row.receivedQuantity || 0
  return ordered - received
})

// 关闭对话框
const handleClose = () => {
  visible.value = false
}

// 提交收货
const handleSubmit = async () => {
  // 检查是否有收货数量
  const hasReceiveItems = form.items.some((item) => item.currentReceiveQuantity > 0)
  if (!hasReceiveItems) {
    ElMessage.warning('请至少输入一个收货数量')
    return
  }

  loading.value = true
  try {
    // 提交每个明细项的收货
    for (const item of form.items) {
      if (item.currentReceiveQuantity > 0) {
        await receiveOrderItem(
          form.orderId!,
          item.itemId || item.id,
          item.currentReceiveQuantity,
          item.inspectionResult,
          item.inspectionRemark
        )
      }
    }

    ElMessage.success('收货成功')
    emit('success')
    handleClose()
  } catch (error) {
    console.error('收货失败:', error)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.dialog-footer {
  text-align: right;
  margin-top: 20px;
}
</style>
