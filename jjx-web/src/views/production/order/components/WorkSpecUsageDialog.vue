<template>
  <el-dialog
    v-model="visible"
    title="换版登记（工单执行区间）"
    width="820px"
    append-to-body
    destroy-on-close
    @open="handleOpen"
  >
    <el-alert type="info" :closable="false" style="margin-bottom: 12px">
      换版须绑定「同产品且已发布」的作业规范版本；登记后工单当前执行版本更新为新版本，历史段落在下方区间表中可追溯。
    </el-alert>

    <el-form :model="form" label-width="120px">
      <el-form-item label="工单">
        <span>{{ order?.orderNo }}（{{ order?.productName }} / {{ order?.productCode }}）</span>
      </el-form-item>
      <el-form-item label="新版本" required>
        <el-select
          v-model="form.specVersionId"
          placeholder="选择已发布的作业规范版本"
          style="width: 320px"
          :loading="versionsLoading"
        >
          <el-option
            v-for="item in publishedVersions"
            :key="item.id"
            :label="`${item.versionNo}${item.changeSummary ? '（' + item.changeSummary + '）' : ''}`"
            :value="item.id"
          />
        </el-select>
        <span v-if="!versionsLoading && !publishedVersions.length" class="tip-warn">
          该产品暂无已发布版本
        </span>
      </el-form-item>
      <el-form-item label="数量区间">
        <el-input-number
          v-model="form.qtyFrom"
          :min="0"
          :controls="false"
          placeholder="起（含）"
          style="width: 150px"
        />
        <span class="range-sep">~</span>
        <el-input-number
          v-model="form.qtyTo"
          :min="0"
          :controls="false"
          placeholder="止（含）"
          style="width: 150px"
        />
      </el-form-item>
      <el-form-item label="生效时间">
        <el-date-picker
          v-model="form.startTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="开始时间"
          style="width: 220px"
        />
        <span class="range-sep">~</span>
        <el-date-picker
          v-model="form.endTime"
          type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss"
          placeholder="结束时间"
          style="width: 220px"
        />
      </el-form-item>
      <el-form-item label="换版原因">
        <el-input v-model="form.changeReason" type="textarea" :rows="2" maxlength="500" show-word-limit />
      </el-form-item>
      <el-form-item label="批准人">
        <el-input v-model="form.approvedBy" placeholder="留空默认当前登录人" style="width: 260px" />
      </el-form-item>
    </el-form>

    <div class="usage-title">执行区间</div>
    <el-table :data="usages" border size="small" v-loading="usagesLoading">
      <el-table-column label="版本" min-width="120">
        <template #default="{ row }">
          {{ row.versionNo || `#${row.specVersionId}` }}
        </template>
      </el-table-column>
      <el-table-column label="数量区间" min-width="130">
        <template #default="{ row }">{{ qtyText(row) }}</template>
      </el-table-column>
      <el-table-column label="生效时间" min-width="200">
        <template #default="{ row }">{{ timeText(row) }}</template>
      </el-table-column>
      <el-table-column prop="approvedBy" label="批准人" width="110" />
      <el-table-column prop="changeReason" label="原因" min-width="160" show-overflow-tooltip />
      <el-table-column prop="createTime" label="登记时间" width="160" />
      <el-table-column label="操作" width="80" align="center">
        <template #default="{ row }">
          <el-button
            link
            type="danger"
            v-hasPermi="['production:order:edit']"
            @click="handleDelete(row)"
          >
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="visible = false">关闭</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        v-hasPermi="['production:order:edit']"
        @click="handleSubmit"
      >
        登记换版
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { productWorkSpecApi } from '@/api/product/workSpec'
import { workSpecUsageApi, type WorkSpecUsageVO } from '@/api/production/workSpecUsage'
import { WorkSpecVersionStatusEnum } from '@/enums/product/WorkSpecVersionStatusEnum'
import type { ProductionOrderVO } from '@/types/production/order'

const props = defineProps<{
  visible: boolean
  order: ProductionOrderVO | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  success: []
}>()

const visible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
})

interface VersionItem {
  id: number
  versionNo: string
  status: string
  changeSummary?: string
}

const versions = ref<VersionItem[]>([])
const versionsLoading = ref(false)
const publishedVersions = computed(() =>
  versions.value.filter((v) => v.status === WorkSpecVersionStatusEnum.PUBLISHED.value),
)

const usages = ref<WorkSpecUsageVO[]>([])
const usagesLoading = ref(false)
const submitting = ref(false)

const form = reactive<{
  specVersionId: number | null
  qtyFrom: number | null
  qtyTo: number | null
  startTime: string | null
  endTime: string | null
  changeReason: string
  approvedBy: string
}>({
  specVersionId: null,
  qtyFrom: null,
  qtyTo: null,
  startTime: null,
  endTime: null,
  changeReason: '',
  approvedBy: '',
})

function resetForm() {
  form.specVersionId = null
  form.qtyFrom = null
  form.qtyTo = null
  form.startTime = null
  form.endTime = null
  form.changeReason = ''
  form.approvedBy = ''
}

function qtyText(row: WorkSpecUsageVO) {
  if (row.qtyFrom == null && row.qtyTo == null) return '不限'
  return `${row.qtyFrom ?? ''} ~ ${row.qtyTo ?? ''}`
}

function timeText(row: WorkSpecUsageVO) {
  if (!row.startTime && !row.endTime) return '不限'
  return `${row.startTime || ''} ~ ${row.endTime || ''}`
}

async function loadVersions() {
  if (!props.order?.productId) {
    versions.value = []
    return
  }
  versionsLoading.value = true
  try {
    const res: any = await productWorkSpecApi.listVersions(Number(props.order.productId))
    versions.value = res?.data || []
  } catch {
    versions.value = []
  } finally {
    versionsLoading.value = false
  }
}

async function loadUsages() {
  if (!props.order?.orderId) {
    usages.value = []
    return
  }
  usagesLoading.value = true
  try {
    const res: any = await workSpecUsageApi.list(Number(props.order.orderId))
    usages.value = res?.data || []
  } catch {
    usages.value = []
  } finally {
    usagesLoading.value = false
  }
}

function handleOpen() {
  resetForm()
  loadVersions()
  loadUsages()
}

async function handleSubmit() {
  if (!props.order?.orderId) return
  if (!form.specVersionId) {
    ElMessage.warning('请选择要绑定的作业规范版本')
    return
  }
  if (form.qtyFrom != null && form.qtyTo != null && form.qtyFrom > form.qtyTo) {
    ElMessage.warning('数量区间起点不得大于终点')
    return
  }
  submitting.value = true
  try {
    await workSpecUsageApi.register({
      workOrderId: Number(props.order.orderId),
      specVersionId: form.specVersionId,
      qtyFrom: form.qtyFrom,
      qtyTo: form.qtyTo,
      startTime: form.startTime,
      endTime: form.endTime,
      changeReason: form.changeReason || undefined,
      approvedBy: form.approvedBy || undefined,
    })
    ElMessage.success('换版登记成功')
    resetForm()
    await loadUsages()
    emit('success')
  } catch (error) {
    console.error('换版登记失败:', error)
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: WorkSpecUsageVO) {
  const confirmed = await ElMessageBox.confirm(
    `确认删除版本 ${row.versionNo || '#' + row.specVersionId} 的执行区间记录？`,
    '删除执行区间',
    { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
  ).catch(() => false)
  if (!confirmed) return
  try {
    await workSpecUsageApi.remove(row.id)
    ElMessage.success('已删除')
    await loadUsages()
    emit('success')
  } catch (error) {
    console.error('删除执行区间失败:', error)
  }
}
</script>

<style scoped>
.range-sep {
  margin: 0 8px;
  color: #909399;
}
.tip-warn {
  margin-left: 10px;
  color: #e6a23c;
  font-size: 12px;
}
.usage-title {
  margin: 6px 0 8px;
  font-weight: 600;
}
</style>
