<template>
  <el-dialog
    :title="title"
    :model-value="props.visible"
    width="min(1480px, calc(100vw - 24px))"
    append-to-body
    :close-on-click-modal="false"
    @close="handleClose"
    @update:model-value="(val: boolean) => emit('update:visible', val)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" :disabled="submitting || formLoading || loadFailed" label-width="100px" v-loading="formLoading">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="订单号" prop="orderNo">
            <el-input v-model="form.orderNo" placeholder="系统自动生成" readonly>
              <template #append>
                <el-button :icon="Search" />
              </template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="供应商" prop="supplierName">
            <SupplierSelector
              v-model="form.supplierId"
              placeholder="请选择供应商"
              :active-only="true"
              :selected-name="form.supplierName"
              :show-code="true"
              @change="handleSupplierChange"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="订单日期" prop="orderDate">
            <el-date-picker
              v-model="form.orderDate"
              type="date"
              placeholder="请选择订单日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="交货日期" prop="expectedDeliveryDate">
            <el-date-picker
              v-model="form.expectedDeliveryDate"
              type="date"
              placeholder="请选择交货日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="订单类型" prop="orderType">
            <el-select v-model="form.orderType" placeholder="请选择" style="width: 100%">
              <el-option
                v-for="dict in PurchaseOrderTypeEnum.items"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="币种" prop="currency">
            <el-select v-model="form.currency" placeholder="请选择" style="width: 100%">
              <el-option
                v-for="dict in CurrencyEnum.items"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="紧急" prop="urgentFlag">
            <el-switch v-model="form.urgentFlag" />
          </el-form-item>
        </el-col>
      </el-row>

      <!-- 紧急原因 -->
      <el-form-item v-if="form.urgentFlag" label="紧急原因" prop="urgentReason">
        <el-input v-model="form.urgentReason" placeholder="请输入紧急原因" maxlength="200" />
      </el-form-item>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="合同号" prop="contractNo">
            <el-input v-model="form.contractNo" placeholder="请输入合同号" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="交货方式" prop="deliveryMethod">
            <el-select
              v-model="form.deliveryMethod"
              placeholder="请选择交货方式"
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="dict in DeliveryMethodEnum.items"
                :key="dict.value"
                :label="dict.label"
                :value="dict.value"
              />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="交货地址" prop="deliveryAddress">
        <el-input
          v-model="form.deliveryAddress"
          placeholder="请输入交货地址"
          maxlength="200"
          :rows="2"
          type="textarea"
        />
      </el-form-item>

      <el-divider content-position="left">订单明细</el-divider>

      <PurchaseItemTable
        :items="form.items" :materials="supplierMaterials" :supplier-id="form.supplierId"
        :material-loading="materialLoading" :units="unitOptions" :currency="form.currency" :disabled="submitting || formLoading || loadFailed"
        @material-change="handleMaterialChange" @search="searchSupplierMaterials"
        @dropdown="handleMaterialDropdownVisible" @pricing="touchPricing" @quantity-change="handlePurchaseQuantityChange"
        @unit-change="handlePurchaseUnitChange" @retry-default="retryDefaults" @save-default="handleSaveDefault" @remove="removeItem" @view="handleView"
      />
      <el-alert v-if="totalsError" :title="totalsError" type="warning" :closable="false" />
      <p class="pricing-hint">金额按计价数量×单价计算；左侧展开可查看尺寸、税率及采购说明。库存基本数量由后端换算，收货按实收核算。</p>
      <el-row>
        <el-col :span="12">
          <el-button type="primary" plain icon="Plus" @click="addItem">添加行</el-button>
        </el-col>
        <el-col :span="12" style="text-align: right">
          <span style="font-size: 16px; font-weight: bold; margin-right: 16px">
            合计金额(不含税)：{{ hasPendingPricing ? '待试算' : totalAmount.toFixed(2) }}
          </span>
          <span style="font-size: 16px; font-weight: bold; margin-right: 16px">
            税额：{{ hasPendingPricing ? '待试算' : totalTax.toFixed(2) }}
          </span>
          <span style="font-size: 16px; font-weight: bold; color: #f56c6c">
            价税合计：{{ hasPendingPricing ? '待试算' : totalWithTax.toFixed(2) }}
          </span>
        </el-col>
      </el-row>

      <el-divider />

      <el-form-item label="备注" prop="remark">
        <el-input
          v-model="form.remark"
          type="textarea"
          placeholder="请输入备注"
          :rows="3"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>
    </el-form>
    <!-- 物料详情弹窗（不再跳路由页） -->
    <MaterialDetailDialog v-model="detailVisible" :material-id="detailMaterialId" />
    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取 消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting" :disabled="loadFailed || formLoading">确 定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { PurchaseOrderTypeEnum, CurrencyEnum, DeliveryMethodEnum } from '@/enums/purchase'
import { normalizePurchaseOrderType } from '@/enums/purchase/order'
import { materialApi } from '@/api/inventory/material'
import {
  addOrder,
  updateOrder,
  generateOrderNo,
  getOrder,
  previewOrderPricing,
} from '@/api/purchase/order'
import type { InventoryMaterial } from '@/types/inventory/material'
import SupplierSelector from '@/components/Selector/SupplierSelector.vue'
import MaterialDetailDialog from '@/components/inventory/MaterialDetailDialog.vue'
import { Search } from '@element-plus/icons-vue'
import PurchaseItemTable from './PurchaseItemTable.vue'
import { getPurchaseMaterialDefault, getPurchaseUnitOptions, savePurchaseMaterialDefault } from '@/api/purchase/orderEntry'
import type { PurchaseEntryItem as OrderItem, PurchaseUnitOption } from '@/api/purchase/orderEntry'

// 订单表单
interface OrderForm {
  orderId?: number
  orderNo: string
  supplierId?: string
  supplierName: string
  orderDate: string
  expectedDeliveryDate: string
  currency: string
  orderType: string
  deliveryMethod: string
  contractNo: string
  deliveryAddress: string
  urgentFlag: boolean
  urgentReason: string
  remark: string
  items: OrderItem[]
}

const props = defineProps<{
  visible: boolean
  orderId?: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success'): void
}>()

const formRef = ref<FormInstance>()
const formLoading = ref(false)
const loadFailed = ref(false)
const unitOptions = ref<PurchaseUnitOption[]>([])
let formEpoch = 0
let closed = false
let disposed = false
const pricingVersions = new WeakMap<OrderItem, number>()
const defaultVersions = new WeakMap<OrderItem, number>()
const submitting = ref(false)
const detailVisible = ref(false)
const detailMaterialId = ref<number | null>(null)
// 当前供应商对应的物料列表
const supplierMaterials = ref<InventoryMaterial[]>([])
const materialLoading = ref(false)
let materialSearchTimer: ReturnType<typeof setTimeout> | undefined
let materialSupplierId: string | undefined
let searchVersion = 0

const createRow = (): OrderItem => ({ materialCode: '', materialName: '', supplierMaterialName: '', materialSpec: '',
  unit: '', purchaseQuantity: 0, quantity: 0, unitPrice: 0, taxRate: 13, amount: 0,
  taxAmount: 0, purchaseRemark: '', inspectionRemark: '' })
const defaultForm: OrderForm = {
  orderNo: '',
  supplierId: undefined,
  supplierName: '',
  orderDate: '',
  expectedDeliveryDate: '',
  currency: 'CNY',
  orderType: 'normal',
  deliveryMethod: '',
  contractNo: '',
  deliveryAddress: '',
  urgentFlag: false,
  urgentReason: '',
  remark: '',
  items: [createRow()],
}

const form = reactive<OrderForm>({ ...defaultForm })

const title = computed(() => (props.orderId ? '修改采购订单' : '新增采购订单'))

const serverTotals = ref<{ amount: number; tax: number; total: number } | null>(null)
const totalsError = ref('')
let totalsGeneration = 0
const hasPendingPricing = computed(() => form.items.some(row => row.materialId) && (!serverTotals.value || !!totalsError.value || form.items.some(row => row.materialId && (row.pricingLoading || row.pricingError || row.defaultLoading || row.defaultError || row.priceQuantity == null))))
const totalAmount = computed(() => serverTotals.value?.amount ?? 0)
const totalTax = computed(() => serverTotals.value?.tax ?? 0)
const totalWithTax = computed(() => serverTotals.value?.total ?? 0)
function acceptTotals(data: any) {
  const values = [data.orderAmount, data.orderTax, data.orderTotalAmount]
  if (values.some(v => v == null || !Number.isFinite(Number(v)))) throw new Error('未返回有效采购合计，请重试')
  serverTotals.value = { amount: Number(values[0]), tax: Number(values[1]), total: Number(values[2]) }
}
async function refreshOrderTotals(): Promise<boolean> {
  if (form.items.length === 1 && serverTotals.value) return true
  if (form.items.some(row => !row.materialId || row.priceQuantity == null || row.pricingError || row.pricingLoading)) return false
  const current = ++totalsGeneration, epoch = formEpoch
  totalsError.value = ''
  try {
    const response = await previewOrderPricing({ items: form.items.map(row => ({ ...row })) })
    if (disposed || closed || epoch !== formEpoch || current !== totalsGeneration) return false
    acceptTotals(response.data)
    return true
  } catch (error) {
    if (epoch === formEpoch && current === totalsGeneration && !closed) {
      serverTotals.value = null
      totalsError.value = error instanceof Error ? error.message : '采购合计计算失败，请重试'
    }
    return false
  }
}

const rules = reactive<FormRules>({
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  orderDate: [{ required: true, message: '请选择订单日期', trigger: 'change' }],
  expectedDeliveryDate: [{ required: true, message: '请选择交货日期', trigger: 'change' }],
  orderType: [{ required: true, message: '请选择订单类型', trigger: 'change' }],
  currency: [{ required: true, message: '请选择币种', trigger: 'change' }],
})

// 监听 visible 变化
watch(
  () => props.visible,
  async (val) => {
    if (val) {
      await initForm()
    }
  },
  { immediate: true }
)

// 初始化表单
async function initForm() {
  formLoading.value = true
  loadFailed.value = false
  closed = false
  const epoch = ++formEpoch
  try {
    const units = await getPurchaseUnitOptions()
    if (epoch !== formEpoch || disposed) return
    unitOptions.value = units.data || []
    // 重置表单
    serverTotals.value = null; totalsError.value = ''; ++totalsGeneration
    Object.assign(form, JSON.parse(JSON.stringify(defaultForm)))
    supplierMaterials.value = []
    materialSupplierId = undefined

    if (props.orderId) {
      // 编辑模式：加载订单数据
      const response = await getOrder(Number(props.orderId))
      const data = response.data
      if (epoch !== formEpoch || disposed) return
      if (data) {
        acceptTotals(data)
        form.orderId = Number(data.orderId)
        form.orderNo = data.orderNo
        form.supplierId = data.supplierId == null ? undefined : String(data.supplierId)
        form.supplierName = data.supplierName
        form.orderDate = data.orderDate
        form.expectedDeliveryDate = data.expectedDeliveryDate
        form.currency = data.currency
        form.orderType = normalizePurchaseOrderType(data.orderType)
        form.deliveryMethod = data.deliveryMethod || ''
        form.contractNo = data.contractNo || ''
        form.deliveryAddress = data.deliveryAddress || ''
        form.urgentFlag = data.urgentFlag
        form.urgentReason = data.urgentReason || ''
        form.remark = data.remark || ''
        form.items = data.items?.length
          ? data.items.map((item: any) => ({
              itemId: item.itemId,
              materialId: item.materialId,
              materialCode: item.materialCode,
              materialName: item.materialName,
              supplierMaterialName: item.supplierMaterialName,
              materialSpec: item.materialSpec || '',
              unit: item.unit,
              purchaseUnit: item.purchaseUnit || item.unit,
              purchaseQuantity: item.purchaseQuantity ?? (item.purchaseUnit === 'ROLL' ? item.rollCount : item.quantity),
              priceQuantity: item.priceQuantity,
              pieceWidthMm: item.pieceWidthMm,
              pieceLengthMm: item.pieceLengthMm,
              purchaseRemark: item.purchaseRemark || '',
              pricingError: item.pricingMessage || '',
              priceUnit: item.priceUnit || item.unit,
              rollCount: item.rollCount,
              rollLengthM: item.rollLengthM,
              rollWidthMm: item.rollWidthMm,
              equivalentUnitPrice: item.equivalentUnitPrice,
              quantity: item.quantity,
              unitPrice: item.unitPrice,
              taxRate: item.taxRate ?? (Number(data.orderTax || 0) === 0 ? 0 : undefined),
              taxAmount: item.taxAmount ?? 0,
              amount: item.amount,
              inspectionRemark: item.inspectionRemark || '',
            }))
          : [...defaultForm.items]

        // 编辑模式也加载供应商物料
        if (data.supplierId) {
          materialSupplierId = String(data.supplierId)
          await loadSupplierMaterials(String(data.supplierId), '', form.items)
          await Promise.all(form.items.map(row => loadDefaults(row, false)))
        }
      }
    } else {
      getOrderNo()
      // 设置默认日期
      const today = new Date()
      form.orderDate = today.toISOString().split('T')[0]
      const nextWeek = new Date(today.getTime() + 7 * 24 * 60 * 60 * 1000)
      form.expectedDeliveryDate = nextWeek.toISOString().split('T')[0]
    }
  } catch (error) {
    console.error('初始化表单失败:', error)
    loadFailed.value = true
    ElMessage.error('初始化表单失败，请关闭后重试')
  } finally {
    if (epoch === formEpoch) formLoading.value = false
  }
}

const getOrderNo = async () => {
  const epoch = formEpoch
  try {
    const response = await generateOrderNo()
    if (epoch === formEpoch && !closed && !disposed) form.orderNo = response.data || ''
  } catch (error) {
    if (epoch === formEpoch && !closed) { form.orderNo = ''; ElMessage.warning('订单编号读取失败，请稍后重试') }
  }
}

// 根据供应商加载物料列表
const loadSupplierMaterials = async (
  supplierId: string,
  keyword = '',
  selectedItems: OrderItem[] = []
) => {
  const epoch = formEpoch
  const requestVersion = ++searchVersion
  materialLoading.value = true
  try {
    const res = await materialApi.search({
      supplierId: Number(supplierId),
      pageNum: 1,
      pageSize: 10,
      keyword: keyword.trim() || undefined,
    })
    if (disposed || closed || epoch !== formEpoch || requestVersion !== searchVersion || String(form.supplierId) !== supplierId) return
    if (res.code === 200 && res.data) {
      const selectedMaterials = selectedItems
        .filter((item) => item.materialId)
        .map(
          (item) =>
            ({
              materialId: Number(item.materialId),
              materialCode: item.materialCode,
              materialName: item.materialName,
              supplierMaterialName: item.supplierMaterialName,
              specification: item.materialSpec,
              unit: item.unit,
            }) as InventoryMaterial
        )
      const merged = [...selectedMaterials, ...(res.data.records || [])]
      supplierMaterials.value = merged.filter(
        (material, index, list) =>
          list.findIndex((item) => item.materialId === material.materialId) === index
      )
    }
  } catch (error) {
    if (requestVersion === searchVersion && epoch === formEpoch && !closed) {
      supplierMaterials.value = []
      ElMessage.warning('物料列表读取失败，请重试搜索')
    }
  } finally {
    if (requestVersion === searchVersion) materialLoading.value = false
  }
}

// 查看详情（弹窗，不跳路由页）
const handleView = (row: any) => {
  detailMaterialId.value = row.materialId
  detailVisible.value = true
}

const searchSupplierMaterials = (keyword: string) => {
  if (!form.supplierId) return
  if (materialSearchTimer) clearTimeout(materialSearchTimer)
  materialSearchTimer = setTimeout(() => {
    loadSupplierMaterials(String(form.supplierId), keyword, form.items)
  }, 300)
}

const handleMaterialDropdownVisible = (visible: boolean) => {
  if (visible && form.supplierId) {
    loadSupplierMaterials(String(form.supplierId), '', form.items)
  }
}

// 供应商、物料变化使在途请求作废；用户自己的详情弹窗保留。
const handleSupplierChange = (supplier: any) => {
  if (submitting.value) return
  const next = supplier?.supplierId ? String(supplier.supplierId) : undefined
  if (materialSupplierId && materialSupplierId !== next) {
    ++formEpoch; ++searchVersion; ++totalsGeneration
    serverTotals.value = null
    const selected = form.items.some(row => row.materialId)
    form.items = [createRow()]
    supplierMaterials.value = []
    if (selected) ElMessage.warning('供应商已变更，请重新选择订单物料')
  }
  materialSupplierId = next
  form.supplierId = next
  form.supplierName = supplier?.supplierName || ''
  if (next) void loadSupplierMaterials(next)
  else supplierMaterials.value = []
}

async function loadDefaults(row: OrderItem, apply: boolean): Promise<void> {
  if (!form.supplierId || !row.materialId) return
  const supplierId = Number(form.supplierId), materialId = row.materialId, epoch = formEpoch
  const version = (defaultVersions.get(row) || 0) + 1
  defaultVersions.set(row, version)
  row.defaultLoading = true
  try {
    const response = await getPurchaseMaterialDefault(supplierId, materialId)
    if (disposed || closed || epoch !== formEpoch || defaultVersions.get(row) !== version || !form.items.includes(row) || row.materialId !== materialId) return
    const data = response.data
    row.defaultError = false
    row.defaultConfigured = data.configured
    row.allowedUnits = data.allowedUnits
    row.archiveWidthMm = data.pieceWidthMm
    row.archiveLengthMm = data.pieceLengthMm
    if (apply) {
      Object.assign(row, { unit: data.unit, purchaseUnit: data.purchaseUnit, priceUnit: data.priceUnit,
        rollWidthMm: data.rollWidthMm, rollLengthM: data.rollLengthM,
        pieceWidthMm: data.pieceWidthMm, pieceLengthMm: data.pieceLengthMm })
      await calculateRowTotal(form.items.indexOf(row))
    }
  } catch (error) {
    if (epoch === formEpoch && defaultVersions.get(row) === version && !closed) {
      row.defaultError = true
      row.pricingError = '采购约定读取失败，请展开明细重试；草稿已保留'
    }
  } finally {
    if (defaultVersions.get(row) === version) row.defaultLoading = false
  }
}

const handleMaterialChange = (materialId: number | undefined, index: number) => {
  const row = form.items[index]
  if (!row || submitting.value) return
  serverTotals.value = null; ++totalsGeneration
  pricingVersions.set(row, (pricingVersions.get(row) || 0) + 1)
  defaultVersions.set(row, (defaultVersions.get(row) || 0) + 1)
  const material = supplierMaterials.value.find(item => item.materialId === materialId)
  const duplicate = material && form.items.some((item, i) => i !== index && item.materialId === materialId)
  const retained = { itemId: row.itemId, taxRate: row.taxRate ?? 13 }
  Object.assign(row, createRow(), retained, { materialId: undefined, purchaseUnit: '', priceUnit: '',
    rollCount: undefined, rollLengthM: undefined, rollWidthMm: undefined,
    pieceWidthMm: undefined, pieceLengthMm: undefined, archiveWidthMm: undefined, archiveLengthMm: undefined, equivalentUnitPrice: null,
    priceQuantity: null, defaultLoading: false, defaultError: false, defaultConfigured: false, allowedUnits: undefined, pricingError: '' })
  if (duplicate) { ElMessage.warning(`物料 "${material.materialName}" (${material.materialCode}) 已在明细中，请勿重复添加`); return }
  if (!material) return
  Object.assign(row, { materialId: material.materialId, materialCode: material.materialCode || '',
    materialName: material.materialName || '', supplierMaterialName: material.supplierMaterialName || '',
    materialSpec: material.specification || '', unit: material.unit || '',
    purchaseUnit: material.unit || '', priceUnit: material.unit || '', rollWidthMm: material.widthMm,
    pieceWidthMm: material.widthMm, pieceLengthMm: material.lengthMm })
  if (form.supplierId) void loadDefaults(row, true)
  else void calculateRowTotal(index)
}

// 所有数量、税额、金额来自同一后端；前端不复制换算公式。
async function calculateRowTotal(index: number, showInputError = false): Promise<boolean> {
  const item = form.items[index]
  if (!item || closed || disposed) return false
  const epoch = formEpoch, version = (pricingVersions.get(item) || 0) + 1
  serverTotals.value = null; totalsError.value = ''; ++totalsGeneration
  pricingVersions.set(item, version)
  item.equivalentUnitPrice = null; item.priceQuantity = null; item.amount = 0; item.taxAmount = 0
  item.pricingError = ''; item.pricingLoading = true
  if (!item.materialId || !item.unit) { item.pricingLoading = false; return false }
  const entered = item.purchaseQuantity ?? (item.purchaseUnit === 'ROLL' ? item.rollCount : item.quantity)
  if (!(Number(entered) > 0)) {
    item.pricingLoading = false
    if (showInputError) item.pricingError = '请填写大于0的采购数量'
    return false
  }
  const current = () => !disposed && !closed && epoch === formEpoch && form.items.includes(item) && pricingVersions.get(item) === version
  try {
    const response = await previewOrderPricing({ items: [{ ...item }] })
    if (!current()) return false
    const priced = response.data.items[0]
    Object.assign(item, { quantity: Number(priced.quantity), amount: Number(priced.amount),
      purchaseUnit: priced.purchaseUnit || item.purchaseUnit, priceUnit: priced.priceUnit || item.priceUnit,
      purchaseQuantity: priced.purchaseQuantity ?? item.purchaseQuantity,
      priceQuantity: priced.priceQuantity ?? null, rollCount: priced.rollCount,
      pieceWidthMm: priced.pieceWidthMm ?? item.pieceWidthMm, pieceLengthMm: priced.pieceLengthMm ?? item.pieceLengthMm,
      rollWidthMm: priced.rollWidthMm ?? item.rollWidthMm, equivalentUnitPrice: priced.equivalentUnitPrice,
      taxAmount: Number(priced.taxAmount || 0) })
    if (form.items.length === 1) acceptTotals(response.data)
    return true
  } catch (error: any) {
    if (current()) item.pricingError = error?.message || '请补齐数量、单位和换算依据后重试'
    return false
  } finally {
    if (current()) {
      item.pricingLoading = false
      if (form.items.length > 1 && !submitting.value) void refreshOrderTotals()
    }
  }
}

const touchPricing = (index: number) => {
  const row = form.items[index]
  if (!row || submitting.value || row.defaultLoading) return
  defaultVersions.set(row, (defaultVersions.get(row) || 0) + 1)
  row.defaultLoading = false
  void calculateRowTotal(index)
}
const handlePurchaseQuantityChange = (index: number) => {
  const row = form.items[index]
  if (!row) return
  // 同单位只透传原始数量，不在浏览器换算；卷数由后端承接。
  if (row.purchaseUnit === row.unit) row.quantity = Number(row.purchaseQuantity || 0)
  touchPricing(index)
}
const handlePurchaseUnitChange = (index: number) => { touchPricing(index) }
const retryDefaults = (index: number) => { const row = form.items[index]; if (row && !submitting.value) void loadDefaults(row, true) }
const handleSaveDefault = async (index: number) => {
  const row = form.items[index]
  if (!form.supplierId || !row?.materialId || !row.purchaseUnit || !row.priceUnit || submitting.value) return
  const epoch = formEpoch, materialId = row.materialId, supplierId = Number(form.supplierId)
  try {
    await savePurchaseMaterialDefault({ supplierId, materialId, purchaseUnit: row.purchaseUnit, priceUnit: row.priceUnit })
    if (epoch !== formEpoch || closed || !form.items.includes(row) || row.materialId !== materialId) return
    row.defaultConfigured = true
    row.defaultError = false
    ElMessage.success('已保存采购默认单位；本单数量和单价不作为默认保存')
  } catch (error) { if (epoch === formEpoch && !closed) ElMessage.error('采购默认单位保存失败，草稿已保留') }
}

// 添加行
const addItem = () => { if (!submitting.value) form.items.push(createRow()) }
const removeItem = (index: number) => {
  if (submitting.value || form.items.length <= 1) return
  const row = form.items[index]
  pricingVersions.set(row, (pricingVersions.get(row) || 0) + 1)
  defaultVersions.set(row, (defaultVersions.get(row) || 0) + 1)
  form.items.splice(index, 1)
  serverTotals.value = null; ++totalsGeneration
  void refreshOrderTotals()
}

const handleSubmit = async () => {
  if (!formRef.value || submitting.value || loadFailed.value || closed) return
  submitting.value = true
  const epoch = formEpoch
  try {
    const valid = await formRef.value.validate().catch(() => false)
    if (!valid) return
    if (form.items.some(item => item.taxRate == null)) {
      ElMessage.warning('历史订单未记录行税率，请展开明细确认税率后保存')
      return
    }
    if (form.items.some(item => item.defaultError)) { ElMessage.warning('采购约定读取失败，请展开明细重新读取后保存'); return }
    if (form.items.some(item => item.defaultLoading)) { ElMessage.warning('采购约定仍在读取，请稍后保存'); return }
    const priced = await Promise.all(form.items.map((_, index) => calculateRowTotal(index, true)))
    if (epoch !== formEpoch || closed || disposed) return
    if (priced.some(ok => !ok)) { ElMessage.warning('采购金额计算未通过，请检查明细提示'); return }
    if (form.items.some(item => !item.materialCode || !(Number(item.quantity) > 0))) { ElMessage.warning('请完善订单明细信息'); return }
    if (!await refreshOrderTotals()) { ElMessage.warning('采购合计未通过，请重试'); return }
    const invalid = form.items.find(item => !(Number(item.unitPrice) > 0))
    if (invalid) { ElMessage.warning(`物料 ${invalid.materialCode || invalid.materialName || ''} 的采购单价必须大于0`); return }
    const submitData = {
      ...form, supplierId: form.supplierId ? Number(form.supplierId) : undefined,
      orderAmount: totalAmount.value, orderTax: totalTax.value, orderTotalAmount: totalWithTax.value,
      items: form.items.map(item => ({
        itemId: item.itemId ? Number(item.itemId) : undefined, materialId: item.materialId,
        materialCode: item.materialCode, materialName: item.materialName, supplierMaterialName: item.supplierMaterialName,
        materialSpec: item.materialSpec, unit: item.unit, purchaseUnit: item.purchaseUnit, priceUnit: item.priceUnit,
        purchaseQuantity: item.purchaseQuantity, rollCount: item.rollCount, rollLengthM: item.rollLengthM,
        rollWidthMm: item.rollWidthMm, pieceWidthMm: item.pieceWidthMm, pieceLengthMm: item.pieceLengthMm,
        quantity: item.quantity, unitPrice: item.unitPrice, taxRate: item.taxRate, amount: item.amount,
        purchaseRemark: item.purchaseRemark, inspectionRemark: item.inspectionRemark,
      })),
    }
    if (props.orderId) await updateOrder(submitData as any)
    else await addOrder(submitData as any)
    if (epoch !== formEpoch || closed || disposed) return
    ElMessage.success(props.orderId ? '修改采购订单成功' : '新增采购订单成功')
    emit('success'); handleClose()
  } catch (error) {
    console.error('采购订单提交失败:', error)
    if (epoch === formEpoch && !closed) ElMessage.error('提交失败，草稿已保留')
  } finally { submitting.value = false }
}

// 关闭
const handleClose = () => {
  closed = true; ++formEpoch; ++searchVersion; ++totalsGeneration
  if (materialSearchTimer) clearTimeout(materialSearchTimer)
  formRef.value?.resetFields?.()
  emit('update:visible', false)
}
onBeforeUnmount(() => {
  disposed = true; ++formEpoch; ++searchVersion; ++totalsGeneration
  if (materialSearchTimer) clearTimeout(materialSearchTimer)
})
</script>

<style lang="scss" scoped>
// 表格内输入框紧凑样式 - 紧贴单元格边框
.el-table {
  :deep(.el-input) {
    .el-input__wrapper {
      box-shadow: none !important;
      border-radius: 0;
      padding: 0 2px;
      background: transparent;
    }
    .el-input__inner {
      padding: 0;
      border: none;
      height: 28px;
    }
  }

  // 数字输入框
  :deep(.el-input-number) {
    .el-input__wrapper {
      padding: 0 2px;
    }
    .el-input-number__decrease,
    .el-input-number__increase {
      display: none;
    }
  }

  // 下拉选择框
  :deep(.el-select) {
    .el-select__wrapper {
      box-shadow: none !important;
      border-radius: 0;
      padding: 0 4px;
      min-height: 28px;
    }
  }

  // 表格单元格内边距压缩
  :deep(.el-table__cell) {
    padding: 4px 2px;
  }
}

.material-option {
  display: flex;
  justify-content: space-between;
  gap: 12px;

  small {
    color: var(--el-text-color-secondary);
  }
}
</style>
