<template>
  <div class="sampling-page">
    <el-card>
      <template #header>
        <div class="header">
          <span>抽样方案（AQL）</span>
          <div>
            <span class="tip">来料检验建批时按批量区间自动带出样本量 / AC / RE；成品全检不使用</span>
            <el-button type="primary" @click="openForm()">新增方案</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border size="small">
        <template #empty><el-empty description="暂无抽样方案（未配置时会按全检提示）" /></template>
        <el-table-column prop="planName" label="方案名" min-width="180" />
        <el-table-column label="适用" width="90">
          <template #default="{ row }">{{ row.lotType === 'ALL' ? '全部' : row.lotType === 'IQC' ? '来料' : '成品' }}</template>
        </el-table-column>
        <el-table-column prop="aqlValue" label="AQL" width="80" />
        <el-table-column prop="inspectionLevel" label="检验水平" width="90" />
        <el-table-column label="批量区间" min-width="150">
          <template #default="{ row }">{{ num(row.lotMin) }} ~ {{ num(row.lotMax) }}</template>
        </el-table-column>
        <el-table-column label="样本量" width="90" align="right">
          <template #default="{ row }">{{ num(row.sampleQuantity) }}</template>
        </el-table-column>
        <el-table-column label="AC" width="70" align="right">
          <template #default="{ row }">{{ num(row.acceptNumber) }}</template>
        </el-table-column>
        <el-table-column label="RE" width="70" align="right">
          <template #default="{ row }">{{ num(row.rejectNumber) }}</template>
        </el-table-column>
        <el-table-column label="启用" width="80">
          <template #default="{ row }">
            <el-tag size="small" :type="row.isEnabled ? 'success' : 'info'">{{ row.isEnabled ? '启用' : '停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openForm(row)">修改</el-button>
            <el-button link type="danger" size="small" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="visible" :title="form.planId ? '修改抽样方案' : '新增抽样方案'" width="560px" append-to-body>
      <el-form label-width="110px">
        <el-form-item label="方案名" required>
          <el-input v-model="form.planName" placeholder="如 AQL1.0 一般检验II级" />
        </el-form-item>
        <el-form-item label="适用类型">
          <el-select v-model="form.lotType" style="width: 100%">
            <el-option label="来料检验" value="IQC" />
            <el-option label="成品检验" value="FQC" />
            <el-option label="全部" value="ALL" />
          </el-select>
        </el-form-item>
        <el-form-item label="AQL 值">
          <el-input-number v-model="form.aqlValue" :min="0" :precision="3" :step="0.1" />
        </el-form-item>
        <el-form-item label="检验水平">
          <el-select v-model="form.inspectionLevel" style="width: 100%">
            <el-option label="I" value="I" />
            <el-option label="II" value="II" />
            <el-option label="III" value="III" />
          </el-select>
        </el-form-item>
        <el-form-item label="批量区间" required>
          <el-input-number v-model="form.lotMin" :min="0" />
          <span class="gap">~</span>
          <el-input-number v-model="form.lotMax" :min="0" />
        </el-form-item>
        <el-form-item label="样本量" required>
          <el-input-number v-model="form.sampleQuantity" :min="1" />
        </el-form-item>
        <el-form-item label="AC / RE" required>
          <el-input-number v-model="form.acceptNumber" :min="0" />
          <span class="gap">/</span>
          <el-input-number v-model="form.rejectNumber" :min="0" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.isEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { qualitySamplingApi } from '@/api/quality/lot'

const loading = ref(false)
const saving = ref(false)
const rows = ref<Record<string, any>[]>([])
const visible = ref(false)
const num = (value?: number | null) =>
  value == null ? '-' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })

const emptyForm = () => ({
  planId: undefined as number | undefined,
  planName: '',
  lotType: 'IQC',
  aqlValue: 1,
  inspectionLevel: 'II',
  lotMin: 0,
  lotMax: 0,
  sampleQuantity: 1,
  acceptNumber: 0,
  rejectNumber: 1,
  isEnabled: 1,
  remark: '',
})
const form = reactive(emptyForm())

const load = async () => {
  loading.value = true
  try {
    const res: any = await qualitySamplingApi.list()
    rows.value = res?.data || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载抽样方案失败')
    rows.value = []
  } finally {
    loading.value = false
  }
}
const openForm = (row?: Record<string, any>) => {
  Object.assign(form, emptyForm())
  if (row) Object.assign(form, row)
  visible.value = true
}
const submit = async () => {
  if (!form.planName.trim()) return ElMessage.warning('请填写方案名')
  if (Number(form.lotMin) > Number(form.lotMax)) return ElMessage.warning('批量下限不能大于上限')
  saving.value = true
  try {
    await qualitySamplingApi.save({ ...form })
    ElMessage.success('已保存')
    visible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}
const remove = async (row: Record<string, any>) => {
  try {
    await ElMessageBox.confirm(`确认删除方案「${row.planName}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await qualitySamplingApi.remove(row.planId)
    ElMessage.success('已删除')
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

onMounted(load)
</script>

<style scoped>
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.tip {
  color: #909399;
  font-size: 12px;
  margin-right: 12px;
}
.gap {
  margin: 0 8px;
}
</style>
