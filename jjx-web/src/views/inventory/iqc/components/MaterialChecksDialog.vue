<template>
  <el-dialog
    v-model="opened"
    :title="`${row?.materialCode || ''} ${row?.materialName || ''} 检测项目`"
    width="900px"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <el-table
      v-if="row"
      :data="row.inspectionItems"
      border
      size="small"
      :class="{ 'locked-checks': row.locked }"
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
        ><template #default="{ row: check }"><el-input v-model="check.remark" /></template
      ></el-table-column>
      <el-table-column v-if="!row.locked" label="操作" width="70" fixed="right"
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
    <template #footer
      ><el-button @click="opened = false">取消</el-button
      ><el-button type="primary" @click="save">保存</el-button></template
    >
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { ElMessage } from 'element-plus'
import {
  InspectionResultEnum as InboundInspectionResultEnum,
  IqcDispositionEnum,
} from '@/enums/inventory/InboundEnum'
import {
  InspectionResult as QualityInspectionResult,
  InspectionResultEnum as QualityInspectionResultEnum,
} from '@/enums/quality/InspectionEnum'

const props = defineProps<{ visible: boolean; row?: any }>()
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'saved'): void }>()
const opened = computed({ get: () => props.visible, set: (value) => emit('update:visible', value) })
const checkResultOptions = QualityInspectionResultEnum.items.filter(
  (item) => item.value !== QualityInspectionResult.PENDING
)

function syncDisposition(row: any) {
  if (
    row.disposition === IqcDispositionEnum.CONCESSION.value ||
    row.disposition === IqcDispositionEnum.PARTIAL_ACCEPT.value
  )
    row.acceptedQuantity = Number(row.quantity || 0)
  else row.acceptedQuantity = 0
}
function syncRow() {
  const row = props.row
  if (!row) return
  const total = row.inspectionItems.reduce(
    (sum: number, check: any) =>
      sum +
      Number(check.crQuantity || 0) +
      Number(check.maQuantity || 0) +
      Number(check.miQuantity || 0),
    0
  )
  let sampled = Number(row.sampledQuantity || 0)
  if (sampled === 0 && total > 0) sampled = total
  row.sampledQuantity = sampled
  row.rejectedQuantity = Math.min(sampled, total)
  row.qualifiedQuantity = Math.max(0, sampled - row.rejectedQuantity)
  const critical = row.inspectionItems.reduce(
    (sum: number, check: any) => sum + Number(check.crQuantity || 0),
    0
  )
  if (critical > 0 || row.rejectedQuantity > 0)
    row.inspectionResult = InboundInspectionResultEnum.FAIL.value
  else if (row.qualifiedQuantity > 0) row.inspectionResult = InboundInspectionResultEnum.PASS.value
  else row.inspectionResult = ''
  if (row.inspectionResult === InboundInspectionResultEnum.PASS.value) {
    row.disposition = undefined
    row.acceptedQuantity = Number(row.quantity || 0)
  } else syncDisposition(row)
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
</script>

<style scoped>
.locked-checks {
  pointer-events: none;
  opacity: 0.65;
}
</style>
