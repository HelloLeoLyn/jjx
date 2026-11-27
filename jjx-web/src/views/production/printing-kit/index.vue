<template>
  <div class="app-container printing-kit">
    <!-- 产品级预检 -->
    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-head">
          <span>产品级预检</span>
          <span class="tip">只做资料预检，不代表工单齐套确认</span>
        </div>
      </template>
      <el-form inline>
        <el-form-item label="产品">
          <div style="width: 300px">
            <ProductSelector v-model="productId" value-type="productId" placeholder="搜索并选择产品" />
          </div>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :disabled="!productId" @click="runProductPrecheck">
            预检
          </el-button>
        </el-form-item>
      </el-form>
      <template v-if="productKit">
        <el-alert
          :type="productKit.ok ? 'success' : 'warning'"
          :closable="false"
          :title="productKit.ok ? '图案版本均有可用网版' : `缺版 ${productKit.gaps?.length || 0} 项`"
        />
        <el-table :data="productKit.items || []" size="small" border style="margin-top: 10px">
          <el-table-column label="图案编号" prop="artworkNo" width="160" />
          <el-table-column label="名称" prop="artworkName" min-width="160" show-overflow-tooltip />
          <el-table-column label="生效版本" prop="versionNo" width="110" align="center" />
          <el-table-column label="需要" prop="need" width="80" align="center" />
          <el-table-column label="可用网版" prop="available" width="100" align="center" />
          <el-table-column label="缺口" width="80" align="center">
            <template #default="{ row }">
              <el-tag :type="row.gap ? 'danger' : 'success'" size="small">
                {{ row.gap ? row.gap : '齐' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-card>

    <!-- 工单齐套 -->
    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-head">
          <span>工单齐套</span>
          <span class="tip">按工单绑定的规范快照逐工序核对；物料沿用现有缺料预检</span>
        </div>
      </template>
      <el-form inline>
        <el-form-item label="工单ID">
          <el-input-number v-model="orderId" :min="1" :controls="false" style="width: 160px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" :disabled="!orderId" @click="loadOrder">核对</el-button>
          <el-button :disabled="!orderId" :loading="rechecking" @click="recheckOrder">复查并更新台账</el-button>
        </el-form-item>
      </el-form>
      <template v-if="orderKit">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="工单">{{ orderKit.orderNo || orderKit.orderId }}</el-descriptions-item>
          <el-descriptions-item label="产品">{{ orderKit.productId || '-' }}</el-descriptions-item>
          <el-descriptions-item label="结论">
            <el-tag :type="orderKit.ok ? 'success' : 'warning'" size="small">
              {{ orderKit.ok ? '网版齐套' : '缺版 ' + (orderKit.gaps?.length || 0) + ' 项' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        <el-table :data="orderKit.checks || []" size="small" border style="margin-top: 10px">
          <el-table-column label="工序" prop="processName" min-width="140" />
          <el-table-column label="图案编号" prop="resourceCode" width="160" />
          <el-table-column label="图案版本" prop="resourceName" width="120" />
          <el-table-column label="需要(张)" prop="need" width="90" align="center" />
          <el-table-column label="可用" prop="available" width="80" align="center" />
          <el-table-column label="缺口" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.gap ? 'danger' : 'success'" size="small">
                {{ row.gap ? row.gap + ' 张' : '齐' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-card>

    <!-- 缺口台账 -->
    <el-card shadow="never" class="card">
      <template #header>
        <div class="card-head">
          <span>缺口台账</span>
          <el-radio-group v-model="statusFilter" size="small" @change="loadShortages">
            <el-radio-button :value="0">待处理</el-radio-button>
            <el-radio-button :value="1">已关闭</el-radio-button>
            <el-radio-button :value="undefined">全部</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <el-table :data="shortages" v-loading="loading" size="small" border>
        <el-table-column label="工单" prop="orderNo" width="150" />
        <el-table-column label="工序" prop="processName" min-width="130" />
        <el-table-column label="图案/版本" min-width="170">
          <template #default="{ row }">{{ row.resourceCode }} {{ row.resourceName }}</template>
        </el-table-column>
        <el-table-column label="需要" prop="needQty" width="70" align="center" />
        <el-table-column label="可用" prop="availableQty" width="70" align="center" />
        <el-table-column label="缺口" prop="gapQty" width="70" align="center" />
        <el-table-column label="首次发现" prop="firstFoundAt" width="160" />
        <el-table-column label="最近复查" prop="lastCheckAt" width="160" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 0 ? 'warning' : 'info'" size="small">
              {{ row.status === 0 ? '待处理' : '已关闭' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status === 0"
              link
              type="primary"
              size="small"
              @click="goMakePlate(row)"
            >
              去制版
            </el-button>
            <el-button
              v-if="row.status === 0"
              link
              type="danger"
              size="small"
              @click="closeShortage(row)"
            >
              关闭
            </el-button>
            <span v-else class="tip">{{ row.closedReason || '-' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
/** 印刷多资源齐套（dev-20261126-002）。 */
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import ProductSelector from '@/components/Selector/ProductSelector.vue'
import {
  printingKitApi,
  type KitResult,
  type KitShortage,
} from '@/api/production/printingKit'

defineOptions({ name: 'PrintingKit' })

const router = useRouter()
const productId = ref<number | null>(null)
const productKit = ref<KitResult | null>(null)
const orderId = ref<number>()
const orderKit = ref<KitResult | null>(null)
const shortages = ref<KitShortage[]>([])
const statusFilter = ref<number | undefined>(0)
const loading = ref(false)
const rechecking = ref(false)

async function runProductPrecheck() {
  if (!productId.value) return
  try {
    const res: any = await printingKitApi.productPrecheck(productId.value)
    productKit.value = res?.data || null
  } catch {
    productKit.value = null
  }
}

async function loadOrder() {
  if (!orderId.value) return
  try {
    const res: any = await printingKitApi.orderKit(orderId.value)
    orderKit.value = res?.data || null
  } catch {
    orderKit.value = null
  }
}

async function recheckOrder() {
  if (!orderId.value) return
  rechecking.value = true
  try {
    const res: any = await printingKitApi.recheck(orderId.value)
    orderKit.value = res?.data || null
    ElMessage.success('复查完成，台账已同步')
    await loadShortages()
  } catch {
    /* 拦截器已提示 */
  } finally {
    rechecking.value = false
  }
}

async function loadShortages() {
  loading.value = true
  try {
    const res: any = await printingKitApi.shortages({ status: statusFilter.value })
    shortages.value = res?.data || []
  } catch {
    shortages.value = []
  } finally {
    loading.value = false
  }
}

async function closeShortage(row: KitShortage) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt('请填写关闭原因', '关闭缺口', {
      inputPlaceholder: '例如：已用同版本备用版替代',
      inputValidator: (v: string) => (v && v.trim() ? true : '原因不能为空'),
    })
    reason = value.trim()
  } catch {
    return
  }
  try {
    await printingKitApi.close(row.id, reason)
    ElMessage.success('已关闭')
    await loadShortages()
  } catch {
    /* 拦截器已提示 */
  }
}

function goMakePlate(row: KitShortage) {
  router.push({
    path: '/engineering/print-resources',
    query: { versionId: row.resourceKey || undefined },
  })
}

onMounted(() => {
  loadShortages()
})
</script>

<style scoped>
.printing-kit .card { margin-bottom: 16px; }
.card-head { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.tip { color: #909399; font-size: 12px; }
</style>
