<!-- components/ReviewDialog.vue -->
<template>
  <el-dialog
    v-model="dialogVisible"
    title="订单审核"
    width="1200px"
    :close-on-click-modal="false"
    :close-on-press-escape="!loading"
    :show-close="!loading"
    @close="handleClose"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="100px">
      <el-form-item label="订单信息" prop="orderNo">
        <el-input v-model="formData.orderNo" disabled placeholder="订单编号" />
      </el-form-item>

      <el-form-item label="客户名称" prop="customerName">
        <el-input v-model="formData.customerName" disabled placeholder="客户名称" />
      </el-form-item>

      <el-form-item label="订单金额" prop="totalAmount">
        <el-input v-model="formData.totalAmount" disabled placeholder="订单金额">
          <template #append>元</template>
        </el-input>
      </el-form-item>

      <el-form-item label="审核结果" prop="result" required>
        <el-radio-group v-model="formData.result" :disabled="loading">
          <el-radio value="approve">审核通过</el-radio>
          <el-radio value="reject">审核驳回</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="formData.result === 'reject'" label="驳回原因" prop="remark">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="4"
          placeholder="请填写驳回原因"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-form-item v-else-if="formData.result === 'approve'" label="审核意见" prop="remark">
        <el-input
          v-model="formData.remark"
          type="textarea"
          :rows="3"
          placeholder="请输入审核意见（选填）"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <!-- 审核通过与驳回都保存本次选用的资料引用。 -->
      <AttachmentCenter
        ref="attachmentCenter"
        source-type="sales_order"
        :source-id="props.orderId || null"
        :trace-id="orderDetail?.traceId"
        variant="upload-only"
        :default-document-type="REVIEW_DEFAULT_DOC_TYPE"
        :allowed-document-types="REVIEW_DOCUMENT_TYPES"
        :accept="['.pdf', '.jpg', '.jpeg', '.png', '.doc', '.docx']"
        view-perm="sales:order:view"
        edit-perm="sales:order:edit"
        @bound="(p: { refId: number }) => onBound(p.refId)"
        @unbound="(p: { refId: number }) => onUnbound(p.refId)"
      />
      <!-- 审核人信息（只读） -->
      <el-divider content-position="left">审核信息</el-divider>

      <el-form-item label="审核人">
        <el-input v-model="formData.reviewerName" disabled placeholder="当前登录用户" />
      </el-form-item>

      <el-form-item label="审核时间">
        <el-input v-model="formData.reviewTime" disabled placeholder="当前时间" />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button :disabled="loading" @click="handleClose">取消</el-button>
      <el-button
        type="primary"
        :loading="loading"
        :disabled="loading || !formData.result"
        @click="handleConfirm"
        >提交审核结果</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { orderStatusApi } from '@/api/sales/orderStatus'
import { orderApi } from '@/api/sales/order'
import { useUserStore } from '@/store/modules/user'
import AttachmentCenter from '@/components/attachment/AttachmentCenter.vue'

const props = defineProps<{
  modelValue: boolean
  orderId: number
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

// 用户信息
const userStore = useUserStore()

// 对话框可见性
const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val),
})

// 表单引用
const formRef = ref()
const loading = ref(false)

// 订单详情
const orderDetail = ref<any>(null)

// 表单数据
const formData = ref({
  orderId: 0,
  orderNo: '',
  customerName: '',
  totalAmount: '',
  result: undefined as 'approve' | 'reject' | undefined,
  remark: '',
  reviewerName: '',
  reviewTime: '',
})

// 本次审核已绑定的订单附件引用 ID（选填）
const attachmentRefIds = ref<number[]>([])
const attachmentCenter = ref<InstanceType<typeof AttachmentCenter> | null>(null)
/** 审核附件可用的二级资料类型（一级=商务资料/客户资料，dev-20261029-005） */
const REVIEW_DOCUMENT_TYPES = ['ORDER_CONFIRMATION', 'CONTRACT', 'CUSTOMER_PO', 'CUSTOMER_APPROVAL', 'OTHER']
/** 上传即绑定用的默认二级类型 */
const REVIEW_DEFAULT_DOC_TYPE = 'ORDER_CONFIRMATION'

// 只记录绑定成功的引用，临时文件 ID 不进入审核流水
function onBound(id: number) {
  if (!attachmentRefIds.value.includes(id)) {
    attachmentRefIds.value.push(id)
  }
}

function onUnbound(id: number) {
  attachmentRefIds.value = attachmentRefIds.value.filter((refId) => refId !== id)
}

// 表单验证规则
const rules = computed(() => ({
  result: [{ required: true, message: '请选择审核结果', trigger: 'change' }],
  remark: [
    {
      required: formData.value.result === 'reject',
      message: '请填写驳回原因',
      trigger: 'blur',
    },
  ],
}))

// 获取订单详情
const fetchOrderDetail = async () => {
  if (!props.orderId) return

  try {
    const response = await orderApi.getOrder(props.orderId)
    const data = response.data!

    orderDetail.value = data
    formData.value.orderId = data.orderId
    formData.value.orderNo = data.orderNo
    formData.value.customerName = data.customerName
    formData.value.totalAmount = formatAmount(data.totalAmount)
  } catch (error) {
    console.error('获取订单详情失败', error)
    ElMessage.error('获取订单详情失败')
  }
}

// 格式化金额
const formatAmount = (amount: number): string => {
  if (amount === undefined || amount === null) return '0.00'
  return amount.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

// 格式化时间
const formatTime = (date: Date): string => {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`
}

// 确认审核
const handleConfirm = async () => {
  if (loading.value) return
  if (!formData.value.result) {
    ElMessage.warning('请选择审核结果')
    return
  }
  loading.value = true
  try {
    try {
      const valid = await formRef.value?.validate()
      if (!valid) return
    } catch {
      return
    }
    if (attachmentCenter.value?.uploading) {
      ElMessage.warning('附件仍在上传，请稍候再提交')
      return
    }
    // 延后绑定（dev-20261029-004）：提交审核时把待绑定附件一次性绑成引用，再随审核结果提交
    const pendingCount = attachmentCenter.value?.pendingCount ?? 0
    if (pendingCount) {
      const result = await attachmentCenter.value!.bindPending({ documentType: REVIEW_DEFAULT_DOC_TYPE })
      if (result.failed?.length) {
        const detail = result.failed.map((item) => `${item.fileName}：${item.message}`).join('；')
        ElMessage.warning(`附件未能绑定：${detail}`)
        return
      }
      for (const item of result.bound || []) onBound(item.refId)
      if (result.status === 'document-not-saved') {
        ElMessage.warning('单据尚未保存，无法绑定附件')
        return
      }
    }
    if (formData.value.result === 'approve') {
      // 审核通过时只提交已绑定的引用 ID
      await orderStatusApi.approveOrder(
        props.orderId,
        formData.value.remark,
        attachmentRefIds.value
      )
      ElMessage.success('审核通过成功')
    } else {
      // 审核驳回
      await orderStatusApi.rejectOrder(props.orderId, formData.value.remark, attachmentRefIds.value)
      ElMessage.success('审核驳回成功')
    }

    emit('success')
    loading.value = false
    handleClose()
  } catch (error) {
    console.error('审核操作失败', error)
    ElMessage.error('审核操作失败')
  } finally {
    loading.value = false
  }
}

// 关闭对话框
const handleClose = () => {
  if (loading.value) return
  dialogVisible.value = false
  // 重置表单
  formData.value = {
    orderId: 0,
    orderNo: '',
    customerName: '',
    totalAmount: '',
    result: undefined as 'approve' | 'reject' | undefined,
    remark: '',
    reviewerName: '',
    reviewTime: '',
  }
  attachmentRefIds.value = []
  formRef.value?.resetFields()
}

// 监听对话框打开
watch(
  () => props.modelValue,
  async (val) => {
    if (val && props.orderId) {
      formData.value.result = undefined
      formData.value.remark = ''
      formRef.value?.clearValidate()
      await fetchOrderDetail()
      // 设置审核人信息
      formData.value.reviewerName = userStore.userInfo?.userName || '当前用户'
      formData.value.reviewTime = formatTime(new Date())
    }
  }
)

// 切换结果时清除上一种结果的校验提示。
watch(
  () => formData.value.result,
  () => formRef.value?.clearValidate('remark')
)
</script>

<style scoped lang="scss">
:deep(.el-dialog__body) {
  padding-top: 20px;
}

:deep(.el-divider) {
  margin: 16px 0;
}
</style>
