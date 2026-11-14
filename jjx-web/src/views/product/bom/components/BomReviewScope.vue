<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="1200px"
    :before-close="handleClose"
    class="bom-review-scope"
  >
    <div v-loading="loading">
      <el-alert v-if="error" type="error" :closable="false" show-icon :title="error" />
      <el-empty v-else-if="!loading && !plans.length" description="没有可展示的整套范围" />
      <div v-for="plan in plans" :key="plan.rootBomId" class="scope-plan">
        <div class="scope-head">
          <div class="scope-title">
            <strong>{{ plan.productCode || '未命名' }}</strong>
            <span v-if="plan.productName"> · {{ plan.productName }}</span>
            <span class="scope-version">
              {{ plan.rootBomCode || '根方案' }} {{ plan.rootBomVersion || '' }}
            </span>
            <el-tag v-if="plan.rootStatus != null" :type="statusTagType(plan.rootStatus)" size="small">
              {{ statusLabel(plan.rootStatus) }}
            </el-tag>
          </div>
          <div class="scope-meta">
            <el-tag v-if="plan.executable" type="success" size="small">可执行</el-tag>
            <el-tag v-else type="danger" size="small">不可执行</el-tag>
            <span v-if="action === 'APPROVE'">
              本次通过 {{ plan.actionCount }} 个方案，复用已批准 {{ plan.reuseCount }} 个
            </span>
            <span v-else-if="action === 'SUBMIT'">
              本次送审 {{ plan.actionCount }} 个方案，已批准复用 {{ plan.reuseCount }} 个，审核中复用
              {{ plan.pendingReuseCount }} 个
            </span>
            <span v-else>只退回该产品根方案，不连带公共下层方案</span>
          </div>
        </div>

        <el-alert
          v-if="plan.blockingIssues?.length"
          type="warning"
          :closable="false"
          show-icon
          :title="'暂不能执行：' + plan.blockingIssues.length + ' 项待处理'"
          class="scope-blocking"
        >
          <div v-for="(issue, index) in plan.blockingIssues.slice(0, 8)" :key="index" class="blocking-line">
            · {{ issueLocation(plan, issue) }} {{ issue.message }}
          </div>
          <div v-if="plan.blockingIssues.length > 8" class="blocking-line">
            … 共 {{ plan.blockingIssues.length }} 项
          </div>
        </el-alert>
        <div v-else-if="plan.reason" class="scope-reason">{{ plan.reason }}</div>

        <el-table :data="plan.nodes" size="small" border>
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="node-items">
                <el-table :data="row.items || []" size="small" border>
                  <el-table-column prop="materialCode" label="物料编码" min-width="140" />
                  <el-table-column prop="materialName" label="材料名称" min-width="160" />
                  <el-table-column prop="specification" label="规格" min-width="140" />
                  <el-table-column label="用量" width="110">
                    <template #default="{ row: item }">
                      {{ item.quantity ?? '—' }} {{ item.unit || '' }}
                    </template>
                  </el-table-column>
                  <el-table-column label="行状态" width="110">
                    <template #default="{ row: item }">
                      <el-tag size="small" :type="itemStatusTag(item.reviewStatus)">
                        {{ itemStatusLabel(item.reviewStatus) }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="下层方案" width="100">
                    <template #default="{ row: item }">{{ item.subBomId ? '有' : '—' }}</template>
                  </el-table-column>
                </el-table>
                <div v-if="row.issues?.length" class="node-issues">
                  <div v-for="(issue, index) in row.issues.slice(0, 5)" :key="index">
                    · {{ issue.materialCode || issue.materialName || (issue.itemId ? '行#' + issue.itemId : '主表') }}
                    {{ issue.message }}
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="bomCode" label="方案编码" min-width="170" />
          <el-table-column prop="bomName" label="方案名称" min-width="180" />
          <el-table-column prop="bomVersion" label="版本" width="90" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag v-if="row.status != null" size="small" :type="statusTagType(row.status)">
                {{ statusLabel(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="本次动作" width="120">
            <template #default="{ row }">
              <el-tag size="small" :type="actionTagType(row.plannedAction)">
                {{ plannedActionLabel(row.plannedAction) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="明细" width="130">
            <template #default="{ row }">
              {{ row.itemCount || 0 }} 行（待审 {{ row.pendingItemCount || 0 }}）
            </template>
          </el-table-column>
          <el-table-column label="问题" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.issues?.length" type="danger" size="small">
                {{ row.issues.length }}
              </el-tag>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="110">
            <template #default="{ row }">
              <el-button link type="primary" @click="emit('open-bom', row.bomId)">打开处理</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </div>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose">{{ readonly ? '关闭' : '取消' }}</el-button>
        <el-button
          v-if="!readonly"
          type="primary"
          :loading="confirming"
          :disabled="!executableScopes.length"
          @click="handleConfirm"
        >
          确认{{ actionLabel }}
        </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script lang="ts">
import { BomStatusEnum, BomItemReviewStatusEnum } from '@/enums/product'
import type {
  BomReviewPackage,
  BomReviewPackageIssue,
  BomReviewProductScope,
} from '@/types/product/bom'

export type ReviewAction = 'SUBMIT' | 'APPROVE' | 'REJECT'

/**
 * 把预览结果转成执行请求：只提交可执行产品；范围和指纹全部来自本次预览，
 * 不在前端重新选择版本或方案。
 */
export function buildReviewPackageScopes(
  plans: BomReviewPackage[],
  action: ReviewAction
): BomReviewProductScope[] {
  return (plans || [])
    .filter((plan) => plan && plan.executable)
    .map((plan) => {
      const nodes =
        action === 'REJECT'
          ? (plan.nodes || []).filter((node) => node.bomId === plan.rootBomId)
          : plan.nodes || []
      return {
        rootBomId: plan.rootBomId,
        expectedVersion: plan.rootBomVersion || '',
        expectedNodes: nodes.map((node) => ({
          bomId: node.bomId,
          version: node.bomVersion || '',
          status: Number(node.status),
          packageRevision: node.packageRevision || '',
        })),
      }
    })
    .filter(
      (scope) =>
        scope.expectedVersion &&
        scope.expectedNodes.length &&
        scope.expectedNodes.every((node) => node.packageRevision && node.version)
    )
}

/** 可执行 / 不可执行产品数，供范围确认页脚与测试核对。 */
export function summarizeReviewPackages(plans: BomReviewPackage[]) {
  const list = plans || []
  return {
    executable: list.filter((plan) => plan.executable).length,
    blocked: list.filter((plan) => !plan.executable).length,
  }
}

export function reviewActionLabel(action: ReviewAction): string {
  return action === 'SUBMIT' ? '整套送审' : action === 'APPROVE' ? '整套通过' : '整套驳回'
}
</script>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { productBomApi } from '@/api/product/bom'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  action: { type: String as () => ReviewAction, default: 'SUBMIT' },
  rootBomIds: { type: Array as () => number[], default: () => [] },
  readonly: { type: Boolean, default: false },
})
const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'confirm', scopes: BomReviewProductScope[], plans: BomReviewPackage[]): void
  (event: 'open-bom', bomId: number): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})
const loading = ref(false)
const confirming = ref(false)
const error = ref('')
const plans = ref<BomReviewPackage[]>([])
const dialogTitle = computed(() => reviewActionLabel(props.action) + '范围确认')
const actionLabel = computed(() => reviewActionLabel(props.action))
const executableScopes = computed(() => buildReviewPackageScopes(plans.value, props.action))

const loadPlan = async () => {
  if (!props.modelValue) return
  const ids = (props.rootBomIds || []).filter((id) => Number.isFinite(id) && id > 0)
  if (!ids.length) {
    plans.value = []
    error.value = '请先选择至少一个产品BOM'
    return
  }
  loading.value = true
  error.value = ''
  plans.value = []
  try {
    const response = await productBomApi.planReviewPackages(props.action, ids)
    plans.value = response.data || []
  } catch (e: any) {
    error.value = e?.message || '整套范围加载失败，请刷新后重试'
  } finally {
    loading.value = false
  }
}

watch(
  () => [props.modelValue, props.action, props.rootBomIds] as const,
  () => {
    if (props.modelValue) void loadPlan()
  },
  { immediate: true }
)

const statusLabel = (value: number) => BomStatusEnum.getLabel(value)
const statusTagType = (value: number) => {
  const item = BomStatusEnum.items.find((entry) => entry.value === value)
  return (item as any)?.tagType || 'info'
}
const itemStatusLabel = (value?: number) =>
  value == null ? '—' : BomItemReviewStatusEnum.getLabel(value)
const itemStatusTag = (value?: number) => {
  const item = BomItemReviewStatusEnum.items.find((entry) => entry.value === value)
  return (item as any)?.tagType || 'info'
}
const plannedActionLabel = (value?: string) =>
  ({
    SUBMIT: '本次送审',
    APPROVE: '本次通过',
    REJECT: '退回根方案',
    REUSE: '已批准复用',
    REUSE_PENDING: '审核中复用',
    BLOCKED: '阻断',
  })[value || ''] || '—'
const actionTagType = (value?: string) =>
  value === 'REUSE' || value === 'REUSE_PENDING'
    ? 'success'
    : value === 'BLOCKED'
      ? 'danger'
      : value
        ? 'warning'
        : 'info'
const issueLocation = (plan: BomReviewPackage, issue: BomReviewPackageIssue) => {
  const node = (plan.nodes || []).find((item) => item.bomId === issue.bomId)
  const base =
    issue.bomId === plan.rootBomId ? plan.rootBomCode || '根方案' : node?.bomCode || '方案'
  return issue.itemId ? base + ' 行#' + issue.itemId : base
}

const handleConfirm = () => {
  if (props.readonly) return
  const scopes = executableScopes.value
  if (!scopes.length) return
  confirming.value = true
  emit('confirm', scopes, plans.value)
  confirming.value = false
}
const handleClose = () => {
  visible.value = false
}
</script>

<style scoped>
.scope-plan { margin-bottom: 20px; }
.scope-head { display: flex; justify-content: space-between; gap: 12px; flex-wrap: wrap; margin-bottom: 8px; }
.scope-title { font-size: 14px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.scope-version { color: #606266; }
.scope-meta { color: #606266; font-size: 12px; display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.scope-blocking { margin-bottom: 8px; }
.blocking-line { font-size: 12px; line-height: 20px; }
.scope-reason { color: #909399; font-size: 12px; margin-bottom: 8px; }
.node-items { padding: 8px 16px; }
.node-issues { color: #f56c6c; font-size: 12px; margin-top: 6px; }
</style>
