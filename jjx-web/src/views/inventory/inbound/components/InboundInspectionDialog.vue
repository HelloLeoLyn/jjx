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
      <el-table
        :data="form.items"
        :row-key="getRowKey"
        :expand-row-keys="expandedRowKeys"
        border
        class="inspection-table"
        @expand-change="handleExpandChange"
      >
        <el-table-column type="expand" width="48">
          <template #default="{ row }">
            <div class="check-panel">
              <div class="check-panel-title">
                {{ row.materialCode }} 检验项目（将回填到 QR-037）
              </div>
              <el-table
                :data="row.inspectionItems"
                border
                size="small"
                :class="{ 'locked-checks': row.locked }"
              >
                <el-table-column label="检验项目" prop="checkItem" width="100" />
                <el-table-column label="检验标准" min-width="210">
                  <template #default="{ row: check }"
                    ><el-input v-model="check.standard"
                  /></template>
                </el-table-column>
                <el-table-column label="方法" width="130">
                  <template #default="{ row: check }"
                    ><el-input v-model="check.inspectionMethod"
                  /></template>
                </el-table-column>
                <el-table-column label="设备" width="120">
                  <template #default="{ row: check }"
                    ><el-input v-model="check.equipment"
                  /></template>
                </el-table-column>
                <el-table-column label="实测/检查记录" min-width="180">
                  <template #default="{ row: check }"
                    ><el-input v-model="check.actualValue"
                  /></template>
                </el-table-column>
                <el-table-column label="CR" width="90">
                  <template #default="{ row: check }"
                    ><el-input-number
                      v-model="check.crQuantity"
                      :min="0"
                      controls-position="right"
                      @change="syncDefects(row)"
                  /></template>
                </el-table-column>
                <el-table-column label="MA" width="90">
                  <template #default="{ row: check }"
                    ><el-input-number
                      v-model="check.maQuantity"
                      :min="0"
                      controls-position="right"
                      @change="syncDefects(row)"
                  /></template>
                </el-table-column>
                <el-table-column label="MI" width="90">
                  <template #default="{ row: check }"
                    ><el-input-number
                      v-model="check.miQuantity"
                      :min="0"
                      controls-position="right"
                      @change="syncDefects(row)"
                  /></template>
                </el-table-column>
                <el-table-column label="项目结论" width="120">
                  <template #default="{ row: check }">
                    <el-select v-model="check.result">
                      <el-option
                        v-for="option in QualityInspectionResultEnum.items.filter(
                          (item) => item.value !== QualityInspectionResult.PENDING
                        )"
                        :key="option.value"
                        :label="option.label"
                        :value="option.value"
                      />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column label="备注" min-width="140">
                  <template #default="{ row: check }"><el-input v-model="check.remark" /></template>
                </el-table-column>
              </el-table>
            </div>
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
        <el-table-column label="抽检数量" width="140">
          <template #default="{ row }"
            ><el-input-number
              v-model="row.sampledQuantity"
              :min="0"
              :max="row.quantity"
              :disabled="row.locked"
              controls-position="right"
          /></template>
        </el-table-column>
        <el-table-column label="样品合格数" width="140">
          <template #default="{ row }"
            ><el-input-number
              v-model="row.qualifiedQuantity"
              :min="0"
              :max="row.quantity"
              :disabled="row.locked"
              controls-position="right"
          /></template>
        </el-table-column>
        <el-table-column label="样品不良数" width="140">
          <template #default="{ row }"
            ><el-input-number
              v-model="row.rejectedQuantity"
              :min="0"
              :max="row.quantity"
              :disabled="row.locked"
              controls-position="right"
          /></template>
        </el-table-column>
        <el-table-column label="允收入库" width="140">
          <template #default="{ row }"
            ><el-input-number
              v-model="row.acceptedQuantity"
              :min="0"
              :max="row.quantity"
              :disabled="row.locked"
              controls-position="right"
          /></template>
        </el-table-column>
        <el-table-column label="检验判定" width="150">
          <template #default="{ row }">
            <el-select
              v-model="row.inspectionResult"
              :disabled="row.locked"
              @change="handleResultChange(row)"
            >
              <el-option
                v-for="option in InspectionResultEnum.items.filter(
                  (item) => item.value !== InspectionResultEnum.OTHER.value
                )"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="不合格处置" width="180">
          <template #default="{ row }">
            <el-select
              v-if="row.inspectionResult === InspectionResultEnum.FAIL.value"
              v-model="row.disposition"
              placeholder="请选择"
              :disabled="row.locked"
              @change="syncDisposition(row)"
            >
              <el-option
                v-for="option in IqcDispositionEnum.items"
                :key="option.value"
                :label="option.label"
                :value="option.value"
              />
            </el-select>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="总体缺陷说明" min-width="160">
          <template #default="{ row }"
            ><el-input v-model="row.rejectReason" maxlength="255" :disabled="row.locked"
          /></template>
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
    <template #footer>
      <el-button @click="opened = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">提交检验</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { inboundApi } from '@/api/inventory/inbound'
import { qualityApi } from '@/api/production/quality'
import { InspectionResultEnum, IqcDispositionEnum } from '@/enums/inventory/InboundEnum'
import {
  InspectionResult as QualityInspectionResult,
  InspectionResultEnum as QualityInspectionResultEnum,
  QualityReviewStatus,
  QualityReviewStatusEnum,
} from '@/enums/quality/InspectionEnum'
import { formatNumber } from '@/utils/format'
import type { InboundVO } from '@/types/inventory/inbound'

const props = defineProps<{ visible: boolean; inboundId?: number; itemId?: number }>()
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'success'): void }>()
const opened = computed({ get: () => props.visible, set: (value) => emit('update:visible', value) })
const loading = ref(false)
const submitting = ref(false)
const inbound = ref<InboundVO | null>(null)
const form = reactive({ inspectionRemark: '', items: [] as any[] })
const expandedRowKeys = ref<string[]>([])

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
      expandedRowKeys.value = []
      form.items = await Promise.all(
        (data?.items || [])
          .filter((item) => !props.itemId || Number(item.inboundItemId || item.itemId) === props.itemId)
          .map(async (item) => {
          const quality = item.inspectionId
            ? (await qualityApi.getById(Number(item.inspectionId))).data
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
            sampledQuantity: isReinspection
              ? reinspectionQuantity
              : Number(item.sampledQuantity ?? item.quantity ?? 0),
            qualifiedQuantity: isReinspection
              ? reinspectionQuantity
              : Number(item.qualifiedQuantity ?? item.quantity ?? 0),
            rejectedQuantity: isReinspection ? 0 : Number(item.rejectedQuantity || 0),
            acceptedQuantity: isReinspection
              ? reinspectionQuantity
              : Number(item.acceptedQuantity ?? item.quantity ?? 0),
            inspectionResult: isReinspection
              ? InspectionResultEnum.PASS.value
              : item.inspectionResult || InspectionResultEnum.PASS.value,
            disposition: isReinspection ? undefined : item.disposition,
            rejectReason: isReinspection ? '' : item.rejectReason || '',
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

function defectTotal(row: any) {
  return row.inspectionItems.reduce(
    (sum: number, check: any) =>
      sum +
      Number(check.crQuantity || 0) +
      Number(check.maQuantity || 0) +
      Number(check.miQuantity || 0),
    0
  )
}
function syncDefects(row: any) {
  const total = defectTotal(row)
  row.rejectedQuantity = Math.min(Number(row.sampledQuantity || 0), total)
  row.qualifiedQuantity = Math.max(0, Number(row.sampledQuantity || 0) - row.rejectedQuantity)
  const critical = row.inspectionItems.reduce(
    (sum: number, check: any) => sum + Number(check.crQuantity || 0),
    0
  )
  if (critical > 0 && row.inspectionResult !== InspectionResultEnum.FAIL.value) {
    row.inspectionResult = InspectionResultEnum.FAIL.value
    row.acceptedQuantity = Number(row.qualifiedQuantity || 0)
  }
}
function handleResultChange(row: any) {
  if (row.inspectionResult === InspectionResultEnum.PASS.value) {
    row.disposition = undefined
    row.acceptedQuantity = Number(row.quantity || 0)
  } else if (row.inspectionResult === InspectionResultEnum.FAIL.value) {
    row.acceptedQuantity = Number(row.qualifiedQuantity || 0)
  }
}
function handleExpandChange(_: any, expandedRows: any[]) {
  expandedRowKeys.value = expandedRows.map(getRowKey)
}
function getRowKey(row: any) {
  return String(row.itemId)
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
function syncDisposition(row: any) {
  if (row.disposition === IqcDispositionEnum.CONCESSION.value)
    row.acceptedQuantity = Number(row.quantity || 0)
  else if (
    row.disposition === IqcDispositionEnum.RETURN.value ||
    row.disposition === IqcDispositionEnum.SCRAP.value ||
    row.disposition === IqcDispositionEnum.REINSPECT.value ||
    row.disposition === IqcDispositionEnum.HOLD.value ||
    row.disposition === IqcDispositionEnum.SUPPLIER_REWORK.value
  )
    row.acceptedQuantity = 0
  else row.acceptedQuantity = Number(row.qualifiedQuantity || 0)
}

async function submit() {
  for (const item of form.items) {
    if (
      Number(item.sampledQuantity) !==
      Number(item.qualifiedQuantity) + Number(item.rejectedQuantity)
    ) {
      ElMessage.warning(`${item.materialCode}：抽检数量须等于合格与不良数量之和`)
      return
    }
    if (item.inspectionResult === InspectionResultEnum.FAIL.value && !item.disposition) {
      ElMessage.warning(`${item.materialCode}：不合格时必须选择处置方式`)
      return
    }
    if (Number(item.acceptedQuantity) > Number(item.quantity)) {
      ElMessage.warning(`${item.materialCode}：允收入库数量不能超过收货数量`)
      return
    }
  }
  submitting.value = true
  try {
    const { data } = await inboundApi.submitApprove(String(props.inboundId), {
      inspectionRemark: form.inspectionRemark || undefined,
      items: form.items.map(
        ({
          itemId,
          sampledQuantity,
          inspectionResult,
          disposition,
          qualifiedQuantity,
          rejectedQuantity,
          acceptedQuantity,
          rejectReason,
          inspectionItems,
        }) => ({
          itemId,
          sampledQuantity,
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
.locked-checks {
  pointer-events: none;
  opacity: 0.72;
}
</style>
