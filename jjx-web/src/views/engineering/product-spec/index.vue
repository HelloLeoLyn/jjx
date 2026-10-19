<template>
  <div class="product-spec-page">
    <!-- 搜索 -->
    <el-card shadow="never" class="search-card">
      <el-form inline>
        <el-form-item label="产品编码">
          <el-input
            v-model="query.productCode"
            placeholder="产品编码"
            clearable
            style="width: 160px"
            @keyup.enter="load"
          />
        </el-form-item>
        <el-form-item label="产品名称">
          <el-input
            v-model="query.productName"
            placeholder="产品名称"
            clearable
            style="width: 160px"
            @keyup.enter="load"
          />
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
        <el-table-column
          label="产品名称"
          prop="productName"
          min-width="180"
          show-overflow-tooltip
        />
        <el-table-column label="客户" prop="customerName" min-width="150" show-overflow-tooltip />
        <el-table-column label="当前BOM版本" width="120" align="center">
          <template #default="{ row }">{{ (row as any).bomVersion || '-' }}</template>
        </el-table-column>
        <el-table-column label="当前路线版本" width="120" align="center">
          <template #default="{ row }">{{ (row as any).routeVersion || '-' }}</template>
        </el-table-column>
        <el-table-column label="当前规范版本" width="170" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openVersions(row)">
              {{ versionInfoMap[Number(row.productId)]?.latestVersionNo || '未定版' }}
            </el-button>
            <el-tag
              v-if="versionInfoMap[Number(row.productId)]?.hasUnpublishedChanges"
              size="small"
              type="warning"
              effect="plain"
              style="margin-left: 4px"
              >待定版改动</el-tag
            >
          </template>
        </el-table-column>
        <el-table-column label="操作" width="340" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSpec(row)">作业规范</el-button>
            <el-button
              v-hasPermi="['product:work-spec:publish']"
              link
              type="primary"
              @click="publishVersion(row)"
              >规范定版</el-button
            >
            <el-button link type="primary" @click="openVersions(row)">版本</el-button>
            <el-button link type="primary" @click="openPreview(row)">预览/打印</el-button>
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

    <ProductSpecDrawer ref="specDrawer" />

    <!-- 规范定版：列表操作栏入口 -->
    <el-dialog
      v-model="versionsVisible"
      :title="`规范定版 - ${versionsRow?.productCode || ''}`"
      width="900px"
      top="6vh"
      append-to-body
      destroy-on-close
    >
      <ProductWorkSpecVersionPanel
        v-if="versionsVisible && versionsRow"
        :product-id="Number(versionsRow.productId)"
        show-full-page
        @open-version="openVersionFullPage"
        @updated="loadVersionsStatus"
      />
    </el-dialog>

    <!-- 预览 / 打印文档集（复用现有 ProductSpecPreview；dev-20261011-028） -->
    <el-dialog
      v-model="previewVisible"
      :title="`预览 / 打印文档集 - ${previewRow?.productCode || ''}`"
      width="94%"
      top="4vh"
      append-to-body
      destroy-on-close
    >
      <ProductSpecPreview
        v-if="previewVisible && previewRow"
        :product-id="Number(previewRow.productId)"
      />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { listProductPage } from '@/api/product'
import { productWorkSpecApi } from '@/api/product/workSpec'
import ProductSpecDrawer from '@/components/product/ProductSpecDrawer.vue'
import ProductWorkSpecVersionPanel from './components/ProductWorkSpecVersionPanel.vue'
import ProductSpecPreview from './components/ProductSpecPreview.vue'
import type { ProductVo } from '@/types/product'

defineOptions({ name: 'ProductSpec' })

const route = useRoute()
const specDrawer = ref<InstanceType<typeof ProductSpecDrawer>>()
const loading = ref(false)
const rows = ref<ProductVo[]>([])
const total = ref(0)
const query = reactive({ productCode: '', productName: '', current: 1, pageSize: 20 })

// 规范定版（列表操作栏）
const versionsVisible = ref(false)
const versionsRow = ref<any>(null)
// 当前规范版本列（dev-20261011-027）
const versionInfoMap = ref<Record<number, any>>({})
async function loadVersionsStatus() {
  const ids = rows.value.map((r) => Number(r.productId)).filter((id) => !!id)
  versionInfoMap.value = {}
  if (!ids.length) return
  try {
    const res: any = await productWorkSpecApi.batchStatus(ids)
    const map: Record<number, any> = {}
    for (const item of res?.data || []) map[Number(item.productId)] = item
    versionInfoMap.value = map
  } catch {
    /* 版本状态列失败不阻塞列表 */
  }
}
function openVersions(row: any) {
  versionsRow.value = row
  versionsVisible.value = true
}
// 整页查看：关弹窗 → 打开作业规范抽屉并定位到该版本只读（dev-20261011-029）
function openVersionFullPage(versionId: number) {
  const productId = Number(versionsRow.value?.productId)
  versionsVisible.value = false
  if (productId && versionId) specDrawer.value?.open(productId, versionId)
}
// 预览 / 打印文档集（列表操作栏；dev-20261011-028）
const previewVisible = ref(false)
const previewRow = ref<any>(null)
function openPreview(row: any) {
  previewRow.value = row
  previewVisible.value = true
}
async function publishVersion(row: any) {
  openVersions(row)
}

async function load() {
  loading.value = true
  try {
    const res: any = await listProductPage(query as any)
    const page = res?.data || {}
    rows.value = page.records || []
    total.value = page.total || 0
    await loadVersionsStatus()
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

function openSpec(row: Pick<ProductVo, 'productId'>) {
  specDrawer.value?.open(Number(row.productId))
}

// 兼容原有带 productId 的作业规范链接。
onMounted(() => {
  load()
  const id = Number(route.query.productId || 0)
  if (id) specDrawer.value?.open(id)
})
</script>

<style scoped>
.product-spec-page {
  padding: 12px;
}
.search-card {
  margin-bottom: 12px;
}
</style>
