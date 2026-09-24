<template>
  <el-dialog
    v-model="opened"
    title="来料检验"
    width="90%"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <div v-loading="loading">
      <el-descriptions v-if="inbound" :column="4" border>
        <el-descriptions-item label="单号">{{ inbound.inboundNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{
          inbound.supplierName || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ inbound.inboundDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来料批量">{{
          formatNumber(inbound.totalQuantity)
        }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="form.items" border class="inspection-table">
        <el-table-column label="检测项目" width="150">
          <template #default="{ row }">
            <el-button link type="primary" :disabled="row.locked" @click="openChecks(row)">
              录入{{ checkProgress(row) }}/{{ (row.inspectionItems || []).length }}
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="物料编码" prop="materialCode" width="130" />
        <el-table-column label="物料名称" prop="materialName" min-width="150" />
        <el-table-column label="IQC 状态" width="100">
          <template #default="{ row }">
            <el-tag :type="QualityReviewStatusEnum.getTagProps(row.reviewStatus || '').type">
              {{ QualityReviewStatusEnum.getLabel(row.reviewStatus || '') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="收货数量" prop="quantity" width="100" />
        <el-table-column label="合格数量" prop="qualifiedQuantity" width="100" />
        <el-table-column label="不良数量" prop="rejectedQuantity" width="100" />
        <el-table-column label="允收入库" prop="acceptedQuantity" width="140" />
        <el-table-column label="检验判定" width="110">
          <template #default="{ row }">
            <el-tag :type="InspectionResultEnum.getTagProps(row.inspectionResult).type">
              {{ InspectionResultEnum.getLabel(row.inspectionResult) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="不合格原因" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.inspectionResult === InspectionResultEnum.FAIL.value">{{
              deriveIqcReasonText(row) || '-'
            }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
      <el-form label-width="90px">
        <el-form-item label="检验备注"
          ><el-input
            v-model="form.inspectionRemark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
        /></el-form-item>
      </el-form>
    </div>
    <MaterialChecksDialog
      v-model:visible="checksVisible"
      :row="activeRow"
      :next-label="nextLabel"
      :readonly="!!activeRow?.locked"
      @saved="onChecksSaved"
      @next="openNextChecks"
    />
    <template #footer>
      <div class="dialog-footer">
        <span class="footer-tip">点「录入」填检验项目 · Tab 移动 · Enter 保存本行 · 键盘录入更快</span>
        <el-button @click="opened = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">提交检验</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { inboundApi } from '@/api/inventory/inbound'
import { qualityApi } from '@/api/production/quality'
import { InspectionResultEnum } from '@/enums/inventory/InboundEnum'
import {
  InspectionResult as QualityInspectionResult,
  InspectionResultEnum as QualityInspectionResultEnum,
  QualityReviewStatus,
  QualityReviewStatusEnum,
} from '@/enums/quality/InspectionEnum'
import { formatNumber } from '@/utils/format'
import { sanitize } from '@/utils/reasonSanitizer'
import type { InboundVO } from '@/types/inventory/inbound'
import MaterialChecksDialog from '@/views/inventory/iqc/components/MaterialChecksDialog.vue'
import { deriveIqcReasonText, iqcRowProblems } from '@/views/inventory/iqc/iqcRowRules'

const props = defineProps<{ visible: boolean; inboundId?: number; itemId?: number }>()
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'success'): void }>()
const opened = computed({ get: () => props.visible, set: (value) => emit('update:visible', value) })
const loading = ref(false)
const submitting = ref(false)
const inbound = ref<InboundVO | null>(null)
const form = reactive({ inspectionRemark: '', items: [] as any[] })
const checksVisible = ref(false)
const activeRow = ref<any>(null)

const defaultInspectionItems = () => [
  createCheck('规格', '与采购订单及实物一致', '核对', '目视'),
  createCheck('颜色', '与标准样板无明显偏差', '比较样板', '目视/样板'),
  createCheck('外观', '无脏污、黑点、变形、折伤、刮伤、混料、晶点、毛边', '目视', '目视'),
  createCheck('长度', '符合图纸或采购要求', '测量', '钢直尺/卡尺'),
  createCheck('宽度', '符合图纸或采购要求', '测量', '钢直尺/卡尺'),
  createCheck('厚度', '符合图纸或采购要求', '测量', '千分尺'),
  createCheck('特性', '附着力及其他特性符合要求', '测试', '3M600胶'),
  createCheck('包装、标识', '包装完整，标识与订单及实物一致并符合环保要求', '目视', '目视'),
]

function createCheck(
  checkItem: string,
  standard: string,
  inspectionMethod: string,
  equipment: string
) {
  return {
    checkItem,
    standard,
    inspectionMethod,
    equipment,
    actualValue: '',
    result: QualityInspectionResult.PASS,
    crQuantity: 0,
    maQuantity: 0,
    miQuantity: 0,
    remark: '',
  }
}

watch(
  () => props.visible,
  async (visible) => {
    if (!visible || !props.inboundId) return
    loading.value = true
    try {
      const { data } = await inboundApi.getById(String(props.inboundId))
      inbound.value = data
      form.inspectionRemark = ''
      form.items = await Promise.all(
        (data?.items || [])
          .filter((item) => !props.itemId || Number(item.inboundItemId || item.itemId) === props.itemId)
          .map(async (item) => {
          // dev-20260922-009：检验批在 lotId（inspectionId 已置空），优先取 lotId
          const lotRef = item.lotId ?? item.inspectionId
          const quality = lotRef
            ? (await qualityApi.getById(Number(lotRef))).data
            : undefined
          const previousQuality = quality?.previousInspectionId
            ? (await qualityApi.getById(Number(quality.previousInspectionId))).data
            : undefined
          // 返工/复检只检验上一版的不良数量，不能把整批收货数量和旧版结果带入新版本。
          const isReinspection =
            quality?.previousInspectionId &&
            quality?.result === QualityInspectionResult.PENDING
          const reinspectionQuantity = Number(previousQuality?.failQty || 0)
          return {
            itemId: item.inboundItemId || item.itemId,
            materialCode: item.materialCode,
            materialName: item.materialName,
            quantity: Number(item.quantity || 0),
            qualifiedQuantity: isReinspection
              ? reinspectionQuantity
              : Number(item.qualifiedQuantity ?? item.quantity ?? 0),
            rejectedQuantity: isReinspection ? 0 : Number(item.rejectedQuantity || 0),
            acceptedQuantity: isReinspection
              ? reinspectionQuantity
              : Number(item.acceptedQuantity ?? item.quantity ?? 0),
            isReinspection: Boolean(isReinspection),
            reinspectionQuantity,
            inspectionResult: isReinspection
              ? InspectionResultEnum.PASS.value
              : item.inspectionResult || InspectionResultEnum.PASS.value,
            disposition: isReinspection ? undefined : item.disposition,
            rejectReason: isReinspection ? '' : sanitize(item.rejectReason),
            reviewStatus: quality?.reviewStatus,
            locked: quality?.reviewStatus === QualityReviewStatus.APPROVED,
            inspectionItems: quality?.items?.length
              ? quality.items.map(normalizeInspectionItem)
              : defaultInspectionItems(),
          }
          })
      )
    } finally {
      loading.value = false
    }
  }
)

watch(
  () => form.items,
  (items) => {
    items.forEach((row) => {
      if (row.rejectReason.length > 200) {
        row.rejectReason = row.rejectReason.slice(0, 200)
        ElMessage.warning('不合格补充说明最多 200 字，已截断')
      }
    })
  },
  { deep: true }
)

/** 行内检验项完成度（已给结论的项数） */
function checkProgress(row: any) {
  const items: any[] = row?.inspectionItems || []
  return items.filter((check: any) =>
    [QualityInspectionResult.PASS, QualityInspectionResult.FAIL].includes(check.result)
  ).length
}
function editableRows(): any[] {
  return form.items.filter((item: any) => !item.locked)
}
function nextEditableRow(): any | undefined {
  const rows = editableRows()
  const index = rows.indexOf(activeRow.value)
  return index < 0 ? rows[0] : rows[index + 1]
}
const nextLabel = computed(() => {
  const next = nextEditableRow()
  return next ? `${next.materialCode} ${next.materialName}` : ''
})
function openChecks(row: any) {
  activeRow.value = row
  checksVisible.value = true
}
/** 保存本行：统一弹窗已按检验项汇总回写数量/判定，这里无需额外处理 */
function onChecksSaved() {
  /* no-op */
}
function openNextChecks() {
  const next = nextEditableRow()
  if (next) activeRow.value = next
  else ElMessage.info('已是最后一行可录入的材料')
}
function normalizeInspectionItem(check: any) {
  return {
    checkItem: check.checkItem,
    standard: check.standard,
    inspectionMethod: check.inspectionMethod,
    equipment: check.equipment,
    actualValue: check.actualValue,
    result: check.result,
    crQuantity: Number(check.crQuantity || 0),
    maQuantity: Number(check.maQuantity || 0),
    miQuantity: Number(check.miQuantity || 0),
    remark: check.remark,
  }
}
async function submit() {
  const problems: string[] = []
  const badRows = new Set<string>()
  for (const item of form.items) {
    item.acceptedQuantity = Number(item.qualifiedQuantity || 0)
    const issues = iqcRowProblems(item)
    if (issues.length) {
      problems.push(`${item.materialCode}：${issues.join('；')}`)
      badRows.add(String(item.itemId))
    }
  }
  if (problems.length) {
    const esc = (text: string) => text.replace(/</g, '&lt;').replace(/>/g, '&gt;')
    await ElMessageBox.alert(
      `<div style="max-height:320px;overflow:auto">${problems
        .map((p) => `<div>· ${esc(p)}</div>`)
        .join('')}</div>`,
      `还有 ${badRows.size} 行需要处理`,
      { dangerouslyUseHTMLString: true, confirmButtonText: '知道了' }
    ).catch(() => undefined)
    return
  }
  submitting.value = true
  try {
    const { data } = await inboundApi.submitApprove(String(props.inboundId), {
      inspectionRemark: form.inspectionRemark || undefined,
      items: form.items.map(
        ({
          itemId,
          inspectionResult,
          disposition,
          qualifiedQuantity,
          rejectedQuantity,
          acceptedQuantity,
          rejectReason,
          inspectionItems,
        }) => ({
          itemId,
          inspectionResult,
          disposition,
          qualifiedQuantity,
          rejectedQuantity,
          acceptedQuantity,
          rejectReason: rejectReason || undefined,
          inspectionItems: inspectionItems.map(normalizeInspectionItem),
        })
      ),
    })
    if (data) {
      ElMessage.success('检验已逐项提交，等待品质主管复核')
      emit('success')
      opened.value = false
    } else {
      ElMessage.error('检验提交未生效，请检查入库单状态或刷新后重试')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.inspection-table {
  margin: 16px 0;
}
.check-panel {
  padding: 12px 18px 18px;
  background: #f7f9fc;
}
.check-panel-title {
  margin-bottom: 10px;
  font-weight: 600;
}
.dialog-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}
.footer-tip {
  flex: 1;
  text-align: left;
  color: #909399;
  font-size: 12px;
}
.locked-checks {
  pointer-events: none;
  opacity: 0.72;
}
</style>
