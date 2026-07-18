<template>
  <el-dialog
    v-model="visible"
    title="产品详情"
    width="1200px"
    append-to-body
    :close-on-click-modal="false"
  >
    <!-- 顶部固定操作栏 -->
    <div class="dialog-header-actions" v-if="!loading && !error">
      <div class="action-left">
        <el-tag :type="getProductStatusTagType(productStatus)" size="large">
          {{ getProductStatusLabel(productStatus) }}
        </el-tag>
        <el-tag v-if="productType === 'custom'" type="success" size="large">定制产品</el-tag>
        <el-tag v-else type="primary" size="large">标准产品</el-tag>
      </div>
    </div>

    <!-- 滚动内容区域 -->
    <div class="scroll-content" v-loading="loading">
      <ProductDetail
        ref="productDetailRef"
        :product-id="productId"
        @loaded="handleLoaded"
        @status-change="handleStatusChange"
      />
    </div>

    <!-- 底部固定操作栏 -->
    <div class="dialog-footer-actions">
      <div class="footer-right">
        <el-button @click="visible = false">关闭</el-button>
        <el-button type="primary" @click="handleSubmit" v-if="canSubmit">
          <el-icon><Promotion /></el-icon>提交审核
        </el-button>
      </div>
    </div>

    <template #footer></template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Promotion } from '@element-plus/icons-vue'
import { productApi } from '@/api/product'
import { ProductEnum } from '@/enums'
import ProductDetail from './ProductDetail.vue'
import type { ProductFullVO } from '@/types/product'

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
  (e: 'success'): void
}

const emit = defineEmits<Emits>()
const router = useRouter()
const productDetailRef = ref()

// 响应式数据
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const loading = ref(false)
const error = ref<string>('')
const productStatus = ref<number>()
const productType = ref<string>()

// 获取产品状态
const getProductStatusLabel = (status?: number) => {
  return ProductEnum.status.getLabel(status ?? 0)
}

const getProductStatusTagType = (status?: number) => {
  return ProductEnum.status.getTagProps(status ?? 0).type
}

// 计算属性：是否可以提交审核（草稿状态）
const canSubmit = computed(() => {
  return productStatus.value === 1
})

// 计算属性：是否可以删除（草稿或已驳回状态）
const canDelete = computed(() => {
  return productStatus.value === 1 || productStatus.value === 5
})

// 加载完成回调
const handleLoaded = (data: ProductFullVO) => {
  productStatus.value = data.product?.productStatus
  productType.value = data.product?.productType
  loading.value = false
  error.value = ''
}

// 状态变化回调
const handleStatusChange = (status: number) => {
  productStatus.value = status
}

// 提交审核
const handleSubmit = async () => {
  if (!props.productId) return

  try {
    await ElMessageBox.confirm('确定要提交该产品审核吗？', '提交审核', { type: 'info' })
    await productApi.submitApprove(props.productId)
    ElMessage.success('提交成功')

    // 刷新子组件
    productDetailRef.value?.loadData()
    emit('success')
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error('提交失败')
    }
  }
}

// 监听visible变化，重置状态
watch(
  () => visible.value,
  (newVisible) => {
    if (newVisible && props.productId) {
      loading.value = true
      error.value = ''
      // 强制刷新子组件数据（解决第二次打开时 productId 未变化导致不触发加载的问题）
      nextTick(() => {
        productDetailRef.value?.loadData()
      })
    } else {
      productStatus.value = undefined
      productType.value = undefined
      loading.value = false
      error.value = ''
    }
  }
)
</script>
<style scoped lang="scss">
.dialog-header-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background-color: #f5f7fa;
  border-bottom: 1px solid #e4e7ed;
  margin: -20px 0 0 0;
}

.scroll-content {
  max-height: 60vh;
  overflow-y: auto;
  margin: 0 -20px;
  padding: 0 20px;
}

.dialog-footer-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  background-color: #fff;
  border-top: 1px solid #e4e7ed;
}

.action-left,
.action-right {
  display: flex;
  gap: 8px;
}

.footer-left,
.footer-right {
  display: flex;
  gap: 8px;
}
</style>
