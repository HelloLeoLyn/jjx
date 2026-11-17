<template>
  <div class="tree-editor">
    <div class="toolbar">
      <strong>加工树</strong
      ><el-button v-if="!tree" link type="primary" :disabled="disabled || busy" @click="open"
        >添加加工要求</el-button
      ><el-button v-if="modelValue" link type="danger" :disabled="disabled || busy" @click="remove"
        >移除加工要求</el-button
      >
      <el-button v-if="tree && !modelValue" link :disabled="busy" @click="cancelAdding"
        >取消添加</el-button
      >
    </div>
    <el-radio-group v-if="tree" v-model="deliveryOnly" aria-label="加工树显示模式">
      <el-radio-button :value="false">完整加工树</el-radio-button>
      <el-radio-button :value="true">仅交付物</el-radio-button>
    </el-radio-group>
    <div class="tree-scroll" v-loading="busy">
      <PurchaseProcessingTreeRows
        v-if="tree"
        :tree="tree"
        collapsible
        :expand-node-id="expandNodeId"
        :expand-revision="expandRevision"
        :delivery-only="deliveryOnly"
        :material-name="item.supplierMaterialName || item.materialName"
      >
        <template #actions="{ node, sourceLabel, slitSources }">
          <el-button
            v-if="!deliveryOnly"
            link
            type="primary"
            :disabled="
              busy || disabled || node.delivery === PurchaseProcessingDeliveryEnum.SCRAP.value
            "
            @click="
              edit(node, PurchaseProcessingOperationEnum.SLIT.value, sourceLabel, slitSources)
            "
            >{{
              node.operation?.type === PurchaseProcessingOperationEnum.SLIT.value
                ? '修改分切'
                : node.operation
                  ? '改为分切'
                  : '分切'
            }}</el-button
          >
          <el-button
            v-if="!deliveryOnly"
            link
            type="primary"
            :disabled="
              busy || disabled || node.delivery === PurchaseProcessingDeliveryEnum.SCRAP.value
            "
            @click="edit(node, PurchaseProcessingOperationEnum.CUT.value, sourceLabel)"
            >{{
              node.operation?.type === PurchaseProcessingOperationEnum.CUT.value
                ? '修改切片'
                : node.operation
                  ? '改为切片'
                  : '切片'
            }}</el-button
          >
          <el-button
            v-if="node.operation"
            link
            type="danger"
            :disabled="busy || disabled"
            @click="clear(node)"
            >清除下级加工</el-button
          >
          <el-button v-else link :disabled="busy || disabled" @click="bind(node, sourceLabel)">{{
            node.delivery === PurchaseProcessingDeliveryEnum.SCRAP.value
              ? '修改报废处理'
              : '指定交货物料'
          }}</el-button>
        </template>
      </PurchaseProcessingTreeRows>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" class="notice" />
    <p class="hint">
      只对最终节点交货，中间料不重复入库。原卷自动关联原物料；切片和余料可先保存草稿，提交审核前须指定对应规格物料。当前几何按不扣切缝、修边损耗计算；切片方向和每卷/块片数可明确填写。
    </p>
    <PurchaseProcessingOperationDialog
      v-model="operationVisible"
      v-model:operation="operation"
      :active="active"
      :active-label="activeLabel"
      :slit-steps="slitSteps"
      :busy="busy || !!disabled"
      :error="error"
      :cut-preview-busy="cutPreviewBusy"
      :cut-preview-error="cutPreviewError"
      :cut-preview-piece="cutPreviewPiece"
      :cut-preview-remainders="cutPreviewRemainders"
      @select-slit-step="selectSlitStep"
      @change-cut-axis="changeCutAxis"
      @clear="clearActive"
      @apply="applyOperation"
    />
    <PurchaseProcessingBindingDialog
      v-model="bindingVisible"
      v-model:binding-id="bindingId"
      v-model:binding-scrap="bindingScrap"
      v-model:disposal-reason="disposalReason"
      v-model:material-select-open="materialSelectOpen"
      :active="active"
      :active-label="activeLabel"
      :busy="busy"
      :disabled="disabled"
      :searching="searching"
      :material-options="materialOptions"
      :can-create-from-search="canCreateFromSearch"
      :error="error"
      @search="search"
      @create-material="createMaterial"
      @change-delivery="changeDelivery"
      @apply="applyBinding"
    />
    <MaterialFormDialog
      v-if="materialFormVisible"
      v-model="materialFormVisible"
      :preset-data="materialPreset"
      @success="materialCreated"
    />
  </div>
</template>
<script setup lang="ts">
import { computed, ref, onBeforeUnmount, watch } from 'vue'
import { ElMessageBox } from 'element-plus'
import { materialApi } from '@/api/inventory/material'
import { previewProcessingPlan, readProcessingPlan } from '@/api/purchase/orderEntry'
import type {
  PurchaseEntryItem,
  ProcessingTreeNode,
  ProcessingTreeOperation,
} from '@/api/purchase/orderEntry'
import type { InventoryMaterial } from '@/types/inventory/material'
import {
  PurchaseProcessingOperationEnum,
  PurchaseProcessingDeliveryEnum,
} from '@/enums/purchase/processing'
import MaterialFormDialog from '@/components/inventory/MaterialFormDialog.vue'
import { usePermission } from '@/composables/usePermission'
import { Permissions } from '@/enums/permissions'
import PurchaseProcessingTreeRows from './PurchaseProcessingTreeRows.vue'
import PurchaseProcessingOperationDialog from './PurchaseProcessingOperationDialog.vue'
import PurchaseProcessingBindingDialog from './PurchaseProcessingBindingDialog.vue'
const props = defineProps<{
  modelValue?: string | null
  item: PurchaseEntryItem
  disabled?: boolean
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: string | null): void
  (e: 'change'): void
  (e: 'cancel'): void
}>()
const deliveryOnly = ref(false)
const expandNodeId = ref('')
const expandRevision = ref(0)
const normalizedPlan = ref('')
const activeLabel = ref('')
const slitSteps = ref<ProcessingTreeNode[]>([])
const { hasPermission } = usePermission()
const materialSelectOpen = ref(false)
const bindingScrap = ref(false),
  disposalReason = ref(''),
  searchCompleted = ref(false),
  noMatches = ref(false)
const materialFormVisible = ref(false),
  materialPreset = ref({ materialName: '', specification: '', unit: '' })
let createContext: { nodeId: string; version: number } | undefined
const canCreateFromSearch = computed(
  () =>
    !bindingScrap.value &&
    !bindingId.value &&
    searchCompleted.value &&
    noMatches.value &&
    !searching.value &&
    hasPermission(Permissions.INVENTORY_MATERIAL_ADD)
)
const savedTree = computed(() => readProcessingPlan(props.modelValue)?.tree)
const visible = ref(true),
  busy = ref(false),
  error = ref(''),
  tree = ref<ProcessingTreeNode>(),
  active = ref<ProcessingTreeNode>(),
  operationVisible = ref(false),
  bindingVisible = ref(false),
  bindingId = ref<number>(),
  materialOptions = ref<InventoryMaterial[]>([]),
  searching = ref(false)
const operation = ref<ProcessingTreeOperation>({
  type: PurchaseProcessingOperationEnum.SLIT.value,
  useQuantity: 1,
  slits: [],
})
const cutPreview = ref<ProcessingTreeNode>()
const cutPreviewBusy = ref(false),
  cutPreviewError = ref('')
let cutPreviewVersion = 0
let cutPreviewTimer: ReturnType<typeof setTimeout> | undefined
const cutPreviewPiece = computed(() =>
  cutPreview.value?.children?.find((n) => n.nodeId === `${cutPreview.value?.nodeId}-pieces`)
)
const cutPreviewRemainders = computed(() =>
  (cutPreview.value?.children || []).filter((n) => n !== cutPreviewPiece.value)
)
function resetCutPreview() {
  ++cutPreviewVersion
  clearTimeout(cutPreviewTimer)
  cutPreview.value = undefined
  cutPreviewBusy.value = false
  cutPreviewError.value = ''
}
async function previewCut(v = cutPreviewVersion) {
  if (
    !tree.value ||
    !active.value ||
    !operationVisible.value ||
    disposed ||
    v !== cutPreviewVersion
  )
    return
  const candidate = clone(tree.value)
  const node = find(candidate, active.value.nodeId)
  if (!node) return
  node.operation = clone(operation.value)
  cutPreviewBusy.value = true
  try {
    const r = await previewProcessingPlan({
      ...props.item,
      processingPlan: JSON.stringify({ tree: candidate }),
    })
    if (disposed || v !== cutPreviewVersion || !operationVisible.value) return
    const result = readProcessingPlan(r.data.processingPlan)?.tree
    const output = result && find(result, node.nodeId)
    if (!output?.children?.some((n) => n.nodeId === `${node.nodeId}-pieces`))
      throw new Error('切片预览响应无效')
    cutPreview.value = output
  } catch (ex: any) {
    if (v === cutPreviewVersion)
      cutPreviewError.value = ex?.message || '片数计算失败，请调整尺寸后重试'
  } finally {
    if (v === cutPreviewVersion) cutPreviewBusy.value = false
  }
}
watch(
  () => [
    operationVisible.value,
    active.value?.nodeId,
    JSON.stringify(operation.value),
    props.item.materialId,
    props.item.purchaseQuantity,
    props.item.rollWidthMm,
    props.item.rollLengthM,
  ],
  () => {
    resetCutPreview()
    if (
      !operationVisible.value ||
      operation.value.type !== PurchaseProcessingOperationEnum.CUT.value ||
      !(Number(operation.value.pieceWidthMm) > 0) ||
      !(Number(operation.value.pieceLengthMm) > 0) ||
      !(operation.value.useQuantity > 0)
    )
      return
    cutPreviewBusy.value = true
    const v = cutPreviewVersion
    cutPreviewTimer = setTimeout(() => {
      void previewCut(v)
    }, 300)
  },
  { flush: 'sync' }
)
let version = 0,
  disposed = false,
  searchVersion = 0
const clone = <T,>(v: T): T => JSON.parse(JSON.stringify(v))
function invalidate() {
  ++version
  busy.value = false
  operationVisible.value = false
  bindingVisible.value = false
  materialFormVisible.value = false
  ++searchVersion
  resetCutPreview()
}
function find(n: ProcessingTreeNode, id: string): ProcessingTreeNode | undefined {
  if (n.nodeId === id) return n
  for (const c of n.children || []) {
    const hit = find(c, id)
    if (hit) return hit
  }
}
async function recalculate(candidate: ProcessingTreeNode) {
  const v = ++version
  busy.value = true
  error.value = ''
  try {
    const r = await previewProcessingPlan({
      ...props.item,
      processingPlan: JSON.stringify({ tree: candidate }),
    })
    if (disposed || v !== version || !visible.value) return false
    const next = readProcessingPlan(r.data.processingPlan)?.tree
    if (!next) throw new Error('加工树响应无效')
    tree.value = next
    normalizedPlan.value = r.data.processingPlan!
    return true
  } catch (ex: any) {
    if (v === version) error.value = ex?.message || '加工树校核失败，草稿已保留'
    return false
  } finally {
    if (v === version) busy.value = false
  }
}
async function open() {
  if (props.disabled || busy.value) return
  visible.value = true
  tree.value = undefined
  error.value = ''
  if (props.item.purchaseUnit !== 'ROLL') {
    error.value = '请先选择按卷采购，并确认每卷长度'
    return
  }
  await recalculate(
    clone(
      savedTree.value ||
        ({
          nodeId: 'root',
          role: '采购原卷',
          unit: 'ROLL',
          widthMm: 0,
          lengthMm: 0,
          quantity: 0,
          children: [],
        } as ProcessingTreeNode)
    )
  )
}
function edit(
  n: ProcessingTreeNode,
  type: ProcessingTreeOperation['type'],
  sourceLabel = '',
  sources: ProcessingTreeNode[] = []
) {
  if (props.disabled || busy.value) return
  active.value = n
  activeLabel.value = sourceLabel
  slitSteps.value = type === PurchaseProcessingOperationEnum.SLIT.value ? sources : []
  error.value = ''
  operation.value =
    n.operation?.type === type
      ? clone(n.operation)
      : {
          type,
          useQuantity: n.nodeId === 'root' ? 1 : Number(n.quantity),
          slits: [{ widthMm: 0, rolls: 1 }],
          pieceWidthMm: Number(n.widthMm),
          pieceLengthMm: undefined,
          rotated: false,
          piecesPerUnit: null,
        }
  operationVisible.value = true
  if (type === PurchaseProcessingOperationEnum.CUT.value)
    operation.value.rotated = !!operation.value.rotated
}
function changeCutAxis(rotated: boolean) {
  if (rotated === !!operation.value.rotated) return
  const { pieceWidthMm, pieceLengthMm } = operation.value
  operation.value.pieceWidthMm = pieceLengthMm
  operation.value.pieceLengthMm = pieceWidthMm
  operation.value.rotated = rotated
}
function selectSlitStep(id: string) {
  const node = slitSteps.value.find((n) => n.nodeId === id)
  if (node)
    edit(node, PurchaseProcessingOperationEnum.SLIT.value, activeLabel.value, slitSteps.value)
}
async function applyOperation() {
  if (!tree.value || !active.value || busy.value || props.disabled) return
  const candidate = clone(tree.value),
    node = find(candidate, active.value.nodeId)!
  node.operation = clone(operation.value)
  if (await recalculate(candidate)) {
    expandNodeId.value = active.value.nodeId
    ++expandRevision.value
    publishDraft()
    operationVisible.value = false
  }
}
async function clear(n: ProcessingTreeNode) {
  if (props.disabled || busy.value) return false
  try {
    await ElMessageBox.confirm('清除该节点及下级加工，恢复为该规格交货？', '清除下级加工')
    if (!tree.value) return
    const candidate = clone(tree.value),
      node = find(candidate, n.nodeId)!
    node.operation = null
    node.children = []
    if (!(await recalculate(candidate))) return false
    publishDraft()
    return true
  } catch {
    /* 取消保留加工树。 */
    return false
  }
}
async function clearActive() {
  if (active.value && (await clear(active.value))) operationVisible.value = false
}
function bind(n: ProcessingTreeNode, sourceLabel = '') {
  if (props.disabled || busy.value) return
  active.value = n
  activeLabel.value = sourceLabel
  bindingId.value = n.materialId || undefined
  bindingScrap.value = n.delivery === PurchaseProcessingDeliveryEnum.SCRAP.value
  disposalReason.value = n.disposalReason || ''
  searchCompleted.value = false
  materialSelectOpen.value = false
  materialOptions.value = n.materialId
    ? [
        {
          materialId: n.materialId,
          materialCode: n.materialCode,
          materialName: n.materialName,
          specification: `${n.widthMm}×${n.lengthMm}mm`,
        } as InventoryMaterial,
      ]
    : []
  error.value = ''
  bindingVisible.value = true
  if (!bindingScrap.value) void search('')
}
function changeDelivery() {
  ++searchVersion
  searchCompleted.value = false
  searching.value = false
  error.value = ''
  if (!bindingScrap.value) void search('')
}
async function search(keyword: string) {
  const node = active.value
  if (!node || !bindingVisible.value || bindingScrap.value) return
  const v = ++searchVersion
  searching.value = true
  searchCompleted.value = false
  error.value = ''
  try {
    const r = await materialApi.search({
      keyword,
      pageNum: 1,
      pageSize: 30,
      processingWidthMm: Number(node.widthMm),
      processingLengthMm: Number(node.lengthMm),
      processingUnit: node.unit,
    })
    if (
      v === searchVersion &&
      !disposed &&
      bindingVisible.value &&
      active.value?.nodeId === node.nodeId
    ) {
      if (!Array.isArray(r.data?.records)) throw new Error('物料搜索响应无效，请重试')
      materialOptions.value = r.data.records
      noMatches.value = materialOptions.value.length === 0
      searchCompleted.value = true
    }
  } catch (ex: any) {
    if (v === searchVersion) error.value = ex?.message || '物料搜索失败'
  } finally {
    if (v === searchVersion) searching.value = false
  }
}
function createMaterial() {
  if (!canCreateFromSearch.value || !active.value || busy.value || props.disabled) return
  createContext = { nodeId: active.value.nodeId, version }
  materialPreset.value = {
    materialName: props.item.materialName || props.item.supplierMaterialName || '',
    specification: `${active.value.widthMm}*${active.value.lengthMm}mm`,
    unit: active.value.unit === 'ROLL' ? '㎡' : 'PCS',
  }
  materialFormVisible.value = true
}
async function materialCreated(material: InventoryMaterial) {
  materialFormVisible.value = false
  if (
    !createContext ||
    disposed ||
    !visible.value ||
    !bindingVisible.value ||
    bindingScrap.value ||
    busy.value ||
    props.disabled ||
    createContext.version !== version ||
    createContext.nodeId !== active.value?.nodeId
  )
    return
  materialOptions.value = [material]
  bindingId.value = material.materialId
  await applyBinding()
}
async function applyBinding() {
  if (
    !tree.value ||
    !active.value ||
    (bindingScrap.value ? !disposalReason.value.trim() : !bindingId.value) ||
    busy.value ||
    props.disabled
  )
    return
  const candidate = clone(tree.value),
    id = active.value.nodeId
  const target = find(candidate, id)!
  target.materialId = bindingScrap.value ? null : bindingId.value
  target.delivery = bindingScrap.value
    ? PurchaseProcessingDeliveryEnum.SCRAP.value
    : PurchaseProcessingDeliveryEnum.INBOUND.value
  target.disposalReason = bindingScrap.value ? disposalReason.value.trim() : null
  if (await recalculate(candidate)) {
    if (
      bindingScrap.value
        ? find(tree.value!, id)?.delivery === PurchaseProcessingDeliveryEnum.SCRAP.value
        : find(tree.value!, id)?.materialId === bindingId.value
    ) {
      publishDraft()
      bindingVisible.value = false
    } else error.value = '所选物料与终端规格不匹配，请重新选择'
  }
}
function publishDraft() {
  emit('update:modelValue', tree.value?.operation ? normalizedPlan.value : null)
  emit('change')
}
function cancelAdding() {
  invalidate()
  tree.value = undefined
  error.value = ''
  emit('cancel')
}
async function remove() {
  if (props.disabled || busy.value) return
  try {
    await ElMessageBox.confirm('移除本采购行的加工树？保存订单后生效。', '移除加工要求')
    invalidate()
    tree.value = undefined
    normalizedPlan.value = ''
    emit('update:modelValue', null)
    emit('change')
  } catch {
    /* 取消保留原方案。 */
  }
}
watch(
  () => props.modelValue,
  (value) => {
    if (value === normalizedPlan.value) return
    invalidate()
    visible.value = true
    tree.value = savedTree.value ? clone(savedTree.value) : undefined
    normalizedPlan.value = value || ''
  },
  { immediate: true }
)
watch(
  () => [
    props.item.materialId,
    props.item.purchaseUnit,
    props.item.purchaseQuantity,
    props.item.rollWidthMm,
    props.item.rollLengthM,
  ],
  async () => {
    invalidate()
    if (tree.value && !props.disabled && (await recalculate(clone(tree.value)))) publishDraft()
  }
)
onBeforeUnmount(() => {
  resetCutPreview()
  disposed = true
  ++version
  ++searchVersion
})
watch(bindingVisible, (value) => {
  if (!value) {
    ++searchVersion
    searchCompleted.value = false
    materialFormVisible.value = false
    createContext = undefined
  }
})
</script>
<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 12px 0;
}
.tree-scroll {
  max-height: 65vh;
  overflow: auto;
}
.hint {
  font-size: 12px;
  color: #606266;
  line-height: 1.8;
}
.notice {
  margin-top: 12px;
}
.empty-materials {
  padding: 12px;
  text-align: center;
}
.slit-row {
  display: flex;
  gap: 8px;
  align-items: center;
  margin: 14px 0;
}
.slit-row :deep(.el-input-number) {
  width: 110px;
}
</style>
