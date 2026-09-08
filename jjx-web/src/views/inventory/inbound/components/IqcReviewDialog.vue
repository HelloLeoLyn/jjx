<template>
  <el-dialog
    :model-value="visible"
    :title="`IQC 单项审核 - ${inboundNo || ''}`"
    width="1100px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @update:model-value="emit('update:visible', $event)"
  >
    <el-alert
      title="每项材料独立审核；全部通过后只锁定 IQC 结论，本阶段不执行库存过账。"
      type="info"
      :closable="false"
      show-icon
      class="review-tip"
    />
    <el-table v-loading="loading" :data="rows" border row-key="itemId">
      <el-table-column type="expand">
        <template #default="{ row }">
          <div class="history-panel">
            <b>检验历史</b>
            <el-table :data="row.history" size="small" border>
              <el-table-column label="版本" width="70">
                <template #default="{ row: record }">V{{ record.inspectionVersion || 1 }}</template>
              </el-table-column>
              <el-table-column prop="inspectionNo" label="检验单号" min-width="180" />
              <el-table-column label="审核状态" width="100">
                <template #default="{ row: record }">
                  <el-tag :type="QualityReviewStatusEnum.getTagProps(record.reviewStatus).type">
                    {{ QualityReviewStatusEnum.getLabel(record.reviewStatus) }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="reviewerName" label="审核人" width="100" />
              <el-table-column prop="reviewRemark" label="审核意见" min-width="180" />
              <el-table-column prop="reviewTime" label="审核时间" width="170" />
            </el-table>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="materialCode" label="物料编码" width="140" />
      <el-table-column prop="materialName" label="物料名称" min-width="160" />
      <el-table-column prop="batchNo" label="批次" width="140" />
      <el-table-column label="检验结论" width="100">
        <template #default="{ row }">{{
          InspectionResultEnum.getLabel(row.inspectionResult)
        }}</template>
      </el-table-column>
      <el-table-column label="处置" width="120">
        <template #default="{ row }">{{
          row.disposition ? IqcDispositionEnum.getLabel(row.disposition) : '-'
        }}</template>
      </el-table-column>
      <el-table-column label="允收数量" prop="acceptedQuantity" width="100" align="right" />
      <el-table-column label="审核状态" width="100">
        <template #default="{ row }">
          <el-tag :type="QualityReviewStatusEnum.getTagProps(row.quality?.reviewStatus).type">
            {{
              row.quality ? QualityReviewStatusEnum.getLabel(row.quality.reviewStatus) : '未提交'
            }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <template v-if="row.quality?.reviewStatus === QualityReviewStatus.PENDING">
            <el-button
              link
              type="success"
              :loading="submittingId === row.itemId"
              @click="approve(row)"
              >通过</el-button
            >
            <el-button
              link
              type="danger"
              :loading="submittingId === row.itemId"
              @click="reject(row)"
              >驳回</el-button
            >
          </template>
          <el-button
            v-if="row.quality?.reviewStatus === QualityReviewStatus.APPROVED"
            link
            type="warning"
            :loading="submittingId === row.itemId"
            @click="reinspect(row)"
            >发起复检</el-button
          >
          <el-button v-if="row.inspectionId" link type="primary" @click="preview(row)"
            >查看报告</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <template #footer><el-button @click="emit('update:visible', false)">关闭</el-button></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { inboundApi } from '@/api/inventory/inbound'
import { qualityApi, type QualityVO } from '@/api/production/quality'
import { useUserStore } from '@/store/modules/user'
import { InspectionResultEnum, IqcDispositionEnum } from '@/enums/inventory/InboundEnum'
import { QualityReviewStatus, QualityReviewStatusEnum } from '@/enums/quality/InspectionEnum'
import type { InboundItemVO } from '@/types/inventory/inbound'

const props = defineProps<{ visible: boolean; inboundId?: number; inboundNo?: string }>()
const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void
  (event: 'success'): void
}>()
const userStore = useUserStore()
const loading = ref(false)
const submittingId = ref('')
const rows = ref<
  Array<InboundItemVO & { itemId: string; quality?: QualityVO; history: QualityVO[] }>
>([])

watch(
  () => [props.visible, props.inboundId] as const,
  ([visible]) => {
    if (visible) load()
  },
  { immediate: true }
)

async function load() {
  if (!props.inboundId) return
  loading.value = true
  try {
    const inbound = (await inboundApi.getById(String(props.inboundId))).data
    rows.value = await Promise.all(
      (inbound?.items || []).map(async (item) => {
        const itemId = String(item.inboundItemId || item.itemId || '')
        const [qualityResult, history] = await Promise.all([
          item.inspectionId
            ? qualityApi.getById(Number(item.inspectionId)).then((res) => res.data)
            : undefined,
          qualityApi
            .page({ pageNum: 1, pageSize: 50, sourceType: 'INBOUND', sourceItemId: Number(itemId) })
            .then((res) => res.data?.records || []),
        ])
        return { ...item, itemId, quality: qualityResult || undefined, history }
      })
    )
  } finally {
    loading.value = false
  }
}

function reviewer(remark?: string) {
  return {
    approverId: String(userStore.userId || ''),
    approverName: String(userStore.nickName || userStore.userName || ''),
    remark,
  }
}

async function approve(row: (typeof rows.value)[number]) {
  const { value } = await ElMessageBox.prompt(
    `确认通过 ${row.materialCode} 的 IQC 审核？通过后报告将锁定。`,
    '单项 IQC 审核',
    {
      inputPlaceholder: '审核意见（选填）',
      confirmButtonText: '审核通过',
      cancelButtonText: '取消',
    }
  )
  submittingId.value = row.itemId
  try {
    await inboundApi.approveInspectionItem(row.itemId, reviewer(value?.trim() || undefined))
    ElMessage.success('审核通过')
    await load()
    emit('success')
  } finally {
    submittingId.value = ''
  }
}

async function reject(row: (typeof rows.value)[number]) {
  const { value } = await ElMessageBox.prompt(
    '请填写驳回原因，检验员修改后可重新提交。',
    '驳回单项 IQC',
    { inputValidator: (value) => !!value?.trim() || '驳回原因不能为空' }
  )
  submittingId.value = row.itemId
  try {
    await inboundApi.rejectInspectionItem(row.itemId, { ...reviewer(value), remark: value })
    ElMessage.success('已驳回')
    await load()
    emit('success')
  } finally {
    submittingId.value = ''
  }
}

async function reinspect(row: (typeof rows.value)[number]) {
  await ElMessageBox.confirm(`确认为 ${row.materialCode} 创建下一版复检记录？`, '发起复检')
  submittingId.value = row.itemId
  try {
    await inboundApi.reinspectItem(row.itemId)
    ElMessage.success('已创建复检版本')
    await load()
    emit('success')
  } finally {
    submittingId.value = ''
  }
}

function preview(row: (typeof rows.value)[number]) {
  window.open(
    `/production/quality-print/iqc-report?inboundId=${props.inboundId}&inspectionId=${row.inspectionId}`,
    '_blank'
  )
}
</script>

<style scoped>
.review-tip {
  margin-bottom: 16px;
}
.history-panel {
  padding: 12px 20px;
}
.history-panel > b {
  display: block;
  margin-bottom: 10px;
}
</style>
