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
        <el-table-column label="操作" width="260" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSpec(row)">作业规范</el-button>
            <el-button v-hasPermi="['product:work-spec:publish']" link type="primary" @click="publishVersion(row)">发布版本</el-button>
            <el-button link type="primary" @click="openVersions(row)">版本</el-button>
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

    <!-- 发布版本：列表操作栏入口 -->
    <el-dialog
      v-model="versionsVisible"
      :title="`发布版本 - ${versionsRow?.productCode || ''}`"
      width="900px"
      top="6vh"
      append-to-body
      destroy-on-close
    >
      <ProductWorkSpecVersionPanel v-if="versionsVisible && versionsRow" :product-id="Number(versionsRow.productId)" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { listProductPage } from '@/api/product'
import { productWorkSpecApi } from '@/api/product/workSpec'
import ProductSpecDrawer from '@/components/product/ProductSpecDrawer.vue'
import ProductWorkSpecVersionPanel from './components/ProductWorkSpecVersionPanel.vue'
import type { ProductVo } from '@/types/product'

defineOptions({ name: 'ProductSpec' })

const route = useRoute()
const specDrawer = ref<InstanceType<typeof ProductSpecDrawer>>()
const loading = ref(false)
const rows = ref<ProductVo[]>([])
const total = ref(0)
const query = reactive({ productCode: '', productName: '', current: 1, pageSize: 20 })

// 发布版本（列表操作栏）
const versionsVisible = ref(false)
const versionsRow = ref<any>(null)
function openVersions(row: any) {
  versionsRow.value = row
  versionsVisible.value = true
}
async function publishVersion(row: any) {
  const productId = Number(row?.productId)
  if (!productId) return
  try {
    const { value } = await ElMessageBox.prompt('填写本次修订说明（可空）', `发布版本 - ${row?.productCode || ''}`, {
      inputPlaceholder: '如：冲型压力 80→85',
      inputValue: '',
      confirmButtonText: '发布',
      cancelButtonText: '取消',
    })
    const res: any = await productWorkSpecApi.publishVersion(productId, value || undefined)
    if (res?.code === 200) ElMessage.success(`已发布 ${res.data?.versionNo || ''}`)
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.message || '发布失败')
  }
}

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
