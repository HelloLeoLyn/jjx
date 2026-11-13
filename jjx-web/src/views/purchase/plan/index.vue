<template>
  <div class="purchase-plan">
    <div class="plan-title">采购计划工作台</div>
    <el-card shadow="never">
      <div class="plan-toolbar">
        <el-button
          type="primary"
          plain
          icon="Warning"
          :loading="loadingSuggestions"
          :disabled="busy"
          @click="loadSuggestions"
          >从预警加载</el-button
        >
        <el-button type="success" plain icon="Plus" :disabled="busy" @click="openAdd"
          >添加物料</el-button
        >
        <el-button plain icon="Refresh" :disabled="busy" @click="clearPlan">清空</el-button>
        <el-button plain icon="Download" :disabled="!planRows.length || busy" @click="handleExport"
          >导出 Excel</el-button
        >
        <el-button
          plain
          icon="Printer"
          :disabled="!planRows.length || busy"
          @click="handlePrintPlan"
          >打印计划</el-button
        >
        <el-button
          type="primary"
          icon="Check"
          :disabled="!selectedRows.length || busy"
          @click="handleConfirmPlan"
          >核对采购订单</el-button
        >
      </div>
      <p class="plan-hint">
        需求、库存和在途均按物料基本单位显示；计划采购量可另选单位。未读取的数据显示“—”。当前计划刷新页面后不保留。
      </p>
      <el-alert v-if="unitsError" :title="unitsError" type="error" :closable="false"
        ><el-button link @click="loadUnits">重新读取单位</el-button></el-alert
      >
    </el-card>
    <el-card shadow="never" class="table-card">
      <el-table :data="planRows" row-key="key" border @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="45" :selectable="() => !busy" />
        <el-table-column label="物料 / 品名" min-width="210">
          <template #default="{ row }"
            ><strong>{{ row.supplierMaterialName || row.materialName }}</strong>
            <div>
              <el-link type="primary" @click="openMaterialDetail(row.materialId)">{{
                row.materialCode
              }}</el-link>
              · {{ row.materialName }}
            </div></template
          >
        </el-table-column>
        <el-table-column label="规格" min-width="160"
          ><template #default="{ row }">{{ entrySpecification(row) }}</template></el-table-column
        >
        <el-table-column label="原始需求" width="120" align="right"
          ><template #default="{ row }">{{
            quantityLabel(row.demandQuantity, row.unit)
          }}</template></el-table-column
        >
        <el-table-column label="库存 / 可用" width="150" align="right"
          ><template #default="{ row }"
            ><div>{{ quantityLabel(row.currentStock, row.unit) }}</div>
            <small>可用 {{ quantityLabel(row.availableStock, row.unit) }}</small></template
          ></el-table-column
        >
        <el-table-column label="采购在途" width="120" align="right"
          ><template #default="{ row }">{{
            quantityLabel(row.inTransit, row.unit)
          }}</template></el-table-column
        >
        <el-table-column label="计划采购量" width="145" align="right"
          ><template #default="{ row }">{{
            quantityLabel(row.purchaseQuantity, row.purchaseUnit)
          }}</template></el-table-column
        >
        <el-table-column label="折合基本数量" width="145" align="right"
          ><template #default="{ row }">{{
            row.pricingError ? '待确认' : quantityLabel(row.quantity, row.unit)
          }}</template></el-table-column
        >
        <el-table-column label="需求日期" prop="needDate" width="115" />
        <el-table-column label="来源 / 说明" min-width="180"
          ><template #default="{ row }"
            ><div>{{ row.reason }}</div>
            <div>{{ row.purchaseRemark }}</div>
            <small v-if="row.linkedOrderNo"
              >已关联 {{ row.linkedOrderNo }}，请核对剩余计划，避免重复采购。</small
            ><el-button v-if="row.contextError" link type="warning" @click="refreshContext(row)"
              >{{ row.contextError }}，重试</el-button
            ><small v-if="row.contextLoading">读取库存中…</small></template
          ></el-table-column
        >
        <el-table-column label="优先级" width="80"
          ><template #default="{ row }"
            ><el-tag
              :type="
                row.priority === 'urgent' ? 'danger' : row.priority === 'high' ? 'warning' : 'info'
              "
              >{{ priorityLabel(row.priority) }}</el-tag
            ></template
          ></el-table-column
        >
        <el-table-column label="操作" width="120" fixed="right"
          ><template #default="{ row }"
            ><el-button link :disabled="busy" @click="openEdit(row)">编辑</el-button
            ><el-button link type="danger" :disabled="busy" @click="removeRow(row)"
              >移除</el-button
            ></template
          ></el-table-column
        >
      </el-table>
      <el-empty
        v-if="!planRows.length"
        description="暂无计划物料，点击「从预警加载」或「添加物料」"
        :image-size="80"
      />
    </el-card>
    <MaterialDetailDialog v-model="showMaterialDetail" :material-id="detailMaterialId" />
    <el-dialog
      v-model="showAddMaterial"
      :title="editingKey ? '修改计划物料' : '添加计划物料'"
      width="760px"
      append-to-body
      :close-on-click-modal="false"
      @closed="invalidateDraft"
    >
      <el-form label-width="130px" :disabled="draftLoading || adding">
        <el-form-item label="物料">
          <el-select
            v-model="addMaterialId"
            :disabled="!!editingKey"
            filterable
            remote
            :remote-method="searchMaterials"
            :loading="materialSearching"
            placeholder="搜索编码 / 名称 / 规格"
            style="width: 100%"
            @change="selectMaterial"
          >
            <el-option
              v-for="m in materialOptions"
              :key="m.materialId"
              :value="m.materialId"
              :label="`${m.materialCode} · ${m.supplierMaterialName || m.materialName} · ${m.specification || ''}`"
            />
          </el-select>
        </el-form-item>
        <template v-if="draft && !draftLoading">
          <el-form-item label="档案规格"
            ><span>{{ draft.materialSpec || '未维护' }} · 基本单位 {{ unitLabel(draft.unit) }}</span
            ><el-button link type="primary" @click="openMaterialDetail(draft.materialId)"
              >物料详情</el-button
            ></el-form-item
          >
          <el-form-item label="库存/可用/在途"
            ><span
              >{{ quantityLabel(draft.currentStock, draft.unit) }} /
              {{ quantityLabel(draft.availableStock, draft.unit) }} /
              {{ quantityLabel(draft.inTransit, draft.unit) }}</span
            ></el-form-item
          >
          <el-form-item v-if="draft.demandQuantity != null" label="原始需求"
            >{{ quantityLabel(draft.demandQuantity, draft.unit) }}（保留来源需求）</el-form-item
          >
          <el-form-item label="计划采购数量">
            <el-input-number
              v-model="draft.purchaseQuantity"
              :min="0"
              :precision="4"
              :controls="false"
              aria-label="计划采购数量"
              @change="calculateDraft"
            />
            <el-select
              v-model="draft.purchaseUnit"
              style="width: 120px; margin-left: 10px"
              aria-label="计划采购单位"
              @change="calculateDraft"
              ><el-option v-for="u in draftUnits" :key="u.value" :label="u.label" :value="u.value"
            /></el-select>
          </el-form-item>
          <template v-if="draft.purchaseUnit === 'ROLL'">
            <el-form-item label="每卷长度(m)"
              ><el-input-number
                v-model="draft.rollLengthM"
                :min="0"
                :precision="4"
                :controls="false"
                @change="calculateDraft"
            /></el-form-item>
            <el-form-item label="卷宽(mm)"
              ><el-input-number
                v-model="draft.rollWidthMm"
                :disabled="Number(draft.archiveWidthMm) > 0"
                :min="0"
                :precision="2"
                :controls="false"
                @change="calculateDraft"
            /></el-form-item>
          </template>
          <template v-else-if="needsPieceBasis">
            <el-form-item label="每片宽度(mm)"
              ><el-input-number
                v-model="draft.pieceWidthMm"
                :disabled="Number(draft.archiveWidthMm) > 0"
                :min="0"
                :precision="2"
                :controls="false"
                @change="calculateDraft"
            /></el-form-item>
            <el-form-item label="每片长度(mm)"
              ><el-input-number
                v-model="draft.pieceLengthMm"
                :disabled="Number(draft.archiveLengthMm) > 0"
                :min="0"
                :precision="2"
                :controls="false"
                @change="calculateDraft"
            /></el-form-item>
          </template>
          <el-form-item
            v-else-if="
              draft.purchaseUnit !== draft.unit && [draft.unit, draft.purchaseUnit].includes('M')
            "
            label="宽度(mm)"
            ><el-input-number
              v-model="draft.rollWidthMm"
              :disabled="Number(draft.archiveWidthMm) > 0"
              :min="0"
              :precision="2"
              :controls="false"
              @change="calculateDraft"
          /></el-form-item>
          <el-form-item label="折合基本数量"
            ><strong>{{
              draftPricing
                ? '换算中…'
                : draft.pricingError
                  ? '待确认'
                  : quantityLabel(draft.quantity, draft.unit)
            }}</strong></el-form-item
          >
          <el-alert
            v-if="draft.pricingError || draft.contextError"
            :title="draft.pricingError || draft.contextError"
            type="warning"
            :closable="false"
          />
          <el-form-item label="需求日期"
            ><el-date-picker
              v-model="draft.needDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="可选，带入订单交期"
          /></el-form-item>
          <el-form-item label="采购说明"
            ><el-input
              v-model="draft.purchaseRemark"
              type="textarea"
              :rows="2"
              maxlength="1700"
              show-word-limit
              placeholder="采购 / 加工 / 交货要求"
          /></el-form-item>
          <p class="plan-hint">
            档案已确定尺寸只读，换规格请重新选物料；整数片 / 卷及换算精度由系统校验。
          </p>
        </template>
        <el-alert v-if="draftError" :title="draftError" type="error" :closable="false"
          ><el-button link @click="selectMaterial(addMaterialId)">重试</el-button></el-alert
        >
      </el-form>
      <template #footer
        ><el-button :disabled="adding" @click="showAddMaterial = false">取消</el-button
        ><el-button
          type="primary"
          :loading="adding"
          :disabled="!draft || draftLoading || !!draftError || !!unitsError"
          @click="confirmAddMaterial"
          >{{ editingKey ? '保存计划行' : '加入计划' }}</el-button
        ></template
      >
    </el-dialog>
    <el-dialog v-model="showConfirmDialog" title="选择本次采购供应商" width="520px" append-to-body>
      <el-form label-width="90px"
        ><el-form-item label="供应商"
          ><SupplierSelector
            v-model="confirmSupplierId"
            :active-only="true"
            :selected-name="confirmSupplierName"
            @change="selectSupplier" /></el-form-item
        ><el-form-item label="计划物料"
          >{{ selectedRows.length }} 个物料（不同单位不合计数量）</el-form-item
        ></el-form
      >
      <p>下一步核对采购数量、报价单位、价格、税率和交期；保存成功后才关联订单并处理来源预警。</p>
      <template #footer
        ><el-button @click="showConfirmDialog = false">取消</el-button
        ><el-button type="primary" :disabled="!confirmSupplierId" @click="doConfirmPlan"
          >下一步：核对订单</el-button
        ></template
      >
    </el-dialog>
    <OrderFormDialog
      v-if="orderVisible"
      v-model:visible="orderVisible"
      :plan-seed="orderSeed"
      @success="handleOrderSaved"
    />
    <el-alert
      v-if="pendingAlert"
      type="warning"
      :closable="false"
      title="采购订单已保存，来源预警关联失败。请重试关联，不要重复下单。"
      ><el-button :loading="linking" @click="retryAlertLink">重试关联预警</el-button></el-alert
    >
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'PurchasePlan' })
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as XLSX from 'xlsx'
import { getPlanSuggestions } from '@/api/purchase/order'
import { getPlanMaterial, getPlanUnits, previewPlanQuantity } from '@/api/purchase/planEntry'
import type { PlanRow, PlanOrderSeed, SavedPurchaseOrder } from '@/api/purchase/planEntry'
import { entryNumber, entrySpecification } from '@/api/purchase/orderEntry'
import type { PurchaseUnitOption } from '@/api/purchase/orderEntry'
import { alertApi } from '@/api/inventory/alert'
import { materialApi } from '@/api/inventory/material'
import type { InventoryMaterial } from '@/types/inventory/material'
import SupplierSelector from '@/components/Selector/SupplierSelector.vue'
import MaterialDetailDialog from '@/components/inventory/MaterialDetailDialog.vue'
import OrderFormDialog from '@/views/purchase/order/components/OrderFormDialog.vue'

const planRows = ref<PlanRow[]>([]),
  selectedRows = ref<PlanRow[]>([])
const units = ref<PurchaseUnitOption[]>([]),
  unitsError = ref('')
const loadingSuggestions = ref(false),
  showAddMaterial = ref(false),
  adding = ref(false)
const addMaterialId = ref<number | null>(null),
  materialOptions = ref<InventoryMaterial[]>([]),
  materialSearching = ref(false)
const draft = ref<PlanRow>(),
  editingKey = ref(''),
  draftLoading = ref(false),
  draftPricing = ref(false),
  draftError = ref('')
const showMaterialDetail = ref(false),
  detailMaterialId = ref<number>()
const showConfirmDialog = ref(false),
  confirmSupplierId = ref<string>(),
  confirmSupplierName = ref('')
const orderVisible = ref(false),
  orderSeed = ref<PlanOrderSeed>(),
  linking = ref(false)
const pendingAlert = ref<{
  alertIds: number[]
  materialIds: number[]
  relatedOrderNo: string
  remark: string
}>()
const route = useRoute()
let disposed = false,
  draftEpoch = 0,
  pricingVersion = 0,
  searchVersion = 0,
  nextKey = 0
let orderSources: PlanRow[] = []
const contextVersions = new WeakMap<PlanRow, number>()
const busy = computed(() => orderVisible.value || linking.value || !!pendingAlert.value)
const unitLabel = (code?: string) => units.value.find((u) => u.value === code)?.label || code || '—'
const quantityLabel = (n: unknown, unit?: string) =>
  n == null ? '—' : `${entryNumber(n)} ${unitLabel(unit)}`
const priorityLabel = (p: string) => (p === 'urgent' ? '紧急' : p === 'high' ? '高' : '普通')
const draftUnits = computed(() =>
  units.value.filter((u) => draft.value?.allowedUnits?.includes(u.value))
)
const needsPieceBasis = computed(
  () =>
    !!draft.value &&
    [draft.value.unit, draft.value.purchaseUnit].includes('㎡') &&
    [draft.value.unit, draft.value.purchaseUnit].some((u) => u === 'PCS' || u === 'SHEET')
)
function openMaterialDetail(id?: number) {
  if (id) {
    detailMaterialId.value = id
    showMaterialDetail.value = true
  }
}
function handleSelectionChange(rows: PlanRow[]) {
  selectedRows.value = rows
}
function baseRow(m: any): PlanRow {
  return {
    key: `plan-${++nextKey}`,
    materialId: Number(m.materialId),
    materialCode: m.materialCode,
    materialName: m.materialName,
    supplierMaterialName: m.supplierMaterialName,
    materialSpec: m.materialSpec || m.specification || '',
    unit: m.unit || '',
    purchaseUnit: m.unit || '',
    priceUnit: m.unit || '',
    purchaseQuantity: 1,
    quantity: 1,
    unitPrice: 0,
    taxRate: 0,
    amount: 0,
    supplierId: m.supplierId,
    supplierName: m.supplierName,
    reason: '手动添加',
    priority: 'normal',
    sourceAlertIds: [],
  }
}
async function loadUnits() {
  try {
    const r = await getPlanUnits()
    if (disposed) return
    if (!Array.isArray(r.data) || !r.data.length) throw new Error('单位字典为空')
    units.value = r.data
    unitsError.value = ''
  } catch {
    if (!disposed) unitsError.value = '单位读取失败，请重试后操作'
  }
}
async function refreshContext(row: PlanRow) {
  const v = (contextVersions.get(row) || 0) + 1
  contextVersions.set(row, v)
  row.contextLoading = true
  try {
    const r = await getPlanMaterial(row.materialId!)
    if (disposed || contextVersions.get(row) !== v || !planRows.value.includes(row)) return
    const d = r.data
    const previousUnit = row.unit
    if (!row.purchaseUnit || row.purchaseUnit === previousUnit) row.purchaseUnit = d.unit
    // 更新档案信息与余额，不覆盖需求、计划单位/数量及本单确认尺寸。
    Object.assign(row, {
      materialCode: d.materialCode,
      materialName: d.materialName,
      supplierMaterialName: d.supplierMaterialName,
      materialSpec: d.materialSpec,
      unit: d.unit,
      allowedUnits: d.allowedUnits,
      archiveWidthMm: d.archiveWidthMm,
      archiveLengthMm: d.archiveLengthMm,
      currentStock: d.currentStock,
      availableStock: d.availableStock,
      inTransit: d.inTransit,
      contextError: '',
    })
    for (const key of ['rollWidthMm', 'rollLengthM', 'pieceWidthMm', 'pieceLengthMm'] as const)
      if (row[key] == null) row[key] = d[key]
  } catch {
    if (!disposed && contextVersions.get(row) === v) {
      row.currentStock = null
      row.availableStock = null
      row.inTransit = null
      row.contextError = '库存与在途未读取'
    }
  } finally {
    if (contextVersions.get(row) === v) row.contextLoading = false
  }
}
async function loadSuggestions() {
  loadingSuggestions.value = true
  try {
    const r: any = await getPlanSuggestions()
    if (disposed) return
    let added = 0
    for (const s of r.data || []) {
      if (!s.materialId) continue
      const existing = planRows.value.find((row) => row.materialId === Number(s.materialId))
      if (existing) {
        if (s.sourceAlertId && !existing.sourceAlertIds.includes(s.sourceAlertId))
          existing.sourceAlertIds.push(s.sourceAlertId)
        continue
      }
      const qty = s.suggestQuantity == null ? null : Number(s.suggestQuantity)
      const row = baseRow(s)
      Object.assign(row, {
        demandQuantity: qty,
        purchaseQuantity: qty,
        quantity: qty || 0,
        reason: s.reason || '采购建议',
        priority: s.priority || 'normal',
        sourceAlertIds: s.sourceAlertId ? [s.sourceAlertId] : [],
        pricingError: qty && qty > 0 ? '' : '请确认计划采购数量',
      })
      planRows.value.push(row)
      added++
      void refreshContext(planRows.value[planRows.value.length - 1])
    }
    if (added) ElMessage.success(`新增 ${added} 个计划物料`)
    else ElMessage.info('无新增采购建议，现有计划已保留')
  } catch (e: any) {
    ElMessage.error(e?.message || '加载采购建议失败，当前计划已保留')
  } finally {
    loadingSuggestions.value = false
  }
}
async function searchMaterials(keyword: string) {
  const v = ++searchVersion
  if (!keyword) {
    materialOptions.value = []
    return
  }
  materialSearching.value = true
  try {
    const r = await materialApi.search({ keyword, pageNum: 1, pageSize: 20 } as any)
    if (!disposed && v === searchVersion) materialOptions.value = r.data?.records || []
  } catch {
    if (v === searchVersion) ElMessage.error('物料搜索失败，请重试')
  } finally {
    if (v === searchVersion) materialSearching.value = false
  }
}
function invalidateDraft() {
  ++draftEpoch
  ++pricingVersion
  draftLoading.value = false
  draftPricing.value = false
}
function openAdd() {
  invalidateDraft()
  editingKey.value = ''
  addMaterialId.value = null
  draft.value = undefined
  draftError.value = ''
  materialOptions.value = []
  showAddMaterial.value = true
}
async function selectMaterial(id: number | null) {
  const epoch = ++draftEpoch
  ++pricingVersion
  draft.value = undefined
  draftError.value = ''
  if (!id) return
  draftLoading.value = true
  try {
    const r = await getPlanMaterial(id)
    if (disposed || epoch !== draftEpoch || !showAddMaterial.value) return
    const row = baseRow(r.data)
    Object.assign(row, r.data)
    draft.value = row
    await calculateDraft()
  } catch (e: any) {
    if (epoch === draftEpoch) draftError.value = e?.message || '物料与库存读取失败，请重试'
  } finally {
    if (epoch === draftEpoch) draftLoading.value = false
  }
}
async function openEdit(row: PlanRow) {
  invalidateDraft()
  editingKey.value = row.key
  addMaterialId.value = row.materialId!
  draftError.value = ''
  materialOptions.value = [
    {
      materialId: row.materialId,
      materialCode: row.materialCode,
      materialName: row.materialName,
      specification: row.materialSpec,
    } as InventoryMaterial,
  ]
  draft.value = JSON.parse(JSON.stringify(row))
  showAddMaterial.value = true
  // 库存/档案重新读取；编辑时不丢弃草稿，也不让旧余额伪装成最新余额。
  const epoch = ++draftEpoch
  draftLoading.value = true
  try {
    const r = await getPlanMaterial(row.materialId!)
    if (epoch !== draftEpoch || disposed || !showAddMaterial.value) return
    Object.assign(draft.value!, {
      currentStock: r.data.currentStock,
      availableStock: r.data.availableStock,
      inTransit: r.data.inTransit,
      allowedUnits: r.data.allowedUnits,
      archiveWidthMm: r.data.archiveWidthMm,
      archiveLengthMm: r.data.archiveLengthMm,
      contextError: '',
    })
    await calculateDraft()
  } catch {
    if (epoch === draftEpoch) {
      draft.value!.currentStock = null
      draft.value!.availableStock = null
      draft.value!.inTransit = null
      draftError.value = '物料与库存读取失败，请关闭后重新编辑，原计划保留'
    }
  } finally {
    if (epoch === draftEpoch) draftLoading.value = false
  }
}
async function calculateDraft(): Promise<boolean> {
  const row = draft.value,
    epoch = draftEpoch,
    v = ++pricingVersion
  if (!row) return false
  row.pricingError = ''
  draftPricing.value = true
  if (!(Number(row.purchaseQuantity) > 0)) {
    row.pricingError = '请输入大于0的计划采购数量'
    draftPricing.value = false
    return false
  }
  try {
    const r = await previewPlanQuantity({ ...row })
    if (
      disposed ||
      epoch !== draftEpoch ||
      v !== pricingVersion ||
      draft.value !== row ||
      !showAddMaterial.value
    )
      return false
    Object.assign(row, {
      quantity: r.data.quantity,
      unit: r.data.unit,
      purchaseUnit: r.data.purchaseUnit,
      purchaseQuantity: r.data.purchaseQuantity,
      rollCount: r.data.rollCount,
      pricingError: '',
    })
    return true
  } catch (e: any) {
    if (epoch === draftEpoch && v === pricingVersion)
      row.pricingError = e?.message || '数量换算失败，请核对单位与尺寸'
    return false
  } finally {
    if (epoch === draftEpoch && v === pricingVersion) draftPricing.value = false
  }
}
async function confirmAddMaterial() {
  if (!draft.value || adding.value || draftLoading.value || draftError.value || unitsError.value)
    return
  adding.value = true
  try {
    if (!(await calculateDraft())) return
    const row = JSON.parse(JSON.stringify(draft.value)) as PlanRow
    const duplicate = planRows.value.find(
      (r) => r.materialId === row.materialId && r.key !== editingKey.value
    )
    if (duplicate) {
      ElMessage.warning('该物料已在计划中，请编辑现有行；不同采购单位不能直接累加')
      return
    }
    const index = planRows.value.findIndex((r) => r.key === editingKey.value)
    if (index >= 0) {
      planRows.value.splice(index, 1, row)
      selectedRows.value = selectedRows.value.map((r) => (r.key === row.key ? row : r))
    } else planRows.value.push(row)
    if (Number(row.inTransit) > 0)
      ElMessage.warning(
        `该物料已有在途 ${quantityLabel(row.inTransit, row.unit)}，请核对避免重复采购`
      )
    showAddMaterial.value = false
    invalidateDraft()
  } finally {
    adding.value = false
  }
}
async function removeRow(row: PlanRow) {
  if (busy.value) return
  if (row.sourceAlertIds.length) {
    try {
      await ElMessageBox.confirm(
        '移除计划行不会处理来源预警，下次加载仍可能出现。确定移除吗？',
        '提示',
        { type: 'warning' }
      )
    } catch {
      return
    }
  }
  planRows.value = planRows.value.filter((r) => r.key !== row.key)
  selectedRows.value = selectedRows.value.filter((r) => r.key !== row.key)
}
async function clearPlan() {
  if (busy.value) return
  try {
    await ElMessageBox.confirm('确定清空当前计划？来源预警保留。', '提示', { type: 'warning' })
  } catch {
    return
  }
  planRows.value = []
  selectedRows.value = []
}
function selectSupplier(s: any) {
  confirmSupplierId.value = s?.supplierId ? String(s.supplierId) : undefined
  confirmSupplierName.value = s?.supplierName || ''
}
function handleConfirmPlan() {
  if (!selectedRows.value.length || busy.value) return
  confirmSupplierId.value = undefined
  confirmSupplierName.value = ''
  showConfirmDialog.value = true
}
function doConfirmPlan() {
  if (!confirmSupplierId.value || !confirmSupplierName.value || busy.value) return
  if (unitsError.value) {
    ElMessage.warning('请先重新读取单位')
    return
  }
  if (
    selectedRows.value.some(
      (row) =>
        row.pricingError ||
        row.contextLoading ||
        row.contextError ||
        !row.unit ||
        !(row.quantity > 0)
    )
  ) {
    ElMessage.warning('请先编辑并确认计划数量')
    return
  }
  orderSources = selectedRows.value.map((row) => JSON.parse(JSON.stringify(row)))
  const dates = orderSources
    .map((r) => r.needDate)
    .filter((d): d is string => !!d)
    .sort()
  orderSeed.value = {
    supplierId: Number(confirmSupplierId.value),
    supplierName: confirmSupplierName.value,
    expectedDeliveryDate: dates[0],
    urgentFlag: orderSources.some((r) => r.priority === 'urgent'),
    items: orderSources.map((row) => ({
      ...row,
      unitPrice: 0,
      taxRate: 13,
      amount: 0,
      purchaseRemark: [
        row.purchaseRemark,
        `计划来源：${row.reason}${row.demandQuantity == null ? '' : `；原始需求 ${quantityLabel(row.demandQuantity, row.unit)}`}${row.needDate ? `；需求日期 ${row.needDate}` : ''}`,
      ]
        .filter(Boolean)
        .join('\n'),
    })),
  }
  showConfirmDialog.value = false
  orderVisible.value = true
}
async function handleOrderSaved(saved?: SavedPurchaseOrder) {
  if (!saved?.orderNo) {
    ElMessage.warning('订单已保存，但未取得关联信息，请核对采购订单')
    return
  }
  const covered = orderSources.filter((source) => {
    const item = saved.items.find((i) => i.materialId === source.materialId)
    return (
      item &&
      item.unit === source.unit &&
      Number(item.quantity) >= Math.max(Number(source.quantity), Number(source.demandQuantity || 0))
    )
  })
  const coveredKeys = new Set(covered.map((r) => r.key))
  for (const row of planRows.value)
    if (orderSources.some((s) => s.key === row.key)) row.linkedOrderNo = saved.orderNo
  planRows.value = planRows.value.filter((r) => !coveredKeys.has(r.key))
  selectedRows.value = []
  const alertIds = [...new Set(covered.flatMap((r) => r.sourceAlertIds))]
  if (covered.length < orderSources.length)
    ElMessage.warning(
      '部分物料下单量未覆盖原始需求或计划量，计划已保留并标记订单，请核对剩余量，避免重复采购'
    )
  if (covered.length) {
    pendingAlert.value = {
      alertIds,
      materialIds: covered.map((r) => r.materialId!),
      relatedOrderNo: saved.orderNo,
      remark: '采购计划核对订单保存成功',
    }
    await retryAlertLink()
  }
}
async function retryAlertLink() {
  if (!pendingAlert.value || linking.value) return
  linking.value = true
  try {
    await alertApi.batchProcess(pendingAlert.value)
    pendingAlert.value = undefined
    ElMessage.success('来源预警已关联采购订单')
  } catch {
    ElMessage.warning('订单已保存，预警关联失败，请重试关联；不要重复下单')
  } finally {
    linking.value = false
  }
}
async function prefetchMaterial(id: number, alertId?: number) {
  const existing = planRows.value.find((r) => r.materialId === id)
  if (existing) {
    if (alertId && !existing.sourceAlertIds.includes(alertId)) existing.sourceAlertIds.push(alertId)
    return
  }
  try {
    const r = await getPlanMaterial(id)
    if (disposed) return
    const row = baseRow(r.data)
    Object.assign(row, r.data, {
      reason: alertId ? `来自预警#${alertId}` : '预警跳转预填',
      sourceAlertIds: alertId ? [alertId] : [],
    })
    planRows.value.push(row)
  } catch {
    ElMessage.error('预填物料失败，请手动添加')
  }
}
function exportRows() {
  return planRows.value.map((r) => ({
    物料编码: r.materialCode,
    物料名称: r.materialName,
    供应商品名: r.supplierMaterialName || '',
    规格: entrySpecification(r),
    基本单位: unitLabel(r.unit),
    原始需求: r.demandQuantity ?? '',
    当前库存: r.currentStock ?? '',
    可用库存: r.availableStock ?? '',
    采购在途: r.inTransit ?? '',
    计划采购量: r.purchaseQuantity,
    采购单位: unitLabel(r.purchaseUnit),
    折合基本数量: r.pricingError ? '待确认' : r.quantity,
    需求日期: r.needDate || '',
    来源: r.reason,
    采购说明: r.purchaseRemark || '',
    优先级: priorityLabel(r.priority),
    已关联订单: r.linkedOrderNo || '',
  }))
}
function handleExport() {
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, XLSX.utils.json_to_sheet(exportRows()), '采购计划')
  XLSX.writeFile(wb, `采购计划_${new Date().toISOString().slice(0, 10)}.xlsx`)
}
function handlePrintPlan() {
  const key = `purchase-plan-print-${Date.now()}-${Math.random().toString(36).slice(2)}`
  try {
    sessionStorage.setItem(key, JSON.stringify(exportRows()))
    const win = window.open(`/purchase/plan/print?snapshot=${encodeURIComponent(key)}`, '_blank')
    sessionStorage.removeItem(key)
    if (!win) ElMessage.warning('打印窗口被拦截，请允许弹出窗口后重试')
  } catch {
    ElMessage.error('打印快照生成失败，请重试')
  }
}
onMounted(async () => {
  void loadUnits()
  await loadSuggestions()
  if (route.query.materialId)
    await prefetchMaterial(
      Number(route.query.materialId),
      route.query.alertId ? Number(route.query.alertId) : undefined
    )
})
onBeforeUnmount(() => {
  disposed = true
  invalidateDraft()
  ++searchVersion
})
</script>
<style scoped>
.plan-title {
  font-size: 16px;
  font-weight: 600;
  margin-bottom: 12px;
}
.plan-toolbar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.plan-toolbar .el-button {
  margin-left: 0;
}
.plan-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  line-height: 1.8;
  margin: 12px 0 0;
}
.table-card {
  margin-top: 12px;
}
.purchase-plan small {
  color: var(--el-text-color-secondary);
}
</style>
