<template>
  <el-dialog
    v-model="opened"
    :title="`${row?.materialCode || ''} ${row?.materialName || ''} 检测项目`"
    min-width="900px"
    max-width="1200px"
    destroy-on-close
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
    <template #footer>
      <div class="dialog-footer">
        <el-button v-if="!readonly" @click="batchPassRow">整批合格（本行）</el-button>
        <span class="footer-tip">实测记录可留空；Tab 移动、Enter 保存</span>
        <el-button @click="opened = false">取消</el-button>
        <el-button v-if="!readonly && nextLabel" @click="saveAndNext"
          >保存并下一行（{{ nextLabel }}）</el-button
        >
        <el-button v-if="!readonly" type="primary" @click="save">保存</el-button>
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
  batchPassIqcRow,
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
/** 本行"整批合格"：结论合格 + 实测记录留空 + 缺陷数归零 + 抽检/接收=收货数（dev-20260916-008 口径） */
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
  emit('saved')
  opened.value = false
}
/** 保存本行并直接切到下一可编辑行（弹窗不关闭，连续录入，dev-20260916-008） */
function saveAndNext() {
  syncRow()
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
</style>
