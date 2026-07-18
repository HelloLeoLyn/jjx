<template>
  <el-dialog
    title="产品配置验证"
    v-model="visible"
    width="600px"
    append-to-body
    @close="handleClose"
  >
    <div v-if="loading" class="loading-container">
      <el-skeleton :rows="6" animated />
    </div>

    <div v-else-if="validationResult">
      <el-alert
        :title="validationResult.isValid ? '配置完整' : '配置不完整'"
        :type="validationResult.isValid ? 'success' : 'warning'"
        :closable="false"
        show-icon
      >
        <template #default>
          <div>完整度评分: {{ validationResult.completenessScore }}分</div>
        </template>
      </el-alert>

      <el-divider content-position="left">配置摘要</el-divider>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="产品">
          {{ validationResult.configSummary.product.productName }} ({{
            validationResult.configSummary.product.productCode
          }})
        </el-descriptions-item>
        <el-descriptions-item label="BOM配置">
          <el-tag :type="validationResult.configSummary.hasBom ? 'success' : 'warning'">
            {{ validationResult.configSummary.hasBom ? '已配置' : '未配置' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="工艺路线配置">
          <el-tag :type="validationResult.configSummary.hasRoute ? 'success' : 'warning'">
            {{ validationResult.configSummary.hasRoute ? '已配置' : '未配置' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="配置完整性">
          <el-progress
            :percentage="validationResult.completenessScore"
            :status="validationResult.isValid ? 'success' : 'warning'"
            :stroke-width="15"
          />
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left" v-if="validationResult.missingComponents.length > 0"
        >缺失组件</el-divider
      >
      <el-alert
        v-if="validationResult.missingComponents.length > 0"
        :title="`缺失 ${validationResult.missingComponents.length} 个组件`"
        type="warning"
        :closable="false"
        show-icon
      >
        <template #default>
          <div v-for="(component, index) in validationResult.missingComponents" :key="index">
            {{ component }}
          </div>
        </template>
      </el-alert>

      <el-divider content-position="left" v-if="validationResult.validationErrors.length > 0"
        >验证错误</el-divider
      >
      <el-alert
        v-if="validationResult.validationErrors.length > 0"
        :title="`发现 ${validationResult.validationErrors.length} 个错误`"
        type="error"
        :closable="false"
        show-icon
      >
        <template #default>
          <div v-for="(error, index) in validationResult.validationErrors" :key="index">
            <strong>{{ error.component }}:</strong> {{ error.errorMessage }}
          </div>
        </template>
      </el-alert>
    </div>

    <div v-else class="empty-container">
      <el-empty description="暂无验证数据" :image-size="60" />
    </div>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="visible = false">关闭</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { ProductConfigValidation } from '@/types/product'

// Props定义
interface Props {
  modelValue: boolean
  productId?: number
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  productId: undefined,
})

// Emits定义
interface Emits {
  (e: 'update:modelValue', value: boolean): void
}

const emit = defineEmits<Emits>()

// 响应式数据
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const validationResult = ref<ProductConfigValidation | null>(null)
const loading = ref(false)

// 监听visible变化
watch(
  () => visible.value,
  (newVisible) => {
    if (newVisible && props.productId) {
      loadValidation()
    } else {
      validationResult.value = null
    }
  }
)

// 生成mock验证数据
const generateMockValidation = (productId: number): ProductConfigValidation => {
  const hasBom = Math.random() > 0.3
  const hasRoute = Math.random() > 0.3
  const missingComponents: string[] = []
  const validationErrors: any[] = []

  if (!hasBom) {
    missingComponents.push('BOM配置')
  }
  if (!hasRoute) {
    missingComponents.push('工艺路线配置')
  }

  // 随机生成一些验证错误
  if (hasBom && Math.random() > 0.6) {
    validationErrors.push({
      component: 'BOM',
      errorCode: 'BOM-001',
      errorMessage: 'BOM物料清单中存在未审核的物料',
      severity: 'warning',
    })
  }
  if (hasRoute && Math.random() > 0.6) {
    validationErrors.push({
      component: '工艺路线',
      errorCode: 'ROUTE-001',
      errorMessage: '工艺路线中存在未配置工时的工序',
      severity: 'warning',
    })
  }

  const totalItems = 3
  const missingCount = missingComponents.length
  const errorCount = validationErrors.length
  const completenessScore = Math.max(
    0,
    Math.round(((totalItems - missingCount) / totalItems) * 100 - errorCount * 10)
  )

  return {
    productId,
    productCode: `PROD-${String(productId).padStart(3, '0')}`,
    productName: `产品${String.fromCharCode(64 + productId)}`,
    isValid:
      missingComponents.length === 0 &&
      validationErrors.filter((e) => e.severity === 'error').length === 0,
    completenessScore,
    missingComponents,
    validationErrors,
    configSummary: {
      product: {
        productId,
        productCode: `PROD-${String(productId).padStart(3, '0')}`,
        productName: `产品${String.fromCharCode(64 + productId)}`,
        categoryId: 1,
        categoryName: '电子元器件',
        specification: '标准规格',
        unit: '个',
        weight: 0,
        volume: 0,
        material: '',
        color: '',
        brand: '',
        model: '',
        description: '',
        productStatus: 1,
        approveStatus: 1,
        remark: '',
        createTime: '2024-01-15 10:30:00',
        updateTime: '2024-01-15 10:30:00',
        createBy: 'admin',
        updateBy: 'admin',
      },
      hasBom,
      hasRoute,
      isComplete: missingComponents.length === 0,
    },
  }
}

// 加载验证数据（使用mock）
const loadValidation = async () => {
  if (!props.productId) return

  loading.value = true
  try {
    // 模拟网络延迟
    await new Promise((resolve) => setTimeout(resolve, 500))
    validationResult.value = generateMockValidation(props.productId)
  } catch (error) {
    console.error('加载配置验证失败:', error)
    validationResult.value = null
  } finally {
    loading.value = false
  }
}

// 关闭时重置
const handleClose = () => {
  validationResult.value = null
}

// 暴露方法给父组件
defineExpose({
  loadValidation,
})
</script>

<style scoped>
.loading-container {
  padding: 20px;
}

.empty-container {
  padding: 40px 0;
  text-align: center;
}

.dialog-footer {
  text-align: right;
}
</style>
