<template>
  <el-dialog
    :model-value="modelValue"
    title="齐套检查 - 预览确认"
    width="860px"
    append-to-body
    @update:model-value="(value: boolean) => emit('update:modelValue', value)"
  >
    <div v-loading="loading" class="preview-body">
      <el-alert
        v-if="errorMsg"
        type="error"
        :closable="false"
        show-icon
        :title="errorMsg"
        class="section-gap"
      />

      <div class="order-info">
        <span><span class="info-label">订单号</span>{{ preview.orderNo || orderNo || '-' }}</span>
        <span><span class="info-label">检查时间</span>{{ checkTime || '-' }}</span>
      </div>

      <div class="summary-bar">
        缺料 {{ summary.shortageCount }} 种物料 · 在途已覆盖 {{ summary.coveredCount }} ·
        现货覆盖 {{ summary.stockCoveredCount }} 种产品 · 无BOM产品 {{ summary.noBomCount }} 个
      </div>

      <section class="preview-section">
        <h3>第一步 · 成品库存覆盖</h3>
        <el-table :data="preview.productRows" border empty-text="暂无产品明细">
          <el-table-column prop="productCode" label="产品编码" min-width="120" />
          <el-table-column prop="productName" label="产品名称" min-width="140" />
          <el-table-column label="订单数量" width="100" align="right">
            <template #default="{ row }">{{ fmtNum(row.orderQty) }}</template>
          </el-table-column>
          <el-table-column label="成品可用" width="100" align="right">
            <template #default="{ row }">{{ fmtNum(row.productAvailable) }}</template>
          </el-table-column>
          <el-table-column label="需生产(BOM展开)" width="150" align="right">
            <template #default="{ row }">{{ fmtNum(row.needProduce) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110" align="center">
            <template #default="{ row }">
              <el-tag :type="productStatus[row.status]?.type || 'info'" size="small">
                {{ productStatus[row.status]?.label || row.status }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="preview-section">
        <h3>第二步 · 物料齐套明细</h3>
        <el-table :data="preview.materialRows" border empty-text="暂无物料需求">
          <el-table-column prop="materialCode" label="物料编码" min-width="120" />
          <el-table-column prop="materialName" label="物料名称" min-width="140" />
          <el-table-column label="需求(含损耗)" width="120" align="right">
            <template #default="{ row }">{{ fmtNum(row.demand) }}</template>
          </el-table-column>
          <el-table-column label="可用" width="85" align="right">
            <template #default="{ row }">{{ fmtNum(row.available) }}</template>
          </el-table-column>
          <el-table-column label="在途" width="85" align="right">
            <template #default="{ row }">{{ fmtNum(row.inTransit) }}</template>
          </el-table-column>
          <el-table-column label="实际缺口" width="95" align="right">
            <template #default="{ row }">
              <span :class="{ shortage: row.status === 'shortage' }">{{ fmtNum(row.actualGap) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="materialStatus[row.status]?.type || 'info'" size="small">
                {{ materialStatus[row.status]?.label || row.status }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <el-alert
        v-if="!loading && !errorMsg && summary.shortageCount === 0"
        type="success"
        :closable="false"
        show-icon
        title="齐套通过：无缺料（含在途已覆盖），未生成缺料预警"
      />
    </div>

    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        v-if="summary.shortageCount > 0"
        type="primary"
        :loading="submitting"
        :disabled="!!errorMsg || loading"
        @click="handleConfirm"
      >
        确认生成缺料预警
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { alertApi } from '@/api/inventory/alert'

interface Summary {
  shortageCount: number
  coveredCount: number
  stockCoveredCount: number
  noBomCount: number
}

const props = defineProps<{
  modelValue: boolean
  orderId: number
  orderNo: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: [summary: Summary]
}>()

const emptySummary = (): Summary => ({
  shortageCount: 0,
  coveredCount: 0,
  stockCoveredCount: 0,
  noBomCount: 0,
})

const loading = ref(false)
const submitting = ref(false)
const errorMsg = ref('')
const checkTime = ref('')
const preview = ref<any>({ orderNo: '', productRows: [], materialRows: [], summary: emptySummary() })
const summary = computed<Summary>(() => preview.value.summary || emptySummary())

const productStatus: Record<string, { label: string; type: 'success' | 'warning' | 'info' }> = {
  'stock-covered': { label: '现货覆盖', type: 'success' },
  'to-produce': { label: '需生产', type: 'warning' },
  'no-bom': { label: '无BOM跳过', type: 'info' },
}
const materialStatus: Record<string, { label: string; type: 'success' | 'info' | 'danger' }> = {
  ok: { label: '充足', type: 'success' },
  covered: { label: '在途覆盖', type: 'info' },
  shortage: { label: '缺料', type: 'danger' },
}

function fmtNum(value: any): string {
  if (value === null || value === undefined || value === '') return '-'
  const number = Number(value)
  return Number.isInteger(number) ? String(number) : number.toFixed(2)
}

async function loadPreview() {
  loading.value = true
  errorMsg.value = ''
  checkTime.value = ''
  preview.value = { orderNo: props.orderNo, productRows: [], materialRows: [], summary: emptySummary() }
  try {
    const response: any = await alertApi.orderShortagePreview(props.orderId)
    preview.value = response?.data || preview.value
    checkTime.value = new Date().toLocaleString()
  } catch (error: any) {
    errorMsg.value = error?.message || '齐套检查预览加载失败'
  } finally {
    loading.value = false
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (visible) loadPreview()
  },
  { immediate: true },
)

async function handleConfirm() {
  submitting.value = true
  try {
    await alertApi.checkOrderShortage(props.orderId)
    const confirmedSummary = { ...summary.value }
    emit('update:modelValue', false)
    emit('success', confirmedSummary)
  } catch (error: any) {
    ElMessage.error(error?.message || '生成缺料预警失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.preview-body {
  max-height: 70vh;
  overflow-y: auto;
  padding: 4px;
}
.section-gap {
  margin-bottom: 12px;
}
.order-info {
  display: flex;
  gap: 32px;
  padding: 12px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}
.info-label {
  margin-right: 8px;
  color: #909399;
}
.summary-bar {
  margin: 12px 0;
  padding: 10px 14px;
  color: #606266;
  background: #f5f7fa;
  border-radius: 4px;
}
.preview-section {
  margin-bottom: 18px;
}
.preview-section h3 {
  margin: 0 0 10px;
  font-size: 15px;
  color: #303133;
}
.shortage {
  color: #f56c6c;
  font-weight: 600;
}
</style>
