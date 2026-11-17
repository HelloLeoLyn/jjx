<template>
  <div class="tree-editor">
    <div class="toolbar">
      <strong>加工树</strong
      ><el-button link type="primary" :disabled="disabled" @click="open">{{
        modelValue ? '维护加工树' : '添加加工要求'
      }}</el-button
      ><el-button v-if="modelValue" link type="danger" :disabled="disabled" @click="remove"
        >移除加工要求</el-button
      >
    </div>
    <PurchaseProcessingTreeRows v-if="savedTree" :tree="savedTree" />
    <el-dialog
      v-model="visible"
      title="加工树"
      width="min(1280px, calc(100vw - 24px))"
      append-to-body
      :close-on-click-modal="false"
      :close-on-press-escape="!busy"
      :show-close="!busy"
      @closed="invalidate"
    >
      <p>
        {{ item.supplierMaterialName || item.materialName }} · {{ item.materialCode }}：采购
        {{
          item.purchaseQuantity
        }}卷。选择要加工的卷数，其余原卷自动保留；分切生成宽度余料，切片生成边料和尾料。
      </p>
      <div class="tree-scroll" v-loading="busy">
        <PurchaseProcessingTreeRows
          v-if="tree"
          :tree="tree"
          :material-name="item.supplierMaterialName || item.materialName"
        >
          <template #actions="{ node, sourceLabel, slitSources }">
            <el-button
              link
              type="primary"
              :disabled="busy || disabled"
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
              link
              type="primary"
              :disabled="busy || disabled"
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
            <el-button v-else link :disabled="busy || disabled" @click="bind(node, sourceLabel)"
              >指定交货物料</el-button
            >
          </template>
        </PurchaseProcessingTreeRows>
      </div>
      <el-alert v-if="error" :title="error" type="error" :closable="false" class="notice" />
      <p class="hint">
        只对最终节点交货，中间料不重复入库。原卷自动关联原物料；切片和余料可先保存草稿，提交审核前须指定对应规格物料。当前几何按不扣切缝、修边损耗计算；切片方向和每卷/块片数可明确填写。
      </p>
      <template #footer
        ><el-button :disabled="busy" @click="visible = false">取消</el-button
        ><el-button type="primary" :loading="busy" :disabled="!tree || disabled" @click="adopt"
          >采用加工树</el-button
        ></template
      >
    </el-dialog>
    <el-dialog
      v-model="operationVisible"
      :title="operation.type === PurchaseProcessingOperationEnum.SLIT.value ? '分切' : '切片'"
      width="650px"
      append-to-body
      :close-on-click-modal="false"
      :show-close="!busy"
      :close-on-press-escape="!busy"
    >
      <el-form label-width="145px" :disabled="busy">
        <p v-if="activeLabel">所属：{{ activeLabel }}</p>
        <p v-if="active">
          来源：{{ active.role }} · {{ active.widthMm }}mm ×
          {{ active.unit === 'ROLL' ? Number(active.lengthMm) / 1000 : active.lengthMm
          }}{{ active.unit === 'ROLL' ? 'm' : 'mm' }}，{{ active.quantity
          }}{{ active.unit === 'ROLL' ? '卷' : '块' }}
        </p>
        <el-form-item v-if="slitSteps.length > 1" label="分切步骤">
          <el-select
            :model-value="active?.nodeId"
            aria-label="选择分切步骤"
            @change="selectSlitStep"
          >
            <el-option
              v-for="(step, i) in slitSteps"
              :key="step.nodeId"
              :value="step.nodeId"
              :label="`${i ? '剩余宽度' : '来源原料'} ${step.widthMm}mm → ${step.operation?.slits?.map((s) => `${s.widthMm}mm×${s.rolls}份`).join('、')}`"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="active?.unit === 'ROLL' ? '本次使用卷数' : '本次使用块数'"
          ><el-input-number
            v-model="operation.useQuantity"
            :min="1"
            :max="active?.quantity"
            :precision="0"
            :controls="false"
            aria-label="本次使用数量"
        /></el-form-item>
        <template v-if="operation.type === PurchaseProcessingOperationEnum.SLIT.value">
          <div v-for="(slit, i) in operation.slits" :key="i" class="slit-row">
            <span>每卷 / 块分切</span
            ><el-input-number
              v-model="slit.widthMm"
              :min="0"
              :precision="2"
              :controls="false"
              aria-label="分切宽度"
            />mm ×
            <el-input-number
              v-model="slit.rolls"
              :min="1"
              :precision="0"
              :controls="false"
              aria-label="每卷分切份数"
            />份<el-button link type="danger" @click="operation.slits?.splice(i, 1)"
              >移除</el-button
            >
          </div>
          <el-button plain @click="operation.slits?.push({ widthMm: 0, rolls: 1 })"
            >添加分切规格</el-button
          >
        </template>
        <template v-else>
          <el-form-item label="切片宽度(mm)"
            ><el-input-number
              v-model="operation.pieceWidthMm"
              :min="0"
              :precision="2"
              :controls="false"
              aria-label="切片宽度"
          /></el-form-item>
          <el-form-item label="切片长度(mm)"
            ><el-input-number
              v-model="operation.pieceLengthMm"
              :min="0"
              :precision="2"
              :controls="false"
              aria-label="切片长度"
          /></el-form-item>
          <el-form-item label="排版方向"
            ><el-checkbox v-model="operation.rotated">旋转90°排版</el-checkbox></el-form-item
          >
          <el-form-item label="每卷 / 块切片数"
            ><el-input-number
              v-model="operation.piecesPerUnit"
              :min="1"
              :precision="0"
              :controls="false"
              placeholder="留空按可切满"
              aria-label="每卷切片数"
              clearable
          /></el-form-item>
          <p class="hint">
            片数留空时由后台按尺寸算可切满数量；填写时按每个来源卷/块解释，剩余部分自动生成余料。
          </p>
        </template>
      </el-form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" />
      <template #footer
        ><el-button
          v-if="active?.operation"
          type="danger"
          link
          :disabled="busy"
          @click="clearActive"
          >清除本步及下级加工</el-button
        ><el-button :disabled="busy" @click="operationVisible = false">取消</el-button
        ><el-button type="primary" :loading="busy" @click="applyOperation"
          >确定并生成余料</el-button
        ></template
      >
    </el-dialog>
    <el-dialog
      v-model="bindingVisible"
      title="指定终端交货物料"
      width="600px"
      append-to-body
      :close-on-click-modal="false"
      :show-close="!busy"
    >
      <p v-if="active">
        {{ activeLabel || active.role }}：{{ active.widthMm }}mm ×
        {{ active.unit === 'ROLL' ? Number(active.lengthMm) / 1000 : active.lengthMm
        }}{{ active.unit === 'ROLL' ? 'm' : 'mm' }}，{{ active.quantity
        }}{{ active.unit === 'ROLL' ? '卷' : '片' }}
      </p>
      <el-select
        v-model="bindingId"
        remote
        filterable
        :remote-method="search"
        :loading="searching"
        :disabled="busy"
        placeholder="选择对应规格物料"
        style="width: 100%"
        ><el-option
          v-for="m in materialOptions"
          :key="m.materialId"
          :value="m.materialId"
          :label="`${m.materialCode} · ${m.materialName} · ${m.specification || ''}`"
      /></el-select>
      <el-alert v-if="error" :title="error" type="error" :closable="false" class="notice" />
      <template #footer
        ><el-button :disabled="busy" @click="bindingVisible = false">取消</el-button
        ><el-button type="primary" :disabled="!bindingId" :loading="busy" @click="applyBinding"
          >确定物料</el-button
        ></template
      >
    </el-dialog>
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
import { PurchaseProcessingOperationEnum } from '@/enums/purchase/processing'
import PurchaseProcessingTreeRows from './PurchaseProcessingTreeRows.vue'
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
const normalizedPlan = ref('')
const activeLabel = ref('')
const slitSteps = ref<ProcessingTreeNode[]>([])
const savedTree = computed(() => readProcessingPlan(props.modelValue)?.tree)
const visible = ref(false),
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
let version = 0,
  disposed = false,
  searchVersion = 0,
  adopted = false
const clone = <T,>(v: T): T => JSON.parse(JSON.stringify(v))
function invalidate() {
  ++version
  busy.value = false
  operationVisible.value = false
  bindingVisible.value = false
  if (!adopted) emit('cancel')
  adopted = false
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
  adopted = false
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
}
function selectSlitStep(id: string) {
  const node = slitSteps.value.find((n) => n.nodeId === id)
  if (node)
    edit(node, PurchaseProcessingOperationEnum.SLIT.value, activeLabel.value, slitSteps.value)
}
async function applyOperation() {
  if (!tree.value || !active.value || busy.value) return
  const candidate = clone(tree.value),
    node = find(candidate, active.value.nodeId)!
  node.operation = clone(operation.value)
  if (await recalculate(candidate)) operationVisible.value = false
}
async function clear(n: ProcessingTreeNode) {
  try {
    await ElMessageBox.confirm('清除该节点及下级加工，恢复为该规格交货？', '清除下级加工')
    if (!tree.value) return
    const candidate = clone(tree.value),
      node = find(candidate, n.nodeId)!
    node.operation = null
    node.children = []
    return await recalculate(candidate)
  } catch {
    /* 取消保留加工树。 */
    return false
  }
}
async function clearActive() {
  if (active.value && (await clear(active.value))) operationVisible.value = false
}
function bind(n: ProcessingTreeNode, sourceLabel = '') {
  active.value = n
  activeLabel.value = sourceLabel
  bindingId.value = n.materialId || undefined
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
}
async function search(keyword: string) {
  const v = ++searchVersion
  searching.value = true
  try {
    const r = await materialApi.search({ keyword, pageNum: 1, pageSize: 30 })
    if (v === searchVersion && !disposed) materialOptions.value = r.data?.records || []
  } catch (ex: any) {
    if (v === searchVersion) error.value = ex?.message || '物料搜索失败'
  } finally {
    if (v === searchVersion) searching.value = false
  }
}
async function applyBinding() {
  if (!tree.value || !active.value || !bindingId.value || busy.value) return
  const candidate = clone(tree.value),
    id = active.value.nodeId
  find(candidate, id)!.materialId = bindingId.value
  if (await recalculate(candidate)) {
    if (find(tree.value!, id)?.materialId === bindingId.value) bindingVisible.value = false
    else error.value = '所选物料与终端规格不匹配，请重新选择'
  }
}
async function adopt() {
  if (!tree.value || busy.value) return
  if (!tree.value.operation) {
    error.value = '请至少添加一处分切或切片要求'
    return
  }
  if (!(await recalculate(clone(tree.value)))) return
  emit('update:modelValue', normalizedPlan.value)
  emit('change')
  adopted = true
  visible.value = false
}
async function remove() {
  try {
    await ElMessageBox.confirm('移除本采购行的加工树？保存订单后生效。', '移除加工要求')
    emit('update:modelValue', null)
    emit('change')
  } catch {
    /* 取消保留原方案。 */
  }
}
watch(
  () => [
    props.item.materialId,
    props.item.purchaseQuantity,
    props.item.rollWidthMm,
    props.item.rollLengthM,
  ],
  () => {
    if (visible.value) {
      ++version
      busy.value = false
      error.value = '来源采购数量或尺寸已变化，请关闭后重新打开加工树'
    }
  }
)
onBeforeUnmount(() => {
  disposed = true
  ++version
  ++searchVersion
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
