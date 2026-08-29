<!-- views/sales/quotation/components/QuotationDetailDialog.vue -->
<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="1200px"
    append-to-body
    @close="handleClose"
  >
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="10" animated />
    </div>
    <div v-else-if="detailData">
      <!-- 基本信息 -->
      <el-descriptions :column="3" border>
        <el-descriptions-item label="报价单号">
          {{ detailData.quotationNo }}
        </el-descriptions-item>
        <el-descriptions-item label="报价类型">
          {{ detailData.quotationType === 1 ? '标准品' : '样品' }}
        </el-descriptions-item>
        <el-descriptions-item label="报价状态">
          <el-tag :type="getStatusTagType(detailData.quotationStatus)">
            {{ getStatusLabel(detailData.quotationStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="客户名称">
          {{ detailData.customerName }}
        </el-descriptions-item>
        <el-descriptions-item label="报价日期">
          {{ detailData.quotationDate }}
        </el-descriptions-item>

        <el-descriptions-item label="有效期至">
          {{ detailData.validUntil || '-' }}
        </el-descriptions-item>

        <template v-if="isSensitive">
          <el-descriptions-item label="币种">
            {{ detailData.currency }}
          </el-descriptions-item>
          <el-descriptions-item label="汇率">
            {{ detailData.exchangeRate }}
          </el-descriptions-item>
          <el-descriptions-item label="销售员">
            {{ detailData.salesPersonName || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="总金额" :span="3">
            <span style="font-weight: bold; font-size: 16px; color: #409eff">
              {{ formatCurrency(detailData.totalAmount) }} {{ detailData.currency }}
            </span>
          </el-descriptions-item>
        </template>
        <el-descriptions-item label="备注" :span="3">
          {{ detailData.remark || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 明细列表 -->
      <el-divider content-position="left">报价明细</el-divider>
      <el-table :data="detailData.items || []" border style="width: 100%">
        <el-table-column label="序号" type="index" width="60" align="center" />
        <el-table-column label="产品编码" prop="productCode" width="140" />
        <el-table-column label="产品名称" prop="productName" />
        <el-table-column label="数量" prop="quantity" width="100" align="center" />
        <template v-if="isSensitive">
          <el-table-column label="单价" prop="unitPrice" width="120" align="right">
            <template #default="{ row }">
              {{ formatCurrency(row.unitPrice) }}
            </template>
          </el-table-column>
          <el-table-column label="金额" prop="amount" width="150" align="right">
            <template #default="{ row }">
              <span style="font-weight: bold">{{ formatCurrency(row.amount) }}</span>
            </template>
          </el-table-column>
        </template>
      </el-table>

      <template v-if="isSensitive">
        <!-- 金额汇总 -->
        <el-divider content-position="left">金额汇总</el-divider>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-statistic title="小计金额" :value="detailData.subtotalAmount || 0" :precision="2" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="税额" :value="detailData.taxAmount || 0" :precision="2" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="折扣金额" :value="detailData.discountAmount || 0" :precision="2" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="最终金额" :value="detailData.finalAmount || 0" :precision="2" />
          </el-col>
        </el-row>
      </template>

      <!-- 底部按钮 -->
      <div v-if="mode === 'submitReview'" style="margin-top: 20px; text-align: center">
        <el-button type="primary" @click="handleConfirmSubmit">确认提交审核</el-button>
      </div>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">关 闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { quotationApi } from '@/api/sales/quotation'
import { QuotationStatusEnum } from '@/enums/sales'
import { formatCurrency } from '@/utils/format'

const props = defineProps<{
  modelValue: boolean
  quotationId: number
  mode?: 'view' | 'submitReview'
  isSensitive?: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'submitted'): void
}>()

const visible = ref(false)
const loading = ref(false)
const detailData = ref<any>(null)

const dialogTitle = computed(() => {
  if (props.mode === 'submitReview') {
    return `提交审核 - ${detailData.value?.quotationNo || ''}`
  }
  return `报价单详情 - ${detailData.value?.quotationNo || ''}`
})

const getStatusTagType = (status: number) => {
  return QuotationStatusEnum.getTagProps(status).type || 'info'
}

const getStatusLabel = (status: number) => {
  const label = QuotationStatusEnum.getLabel(status)
  return label && label !== '未知' ? label : '未知状态'
}

watch(
  () => props.modelValue,
  async (val) => {
    visible.value = val
    if (val && props.quotationId) {
      await loadDetail()
    }
  }
)

watch(visible, (val) => {
  if (!val) {
    emit('update:modelValue', val)
  }
})

const loadDetail = async () => {
  loading.value = true
  try {
    const res: any = await quotationApi.getInfo(props.quotationId)
    detailData.value = res?.data || null
  } catch (error) {
    console.error('加载详情失败:', error)
    ElMessage.error('加载详情失败')
  } finally {
    loading.value = false
  }
}

const handleConfirmSubmit = async () => {
  try {
    await quotationApi.submitReview(props.quotationId)
    ElMessage.success('提交审核成功')
    visible.value = false
    emit('submitted')
  } catch (error) {
    console.error('提交审核失败:', error)
    ElMessage.error('提交审核失败')
  }
}

const handleClose = () => {
  visible.value = false
}
</script>

<style scoped>
.loading-container {
  padding: 20px;
}
.dialog-footer {
  text-align: right;
}
</style>
