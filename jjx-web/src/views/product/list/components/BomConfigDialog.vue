<template>
  <el-dialog
    v-model="visible"
    title="配置BOM"
    width="1000px"
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

    <!-- 当前已配置的BOM信息 -->
    <el-alert
      v-if="currentBomInfo"
      :title="`当前已配置: ${currentBomInfo.bomCode}_${currentBomInfo.bomVersion} (${currentBomInfo.bomName})`"
      type="info"
      show-icon
      :closable="false"
      style="margin-bottom: 16px"
    />

    <!-- 搜索区域 -->
    <div class="search-area" style="margin-bottom: 16px">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索BOM编码/名称"
        clearable
        style="width: 300px"
        @input="handleSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
      </el-input>
      <span style="margin-left: 12px; color: #909399; font-size: 13px">
        共 {{ filteredBomList.length }} 个已审批BOM
      </span>
    </div>

    <!-- BOM列表 -->
    <el-table
      v-loading="loading"
      :data="filteredBomList"
      border
      style="width: 100%"
      highlight-current-row
      @row-click="handleRowClick"
    >
      <el-table-column width="50" align="center">
        <template #default="scope">
          <el-radio v-model="selectedBomId" :label="scope.row.bomId" @click.stop> </el-radio>
        </template>
      </el-table-column>
      <el-table-column label="BOM编码" prop="bomCode" width="160" />
      <el-table-column label="BOM名称" prop="bomName" min-width="160" />
      <el-table-column label="版本" prop="bomVersion" width="80" align="center" />
      <el-table-column label="物料数" width="80" align="center">
        <template #default="scope">
          <el-tag size="small">{{ scope.row.items?.length || 0 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="总成本" width="130" align="right">
        <template #default="scope">
          <span style="color: #e6a23c; font-weight: 600">
            ¥ {{ formatCurrency(calcTotalCost(scope.row.items)) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="生效日期" prop="effectiveDate" width="120" />
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
    <div v-if="!loading && bomList.length === 0" style="text-align: center; padding: 60px 0">
      <el-icon :size="80" color="#c0c4cc"><FolderDelete /></el-icon>
      <p style="color: #909399; font-size: 14px; margin: 16px 0 8px">暂无已审批的BOM</p>
      <p style="color: #c0c4cc; font-size: 13px; margin-bottom: 20px">
        请先创建并审批BOM，或直接前往BOM管理模块
      </p>
      <el-button type="primary" @click="handleGoCreateBom">去创建BOM</el-button>
    </div>

    <!-- 无搜索结果 -->
    <el-empty
      v-if="!loading && bomList.length > 0 && filteredBomList.length === 0"
      description="未找到匹配的BOM"
      :image-size="80"
      style="padding: 40px 0"
    />

    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" :disabled="!selectedBomId" :loading="saving" @click="handleSave">
          确认配置
        </el-button>
        <el-button @click="visible = false">取 消</el-button>
      </div>
    </template>

    <!-- BOM详情对话框 -->
    <BomDetail v-model="previewVisible" :bom-id="previewBomId" />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Search, FolderDelete } from '@element-plus/icons-vue'
import BomDetail from '../../bom/components/BomDetail.vue'
import { productBomApi } from '@/api/product/bom'
import { productApi } from '@/api/product'
import type { EngineeringBomVO, EngineeringBomItem } from '@/types/product/bom'
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
const bomList = ref<EngineeringBomVO[]>([])
const searchKeyword = ref('')
const selectedBomId = ref<number | undefined>(undefined)
const previewVisible = ref(false)
const previewBomId = ref<number | undefined>(undefined)

// 当前已配置的BOM信息
const currentBomInfo = computed(() => {
  if (!props.product?.bomCode) return null
  return {
    bomCode: props.product.bomCode,
    bomName: props.product.bomName,
    bomVersion: props.product.bomVersion,
  }
})

// 过滤后的BOM列表
const filteredBomList = computed(() => {
  if (!searchKeyword.value) return bomList.value
  const keyword = searchKeyword.value.toLowerCase()
  return bomList.value.filter(
    (bom) =>
      bom.bomCode?.toLowerCase().includes(keyword) || bom.bomName?.toLowerCase().includes(keyword)
  )
})

// 计算总成本
const calcTotalCost = (items?: EngineeringBomItem[]): number => {
  if (!items || items.length === 0) return 0
  return items.reduce((sum, item) => sum + ((item as any).totalPrice || 0), 0)
}

// 格式化金额
const formatCurrency = (value: number): string => {
  if (value === undefined || value === null) return '0.00'
  return value.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

// 搜索
const handleSearch = () => {
  // 计算属性会自动过滤
}

// 行点击
const handleRowClick = (row: EngineeringBomVO) => {
  selectedBomId.value = row.bomId
}

// 预览BOM
const handlePreview = async (row: EngineeringBomVO) => {
  previewBomId.value = row.bomId
  previewVisible.value = true
}

// 去创建BOM
const handleGoCreateBom = () => {
  visible.value = false
  router.push('/engineering/bom')
}

// 加载已审批BOM列表
const loadApprovedBoms = async () => {
  if (!props.product?.productId) return

  loading.value = true
  selectedBomId.value = undefined
  try {
    const res = await productBomApi.getApprovedBomByProductId(props.product.productId)
    bomList.value = (res.data || []).map((item: any) => {
      // 如果返回的是 BomSimpleVo，需要获取完整详情
      if (!item.items) {
        return { ...item, items: [] }
      }
      return item
    }) as EngineeringBomVO[]

    // 如果返回的是简单VO，需要获取完整详情
    if (bomList.value.length > 0 && !bomList.value[0].items) {
      const fullList: EngineeringBomVO[] = []
      for (const bom of bomList.value) {
        try {
          const detailRes = await productBomApi.getEngineeringBomInfo(bom.bomId)
          fullList.push(detailRes.data as EngineeringBomVO)
        } catch {
          fullList.push(bom)
        }
      }
      bomList.value = fullList
    }
  } catch (error) {
    console.error('获取已审批BOM列表失败:', error)
    ElMessage.error('获取BOM列表失败')
    bomList.value = []
  } finally {
    loading.value = false
  }
}

// 确认配置
const handleSave = async () => {
  if (!selectedBomId.value || !props.product?.productId) return

  saving.value = true
  try {
    // 调用配置BOM接口
    await productApi.configBom(props.product.productId, selectedBomId.value)
    ElMessage.success('BOM配置成功')
    visible.value = false
    emit('success')
  } catch (error) {
    console.error('配置BOM失败:', error)
    ElMessage.error('配置BOM失败')
  } finally {
    saving.value = false
  }
}

// 监听对话框打开
watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal) {
      loadApprovedBoms()
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
