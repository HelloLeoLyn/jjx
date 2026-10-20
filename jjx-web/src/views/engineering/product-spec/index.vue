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
        <el-table-column label="制定状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="specStateType(row)">{{ specStateLabel(row) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="制定负责人" width="120" align="center">
          <template #default="{ row }">
            {{ versionInfoMap[Number(row.productId)]?.preparedByName || '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="540" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openSpec(row, true)">作业规范</el-button>
            <el-button
              v-if="canContinueSpec(row)"
              v-hasPermi="['product:edit']"
              link
              type="primary"
              @click="openSpec(row, false)"
              >继续制定</el-button
            >
            <el-button
              v-if="canClaimSpec(row)"
              v-hasPermi="['product:edit']"
              link
              type="primary"
              :loading="claimingId === Number(row.productId)"
              @click="claimSpec(row)"
              >制定规范</el-button
            >
            <el-button
              v-if="canTransferSpec(row)"
              v-hasPermi="['product:edit']"
              link
              type="primary"
              @click="openTransfer(row)"
              >{{ isSpecOwner(row) ? '转交' : '改派' }}</el-button
            >
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

    <!-- 转交/改派制定负责人（dev-20261020-018） -->
    <el-dialog
      v-model="transferVisible"
      :title="transferRow && isSpecOwner(transferRow) ? '转交制定负责人' : '改派制定负责人'"
      width="460px"
      append-to-body
      destroy-on-close
    >
      <p class="transfer-tip">
        当前负责人：{{
          transferRow ? versionInfoMap[Number(transferRow.productId)]?.preparedByName || '-' : '-'
        }}
      </p>
      <el-select
        v-model="transferTarget"
        filterable
        placeholder="选择新的制定负责人"
        style="width: 100%"
        :disabled="transferLoading || transferSubmitting"
      >
        <el-option
          v-for="person in transferUsers"
          :key="person.id"
          :label="person.name"
          :value="person.id"
        />
      </el-select>
      <template #footer>
        <el-button :disabled="transferSubmitting" @click="transferVisible = false">取消</el-button>
        <el-button
          type="primary"
          :loading="transferSubmitting"
          :disabled="transferLoading || !transferTarget"
          @click="submitTransfer"
          >确认{{ transferRow && isSpecOwner(transferRow) ? '转交' : '改派' }}</el-button
        >
      </template>
    </el-dialog>

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
import { ElMessage, ElMessageBox } from 'element-plus'
import { listProductPage } from '@/api/product'
import { productWorkSpecApi } from '@/api/product/workSpec'
import { useUserStore } from '@/store/modules/user'
import { usePermission } from '@/composables/usePermission'
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
const userStore = useUserStore()
const { hasRole } = usePermission()

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
// 列表制定状态/负责人（dev-20261020-017）：数据来自 batchStatus 的 preparedBy/finalizedVersionId。
const claimingId = ref<number>()
function specInfo(row: any) {
  return versionInfoMap.value[Number(row?.productId)]
}
function specStateLabel(row: any) {
  const info = specInfo(row)
  if (!info) return '-'
  if (info.finalizedVersionId) return '已定版'
  if (info.preparedById) return '制定中'
  return '待制定'
}
function specStateType(row: any): 'success' | 'warning' | 'info' {
  const state = specStateLabel(row)
  if (state === '已定版') return 'success'
  if (state === '制定中') return 'warning'
  return 'info'
}
function canClaimSpec(row: any) {
  const info = specInfo(row)
  return !!info && !info.preparedById && !info.finalizedVersionId
}
function isSpecOwner(row: any) {
  const info = specInfo(row)
  return !!info?.preparedById && Number(info.preparedById) === Number(userStore.userId)
}
function canContinueSpec(row: any) {
  const info = specInfo(row)
  return !!info && !!info.preparedById && !info.finalizedVersionId && isSpecOwner(row)
}
function canTransferSpec(row: any) {
  const info = specInfo(row)
  return (
    !!info &&
    !!info.preparedById &&
    !info.finalizedVersionId &&
    (isSpecOwner(row) || hasRole('engineering:all'))
  )
}
const transferVisible = ref(false)
const transferRow = ref<any>()
const transferUsers = ref<{ id: number; name: string }[]>([])
const transferTarget = ref<number>()
const transferLoading = ref(false)
const transferSubmitting = ref(false)
const transferRevision = ref('')
async function openTransfer(row: any) {
  const productId = Number(row?.productId)
  if (!productId) return
  transferRow.value = row
  transferTarget.value = undefined
  transferUsers.value = []
  transferRevision.value = ''
  transferVisible.value = true
  transferLoading.value = true
  try {
    const [detail, users] = await Promise.all([
      productWorkSpecApi.get(productId),
      productWorkSpecApi.responsibleUsers(productId),
    ])
    transferRevision.value = String(detail?.data?.revision || '')
    const currentOwner = Number(specInfo(row)?.preparedById)
    transferUsers.value = ((users?.data || []) as { id: number; name: string }[]).filter(
      (person) => Number(person.id) !== currentOwner
    )
  } catch (error: any) {
    ElMessage.error(error?.message || '负责人列表加载失败')
    transferVisible.value = false
  } finally {
    transferLoading.value = false
  }
}
async function submitTransfer() {
  const productId = Number(transferRow.value?.productId)
  const target = Number(transferTarget.value)
  if (!productId || !target || !transferRevision.value || transferSubmitting.value) return
  transferSubmitting.value = true
  try {
    await productWorkSpecApi.transferWorkSpec(productId, {
      revision: transferRevision.value,
      preparedById: target,
      ownerTransferConfirmed: true,
    })
    ElMessage.success(isSpecOwner(transferRow.value) ? '已转交制定负责人' : '已改派制定负责人')
    transferVisible.value = false
    await load()
  } catch (error: any) {
    ElMessage.error(error?.message || '转交失败，请刷新后重试')
    await load()
  } finally {
    transferSubmitting.value = false
  }
}
async function claimSpec(row: any) {
  const productId = Number(row?.productId)
  if (!productId || claimingId.value) return
  try {
    await ElMessageBox.confirm(
      `确认制定「${row.productCode || row.productName || productId}」的作业规范？制定后由你负责，其他人只能查看。`,
      '制定规范',
      { type: 'warning', confirmButtonText: '确认制定', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  claimingId.value = productId
  try {
    await productWorkSpecApi.claimWorkSpec(productId)
    ElMessage.success('已开始制定，可以编辑作业规范')
    await load()
    openSpec(row, false)
  } catch (error: any) {
    ElMessage.error(error?.message || '制定失败，请刷新后重试')
    await load()
  } finally {
    claimingId.value = undefined
  }
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

function openSpec(row: Pick<ProductVo, 'productId'>, readOnly = true) {
  specDrawer.value?.open(Number(row.productId), undefined, { readOnly })
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
.transfer-tip {
  margin: 0 0 10px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
