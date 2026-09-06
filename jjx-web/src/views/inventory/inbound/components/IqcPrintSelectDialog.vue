<template>
  <el-dialog
    :model-value="visible"
    :title="`选择进料检验报告 - ${inboundNo || ''}`"
    width="900px"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:visible', $event)"
  >
    <el-alert
      title="进料检验报告按材料和批次分别打印，请选择需要打印的明细。"
      type="info"
      :closable="false"
      show-icon
      class="select-tip"
    />
    <el-table v-loading="loading" :data="items" border row-key="itemId">
      <el-table-column type="index" label="序号" width="60" align="center" />
      <el-table-column label="物料编码" prop="materialCode" width="150" />
      <el-table-column label="物料名称" prop="materialName" min-width="180" show-overflow-tooltip />
      <el-table-column label="批次" prop="batchNo" min-width="150" />
      <el-table-column label="收货数量" prop="quantity" width="100" align="right" />
      <el-table-column label="检验结论" width="100" align="center">
        <template #default="{ row }">
          <el-tag
            v-if="row.inspectionResult"
            :type="InspectionResultEnum.getTagProps(row.inspectionResult).type"
          >
            {{ InspectionResultEnum.getLabel(row.inspectionResult) }}
          </el-tag>
          <el-tag v-else type="info">未检验</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="处置" width="120" align="center">
        <template #default="{ row }">{{ dispositionLabel(row.disposition) }}</template>
      </el-table-column>
      <el-table-column label="报告状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="QualityReviewStatusEnum.getTagProps(row.reviewStatus).type">
            {{ row.reviewStatus ? QualityReviewStatusEnum.getLabel(row.reviewStatus) : '未提交' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" align="center" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" :disabled="!row.inspectionId" @click="openPrint(row)">
            {{ row.reviewStatus === QualityReviewStatus.APPROVED ? '打印正式版' : '预览' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button @click="emit('update:visible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { inboundApi } from '@/api/inventory/inbound'
import { qualityApi } from '@/api/production/quality'
import { InspectionResultEnum, IqcDispositionEnum } from '@/enums/inventory/InboundEnum'
import { QualityReviewStatus, QualityReviewStatusEnum } from '@/enums/quality/InspectionEnum'
import type { InboundItemVO } from '@/types/inventory/inbound'

const props = defineProps<{ visible: boolean; inboundId?: number; inboundNo?: string }>()
const emit = defineEmits<{ (event: 'update:visible', value: boolean): void }>()
const router = useRouter()
const loading = ref(false)
const items = ref<Array<InboundItemVO & { reviewStatus?: string }>>([])

watch(
  () => [props.visible, props.inboundId] as const,
  async ([visible, inboundId]) => {
    if (!visible || !inboundId) return
    loading.value = true
    try {
      const inboundItems = (await inboundApi.getById(String(inboundId))).data?.items || []
      items.value = await Promise.all(
        inboundItems.map(async (item) => ({
          ...item,
          reviewStatus: item.inspectionId
            ? (await qualityApi.getById(Number(item.inspectionId))).data?.reviewStatus
            : undefined,
        }))
      )
    } catch (error: any) {
      ElMessage.error(error?.message || '加载进料检验明细失败')
    } finally {
      loading.value = false
    }
  },
  { immediate: true }
)

function dispositionLabel(value?: string) {
  return value ? IqcDispositionEnum.getLabel(value) : '-'
}

function openPrint(row: InboundItemVO) {
  if (!row.inspectionId || !props.inboundId) return
  window.open(
    router.resolve({
      path: '/production/quality-print/iqc-report',
      query: { inboundId: props.inboundId, inspectionId: row.inspectionId },
    }).href,
    '_blank'
  )
}
</script>

<style scoped>
.select-tip {
  margin-bottom: 16px;
}
</style>
