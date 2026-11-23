<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="1200px"
    append-to-body
    class="pick-preview-dialog"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
  >
    <div v-loading="loading" class="preview-body">
      <el-alert
        v-if="isSupplement && reasonType === 'SCRAP_REPLENISHMENT'"
        type="info"
        :closable="false"
        show-icon
        title="这是补产物料申请。仓库确认发料后，系统才会按原工单工艺路线生成补产任务；申请或驳回不会创建任务。"
        style="margin-bottom: 12px"
      />
      <el-alert
        v-if="errorMsg"
        type="error"
        :closable="false"
        show-icon
        :title="errorMsg"
        style="margin-bottom: 12px"
      />
      <el-alert
        v-if="treeWarning"
        type="warning"
        :closable="false"
        :title="treeWarning"
        style="margin-bottom: 12px"
      >
        <el-button link :disabled="treeLoading" @click="loadTreeDetails()">重试加载明细</el-button>
      </el-alert>
      <!-- A4 打印页样式预览 -->
      <div class="a4-preview">
        <div v-if="isSupplement" class="supplement-fields">
          <el-form label-width="110px" size="small">
            <el-form-item label="补料来源">
              <el-select
                v-model="reasonType"
                style="width: 260px"
                @change="onSupplementReasonChange"
              >
                <el-option label="生产超耗/现场缺料" value="PRODUCTION_OVERUSE" />
                <el-option label="报废后补产" value="SCRAP_REPLENISHMENT" />
                <!-- dev-20260923-025：补料来源三类补齐（原因口径与「不良原因」分开，各管一段） -->
                <el-option label="试制调机" value="TRIAL_ADJUSTMENT" />
                <el-option label="来料不良" value="INCOMING_DEFECT" />
              </el-select>
            </el-form-item>
            <el-form-item v-if="reasonType === 'SCRAP_REPLENISHMENT'" label="质量不良单" required>
              <!-- dev-20260923-025：从「手输 ID」改为下拉选未结不良单（显示单号/产品/待处置量），选中即预填补产数量 -->
              <el-select
                v-model="ncrId"
                filterable
                clearable
                placeholder="选择该工单的未结不良单"
                style="width: 320px"
                @change="onNcrChange"
              >
                <el-option
                  v-for="n in ncrOptions"
                  :key="n.ncrId"
                  :label="`${n.ncrNo} · 已报废 ${fmtNum(n.completedScrapQuantity)} · 已申请 ${fmtNum(n.requestedReplacementQuantity)} · 可补 ${fmtNum(n.remainingReplacementQuantity)}`"
                  :value="n.ncrId"
                />
              </el-select>
              <div v-if="ncrError" class="tree-error">{{ ncrError }}</div>
            </el-form-item>
            <el-form-item label="补产数量" required>
              <el-input-number
                v-model="supplementQuantity"
                :min="0.01"
                :max="
                  reasonType === 'SCRAP_REPLENISHMENT'
                    ? selectedNcr?.remainingReplacementQuantity
                    : undefined
                "
                :precision="2"
                @change="onSupplementQuantityChange"
              />
            </el-form-item>
            <el-form-item label="补料原因" required>
              <el-input v-model="supplementReason" maxlength="255" show-word-limit />
            </el-form-item>
          </el-form>
        </div>
        <div class="group-head">
          <el-checkbox v-model="groupView" @change="onGroupViewChange">按组合查看</el-checkbox>
          <span class="group-hint"
            >组合 = 连续相同项目；半成品按项目名匹配档案（匹配不到需先建档）</span
          >
          <el-button size="small" @click="resetPickQuantities">恢复建议数量</el-button>
        </div>
        <table v-if="groupView" class="doc-items group-summary">
          <thead>
            <tr>
              <th style="width: 8%">序号</th>
              <th>组合 / 项目</th>
              <th style="width: 12%">材料项</th>
              <th style="width: 12%">合计实领</th>
              <th style="width: 22%">半成品档案</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(g, i) in groups" :key="i">
              <td class="col-center">{{ i + 1 }}</td>
              <td>{{ g.processName || '独立材料' }}</td>
              <td class="col-center">{{ (g.children || []).length }}</td>
              <td class="col-center">{{ fmtNum(g.qtyPick) }}</td>
              <td>
                <el-tag v-if="g.semiMatched" type="success" size="small">已匹配</el-tag>
                <el-tag v-else type="warning" size="small">未匹配（需建半成品档案）</el-tag>
              </td>
            </tr>
            <tr v-if="!groups.length">
              <td colspan="5" class="col-center">无组合数据</td>
            </tr>
          </tbody>
        </table>
        <div class="doc-title">领 料 单（预览）</div>
        <div class="doc-info">
          <div class="info-item"><span class="info-label">工单号</span>{{ orderNo }}</div>
          <div class="info-item"><span class="info-label">产品</span>{{ productName || '-' }}</div>
          <div class="info-item">
            <span class="info-label">产品编码</span>{{ productCode || '-' }}
          </div>
          <div class="info-item">
            <span class="info-label">计划数量</span>{{ fmtNum(plannedQuantity) }}
          </div>
          <div class="info-item">
            <span class="info-label">物料种类</span>{{ mainRows.length }} 项
          </div>
        </div>
        <table class="doc-items">
          <thead>
            <tr>
              <th style="width: 4%">序号</th>
              <th style="width: 11%">材料编码</th>
              <th style="width: 16%">材料名称</th>
              <th style="width: 9%">项目名称</th>
              <th style="width: 11%">规格</th>
              <th style="width: 5%">单位</th>
              <th style="width: 8%">模数/基数</th>
              <th style="width: 8%">实发数量</th>
              <th style="width: 7%">需求</th>
              <th style="width: 8%">库存数量</th>
              <th style="width: 7%">备注</th>
              <th style="width: 6%">状态</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="{ node, row: r, depth, sequence } in visibleTreeRows"
              :key="node.key"
              :class="{
                'row-sub': r.substitute,
                'row-insufficient': r.insufficient,
                'row-bom-detail': r.isBomDetail,
              }"
            >
              <td class="col-center">{{ sequence }}</td>
              <td>{{ r.materialCode }}</td>
              <td>
                <div class="tree-material" :style="{ paddingLeft: `${depth * 16}px` }">
                  <button
                    v-if="node.children.length"
                    type="button"
                    class="tree-toggle"
                    :aria-expanded="!collapsedRows.has(node.key)"
                    :aria-label="`${collapsedRows.has(node.key) ? '展开' : '收起'}${r.materialName}制造BOM明细`"
                    @click="toggleTreeRow(node.key)"
                  >
                    {{ collapsedRows.has(node.key) ? '▸' : '▾' }}
                  </button>
                  <span v-else class="tree-spacer" aria-hidden="true"></span>
                  <span>{{ r.materialName }}</span>
                </div>
                <div v-if="node.error" class="tree-error">{{ node.error }}</div>
                <el-tag v-if="r.substitute" type="warning" size="small" style="margin-left: 6px"
                  >替代 {{ r.substituteOf }}</el-tag
                >
              </td>
              <td class="col-center">{{ r.processName || '-' }}</td>
              <td>
                {{ r.specification || '-' }}
              </td>
              <td class="col-center">{{ r.unit || '-' }}</td>
              <td class="col-center">{{ formatPickRatio(r) }}</td>
              <td class="col-center">
                <el-input-number
                  v-if="!r.substitute && !r.isBomDetail"
                  v-model="r.qtyPick"
                  :min="0"
                  :max="isSupplement ? undefined : Number(r.available)"
                  size="small"
                  controls-position="right"
                  style="width: 96px"
                />
                <span v-else-if="r.substitute" class="auto-sub">自动补足</span>
                <span v-else>-</span>
              </td>
              <td class="col-center">{{ fmtNum(r.qtyNeeded) }}</td>
              <td class="col-center">{{ fmtNum(r.available) }}</td>
              <td>{{ node.error || '-' }}</td>
              <td class="col-center">
                <el-tag v-if="r.isBomDetail" type="info" size="small">BOM明细</el-tag>
                <el-tag v-else-if="r.substitute" type="warning" size="small">替代料</el-tag>
                <el-tag
                  v-else-if="Number(r.qtyPick) > Number(r.qtyNeeded)"
                  type="warning"
                  size="small"
                  >超领</el-tag
                >
                <el-tag v-else-if="r.insufficient" type="danger" size="small">库存不足</el-tag>
                <el-tag v-else type="success" size="small">充足</el-tag>
              </td>
            </tr>
            <tr v-if="previewRows.length === 0 && !loading && !errorMsg">
              <td colspan="12" class="col-center">{{
                isSupplement && (!supplementQuantity || supplementQuantity <= 0)
                  ? '请先填写补产数量'
                  : '暂无领料明细'
              }}</td>
            </tr>
          </tbody>
        </table>
        <div v-if="overIssueCount" class="over-issue-box">
          <el-alert
            type="warning"
            :closable="false"
            show-icon
            :title="`有 ${overIssueCount} 行超领，提交后需审批，请填写原因`"
          />
          <el-input
            v-model="overIssueReason"
            type="textarea"
            :rows="2"
            maxlength="200"
            show-word-limit
            placeholder="超领原因（必填）"
          />
        </div>
        <div class="doc-summary">
          共 {{ previewRows.length }} 行 · 缺料 {{ shortageCount }} 行 · 替代 {{ substituteCount }} 行
        </div>
        <div class="doc-tip">
          <span>💡 实发数量可调整（0 ~ 库存数量）；主料领不足时，替代料由系统按短缺自动补足。</span>
        </div>
        <div class="doc-signs">
          <div class="sign-item">
            <div class="sign-line">领料人：</div>
            <div class="sign-underline"></div>
          </div>
          <div class="sign-item">
            <div class="sign-line">仓管员：</div>
            <div class="sign-underline"></div>
          </div>
          <div class="sign-item">
            <div class="sign-line">日期：</div>
            <div class="sign-underline"></div>
          </div>
        </div>
      </div>
    </div>
    <PickPreviewPrintDialog
      v-if="printVisible"
      v-model="printVisible"
      :order-no="orderNo"
      :product-code="productCode"
      :product-name="productName"
      :planned-quantity="plannedQuantity"
      :rows="printRows"
      :detail-warning="treeWarning"
      :supplement-quantity="isSupplement ? supplementQuantity : undefined"
    />
    <template #footer>
      <el-button
        :disabled="loading || treeLoading || submitting || !!errorMsg || !previewRows.length"
        @click="printVisible = true"
      >
        打印预览
      </el-button>
      <el-button
        v-if="!isSupplement"
        plain
        type="warning"
        :disabled="submitting"
        @click="emit('open-calculation')"
      >
        用料计算（缺料或需平替）
      </el-button>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="loading || treeLoading || submitting || !previewRows.length || !!errorMsg"
        @click="handleConfirm"
      >
        {{ isSupplement ? '提交补料申请' : isAppend ? '确认追加领料单' : '确认生成领料单' }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PickPreviewPrintDialog from './PickPreviewPrintDialog.vue'
import { formatPickRatio } from './pickPreviewPrint'
import { getProductionOrderDetail } from '@/api/production/order'
import { OrderType } from '@/types/production/order'
import { productBomApi } from '@/api/product/bom'
import {
  buildPickPreviewTree,
  flatPickPreviewNodes,
  flattenPickPreviewTree,
  type PickPreviewTreeNode,
} from './pickPreviewTree'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    workOrderId: number
    orderNo: string
    productCode?: string
    productName?: string
    plannedQuantity?: number
    // 2026-09-08 部分领料修正：first=首张领料单(createFromProduction)；append=追加补领(createProductionPick，按剩余需求)
    mode?: 'first' | 'append' | 'supplement'
    /** dev-20260923-025：由处置行发起时预填（报废后补产 + 指定不良单 + 建议补产数量） */
    presetReasonType?: string
    presetNcrId?: number
    presetProductionQuantity?: number
  }>(),
  { mode: 'first' }
)

const emit = defineEmits<{
  'update:modelValue': [v: boolean]
  success: [outboundId: number]
  'open-calculation': []
}>()

const isAppend = computed(() => props.mode === 'append')
const isSupplement = computed(() => props.mode === 'supplement')
const reasonType = ref<string>('PRODUCTION_OVERUSE')
const supplementReason = ref('')
const ncrId = ref<number>()
const supplementQuantity = ref<number>()
const overIssueReason = ref('')
/** 未结不良单选项（报废补产必选）—— dev-20260923-025 */
const ncrOptions = ref<any[]>([])
const ncrError = ref('')
const selectedNcr = computed(() =>
  ncrOptions.value.find((n) => Number(n.ncrId) === Number(ncrId.value))
)
const loadNcrOptions = async () => {
  try {
    const { qualityNcrApi } = await import('@/api/quality/lot')
    const res: any = await qualityNcrApi.byOrder(props.workOrderId)
    const list = (res?.data || []) as any[]
    ncrOptions.value = list.filter((n) => Number(n.remainingReplacementQuantity || 0) > 0)
    if (ncrId.value) {
      const selected = ncrOptions.value.find((n) => Number(n.ncrId) === Number(ncrId.value))
      if (!selected) {
        ncrId.value = undefined
        supplementQuantity.value = undefined
      } else if (supplementQuantity.value) {
        supplementQuantity.value = Math.min(
          Number(supplementQuantity.value),
          Number(selected.remainingReplacementQuantity)
        )
      }
    }
  } catch {
    ncrOptions.value = []
    ncrError.value = '未能加载未结不良单，请稍后重试'
  }
}
const onNcrChange = () => {
  const hit = ncrOptions.value.find((n) => Number(n.ncrId) === Number(ncrId.value))
  if (hit) {
    const available = Number(hit.remainingReplacementQuantity || 0)
    supplementQuantity.value = props.presetProductionQuantity
      ? Math.min(Number(props.presetProductionQuantity), available)
      : available || undefined
    loadPreview(true)
  }
}
const dialogTitle = computed(() =>
  isSupplement.value
    ? '申请补料 - 超耗/报废补产'
    : isAppend.value
      ? '追加领料单 - 按剩余需求补领（预览确认）'
      : '生成领料单 - 预览确认'
)

const loading = ref(false)
const submitting = ref(false)
const errorMsg = ref('')
const rows = ref<any[]>([])
const printVisible = ref(false)
const treeNodes = ref<PickPreviewTreeNode[]>([])
const collapsedRows = ref(new Set<string>())
const treeWarning = ref('')
const treeLoading = ref(false)
let treeGeneration = 0
const visibleTreeRows = computed(() => flattenPickPreviewTree(treeNodes.value, collapsedRows.value))
// 打印始终包含全部明细，不受屏幕上临时折叠影响。
const printRows = computed(() =>
  flattenPickPreviewTree(treeNodes.value).map(({ row, node, depth, sequence }) => ({
    ...row,
    depth,
    sequence,
    detailNote: node.error,
  }))
)

function toggleTreeRow(key: string) {
  if (collapsedRows.value.has(key)) collapsedRows.value.delete(key)
  else collapsedRows.value.add(key)
}

async function loadTreeDetails(requestId = previewRequestId) {
  const generation = ++treeGeneration
  const currentRows = rows.value
  treeNodes.value = flatPickPreviewNodes(currentRows)
  collapsedRows.value = new Set()
  treeWarning.value = ''
  if (!currentRows.length) return
  treeLoading.value = true
  const current = () =>
    generation === treeGeneration && requestId === previewRequestId && props.modelValue
  try {
    const response = await getProductionOrderDetail(String(props.workOrderId), OrderType.WORK_ORDER)
    if (!current()) return
    const bomId = Number(response.data?.bomId)
    if (!Number.isSafeInteger(bomId) || bomId <= 0) {
      treeWarning.value = '工单未绑定 BOM，当前仅展示本层领料明细'
      return
    }
    const nodes = await buildPickPreviewTree(currentRows, bomId, async (id) => {
      const result = await productBomApi.getEngineeringBomInfo(id)
      return result.data
    })
    if (!current()) return
    treeNodes.value = nodes
    if (flattenPickPreviewTree(nodes).some(({ node }) => node.error)) {
      treeWarning.value = '部分制造 BOM 明细未能展开，原因见对应材料行'
    }
  } catch (error) {
    if (current()) treeWarning.value = '制造 BOM 明细加载失败，当前仅展示本层领料明细'
    console.warn('读取领料制造 BOM 明细失败:', error)
  } finally {
    if (generation === treeGeneration) treeLoading.value = false
  }
}
/** dev-20261022-019：按组合查看（汇总），仅展示，不改变平铺领料的扣账口径 */
const groupView = ref(false)
const groups = ref<any[]>([])

async function loadGroups() {
  if (!groupView.value || !props.workOrderId) {
    groups.value = []
    return
  }
  try {
    const { materialPickApi } = await import('@/api/inventory/materialPick')
    const res: any = await materialPickApi.pickPreview(
      props.workOrderId,
      isSupplement.value && supplementQuantity.value ? Number(supplementQuantity.value) : undefined,
      'GROUP'
    )
    groups.value = (res?.data || []) as any[]
  } catch (error) {
    console.warn('读取组合视图失败:', error)
    groups.value = []
  }
}

function onGroupViewChange() {
  void loadGroups()
}

let previewRequestId = 0

// 主料行（可调整）
const mainRows = computed(() => rows.value.filter((r) => !r.substitute))
const overIssueCount = computed(
  () => mainRows.value.filter((r) => Number(r.qtyPick) > Number(r.qtyNeeded)).length,
)
// 编辑、打印和提交读取同一行；上限保留接口初始值，不随输入变化。
const previewRows = computed(() => rows.value)
const shortageCount = computed(() => previewRows.value.filter((r) => r.insufficient).length)
const substituteCount = computed(() => previewRows.value.filter((r) => r.substitute).length)

function resetPickQuantities() {
  for (const r of rows.value) {
    if (r.substitute || r.isBomDetail) continue
    r.qtyPick = Number(r.qtyPickMax)
  }
}

function fmtNum(v: any): string {
  if (v === null || v === undefined || v === '') return '-'
  const n = Number(v)
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

async function loadPreview(preserveEditedQuantities = false) {
  const supplementPreview = isSupplement.value
  if (supplementPreview && (!supplementQuantity.value || supplementQuantity.value <= 0)) {
    previewRequestId += 1
    rows.value = []
    treeNodes.value = []
    treeWarning.value = ''
    treeGeneration += 1
    treeLoading.value = false
    errorMsg.value = ''
    loading.value = false
    return
  }
  const requestId = ++previewRequestId
  const previousQtyByMaterial = new Map<number, number>(
    rows.value
      .filter((r) => !r.substitute && Number.isFinite(Number(r.materialId)))
      .map((r) => [Number(r.materialId), Number(r.qtyPick)])
  )
  loading.value = true
  errorMsg.value = ''
  try {
    const { materialPickApi } = await import('@/api/inventory/materialPick')
    const res: any = await materialPickApi.pickPreview(
      props.workOrderId,
      supplementPreview ? Number(supplementQuantity.value) : undefined
    )
    if (requestId !== previewRequestId) return
    rows.value = (res?.data || []).map((r: any) => ({
      ...r,
      qtyPickMax: Number(r.qtyPick),
      qtyPick: isSupplement.value
        ? preserveEditedQuantities && previousQtyByMaterial.has(Number(r.materialId))
          ? previousQtyByMaterial.get(Number(r.materialId))
          : Number(r.qtyNeeded) || 0
        : Number(r.qtyPick),
    }))
    await loadTreeDetails(requestId)
  } catch (e: any) {
    if (requestId === previewRequestId) errorMsg.value = e?.message || '领料预览加载失败'
  } finally {
    if (requestId === previewRequestId) loading.value = false
  }
}

let supplementQtyTimer: ReturnType<typeof setTimeout> | null = null
const onSupplementQuantityChange = () => {
  if (supplementQtyTimer) clearTimeout(supplementQtyTimer)
  if (!(isSupplement.value && props.modelValue)) return
  supplementQtyTimer = setTimeout(() => {
    supplementQtyTimer = null
    loadPreview()
  }, 400)
}

const onSupplementReasonChange = async (value: string) => {
  if (value === 'SCRAP_REPLENISHMENT') {
    await loadNcrOptions()
    await loadPreview(true)
    return
  }
  ncrId.value = undefined
  await loadPreview(true)
}

watch(
  () => props.modelValue,
  async (v) => {
    if (supplementQtyTimer) {
      clearTimeout(supplementQtyTimer)
      supplementQtyTimer = null
    }
    printVisible.value = false
    treeGeneration += 1
    treeLoading.value = false
    if (v) {
      // dev-20260923-025：处置行发起时预填来源/不良单/建议补产数量
      if (isSupplement.value) {
        reasonType.value = props.presetReasonType || 'PRODUCTION_OVERUSE'
        ncrId.value = props.presetNcrId
        supplementQuantity.value = props.presetProductionQuantity
      }
      if (isSupplement.value && props.presetReasonType) {
        reasonType.value = props.presetReasonType
      }
      if (isSupplement.value && props.presetNcrId) {
        ncrId.value = props.presetNcrId
      }
      if (isSupplement.value && props.presetProductionQuantity) {
        supplementQuantity.value = props.presetProductionQuantity
      }
      if (isSupplement.value) {
        if (reasonType.value === 'SCRAP_REPLENISHMENT') await loadNcrOptions()
        await loadPreview()
      } else {
        await loadPreview()
      }
    }
  },
  // 2026-08-18：immediate——组件首次挂载时 modelValue 已是 true，无变化事件，不加会漏首次加载
  { immediate: true }
)

async function handleConfirm() {
  submitting.value = true
  try {
    const { materialPickApi } = await import('@/api/inventory/materialPick')
    if (isSupplement.value) {
      if (!supplementReason.value.trim()) {
        ElMessage.warning('请填写补料原因')
        return
      }
      if (reasonType.value === 'SCRAP_REPLENISHMENT' && !ncrId.value) {
        ElMessage.warning('报废补产补料必须关联质量不良单')
        return
      }
      if (!supplementQuantity.value || supplementQuantity.value <= 0) {
        ElMessage.warning('请填写本次补产成品数量')
        return
      }
      const items = mainRows.value
        .filter((r) => Number(r.qtyPick) > 0)
        .map((r) => ({
          materialId: Number(r.materialId),
          quantity: Number(r.qtyPick),
          materialCode: r.materialCode,
          materialName: r.materialName,
        }))
      if (!items.length) {
        ElMessage.warning('请至少填写一种补料物料和数量')
        return
      }
      const res: any = await materialPickApi.createProductionSupplement(props.workOrderId, {
        reasonType: reasonType.value,
        reason: supplementReason.value.trim(),
        ncrId: ncrId.value,
        productionQuantity: supplementQuantity.value,
        items,
      })
      ElMessage.success(`补料申请已提交，出库单 ${res?.data}，请仓库审核发料`)
      emit('update:modelValue', false)
      emit('success', res?.data)
      return
    }
    if (isAppend.value) {
      // 追加：按剩余需求提交（0 < 实领 ≤ 剩余可领量），后端 createProductionPick 会二次校验
      const items = mainRows.value
        .filter((r) => Number(r.qtyPick) > 0)
        .map((r) => ({
          materialId: Number(r.materialId),
          quantity: Number(r.qtyPick),
          materialCode: r.materialCode,
          materialName: r.materialName,
        }))
      if (items.length === 0) {
        ElMessage.warning('剩余可领数量均为 0，无需追加领料')
        submitting.value = false
        return
      }
      const res: any = await materialPickApi.createProductionPick(props.workOrderId, items)
      ElMessage.success(`追加领料单已生成（出库单 ${res?.data}）`)
      emit('update:modelValue', false)
      emit('success', res?.data)
      return
    }
    if (overIssueCount.value > 0 && !overIssueReason.value.trim()) {
      ElMessage.warning('有超领行，请填写超领原因')
      return
    }
    // 全为 0 时后端会抛“库存不足”，这里先拦下并说明是未填写实领
    if (mainRows.value.length && mainRows.value.every((r) => Number(r.qtyPick) <= 0)) {
      ElMessage.warning('未填写实领数量，未生成领料单')
      return
    }
    // 首张领料单：仅传有调整的主料行（数量 ≠ 默认实领）；传 0 表示该行本次不领
    const adjusted = mainRows.value
      .filter((r) => Number(r.qtyPick) !== Number(r.qtyPickMax))
      .map((r) => ({ materialId: Number(r.materialId), quantity: Number(r.qtyPick) }))
    const res: any = await materialPickApi.createFromProduction(
      props.workOrderId,
      adjusted,
      overIssueCount.value > 0 ? overIssueReason.value.trim() : undefined,
    )
    ElMessage.success(`领料单已生成（出库单 ${res?.data}）`)
    emit('update:modelValue', false)
    emit('success', res?.data)
  } catch (e: any) {
    ElMessage.error(e?.message || (isAppend ? '追加领料失败' : '生成领料单失败'))
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.group-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 6px 0 8px;
}
.group-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
.group-summary {
  margin-bottom: 10px;
}
.group-summary td {
  vertical-align: middle;
}
</style>
<style scoped>
.preview-body {
  max-height: 70vh;
  overflow-y: auto;
  padding: 4px;
}
.a4-preview {
  background: #fff;
  border: 1px solid #dcdfe6;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
  padding: 24px 28px;
  font-size: 13px;
  color: #303133;
}
.doc-title {
  text-align: center;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 6px;
  margin-bottom: 14px;
  color: #000;
}
.doc-info {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 24px;
  margin-bottom: 10px;
  font-size: 12px;
}
.info-item .info-label {
  color: #909399;
  margin-right: 4px;
}
.doc-items {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.doc-items th,
.doc-items td {
  border: 1px solid #c0c4cc;
  padding: 4px 6px;
  text-align: left;
}
.doc-items th {
  background: #f5f7fa;
  font-weight: 600;
}
.col-center {
  text-align: center !important;
}
.row-sub {
  background: #fdf6ec;
}
.row-insufficient td {
  color: #f56c6c;
}
.auto-sub {
  color: #e6a23c;
  font-size: 12px;
}
.tree-material {
  display: flex;
  align-items: center;
  gap: 4px;
}
.tree-toggle {
  flex-shrink: 0;
  width: 22px;
  padding: 0;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.tree-toggle:focus-visible {
  outline: 2px solid var(--el-color-primary);
}
.tree-spacer {
  width: 22px;
  flex-shrink: 0;
}
.tree-error {
  color: var(--el-color-danger);
  font-size: 12px;
}
.row-bom-detail {
  background: var(--el-fill-color-light);
}
.over-issue-box {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.doc-summary {
  margin-top: 8px;
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.doc-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #909399;
}
.doc-signs {
  display: flex;
  justify-content: space-between;
  margin-top: 32px;
}
.sign-item {
  width: 140px;
}
.sign-line {
  font-size: 12px;
  color: #303133;
}
.sign-underline {
  border-bottom: 1px solid #303133;
  height: 24px;
}
</style>
