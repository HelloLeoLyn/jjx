<template>
  <el-dialog
    :title="title"
    :model-value="props.visible"
    width="1200px"
    append-to-body
    :close-on-click-modal="false"
    :close-on-press-escape="!busy"
    :show-close="!busy"
    :before-close="beforeClose"
    @close="handleClose"
    @update:model-value="(val: boolean) => emit('update:visible', val)"
  >
    <el-form label-width="100px" v-loading="loading" :disabled="busy">
      <el-form-item label="订单号">
        <el-input :model-value="orderNo" disabled />
      </el-form-item>

      <div class="detail-head">
        <el-divider content-position="left">收货明细</el-divider>
        <div class="quick-actions">
          <el-button link type="primary" @click="fillAll">全部填满</el-button>
          <el-button link @click="clearAll">全部清空</el-button>
        </div>
      </div>

      <el-table ref="receiptTable" :data="form.items" row-key="itemId" border style="width: 100%; margin-bottom: 8px">
        <el-table-column type="expand" width="40">
          <template #default="{ row: parent }">
            <PurchaseProcessingReceive v-if="parent.processingPlan" :key="`${receiptDraftId}-${parent.itemId}`" :ref="el=>setProcessingRef(parent.itemId,el)" v-model="parent.processingGroups" :item-id="parent.itemId" :disabled="busy" />
            <div v-else-if="isRollPlanned(parent)">
              <el-table :data="parent.rolls || []" size="small" border>
                <el-table-column label="卷号" width="220">
                  <template #default="{ row: roll, $index }">{{ roll.rollNo || `自动生成 R${$index + 1}` }}</template>
                </el-table-column>
                <el-table-column label="卷宽(mm)" width="150">
                  <template #default="{ row: roll }">
                    <el-input-number v-model="roll.rollWidthMm" :min="0.0001" :precision="4" :controls="false" @change="syncRollQuantity(parent)" />
                  </template>
                </el-table-column>
                <el-table-column label="卷长(m)" width="150">
                  <template #default="{ row: roll }">
                    <el-input-number v-model="roll.rollLengthM" :min="0.0001" :precision="4" :controls="false" @change="syncRollQuantity(parent)" />
                  </template>
                </el-table-column>
                <el-table-column v-if="parent.measurement?.policy.mode === MeasurementModeEnum.MEASURED_WEIGHT.value" label="实际净重(KG)" width="170">
                  <template #default="{ row: roll }"><el-input-number v-model="roll.actualWeightKg" :min="0.0001" :precision="4" :controls="false" @change="syncRollQuantity(parent)" /></template>
                </el-table-column>
                <el-table-column label="面积(㎡)">
                  <template #default="{ row: roll }">{{ parent.measurement ? (roll.inventoryArea ?? '—') : rollArea(roll.rollWidthMm, roll.rollLengthM) }}</template>
                </el-table-column>
              </el-table>
              <div class="doc-tip">
                <span v-if="parent.previewError" class="qty-err">{{ parent.previewError }}</span>
                <el-button link type="primary" @click="parent.rolls?.push({rollWidthMm:parent.rollWidthMm,rollLengthM:parent.rollLengthM});syncRollQuantity(parent)">增加一卷</el-button>
                <el-button link @click="parent.rolls?.pop();syncRollQuantity(parent)">移除末卷</el-button>
                按采购行 {{ parent.rollCount }} 卷 / {{ parent.rollWidthMm }}mm × {{ parent.rollLengthM }}m 生成；卷号提交后自动生成，卷宽/卷长可按实际修改。
              </div>
            </div>
            <div v-else>
              <span>普通采购行，按本次实际收货数量登记。</span>
              <template v-if="parent.measurement?.policy.mode === MeasurementModeEnum.MEASURED_WEIGHT.value">
                <span>本次实物数量（{{ parent.measurement.policy.physicalUnit }}）</span>
                <el-input-number v-model="parent.measurementActual.physicalQuantity" :min="0" :precision="0" />
                <span>本次实际净重(KG)</span><el-input-number v-model="parent.measurementActual.actualWeightKg" :min="0" :precision="4" />
              </template>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="物料编码" prop="materialCode" width="160" />
        <el-table-column label="物料名称" prop="materialName" min-width="160" />
        <el-table-column label="规格型号" prop="materialSpec" width="120" />
        <el-table-column label="单位" prop="unit" width="60" />
        <el-table-column label="订购数量" width="90" align="right">
          <template #default="{ row }">{{ fmt(row.quantity) }}</template>
        </el-table-column>
        <!-- 修②：已收数量只读展示，不再与「本次收货」共用同一字段 -->
        <el-table-column label="已收 / 已处置" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.receivedQuantity) }}</template>
        </el-table-column>
        <el-table-column label="未收 / 待处置" width="110" align="right">
          <template #default="{ row }">{{ fmt(remainOf(row)) }}</template>
        </el-table-column>
        <!-- ①②：默认填满「未收」；超出不夹断而是标红并拦截提交 -->
        <el-table-column label="本次收货" width="180">
          <template #default="{ row, $index }">
            <el-button v-if="row.processingPlan" link type="primary" @click="expandProcessing(row)">登记交货 / 报废</el-button>
            <div v-else class="qty-cell" :class="{ 'is-over': overOf(row) > 0 }">
              <el-input-number
                :ref="(el: any) => setQtyRef(el, $index)"
                v-model="row.receiveQuantity"
                :disabled="isRollPlanned(row)"
                :min="0"
                :precision="row.measurement ? 4 : 2"
                :step="1"
                controls-position="right"
                size="small"
                style="width: 100%"
                @keyup.enter="focusNext($index)"
              />
              <div v-if="overOf(row) > 0" class="qty-err">超出未收 {{ fmt(overOf(row)) }}</div>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" align="center">
          <template #default="{ row }">
            <el-button v-if="!row.processingPlan" link type="primary" size="small" @click="fillRow(row)">填满</el-button>
            <el-button v-if="!row.processingPlan" link size="small" @click="clearRow(row)">清零</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="detail-summary">
        <span>普通采购可收合计 <b>{{ fmt(totalRemain) }}</b></span>
        <span>普通采购本次合计 <b :class="{ 'is-err': totalOver > 0 }">{{ fmt(totalInput) }}</b></span>
        <span v-if="totalOver > 0" class="is-err">超出 <b>{{ fmt(totalOver) }}</b>，请调整后再提交</span>
        <span v-else>提交后剩余 <b>{{ fmt(totalRemain - totalInput) }}</b></span>
      </div>

      <el-divider content-position="left">收货票据</el-divider>

      <el-form-item label="上传票据">
        <AttachmentCenter
          v-if="props.visible && receiptDraftId && canUploadDoc"
          :key="receiptDraftId"
          ref="receiptAttachments"
          source-type="purchase_receipt"
          :draft-id="receiptDraftId"
          variant="upload-only"
          title="本次收货票据"
          :auto-bind="false"
          :actions="['remove']"
          :locked="submitting"
          :lock-reason="submitting ? '提交中，请稍候' : ''"
          :max-size-m-b="10"
          :accept="['.jpg', '.jpeg', '.png', '.gif', '.bmp', '.webp']"
          default-document-type="DELIVERY_NOTE"
          view-perm="purchase:receipt:doc:view"
          edit-perm="purchase:receipt:doc:add"
        />
        <span v-else class="doc-tip">无票据上传权限</span>
        <div class="doc-tip">支持 JPG/PNG/GIF/BMP/WebP，单张不超过 10MB；确认收货后关联本次入库单。</div>
        <div v-for="file in pendingFiles" :key="file.attachmentId">
          <el-button link type="primary" @click="previewAttachment(file.attachmentId)">预览 {{ file.fileName }}</el-button>
        </div>
      </el-form-item>

      <el-form-item v-if="canViewDoc && historyFiles.length" label="历史票据">
        <div>
          <div v-for="file in historyFiles" :key="file.refId">
            <span>{{ file.inboundNo }}：</span>
            <el-button link type="primary" @click="previewAttachment(file.attachmentId)">{{ file.fileName }}</el-button>
            <el-link :href="attachmentApi.downloadUrl(file.attachmentId)" target="_blank">下载</el-link>
          </div>
        </div>
      </el-form-item>

      <!-- 收货结果：把「生成了哪张入库单」明确告诉用户，并引到 IQC（检验在质量域，不在这里录） -->
      <el-alert
        v-if="lastInboundNo"
        class="result-alert"
        type="success"
        :closable="false"
        show-icon
        :title="`收货成功，已生成入库单 ${lastInboundNo}（待来料检验）`"
      >
        <template #default>
          <span class="doc-tip">下一步：由质量部门在「质量管理 → 来料检验」录入并判定（库存未增加，仓库确认入库后才可用）。</span>
          <el-button v-if="canGotoIqc" link type="primary" @click="gotoIqc">去来料检验</el-button>
        </template>
      </el-alert>

      <el-image-viewer v-if="previewVisible" :url-list="[previewUrl]" @close="previewVisible = false" />
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button :disabled="busy" @click="handleClose">关 闭</el-button>
        <el-tooltip :disabled="!hasOver" content="有明细超出未收数量，请先调整" placement="top">
          <span>
            <el-button type="primary" :loading="submitting" :disabled="!canSubmit" @click="handleSubmit">
              确认收货（{{ filledCount }}行）
            </el-button>
          </span>
        </el-tooltip>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, nextTick, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import PurchaseProcessingReceive from './PurchaseProcessingReceive.vue'
import type { ProcessingReceiptGroup } from '@/api/purchase/orderEntry'
import AttachmentCenter from '@/components/attachment/AttachmentCenter.vue'
import { attachmentApi } from '@/api/system/attachment'
import { useUserStore } from '@/store/modules/user'
import {
  getReceiptsByOrder,
  confirmBatchReceive,
  getReceiptDocRefs,
} from '@/api/purchase/receipt'

/**
 * 采购收货弹窗（共享组件 · dev-20260923-004 方案 A + dev-20260923-005 收货小优化）
 *
 * 采购订单页与采购收货页共用同一实现：
 *  - 提交走**收货域**端点 `POST /purchase/receipt/confirm-batch`（`purchase:receipt:add`）；
 *  - 明细来自 `GET /purchase/receipt/order/{orderId}`（`purchase:receipt:view`）；
 *  - 已收数量只读、本次收货独立字段（缺陷②）；票据随提交落库（缺陷④）；
 *  - 检验不在本弹窗录入：收货生成的入库单进入 质量管理 → 来料检验（IQC），这里只给入库单号并可跳转。
 *
 * 收货小优化（2026-09-23 用户拍板）：
 *  ① 打开即**默认填满**每行「未收」数量；
 *  ② 超出未收**不夹断**，改为红框 + 红字 + 行内差额提示，并禁用提交（后端超收校验仍在，防绕过）；
 *  ④ 顶部「全部填满 / 全部清空」；⑤ 行内「填满 / 清零」；
 *  ⑥ 提交按钮带合计 + 二次确认；⑦ 回车跳下一行；⑧ 出现超量时自动把该行滚入视野。
 */
import { materialMeasurementApi } from '@/api/inventory/measurement'
import type { MeasurementPolicy, MeasurementResult, MeasurementInput } from '@/types/inventory/measurement'
import { MeasurementModeEnum } from '@/enums/inventory/MeasurementEnum'
interface ReceiveItem {
  measurement?: { policy: MeasurementPolicy; result: MeasurementResult }
  measurementActual?: Partial<MeasurementInput>
  previewVersion?: number
  previewError?: string
  itemId: number
  materialCode: string
  materialName: string
  materialSpec?: string
  unit?: string
  quantity: number
  receivedQuantity: number
  processingPlan?: string
  processingGroups?: ProcessingReceiptGroup[]
  receiveQuantity: number | undefined
  /** 按卷采购：采购行卷数、卷宽、卷长 */
  rollCount?: number
  rollWidthMm?: number
  rollLengthM?: number
  /** 本次收货卷明细（卷号留空由后端自动生成） */
  rolls?: RollRow[]
}

interface RollRow {
  inventoryArea?: number | null
  actualWeightKg?: number
  rollNo?: string
  rollWidthMm: number
  rollLengthM: number
}

const props = defineProps<{
  visible: boolean
  orderId?: number
  orderNo: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success', payload?: { inboundNo?: string }): void
  (e: 'goto-iqc', inboundNo: string): void
}>()

const userStore = useUserStore()
const canUploadDoc = computed(() => userStore.hasPermission('purchase:receipt:doc:add'))
const canViewDoc = computed(() => userStore.hasPermission('purchase:receipt:doc:view'))
/** 来料检验在质量域，跳转按钮只在有 IQC 读权限时出现（否则点进去是 403） */
const canGotoIqc = computed(() => userStore.hasPermission('quality:lot:view'))

const receiptAttachments = ref<InstanceType<typeof AttachmentCenter>>()
const receiptDraftId = ref('')
const pendingFiles = computed(() => receiptAttachments.value?.pendingEntries || [])
const preparing=ref(false)
const busy = computed(() => preparing.value || submitting.value || !!receiptAttachments.value?.uploading)
const historyFiles = ref<{ refId: number; attachmentId: number; fileName: string; inboundNo: string }[]>([])
function newDraft() {
  receiptAttachments.value?.clearPending()
  receiptDraftId.value = globalThis.crypto?.randomUUID?.() || `receipt-${Date.now()}-${Math.random().toString(36).slice(2)}`
}
const loading = ref(false)
const submitting = ref(false)
const lastInboundNo = ref('')

const form = reactive({ items: [] as ReceiveItem[] })
const receiptTable=ref<any>()
const processingRefs=new Map<number,InstanceType<typeof PurchaseProcessingReceive>>()
function setProcessingRef(id:number,el:unknown){if(el)processingRefs.set(id,el as InstanceType<typeof PurchaseProcessingReceive>);else processingRefs.delete(id)}
function expandProcessing(row:ReceiveItem){receiptTable.value?.toggleRowExpansion(row,true)}
function processingFilled(row:ReceiveItem){return !!row.processingGroups?.some(g=>g.outputs.some(o=>Number(o.quantity)>0)||Number(g.lossArea)>0)}

const previewVisible = ref(false)
const previewUrl = ref('')

const title = computed(() => `收货 - ${props.orderNo}`)

function fmt(v?: number | string | null): string {
  const n = Number(v ?? 0)
  if (!Number.isFinite(n)) return '0'
  return String(Math.round(n * 10000) / 10000)
}

function remainOf(row: ReceiveItem): number {
  return Number((Number(row.quantity || 0) - Number(row.receivedQuantity || 0)).toFixed(row.measurement ? 4 : 2))
}

/** 超出量（>0 即超收） */
function overOf(row: ReceiveItem): number {
  return Number((Number(row.receiveQuantity || 0) - remainOf(row)).toFixed(row.measurement ? 4 : 2))
}

const totalRemain = computed(() => Number(form.items.filter(it=>!it.processingPlan).reduce((s, it) => s + Math.max(0, remainOf(it)), 0).toFixed(4)))
const totalInput = computed(() => Number(form.items.filter(it=>!it.processingPlan).reduce((s, it) => s + Number(it.receiveQuantity || 0), 0).toFixed(4)))
const totalOver = computed(() => Number(form.items.reduce((s, it) => s + Math.max(0, overOf(it)), 0).toFixed(4)))
const hasOver = computed(() => totalOver.value > 0)
const filledCount = computed(() => form.items.filter((it) => it.processingPlan ? processingFilled(it) : Number(it.receiveQuantity || 0) > 0).length)
const canSubmit = computed(() => !busy.value && !loading.value && filledCount.value > 0 && !hasOver.value)

/** ⑧ 第一个超量行下标（-1=没有），用于自动滚入视野 */
const firstOverIndex = computed(() => form.items.findIndex((it) => overOf(it) > 0))

// ⑦ 输入框引用（回车跳下一行）
const qtyRefs = ref<any[]>([])
function setQtyRef(el: any, index: number) {
  qtyRefs.value[index] = el
}
function focusNext(index: number) {
  const next = qtyRefs.value[index + 1]
  next?.focus?.()
}
function round2(v: number): number {
  return Math.round(v * 100) / 100
}

/** 按卷采购行：采购行带卷数、卷宽、卷长 */
function isRollPlanned(row: ReceiveItem): boolean {
  return row.measurement?.policy.physicalUnit === 'ROLL' || (Number(row.rollCount || 0) > 0 && !!row.rollWidthMm && !!row.rollLengthM)
}

/** 面积口径与后端一致：卷宽(mm) × 卷长(m) ÷ 1000 */
function rollArea(width?: number, length?: number): number {
  if (!width || !length) return 0
  return Math.round((Number(width) * Number(length) / 1000) * 10000) / 10000
}

/** 按未收数量生成卷明细；最后一卷按差额收口 */
function rebuildRolls(row: ReceiveItem, fill = true) {
  if (!isRollPlanned(row)) { row.rolls = []; return }
  if(row.measurement) {
    const count=fill?Number(row.rollCount||0):0
    row.rolls=Array.from({length:count},()=>({rollWidthMm:Number(row.rollWidthMm),rollLengthM:Number(row.rollLengthM)}))
    void syncRollQuantity(row);return
  }
  const width = Number(row.rollWidthMm)
  const per = rollArea(width, Number(row.rollLengthM))
  const target = fill ? Math.max(0, remainOf(row)) : 0
  if (!(per > 0) || target <= 0) { row.rolls = []; row.receiveQuantity = 0; return }
  const count = Math.max(1, Math.ceil(target / per - 1e-9))
  let left = target
  const rolls: RollRow[] = []
  for (let i = 0; i < count && left > 0; i++) {
    const area = round2(Math.min(per, left))
    const length = Math.round((area * 1000 / width) * 10000) / 10000
    rolls.push({ rollWidthMm: width, rollLengthM: length })
    left = Math.round((left - area) * 10000) / 10000
  }
  row.rolls = rolls
  syncRollQuantity(row)
}

/** 卷明细面积合计回写本次收货数量 */
async function syncRollQuantity(row: ReceiveItem) {
  const version=(row.previewVersion||0)+1;row.previewVersion=version
  if (!row.rolls?.length) {row.receiveQuantity=0;return}
  if(row.measurement) {
    row.previewError=''
    try {
      const quantities=await Promise.all(row.rolls.map(async roll=>{
        // 实物单位是米时每卷的实物数量是「该卷米数」；实物单位是卷时才是 1 卷。
        const physicalQuantity=row.measurement!.policy.physicalUnit==='M'?Number(roll.rollLengthM):1
        const result=(await materialMeasurementApi.preview({policy:row.measurement!.policy,actual:{
          ...row.measurement!.result.actual,physicalQuantity,widthMm:roll.rollWidthMm,rollLengthM:roll.rollLengthM,
          actualWeightKg:roll.actualWeightKg,
        }})).data
        if (!result) throw new Error('计量预览未返回结果')
        if(row.previewVersion===version)roll.inventoryArea=result.inventoryArea
        return row.unit===result.physicalUnit?result.physicalQuantity:result.priceQuantity
      }))
      if(row.previewVersion===version)row.receiveQuantity=Number(quantities.reduce((a,b)=>a+Number(b),0).toFixed(4))
    } catch(error:any) {if(row.previewVersion===version){row.receiveQuantity=undefined;row.previewError=error?.message||'请补齐每卷实际计量参数'}}
    return
  }
  row.receiveQuantity = round2(row.rolls.reduce((sum, roll) => sum + rollArea(roll.rollWidthMm, roll.rollLengthM), 0))
}

function fillRow(row: ReceiveItem) {
  if (row.processingPlan) return
  if (isRollPlanned(row)) { rebuildRolls(row, true); return }
  row.receiveQuantity = Math.max(0, remainOf(row))
}
function clearRow(row: ReceiveItem) {
  if (isRollPlanned(row)) row.rolls = []
  row.receiveQuantity = 0
}
function fillAll() {
  form.items.forEach((it) => fillRow(it))
}
function clearAll() {
  form.items.forEach((it) => clearRow(it))
}

watch(
  () => props.visible,
  async (val) => {
    if (val) {
      newDraft()
      lastInboundNo.value = ''
      await loadItems()
      await loadImages()
    }
  }
)

const loadItems = async () => {
  if (!props.orderId) return
  loading.value = true
  qtyRefs.value = []
  try {
    const response: any = await getReceiptsByOrder(Number(props.orderId))
    const items = response.data || []
    form.items = items.map((item: any) => {
      const quantity = Number(item.quantity || 0)
      const receivedQuantity = Number(item.receivedQuantity || 0)
        const remain = Number((quantity - receivedQuantity).toFixed(item.measurementSnapshot ? 4 : 2))
      return {
        measurement: item.measurementSnapshot ? JSON.parse(item.measurementSnapshot) : undefined,
        measurementActual: item.measurementSnapshot ? { ...JSON.parse(item.measurementSnapshot).result.actual, physicalQuantity:undefined,actualWeightKg:undefined } : undefined,
        itemId: Number(item.itemId),
        processingPlan: item.processingPlan, processingGroups: [],
        materialCode: item.materialCode,
        materialName: item.materialName,
        materialSpec: item.materialSpec || '',
        unit: item.unit,
        quantity,
        receivedQuantity,
        rollCount: Number(item.rollCount || 0),
        rollWidthMm: item.rollWidthMm == null ? undefined : Number(item.rollWidthMm),
        rollLengthM: item.rollLengthM == null ? undefined : Number(item.rollLengthM),
        rolls: [],
        // ① 默认填满未收（未收<=0 的行保持 0，不需要收）
        receiveQuantity: item.processingPlan ? 0 : remain > 0 ? remain : 0,
      }
    })
    form.items.forEach((row) => { if (isRollPlanned(row)) rebuildRolls(row, true) })
  } catch (error) {
    console.error('加载收货明细失败:', error)
    ElMessage.error('加载收货明细失败')
  } finally {
    loading.value = false
  }
}

/** 历史票据只按附件中心引用展示（不再扫描磁盘旧目录，dev-20261117-023）：一套账、有引用、可追溯。 */
const loadImages = async () => {
  historyFiles.value = []
  if (!props.orderId || !canViewDoc.value) return
  try {
    const res = await getReceiptDocRefs(Number(props.orderId))
    historyFiles.value = res.data || []
  } catch {
    ElMessage.warning('历史票据加载失败，请重新打开查看')
  }
}

function previewAttachment(id: number) {
  previewLegacy(attachmentApi.downloadUrl(id))
}
function previewLegacy(url: string) {
  previewUrl.value = url
  previewVisible.value = true
}

/** ⑧ 一旦出现超量行，把它滚入视野（nearest：尽量少跳动） */
watch(firstOverIndex, async (idx) => {
  if (idx < 0) return
  await nextTick()
  const el = qtyRefs.value[idx]?.$el || qtyRefs.value[idx]
  el?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' })
})

const handleSubmit = async () => {
  if (!props.orderId || !canSubmit.value) return
  preparing.value=true
  try {
    const receiveItems: {itemId:number;receivedQuantity?:number;measurementActual?:Partial<MeasurementInput>;processingGroups?:ProcessingReceiptGroup[];rolls?:{rollWidthMm:number;rollLengthM:number;actualWeightKg?:number}[]}[]=[]
    let hasInbound = false
    for(const item of form.items){
      if(item.processingPlan){
        if(!processingFilled(item))continue
        const component=processingRefs.get(item.itemId)
        if(!component){ElMessage.warning('请展开并核对实际交付');return}
        const validated=await component.validate()
        if(!validated)return
        receiveItems.push({itemId:item.itemId,receivedQuantity:validated.receivedQuantity,processingGroups:validated.groups})
        if(validated.inboundItemCount !== 0) hasInbound = true
      }else if(isRollPlanned(item)){
        const rolls=(item.rolls||[]).filter(r=>Number(r.rollWidthMm)>0 && Number(r.rollLengthM)>0)
        if(!rolls.length){ElMessage.warning(`请为按卷采购行 ${item.materialCode} 生成卷明细`);return}
        await syncRollQuantity(item)
        if(!(Number(item.receiveQuantity)>0)){ElMessage.warning(`按卷采购行 ${item.materialCode} 的卷面积合计必须大于0`);return}
        hasInbound = true
        receiveItems.push({itemId:Number(item.itemId),receivedQuantity:item.measurement ? undefined : Number(Number(item.receiveQuantity).toFixed(4)),
          rolls:rolls.map(r=>({rollWidthMm:Number(r.rollWidthMm),rollLengthM:Number(r.rollLengthM),actualWeightKg:r.actualWeightKg}))})
      }else if(Number(item.receiveQuantity)>0){
        hasInbound = true
        receiveItems.push({itemId:Number(item.itemId),measurementActual:item.measurementActual,receivedQuantity:Number(Number(item.receiveQuantity).toFixed(4))})
      }
    }
    if (!receiveItems.length) {ElMessage.warning('请至少输入一个物料的收货数量');return}
    const missingTypeCount = receiptAttachments.value?.getMissingTypeCount?.() ?? 0
    if (missingTypeCount > 0) {ElMessage.warning(`有 ${missingTypeCount} 个票据未选择资料类型，请先选择后再提交`);return}
    if(!hasInbound && pendingFiles.value.length){ElMessage.warning('本次仅确认报废，没有入库单，请移除收货票据后提交');return}
    try {
      await ElMessageBox.confirm(
        `确认本次交货或报废？涉及 ${receiveItems.length} 行采购明细（订单 ${props.orderNo}）。${hasInbound ? '实际交货生成待确认入库单，库存尚未增加；报废只登记损耗。' : '本次仅登记报废损耗，不生成入库单。'}`,
        '收货确认', { type: 'warning', confirmButtonText: '确认收货', cancelButtonText: '再改改' }
      )
    } catch {return}
    submitting.value=true
    const response: any = await confirmBatchReceive(Number(props.orderId), receiveItems, {
      receiptRequestKey: receiptDraftId.value, attachmentIds: pendingFiles.value.map(file => file.attachmentId),
    })
    const inboundNo = String(response.data?.inboundNo || '')
    newDraft();lastInboundNo.value = inboundNo
    ElMessage.success(response.data?.disposalOnly ? '报废确认成功，本次未生成入库单' : inboundNo ? `收货成功，已生成入库单 ${inboundNo}（待来料检验）` : '收货成功')
    await loadItems();await loadImages();emit('success', { inboundNo })
  } catch (error: any) {
    console.error('收货失败:', error);ElMessage.error(error?.message || '收货失败')
  } finally {submitting.value=false;preparing.value=false}
}

const gotoIqc = () => {
  if (!lastInboundNo.value) return
  emit('goto-iqc', lastInboundNo.value)
}

const beforeClose = (done: () => void) => { if (!busy.value) done() }
const handleClose = () => {
  if (busy.value) return
  receiptAttachments.value?.clearPending()
  receiptDraftId.value = ''
  historyFiles.value = []
  previewVisible.value = false
  form.items = []
  lastInboundNo.value = ''
  qtyRefs.value = []
  emit('update:visible', false)
}
</script>

<style scoped>
.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}
.detail-head {
  display: flex;
  align-items: center;
  gap: 12px;
}
.detail-head :deep(.el-divider--horizontal) {
  flex: 1;
  margin: 16px 0;
}
.quick-actions {
  white-space: nowrap;
}
.qty-cell.is-over :deep(.el-input__wrapper) {
  box-shadow: 0 0 0 1px #f56c6c inset;
}
.qty-cell.is-over :deep(.el-input__inner) {
  color: #f56c6c;
}
.qty-err {
  margin-top: 2px;
  font-size: 12px;
  line-height: 16px;
  color: #f56c6c;
}
.detail-summary {
  display: flex;
  gap: 20px;
  margin: 0 0 8px;
  font-size: 13px;
  color: #606266;
}
.detail-summary b {
  color: #303133;
}
.detail-summary .is-err,
.detail-summary b.is-err {
  color: #f56c6c;
}
.result-alert {
  margin-top: 8px;
}
.doc-tip {
  color: #909399;
  font-size: 12px;
}
.doc-tag {
  margin-right: 6px;
}
</style>
