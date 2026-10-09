<template>
  <div class="product-spec-page">
    <!-- 搜索 -->
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item label="产品编码">
          <el-input v-model="query.productCode" placeholder="产品编码" clearable style="width: 160px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input v-model="query.productName" placeholder="产品名称" clearable style="width: 160px" @keyup.enter="load" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="load">查询</el-button>
          <el-button @click="reset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表：一个产品一行 -->
    <el-card shadow="never">
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column label="产品编码" prop="productCode" min-width="150" />
        <el-table-column label="产品名称" prop="productName" min-width="180" show-overflow-tooltip />
        <el-table-column label="客户" prop="customerName" min-width="150" show-overflow-tooltip />
        <el-table-column label="当前BOM版本" width="120" align="center">
          <template #default="{ row }">{{ (row as any).currentBomVersion || '-' }}</template>
        </el-table-column>
        <el-table-column label="当前路线版本" width="120" align="center">
          <template #default="{ row }">{{ (row as any).currentRoutingVersion || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSpec(row)">作业规范</el-button>
          </template>
        </el-table-column>
      </el-table>
      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="query.current"
        v-model:limit="query.pageSize"
        @pagination="load"
      />
    </el-card>

    <!-- 作业规范详情 -->
    <el-drawer v-model="specVisible" :title="`产品作业规范 - ${detail?.product?.productCode || ''}`" size="82%" destroy-on-close>
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
            <el-button type="primary" @click="openExport">生成文档集</el-button>
          </div>
        </div>

        <el-tabs v-model="activeTab" class="spec-tabs">
          <!-- ① 客供资料（+样品需求） -->
          <el-tab-pane label="客供资料" name="customer">
            <el-alert type="info" :closable="false" class="tab-tip"
              title="客供资料 = 询价/报价里上传的客户附件（引用，只读）；产品文件库·客供稿 为工程侧上传。" />
            <el-divider content-position="left">询价 / 报价附件（引用，只读）</el-divider>
            <el-table :data="customerDocs" size="small" border>
              <el-table-column label="来源" width="90" align="center">
                <template #default="{ row }">{{ row.sourceType || row.sourcetype || '-' }}</template>
              </el-table-column>
              <el-table-column label="单据号" width="170">
                <template #default="{ row }">{{ row.sourceNo || row.sourceno || '-' }}</template>
              </el-table-column>
              <el-table-column label="文件名" min-width="220" show-overflow-tooltip>
                <template #default="{ row }">
                  <el-link type="primary" @click="openDoc(row)">{{ row.file_name || row.fileName }}</el-link>
                </template>
              </el-table-column>
              <el-table-column label="上传人" width="110">
                <template #default="{ row }">{{ row.create_by || row.createBy || '-' }}</template>
              </el-table-column>
              <el-table-column label="上传时间" width="160">
                <template #default="{ row }">{{ row.create_time || row.createTime || '' }}</template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!customerDocs.length" description="暂无询价/报价附件" :image-size="50" />
            <el-divider content-position="left">产品文件库 · 客供稿（工程上传）</el-divider>
            <ProductFileLibrary
              v-if="productCode"
              :product-code="productCode"
              :categories="['客供稿']"
              upload-perm="product:edit"
              delete-perm="product:delete"
            />
          </el-tab-pane>

          <!-- ② 产品作业规范 -->
          <el-tab-pane label="产品作业规范" name="spec">
            <el-divider content-position="left">BOM（引用当前版本）</el-divider>
            <el-table :data="bomItems" size="small" border>
              <el-table-column label="物料编码" prop="materialCode" width="140" />
              <el-table-column label="物料名称" prop="materialName" min-width="160" show-overflow-tooltip />
              <el-table-column label="用量" prop="quantity" width="90" align="right" />
              <el-table-column label="单位" prop="unit" width="70" align="center" />
              <el-table-column label="损耗%" prop="lossRate" width="80" align="right" />
            </el-table>
            <div class="sec-note">BOM：{{ detail?.bom?.bomCode || '未配置' }} / {{ (detail?.bom as any)?.bomVersion || '-' }}</div>

            <el-divider content-position="left">工艺路线（引用当前版本）</el-divider>
            <el-table :data="routingItems" size="small" border>
              <el-table-column label="工序顺序" prop="processOrder" width="90" align="center" />
              <el-table-column label="工序名称" prop="processName" min-width="160" show-overflow-tooltip />
              <el-table-column label="类别" prop="processCategory" width="100" align="center" />
              <el-table-column label="说明" prop="description" min-width="180" show-overflow-tooltip />
            </el-table>
            <div class="sec-note">工艺路线：{{ detail?.routing?.routingCode || '未配置' }} / {{ (detail?.routing as any)?.routingVersion || '-' }}</div>

            <el-divider content-position="left">刀模（库位）</el-divider>
            <div style="margin-bottom: 6px">
              <el-button type="primary" plain size="small" @click="openDieLink">关联刀模</el-button>
            </div>
            <el-table :data="productDies" size="small" border>
              <el-table-column label="刀模编号" width="140">
                <template #default="{ row }">{{ row.resourceNo || row.dieNo || row.die_no || '-' }}</template>
              </el-table-column>
              <el-table-column label="刀模名称" min-width="150" show-overflow-tooltip>
                <template #default="{ row }">{{ row.resourceName || row.dieName || row.die_name || '-' }}</template>
              </el-table-column>
              <el-table-column label="规格" min-width="130" show-overflow-tooltip>
                <template #default="{ row }">{{ row.specification || '-' }}</template>
              </el-table-column>
              <el-table-column label="库位" width="110">
                <template #default="{ row }">{{ row.location || '-' }}</template>
              </el-table-column>
              <el-table-column label="状态" width="110" align="center">
                <template #default="{ row }">{{ row.status || '-' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="90" align="center">
                <template #default="{ row }">
                  <el-button link type="danger" @click="unlinkDie(row)">解除</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!productDies.length" description="未关联刀模" :image-size="50" />

            <el-divider content-position="left">凹凸条件</el-divider>
            <el-empty description="凹凸条件录入：待工程侧字段确定后接入" :image-size="50" />
          </el-tab-pane>

          <!-- ③ 印刷规范 -->
          <el-tab-pane label="印刷规范" name="print">
            <el-alert type="info" :closable="false" class="tab-tip"
              title="印刷工序/油墨/网板为引用项；印刷指导图在此上传。油墨调配记录表字段待定（等样张）。" />
            <ProductFileLibrary
              v-if="productCode"
              :product-code="productCode"
              :categories="['印刷指导图']"
              upload-perm="product:edit"
              delete-perm="product:delete"
            />
          </el-tab-pane>

          <!-- ④ 产品图集 -->
          <el-tab-pane label="产品图集" name="atlas">
            <el-alert type="info" :closable="false" class="tab-tip"
              title="工程上传的产品图纸（结构图/外形/面板/线路/菲林/网版/刀模…），标签可选；不强制分类。" />
            <ProductFileLibrary
              v-if="productCode"
              :product-code="productCode"
              :categories="['结构图', '印刷指导图', '产品图集', '确认图', '菲林', '模具']"
              upload-perm="product:edit"
              delete-perm="product:delete"
            />
          </el-tab-pane>

          <!-- ⑤ 分色检查表 -->
          <el-tab-pane label="分色检查表" name="color">
            <el-empty description="规范分色检查表：待样张字段确定后，纳入「质量记录模板」体系（工程录入，暂不强制）" :image-size="60" />
          </el-tab-pane>

          <!-- ⑥ 样品 -->
          <el-tab-pane label="样品" name="sample">
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

          <!-- ⑦ 打样领料单 -->
          <el-tab-pane label="打样领料单" name="pick">
            <el-alert type="info" :closable="false" class="tab-tip"
              title="打样领料：按样品单生成领料单（复用生产领料链路，确认发料后扣库存）；打印 JJX-QR-031（抬头「打样领料单」）。" />
            <el-divider content-position="left">该产品的样品单</el-divider>
            <el-table :data="sampleOrders" size="small" border>
              <el-table-column label="样品单号" prop="orderNo" min-width="150" />
              <el-table-column label="产品编码" prop="productCode" width="140" />
              <el-table-column label="打样数量" prop="sampleQty" width="100" align="right" />
              <el-table-column label="轮次" prop="sampleRound" width="70" align="center" />
              <el-table-column label="操作" width="130" align="center">
                <template #default="{ row }">
                  <el-button link type="primary" @click="openSamplePick(row)">生成领料单</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!sampleOrders.length" description="暂无该产品的样品单" :image-size="50" />

            <el-divider content-position="left">已有打样领料单</el-divider>
            <el-table :data="samplePicks" size="small" border>
              <el-table-column label="领料单号" prop="outboundNo" min-width="170" />
              <el-table-column label="数量" prop="totalQuantity" width="90" align="right" />
              <el-table-column label="状态" width="100" align="center">
                <template #default="{ row }">{{ outboundStatusText(row.orderStatus) }}</template>
              </el-table-column>
              <el-table-column label="创建时间" prop="createTime" width="160" />
              <el-table-column label="操作" width="100" align="center">
                <template #default="{ row }">
                  <el-button link type="primary" @click="printPick(row)">打印</el-button>
                </template>
              </el-table-column>
            </el-table>
            <el-empty v-if="!samplePicks.length" description="暂无打样领料单" :image-size="50" />
          </el-tab-pane>
        </el-tabs>
      </div>
    </el-drawer>

    <!-- 生成文档集：预览 + 勾选 -->
    <el-dialog v-model="exportVisible" title="生成文档集（预览并勾选要哪些）" width="620px" append-to-body>
      <el-checkbox-group v-model="exportSelected" class="export-list">
        <el-checkbox v-for="s in exportSections" :key="s.key" :value="s.key" :label="s.key" class="export-item">
          {{ s.label }}
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="exportVisible = false">取消</el-button>
        <el-button type="primary" @click="doPrint">打印</el-button>
      </template>
    </el-dialog>
    <!-- 关联刀模 -->
    <el-dialog v-model="dieVisible" title="关联刀模" width="760px" append-to-body>
      <div style="margin-bottom: 8px">
        <el-input v-model="dieKeyword" placeholder="刀模编号/名称/用途/库位" clearable style="width: 260px" @keyup.enter="searchDies" />
        <el-button style="margin-left: 8px" @click="searchDies">查询</el-button>
      </div>
      <el-table v-loading="dieLoading" :data="dieOptions" size="small" border height="340">
        <el-table-column label="刀模编号" width="140">
          <template #default="{ row }">{{ row.die_no || row.dieNo }}-{{ row.die_name || row.dieName }}</template>
        </el-table-column>
        <el-table-column label="名称" min-width="140" show-overflow-tooltip>
          <template #default="{ row }">{{ row.die_name || row.dieName || '-' }}</template>
        </el-table-column>
        <el-table-column label="库位" width="110">
          <template #default="{ row }">{{ row.location || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">{{ row.status || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="linkDie(row)">关联</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 打样领料：预览 + 改数量 -->
    <el-dialog v-model="pickVisible" :title="`打样领料 - ${pickSample?.orderNo || ''}`" width="780px" append-to-body>
      <el-table :data="pickRows" size="small" border>
        <el-table-column label="物料名称" prop="materialName" min-width="160" show-overflow-tooltip />
        <el-table-column label="匹配物料" width="130" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.materialId" size="small" type="success">{{ row.materialCode }}</el-tag>
            <el-tag v-else size="small" type="danger">未匹配</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="BOM需求" prop="quantity" width="90" align="right" />
        <el-table-column label="已领" width="80" align="right">
          <template #default="{ row }">{{ row.issuedQuantity ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="可用" width="80" align="right">
          <template #default="{ row }">{{ row.availableQuantity ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="本次领料" width="140">
          <template #default="{ row }">
            <el-input-number v-model="row.pickQty" :min="0" :disabled="!row.materialId" size="small" style="width: 120px" />
          </template>
        </el-table-column>
      </el-table>
      <el-alert v-if="unmatchedCount > 0" type="warning" :closable="false" style="margin-top: 8px"
        :title="`有 ${unmatchedCount} 条样品BOM物料未匹配到物料档案，暂不参与领料（可先在物料档案补建后再来）`" />
      <template #footer>
        <el-button @click="pickVisible = false">取消</el-button>
        <el-button type="primary" :loading="pickSubmitting" @click="submitSamplePick">生成领料单</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listProductPage, getFullProduct } from '@/api/product'
import { outboundApi } from '@/api/inventory/outbound'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { engineeringResourceApi } from '@/api/engineering/resource'
import { attachmentApi } from '@/api/system/attachment'
import { InboundOrderStatusEnum } from '@/enums/inventory'
import ProductFileLibrary from '@/components/product/ProductFileLibrary.vue'
import type { ProductFullVO, ProductVo } from '@/types/product'
import type { SamplePickPreviewRow } from '@/types/inventory/outbound'

defineOptions({ name: 'ProductSpec' })

const route = useRoute()

const loading = ref(false)
const rows = ref<ProductVo[]>([])
const total = ref(0)
const query = reactive({ productCode: '', productName: '', current: 1, pageSize: 20 })

const specVisible = ref(false)
const detailLoading = ref(false)
const detail = ref<ProductFullVO | null>(null)
const activeTab = ref('customer')

const productCode = computed(() => detail.value?.product?.productCode || '')
const bomItems = computed<any[]>(() => ((detail.value?.bom as any)?.items as any[]) || [])
const routingItems = computed<any[]>(() => ((detail.value?.routing as any)?.items as any[]) || [])

// ===== 客供资料：询价/报价附件（①） =====
const customerDocs = ref<any[]>([])
async function loadCustomerDocs() {
  customerDocs.value = []
  if (!productId.value) return
  try {
    const res: any = await attachmentApi.customerDocs(productId.value)
    customerDocs.value = res?.data || []
  } catch {
    customerDocs.value = []
  }
}
function openDoc(row: any) {
  const id = row.id
  if (id) window.open(attachmentApi.downloadUrl(Number(id)), '_blank')
}

// ===== 刀模（库位）关联（②） =====
const productDies = ref<any[]>([])
const dieVisible = ref(false)
const dieKeyword = ref('')
const dieOptions = ref<any[]>([])
const dieLoading = ref(false)

const productId = computed(() => Number((detail.value?.product as any)?.productId || 0))

async function loadProductDies() {
  productDies.value = []
  if (!productId.value) return
  try {
    const res: any = await engineeringResourceApi.byProduct('DIE', productId.value)
    productDies.value = res?.data || []
  } catch {
    productDies.value = []
  }
}

function openDieLink() {
  dieVisible.value = true
  searchDies()
}

async function searchDies() {
  dieLoading.value = true
  try {
    const res: any = await engineeringResourceApi.dies({ keyword: dieKeyword.value || undefined, pageNum: 1, pageSize: 50 })
    dieOptions.value = res?.data?.records || []
  } catch {
    dieOptions.value = []
  } finally {
    dieLoading.value = false
  }
}

/** 关联：把该产品并入刀模的产品列表（保留已有） */
async function linkDie(row: any) {
  const dieId = Number(row.die_id || row.dieId)
  if (!dieId || !productId.value) return
  try {
    const cur: any = await engineeringResourceApi.products('DIE', dieId)
    const ids = (cur?.data || []).map((x: any) => Number(x.product_id ?? x.productId)).filter(Boolean)
    if (!ids.includes(productId.value)) ids.push(productId.value)
    await engineeringResourceApi.replaceProducts('DIE', dieId, ids)
    ElMessage.success('已关联')
    dieVisible.value = false
    loadProductDies()
  } catch (e: any) {
    ElMessage.error(e?.message || '关联失败')
  }
}

/** 解除：从刀模的产品列表移除该产品 */
async function unlinkDie(row: any) {
  const dieId = Number(row.resourceId || row.die_id || row.dieId)
  if (!dieId || !productId.value) return
  try {
    const cur: any = await engineeringResourceApi.products('DIE', dieId)
    const ids = (cur?.data || [])
      .map((x: any) => Number(x.product_id ?? x.productId))
      .filter((x: number) => x && x !== productId.value)
    await engineeringResourceApi.replaceProducts('DIE', dieId, ids)
    ElMessage.success('已解除')
    loadProductDies()
  } catch (e: any) {
    ElMessage.error(e?.message || '解除失败')
  }
}

// ===== 打样领料单（⑦） =====
const sampleOrders = ref<any[]>([])
const samplePicks = ref<any[]>([])
const pickVisible = ref(false)
const pickSample = ref<any>(null)
const pickRows = ref<(SamplePickPreviewRow & { pickQty: number })[]>([])
const pickSubmitting = ref(false)
const unmatchedCount = computed(() => pickRows.value.filter((r) => !r.materialId).length)

// dev-20261008-014：改用现成的单据状态枚举 InboundOrderStatusEnum（0草稿…12调拨中），
// 不再自造本地 OUTBOUND_STATUS 映射（AGENTS 状态枚举铁律）。
const outboundStatusText = (s?: number) =>
  s === undefined || s === null
    ? '-'
    : InboundOrderStatusEnum.canDo(s)
      ? InboundOrderStatusEnum.getLabel(s)
      : String(s)

async function loadSampleData() {
  sampleOrders.value = []
  samplePicks.value = []
  const productId = (detail.value?.product as any)?.productId
  const code = productCode.value
  if (!productId && !code) return
  try {
    const res: any = await sampleOrderApi.page({ productId, productCode: code, pageNum: 1, pageSize: 100 } as any)
    const records: any[] = res?.data?.records || []
    // 兜底：按产品编码过滤（后端未过滤时）
    sampleOrders.value = code ? records.filter((r) => !r.productCode || r.productCode === code) : records
  } catch {
    sampleOrders.value = []
  }
  try {
    const ids = new Set(sampleOrders.value.map((r) => r.sampleOrderId))
    const res: any = await outboundApi.list({ sourceType: 'sample', pageNum: 1, pageSize: 200 } as any)
    const records: any[] = res?.data?.records || []
    samplePicks.value = ids.size ? records.filter((r) => ids.has(Number(r.sourceId))) : []
  } catch {
    samplePicks.value = []
  }
}

async function openSamplePick(row: any) {
  pickSample.value = row
  pickRows.value = []
  try {
    const res: any = await outboundApi.samplePickPreview(Number(row.sampleOrderId))
    const rows: SamplePickPreviewRow[] = res?.data || []
    pickRows.value = rows.map((r) => ({
      ...r,
      pickQty: Math.max(0, Number(r.quantity || 0) - Number(r.issuedQuantity || 0)),
    }))
  } catch (e: any) {
    ElMessage.error(e?.message || '加载打样领料预览失败')
    return
  }
  pickVisible.value = true
}

async function submitSamplePick() {
  if (!pickSample.value) return
  const items = pickRows.value
    .filter((r) => r.materialId && Number(r.pickQty) > 0)
    .map((r) => ({
      materialId: Number(r.materialId),
      materialCode: r.materialCode || undefined,
      materialName: r.materialName,
      quantity: Number(r.pickQty),
    }))
  if (!items.length) {
    ElMessage.warning('请填写本次领料数量（至少一行、物料已匹配）')
    return
  }
  pickSubmitting.value = true
  try {
    await outboundApi.createSamplePick(Number(pickSample.value.sampleOrderId), items)
    ElMessage.success('打样领料单已生成（待仓库确认发料后扣库存）')
    pickVisible.value = false
    loadSampleData()
  } catch (e: any) {
    ElMessage.error(e?.message || '生成失败')
  } finally {
    pickSubmitting.value = false
  }
}

function printPick(row: any) {
  window.open(`/print/outbound/${row.outboundId}`, '_blank')
}

watch(activeTab, (v) => {
  if (v === 'pick') loadSampleData()
  if (v === 'customer') loadCustomerDocs()
})

async function load() {
  loading.value = true
  try {
    const res: any = await listProductPage(query as any)
    const page = res?.data || {}
    rows.value = page.records || []
    total.value = page.total || 0
  } catch {
    rows.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function reset() {
  query.productCode = ''
  query.productName = ''
  query.current = 1
  load()
}

async function openSpec(row: any) {
  activeTab.value = 'customer'
  detail.value = null
  specVisible.value = true
  detailLoading.value = true
  try {
    const res: any = await getFullProduct(Number(row.productId))
    detail.value = res?.data || null
    loadProductDies()
    loadCustomerDocs()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载作业规范失败')
  } finally {
    detailLoading.value = false
  }
}

const exportSections = [
  { key: 'customer', label: '客供资料' },
  { key: 'spec', label: '产品作业规范（BOM/工艺路线/刀模/凹凸条件）' },
  { key: 'print', label: '印刷规范（+油墨调配记录表）' },
  { key: 'atlas', label: '产品图集' },
  { key: 'color', label: '规范分色检查表' },
  { key: 'sample', label: '样品' },
  { key: 'pick', label: '打样领料单' },
]
const exportVisible = ref(false)
const exportSelected = ref<string[]>(exportSections.map((s) => s.key))

function openExport() {
  exportSelected.value = exportSections.map((s) => s.key)
  exportVisible.value = true
}

function doPrint() {
  const id = productId.value
  if (!id) return
  const sections = exportSelected.value.join(',')
  window.open(`/print/product-spec/${id}?sections=${encodeURIComponent(sections)}`, '_blank')
  exportVisible.value = false
}

// 支持从产品管理侧带 ?productId= 跳入（方案c 两处入口）：先载列表，再直接开该产品的作业规范抽屉
onMounted(async () => {
  await load()
  const pid = Number(route.query.productId || 0)
  if (pid) openSpec({ productId: pid })
})
</script>

<style scoped>
.product-spec-page {
  padding: 12px;
}
.search-card {
  margin-bottom: 12px;
}
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
.sec-note {
  color: #909399;
  font-size: 12px;
  margin: 6px 0 2px;
}
.export-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.export-item {
  margin-right: 0;
}
</style>
