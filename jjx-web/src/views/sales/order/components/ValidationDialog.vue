<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="1200"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :show-close="false"
    destroy-on-close
  >
    <!-- 验证结果摘要 -->
    <el-card v-if="validationResult" class="summary-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span>验证结果摘要</span>
        </div>
      </template>

      <el-row :gutter="20">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">订单编号</div>
            <div class="stat-value">{{ validationResult.orderNo }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">是否有效</div>
            <div class="stat-value">
              <el-tag v-if="validationResult.canSubmit" type="success">有效</el-tag>
              <el-tag v-else type="danger">无效</el-tag>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">是否可以提交</div>
            <div class="stat-value">
              <el-tag v-if="validationResult.canSubmit" type="success">可以</el-tag>
              <el-tag v-else type="danger">不可以</el-tag>
            </div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">验证耗时</div>
            <div class="stat-value">{{ validationDuration }}ms</div>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="20" class="mt-4">
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">错误</div>
            <div class="stat-value stat-error">{{ validationResult.errorCount ?? 0 }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">警告</div>
            <div class="stat-value stat-warning">{{ validationResult.warningCount ?? 0 }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">提示</div>
            <div class="stat-value">{{ validationResult.infoCount ?? 0 }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-item">
            <div class="stat-label">验证时间</div>
            <div class="stat-value">{{ formatTime(validationTime) }}</div>
          </div>
        </el-col>
      </el-row>
    </el-card>
    <el-card shadow="never">
      <el-table :data="validationResult?.items" border>
        <el-table-column prop="productCode" label="产品编码" width="120" />
        <el-table-column prop="productName" label="产品名称" width="200" />
        <el-table-column prop="productStatus" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="ProductEnum.status.getTagProps(row.productStatus)?.type">
              {{ ProductEnum.status.getLabel(row.productStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="bomCode" label="BOM" width="200" />
        <!-- <el-table-column prop="isBomCurrentVersion" label="是否生效BOM版本" width="180" /> -->
        <el-table-column prop="bomVersion" label="BOM版本" width="120" />
        <el-table-column prop="routingCode" label="工艺路线" width="280" />
        <!-- <el-table-column prop="isRoutingCurrentVersion" label="是否生效工艺路线版本" width="180" /> -->
        <el-table-column prop="routingVersion" label="工艺路线版本" width="120" />
      </el-table>
    </el-card>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleCancel" :disabled="loading">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">
          确认提交审核
        </el-button>
        <el-button type="danger" @click="handleCancel" :disabled="submitting"> 关闭 </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { OrderValidationService } from '@/services/sales/order-validation.service'
import { orderStatusApi } from '@/api/sales/orderStatus'
import type { OrderReferValidationVO } from '@/types/sales/order'
import { ProductEnum } from '@/enums'
const props = defineProps<{
  modelValue: boolean
  orderId: number
  orderNo: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
  cancel: []
}>()

// 响应式数据
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const title = computed(() => `订单验证 - ${props.orderNo}`)
const loading = ref(false)
const submitting = ref(false)
const error = ref<string | null>(null)
const validationResult = ref<OrderReferValidationVO | null>(null)
const activeTab = ref('errors')
const validationDuration = ref(0)
const validationTime = ref('')

// 获取验证服务实例
const validationService = OrderValidationService.getInstance()

// 取消
const handleCancel = () => {
  visible.value = false
  emit('cancel')
}

// 提交审核
const handleSubmit = async () => {
  if (!validationResult.value || !validationResult.value.canSubmit) {
    ElMessage.error('订单验证未通过，无法提交审核')
    return
  }

  submitting.value = true
  try {
    // 2026-08-11 修复：接入真实提交审核 API（原为 TODO 占位）
    await orderStatusApi.submitOrderReview(props.orderId)
    ElMessage.success('订单提交审核成功')
    visible.value = false
    emit('success')
  } catch (error) {
    console.error('提交审核失败:', error)
    ElMessage.error('提交审核失败')
  } finally {
    submitting.value = false
  }
}

// 执行验证
const performValidation = async () => {
  loading.value = true
  error.value = null
  validationResult.value = null
  const start = Date.now()
  try {
    const validationRequest = {
      orderId: props.orderId,
      validateProducts: true,
      validateBom: true,
      validateRouting: true,
      validateCapacity: true,
      validateCost: true,
      options: {
        strictMode: true,
        includeWarnings: true,
      },
    }

    validationResult.value = await validationService.validateOrderForReview(validationRequest)
    validationDuration.value = Date.now() - start
    validationTime.value = new Date().toISOString()
    console.log(validationResult)
  } catch (err) {
    error.value = err instanceof Error ? err.message : String(err)
    console.error('订单验证失败:', err)
  } finally {
    loading.value = false
  }
}

const formatTime = (t: string) => {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 19)
}

// 监听对话框显示状态
watch(
  () => props.modelValue,
  (newValue) => {
    if (newValue) {
      // 对话框打开时执行验证
      performValidation()
    } else {
      // 对话框关闭时重置状态
      validationResult.value = null
      error.value = null
      activeTab.value = 'errors'
    }
  },
  { immediate: true }
)
</script>

<style scoped lang="scss">
.summary-card {
  margin-bottom: 20px;

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .stat-item {
    text-align: center;

    .stat-label {
      font-size: 12px;
      color: #909399;
      margin-bottom: 4px;
    }

    .stat-value {
      font-size: 16px;
      font-weight: 500;
    }
  }
}

.validation-tabs {
  margin-top: 20px;
}

.loading-container,
.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;

  .loading-text,
  .error-text {
    margin-top: 16px;
    font-size: 14px;
    color: #606266;
  }

  .error-text {
    color: #f56c6c;
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.stat-error {
  color: #f56c6c;
  font-weight: 600;
}

.stat-warning {
  color: #e6a23c;
  font-weight: 600;
}

.mt-4 {
  margin-top: 16px;
}
</style>
