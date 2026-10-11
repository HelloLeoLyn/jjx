<template>
    <!-- 作业规范详情 -->
    <el-drawer v-model="specVisible" :title="`生产作业规范 - ${detail?.product?.productCode || ''}`" size="82%" destroy-on-close append-to-body :before-close="closeSpec">
      <div v-loading="detailLoading" class="spec-body">
        <div class="spec-head">
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="产品编码">{{ detail?.product?.productCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="产品名称">{{ detail?.product?.productName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="客户">{{ (detail?.product as any)?.customerName || '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前BOM">{{ (detail?.product as any)?.currentBomVersion || '-' }}</el-descriptions-item>
            <el-descriptions-item label="当前路线">{{ (detail?.product as any)?.currentRoutingVersion || '-' }}</el-descriptions-item>
            <el-descriptions-item label="规格">{{ (detail?.product as any)?.specification || '-' }}</el-descriptions-item>
          </el-descriptions>
          <div class="spec-head-actions">
            <el-select
              v-model="selectedVersionId"
              size="small"
              style="width: 150px; margin-right: 8px"
              title="查看发布版本快照（只读）"
            >
              <el-option label="工作态" :value="0" />
              <el-option v-for="v in versionOptions" :key="v.id" :label="v.versionNo" :value="v.id" />
            </el-select>
            <el-button type="primary" icon="View" :disabled="detailLoading || !productId" @click="openExport">预览 / 打印文档集</el-button>
          </div>
        </div>

        <el-tabs v-model="activeTab" class="spec-tabs" :before-leave="beforeSpecTabLeave">
          <!-- ① 客供资料 -->
          <el-tab-pane label="客供资料" name="customer">
            <el-alert v-if="selectedVersionId" type="warning" :closable="false" class="tab-tip"
              title="未纳入本次发布：客供资料仅反映当前工作态，不随发布版本快照。"
              description="该页签内容未固化进任何发布版本；查看历史版本时请以「产品作业规范」页签快照为准。" />
            <CustomerDocPanel v-if="productId && productCode" :product-id="productId" :product-code="productCode" />
          </el-tab-pane>

          <!-- ② 样品：需求单与实物/确认资料统一入口 -->
          <el-tab-pane label="样品" name="sample">
            <el-alert v-if="selectedVersionId" type="warning" :closable="false" class="tab-tip"
              title="未纳入本次发布：样品需求单 / 样品资料仅为当前执行记录，不随发布版本快照。" />
            <h3 class="sample-section-title">样品需求单</h3>
            <el-table :data="sampleOrders" v-loading="sampleLoading" border size="small">
              <el-table-column label="样品单号" prop="orderNo" min-width="150" show-overflow-tooltip />
              <el-table-column label="轮次" width="70">
                <template #default="{ row }">{{ row.sampleRound ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="120">
                <template #default="{ row }">
                  <el-tag size="small" v-bind="SampleOrderStatus.getTagProps(row.sampleStatus)">{{ SampleOrderStatus.getLabel(row.sampleStatus) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="打样数量" width="90">
                <template #default="{ row }">{{ row.sampleQty ?? '-' }}</template>
              </el-table-column>
              <el-table-column label="送样" min-width="170">
                <template #default="{ row }">
                  <span v-if="row.sampleSendDate || row.sampleTrackingNo">{{ row.sampleSendDate || '-' }}<span v-if="row.sampleTrackingNo"> · {{ row.sampleTrackingNo }}</span></span>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="客户确认" min-width="170">
                <template #default="{ row }">
                  <span v-if="row.sampleConfirmDate || row.sampleClientName">{{ row.sampleConfirmDate || '-' }}<span v-if="row.sampleClientName"> · {{ row.sampleClientName }}</span></span>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="120" fixed="right">
                <template #default="{ row }">
                  <el-button link type="primary" @click="printSampleRequisition(row)">打印 QR-065</el-button>
                </template>
              </el-table-column>
              <template #empty><el-empty description="该产品暂无样品需求单" :image-size="60" /></template>
            </el-table>
            <h3 class="sample-section-title">样品资料</h3>
            <el-alert type="info" :closable="false" class="tab-tip"
              title="样品实物照片 + 客户确认样品（工程上传）；与「客供资料」同一处理位置归集展示。" />
            <ProductFileLibrary
              v-if="productCode"
              :product-code="productCode"
              :categories="['样品照片', '客户确认样品']"
              upload-perm="product:edit"
              delete-perm="product:delete"
            />
          </el-tab-pane>

          <!-- ④ 领料单（只读执行记录） -->
          <el-tab-pane label="领料单" name="pick-order">
            <el-alert v-if="selectedVersionId" type="warning" :closable="false" class="tab-tip"
              title="未纳入本次发布：领料单为执行记录，不随发布版本快照。" />
            <el-alert type="info" :closable="false" class="tab-tip"
              title="只读执行记录：打样领料（来源样品单）与生产领料（来源工单）；不纳入发布版本。" />
            <el-table :data="pickRecords" v-loading="pickLoading" border size="small">
              <el-table-column label="出库单号" prop="outboundNo" min-width="150" show-overflow-tooltip />
              <el-table-column label="类型" width="110">
                <template #default="{ row }">
                  <el-tag size="small" v-bind="OutboundTypeEnum.getTagProps(row.outboundType)">{{ OutboundTypeEnum.getLabel(row.outboundType) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="来源单号" prop="sourceNo" min-width="150" show-overflow-tooltip />
              <el-table-column label="出库日期" prop="outboundDate" width="120" />
              <el-table-column label="数量" prop="totalQuantity" width="90" />
              <el-table-column label="状态" width="110">
                <template #default="{ row }">
                  <el-tag size="small" v-bind="OutboundOrderStatusEnum.getTagProps(row.orderStatus)">{{ OutboundOrderStatusEnum.getLabel(row.orderStatus) }}</el-tag>
                </template>
              </el-table-column>
              <template #empty><el-empty description="该产品暂无领料记录" :image-size="60" /></template>
            </el-table>
          </el-tab-pane>

          <!-- ⑤ 产品作业规范 -->
          <el-tab-pane label="产品作业规范" name="spec">
            <ProductWorkSpecPanel
              v-if="!selectedVersionId && productId && activeTab === 'spec'"
              :key="productId"
              :product-id="productId"
              :product-code="productCode"
              :product-name="detail?.product?.productName || ''"
              @busy="workSpecBusy = $event"
              @updated="refreshSpecSources"
            />
            <div v-else-if="selectedVersionId" v-loading="versionLoading" class="version-snapshot">
              <el-alert type="info" :closable="false" class="tab-tip"
                :title="`正在查看发布版本 ${versionSnapshot?.version?.versionNo || ''}（只读快照，不可编辑）；未纳入本次发布：客供资料、样品、领料单、油墨、实际检测记录。`" />
              <el-descriptions v-if="versionSnapshot?.version" :column="2" border size="small">
                <el-descriptions-item label="版本号">{{ versionSnapshot.version.versionNo }}</el-descriptions-item>
                <el-descriptions-item label="发布人">{{ versionSnapshot.version.publishedBy || '-' }}</el-descriptions-item>
                <el-descriptions-item label="发布时间">{{ versionSnapshot.version.publishedAt || '-' }}</el-descriptions-item>
                <el-descriptions-item label="清单哈希">{{ versionSnapshot.version.manifestHash || '-' }}</el-descriptions-item>
                <el-descriptions-item label="修订说明" :span="2">{{ versionSnapshot.version.changeSummary || '-' }}</el-descriptions-item>
              </el-descriptions>
              <el-descriptions v-if="versionSnapshot?.spec" :column="1" border size="small" style="margin-top: 10px">
                <el-descriptions-item label="加工要求">{{ versionSnapshot.spec.engineeringRequirements || '-' }}</el-descriptions-item>
                <el-descriptions-item label="刀模位置">{{ versionSnapshot.spec.dieLocation || '-' }}</el-descriptions-item>
                <el-descriptions-item label="结构图ID">{{ versionSnapshot.spec.structureFileId ?? '-' }}</el-descriptions-item>
                <el-descriptions-item label="凹凸条件">
                  <div v-for="f in embossFields" :key="f.key">{{ f.label }}：{{ versionSnapshot.spec.emboss[f.key] || '-' }}</div>
                </el-descriptions-item>
                <el-descriptions-item label="变更">
                  <template v-if="versionSnapshot.spec.changes.length">
                    <div v-for="(c, i) in versionSnapshot.spec.changes" :key="i">
                      {{ c.date || '-' }}　{{ c.text || '-' }}<span v-if="c.reason">（{{ c.reason }}）</span>
                    </div>
                  </template>
                  <span v-else>-</span>
                </el-descriptions-item>
              </el-descriptions>
              <el-alert v-else type="warning" :closable="false" style="margin-top: 10px" title="该版本无规范正文快照" />
            </div>
          </el-tab-pane>

          <!-- ⑥ 印刷规范 -->
          <el-tab-pane label="印刷规范" name="print">
            <ProductPrintSpecPanel
              v-if="productId && activeTab === 'print'"
              :key="`${productId}-print`"
              :product-id="productId"
              @busy="printSpecBusy = $event"
              @updated="refreshSpecSources"
            />
          </el-tab-pane>

          <!-- ⑦ 油墨调配记录表（位置：印刷规范 与 工程图集 之间） -->
          <el-tab-pane label="油墨调配记录表" name="ink">
            <el-alert v-if="selectedVersionId" type="warning" :closable="false" class="tab-tip"
              title="未纳入本次发布：油墨调配记录为执行记录，不随发布版本快照。" />
            <el-alert type="info" :closable="false" class="tab-tip"
              title="油墨调配记录表：按参考样张录入机种/色号/色样、原料名称、重量(g)、百分比、油墨罐号、调墨员、调墨日期、溶剂。" />
            <InkMixingForm v-if="productId && activeTab === 'ink'" :key="`${productId}-ink`" />
          </el-tab-pane>

          <!-- ⑧ 工程图集 -->
          <el-tab-pane label="工程图集" name="atlas">
            <el-alert type="info" :closable="false" class="tab-tip"
              title="按图种归集工程图纸：结构图、外形尺寸图、面板图、线路图、组装图、包装图、印刷指导图等；上传时选择对应图种和版本。" />
            <EngineeringDrawingLibrary
              v-if="productCode"
              :key="`${productId}-atlas`"
              :active="activeTab === 'atlas'"
              :product-code="productCode"
              :product-name="detail?.product?.productName || ''"
              :categories="ENGINEERING_DRAWING_VISIBLE_CATEGORIES"
              @busy="drawingBusy = $event"
            />
          </el-tab-pane>

          <!-- ⑨ 分色检查表 -->
          <el-tab-pane label="分色检查表" name="color">
            <ProductColorCheckPanel
              v-if="productId && activeTab === 'color'"
              :key="`${productId}-color`"
              :product-id="productId"
              @busy="colorCheckBusy = $event"
              @updated="refreshSpecSources"
            />
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <!-- 在线纸张预览：目录选择与打印共用同一版式 -->
    <el-dialog v-model="exportVisible" title="产品电子文档集 · 在线预览" width="94%" top="4vh" append-to-body destroy-on-close>
      <ProductSpecPreview v-if="exportVisible && productId" :product-id="productId" />
    </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { getFullProduct } from '@/api/product'
import ProductFileLibrary from '@/components/product/ProductFileLibrary.vue'
import CustomerDocPanel from '@/components/product/customer-doc/CustomerDocPanel.vue'
import EngineeringDrawingLibrary from '@/views/engineering/drawing/components/EngineeringDrawingLibrary.vue'
import { ENGINEERING_DRAWING_VISIBLE_CATEGORIES } from '@/components/product/productFileCategories'
import ProductSpecPreview from '@/views/engineering/product-spec/components/ProductSpecPreview.vue'
import ProductWorkSpecPanel from '@/views/engineering/product-spec/components/ProductWorkSpecPanel.vue'
import ProductPrintSpecPanel from '@/views/engineering/product-spec/components/ProductPrintSpecPanel.vue'
import ProductColorCheckPanel from '@/views/engineering/product-spec/components/ProductColorCheckPanel.vue'
import InkMixingForm from '@/views/engineering/product-spec/components/InkMixingForm.vue'
import type { ProductFullVO } from '@/types/product'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { outboundApi } from '@/api/inventory/outbound'
import { SampleOrderStatus } from '@/enums/sales'
import { OutboundTypeEnum, OutboundOrderStatusEnum } from '@/enums/inventory'
import type { SampleOrder } from '@/types/sales/sampleOrder'
import type { ProductPickRow } from '@/types/inventory/outbound'
import { productWorkSpecApi, normalizeWorkSpec, embossFields } from '@/api/product/workSpec'


const specVisible = ref(false)
const drawingBusy = ref(false)
const workSpecBusy = ref(false)
const printSpecBusy = ref(false)
const colorCheckBusy = ref(false)
function closeSpec(done: () => void) {
  if (drawingBusy.value || workSpecBusy.value || printSpecBusy.value || colorCheckBusy.value) { ElMessage.warning('请先完成或关闭工程规范、印刷备注、分色检查表、图纸维护窗口'); return }
  requestVersion++
  exportVisible.value = false
  done()
}
function beforeSpecTabLeave() {
  if (workSpecBusy.value || printSpecBusy.value || colorCheckBusy.value) { ElMessage.warning('请先完成或关闭规范编辑窗口'); return false }
  return true
}
const detailLoading = ref(false)
const detail = ref<ProductFullVO | null>(null)
const activeTab = ref('customer')

const productCode = computed(() => detail.value?.product?.productCode || '')

const productId = computed(() => Number((detail.value?.product as any)?.productId || 0))
let requestVersion = 0
async function refreshSpecSources() {
  if (!productId.value) return
  const version = ++requestVersion
  const id = productId.value
  try {
    const response = await getFullProduct(id)
    if (version === requestVersion && specVisible.value) detail.value = response.data
  } catch (e) {
    if (version === requestVersion && specVisible.value) ElMessage.error(e instanceof Error ? e.message : '产品资料刷新失败')
  }
}

async function open(id: number) {
  if (!Number.isFinite(id) || id <= 0) return
  if (drawingBusy.value || workSpecBusy.value || printSpecBusy.value || colorCheckBusy.value) {
    ElMessage.warning('请先完成或关闭工程规范、印刷备注、分色检查表、图纸维护窗口')
    return
  }
  const version = ++requestVersion
  activeTab.value = 'customer'
  sampleOrders.value = []
  pickRecords.value = []
  detail.value = null
  exportVisible.value = false
  specVisible.value = true
  detailLoading.value = true
  try {
    const response = await getFullProduct(id)
    if (version === requestVersion && specVisible.value) {
      detail.value = response.data
      void loadVersionOptions()
    }
  } catch (e) {
    if (version === requestVersion && specVisible.value) ElMessage.error(e instanceof Error ? e.message : '加载作业规范失败')
  } finally {
    if (version === requestVersion) detailLoading.value = false
  }
}

const exportVisible = ref(false)

// ② 样品需求单 / ④ 领料单：只读执行记录（dev-20261011-026）
const sampleOrders = ref<SampleOrder[]>([])
const sampleLoading = ref(false)
const pickRecords = ref<ProductPickRow[]>([])
const pickLoading = ref(false)

async function loadSampleOrders() {
  if (!productId.value) { sampleOrders.value = []; return }
  sampleLoading.value = true
  try {
    const response: any = await sampleOrderApi.list({ productId: productId.value })
    sampleOrders.value = response?.data || []
  } catch (e) {
    sampleOrders.value = []
    ElMessage.error(e instanceof Error ? e.message : '样品需求单加载失败')
  } finally {
    sampleLoading.value = false
  }
}

async function loadPickRecords() {
  if (!productId.value) { pickRecords.value = []; return }
  pickLoading.value = true
  try {
    const response: any = await outboundApi.listPickByProduct(productId.value)
    pickRecords.value = response?.data || []
  } catch (e) {
    pickRecords.value = []
    ElMessage.error(e instanceof Error ? e.message : '领料记录加载失败')
  } finally {
    pickLoading.value = false
  }
}

function printSampleRequisition(row: SampleOrder) {
  window.open(`/print/sample-order/${row.orderId}?layout=requisition`, '_blank', 'noopener,noreferrer')
}

watch(activeTab, (tab) => {
  if (tab === 'sample') void loadSampleOrders()
  else if (tab === 'pick-order') void loadPickRecords()
})

// ⑤ 产品作业规范：版本切换（只读快照；dev-20261011-027）
const versionOptions = ref<any[]>([])
const selectedVersionId = ref(0)
const versionSnapshot = ref<{ version: any; spec: any } | null>(null)
const versionLoading = ref(false)

async function loadVersionOptions() {
  versionOptions.value = []
  selectedVersionId.value = 0
  versionSnapshot.value = null
  if (!productId.value) return
  try {
    const res: any = await productWorkSpecApi.listVersions(productId.value)
    versionOptions.value = res?.data || []
  } catch {
    versionOptions.value = []
  }
}

async function applyVersionSelection() {
  versionSnapshot.value = null
  if (!selectedVersionId.value) return
  versionLoading.value = true
  try {
    const res: any = await productWorkSpecApi.getVersion(selectedVersionId.value)
    if (res?.code === 200) {
      const items = res?.data?.items || []
      const specItem = items.find((i: any) => i.resourceType === 'SPEC_JSON')
      let spec: any = null
      if (specItem?.snapshotJson) {
        try { spec = normalizeWorkSpec(JSON.parse(specItem.snapshotJson)) } catch { spec = null }
      }
      versionSnapshot.value = { version: res?.data?.version, spec }
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '版本快照加载失败')
  } finally {
    versionLoading.value = false
  }
}

watch(selectedVersionId, () => { void applyVersionSelection() })

function openExport() {
  exportVisible.value = true
}

onBeforeUnmount(() => { requestVersion++ })
defineExpose({ open })
</script>

<style scoped>
.spec-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.spec-head-actions {
  flex-shrink: 0;
}
.spec-tabs {
  margin-top: 10px;
}
.tab-tip {
  margin-bottom: 10px;
}
.sample-section-title {
  margin: 16px 0 10px;
  font-size: 14px;
  color: var(--el-text-color-primary);
}
.sec-note {
  color: #909399;
  font-size: 12px;
  margin: 6px 0 2px;
}
</style>
