<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="1000px"
    :before-close="handleClose"
    class="bom-batch-review-dialog"
  >
    <div v-loading="loading">
      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" />
      <template v-else-if="result">
        <el-alert
          v-if="result.failures.length"
          type="warning"
          :closable="false"
          show-icon
          :title="'成功 ' + result.successes.length + ' 个产品，失败 ' + result.failures.length + ' 个产品'"
        />
        <el-alert
          v-else
          type="success"
          :closable="false"
          show-icon
          :title="'全部成功：' + result.successes.length + ' 个产品'"
        />

        <div v-if="result.successes.length" class="result-section">
          <h4>成功清单</h4>
          <el-table :data="result.successes" size="small" border>
            <el-table-column label="产品" min-width="220">
              <template #default="{ row }">
                {{ row.productCode || '—' }} {{ row.productName || '' }}
              </template>
            </el-table-column>
            <el-table-column prop="rootBomVersion" label="产品BOM版本" width="120" />
            <el-table-column label="方案处理" min-width="360">
              <template #default="{ row }">
                <div v-for="node in row.nodes" :key="node.bomId" class="node-line">
                  <el-tag size="small" :type="nodeTagType(node.action)">{{ nodeActionLabel(node.action) }}</el-tag>
                  {{ node.bomCode || node.bomId }} {{ node.bomVersion || '' }}
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="message" label="说明" min-width="180" />
          </el-table>
        </div>

        <div v-if="result.failures.length" class="result-section">
          <h4>失败清单（可只重试失败产品）</h4>
          <el-table :data="result.failures" size="small" border>
            <el-table-column label="产品" min-width="220">
              <template #default="{ row }">
                {{ row.productCode || '—' }} {{ row.productName || '' }}
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="失败原因" min-width="420" />
          </el-table>
        </div>

        <div v-if="result.sharedNodes?.length" class="result-section">
          <h4>共享方案处理</h4>
          <el-table :data="result.sharedNodes" size="small" border>
            <el-table-column label="方案" min-width="220">
              <template #default="{ row }">{{ row.bomCode || row.bomId }} {{ row.bomVersion || '' }}</template>
            </el-table-column>
            <el-table-column label="本次动作" width="120">
              <template #default="{ row }">{{ nodeActionLabel(row.action) }}</template>
            </el-table-column>
            <el-table-column label="涉及产品" min-width="260">
              <template #default="{ row }">{{ row.products.join('、') }}</template>
            </el-table-column>
            <el-table-column prop="message" label="说明" min-width="220" />
          </el-table>
        </div>
      </template>
    </div>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">关闭</el-button>
        <el-button
          v-if="failedRootIds.length"
          type="primary"
          :disabled="loading"
          @click="retryFailed"
        >
          重试失败产品
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script lang="ts">
import type { BomReviewBatchResult, BomReviewProductScope } from '@/types/product/bom'
import type { ReviewAction } from './BomReviewScope.vue'

/** 失败产品根 ID 去重，供“只重试失败产品”重新预览。 */
export function extractFailedRootIds(result: BomReviewBatchResult | null | undefined): number[] {
  const ids = (result?.failures || [])
    .map((item) => Number(item.rootBomId))
    .filter((id) => Number.isFinite(id) && id > 0)
  return Array.from(new Set(ids))
}
</script>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { productBomApi } from '@/api/product/bom'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  action: { type: String as () => ReviewAction, default: 'SUBMIT' },
  scopes: { type: Array as () => BomReviewProductScope[], default: () => [] },
})
const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'done', result: BomReviewBatchResult): void
  (event: 'retry-failed', rootBomIds: number[]): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
const loading = ref(false)
const error = ref('')
const result = ref<BomReviewBatchResult | null>(null)
const dialogTitle = computed(() => {
  const label = props.action === 'SUBMIT' ? '整套送审' : props.action === 'APPROVE' ? '整套通过' : '整套驳回'
  return label + '结果'
})
const failedRootIds = computed(() => extractFailedRootIds(result.value))

const execute = async () => {
  if (!props.modelValue) return
  if (!props.scopes.length) {
    error.value = '没有可执行的整套范围'
    return
  }
  loading.value = true
  error.value = ''
  result.value = null
  try {
    const payload = { action: props.action, products: props.scopes }
    const response =
      props.action === 'SUBMIT'
        ? await productBomApi.submitReviewPackages(payload)
        : props.action === 'APPROVE'
          ? await productBomApi.approveReviewPackages(payload)
          : await productBomApi.rejectReviewPackages(payload)
    result.value = response.data
    if (result.value) emit('done', result.value)
  } catch (e: any) {
    error.value = e?.message || '整套操作失败，请刷新后重试'
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.action, props.scopes] as const,
  () => {
    if (props.modelValue) void execute()
  },
  { immediate: true }
)

const nodeActionLabel = (action: string) =>
  ({
    SUBMIT: '送审',
    APPROVE: '通过',
    REJECT: '驳回',
    REUSE: '已批准复用',
    REUSE_PENDING: '审核中复用',
  })[action] || action
const nodeTagType = (action: string) =>
  action === 'REUSE' || action === 'REUSE_PENDING'
    ? 'success'
    : action === 'REJECT'
      ? 'danger'
      : 'warning'
const retryFailed = () => {
  const ids = failedRootIds.value
  if (!ids.length) return
  emit('retry-failed', ids)
}
const handleClose = () => {
  visible.value = false
}
</script>

<style scoped>
.result-section { margin-top: 16px; }
.result-section h4 { margin: 0 0 8px; font-size: 14px; }
.node-line { line-height: 22px; }
</style>
