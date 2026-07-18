<template>
  <el-dialog
    v-model="dialogVisible"
    :title="dialogTitle"
    width="400px"
    :before-close="handleClose"
    destroy-on-close
  >
    <div class="delete-content">
      <el-alert
        v-if="isBatchDelete"
        type="warning"
        :title="`确定要删除选中的 ${selectedRows.length} 个订单吗？`"
        :closable="false"
        show-icon
      >
        <template #default>
          <div class="alert-content">
            <p>此操作将永久删除以下订单：</p>
            <ul class="order-list">
              <li v-for="order in selectedRows.slice(0, 5)" :key="order.orderId">
                {{ order.orderNo }} - {{ order.productName }}
              </li>
              <li v-if="selectedRows.length > 5">... 等 {{ selectedRows.length - 5 }} 个订单</li>
            </ul>
            <p class="warning-text">删除后无法恢复，请谨慎操作！</p>
          </div>
        </template>
      </el-alert>

      <el-alert
        v-else-if="order"
        type="warning"
        :title="`确定要删除订单 ${order.orderNo} 吗？`"
        :closable="false"
        show-icon
      >
        <template #default>
          <div class="alert-content">
            <p>订单信息：</p>
            <ul class="order-info">
              <li>产品：{{ order.productName }} ({{ order.productCode }})</li>
              <li>数量：{{ order.plannedQuantity }}</li>
              <li>状态：{{ order.statusLabel }}</li>
            </ul>
            <p class="warning-text">删除后无法恢复，请谨慎操作！</p>
          </div>
        </template>
      </el-alert>

      <el-form
        v-if="showReasonInput"
        ref="formRef"
        :model="formData"
        :rules="formRules"
        label-width="80px"
        class="reason-form"
      >
        <el-form-item label="删除原因" prop="reason">
          <el-input
            v-model="formData.reason"
            type="textarea"
            :rows="3"
            placeholder="请输入删除原因（可选）"
            maxlength="200"
            show-word-limit
          />
        </el-form-item>
      </el-form>
    </div>

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="handleClose" :disabled="loading">取消</el-button>
        <el-button type="danger" @click="handleConfirm" :loading="loading"> 确认删除 </el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import type { ProductionOrderVO } from '@/types/production/order'

interface Props {
  visible: boolean
  order?: ProductionOrderVO | null
  selectedRows?: ProductionOrderVO[]
  loading?: boolean
  requireReason?: boolean
}

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'confirm', reason?: string): void
  (e: 'close'): void
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  order: null,
  selectedRows: () => [],
  loading: false,
  requireReason: false,
})

const emit = defineEmits<Emits>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (value: boolean) => emit('update:visible', value),
})

// 表单引用
const formRef = ref<FormInstance>()

// 表单数据
const formData = reactive({
  reason: '',
})

// 表单验证规则
const formRules: FormRules = {
  reason: [
    { required: props.requireReason, message: '请输入删除原因', trigger: 'blur' },
    { max: 200, message: '删除原因不能超过200个字符', trigger: 'blur' },
  ],
}

// 计算属性
const isBatchDelete = computed(() => {
  return props.selectedRows.length > 0
})

const dialogTitle = computed(() => {
  if (isBatchDelete.value) {
    return `批量删除订单 (${props.selectedRows.length}个)`
  }
  return '删除订单'
})

const showReasonInput = computed(() => {
  return props.requireReason || isBatchDelete.value
})

// 方法
const handleClose = () => {
  emit('update:visible', false)
  emit('close')
}

const handleConfirm = async () => {
  if (showReasonInput.value && formRef.value) {
    const valid = await formRef.value.validate()
    if (!valid) return
  }

  emit('confirm', formData.reason || undefined)
}
</script>

<style scoped>
.delete-content {
  margin: -20px 0;
}

.alert-content {
  margin-top: 10px;
}

.order-list,
.order-info {
  margin: 8px 0;
  padding-left: 20px;
  color: #606266;
}

.order-list li,
.order-info li {
  margin: 4px 0;
  font-size: 14px;
}

.warning-text {
  color: #e6a23c;
  font-weight: 500;
  margin-top: 10px;
}

.reason-form {
  margin-top: 20px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
