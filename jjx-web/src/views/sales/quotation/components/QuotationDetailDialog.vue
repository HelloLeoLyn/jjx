<template>
  <el-dialog
    :title="mode === 'submitReview' ? '报价单详情（提交审核）' : '报价单详情'"
    :model-value="modelValue"
    width="1200px"
    append-to-body
    @close="emitClose"
  >
    <template v-if="detail.quotationId">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="报价单号">{{ detail.quotationNo }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{ detail.customerName }}</el-descriptions-item>
        <el-descriptions-item label="报价日期">
          {{ parseTime(detail.quotationDate, 'yyyy-MM-dd') }}
        </el-descriptions-item>
        <el-descriptions-item label="有效期至">
          <span v-if="detail.validUntil">{{ parseTime(detail.validUntil, 'yyyy-MM-dd') }}</span>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="报价状态">
          <el-tag :type="getStatusTagType(detail.quotationStatus)">
            {{ getStatusLabel(detail.quotationStatus) }}
          </el-tag>
        </el-descriptions-item>
        <template v-if="!isSensitive">
          <el-descriptions-item label="币种">
            {{ detail.currency || 'CNY' }}
          </el-descriptions-item>
          <el-descriptions-item label="汇率">
            {{ detail.exchangeRate || '1.0000' }}
          </el-descriptions-item>
          <el-descriptions-item label="小计金额">
            {{ formatCurrency(detail.subtotalAmount || 0) }}
          </el-descriptions-item>
          <el-descriptions-item label="税率"> {{ detail.taxRate || 0 }}% </el-descriptions-item>
          <el-descriptions-item label="税额">
            {{ formatCurrency(detail.taxAmount || 0) }}
          </el-descriptions-item>
          <el-descriptions-item label="折扣金额">
            {{ formatCurrency(detail.discountAmount || 0) }}
          </el-descriptions-item>
          <el-descriptions-item label="总金额">
            {{ formatCurrency(detail.totalAmount || 0) }}
          </el-descriptions-item>
          <el-descriptions-item label="最终金额">
            {{ formatCurrency(detail.finalAmount || 0) }}
          </el-descriptions-item>
        </template>
        <el-descriptions-item label="销售员">
          {{ detail.salesPersonName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">
          {{ detail.remark || '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 报价明细表格 -->
      <el-divider content-position="left">报价明细</el-divider>
      <el-table :data="detail.items" border style="width: 100%">
        <el-table-column label="序号" type="index" width="60" align="center" />
        <el-table-column label="产品编码" prop="productCode" width="120" />
        <el-table-column label="产品名称" prop="productName" width="180" />
        <el-table-column label="数量" prop="quantity" width="80" align="right" />
        <template v-if="!isSensitive">
          <el-table-column label="单价" prop="unitPrice" width="100" align="right">
            <template #default="scope">
              {{ formatCurrency(scope.row.unitPrice) }}
            </template>
          </el-table-column>
          <el-table-column label="金额" prop="amount" width="120" align="right">
            <template #default="scope">
              {{ formatCurrency(scope.row.amount) }}
            </template>
          </el-table-column>
        </template>
        <el-table-column label="单位" prop="unit" width="80" />
        <el-table-column label="交期(天)" prop="deliveryDays" width="100" />
        <el-table-column label="定制要求" prop="customRequirements" />
      </el-table>

      <!-- 相关文档 -->
      <el-divider content-position="left">相关文档</el-divider>
      <AttachmentPanel
        v-if="detail.quotationId"
        biz-type="quotation"
        :biz-id="detail.quotationId"
        :trace-id="detail.traceId"
      />
    </template>

    <template #footer>
      <div v-if="mode === 'submitReview'" class="detail-footer">
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="请核对报价单内容（客户/明细/金额）后确认提交，提交后将进入待审核状态"
          style="margin-bottom: 12px"
        />
        <el-button @click="emitClose">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmitReview">
          确认提交审核
        </el-button>
      </div>
      <el-button v-else @click="emitClose">关 闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { quotationApi } from '@/api/sales/quotation'
import { parseTime, formatCurrency } from '@/utils/format'
import { QuotationStatusEnum } from '@/enums/sales/QuotationEnum'
import AttachmentPanel from '@/components/AttachmentPanel/index.vue'

/**
 * 报价单详情弹窗（共享组件）
 * 从报价单列表页抽取（2026-08-26），报价单列表页与工程打样工作台"来源单据"查看共用一套，不新建第二套
 * mode=view 纯查看；mode=submitReview 提交审核模式（提交审核成功后 emit submitted）
 */
const props = defineProps<{
  modelValue: boolean
  quotationId?: number
  mode?: 'view' | 'submitReview'
  // 默认 false，若为 true 则在提交审核时不显示明细金额（仅显示明细名称/数量/单位），用于敏感报价场景
  isSensitive: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
  (e: 'submitted'): void
}>()
const detail = ref<any>({})
const submitting = ref(false)

// 打开时按 quotationId 拉取详情（复用现有 API）
watch(
  () => props.modelValue,
  async (val) => {
    if (val && props.quotationId) {
      try {
        const res: any = await quotationApi.getInfo(props.quotationId)
        detail.value = res.data || {}
      } catch (e: any) {
        ElMessage.error(e?.message || '加载报价单详情失败')
      }
    }
  }
)

function getStatusTagType(status: number): any {
  return (QuotationStatusEnum.getTagProps(status).type as any) || 'info'
}
function getStatusLabel(status: number) {
  const label = QuotationStatusEnum.getLabel(status)
  return label && label !== '未知' ? label : '未知状态'
}

function emitClose() {
  emit('update:modelValue', false)
}

async function handleSubmitReview() {
  const quotationId = detail.value?.quotationId as number
  if (!quotationId) return
  submitting.value = true
  try {
    const res: any = await quotationApi.submitReview(quotationId)
    if (res.code === 200 || res.code === 0) {
      ElMessage.success('提交审核成功')
      emitClose()
      emit('submitted')
    } else {
      ElMessage.error(res.msg || '提交审核失败')
    }
  } catch (e: any) {
    ElMessage.error(e?.msg || e?.message || '提交审核失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.detail-footer {
  text-align: right;
}
</style>
