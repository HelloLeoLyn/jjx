<template>
  <el-dialog
    :title="title"
    :model-value="props.visible"
    width="1200px"
    append-to-body
    :close-on-click-modal="false"
    @close="handleClose"
    @update:model-value="(val: boolean) => emit('update:visible', val)"
  >
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="订单号">
        <el-input :model-value="orderNo" disabled />
      </el-form-item>
      <el-form-item label="订单金额">
        <el-input :model-value="orderTotalAmount.toFixed(2)" disabled>
          <template #append>{{ currency }}</template>
        </el-input>
      </el-form-item>
      <el-form-item label="已付金额">
        <el-input :model-value="paidAmount.toFixed(2)" disabled>
          <template #append>{{ currency }}</template>
        </el-input>
      </el-form-item>
      <el-alert
        title="提交后生成正式付款单，需在付款管理中审批并确认付款；订单已付金额将自动汇总。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form-item label="付款单号" prop="paymentNo">
        <el-input v-model="form.paymentNo" disabled />
      </el-form-item>
      <el-form-item label="计划付款日" prop="paymentDate">
        <el-date-picker
          v-model="form.paymentDate"
          type="date"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="申请金额" prop="paymentAmount">
        <el-input-number
          v-model="form.paymentAmount"
          :min="0"
          :max="orderTotalAmount - paidAmount"
          :precision="2"
          :step="100"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="付款方式" prop="paymentMethod">
        <el-select v-model="form.paymentMethod" placeholder="请选择" style="width: 100%">
          <el-option
            v-for="dict in PaymentMethodEnum.items"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="银行账户">
        <el-input v-model="form.bankAccount" maxlength="100" />
      </el-form-item>
      <el-form-item label="付款备注" prop="remark">
        <el-input
          v-model="form.remark"
          type="textarea"
          :rows="3"
          placeholder="请输入付款备注"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-divider content-position="left">付款凭证</el-divider>

      <el-form-item label="上传凭证">
        <el-upload
          ref="uploadRef"
          :auto-upload="true"
          list-type="picture-card"
          :file-list="imageList"
          :http-request="handleImageUpload"
          :on-preview="handleImagePreview"
          :on-remove="handleImageRemove"
          :before-upload="beforeImageUpload"
          accept="image/jpeg,image/png,image/gif,image/bmp,image/webp"
          multiple
        >
          <el-icon><Plus /></el-icon>
          <template #tip>
            <div class="el-upload__tip">支持 JPG/PNG/GIF/BMP/WebP 格式，单张不超过 10MB</div>
          </template>
        </el-upload>
      </el-form-item>
    </el-form>

    <!-- 图片预览对话框 -->
    <el-image-viewer
      v-if="previewVisible"
      :url-list="[previewUrl]"
      @close="previewVisible = false"
    />

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="handleClose">取 消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">确 定</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, UploadProps, UploadUserFile } from 'element-plus'
import { PaymentMethodEnum, PaymentStatusEnum } from '@/enums/purchase'
import { addPayment, generatePaymentNo } from '@/api/purchase/payment'
import type { PurchasePayment } from '@/types/purchase'
import {
  uploadTempReceiptFile,
  getDiskReceiptFiles,
  deleteTempReceiptFile,
  confirmReceiptDocuments,
} from '@/api/purchase/order'

const props = defineProps<{
  visible: boolean
  orderId?: number
  orderNo: string
  orderTotalAmount: number
  paidAmount: number
  currency: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success'): void
}>()

const formRef = ref<FormInstance>()
const uploadRef = ref()
const submitting = ref(false)

const form = reactive({
  paymentNo: '',
  paymentDate: '',
  paymentAmount: 0,
  paymentMethod: 'bank',
  bankAccount: '',
  remark: '',
})

// 图片相关
const imageList = ref<UploadUserFile[]>([])
const previewVisible = ref(false)
const previewUrl = ref('')

const title = computed(() => `付款 - ${props.orderNo}`)

const rules = reactive<FormRules>({
  paymentNo: [{ required: true, message: '付款单号生成失败，请关闭后重试', trigger: 'blur' }],
  paymentDate: [{ required: true, message: '请选择计划付款日期', trigger: 'change' }],
  paymentAmount: [{ required: true, message: '请输入付款金额', trigger: 'blur' }],
  paymentMethod: [{ required: true, message: '请选择付款方式', trigger: 'change' }],
})

// 监听 visible 变化，打开时加载磁盘上的票据文件
watch(
  () => props.visible,
  async (val) => {
    if (val) {
      form.paymentDate = today()
      form.paymentAmount = Math.max(0, props.orderTotalAmount - props.paidAmount)
      const [numberResult] = await Promise.all([generatePaymentNo(), loadImages()])
      form.paymentNo = numberResult.data || ''
    }
  }
)

// 加载磁盘上的票据文件列表（扫描订单号目录）
const loadImages = async () => {
  if (!props.orderId) return
  try {
    const response = await getDiskReceiptFiles(Number(props.orderId))
    const files: any[] = response.data || []
    imageList.value = files.map((file: any, index: number) => ({
      name: file.storageName || file.fileName,
      url: file.fileUrl,
      uid: index + 1,
    }))
  } catch (error) {
    console.error('加载付款凭证失败:', error)
  }
}

// 上传前校验
const beforeImageUpload: UploadProps['beforeUpload'] = (file) => {
  const isImage = ['image/jpeg', 'image/png', 'image/gif', 'image/bmp', 'image/webp'].includes(
    file.type
  )
  if (!isImage) {
    ElMessage.error('仅支持上传 JPG/PNG/GIF/BMP/WebP 格式的图片')
    return false
  }
  const isLt10M = file.size / 1024 / 1024 < 10
  if (!isLt10M) {
    ElMessage.error('图片大小不能超过 10MB')
    return false
  }
  return true
}

// 自定义上传（临时保存到磁盘，不插入数据库）
const handleImageUpload = async (options: any) => {
  const { file, onSuccess, onError } = options
  try {
    const response = await uploadTempReceiptFile(Number(props.orderId), file)
    onSuccess(response, file)
    ElMessage.success('凭证上传成功')
    // 重新加载票据列表
    await loadImages()
  } catch (error) {
    console.error('凭证上传失败:', error)
    onError(error)
    ElMessage.error('凭证上传失败')
  }
}

// 图片预览
const handleImagePreview: UploadProps['onPreview'] = (file) => {
  previewUrl.value = file.url || ''
  previewVisible.value = true
}

// 删除临时票据文件
const handleImageRemove: UploadProps['onRemove'] = async (file) => {
  try {
    // 从文件列表中获取 fileUrl
    const fileItem = imageList.value.find((item) => item.uid === file.uid)
    if (fileItem && fileItem.url) {
      await deleteTempReceiptFile(fileItem.url)
      ElMessage.success('凭证删除成功')
    }
  } catch (error) {
    console.error('凭证删除失败:', error)
    ElMessage.error('凭证删除失败')
  }
}

const handleSubmit = async () => {
  if (!formRef.value) return

  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (!props.orderId) return
    const payment: PurchasePayment = {
      paymentNo: form.paymentNo,
      orderId: Number(props.orderId),
      paymentDate: form.paymentDate,
      paymentAmount: form.paymentAmount,
      paymentMethod: form.paymentMethod,
      bankAccount: form.bankAccount || undefined,
      paymentStatus: PaymentStatusEnum.PENDING.value,
      remark: form.remark || undefined,
    }
    await addPayment(payment)
    ElMessage.success('付款单已创建，等待审批')

    // 2. 如果有上传的票据文件，确认插入数据库
    if (imageList.value.length > 0) {
      const files = imageList.value.map((item) => ({
        fileName: item.name,
        fileUrl: item.url,
        fileSize: 0,
      }))
      await confirmReceiptDocuments(Number(props.orderId), 0, files)
    }

    emit('success')
    handleClose()
  } catch (error) {
    console.error('创建付款单失败:', error)
    ElMessage.error(error instanceof Error ? error.message : '创建付款单失败')
  } finally {
    submitting.value = false
  }
}

const handleClose = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  form.paymentNo = ''
  form.paymentDate = ''
  form.paymentAmount = 0
  form.paymentMethod = PaymentMethodEnum.items[0]?.value || 'bank'
  form.bankAccount = ''
  form.remark = ''
  imageList.value = []
  emit('update:visible', false)
}

function today() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}
</script>
