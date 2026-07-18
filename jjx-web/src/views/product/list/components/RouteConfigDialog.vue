<template>
  <el-dialog
    v-model="visible"
    title="配置工艺路线"
    width="800px"
    append-to-body
    :close-on-click-modal="false"
  >
    <!-- 产品信息 -->
    <el-descriptions :column="2" border style="margin-bottom: 20px">
      <el-descriptions-item label="产品编码" label-class-name="desc-label">
        {{ product?.productCode || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="产品名称" label-class-name="desc-label">
        {{ product?.productName || '-' }}
      </el-descriptions-item>
    </el-descriptions>

    <!-- 当前已配置的工艺路线信息 -->
    <el-alert
      v-if="currentRouteInfo"
      :title="`当前已配置: ${currentRouteInfo.routeCode}_${currentRouteInfo.routeVersion} (${currentRouteInfo.routeName})`"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <!-- 搜索区域 -->
    <div class="search-area" style="margin-bottom: 16px">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索工艺路线编码/名称"
        clearable
        style="width: 300px"
        @input="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <span style="margin-left: 12px; color: #909399; font-size: 13px">
        共 {{ filteredRouteList.length }} 个已审批工艺路线
      </span>
    </div>

    <!-- 工艺路线列表 -->
    <el-table
      v-loading="loading"
      :data="filteredRouteList"
      border
      style="width: 100%"
      highlight-current-row
      @row-click="handleRowClick"
    >
      <el-table-column width="50" align="center">
        <template #default="scope">
          <el-radio v-model="selectedRouteId" :label="scope.row.routingId" @click.stop>
            &nbsp;
          </el-radio>
        </template>
      </el-table-column>
      <el-table-column label="工艺编码" prop="routingCode" width="160" />
      <el-table-column label="工艺名称" prop="routingName" min-width="160" />
      <el-table-column label="版本" prop="routingVersion" width="80" align="center" />
      <el-table-column label="工序数" width="80" align="center">
        <template #default="scope">
          <el-tag size="small">{{ scope.row.processCount || scope.row.items?.length || 0 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="总工时(人)" width="110" align="right">
        <template #default="scope">
          <span style="color: #409eff; font-weight: 600">
            {{ scope.row.totalLaborHours ?? '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="总工时(机)" width="110" align="right">
        <template #default="scope">
          <span style="color: #67c23a; font-weight: 600">
            {{ scope.row.totalMachineHours ?? '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="当前版本" width="90" align="center">
        <template #default="scope">
          <el-tag v-if="scope.row.isCurrent" type="success" size="small">是</el-tag>
          <span v-else class="text-muted">否</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center" fixed="right">
        <template #default="scope">
          <el-button link type="primary" size="small" @click.stop="handlePreview(scope.row)">
            预览
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 空状态 -->
    <div v-if="!loading && routeList.length === 0" style="text-align: center; padding: 60px 0">
      <el-icon :size="80" color="#c0c4cc"><FolderDelete /></el-icon>
      <p style="color: #909399; font-size: 14px; margin: 16px 0 8px">暂无已审批的工艺路线</p>
      <p style="color: #c0c4cc; font-size: 13px; margin-bottom: 20px">
        请先创建并审批工艺路线，或直接前往工艺路线管理模块
      </p>
      <el-button type="primary" @click="handleGoCreateRoute">去创建工艺路线</el-button>
    </div>

    <!-- 无搜索结果 -->
    <div
      v-if="!loading && routeList.length > 0 && filteredRouteList.length === 0"
      style="text-align: center; padding: 40px 0"
    >
      <p style="color: #c0c4cc; font-size: 14px">未找到匹配的工艺路线</p>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button
          type="primary"
          :disabled="!selectedRouteId"
          :loading="saving"
          @click="handleSave"
        >
          确认配置
        </el-button>
        <el-button @click="visible = false">取 消</el-button>
      </div>
    </template>

    <!-- 工艺路线预览对话框 -->
    <RouteDetailDialog v-model="previewVisible" :routing-id="previewRouteId" />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, FolderDelete } from '@element-plus/icons-vue'
import RouteDetailDialog from '../../route/components/RouteDetailDialog.vue'
import { productRouteApi } from '@/api/product/routing'
import { productApi } from '@/api/product'
import type { ProductRoutingVO } from '@/types/product/routing'
import type { ProductVo } from '@/types/product'

interface Props {
  modelValue: boolean
  product?: ProductVo
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  product: undefined,
})

const emit = defineEmits<Emits>()
const router = useRouter()

// 对话框可见性
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

// 响应式数据
const loading = ref(false)
const saving = ref(false)
const routeList = ref<ProductRoutingVO[]>([])
const searchKeyword = ref('')
const selectedRouteId = ref<number | undefined>(undefined)
const previewVisible = ref(false)
const previewRouteId = ref<number | undefined>(undefined)

// 当前已配置的工艺路线信息
const currentRouteInfo = computed(() => {
  if (!props.product?.routeCode) return null
  return {
    routeCode: props.product.routeCode,
    routeName: props.product.routeName,
    routeVersion: props.product.routeVersion,
  }
})

// 过滤后的工艺路线列表
const filteredRouteList = computed(() => {
  if (!searchKeyword.value) return routeList.value
  const keyword = searchKeyword.value.toLowerCase()
  return routeList.value.filter(
    (route) =>
      route.routingCode?.toLowerCase().includes(keyword) ||
      route.routingName?.toLowerCase().includes(keyword)
  )
})

// 搜索
const handleSearch = () => {
  // 计算属性会自动过滤
}

// 行点击
const handleRowClick = (row: ProductRoutingVO) => {
  selectedRouteId.value = row.routingId
}

// 预览工艺路线
const handlePreview = async (row: ProductRoutingVO) => {
  previewRouteId.value = row.routingId
  previewVisible.value = true
}

// 去创建工艺路线
const handleGoCreateRoute = () => {
  visible.value = false
  router.push('/product/route')
}

// 加载已审批工艺路线列表
const loadApprovedRoutes = async () => {
  if (!props.product?.productId) return

  loading.value = true
  selectedRouteId.value = undefined
  try {
    const res = await productRouteApi.getApprovedRouteList(props.product.productId)
    routeList.value = (res.data || []) as ProductRoutingVO[]
  } catch (error) {
    console.error('获取已审批工艺路线列表失败:', error)
    ElMessage.error('获取工艺路线列表失败')
    routeList.value = []
  } finally {
    loading.value = false
  }
}

// 确认配置
const handleSave = async () => {
  if (!selectedRouteId.value || !props.product?.productId) return

  saving.value = true
  try {
    // 调用配置工艺路线独立接口
    await productApi.configRoute(props.product.productId, selectedRouteId.value)
    ElMessage.success('工艺路线配置成功')
    visible.value = false
    emit('success')
  } catch (error) {
    console.error('配置工艺路线失败:', error)
    ElMessage.error('配置工艺路线失败')
  } finally {
    saving.value = false
  }
}

// 监听对话框打开
watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal) {
      loadApprovedRoutes()
    }
  }
)
</script>

<style scoped>
.dialog-footer {
  text-align: right;
}

.text-muted {
  color: #c0c4cc;
}

.desc-label {
  font-weight: 600;
}

.search-area {
  display: flex;
  align-items: center;
}
</style>
