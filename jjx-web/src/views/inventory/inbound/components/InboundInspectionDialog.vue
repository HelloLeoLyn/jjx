<template>
  <el-dialog v-model="opened" title="来料检验" width="1050px" destroy-on-close :close-on-click-modal="false">
    <div v-loading="loading">
      <el-descriptions v-if="inbound" :column="4" border>
        <el-descriptions-item label="单号">{{ inbound.inboundNo }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ inbound.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ inbound.inboundDate || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来料批量">{{ formatNumber(inbound.totalQuantity) }}</el-descriptions-item>
      </el-descriptions>
      <el-table :data="form.items" border class="inspection-table">
        <el-table-column label="物料编码" prop="materialCode" width="130" />
        <el-table-column label="物料名称" prop="materialName" min-width="150" />
        <el-table-column label="收货数量" prop="quantity" width="100" />
        <el-table-column label="抽检数量" width="140">
          <template #default="{ row }"><el-input-number v-model="row.sampledQuantity" :min="0" :max="row.quantity" controls-position="right" /></template>
        </el-table-column>
        <el-table-column label="合格数量" width="140">
          <template #default="{ row }"><el-input-number v-model="row.qualifiedQuantity" :min="0" :max="row.quantity" controls-position="right" /></template>
        </el-table-column>
        <el-table-column label="不良数量" width="140">
          <template #default="{ row }"><el-input-number v-model="row.rejectedQuantity" :min="0" :max="row.quantity" controls-position="right" /></template>
        </el-table-column>
        <el-table-column label="备注原因" min-width="160">
          <template #default="{ row }"><el-input v-model="row.rejectReason" maxlength="255" /></template>
        </el-table-column>
      </el-table>
      <el-form label-width="90px">
        <el-form-item label="检验判定">
          <el-radio-group v-model="form.inspectionResult">
            <el-radio :value="InspectionResultEnum.PASS.value">合格</el-radio>
            <el-radio :value="InspectionResultEnum.FAIL.value">不合格</el-radio>
            <el-radio :value="InspectionResultEnum.OTHER.value">其它</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.inspectionResult === InspectionResultEnum.FAIL.value" label="处置方式">
          <el-radio-group v-model="form.failDisposition">
            <el-radio value="退货">退货</el-radio>
            <el-radio value="来厂重工">来厂重工</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="检验备注"><el-input v-model="form.inspectionRemark" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
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
import { InspectionResultEnum } from '@/enums/inventory/InboundEnum'
import { formatNumber } from '@/utils/format'
import type { InboundVO } from '@/types/inventory/inbound'

const props = defineProps<{ visible: boolean; inboundId?: number }>()
const emit = defineEmits<{ (e: 'update:visible', value: boolean): void; (e: 'success'): void }>()
const opened = computed({ get: () => props.visible, set: value => emit('update:visible', value) })
const loading = ref(false)
const submitting = ref(false)
const inbound = ref<InboundVO | null>(null)
const form = reactive({ inspectionResult: InspectionResultEnum.PASS.value as string, failDisposition: '退货', inspectionRemark: '', items: [] as any[] })

watch(() => props.visible, async visible => {
  if (!visible || !props.inboundId) return
  loading.value = true
  try {
    const { data } = await inboundApi.getById(String(props.inboundId))
    inbound.value = data
    form.inspectionResult = InspectionResultEnum.PASS.value
    form.inspectionRemark = ''
    form.items = (data?.items || []).map(item => ({
      itemId: item.inboundItemId || item.itemId,
      materialCode: item.materialCode,
      materialName: item.materialName,
      quantity: Number(item.quantity || 0),
      sampledQuantity: Number(item.quantity || 0),
      qualifiedQuantity: Number(item.quantity || 0),
      rejectedQuantity: 0,
      rejectReason: '',
    }))
  } finally { loading.value = false }
})

async function submit() {
  for (const item of form.items) {
    if (Number(item.sampledQuantity) !== Number(item.qualifiedQuantity) + Number(item.rejectedQuantity)) {
      ElMessage.warning(`${item.materialCode}：抽检数量须等于合格与不良数量之和`)
      return
    }
  }
  submitting.value = true
  try {
    const { data } = await inboundApi.submitApprove(String(props.inboundId), {
      inspectionResult: form.inspectionResult,
      inspectionRemark: [form.inspectionResult === InspectionResultEnum.FAIL.value ? `处置方式：${form.failDisposition}` : '', form.inspectionRemark].filter(Boolean).join('；') || undefined,
      items: form.items.map(({ itemId, sampledQuantity, qualifiedQuantity, rejectedQuantity, rejectReason }) => ({ itemId, sampledQuantity, qualifiedQuantity, rejectedQuantity, rejectReason: rejectReason || undefined })),
    })
    if (data) {
      ElMessage.success(form.inspectionResult === InspectionResultEnum.FAIL.value ? '已记录拒收，不进入审批' : '检验已提交，等待品质主管复核')
      emit('success')
      opened.value = false
    }
  } finally { submitting.value = false }
}
</script>

<style scoped>.inspection-table { margin: 16px 0; }</style>
