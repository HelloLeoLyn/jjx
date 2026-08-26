<template>
  <el-dialog
    title="询价单详情"
    :model-value="modelValue"
    width="700px"
    append-to-body
    @close="emitClose"
  >
    <template v-if="detailData.inquiryId">
      <el-descriptions :column="2" border>
        <el-descriptions-item label="询价单号" :span="2">{{
          detailData.inquiryNo
        }}</el-descriptions-item>
        <el-descriptions-item label="类型">
          <el-tag v-if="detailData.inquiryType === 2" type="warning" size="small">样品</el-tag>
          <el-tag v-else type="primary" size="small">标准</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="产品编码">{{ detailData.productCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="产品名称">{{
          detailData.productName || (detailData.productId ? '产品#' + detailData.productId : '-')
        }}</el-descriptions-item>
        <el-descriptions-item label="客户名称">{{
          detailData.customerName
        }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{
          detailData.contactPerson || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{
          detailData.contactPhone || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="询价日期">{{ detailData.inquiryDate }}</el-descriptions-item>
        <el-descriptions-item label="预估数量">{{
          detailData.expectedQuantity || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="产品描述" :span="2">{{
          detailData.productDescription || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="特殊要求" :span="2">{{
          detailData.specialRequirements || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="需求图纸">
          <el-tag v-if="detailData.hasDrawing" type="success" size="small">有图纸</el-tag>
          <el-tag v-else type="info" size="small">无图纸</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="询价状态">
          <el-tag :type="statusTagType(detailData.inquiryStatus)" size="small">
            {{ statusLabel(detailData.inquiryStatus) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="销售负责人" :span="2">{{
          detailData.salesPersonName || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{
          detailData.remark || '-'
        }}</el-descriptions-item>
      </el-descriptions>
    </template>
    <el-divider content-position="left">相关文档</el-divider>
    <AttachmentPanel
      v-if="detailData?.inquiryId"
      biz-type="inquiry"
      :biz-id="detailData.inquiryId"
    />
    <template #footer>
      <el-button @click="emitClose">关 闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { inquiryApi } from '@/api/sales/inquiry'
import AttachmentPanel from '@/components/AttachmentPanel/index.vue'

/**
 * 询价单详情弹窗（共享组件）
 * 从询价单列表页抽取（2026-08-26），询价单列表页与工程打样工作台"来源单据"查看共用一套，不新建第二套
 */
const props = defineProps<{
  modelValue: boolean
  inquiryId?: number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: boolean): void
}>()

const detailData = ref<any>({})

// 打开时按 inquiryId 拉取详情（复用现有 API：selectInquiryById 已填充产品名称）
watch(
  () => props.modelValue,
  async (val) => {
    if (val && props.inquiryId) {
      try {
        const res: any = await inquiryApi.getInfo(props.inquiryId)
        detailData.value = res.data || {}
      } catch (e: any) {
        ElMessage.error(e?.message || '加载询价单详情失败')
      }
    }
  },
)

// 询价单状态映射（与询价单列表页一致）
const statusMap: Record<number, { label: string; type: string }> = {
  0: { label: '草稿', type: 'info' },
  1: { label: '待处理', type: 'warning' },
  2: { label: '已发送', type: 'primary' },
  3: { label: '已转报价', type: 'success' },
  4: { label: '已确认', type: 'success' },
  5: { label: '已拒绝', type: 'danger' },
  6: { label: '已过期', type: 'info' },
}
function statusLabel(status: number): string {
  return statusMap[status]?.label || String(status ?? '')
}
function statusTagType(status: number): any {
  return (statusMap[status]?.type || 'info') as any
}

function emitClose() {
  emit('update:modelValue', false)
}
</script>
