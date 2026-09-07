<template>
  <el-dialog
    :model-value="visible"
    :title="dialogTitle"
    width="1120px"
    top="4vh"
    append-to-body
    destroy-on-close
    class="sales-order-form-dialog"
    @update:model-value="(v: boolean) => emit('update:modelValue', v)"
    @open="handleOpen"
  >
    <div v-loading="innerLoading" class="form-body">
      <!-- 复用标准销售订单表单（2026-09-07：新增/修改/转量产共用同一套表单组件） -->
      <OrderForm
        v-if="mountedOnce"
        ref="orderFormRef"
        :is-edit="mode === 'edit'"
        :order-id="mode === 'edit' ? orderId ?? undefined : undefined"
        :sample-order-id="mode === 'convert' ? orderId ?? undefined : undefined"
        @success="handleSuccess"
        @cancel="handleClose"
      />
    </div>

    <template #footer>
      <el-button @click="handleClose">取 消</el-button>
      <el-button type="primary" :loading="submitting" @click="handleSubmit">
        {{ submitLabel }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import OrderForm from './OrderForm.vue'

defineOptions({
  name: 'SalesOrderFormDialog',
})

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    /** 表单用途：convert=样品转量产（orderId=样品单ID）；add/edit 为后续弹窗化预留 */
    mode?: 'convert' | 'add' | 'edit'
    /** convert=样品单ID；edit=订单ID；add 忽略 */
    orderId?: number | null
    orderNo?: string
  }>(),
  {
    mode: 'convert',
    orderId: null,
    orderNo: '',
  }
)

const emit = defineEmits<{
  'update:modelValue': [value: boolean]
  success: []
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (v: boolean) => emit('update:modelValue', v),
})

const orderFormRef = ref<InstanceType<typeof OrderForm> | null>(null)
const submitting = ref(false)
const innerLoading = ref(false)
const mountedOnce = ref(false)

const dialogTitle = computed(() => {
  if (props.mode === 'convert') return `转量产 · ${props.orderNo || '来自样品单'}`
  if (props.mode === 'edit') return `修改销售订单 · ${props.orderNo || ''}`
  return '新增销售订单'
})

const submitLabel = computed(() => {
  if (props.mode === 'convert') return '确认转量产'
  if (props.mode === 'edit') return '保 存'
  return '提 交'
})

watch(
  () => props.modelValue,
  (v) => {
    if (v) mountedOnce.value = true
  }
)

function handleOpen() {
  submitting.value = false
}

function handleClose() {
  visible.value = false
}

async function handleSubmit() {
  if (!orderFormRef.value) return
  submitting.value = true
  try {
    // OrderForm.submitForm 成功后内部 emit('success') → handleSuccess 关闭弹窗
    await orderFormRef.value.submitForm()
  } catch (error) {
    console.error('提交失败:', error)
  } finally {
    submitting.value = false
  }
}

function handleSuccess() {
  visible.value = false
  emit('success')
}
</script>

<style scoped>
.sales-order-form-dialog :deep(.el-dialog__body) {
  max-height: 76vh;
  overflow-y: auto;
}
</style>
