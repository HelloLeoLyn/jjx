<template>
  <el-dialog
    v-model="visible"
    :title="dialogTitle"
    width="1280px"
    append-to-body
    :close-on-click-modal="false"
    :before-close="handleClose"
  >
    <div v-loading="loading">
      <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon />
      <el-empty v-if="!loading && !plans.length && !error" description="没有可展示的BOM范围" />
      <div v-for="plan in plans" :key="plan.rootBomId" class="scope-plan">
        <div class="scope-head">
          <strong
            >{{ plan.productCode || plan.rootBomCode }}
            {{
              plan.productName && plan.productName !== plan.productCode ? plan.productName : ''
            }}
            · {{ plan.rootBomVersion }}</strong
          >
          <span v-if="decision === 'SUBMIT'"
            >本次提交 {{ plan.actionCount }} 份，已批准复用 {{ plan.reuseCount }} 份，审核中复用
            {{ plan.pendingReuseCount }} 份</span
          >
          <span v-else-if="decision === 'APPROVE'"
            >本次审核 {{ plan.actionCount }} 份，已批准复用 {{ plan.reuseCount }} 份</span
          >
          <span v-else>退回本版本；下层方案保留原状态</span>
        </div>
        <el-alert
          v-if="decision === 'REJECT'"
          title="填写原因后退回本版本，不连带退回共享下层方案。"
          type="warning"
          :closable="false"
        />
        <el-alert
          v-else-if="plan.blockingIssues?.length || plan.reason"
          :title="
            plan.blockingIssues?.length
              ? '有 ' + plan.blockingIssues.length + ' 项待处理，请查看对应位置'
              : plan.reason
          "
          type="warning"
          :closable="false"
        >
          <div v-for="(issue, index) in plan.blockingIssues" :key="index" class="blocking-line">
            {{ issueLocation(plan, issue) }}：{{ issue.message }}
          </div>
        </el-alert>
        <el-alert
          v-if="reviewPackageVersionIssues(plan).length"
          title="BOM版本信息不完整，暂时不能确认整套操作"
          type="error"
          :closable="false"
          show-icon
        >
          <div v-for="message in reviewPackageVersionIssues(plan)" :key="message">{{ message }}</div>
        </el-alert>
        <el-table
          :data="buildBomReviewTree(plan)"
          row-key="key"
          :tree-props="{ children: 'children' }"
          default-expand-all
          size="small"
          border
        >
          <el-table-column label="产品 / 材料 / 生产BOM" min-width="320">
            <template #default="{ row }">
              <strong v-if="row.kind === 'scheme'">{{
                row.bomId === plan.rootBomId ? row.label : '生产BOM'
              }}</strong>
              <span v-else>{{ row.label }}</span>
              <span v-if="row.node" class="scheme-code"
                >{{ row.node.bomCode }} · {{ row.node.bomVersion }}</span
              >
              <div v-for="message in row.issues" :key="message" class="node-issue">
                {{ message }}
              </div>
            </template>
          </el-table-column>
          <el-table-column label="本层用量" width="140">
            <template #default="{ row }">{{
              row.item ? (row.item.quantity ?? '—') + ' ' + (row.item.unit || '') : '—'
            }}</template>
          </el-table-column>
          <el-table-column label="规格" min-width="140">
            <template #default="{ row }">{{ row.item?.specification || '—' }}</template>
          </el-table-column>
          <el-table-column label="状态" width="110">
            <template #default="{ row }">
              <el-tag
                v-if="row.node?.status != null"
                size="small"
                :type="BomStatusEnum.getTagProps(row.node.status)?.type"
                >{{ BomStatusEnum.getLabel(row.node.status) }}</el-tag
              >
              <span v-else-if="row.item?.reviewStatus != null">{{
                BomItemReviewStatusEnum.getLabel(row.item.reviewStatus)
              }}</span>
              <span v-else>—</span>
            </template>
          </el-table-column>
          <el-table-column label="本次处理" width="130">
            <template #default="{ row }">{{
              row.node ? nodeActionLabel(row.node, plan.rootBomId) : '—'
            }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button
                v-if="row.node"
                link
                type="primary"
                :disabled="executing"
                @click="emit('open-bom', row.bomId, false)"
                >查看明细</el-button
              >
              <el-button
                v-if="row.issues.length && canProcessRow(plan, row.bomId)"
                link
                type="primary"
                :disabled="executing"
                @click="emit('open-bom', row.bomId, true)"
                >打开处理</el-button
              >
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-collapse v-if="references.length" class="scope-plan">
        <el-collapse-item
          :title="'引用此生产BOM的上层产品 / 方案（' + references.length + '）'"
          name="references"
        >
          <div v-for="item in references" :key="item.bomId">
            {{ item.outputCode || item.bomCode }} {{ item.outputName }} · {{ item.bomVersion }} ·
            {{ item.status == null ? '—' : BomStatusEnum.getLabel(item.status) }}
          </div>
        </el-collapse-item>
      </el-collapse>
      <el-form
        v-if="action === 'APPROVE' && !readonly"
        label-width="90px"
        class="review-form"
        :disabled="executing"
      >
        <el-form-item label="审核结果">
          <el-radio-group v-model="decision">
            <el-radio-button v-if="hasPermi('engineering:bom:approve')" value="APPROVE"
              >整套通过</el-radio-button
            >
            <el-radio-button v-if="hasPermi('engineering:bom:reject')" value="REJECT"
              >退回修改</el-radio-button
            >
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审核意见" :required="decision === 'REJECT'">
          <el-input
            v-model="remark"
            type="textarea"
            :rows="2"
            maxlength="500"
            show-word-limit
            :placeholder="decision === 'REJECT' ? '请填写退回原因' : '审核意见（选填）'"
          />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button :disabled="executing" @click="handleClose">返回版本维护</el-button>
      <el-button :disabled="loading || executing" @click="loadPlan">重新检查</el-button>
      <el-button
        v-if="!readonly"
        type="primary"
        :loading="executing"
        :disabled="!canExecute"
        @click="execute"
        >{{ confirmLabel }}</el-button
      >
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
/** 兼容旧后端预览：范围不可提交时直接说明缺版本的位置。 */
export function reviewPackageVersionIssues(plan: BomReviewPackage): string[] {
  const issues = new Map<number, string>()
  if (!plan.rootBomVersion?.trim()) issues.set(plan.rootBomId, `${plan.rootBomCode || plan.rootBomId}：BOM版本号为空，请补齐版本号并保存后重新检查`)
  for (const node of plan.nodes || []) {
    if (!node.bomVersion?.trim()) issues.set(node.bomId, `${node.bomCode || node.bomId}：BOM版本号为空，请补齐版本号并保存后重新检查`)
  }
  return [...issues.values()]
}

export function buildReviewPackageScopes(
  plans: BomReviewPackage[],
  action: ReviewAction
): BomReviewProductScope[] {
  return (plans || [])
    .filter((plan) => plan && plan.executable && !reviewPackageVersionIssues(plan).length)
    .map((plan) => {
      const nodes =
        action === 'REJECT'
          ? (plan.nodes || []).filter((node) => node.bomId === plan.rootBomId)
          : plan.nodes || []
      return {
        rootBomId: plan.rootBomId,
        expectedVersion: plan.rootBomVersion?.trim() || '',
        expectedNodes: nodes.map((node) => ({
          bomId: node.bomId,
          version: node.bomVersion?.trim() || '',
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
import { ElMessage } from 'element-plus'
import { productBomApi } from '@/api/product/bom'
import { hasPermi } from '@/directives'
import { ManufacturingOutputTypeEnum } from '@/enums/product'
import { buildBomReviewTree } from './bomReviewTree'
import type {
  BomReviewBatchResult,
  BomReviewPackageNode,
  BomReferenceImpact,
} from '@/types/product/bom'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    action?: ReviewAction
    rootBomIds: number[]
    readonly?: boolean
  }>(),
  { action: 'SUBMIT', readonly: false }
)
const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
  (event: 'done', result: BomReviewBatchResult): void
  (event: 'close'): void
  (event: 'open-bom', bomId: number, process: boolean): void
}>()
const visible = computed({
  get: () => props.modelValue,
  set: (value) => {
    if (!executing.value) emit('update:modelValue', value)
  },
})
const loading = ref(false)
const executing = ref(false)
const needsRefresh = ref(false)
const error = ref('')
const plans = ref<BomReviewPackage[]>([])
const rejectPlans = ref<BomReviewPackage[]>([])
const references = ref<BomReferenceImpact[]>([])
const decision = ref<ReviewAction>('SUBMIT')
const remark = ref('')
let loadRequest = 0
const dialogTitle = computed(() =>
  props.action === 'SUBMIT' ? '提交审核 · 整套BOM' : '审核 · 整套BOM'
)
const confirmLabel = computed(() =>
  decision.value === 'SUBMIT'
    ? '确认整套提交'
    : decision.value === 'APPROVE'
      ? '整套审核通过'
      : '确认退回修改'
)
const scopes = computed(() =>
  buildReviewPackageScopes(
    decision.value === 'REJECT' ? rejectPlans.value : plans.value,
    decision.value
  )
)
const permitted = computed(() =>
  hasPermi(
    decision.value === 'SUBMIT'
      ? 'engineering:bom:edit'
      : decision.value === 'APPROVE'
        ? 'engineering:bom:approve'
        : 'engineering:bom:reject'
  )
)
const canExecute = computed(
  () =>
    !props.readonly &&
    !loading.value &&
    !executing.value &&
    !needsRefresh.value &&
    permitted.value &&
    scopes.value.length === props.rootBomIds.length &&
    scopes.value.length > 0 &&
    (decision.value !== 'REJECT' || !!remark.value.trim())
)
const loadPlan = async () => {
  if (!props.modelValue || executing.value) return
  const token = ++loadRequest
  loading.value = true
  needsRefresh.value = true
  error.value = ''
  plans.value = []
  rejectPlans.value = []
  references.value = []
  try {
    const [response, rejection] = await Promise.all([
      productBomApi.planReviewPackages(props.action, props.rootBomIds),
      props.action === 'APPROVE'
        ? productBomApi.planReviewPackages('REJECT', props.rootBomIds)
        : Promise.resolve(null),
    ])
    if (token !== loadRequest) return
    plans.value = response.data || []
    rejectPlans.value = rejection?.data || []
    const materialRoot = plans.value.find(
      (plan) => plan.outputType === ManufacturingOutputTypeEnum.MATERIAL
    )
    if (materialRoot && props.action === 'APPROVE') {
      const impact = await productBomApi.getBomReferenceImpact(materialRoot.rootBomId)
      if (token !== loadRequest) return
      references.value = impact.data || []
    }
    needsRefresh.value = false
  } catch (e: any) {
    if (token === loadRequest) error.value = e?.message || '整套范围加载失败，请重新检查'
  } finally {
    if (token === loadRequest) loading.value = false
  }
}
const execute = async () => {
  if (!canExecute.value) return
  const payload = { action: decision.value, products: scopes.value, remark: remark.value.trim() }
  executing.value = true
  error.value = ''
  try {
    const response =
      decision.value === 'SUBMIT'
        ? await productBomApi.submitReviewPackages(payload)
        : decision.value === 'APPROVE'
          ? await productBomApi.approveReviewPackages(payload)
          : await productBomApi.rejectReviewPackages(payload)
    const result = response.data
    if (
      !result ||
      result.failures?.length ||
      result.successes?.length !== payload.products.length
    ) {
      needsRefresh.value = true
      error.value =
        result?.failures?.map((item) => item.reason).join('；') || '未能完成整套操作，请重新检查'
      return
    }
    ElMessage.success(
      decision.value === 'SUBMIT'
        ? '整套BOM已提交审核'
        : decision.value === 'APPROVE'
          ? '整套BOM审核通过'
          : '已退回修改'
    )
    emit('done', result)
    executing.value = false
    handleClose()
  } catch (e: any) {
    needsRefresh.value = true
    error.value = e?.message || '整套操作失败，请重新检查后重试'
  } finally {
    executing.value = false
  }
}
const nodeActionLabel = (node: BomReviewPackageNode, rootId: number) => {
  if (decision.value === 'REJECT') return node.bomId === rootId ? '本次退回' : '保留原状态'
  return node.plannedAction
    ? {
        SUBMIT: '本次提交',
        APPROVE: '本次审核',
        REJECT: '本次退回',
        REUSE: '已批准复用',
        REUSE_PENDING: '审核中复用',
        BLOCKED: '待处理',
      }[node.plannedAction]
    : '—'
}
const issueLocation = (plan: BomReviewPackage, issue: BomReviewPackageIssue) => {
  const node = plan.nodes.find((item) => item.bomId === issue.bomId)
  const item = node?.items?.find((item) => item.itemId === issue.itemId)
  return [node?.outputName || node?.bomName || plan.rootBomCode, item?.materialName]
    .filter(Boolean)
    .join(' › ')
}
const canProcessRow = (plan: BomReviewPackage, bomId: number) => {
  if (props.readonly) return false
  const status = plan.nodes.find((node) => node.bomId === bomId)?.status
  return status === BomStatusEnum.REVIEWING.value
    ? hasPermi(['engineering:bom:approve', 'engineering:bom:reject'])
    : (status === BomStatusEnum.DRAFT.value || status === BomStatusEnum.REJECTED.value) &&
        hasPermi('engineering:bom:edit')
}
const handleClose = () => {
  if (executing.value) return
  ++loadRequest
  emit('update:modelValue', false)
  emit('close')
}
watch(
  () => [props.modelValue, props.action, props.rootBomIds] as const,
  () => {
    ++loadRequest
    if (props.modelValue) {
      remark.value = ''
      decision.value =
        props.action === 'APPROVE' && !hasPermi('engineering:bom:approve') ? 'REJECT' : props.action
      void loadPlan()
    }
  },
  { immediate: true }
)
defineExpose({ loadPlan })
</script>

<style scoped>
.scope-plan {
  margin-bottom: 16px;
}
.scope-head {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.scheme-code {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.scope-plan > .el-alert {
  margin-bottom: 12px;
}
.node-issue {
  color: var(--el-color-danger);
  white-space: pre-wrap;
}
.blocking-line {
  line-height: 22px;
}
.review-form {
  margin-top: 16px;
}
</style>
