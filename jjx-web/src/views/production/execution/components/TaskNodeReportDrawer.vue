<template>
  <el-drawer
    :model-value="visible"
    title="生产报工（任务节点）"
    size="520px"
    append-to-body
    @update:model-value="emit('update:visible', $event)"
  >
    <template v-if="node">
      <!-- TaskNode 上下文：个人报工上限 = selfRemaining，不用 Execution 计划数量 -->
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="工单">{{ node.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="工序">{{ node.processName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="我的任务">{{ fmt(node.taskQuantity) }}</el-descriptions-item>
        <el-descriptions-item label="我已完成">{{ fmt(node.selfReported) }}</el-descriptions-item>
        <el-descriptions-item label="已分下级">{{ fmt(node.childOccupied) }}</el-descriptions-item>
        <el-descriptions-item label="本次最大可报">{{ fmt(node.selfRemaining) }}</el-descriptions-item>
        <el-descriptions-item label="我的剩余" :span="2">
          <span style="color: #409eff">{{ fmt(node.selfRemaining) }}</span>
        </el-descriptions-item>
      </el-descriptions>

      <el-form ref="formRef" :model="form" label-width="100px" style="margin-top: 12px">
        <el-form-item label="合格数量" required>
          <el-input-number v-model="form.qualifiedQuantity" :min="0" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item label="不良数量" required>
          <el-input-number v-model="form.defectiveQuantity" :min="0" :precision="4" style="width: 100%" />
        </el-form-item>
        <el-form-item v-if="Number(form.defectiveQuantity) > 0" label="不良原因" required>
          <el-input v-model="form.defectReason" type="textarea" :rows="2" placeholder="存在不良时必填" />
        </el-form-item>
        <el-form-item label="人工工时(h)">
          <el-input-number v-model="form.laborHours" :min="0" :step="0.1" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="机器工时(h)">
          <el-input-number v-model="form.machineHours" :min="0" :step="0.1" :precision="2" style="width: 100%" />
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.workStartTime" type="datetime" placeholder="可空" style="width: 100%" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item label="结束时间">
          <el-date-picker v-model="form.workEndTime" type="datetime" placeholder="可空" style="width: 100%" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item label="本次设备">
          <el-select v-model="form.equipmentId" placeholder="本次实际使用设备（空=默认设备）" clearable filterable style="width: 100%">
            <el-option v-for="eq in equipmentOptions" :key="eq.equipmentId" :label="`${eq.equipmentName}（${eq.equipmentNo}）`" :value="eq.equipmentId" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <div style="color: #909399; font-size: 12px">
          本次合格+不良不得超过“本次最大可报 {{ fmt(node.selfRemaining) }}”；报错后可在报工历史中撤销，容量自动恢复。
        </div>
      </el-form>
    </template>
    <template #footer>
      <el-button @click="emit('update:visible', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="handleSubmit">确认报工</el-button>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, computed, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { submitWorkReport } from '@/api/production/workReport'
import { getEquipmentList } from '@/api/production/equipment'
import type { MyTaskNodeVO } from '@/types/production/taskNode'

const props = defineProps<{
  visible: boolean
  node?: MyTaskNodeVO | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', v: boolean): void
  (e: 'changed'): void
}>()

const submitting = ref(false)
const equipmentOptions = ref<any[]>([])
const form = reactive({
  qualifiedQuantity: 0,
  defectiveQuantity: 0,
  laborHours: 0,
  machineHours: 0,
  workStartTime: undefined as string | undefined,
  workEndTime: undefined as string | undefined,
  equipmentId: undefined as number | undefined,
  defectReason: '',
  remark: '',
})

const maxReportable = computed(() => Number(props.node?.selfRemaining || 0))
const canSubmit = computed(() => {
  const q = Number(form.qualifiedQuantity || 0)
  const d = Number(form.defectiveQuantity || 0)
  return q + d > 0 && q + d <= maxReportable.value
})

function fmt(v?: number | null): string {
  return String(Number(v || 0))
}

watch(
  () => props.visible,
  async (v) => {
    if (!v) return
    Object.assign(form, {
      qualifiedQuantity: 0, defectiveQuantity: 0, laborHours: 0, machineHours: 0,
      workStartTime: undefined, workEndTime: undefined, equipmentId: undefined,
      defectReason: '', remark: '',
    })
    if (!equipmentOptions.value.length) {
      try {
        const res: any = await getEquipmentList({})
        equipmentOptions.value = res?.data || []
      } catch {
        equipmentOptions.value = []
      }
    }
  },
)

const handleSubmit = async () => {
  const node = props.node
  if (!node?.taskNodeId || !node.executionId) return
  const q = Number(form.qualifiedQuantity || 0)
  const d = Number(form.defectiveQuantity || 0)
  if (q < 0 || d < 0) { ElMessage.warning('数量不能为负数'); return }
  if (q + d <= 0) { ElMessage.warning('本次报工合格与不良数量之和必须大于 0'); return }
  if (d > 0 && !form.defectReason?.trim()) { ElMessage.warning('存在不良数量时，不良原因必填'); return }
  if (form.workStartTime && !form.workEndTime || !form.workStartTime && form.workEndTime) {
    ElMessage.warning('生产开始/结束时间需同时填写'); return
  }
  if (form.workStartTime && form.workEndTime && form.workEndTime < form.workStartTime) {
    ElMessage.warning('结束时间不能早于开始时间'); return
  }
  if (q + d > maxReportable.value) {
    ElMessage.warning(`本次报工数量超过节点剩余可报数量 ${fmt(maxReportable.value)}`)
    return
  }
  submitting.value = true
  try {
    await submitWorkReport({
      executionId: node.executionId,
      taskNodeId: node.taskNodeId,
      qualifiedQuantity: q,
      defectiveQuantity: d,
      laborHours: Number(form.laborHours || 0),
      machineHours: Number(form.machineHours || 0),
      workStartTime: form.workStartTime,
      workEndTime: form.workEndTime,
      equipmentId: form.equipmentId,
      defectReason: form.defectReason || undefined,
      remark: form.remark || undefined,
    })
    ElMessage.success('报工成功')
    emit('update:visible', false)
    emit('changed')
  } catch (e: any) {
    ElMessage.error(e?.message || '报工失败')
  } finally {
    submitting.value = false
  }
}
</script>
