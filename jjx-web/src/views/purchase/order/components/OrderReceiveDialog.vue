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
    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px" v-loading="loading">
      <el-form-item label="订单号">
        <el-input :model-value="orderNo" disabled />
      </el-form-item>

      <el-divider content-position="left">收货明细</el-divider>

      <el-table :data="form.items" border style="width: 100%; margin-bottom: 16px">
        <el-table-column label="物料编码" prop="materialCode" width="180" />
        <el-table-column label="物料名称" prop="materialName" />
        <el-table-column label="规格型号" prop="materialSpec" width="120" />
        <el-table-column label="单位" prop="unit" width="60" />
        <el-table-column label="订购数量" prop="quantity" width="90" />
        <el-table-column label="已收数量" prop="receivedQuantity" width="90" />
        <el-table-column label="本次收货" width="120">
          <template #default="scope">
            <el-input
              v-model="scope.row.receiveQuantity"
              :min="0"
              :max="(scope.row.quantity || 0) - (scope.row.receivedQuantity || 0)"
              :precision="2"
              :step="1"
              style="width: 100%"
              type="number"
            />
          </template>
        </el-table-column>
        <el-table-column label="检验结果" width="100">
          <template #default="scope">
            <el-select
              v-model="scope.row.inspectionResult"
              placeholder="请选择"
              style="width: 100%"
            >
              <el-option label="合格" value="合格" />
              <el-option label="不合格" value="不合格" />
              <el-option label="部分合格" value="部分合格" />
            </el-select>
          </template>
        </el-table-column>
      </el-table>

      <el-form-item label="检验备注" prop="inspectionRemark">
        <el-input
          v-model="form.inspectionRemark"
          type="textarea"
          :rows="3"
          placeholder="请输入检验备注"
          maxlength="500"
          show-word-limit
        />
      </el-form-item>

      <el-divider content-position="left">票据图片</el-divider>

      <el-form-item label="上传图片">
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
import {
  getOrderItems,
  batchReceiveOrderItems,
  uploadTempReceiptFile,
  getDiskReceiptFiles,
  deleteTempReceiptFile,
  confirmReceiptDocuments,
} from '@/api/purchase/order'

interface ReceiveItem {
  itemId: string
  materialCode: string
  materialName: string
  materialSpec: string
  unit: string
  quantity: number
  receivedQuantity: number
  receiveQuantity: number
  inspectionResult: string
}

interface DiskFileInfo {
  fileName: string
  storageName: string
  fileUrl: string
  fileSize: number
  orderNo: string
}

const props = defineProps<{
  visible: boolean
  orderId?: number
  orderNo: string
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success'): void
}>()

const formRef = ref<FormInstance>()
const uploadRef = ref()
const loading = ref(false)
const submitting = ref(false)

const form = reactive({
  items: [] as ReceiveItem[],
  inspectionRemark: '',
})

// 图片相关
const imageList = ref<UploadUserFile[]>([])
const previewVisible = ref(false)
const previewUrl = ref('')

const title = computed(() => `收货 - ${props.orderNo}`)

const rules = reactive<FormRules>({})

// 监听 visible 变化
watch(
  () => props.visible,
  async (val) => {
    if (val) {
      await loadItems()
      await loadImages()
    }
  }
)

// 加载订单明细
const loadItems = async () => {
  loading.value = true
  try {
    const response = await getOrderItems(Number(props.orderId))
    const items = response.data || []
    form.items = items.map((item: any) => ({
      itemId: item.itemId,
      materialCode: item.materialCode,
      materialName: item.materialName,
      materialSpec: item.materialSpec || '',
      unit: item.unit,
      quantity: item.quantity,
      receivedQuantity: item.receivedQuantity || 0,
      receiveQuantity: 0,
      inspectionResult: '合格',
    }))
  } catch (error) {
    console.error('加载订单明细失败:', error)
    ElMessage.error('加载订单明细失败')
  } finally {
    loading.value = false
  }
}

// 加载磁盘上的票据文件列表（扫描订单号目录）
const loadImages = async () => {
  if (!props.orderId) return
  try {
    const response = await getDiskReceiptFiles(Number(props.orderId))
    const files: DiskFileInfo[] = response.data || []
    imageList.value = files.map((file, index) => ({
      name: file.storageName,
      url: file.fileUrl,
      uid: index + 1,
    }))
  } catch (error) {
    console.error('加载票据失败:', error)
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
    ElMessage.success('票据上传成功')
    // 重新加载票据列表
    await loadImages()
  } catch (error) {
    console.error('票据上传失败:', error)
    onError(error)
    ElMessage.error('票据上传失败')
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
      ElMessage.success('票据删除成功')
    }
  } catch (error) {
    console.error('票据删除失败:', error)
    ElMessage.error('票据删除失败')
  }
}

// 提交
const handleSubmit = async () => {
  submitting.value = true
  try {
    const receiveItems = form.items
      .filter((item) => item.receiveQuantity > 0)
      .map((item) => ({
        itemId: Number(item.itemId),
        receivedQuantity: item.receiveQuantity,
        inspectionResult: item.inspectionResult,
        inspectionRemark: form.inspectionRemark || undefined,
      }))

    if (receiveItems.length === 0) {
      ElMessage.warning('请至少输入一个物料的收货数量')
      return
    }

    await batchReceiveOrderItems(Number(props.orderId), { items: receiveItems })
    ElMessage.success('收货成功')
    emit('success')
    handleClose()
  } catch (error) {
    console.error('收货失败:', error)
    ElMessage.error('收货失败')
  } finally {
    submitting.value = false
  }
}

// 关闭
const handleClose = () => {
  form.items = []
  form.inspectionRemark = ''
  imageList.value = []
  emit('update:visible', false)
}
</script>
