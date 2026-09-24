<template>
  <el-dialog
    v-model="opened"
    :title="`${row?.materialCode || ''} ${row?.materialName || ''} 检测项目`"
    min-width="900px"
    max-width="1200px"
    destroy-on-close
    append-to-body
    :close-on-click-modal="false"
  >
    <el-table
      v-if="row"
      :data="row.inspectionItems"
      border
      size="small"
      :class="{ 'locked-checks': readonly }"
    >
      <el-table-column label="检验项目" prop="checkItem" width="100" />
      <el-table-column label="检验标准" min-width="210"
        ><template #default="{ row: check }"><el-input v-model="check.standard" /></template
      ></el-table-column>
      <el-table-column label="方法" width="130"
        ><template #default="{ row: check }"><el-input v-model="check.inspectionMethod" /></template
      ></el-table-column>
      <el-table-column label="设备" width="120"
        ><template #default="{ row: check }"><el-input v-model="check.equipment" /></template
      ></el-table-column>
      <el-table-column label="实测记录" min-width="180"
        ><template #default="{ row: check }"><el-input v-model="check.actualValue" /></template
      ></el-table-column>
      <el-table-column label="CR" width="100"
        ><template #default="{ row: check }"
          ><el-input-number
            v-model="check.crQuantity"
            :min="0"
            controls-position="right"
            @change="syncRow" /></template
      ></el-table-column>
      <el-table-column label="MA" width="100"
        ><template #default="{ row: check }"
          ><el-input-number
            v-model="check.maQuantity"
            :min="0"
            controls-position="right"
            @change="syncRow" /></template
      ></el-table-column>
      <el-table-column label="MI" width="100"
        ><template #default="{ row: check }"
          ><el-input-number
            v-model="check.miQuantity"
            :min="0"
            controls-position="right"
            @change="syncRow" /></template
      ></el-table-column>
      <el-table-column label="结论" width="120"
        ><template #default="{ row: check }"
          ><el-select v-model="check.result"
            ><el-option
              v-for="option in checkResultOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value" /></el-select></template
      ></el-table-column>
      <el-table-column label="备注" min-width="140"
        ><template #default="{ row: check }"
          ><el-input v-model="check.remark" @keyup.enter="save" /></template
      ></el-table-column>
      <el-table-column v-if="!readonly" label="操作" width="70" fixed="right"
        ><template #default="{ row: check }"
          ><el-button
            link
            type="danger"
            :disabled="row.inspectionItems.length <= 1"
            @click="removeCheck(check)"
            >删除</el-button
          ></template
        ></el-table-column
      >
    </el-table>
    <!-- dev-20260924-017：本行结论由检验项目自动汇总（数量/判定不手填，与成品检验同口径）；
         不合格时才需要选处置方式 + 填不合格原因 -->
    <div v-if="row" class="row-verdict">
      <div class="rv-head">
        本行结论（由检验项目自动汇总）
        <span class="rv-hint">逐项给结论即可，数量与判定自动算出</span>
      </div>
      <div class="rv-body">
        <span class="rv-item">收货数量 <b>{{ row.quantity }}</b></span>
        <span class="rv-item">合格 <b>{{ row.qualifiedQuantity ?? 0 }}</b></span>
        <span class="rv-item">不良 <b>{{ row.rejectedQuantity ?? 0 }}</b></span>
        <span class="rv-item"
          >判定
          <el-tag
            v-if="row.inspectionResult"
            :type="InboundInspectionResultEnum.getTagProps(row.inspectionResult).type"
            size="small"
            >{{ InboundInspectionResultEnum.getLabel(row.inspectionResult) }}</el-tag
          >
          <span v-else>未检</span>
        </span>
        <span class="rv-item">接收数量 <b>{{ row.acceptedQuantity ?? 0 }}</b></span>
      </div>
      <div v-if="isFail" class="rv-reason">
        <template v-if="!readonly">
          <el-select
            v-model="row.disposition"
            placeholder="处置方式（必选）"
            @change="syncDisposition(row)"
          >
            <el-option
              v-for="option in IqcDispositionEnum.items"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
          <el-input
            v-model="row.rejectReason"
            maxlength="500"
            placeholder="不合格原因（必填）"
            class="reason-input"
          />
        </template>
        <template v-else>
          <span class="rv-item">处置方式：{{ IqcDispositionEnum.getLabel(row.disposition) }}</span>
          <span class="rv-item">不合格原因：{{ row.rejectReason || '-' }}</span>
        </template>
      </div>
    </div>
    <template #footer>
      <div class="dialog-footer">
        <el-button v-if="!readonly" @click="batchPassRow">整批合格（本行）</el-button>
        <span class="footer-tip">实测可留空 · Tab 移动 · Enter 保存本行 · 键盘录入更快</span>
        <el-button @click="opened = false">取消</el-button>
        <el-button v-if="!readonly && nextLabel" @click="saveAndNext"
          >保存并下一行（{{ nextLabel }}）</el-button
        >
        <el-button v-if="!readonly" type="primary" @click="save">保存本行</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  InspectionResult as QualityInspectionResult,
  InspectionResultEnum as QualityInspectionResultEnum,
} from '@/enums/quality/InspectionEnum'
import {
  InspectionResultEnum as InboundInspectionResultEnum,
  IqcDispositionEnum,
} from '@/enums/inventory/InboundEnum'
import {
  batchPassIqcRow,
  iqcRowProblems,
  syncIqcDisposition,
  syncIqcRowFromChecks,
} from '../iqcRowRules'

const props = defineProps<{ visible: boolean; row?: any; nextLabel?: string; readonly?: boolean }>()
const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'saved'): void
  (e: 'next'): void
}>()
const opened = computed({ get: () => props.visible, set: (value) => emit('update:visible', value) })
const checkResultOptions = QualityInspectionResultEnum.items.filter(
  (item) => item.value !== QualityInspectionResult.PENDING
)

function syncDisposition(row: any) {
  syncIqcDisposition(row)
}
/** 本行判定是否不合格（由检验项目汇总而来） */
const isFail = computed(
  () => props.row?.inspectionResult === InboundInspectionResultEnum.FAIL.value
)
/** 保存前校验（与列表「提交检验」同一处规则：iqcRowProblems） */
function canSave(): boolean {
  const problems = iqcRowProblems(props.row)
  if (problems.length) {
    ElMessage.warning(problems.join('；'))
    return false
  }
  return true
}
/** 本行"整批合格"：结论合格 + 实测记录留空 + 缺陷数归零 + 合格/接收=收货数（全检口径） */
function batchPassRow() {
  batchPassIqcRow(props.row)
  ElMessage.success('本行已按整批合格填充，实测记录留空')
}
function syncRow() {
  syncIqcRowFromChecks(props.row)
}
function removeCheck(check: any) {
  const row = props.row
  if (!row) return
  if (row.inspectionItems.length <= 1) {
    ElMessage.warning('至少保留一项检测项目')
    return
  }
  const index = row.inspectionItems.indexOf(check)
  if (index < 0) return
  row.inspectionItems.splice(index, 1)
  syncRow()
}
function save() {
  syncRow()
  if (!canSave()) return
  emit('saved')
  opened.value = false
}
/** 保存本行并直接切到下一可编辑行（弹窗不关闭，连续录入，dev-20260916-008） */
function saveAndNext() {
  syncRow()
  if (!canSave()) return
  emit('saved')
  emit('next')
}
</script>

<style scoped>
.locked-checks {
  pointer-events: none;
  opacity: 0.65;
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

/* dev-20260924-017：本行结论区（数量/判定只读展示 + 不合格时补处置/原因） */
.row-verdict {
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 4px;
  background: var(--el-fill-color-lighter);
}

.rv-head {
  font-size: 13px;
  font-weight: 600;
  margin-bottom: 8px;
}

.rv-hint {
  margin-left: 8px;
  font-weight: 400;
  color: #909399;
  font-size: 12px;
}

.rv-body {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.rv-reason {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}
</style>
