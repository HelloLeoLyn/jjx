<template>
  <el-card class="defect-panel" shadow="never">
    <template #header>
      <div class="panel-head">
        <span class="panel-title">🧾 不良原因及改善（样品单级）</span>
        <span class="panel-sub">回填样品需求单（QR-065）的印刷 / 加工冲型制样记录</span>
      </div>
    </template>

    <el-table :data="list" size="small" border style="width: 100%">
      <el-table-column label="制样类别" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="row.craftType === SampleCraftTypeEnum.PRINT ? 'primary' : 'warning'">
            {{ craftLabel(row.craftType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="defectReason" label="不良原因" min-width="180" show-overflow-tooltip />
      <el-table-column prop="improvement" label="改善" min-width="180" show-overflow-tooltip />
      <el-table-column prop="recorderName" label="记录人" width="90" align="center" />
      <el-table-column prop="recordDate" label="日期" width="110" align="center" />
      <el-table-column v-if="canRecord && !readonly" label="操作" width="80" align="center">
        <template #default="{ row }">
          <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>暂无不良记录</template>
    </el-table>

    <div v-if="canRecord && !readonly" class="add-row">
      <el-select v-model="form.craftType" style="width: 130px" placeholder="制样类别">
        <el-option v-for="c in SAMPLE_CRAFT_TYPES" :key="c.value" :label="c.label" :value="c.value" />
      </el-select>
      <el-input v-model="form.defectReason" placeholder="不良原因" maxlength="500" style="width: 240px" />
      <el-input v-model="form.improvement" placeholder="改善" maxlength="500" style="width: 240px" />
      <el-date-picker v-model="form.recordDate" type="date" value-format="YYYY-MM-DD" placeholder="日期" style="width: 150px" />
      <el-button type="primary" :loading="saving" @click="add">新增</el-button>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import type { SampleDefectRecord } from '@/types/sales/sampleOrder'
import {
  SAMPLE_CRAFT_TYPES,
  SAMPLE_DEFECT_RECORD_PERMISSION,
  SampleCraftTypeEnum,
} from '@/enums/sales/SampleRequisitionEnum'
import { hasPermi } from '@/directives'

const props = defineProps<{
  orderId?: number
  roundNo?: number
  readonly?: boolean
}>()

const list = ref<SampleDefectRecord[]>([])
const saving = ref(false)
const form = reactive<{ craftType: string; defectReason: string; improvement: string; recordDate: string }>({
  craftType: SampleCraftTypeEnum.PRINT,
  defectReason: '',
  improvement: '',
  recordDate: '',
})

const canRecord = hasPermi(SAMPLE_DEFECT_RECORD_PERMISSION)

function craftLabel(code: string) {
  return SAMPLE_CRAFT_TYPES.find((c) => c.value === code)?.label || code
}

async function load() {
  if (!props.orderId) return
  try {
    const res: any = await sampleOrderApi.listDefects(props.orderId)
    list.value = res?.data || []
  } catch {
    list.value = []
  }
}

async function add() {
  if (!props.orderId) return
  if (!form.defectReason.trim()) {
    ElMessage.warning('请填写不良原因')
    return
  }
  saving.value = true
  try {
    await sampleOrderApi.addDefect(props.orderId, {
      craftType: form.craftType,
      defectReason: form.defectReason.trim(),
      improvement: form.improvement.trim() || undefined,
      recordDate: form.recordDate || undefined,
      roundNo: props.roundNo,
    })
    ElMessage.success('已记录')
    form.defectReason = ''
    form.improvement = ''
    form.recordDate = ''
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '记录失败')
  } finally {
    saving.value = false
  }
}

async function remove(row: SampleDefectRecord) {
  try {
    await ElMessageBox.confirm('确认删除该不良记录？', '提示', { type: 'warning' })
  } catch {
    return
  }
  try {
    await sampleOrderApi.deleteDefect(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

watch(() => props.orderId, load, { immediate: true })
</script>

<style scoped>
.defect-panel {
  margin-bottom: 12px;
}

.panel-head {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.panel-title {
  font-weight: 600;
}

.panel-sub {
  font-size: 12px;
  color: #909399;
}

.add-row {
  display: flex;
  gap: 8px;
  margin-top: 10px;
  flex-wrap: wrap;
}
</style>
