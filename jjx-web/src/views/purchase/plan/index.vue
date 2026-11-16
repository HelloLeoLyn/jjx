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
          ><template #default="{ row }">{{ row.materialSpec || '—' }}</template></el-table-column
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
        <el-table-column label="采购安排" min-width="260">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="PurchasePlanSchemeStatusEnum.getTagProps(schemeStatus(row)).type"
              >{{ PurchasePlanSchemeStatusEnum.getLabel(schemeStatus(row)) }}</el-tag
            >
            <template v-if="row.procurement">
              <div>
                {{ purchaseItem(row).materialCode }} · {{ entrySpecification(purchaseItem(row)) }}
              </div>
              <div>
                {{ row.supplierName || '未指定供应商' }} ·
                {{ row.procurement.expectedDeliveryDate || '到货日期未定' }}
              </div>
              <small>{{ row.procurement.message }}</small>
            </template>
            <small v-else>{{
              row.supplierName ? '默认供应商：' + row.supplierName : '请打开采购方案选择本次供应商'
            }}</small>
          </template>
        </el-table-column>
        <el-table-column label="计划采购量" width="145" align="right"
          ><template #default="{ row }">{{
            quantityLabel(purchaseItem(row).purchaseQuantity, purchaseItem(row).purchaseUnit)
          }}</template></el-table-column
        >
        <el-table-column label="折合基本数量" width="145" align="right"
          ><template #default="{ row }">{{
            purchaseItem(row).pricingError
              ? '待确认'
              : quantityLabel(purchaseItem(row).quantity, purchaseItem(row).unit)
          }}</template></el-table-column
        >
        <el-table-column label="需求日期" prop="needDate" width="115" />
        <el-table-column label="参考价 / 单价" width="190" align="right">
          <template #default="{ row }">
            <div class="ref-price">
              上次
              {{
                purchaseItem(row).lastPrice == null ? '—' : entryNumber(purchaseItem(row).lastPrice)
              }}
              · 标准
              {{
                purchaseItem(row).standardPrice == null
                  ? '—'
                  : entryNumber(purchaseItem(row).standardPrice)
              }}
            </div>
            <div>
              {{ entryNumber(purchaseItem(row).unitPrice) }} /
              {{ unitLabel(purchaseItem(row).priceUnit) }}
            </div>
            <small>金额 {{ entryNumber(purchaseItem(row).amount) }}</small>
          </template>
        </el-table-column>
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
        <el-table-column label="操作" width="150" fixed="right"
          ><template #default="{ row }"
            ><el-button link type="primary" :disabled="busy" @click="openEdit(row)"
              >采购方案</el-button
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
    <el-drawer
      v-model="showAddMaterial"
      :title="editingKey ? '采购方案' : '添加物料与采购方案'"
      size="min(760px, 100vw)"
      direction="rtl"
      append-to-body
      :close-on-click-modal="false"
      @closed="invalidateDraft"
    >
      <el-descriptions
        v-if="sourcePlan"
        title="原需求"
        :column="1"
        border
        size="small"
        class="source-demand"
      >
        <el-descriptions-item label="物料"
          >{{ sourcePlan.materialCode }} · {{ sourcePlan.materialName }}</el-descriptions-item
        >
        <el-descriptions-item label="规格">{{
          sourcePlan.materialSpec || '—'
        }}</el-descriptions-item>
        <el-descriptions-item label="需求"
          >{{ quantityLabel(sourcePlan.demandQuantity, sourcePlan.unit) }} ·
          {{ sourcePlan.needDate || '需求日期未定' }}</el-descriptions-item
        >
        <el-descriptions-item label="来源"
          >{{ sourcePlan.reason
          }}{{
            sourcePlan.sourceAlertIds.length
              ? ' · 预警 ' + sourcePlan.sourceAlertIds.join('、')
              : ''
          }}</el-descriptions-item
        >
      </el-descriptions>
      <el-form label-width="130px" :disabled="draftLoading || adding">
        <el-form-item label="本次采购物料">
          <el-select
            v-model="addMaterialId"
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
          <el-form-item label="本次供应商" required>
            <SupplierSelector
              :model-value="draft.supplierId"
              :selected-name="draft.supplierName"
              remote
              @change="selectSchemeSupplier"
            />
          </el-form-item>
          <el-alert
            v-if="sourcePlan && draft.materialId !== sourcePlan.materialId"
            title="本次采购物料与原需求不同"
            description="原需求保留。未建立本次需求的已确认替代/分切规则前，方案只能保留为待技术确认，不能生成替代采购单或抵扣原缺口。"
            type="warning"
            :closable="false"
          />
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
          <el-form-item label="计价单位">
            <el-select v-model="draft.priceUnit" aria-label="计价单位" @change="calculateDraft"
              ><el-option v-for="u in draftUnits" :key="u.value" :label="u.label" :value="u.value"
            /></el-select>
          </el-form-item>
          <el-form-item label="采购单价"
            ><el-input-number
              v-model="draft.unitPrice"
              :min="0"
              :precision="4"
              :controls="false"
              aria-label="采购单价"
              @change="calculateDraft"
          /></el-form-item>
          <el-form-item label="税率(%)"
            ><el-input-number
              v-model="draft.taxRate"
              :min="0"
              :max="100"
              :precision="2"
              :controls="false"
              @change="calculateDraft"
          /></el-form-item>
          <el-form-item label="采购金额"
            >{{ draftPricing ? '试算中…' : entryNumber(draft.amount) }}（税额
            {{ draftPricing ? '—' : entryNumber(draft.taxAmount) }}）</el-form-item
          >
          <el-form-item v-if="!editingKey" label="需求日期"
            ><el-date-picker
              v-model="draft.needDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="可选"
              @change="calculateDraft"
          /></el-form-item>
          <el-form-item label="预计到货"
            ><el-date-picker
              v-model="expectedDeliveryDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="可选，在订单核对时确认"
              @change="calculateDraft"
          /></el-form-item>
          <el-form-item label="优先级"
            ><el-select v-model="draft.priority"
              ><el-option label="普通" value="normal" /><el-option
                label="高"
                value="high" /><el-option label="紧急" value="urgent" /></el-select
          ></el-form-item>
          <el-form-item label="采购说明"
            ><el-input
              v-model="draft.purchaseRemark"
              type="textarea"
              :rows="2"
              maxlength="1700"
              show-word-limit
              placeholder="采购 / 加工 / 交货要求"
          /></el-form-item>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="预计满足原需">{{
              draftPricing
                ? '试算中…'
                : draftPreview?.coveredQuantity == null
                  ? '待确认'
                  : quantityLabel(draftPreview.coveredQuantity, sourcePlan?.unit)
            }}</el-descriptions-item>
            <el-descriptions-item label="剩余缺口">{{
              draftPricing
                ? '试算中…'
                : draftPreview?.remainingQuantity == null
                  ? '待确认'
                  : quantityLabel(draftPreview.remainingQuantity, sourcePlan?.unit)
            }}</el-descriptions-item>
          </el-descriptions>
          <el-alert
            v-if="draftPreview"
            :title="draftPreview.message"
            :type="
              draftPreview.status === PurchasePlanSchemeStatusEnum.READY.value ? 'info' : 'warning'
            "
            :closable="false"
          />
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
          :disabled="!draft || draftLoading || draftPricing || !!draftError || !!unitsError"
          @click="confirmAddMaterial"
          >{{
            draftPreview?.status === PurchasePlanSchemeStatusEnum.NEEDS_CONFIRMATION.value
              ? '保留待确认方案'
              : '采用本次方案'
          }}</el-button
        ></template
      >
    </el-drawer>
    <el-dialog v-model="showConfirmDialog" title="确认采购分组" width="760px" append-to-body>
      <p class="plan-hint">
        按供应商分组下单：一个供应商生成一张采购订单；未安排或待技术确认的行需先处理采购方案。
      </p>
      <el-table :data="confirmGroups" border size="small">
        <el-table-column label="供应商" min-width="200">
          <template #default="{ row }">
            <span :class="{ 'missing-supplier': row.missingSupplier }">{{ row.supplierName }}</span>
          </template>
        </el-table-column>
        <el-table-column label="物料数" width="80" align="center">
          <template #default="{ row }">{{ row.rows.length }}</template>
        </el-table-column>
        <el-table-column label="合计数量" min-width="170" align="right">
          <template #default="{ row }">{{ groupQtyLabel(row) }}</template>
        </el-table-column>
        <el-table-column label="预计到货" width="120"
          ><template #default="{ row }">{{
            row.earliestDeliveryDate || '订单中确认'
          }}</template></el-table-column
        >
        <el-table-column label="最早需求日" width="120" align="center">
          <template #default="{ row }">{{ row.earliestNeedDate || '—' }}</template>
        </el-table-column>
        <el-table-column label="紧急" width="70" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.urgent" type="danger" size="small">紧急</el-tag>
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button
              link
              type="primary"
              :disabled="!groupReady(row) || busy"
              @click="startGroup(row)"
              >生成</el-button
            >
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button :disabled="busy" @click="showConfirmDialog = false">取消</el-button>
        <el-button type="primary" :disabled="busy" @click="generateAllGroups"
          >全部生成（{{ confirmGroups.filter(groupReady).length }} 张）</el-button
        >
      </template>
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
      ><el-button :loading="linking" @click="retryAlertLink(true)"
        >重试关联预警</el-button
      ></el-alert
    >
  </div>
</template>

<script setup lang="ts">
defineOptions({ name: 'PurchasePlan' })
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as XLSX from 'xlsx'
import { getPlanSuggestions } from '@/api/purchase/order'
import {
  getPlanMaterial,
  getPlanUnits,
  getReferencePrices,
  previewPlanScheme,
} from '@/api/purchase/planEntry'
import type {
  PlanRow,
  PlanOrderSeed,
  SavedPurchaseOrder,
  PlanSchemePreview,
  PlanPurchaseItem,
} from '@/api/purchase/planEntry'
import { groupPlanRows, groupQtyLabel } from './groupPlanRows'
import type { PlanGroup } from './groupPlanRows'
import { entryNumber, entrySpecification } from '@/api/purchase/orderEntry'
import type { PurchaseUnitOption } from '@/api/purchase/orderEntry'
import { alertApi } from '@/api/inventory/alert'
import { materialApi } from '@/api/inventory/material'
import type { InventoryMaterial } from '@/types/inventory/material'
import SupplierSelector from '@/components/Selector/SupplierSelector.vue'
import type { PurchaseSupplier } from '@/types/purchase'
import { PurchasePlanSchemeStatusEnum } from '@/enums/purchase/plan'
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
const sourcePlan = ref<PlanRow>()
const draftPreview = ref<PlanSchemePreview>()
const expectedDeliveryDate = ref('')
const validatingPlan = ref(false)
const draft = ref<PlanRow>(),
  editingKey = ref(''),
  draftLoading = ref(false),
  draftPricing = ref(false),
  draftError = ref('')
const showMaterialDetail = ref(false),
  detailMaterialId = ref<number>()
const showConfirmDialog = ref(false)
const confirmGroups = ref<PlanGroup[]>([])
const orderQueue = ref<PlanGroup[]>([])
const currentGroup = ref<PlanGroup | null>(null)
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
const busy = computed(
  () => orderVisible.value || linking.value || !!pendingAlert.value || validatingPlan.value
)
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
    needDate: m.needDate,
    priority: 'normal',
    sourceAlertIds: [],
  }
}
function purchaseItem(row: PlanRow): PlanPurchaseItem {
  return row.procurement?.purchase || row
}
function schemeStatus(row: PlanRow) {
  return row.procurement?.status || PurchasePlanSchemeStatusEnum.UNARRANGED.value
}
function groupReady(group: PlanGroup) {
  return (
    !group.missingSupplier &&
    group.rows.every((row) => schemeStatus(row) === PurchasePlanSchemeStatusEnum.READY.value)
  )
}
function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value))
}
function schemeRequest(source: PlanRow, item: PlanPurchaseItem, delivery?: string) {
  return {
    sourceMaterialId: source.materialId!,
    demandQuantity: source.demandQuantity,
    supplierId: item.supplierId,
    needDate: source.needDate || undefined,
    expectedDeliveryDate: delivery || undefined,
    item: { ...item },
  }
}
function applyScheme(
  source: PlanRow,
  item: PlanPurchaseItem,
  preview: PlanSchemePreview,
  delivery?: string
): PlanRow {
  const purchase = {
    ...clone(item),
    ...preview.item,
    supplierName: preview.supplierName || item.supplierName,
  }
  return {
    ...source,
    supplierId: purchase.supplierId,
    supplierName: purchase.supplierName,
    priority: item.priority,
    procurement: {
      purchase,
      status: preview.status,
      expectedDeliveryDate: delivery || undefined,
      coveredQuantity: preview.coveredQuantity,
      remainingQuantity: preview.remainingQuantity,
      message: preview.message,
    },
  }
}
function selectSchemeSupplier(supplier: PurchaseSupplier | null) {
  if (!draft.value) return
  draft.value.supplierId = supplier?.supplierId
  draft.value.supplierName = supplier?.supplierName || ''
  void calculateDraft()
}
watch(
  () => [
    draft.value?.materialId,
    draft.value?.supplierId,
    draft.value?.purchaseUnit,
    draft.value?.purchaseQuantity,
    draft.value?.priceUnit,
    draft.value?.unitPrice,
    draft.value?.taxRate,
    draft.value?.rollWidthMm,
    draft.value?.rollLengthM,
    draft.value?.pieceWidthMm,
    draft.value?.pieceLengthMm,
    expectedDeliveryDate.value,
  ],
  () => {
    ++pricingVersion
    draftPreview.value = undefined
    draftPricing.value = false
  },
  { flush: 'sync' }
)

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
      ...(!row.procurement ? { supplierId: d.supplierId, supplierName: d.supplierName } : {}),
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
    void loadReferencePrices(planRows.value)
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
  draftPreview.value = undefined
}
function openAdd() {
  invalidateDraft()
  editingKey.value = ''
  sourcePlan.value = undefined
  expectedDeliveryDate.value = ''
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
    if (editingKey.value && sourcePlan.value)
      Object.assign(row, {
        priority: sourcePlan.value.priority,
        reason: sourcePlan.value.reason,
        needDate: sourcePlan.value.needDate,
      })
    draft.value = row
    const activeDraft = draft.value
    if (!editingKey.value) sourcePlan.value = clone(row)
    await loadReferencePrices([activeDraft])
    if (epoch !== draftEpoch || draft.value !== activeDraft) return
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
  sourcePlan.value = clone(row)
  const purchase = purchaseItem(row)
  expectedDeliveryDate.value = row.procurement?.expectedDeliveryDate || row.needDate || ''
  addMaterialId.value = purchase.materialId!
  draftError.value = ''
  materialOptions.value = [
    {
      materialId: purchase.materialId,
      materialCode: purchase.materialCode,
      materialName: purchase.materialName,
      specification: purchase.materialSpec,
    } as InventoryMaterial,
  ]
  draft.value = clone({ ...purchase, procurement: undefined })
  showAddMaterial.value = true
  // 库存/档案重新读取；编辑时不丢弃草稿，也不让旧余额伪装成最新余额。
  const epoch = ++draftEpoch
  draftLoading.value = true
  try {
    const r = await getPlanMaterial(purchase.materialId!)
    if (epoch !== draftEpoch || disposed || !showAddMaterial.value) return
    Object.assign(draft.value!, {
      currentStock: r.data.currentStock,
      availableStock: r.data.availableStock,
      inTransit: r.data.inTransit,
      allowedUnits: r.data.allowedUnits,
      archiveWidthMm: r.data.archiveWidthMm,
      archiveLengthMm: r.data.archiveLengthMm,
      contextError: '',
      ...(!row.procurement
        ? { supplierId: r.data.supplierId, supplierName: r.data.supplierName }
        : {}),
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
  draftPreview.value = undefined
  row.pricingError = ''
  draftPricing.value = true
  if (!(Number(row.purchaseQuantity) > 0)) {
    row.pricingError = '请输入大于0的计划采购数量'
    draftPricing.value = false
    return false
  }
  try {
    const source = sourcePlan.value || row
    const r = await previewPlanScheme(
      schemeRequest(
        { ...source, needDate: editingKey.value ? source.needDate : row.needDate },
        row,
        expectedDeliveryDate.value
      )
    )
    if (
      disposed ||
      epoch !== draftEpoch ||
      v !== pricingVersion ||
      draft.value !== row ||
      !showAddMaterial.value
    )
      return false
    Object.assign(row, r.data.item, {
      supplierName: r.data.supplierName || row.supplierName,
      pricingError: '',
    })
    draftPreview.value = r.data
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
    if (!draftPreview.value || !sourcePlan.value) return
    if (draftPreview.value.status === PurchasePlanSchemeStatusEnum.UNARRANGED.value) {
      ElMessage.warning(draftPreview.value.message)
      return
    }
    const actual = clone(draft.value)
    const origin = clone(sourcePlan.value)
    if (!editingKey.value) origin.needDate = actual.needDate
    const duplicate = planRows.value.find(
      (r) => r.materialId === origin.materialId && r.key !== editingKey.value
    )
    if (duplicate) {
      ElMessage.warning('该原需求物料已在计划中，请打开现有行采购方案')
      return
    }
    const index = planRows.value.findIndex((r) => r.key === editingKey.value)
    const row = applyScheme(
      index >= 0 ? planRows.value[index] : origin,
      actual,
      draftPreview.value,
      expectedDeliveryDate.value
    )
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
/** 参考价：上次采购价 + 标准价；单价为空时默认带出（dev-20261113-010）。 */
async function loadReferencePrices(rows: PlanRow[]) {
  const ids = [...new Set(rows.map((r) => r.materialId).filter((id): id is number => !!id))]
  if (!ids.length) return
  try {
    const res: any = await getReferencePrices(ids)
    const map = res?.data || {}
    for (const row of rows) {
      const ref = map[String(row.materialId)]
      if (!ref) continue
      row.lastPrice = ref.lastPrice == null ? null : Number(ref.lastPrice)
      row.standardPrice = ref.standardPrice == null ? null : Number(ref.standardPrice)
      if (!row.unitPrice || Number(row.unitPrice) <= 0) {
        const price = row.lastPrice ?? row.standardPrice
        if (price != null) {
          row.unitPrice = price
          row.amount = Number((Number(row.quantity || 0) * price).toFixed(2))
        }
      }
    }
  } catch {
    // 参考价读取失败不阻塞计划，单价仍可手填
  }
}

async function validateRows(rows: PlanRow[]): Promise<boolean> {
  if (validatingPlan.value) return false
  validatingPlan.value = true
  let ready = true
  try {
    for (const row of rows) {
      try {
        const candidate = purchaseItem(row)
        const response = await previewPlanScheme(
          schemeRequest(row, candidate, row.procurement?.expectedDeliveryDate)
        )
        if (!planRows.value.includes(row)) {
          ready = false
          continue
        }
        const next = applyScheme(
          row,
          candidate,
          response.data,
          row.procurement?.expectedDeliveryDate
        )
        Object.assign(row, next)
        if (schemeStatus(row) !== PurchasePlanSchemeStatusEnum.READY.value) ready = false
      } catch (e: any) {
        ready = false
        if (row.procurement) {
          row.procurement.status = PurchasePlanSchemeStatusEnum.UNARRANGED.value
          row.procurement.coveredQuantity = null
          row.procurement.remainingQuantity = null
          row.procurement.message = e?.message || '方案校核失败，请重新打开采购方案'
        }
        ElMessage.warning(e?.message || '方案校核失败，请重新打开采购方案')
      }
    }
    return ready
  } finally {
    validatingPlan.value = false
  }
}
async function handleConfirmPlan() {
  if (!selectedRows.value.length || busy.value) return
  if (unitsError.value) {
    ElMessage.warning('请先重新读取单位')
    return
  }
  if (selectedRows.value.some((row) => row.contextLoading || row.contextError)) {
    ElMessage.warning('请先重新读取原需求物料信息')
    return
  }
  await validateRows(selectedRows.value)
  const groups = groupPlanRows(selectedRows.value)
  const blocked = selectedRows.value.filter(
    (row) => schemeStatus(row) !== PurchasePlanSchemeStatusEnum.READY.value
  ).length
  if (blocked) ElMessage.warning(`有 ${blocked} 行采购方案未完成或待技术确认，请在采购方案中处理`)
  confirmGroups.value = groups
  showConfirmDialog.value = true
}

/** 组 → 订单种子：供应商/交期/紧急取组，明细带单价与金额（dev-20261113-010）。 */
function buildSeed(group: PlanGroup): PlanOrderSeed {
  const dates = group.rows
    .map((r) => r.procurement?.expectedDeliveryDate || r.needDate)
    .filter((d): d is string => !!d)
    .sort()
  return {
    supplierId: Number(group.supplierId),
    supplierName: group.supplierName,
    expectedDeliveryDate: dates[0],
    urgentFlag: group.urgent,
    items: group.rows.map((row) => {
      const actual = purchaseItem(row)
      return {
        ...actual,
        purchaseRemark: [
          actual.purchaseRemark,
          `原需求：${row.materialCode} ${row.materialSpec || ''}${row.demandQuantity == null ? '' : '；数量 ' + quantityLabel(row.demandQuantity, row.unit)}`,
          `计划来源：${row.reason}${row.needDate ? '；需求日期 ' + row.needDate : ''}`,
          row.procurement?.expectedDeliveryDate
            ? '预计到货：' + row.procurement.expectedDeliveryDate
            : '',
        ]
          .filter(Boolean)
          .join('\n'),
      }
    }),
  }
}

async function startGroup(group: PlanGroup) {
  if (validatingPlan.value || linking.value || pendingAlert.value) return
  if (!group.supplierId || group.missingSupplier) {
    ElMessage.warning('请在采购方案中选择本次供应商')
    return
  }
  if (!(await validateRows(group.rows)) || !groupReady(group)) {
    ElMessage.warning('本组采购方案未通过校核，请返回采购方案处理后重试')
    orderQueue.value = []
    return
  }
  orderSources = group.rows.map((row) => JSON.parse(JSON.stringify(row)))
  currentGroup.value = group
  orderSeed.value = buildSeed({
    ...group,
    supplierName: group.rows[0]?.supplierName || group.supplierName,
  })
  showConfirmDialog.value = false
  orderVisible.value = true
}

/** 全部生成：按分组顺序串行开单，保存一张再开下一张。 */
function generateAllGroups() {
  const queue = confirmGroups.value.filter(groupReady)
  if (!queue.length) {
    ElMessage.warning('没有可生成的供应商分组')
    return
  }
  orderQueue.value = queue.slice(1)
  startGroup(queue[0])
}
async function handleOrderSaved(saved?: SavedPurchaseOrder) {
  if (!saved?.orderNo) {
    ElMessage.warning('订单已保存，但未取得关联信息，请核对采购订单')
    return
  }
  const covered = orderSources.filter((source) => {
    const actual = purchaseItem(source)
    const item = saved.items.find((i) => i.materialId === actual.materialId)
    return (
      item &&
      actual.materialId === source.materialId &&
      item.unit === source.unit &&
      Number(item.quantity) >= Math.max(Number(actual.quantity), Number(source.demandQuantity || 0))
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
  if (!pendingAlert.value) openNextGroup()
}
/** 分组串行下单：保存一张后自动打开下一组（dev-20261113-010）。 */
function openNextGroup() {
  const next = orderQueue.value.shift()
  if (!next) {
    currentGroup.value = null
    return
  }
  setTimeout(() => startGroup(next), 0)
}
async function retryAlertLink(resumeQueue = false) {
  if (!pendingAlert.value || linking.value) return
  linking.value = true
  try {
    await alertApi.batchProcess(pendingAlert.value)
    pendingAlert.value = undefined
    ElMessage.success('来源预警已关联采购订单')
    if (resumeQueue) openNextGroup()
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
  return planRows.value.map((r) => {
    const actual = purchaseItem(r)
    return {
      物料编码: r.materialCode,
      物料名称: r.materialName,
      供应商品名: actual.supplierMaterialName || '',
      规格: r.materialSpec || '',
      基本单位: unitLabel(r.unit),
      原始需求: r.demandQuantity ?? '',
      当前库存: r.currentStock ?? '',
      可用库存: r.availableStock ?? '',
      采购在途: r.inTransit ?? '',
      计划采购量: actual.purchaseQuantity,
      采购单位: unitLabel(actual.purchaseUnit),
      折合基本数量: actual.pricingError ? '待确认' : actual.quantity,
      采购基本单位: unitLabel(actual.unit),
      采购安排: [
        PurchasePlanSchemeStatusEnum.getLabel(schemeStatus(r)),
        actual.materialCode,
        entrySpecification(actual),
        r.supplierName || '供应商未定',
        r.procurement?.expectedDeliveryDate || '到货未定',
      ].join('；'),
      预计满足原需: r.procurement?.coveredQuantity ?? '待确认',
      剩余缺口: r.procurement?.remainingQuantity ?? '待确认',
      需求日期: r.needDate || '',
      来源: r.reason,
      采购说明: actual.purchaseRemark || '',
      优先级: priorityLabel(r.priority),
      已关联订单: r.linkedOrderNo || '',
    }
  })
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
.source-demand {
  margin-bottom: 20px;
}
.purchase-plan small {
  color: var(--el-text-color-secondary);
}
</style>
