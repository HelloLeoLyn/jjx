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
    <el-drawer v-model="specVisible" :title="`产品作业规范 - ${detail?.product?.productCode || ''}`" size="82%" destroy-on-close :before-close="closeSpec">
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
            <el-button type="primary" icon="View" :disabled="detailLoading || !productId" @click="openExport">预览 / 打印文档集</el-button>
          </div>
        </div>

        <el-tabs v-model="activeTab" class="spec-tabs" :before-leave="beforeSpecTabLeave">
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
            <ProductWorkSpecPanel
              v-if="productId && activeTab === 'spec'"
              :key="productId"
              :product-id="productId"
              :product-code="productCode"
              :product-name="detail?.product?.productName || ''"
              @busy="workSpecBusy = $event"
              @updated="refreshSpecSources"
            />
          </el-tab-pane>

          <!-- ③ 印刷规范 -->
          <el-tab-pane label="印刷规范" name="print">
            <ProductPrintSpecPanel
              v-if="productId && activeTab === 'print'"
              :key="`${productId}-print`"
              :product-id="productId"
              @busy="printSpecBusy = $event"
              @updated="refreshSpecSources"
            />
          </el-tab-pane>

          <!-- ④ 工程图集 -->
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
        </el-tabs>
      </div>
    </el-drawer>

    <!-- 在线纸张预览：目录选择与打印共用同一版式 -->
    <el-dialog v-model="exportVisible" title="产品电子文档集 · 在线预览" width="94%" top="4vh" append-to-body destroy-on-close>
      <ProductSpecPreview v-if="exportVisible && productId" :product-id="productId" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listProductPage, getFullProduct } from '@/api/product'
import { attachmentApi } from '@/api/system/attachment'
import ProductFileLibrary from '@/components/product/ProductFileLibrary.vue'
import EngineeringDrawingLibrary from '../drawing/components/EngineeringDrawingLibrary.vue'
import { ENGINEERING_DRAWING_VISIBLE_CATEGORIES } from '@/components/product/productFileCategories'
import ProductSpecPreview from './components/ProductSpecPreview.vue'
import ProductWorkSpecPanel from './components/ProductWorkSpecPanel.vue'
import ProductPrintSpecPanel from './components/ProductPrintSpecPanel.vue'
import type { ProductFullVO, ProductVo } from '@/types/product'

defineOptions({ name: 'ProductSpec' })

const route = useRoute()

const loading = ref(false)
const rows = ref<ProductVo[]>([])
const total = ref(0)
const query = reactive({ productCode: '', productName: '', current: 1, pageSize: 20 })

const specVisible = ref(false)
const drawingBusy = ref(false)
const workSpecBusy = ref(false)
const printSpecBusy = ref(false)
function closeSpec(done: () => void) {
  if (drawingBusy.value || workSpecBusy.value || printSpecBusy.value) { ElMessage.warning('请先完成或关闭工程规范、印刷备注、图纸维护窗口'); return }
  done()
}
function beforeSpecTabLeave() {
  if (workSpecBusy.value || printSpecBusy.value) { ElMessage.warning('请先完成或关闭规范编辑窗口'); return false }
  return true
}
const detailLoading = ref(false)
const detail = ref<ProductFullVO | null>(null)
const activeTab = ref('customer')

const productCode = computed(() => detail.value?.product?.productCode || '')

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

const productId = computed(() => Number((detail.value?.product as any)?.productId || 0))
async function refreshSpecSources() {
  if (!productId.value) return
  try {
    const response = await getFullProduct(productId.value)
    detail.value = response.data
  } catch (e) { ElMessage.error(e instanceof Error ? e.message : '产品资料刷新失败') }
}

watch(activeTab, (v) => {
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
    loadCustomerDocs()
  } catch (e: any) {
    ElMessage.error(e?.message || '加载作业规范失败')
  } finally {
    detailLoading.value = false
  }
}

const exportVisible = ref(false)
function openExport() {
  exportVisible.value = true
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
</style>
