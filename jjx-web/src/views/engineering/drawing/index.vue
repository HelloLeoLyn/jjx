<template>
  <div class="app-container drawing-workspace">
    <div class="workspace-heading"><div><h2>工程图纸</h2><p>选择产品，管理图纸版本与原稿、打印件</p></div><el-button type="primary" icon="Printer" :disabled="!selected || busy || detailLoading" @click="docsetVisible = true">预览 / 打印文档集</el-button></div>
    <div class="workspace-layout">
      <aside class="product-panel">
        <div class="product-panel-heading"><strong>产品</strong><el-button link :disabled="busy || detailLoading" @click="loadProducts">刷新</el-button></div>
        <ProductSelector v-model="selectorProduct" :options="selectorProduct ? [selectorProduct] : []" allow-remote-with-options status-scope="active" :disabled="busy || detailLoading" :clearable="false" placeholder="搜索产品名称 / 编码" @change="onProductChange" />
        <el-alert v-if="productError" :title="productError" type="error" :closable="false" class="product-error" />
        <div v-loading="productLoading" class="product-list">
          <button v-if="selected && !products.some(item => item.productId === selected?.productId)" class="product-item active" :disabled="busy || detailLoading" @click="choose(selected)"><span>{{ selected.productName }}</span><small>{{ selected.productCode }}</small></button>
          <button v-for="product in products" :key="product.productId" class="product-item" :class="{ active: selected?.productId === product.productId }" :disabled="busy || detailLoading" @click="choose(product)"><span>{{ product.productName }}</span><small>{{ product.productCode }}</small><small v-if="product.customerName" class="product-customer">{{ product.customerName }}</small></button>
          <el-empty v-if="!productLoading && !products.length && !productError" description="暂无产品" :image-size="65" />
        </div>
        <el-pagination small layout="prev, pager, next" :pager-count="5" :total="total" :page-size="pageSize" :current-page="pageNum" :disabled="busy || detailLoading" @current-change="changePage" />
      </aside>
      <main v-loading="detailLoading" class="drawing-panel">
        <template v-if="selected">
          <div class="product-summary"><div class="product-summary-title"><h3>{{ selected.productName }}</h3><el-tag size="small" :type="ProductStatusEnum.getTagProps(selected.productStatus).type">{{ ProductStatusEnum.getLabel(selected.productStatus) }}</el-tag></div><div class="product-summary-fields"><span>产品编码 <strong>{{ selected.productCode }}</strong></span><span>客户 <strong>{{ selected.customerName || '—' }}</strong></span><span>规格 <strong>{{ specificationText || '—' }}</strong></span></div></div>
          <EngineeringDrawingLibrary :key="selected.productId" :product-code="selected.productCode" :product-name="selected.productName" @busy="busy = $event" />
        </template>
        <el-empty v-else description="从左侧选择产品，或按名称、编码搜索" :image-size="100" />
      </main>
    </div>
    <el-dialog v-model="docsetVisible" title="产品电子文档集 · 在线预览" width="94%" top="4vh" append-to-body destroy-on-close><ProductSpecPreview v-if="docsetVisible && selected" :product-id="selected.productId" /></el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { listProductPage, getFullProduct } from '@/api/product'
import type { ProductItem, ProductVo } from '@/types/product'
import { ProductStatusEnum } from '@/enums/product'
import { parseSpecJson } from '@/utils/specJsonHelper'
import ProductSelector from '@/components/Selector/ProductSelector.vue'
import ProductSpecPreview from '../product-spec/components/ProductSpecPreview.vue'
import EngineeringDrawingLibrary from './components/EngineeringDrawingLibrary.vue'
defineOptions({ name: 'EngineeringDrawing' })
type DrawingProduct = ProductVo & { customerName?: string; specification?: string }
const route = useRoute()
const products = ref<DrawingProduct[]>([])
const selected = ref<DrawingProduct>()
const selectorProduct = ref<ProductItem | null>(null)
const productLoading = ref(false)
const productError = ref('')
const detailLoading = ref(false)
const busy = ref(false)
const pageNum = ref(1)
const pageSize = 15
const total = ref(0)
const docsetVisible = ref(false)
const specificationText = computed(() => selected.value?.specification || parseSpecJson(selected.value?.specJson || '').filter(item => String(item.value ?? '').trim()).map(item => `${item.name}：${item.value}${item.unit && item.unit !== '-' ? item.unit : ''}`).join(' · '))
let listGeneration = 0
let detailGeneration = 0
let disposed = false
async function loadProducts() {
  const current = ++listGeneration
  productLoading.value = true; productError.value = ''
  try {
    const res = await listProductPage({ pageNum: pageNum.value, pageSize })
    if (disposed || current !== listGeneration) return
    products.value = res.data?.records || []; total.value = res.data?.total || 0
    if (!selected.value && !Number(route.query.productId) && products.value.length && !detailLoading.value) choose(products.value[0])
  } catch (e) { if (current === listGeneration && !disposed) productError.value = e instanceof Error ? e.message : '产品加载失败，请刷新' }
  finally { if (current === listGeneration && !disposed) productLoading.value = false }
}
async function choose(product: Pick<DrawingProduct, 'productId'>) {
  if (busy.value || selected.value?.productId === product.productId) return
  const current = ++detailGeneration
  detailLoading.value = true
  try {
    const res = await getFullProduct(product.productId)
    if (current !== detailGeneration || disposed) return
    if (!res.data?.product) throw new Error('产品不存在或已删除')
    selected.value = res.data.product as unknown as DrawingProduct
    selectorProduct.value = res.data.product as unknown as ProductItem
  } catch (e) {
    if (current === detailGeneration && !disposed) { selectorProduct.value = selected.value as unknown as ProductItem || null; ElMessage.error(e instanceof Error ? e.message : '产品加载失败') }
  } finally { if (current === detailGeneration && !disposed) detailLoading.value = false }
}
function onProductChange(_value: unknown, product: ProductItem | null) { if (product) choose(product) }
function changePage(value: number) { pageNum.value = value; loadProducts() }
onMounted(() => { loadProducts(); const id = Number(route.query.productId); if (id) choose({ productId: id }) })
onBeforeUnmount(() => { disposed = true; listGeneration++; detailGeneration++ })
</script>
<style scoped>
.drawing-workspace { background:#f4f6fa; min-height:calc(100vh - 105px); }.workspace-heading { display:flex; align-items:center; justify-content:space-between; gap:16px; margin-bottom:20px; }.workspace-heading h2 { font-size:20px; font-weight:600; margin:0; color:#2e3e54; }.workspace-heading p { margin:6px 0 0; font-size:12px; color:#8a97a9; }.workspace-layout { display:grid; grid-template-columns:285px minmax(0,1fr); gap:18px; align-items:start; }.product-panel,.drawing-panel { background:#fff; border:1px solid #e5eaf1; border-radius:8px; }.product-panel { padding:18px 14px; }.product-panel-heading { display:flex; align-items:center; justify-content:space-between; margin:0 4px 14px; font-size:14px; }.product-panel :deep(.product-selector) { width:100%; }.product-list { margin-top:16px; max-height:calc(100vh - 310px); min-height:300px; overflow:auto; }.product-item { display:flex; flex-direction:column; gap:6px; width:100%; text-align:left; padding:13px 12px; background:#fff; border:1px solid transparent; border-radius:6px; margin-bottom:5px; cursor:pointer; color:#47576e; }.product-item:hover { background:#f5f8fc; }.product-item.active { background:#eef4fe; border-color:#d6e5fc; color:#3974c6; }.product-item span { font-size:13px; line-height:1.4; }.product-item small { font-size:11px; color:#8c99a9; }.product-item .product-customer { color:#a2acb9; }.product-item:disabled { cursor:default; }.product-panel :deep(.el-pagination) { justify-content:center; margin-top:18px; }.product-error { margin-top:12px; }.drawing-panel { min-height:500px; padding:24px; }.product-summary { padding-bottom:20px; margin-bottom:24px; border-bottom:1px solid #edf0f5; }.product-summary-title { display:flex; align-items:center; gap:12px; }.product-summary h3 { margin:0; font-size:18px; font-weight:600; color:#31425a; }.product-summary-fields { display:flex; flex-wrap:wrap; gap:12px 30px; margin-top:15px; font-size:12px; color:#9ba5b2; }.product-summary-fields strong { font-weight:400; color:#66768c; margin-left:10px; }
@media(max-width:1100px) { .workspace-layout { grid-template-columns:240px minmax(0,1fr); gap:12px; }.drawing-panel { padding:18px; } }
@media(max-width:760px) { .workspace-layout { grid-template-columns:1fr; }.product-list { max-height:220px; min-height:0; }.workspace-heading { flex-wrap:wrap; } }
</style>
