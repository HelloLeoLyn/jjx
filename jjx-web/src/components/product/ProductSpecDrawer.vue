<template>
    <!-- 作业规范详情 -->
    <el-drawer v-model="specVisible" :title="`生产作业规范 - ${detail?.product?.productCode || ''}`" size="82%" destroy-on-close append-to-body :before-close="closeSpec">
      <div v-loading="detailLoading" class="spec-body">
        <ProductSpecHeader
          v-model:version-id="selectedVersionId"
          :detail="detail"
          :product-id="productId"
          :versions="versionOptions"
          :loading="detailLoading"
          @preview="openExport"
        />

        <el-tabs v-model="activeTab" class="spec-tabs" :before-leave="beforeSpecTabLeave">
          <el-tab-pane
            v-for="tab in PRODUCT_SPEC_TABS"
            :key="tab.name"
            :label="tab.label"
            :name="tab.name"
          >
            <!-- 未纳入本次发布：由注册表 notIncluded 统一渲染 -->
            <el-alert
              v-if="selectedVersionId && tab.notIncluded"
              type="warning"
              :closable="false"
              class="tab-tip"
              :title="tab.notIncluded"
            />

            <!-- ① 客供资料 -->
            <template v-if="tab.name === 'customer'">
              <ProductSpecPreview
                v-if="selectedVersionId && productId && activeTab === 'customer'"
                :key="`${productId}-customer-${selectedVersionId}`"
                :product-id="productId"
                :version-id="selectedVersionId"
                :product-code="productCode"
                :product-name="detail?.product?.productName || ''"
                :initial-sections="['customer']"
                paper-only
              />
              <CustomerDocPanel v-else-if="!selectedVersionId && productId && productCode" :product-id="productId" :product-code="productCode" />
            </template>

            <!-- ② 样品：需求单与实物/确认资料统一入口 -->
            <template v-else-if="tab.name === 'sample'">
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
            </template>

            <!-- ④ 领料单（只读执行记录） -->
            <template v-else-if="tab.name === 'pick-order'">
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
            </template>

            <!-- ⑤ 作业规范：产品作业规范 + 印刷规范 -->
            <template v-else-if="tab.name === 'spec'">
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
                <el-alert v-if="versionLoading" type="info" :closable="false" class="tab-tip" title="正在加载版本快照…" />
                <el-alert v-else-if="versionError" type="error" :closable="false" class="tab-tip" :title="versionError" />
                <el-button v-if="!versionLoading && (versionError || specError)" link type="primary" @click="applyVersionSelection">重新加载版本</el-button>
                <el-alert v-if="versionSnapshot" type="info" :closable="false" class="tab-tip"
                  :title="`正在查看发布版本 ${versionSnapshot?.version?.versionNo || ''}（只读快照，不可编辑）；客供资料请查看同名页签，旧版未收录时会明确提示。领料单、油墨、实际检测记录不属于规范快照。`" />
                <el-descriptions v-if="versionSnapshot?.version" :column="2" border size="small">
                  <el-descriptions-item label="版本号">{{ versionSnapshot.version.versionNo }}</el-descriptions-item>
                  <el-descriptions-item label="发布人">{{ versionSnapshot.version.publishedBy || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="发布时间">{{ versionSnapshot.version.publishedAt || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="清单哈希">{{ versionSnapshot.version.manifestHash || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="修订说明" :span="2">{{ versionSnapshot.version.changeSummary || '-' }}</el-descriptions-item>
                </el-descriptions>
                <el-descriptions v-if="versionSnapshot?.spec" :column="1" border size="small" style="margin-top: 10px">
                  <el-descriptions-item label="备注">{{ versionSnapshot.spec.engineeringRequirements || '-' }}</el-descriptions-item>
                  <el-descriptions-item label="刀模位置">{{ dieText(versionSnapshot.spec) }}</el-descriptions-item>
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
                <el-alert v-else-if="specError" type="error" :closable="false" style="margin-top: 10px" :title="specError" />
                <el-alert v-else-if="versionSnapshot && !versionLoading" type="warning" :closable="false" style="margin-top: 10px" title="该版本无规范正文快照" />
                <el-table
                  v-if="versionSnapshot"
                  :data="versionSnapshot?.drawings || []"
                  size="small"
                  border
                  style="margin-top: 10px"
                  empty-text="该版本未纳入工程图 / 样品资料"
                >
                  <el-table-column label="图种 / 类别" width="140">
                    <template #default="{ row }">{{ row.ref || '-' }}</template>
                  </el-table-column>
                  <el-table-column label="文件" min-width="200" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.fileName || ('附件#' + row.id) }}</template>
                  </el-table-column>
                  <el-table-column label="SHA-256" min-width="240" show-overflow-tooltip>
                    <template #default="{ row }">{{ row.hash || '-' }}</template>
                  </el-table-column>
                </el-table>
              </div>
              <el-alert v-if="selectedVersionId && versionSnapshot?.routingError" :title="versionSnapshot.routingError" type="warning" :closable="false" class="tab-tip" />
              <ProductPrintSpecPanel
                v-if="selectedVersionId && versionSnapshot?.spec && productId && activeTab === 'spec'"
                :key="`${productId}-${selectedVersionId}-print`"
                :product-id="productId"
                :work-spec="versionSnapshot.spec"
                :routing-items="versionSnapshot.routingItems"
                read-only
              />
            </template>

            <!-- ⑦ 油墨调配记录表 -->
            <template v-else-if="tab.name === 'ink'">
              <el-alert type="info" :closable="false" class="tab-tip"
                title="油墨调配记录表：按参考样张录入机种/色号/色样、原料名称、重量(g)、百分比、油墨罐号、调墨员、调墨日期、溶剂。" />
              <InkMixingForm v-if="productId && activeTab === 'ink'" :key="`${productId}-ink`" />
            </template>

            <!-- ⑧ 工程图集 -->
            <template v-else-if="tab.name === 'atlas'">
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
            </template>

            <!-- ⑨ 分色检查表 -->
            <template v-else-if="tab.name === 'color'">
              <ProductColorCheckPanel
                v-if="productId && activeTab === 'color'"
                :key="`${productId}-color`"
                :product-id="productId"
                @busy="colorCheckBusy = $event"
                @updated="refreshSpecSources"
              />
            </template>
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <!-- 在线纸张预览：目录选择与打印共用同一版式 -->
    <el-dialog v-model="exportVisible" title="产品电子文档集 · 在线预览" width="94%" top="4vh" append-to-body destroy-on-close>
      <ProductSpecPreview
        v-if="exportVisible && productId"
        :product-id="productId"
        :version-id="selectedVersionId || undefined"
        :product-code="productCode"
        :product-name="detail?.product?.productName || ''"
      />
    </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { getFullProduct } from '@/api/product'
import ProductFileLibrary from '@/components/product/ProductFileLibrary.vue'
import ProductSpecHeader from '@/components/product/ProductSpecHeader.vue'
import CustomerDocPanel from '@/components/product/customer-doc/CustomerDocPanel.vue'
import EngineeringDrawingLibrary from '@/views/engineering/drawing/components/EngineeringDrawingLibrary.vue'
import { ENGINEERING_DRAWING_VISIBLE_CATEGORIES } from '@/components/product/productFileCategories'
import ProductSpecPreview from '@/views/engineering/product-spec/components/ProductSpecPreview.vue'
import ProductWorkSpecPanel from '@/views/engineering/product-spec/components/ProductWorkSpecPanel.vue'
import ProductPrintSpecPanel from '@/views/engineering/product-spec/components/ProductPrintSpecPanel.vue'
import ProductColorCheckPanel from '@/views/engineering/product-spec/components/ProductColorCheckPanel.vue'
import InkMixingForm from '@/views/engineering/product-spec/components/InkMixingForm.vue'
import { PRODUCT_SPEC_TABS } from '@/components/product/productSpecTabs'
import type { ProductFullVO } from '@/types/product'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { outboundApi } from '@/api/inventory/outbound'
import { SampleOrderStatus } from '@/enums/sales'
import { OutboundTypeEnum, OutboundOrderStatusEnum } from '@/enums/inventory'
import type { SampleOrder } from '@/types/sales/sampleOrder'
import type { ProductPickRow } from '@/types/inventory/outbound'
import { productWorkSpecApi, normalizeWorkSpec, embossFields } from '@/api/product/workSpec'
import { productRouteApi } from '@/api/product/routing'

function dieText(spec: any): string {
  const values = Object.values(spec?.dieLocation || {}).filter(Boolean)
  return values.length ? values.join('；') : (spec?.dieLocationText || '-')
}
const specVisible = ref(false)
const drawingBusy = ref(false)
const workSpecBusy = ref(false)
const colorCheckBusy = ref(false)
function closeSpec(done: () => void) {
  if (drawingBusy.value || workSpecBusy.value || colorCheckBusy.value) { ElMessage.warning('请先完成或关闭工程规范、印刷备注、分色检查表、图纸维护窗口'); return }
  requestVersion++
  resetVersionSnapshot()
  exportVisible.value = false
  done()
}
function beforeSpecTabLeave() {
  if (workSpecBusy.value || colorCheckBusy.value) { ElMessage.warning('请先完成或关闭规范编辑窗口'); return false }
  return true
}
const detailLoading = ref(false)
const detail = ref<ProductFullVO | null>(null)
const activeTab = ref('customer')

const productCode = computed(() => detail.value?.product?.productCode || '')

const productId = computed(() => Number(detail.value?.product?.productId || 0))
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

async function open(id: number, versionId?: number) {
  if (!Number.isFinite(id) || id <= 0) return
  if (drawingBusy.value || workSpecBusy.value || colorCheckBusy.value) {
    ElMessage.warning('请先完成或关闭工程规范、印刷备注、分色检查表、图纸维护窗口')
    return
  }
  const version = ++requestVersion
  activeTab.value = 'customer'
  selectedVersionId.value = 0
  resetVersionSnapshot()
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
      await loadVersionOptions()
      // 带版本号打开：定位到该发布版本只读（dev-20261011-029）
      if (versionId && version === requestVersion && specVisible.value) {
        selectedVersionId.value = versionId
        activeTab.value = 'spec'
      }
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
const versionSnapshot = ref<{ version: any; spec: any; drawings: any[]; routingItems: any[]; routingError: string } | null>(null)
const versionLoading = ref(false)
const versionError = ref('')
const specError = ref('')
let snapshotRequest = 0

function resetVersionSnapshot() {
  snapshotRequest++
  versionSnapshot.value = null
  versionLoading.value = false
  versionError.value = ''
  specError.value = ''
}

async function loadVersionOptions() {
  versionOptions.value = []
  selectedVersionId.value = 0
  resetVersionSnapshot()
  if (!productId.value) return
  try {
    const res: any = await productWorkSpecApi.listVersions(productId.value)
    versionOptions.value = res?.data || []
  } catch {
    versionOptions.value = []
  }
}

async function applyVersionSelection() {
  resetVersionSnapshot()
  if (!selectedVersionId.value) return
  versionLoading.value = true
  const request = snapshotRequest
  const requestedVersionId = selectedVersionId.value
  try {
    const res: any = await productWorkSpecApi.getVersion(requestedVersionId)
    if (request !== snapshotRequest) return
    if (res?.code !== 200 || !res.data?.version || !Array.isArray(res.data.items)) {
      throw new Error(res?.msg || '版本详情返回不完整')
    }
    const items = res.data.items
    // 新发布版本：统一读完整快照（dev-20261013-011），不再分别请求 SPEC_JSON/路线/图纸。
    const docsetItem = items.find((i: any) => i.resourceType === 'DOCSET_JSON')
    if (docsetItem) {
      let snapshot: any = null
      try { snapshot = JSON.parse(docsetItem.snapshotJson || '{}') } catch { snapshot = null }
      if (!snapshot || typeof snapshot !== 'object' || Array.isArray(snapshot)) throw new Error('该版本完整快照解析失败')
      versionSnapshot.value = {
        version: res.data.version,
        spec: normalizeWorkSpec(snapshot.spec || {}),
        drawings: (snapshot.files || []).map((f: any) => ({ id: f.attachmentId, ref: f.category || '', hash: f.sha256 || '', fileName: f.fileName || null })),
        routingItems: snapshot.routing?.items || [],
        routingError: '',
      }
      versionLoading.value = false
      return
    }
    const specItem = items.find((i: any) => i.resourceType === 'SPEC_JSON')
    let spec: any = null
    if (specItem?.snapshotJson != null && specItem.snapshotJson !== '') {
      try {
        const parsed = JSON.parse(specItem.snapshotJson)
        if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error('规范正文应为 JSON 对象')
        spec = normalizeWorkSpec(parsed)
      } catch (error) {
        console.error('解析发布版本规范正文失败', { versionId: requestedVersionId, error })
        specError.value = '该版本规范正文解析失败，请重试或联系管理员'
      }
    }
    // 正文已就绪，不等待独立的工艺路线接口；路线失败不影响正文。
    const snapshot = { version: res.data.version, spec, drawings: drawingItems(items), routingItems: [] as any[], routingError: '' }
    versionSnapshot.value = snapshot
    versionLoading.value = false
    const routingItem = items.find((item: any) => item.resourceType === 'ROUTING')
    try {
      const routingId = JSON.parse(routingItem?.snapshotJson || '{}').routingId
      if (routingId) {
        const routing = await productRouteApi.getProductRouteInfo(Number(routingId))
        if (request !== snapshotRequest) return
        versionSnapshot.value!.routingItems = routing.data?.items || []
      }
    } catch (error) {
      if (request !== snapshotRequest) return
      console.error('读取发布版本印刷工序失败', error)
      versionSnapshot.value!.routingError = '该版本的印刷工序读取失败，请重新选择版本重试'
    }
  } catch (e) {
    if (request !== snapshotRequest) return
    console.error('加载发布版本快照失败', { versionId: requestedVersionId, error: e })
    versionError.value = `版本快照加载失败：${e instanceof Error ? e.message : '请重试'}`
  } finally {
    if (request === snapshotRequest) versionLoading.value = false
  }
}

watch(selectedVersionId, () => { void applyVersionSelection() }, { flush: 'sync' })

/** 该版本的 DRAWING 条目（工程图 / 样品资料；多条目；dev-20261011-034）。 */
function drawingItems(items: any[]) {
  return items
    .filter((i) => i?.resourceType === 'DRAWING')
    .map((i) => {
      let fileName: string | null = null
      try {
        fileName = JSON.parse(i.snapshotJson || '{}')?.fileName ?? null
      } catch {
        fileName = null
      }
      return { id: i.sourceId, ref: i.operationRef || '', hash: i.contentHash || '', fileName }
    })
}

function openExport() {
  exportVisible.value = true
}

onBeforeUnmount(() => { requestVersion++; resetVersionSnapshot() })
defineExpose({ open })
</script>

<style scoped>
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
