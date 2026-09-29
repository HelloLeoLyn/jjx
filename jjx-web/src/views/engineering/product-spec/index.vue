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
              title="客供资料 = 询价/报价里上传的客户附件；此处按产品归集展示（样品需求单见 QR-065）。" />
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

            <el-divider content-position="left">刀模（库位）/ 凹凸条件</el-divider>
            <el-empty description="刀模库位关联、凹凸条件录入：待接入（工程侧）" :image-size="50" />
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
            <el-empty description="打样领料单：复用生产领料（放开到样品单）+ QR-031 模板抬头改「打样」——待改造" :image-size="60" />
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listProductPage, getFullProduct } from '@/api/product'
import ProductFileLibrary from '@/components/product/ProductFileLibrary.vue'
import type { ProductFullVO, ProductVo } from '@/types/product'

defineOptions({ name: 'ProductSpec' })

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
  const p: any = detail.value?.product || {}
  const picked = exportSections.filter((s) => exportSelected.value.includes(s.key))
  const win = window.open('', '_blank')
  if (!win) {
    ElMessage.warning('浏览器拦截了新窗口，请允许弹窗后重试')
    return
  }
  const html = `
    <html><head><title>产品作业规范 - ${p.productCode || ''}</title>
    <style>body{font-family:sans-serif;padding:20px} h1{font-size:18px} h2{font-size:15px;margin-top:18px;border-bottom:1px solid #ccc} table{border-collapse:collapse;width:100%;font-size:12px} td,th{border:1px solid #999;padding:4px}</style>
    </head><body>
    <h1>产品作业规范（文档集勾选预览）</h1>
    <div>产品编码：${p.productCode || '-'}　产品名称：${p.productName || '-'}　客户：${p.customerName || '-'}</div>
    ${picked.map((s) => `<h2>${s.label}</h2>`).join('')}
    <p style="color:#888;font-size:12px">（此处为勾选项预览；正式版式待接入打印中心/质量记录模板）</p>
    </body></html>`
  win.document.write(html)
  win.document.close()
  win.focus()
  win.print()
  exportVisible.value = false
}

onMounted(load)
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
