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
      <el-form-item label="付款金额" prop="paymentAmount">
        <el-input-number
          v-model="form.paymentAmount"
          :min="0"
          :max="orderTotalAmount - paidAmount"
          :precision="2"
          :step="100"
          style="width: 100%"
        />
      </el-form-item>
      <el-form-item label="付款状态" prop="paymentStatus">
        <el-select v-model="form.paymentStatus" placeholder="请选择" style="width: 100%">
          <el-option
            v-for="dict in PaymentStatusEnum.items"
            :key="dict.value"
            :label="dict.label"
            :value="dict.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="付款备注" prop="paymentComment">
        <el-input
          v-model="form.paymentComment"
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
import { PaymentStatusEnum } from '@/enums/purchase'
import {
  updatePaymentInfo,
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
  paymentAmount: 0,
  paymentStatus: 0,
  paymentComment: '',
})

// 图片相关
const imageList = ref<UploadUserFile[]>([])
const previewVisible = ref(false)
const previewUrl = ref('')

const title = computed(() => `付款 - ${props.orderNo}`)

const rules = reactive<FormRules>({
  paymentAmount: [{ required: true, message: '请输入付款金额', trigger: 'blur' }],
  paymentStatus: [{ required: true, message: '请选择付款状态', trigger: 'change' }],
})

// 监听 visible 变化，打开时加载磁盘上的票据文件
watch(
  () => props.visible,
  async (val) => {
    if (val) {
      await loadImages()
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
    // 1. 更新付款信息
    await updatePaymentInfo(Number(props.orderId), form.paymentAmount, form.paymentStatus)
    ElMessage.success('更新付款信息成功')

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
    console.error('更新付款信息失败:', error)
    ElMessage.error('更新付款信息失败')
  } finally {
    submitting.value = false
  }
}

const handleClose = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  form.paymentAmount = 0
  form.paymentStatus = 0
  form.paymentComment = ''
  imageList.value = []
  emit('update:visible', false)
}
</script>
