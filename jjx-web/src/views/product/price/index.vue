<template>
  <div class="app-container">
    <template v-if="hasPermi('product:price:view')">
      <el-card shadow="never" class="search-card">
        <el-form inline :model="query" @submit.prevent="search">
          <el-form-item label="产品编码"><el-input v-model="query.productCode" clearable placeholder="产品编码" @keyup.enter="search" /></el-form-item>
          <el-form-item label="产品名称"><el-input v-model="query.productName" clearable placeholder="产品名称" @keyup.enter="search" /></el-form-item>
          <el-form-item><el-button type="primary" :loading="loading" @click="search">查询</el-button><el-button @click="reset">重置</el-button><el-button :loading="loading" @click="load">刷新</el-button></el-form-item>
        </el-form>
      </el-card>
      <el-card shadow="never">
        <template #header><div class="page-heading"><strong>产品价格维护</strong><span>维护产品基础售价与标准成本；毛利率自动计算</span></div></template>
        <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" class="load-error" />
        <el-table v-loading="loading" :data="rows" border>
          <el-table-column prop="productCode" label="产品编码" min-width="150" />
          <el-table-column prop="productName" label="产品名称" min-width="180" />
          <el-table-column prop="customerName" label="客户" min-width="180" show-overflow-tooltip />
          <el-table-column prop="unit" label="单位" width="80" />
          <el-table-column label="基础售价（元）" width="145" align="right"><template #default="{ row }">{{ money(row.basePrice) }}</template></el-table-column>
          <el-table-column label="标准成本（元）" width="145" align="right"><template #default="{ row }">{{ money(row.costPrice) }}</template></el-table-column>
          <el-table-column label="参考毛利率" width="120" align="right"><template #default="{ row }">{{ margin(row.basePrice, row.costPrice) }}</template></el-table-column>
          <el-table-column v-if="canEdit" label="操作" width="120" fixed="right"><template #default="{ row }"><el-button type="primary" link :disabled="opening || saving" @click="edit(row.productId)">维护价格</el-button></template></el-table-column>
        </el-table>
        <pagination v-show="total > 0" v-model:page="query.pageNum" v-model:limit="query.pageSize" :total="total" @pagination="load" />
        <p class="page-note">基础售价为产品参考价，实际客户报价与订单单价仍由对应单据确定。标准成本用于生产成本对比。</p>
      </el-card>
      <el-dialog v-model="visible" title="维护产品价格" width="560px" :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving" :before-close="beforeClose" append-to-body>
        <el-descriptions :column="1" border class="product-info"><el-descriptions-item label="产品编码">{{ selected?.productCode }}</el-descriptions-item><el-descriptions-item label="产品名称">{{ selected?.productName }}</el-descriptions-item></el-descriptions>
        <el-form ref="formRef" :model="form" :rules="rules" label-width="130px" :disabled="saving || !canEdit">
          <el-form-item label="基础售价（元）" prop="basePrice"><el-input-number v-model="form.basePrice" :min="0" :max="9999999999.99" :precision="2" controls-position="right" placeholder="请填写基础售价" /></el-form-item>
          <el-form-item label="标准成本（元）" prop="costPrice"><el-input-number v-model="form.costPrice" :min="0" :max="9999999999.99" :precision="2" controls-position="right" placeholder="请填写标准成本" /></el-form-item>
          <el-form-item label="参考毛利率"><span>{{ margin(form.basePrice, form.costPrice) }}</span></el-form-item>
        </el-form>
        <template #footer><el-button :disabled="saving" @click="visible = false">取消</el-button><el-button type="primary" :disabled="!canEdit" :loading="saving" @click="save">保存</el-button></template>
      </el-dialog>
    </template>
    <el-empty v-else description="暂无产品价格查看权限" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { hasPermi } from '@/directives'
import { productPriceApi, type ProductPrice } from '@/api/product/price'

const canEdit = computed(() => hasPermi('product:price:edit'))
const query = reactive({ productCode: '', productName: '', pageNum: 1, pageSize: 20 })
const rows = ref<ProductPrice[]>([])
const total = ref(0)
const loading = ref(false)
const loadError = ref('')
const opening = ref(false)
const saving = ref(false)
const visible = ref(false)
const selected = ref<ProductPrice>()
const formRef = ref<FormInstance>()
const form = reactive<{ basePrice?: number; costPrice?: number }>({})
const rules: FormRules = {
  basePrice: [{ required: true, type: 'number', min: 0, message: '请填写不小于0的基础售价', trigger: 'blur' }],
  costPrice: [{ required: true, type: 'number', min: 0, message: '请填写不小于0的标准成本', trigger: 'blur' }],
}
const money = (value?: number | null) => value == null ? '—' : Number(value).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const margin = (price?: number | null, cost?: number | null) => price == null || cost == null || price <= 0 ? '—' : `${((price - cost) / price * 100).toFixed(1)}%`
let loadSequence = 0

async function load() {
  if (!hasPermi('product:price:view')) return
  const sequence = ++loadSequence
  loading.value = true; loadError.value = ''
  try {
    const response = await productPriceApi.page({ ...query })
    if (sequence !== loadSequence) return
    rows.value = response.data?.records || []; total.value = response.data?.total || 0
  } catch (error) {
    if (sequence !== loadSequence) return
    rows.value = []; total.value = 0
    loadError.value = error instanceof Error ? error.message : '产品价格加载失败，请重试'
  } finally { if (sequence === loadSequence) loading.value = false }
}
function search() { query.pageNum = 1; void load() }
function reset() { query.productCode = ''; query.productName = ''; search() }
function beforeClose(done: () => void) { if (!saving.value) done() }
async function edit(productId: number) {
  if (!canEdit.value || opening.value || saving.value) return
  opening.value = true
  try {
    const response = await productPriceApi.get(productId)
    if (!response.data) throw new Error('产品不存在或已删除')
    selected.value = response.data
    form.basePrice = response.data.basePrice ?? undefined
    form.costPrice = response.data.costPrice ?? undefined
    formRef.value?.clearValidate()
    visible.value = true
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '产品价格读取失败') }
  finally { opening.value = false }
}
async function save() {
  if (!canEdit.value || saving.value || !selected.value || !formRef.value) return
  try { await formRef.value.validate() } catch { return }
  if (form.basePrice == null || form.costPrice == null) return
  saving.value = true
  try {
    const response = await productPriceApi.update(selected.value.productId, {
      basePrice: form.basePrice, costPrice: form.costPrice,
      expectedBasePrice: selected.value.basePrice ?? null,
      expectedCostPrice: selected.value.costPrice ?? null,
    })
    if (!response.data?.success) throw new Error('价格保存失败，请刷新后重试')
    visible.value = false; ElMessage.success('产品价格已保存'); await load()
  } catch (error) { ElMessage.error(error instanceof Error ? error.message : '价格保存失败') }
  finally { saving.value = false }
}
onMounted(load)
</script>

<style scoped>
.search-card { margin-bottom:16px; }
.page-heading { display:flex; align-items:center; flex-wrap:wrap; gap:16px; }
.page-heading span,.page-note { color:#64748b; font-size:12px; line-height:1.7; }
.load-error,.product-info { margin-bottom:16px; }
.page-note { margin:12px 0 0; }
:deep(.el-input-number) { width:100%; }
</style>
