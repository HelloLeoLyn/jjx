<template>
  <div class="m-report">
    <header class="m-header">
      <el-button link @click="router.back()">← 返回</el-button>
      <span class="m-header-title">报工</span>
      <span class="m-header-spacer"></span>
    </header>

    <div v-loading="loading" class="m-report-body">
      <!-- 工序信息 -->
      <div class="m-report-card">
        <div class="m-report-order">{{ orderNo }}</div>
        <div class="m-report-process">{{ processName }}</div>
      </div>

      <!-- 我的任务选择 -->
      <template v-if="tasks.length">
        <div class="m-section-title">我的任务</div>
        <div
          v-for="t in tasks"
          :key="t.taskId"
          class="m-task-card"
          :class="{ active: selectedTaskId === t.taskId }"
          @click="selectedTaskId = t.taskId"
        >
          <div class="m-task-head">
            <span class="m-task-no">{{ t.taskNo || `任务#${t.taskId}` }}</span>
            <el-tag size="small">{{ t.statusLabel || t.status }}</el-tag>
          </div>
          <div class="m-task-qty">
            任务量 {{ fmtQty(t.taskQuantity) }} · 已完成 {{ fmtQty(t.completedQuantity) }} · 剩余
            {{ fmtQty(t.remainingQuantity) }}
          </div>
        </div>

        <!-- 报工表单 -->
        <div class="m-section-title">本次报工</div>
        <div class="m-report-form">
          <div class="m-form-item">
            <label>合格数量 <span class="m-req">*</span></label>
            <el-input-number
              v-model="reportForm.qualifiedQuantity"
              :min="0"
              :precision="0"
              controls-position="right"
              class="m-form-input"
              placeholder="合格数"
            />
          </div>
          <div class="m-form-item">
            <label>不良数量</label>
            <el-input-number
              v-model="reportForm.defectiveQuantity"
              :min="0"
              :precision="0"
              controls-position="right"
              class="m-form-input"
              placeholder="不良数"
            />
          </div>
          <div v-if="Number(reportForm.defectiveQuantity) > 0" class="m-form-item">
            <label>不良原因 <span class="m-req">*</span></label>
            <el-input
              v-model="reportForm.defectReason"
              class="m-form-input"
              placeholder="不良数量大于 0 时必填"
              clearable
            />
          </div>
          <div class="m-form-item">
            <label>人工工时 (h)</label>
            <el-input-number
              v-model="reportForm.laborHours"
              :min="0"
              :precision="2"
              controls-position="right"
              class="m-form-input"
              placeholder="可空"
            />
          </div>
          <div class="m-form-item">
            <label>机器工时 (h)</label>
            <el-input-number
              v-model="reportForm.machineHours"
              :min="0"
              :precision="2"
              controls-position="right"
              class="m-form-input"
              placeholder="可空"
            />
          </div>
          <div class="m-form-item">
            <label>备注</label>
            <el-input
              v-model="reportForm.remark"
              type="textarea"
              :rows="2"
              class="m-form-input"
              placeholder="可空"
            />
          </div>
        </div>

        <el-button
          type="primary"
          size="large"
          class="m-report-btn"
          :loading="submitting"
          @click="handleSubmit"
        >
          提交报工
        </el-button>
      </template>
      <el-empty v-else-if="!loading" description="该工序暂无我的任务，无法报工" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getMyTasks } from '@/api/production/task'
import { submitWorkReport } from '@/api/production/workReport'
import type { TaskTreeRow } from '@/types/production/task'

const route = useRoute()
const router = useRouter()

const executionId = computed(() => Number(route.query.executionId || 0))
const orderNo = computed(() => String(route.query.orderNo || ''))
const processName = computed(() => String(route.query.processName || ''))

const loading = ref(false)
const submitting = ref(false)
const tasks = ref<TaskTreeRow[]>([])
const selectedTaskId = ref<number | null>(null)

const reportForm = ref({
  qualifiedQuantity: 0,
  defectiveQuantity: 0,
  defectReason: '',
  laborHours: undefined as number | undefined,
  machineHours: undefined as number | undefined,
  remark: '',
})

function fmtQty(v?: number | string | null): string {
  const n = Number(v || 0)
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

async function loadTasks() {
  if (!executionId.value) return
  loading.value = true
  try {
    const res: any = await getMyTasks(executionId.value)
    tasks.value = res?.data || []
    // 默认选中第一个有剩余额度的任务
    const first = tasks.value.find((t) => Number(t.remainingQuantity || 0) > 0)
    selectedTaskId.value = first?.taskId ?? tasks.value[0]?.taskId ?? null
  } catch (e: any) {
    ElMessage.error(e?.message || '加载任务失败')
  } finally {
    loading.value = false
  }
}

async function handleSubmit() {
  const task = tasks.value.find((t) => t.taskId === selectedTaskId.value)
  if (!task) {
    ElMessage.warning('请先选择要报工的任务')
    return
  }
  const qualified = Number(reportForm.value.qualifiedQuantity || 0)
  const defective = Number(reportForm.value.defectiveQuantity || 0)
  if (qualified + defective <= 0) {
    ElMessage.warning('合格与不良数量之和必须大于 0')
    return
  }
  if (defective > 0 && !reportForm.value.defectReason.trim()) {
    ElMessage.warning('不良数量大于 0 时，不良原因必填')
    return
  }

  submitting.value = true
  try {
    const res: any = await submitWorkReport({
      executionId: executionId.value,
      taskId: task.taskId,
      qualifiedQuantity: qualified,
      defectiveQuantity: defective,
      laborHours: Number(reportForm.value.laborHours || 0) || undefined,
      machineHours: Number(reportForm.value.machineHours || 0) || undefined,
      defectReason: defective > 0 ? reportForm.value.defectReason.trim() : undefined,
      remark: reportForm.value.remark.trim() || undefined,
    })
    if (!res?.data) throw new Error(res?.msg || '报工提交失败')
    ElMessage.success('报工已提交，等待审批')
    // 重置表单并刷新任务剩余
    reportForm.value = {
      qualifiedQuantity: 0,
      defectiveQuantity: 0,
      defectReason: '',
      laborHours: undefined,
      machineHours: undefined,
      remark: '',
    }
    await loadTasks()
  } catch (e: any) {
    ElMessage.error(e?.message || '报工提交失败')
  } finally {
    submitting.value = false
  }
}

loadTasks()
</script>

<style scoped>
.m-report {
  min-height: 100vh;
  background: #f5f7fa;
}
.m-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  position: sticky;
  top: 0;
  z-index: 10;
}
.m-header-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.m-header-spacer {
  width: 48px;
}
.m-report-body {
  padding: 12px;
}
.m-report-card {
  background: #fff;
  border-radius: 10px;
  padding: 14px;
  margin-bottom: 12px;
  border: 1px solid #ebeef5;
}
.m-report-order {
  font-size: 13px;
  color: #909399;
  margin-bottom: 4px;
}
.m-report-process {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.m-section-title {
  font-size: 14px;
  font-weight: 600;
  color: #606266;
  margin: 4px 0 10px;
}
.m-task-card {
  background: #fff;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid #ebeef5;
}
.m-task-card.active {
  border-color: #409eff;
  box-shadow: 0 0 0 1px #409eff;
}
.m-task-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.m-task-no {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.m-task-qty {
  font-size: 13px;
  color: #606266;
}
.m-report-form {
  background: #fff;
  border-radius: 10px;
  padding: 14px;
  border: 1px solid #ebeef5;
}
.m-form-item {
  margin-bottom: 14px;
}
.m-form-item label {
  display: block;
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
}
.m-req {
  color: #f56c6c;
}
.m-form-input {
  width: 100%;
}
.m-report-btn {
  width: 100%;
  height: 48px;
  font-size: 16px;
  margin-top: 16px;
}
</style>
