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
          <template v-if="canJudge && row.quality?.reviewStatus === QualityReviewStatus.PENDING">
            <el-button
              link
              type="success"
              :loading="submittingId === row.itemId"
              :disabled="busy"
              @click="approve(row)"
              >通过</el-button
            >
            <el-button
              link
              type="danger"
              :loading="submittingId === row.itemId"
              :disabled="busy"
              @click="reject(row)"
              >驳回</el-button
            >
          </template>
          <el-button
            v-if="canInspect && row.quality?.reviewStatus === QualityReviewStatus.APPROVED"
            link
            type="warning"
            :loading="submittingId === row.itemId"
            :disabled="busy"
            @click="reinspect(row)"
            >发起复检</el-button
          >
          <el-button v-if="row.lotId ?? row.inspectionId" link type="primary" @click="preview(row)"
            >查看报告</el-button
          >
        </template>
      </el-table-column>
    </el-table>
    <template #footer>
      <el-button
        v-if="needSyncReviewStatus"
        type="primary"
        :loading="busy"
        @click="syncReviewStatus"
        >重算单据状态</el-button
      >
      <el-button @click="emit('update:visible', false)">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { inboundApi } from '@/api/inventory/inbound'
import { qualityApi, type QualityVO } from '@/api/production/quality'
import { useUserStore } from '@/store/modules/user'
import { hasPermi } from '@/directives'
import { InspectionResultEnum, IqcDispositionEnum, InboundOrderStatusEnum } from '@/enums/inventory/InboundEnum'
import { QualityReviewStatus, QualityReviewStatusEnum } from '@/enums/quality/InspectionEnum'
import type { InboundItemVO } from '@/types/inventory/inbound'

const props = defineProps<{ visible: boolean; inboundId?: number; inboundNo?: string }>()
const emit = defineEmits<{
  (event: 'update:visible', value: boolean): void
  (event: 'success'): void
}>()
const userStore = useUserStore()
const canJudge = computed(() =>
  hasPermi(['quality:lot:judge', 'inventory:inbound:approve'])
)
const canInspect = computed(() =>
  hasPermi(['quality:lot:inspect', 'inventory:inbound:edit'])
)
const loading = ref(false)
const submittingId = ref('')
/**
 * 并发/连点闸门（2026-09-21 dev-20260921-003）：
 * 原来 submittingId 是在确认框返回后才置位，确认框还没关就能再点一次「通过」，
 * 同一单据的两次复核并发提交 → 单据状态推进判定互相看不到对方结果（PO202609210001 实测卡住）。
 */
const busy = ref(false)
/** 当前入库单状态：用于判断「明细已全部审核但单据还没推进」需要重算 */
const inboundOrderStatus = ref<number>()
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
    // InboundVO.status 即 order_status 状态码（后端 vo.setStatus(order.getOrderStatus())）
    inboundOrderStatus.value = inbound?.status
    rows.value = await Promise.all(
      (inbound?.items || []).map(async (item) => {
        const itemId = String(item.inboundItemId || item.itemId || '')
        const [qualityResult, history] = await Promise.all([
          // dev-20260922-009：检验批在 lotId（inspectionId 已置空），优先取 lotId
          (item.lotId ?? item.inspectionId)
            ? qualityApi.getById(Number(item.lotId ?? item.inspectionId)).then((res) => res.data)
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
  if (busy.value) return
  busy.value = true
  try {
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
    await inboundApi.approveInspectionItem(row.itemId, reviewer(value?.trim() || undefined))
    ElMessage.success('审核通过')
    await load()
    emit('success')
  } finally {
    submittingId.value = ''
    busy.value = false
  }
}

async function reject(row: (typeof rows.value)[number]) {
  if (busy.value) return
  busy.value = true
  try {
    const { value } = await ElMessageBox.prompt(
      '请填写驳回原因，检验员修改后可重新提交。',
      '驳回单项 IQC',
      { inputValidator: (value) => !!value?.trim() || '驳回原因不能为空' }
    )
    submittingId.value = row.itemId
    await inboundApi.rejectInspectionItem(row.itemId, { ...reviewer(value), remark: value })
    ElMessage.success('已驳回')
    await load()
    emit('success')
  } finally {
    submittingId.value = ''
    busy.value = false
  }
}

async function reinspect(row: (typeof rows.value)[number]) {
  if (busy.value) return
  busy.value = true
  try {
    await ElMessageBox.confirm(`确认为 ${row.materialCode} 创建下一版复检记录？`, '发起复检')
    submittingId.value = row.itemId
    await inboundApi.reinspectItem(row.itemId)
    ElMessage.success('已创建复检版本')
    await load()
    emit('success')
  } finally {
    submittingId.value = ''
    busy.value = false
  }
}

/**
 * 明细已经全部审核通过、单据却还停在待审批（历史并发复核留下的状态）时，允许一键重算收尾。
 */
const needSyncReviewStatus = computed(
  () =>
    canJudge.value &&
    rows.value.length > 0 &&
    rows.value.every(
      (row) => row.quality?.reviewStatus === QualityReviewStatus.APPROVED
    ) &&
    inboundOrderStatus.value === InboundOrderStatusEnum.PENDING.value
)

async function syncReviewStatus() {
  if (busy.value) return
  busy.value = true
  try {
    const { data } = await inboundApi.syncReviewStatus(String(props.inboundId))
    if (data) {
      ElMessage.success('单据状态已推进为「已批准」，可以去确认入库')
    } else {
      ElMessage.warning('仍有明细未审核通过，单据状态未推进')
    }
    await load()
    emit('success')
  } finally {
    busy.value = false
  }
}

function preview(row: (typeof rows.value)[number]) {
  // dev-20260922-009：检验批 id 优先取 lotId（inspectionId 切新模型后已置空）
  const lotRef = row.lotId ?? row.inspectionId
  window.open(
    `/production/quality-print/iqc-report?inboundId=${props.inboundId}&inspectionId=${lotRef}`,
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
