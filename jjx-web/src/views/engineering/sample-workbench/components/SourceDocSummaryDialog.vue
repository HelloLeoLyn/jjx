<!-- views/engineering/sample-workbench/components/SourceDocSummaryDialog.vue
  打样工作台「来源单据」只读摘要弹窗（任务1438 / dev-20260905-004）
  工程角色无 sales:quotation:view / sales:inquiry:view（复用销售详情弹窗会 403），
  此弹窗改调按样品单收敛的摘要接口：仅展示打样参考所需字段，价格/金额/联系人电话等敏感数据后端已剔除，前端也不展示。
  有 sales 查看权的角色仍走原销售详情弹窗（workbench.vue 按权限分流）。
-->
<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="1200px"
    append-to-body
    @close="emitClose"
  >
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="8" animated />
    </div>
    <div v-else-if="detailData">
      <!-- 询价单摘要（需求描述全保留，联系电话/销售负责人/单价不展示） -->
      <template v-if="docType === 'inquiry'">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="询价单号" :span="2">{{
            detailData.inquiryNo || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="类型">
            <el-tag
              v-if="detailData.inquiryType === 2"
              type="warning"
              size="small"
              >样品</el-tag
            >
            <el-tag v-else type="primary" size="small">标准</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="询价状态">
            <el-tag :type="inquiryStatusTag(detailData.inquiryStatus)" size="small">
              {{ inquiryStatusLabel(detailData.inquiryStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="客户名称">{{
            detailData.customerName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{
            detailData.contactPerson || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="产品编码">{{
            detailData.productCode || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="产品名称">{{
            detailData.productName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="询价日期">{{
            detailData.inquiryDate || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="预估数量">{{
            detailData.expectedQuantity ?? '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="需求时间" :span="2">{{
            detailData.startDate || detailData.endDate
              ? `${detailData.startDate || '?'} ~ ${detailData.endDate || '?'}`
              : '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="需求图纸">
            <el-tag v-if="detailData.hasDrawing" type="success" size="small"
              >有图纸</el-tag
            >
            <el-tag v-else type="info" size="small">无图纸</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="按键数">{{
            detailData.keyCount ?? '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="产品描述" :span="2">{{
            detailData.productDescription || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="尺寸描述" :span="2">{{
            detailData.sizeDescription || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="材料要求" :span="2">{{
            detailData.materialRequirements || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="线路要求" :span="2">{{
            detailData.circuitRequirements || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="连接器要求" :span="2">{{
            detailData.connectorRequirements || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="特殊要求" :span="2">{{
            detailData.specialRequirements || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{
            detailData.remark || '-'
          }}</el-descriptions-item>
        </el-descriptions>
      </template>

      <!-- 报价单摘要（技术/规格全保留，单价/金额不展示） -->
      <template v-else>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="报价单号">{{
            detailData.quotationNo || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="报价类型">{{
            detailData.quotationType === 1 ? '标准品' : '样品'
          }}</el-descriptions-item>
          <el-descriptions-item label="报价状态">
            <el-tag :type="quotationStatusTag(detailData.quotationStatus)" size="small">
              {{ quotationStatusLabel(detailData.quotationStatus) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="客户名称">{{
            detailData.customerName || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="报价日期">{{
            detailData.quotationDate || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="有效期至">{{
            detailData.validUntil || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="来源询价单号" :span="2">{{
            detailData.sourceInquiryNo || '-'
          }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="1">{{
            detailData.remark || '-'
          }}</el-descriptions-item>
        </el-descriptions>

        <el-divider content-position="left">报价明细（技术参数）</el-divider>
        <el-table
          v-if="(detailData.items || []).length"
          :data="detailData.items"
          border
          size="small"
          style="width: 100%"
        >
          <el-table-column type="index" label="序号" width="56" align="center" />
          <el-table-column label="产品编码" prop="productCode" width="130" />
          <el-table-column label="产品名称" prop="productName" min-width="130" />
          <el-table-column label="按键数" prop="keyCount" width="76" align="center" />
          <el-table-column label="尺寸(宽×高×厚mm)" width="150" align="center">
            <template #default="{ row }">
              {{
                row.width || row.height || row.thickness
                  ? `${row.width ?? '-'}×${row.height ?? '-'}×${row.thickness ?? '-'}`
                  : '-'
              }}
            </template>
          </el-table-column>
          <el-table-column label="材料" prop="materialType" width="96" />
          <el-table-column label="颜色" prop="color" width="86" />
          <el-table-column label="线路类型" prop="circuitType" width="100" />
          <el-table-column label="连接器" prop="connectorType" width="100" />
          <el-table-column label="面板结构/特征" width="130">
            <template #default="{ row }">
              {{
                [row.panelType, row.panelFeature].filter(Boolean).join(' / ') || '-'
              }}
            </template>
          </el-table-column>
          <el-table-column label="线路特征" prop="circuitFeature" width="100" />
          <el-table-column label="数量" prop="quantity" width="70" align="center" />
          <el-table-column label="单位" prop="unit" width="56" align="center" />
          <el-table-column label="交期(天)" prop="deliveryDays" width="80" align="center" />
          <el-table-column label="预计交期" prop="estimatedDeliveryDate" width="100" />
          <el-table-column label="自定义要求" prop="customRequirements" min-width="120" />
          <el-table-column label="Logo要求" prop="logoRequirement" min-width="100" />
          <el-table-column label="认证要求" prop="certificationRequirement" min-width="110" />
        </el-table>
        <el-empty v-else description="该报价单暂无明细" :image-size="60" />
      </template>
    </div>
    <el-empty
      v-else-if="!loading && loadFailed"
      description="来源单据加载失败或无权限查看"
      :image-size="60"
    />
    <el-empty v-else-if="!loading" description="暂无来源单据" :image-size="60" />

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="emitClose">关 闭</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { QuotationStatusEnum, InquiryStatusEnum } from '@/enums/sales'

const props = defineProps<{
  modelValue: boolean
  docType: 'quotation' | 'inquiry'
  orderId?: number | string
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const loading = ref(false)
const loadFailed = ref(false)
const detailData = ref<any>(null)

const dialogTitle = computed(() => {
  if (props.docType === 'inquiry') {
    return `来源询价单${detailData.value?.inquiryNo ? ' · ' + detailData.value.inquiryNo : ''}`
  }
  return `来源报价单${detailData.value?.quotationNo ? ' · ' + detailData.value.quotationNo : ''}`
})

// 状态枚举展示（复用销售模块同一套枚举，口径一致）
function quotationStatusLabel(status: number | undefined | null): string {
  return status == null ? '未知' : QuotationStatusEnum.getLabel(status)
}
function quotationStatusTag(status: number | undefined | null): any {
  return status == null ? 'info' : QuotationStatusEnum.getTagProps(status).type || 'info'
}
function inquiryStatusLabel(status: number | undefined | null): string {
  return status == null ? '未知' : InquiryStatusEnum.getLabel(status)
}
function inquiryStatusTag(status: number | undefined | null): any {
  return status == null ? 'info' : InquiryStatusEnum.getTagProps(status).type || 'info'
}

// 打开时按样品单 orderId 拉取对应来源单据摘要（收敛接口，服务端已剔除敏感数据）
watch(
  () => props.modelValue,
  async (val) => {
    if (!val) return
    const orderId = Number(props.orderId)
    if (!orderId) {
      detailData.value = null
      return
    }
    loading.value = true
    loadFailed.value = false
    detailData.value = null
    try {
      const res: any =
        props.docType === 'inquiry'
          ? await sampleOrderApi.getSourceInquirySummary(orderId)
          : await sampleOrderApi.getSourceQuotationSummary(orderId)
      detailData.value = res.data || null
    } catch (e: any) {
      loadFailed.value = true
      ElMessage.error(e?.message || '加载来源单据摘要失败')
    } finally {
      loading.value = false
    }
  }
)

function emitClose() {
  emit('update:modelValue', false)
}
</script>

<style scoped>
.loading-container {
  padding: 8px 0;
}
</style>
