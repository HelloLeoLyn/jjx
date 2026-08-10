<template>
  <el-dialog
    v-model="visible"
    width="1400px"
    append-to-body
    :fullscreen="isFullscreen"
    destroy-on-close
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">{{ title }}</span>
        <el-button text @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
          <span style="margin-left: 4px">{{ isFullscreen ? '退出全屏' : '全屏' }}</span>
        </el-button>
      </div>
    </template>
    <el-form ref="bomFormRef" :model="formData" :rules="rules" label-width="120px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="产品" prop="productId">
            <ProductSelector
              v-model="formData.productId"
              valueType="productId"
              :options="productOptions"
              @change="handleProductChange"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="BOM编码" prop="bomCode">
            <el-input v-model="formData.bomCode" placeholder="请输入BOM编码" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="BOM版本" prop="bomVersion">
            <el-input v-model="formData.bomVersion" placeholder="请输入BOM版本" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="BOM名称" prop="bomName">
            <el-input v-model="formData.bomName" placeholder="请输入BOM名称" maxlength="50" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="生效日期" prop="effectiveDate">
            <el-date-picker
              v-model="formData.effectiveDate"
              type="date"
              placeholder="请选择生效日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="失效日期" prop="expiryDate">
            <el-date-picker
              v-model="formData.expiryDate"
              type="date"
              placeholder="请选择失效日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input
              v-model="formData.remark"
              type="textarea"
              placeholder="请输入备注"
              :rows="3"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- Excel导入区域 -->
    <div class="excel-import-area">
      <el-upload
        ref="importUploadRef"
        drag
        accept=".xlsx,.xls"
        :auto-upload="false"
        :show-file-list="false"
        :limit="1"
        :on-change="handleImportFileChange"
        :on-exceed="handleImportExceed"
      >
        <el-icon class="upload-icon" :size="32"><UploadFilled /></el-icon>
        <div class="upload-text">
          <span>导入领料单 Excel 文件，</span>
          <em>点击选择文件</em>
        </div>
        <template #tip>
          <div class="upload-tip">仅支持 .xlsx / .xls 格式，解析后将自动填充到物料明细表格</div>
        </template>
      </el-upload>
    </div>

    <BomItemEditor
      v-model="formData.items"
      :bom-id="formData.bomId"
      @change="handleItemsChange"
      ref="bomItemEditorRef"
    />

    <template #footer>
      <div class="dialog-footer">
        <el-button v-if="formData.items?.length" icon="Printer" @click="printPreviewVisible = true">打印预览</el-button>
        <el-button type="primary" :loading="submitting" @click="submitForm">确 定</el-button>
        <el-button @click="handleCancel">取 消</el-button>
      </div>
    </template>

    <!-- BOM 作业指导书打印预览（57.webp 样式） -->
    <BomPrintPreview
      v-model="printPreviewVisible"
      :items="formData.items"
      :bom-code="formData.bomCode"
      :bom-name="formData.bomName"
      :bom-version="formData.bomVersion || formData.bomVersion"
      :product-id="formData.productId"
      :product-code="formData.productCode"
      :product-name="formData.productName"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules, UploadFile, UploadInstance } from 'element-plus'
import { FullScreen, UploadFilled } from '@element-plus/icons-vue'
import { productBomApi } from '@/api/product/bom'
import BomItemEditor from '@/components/BomItemEditor.vue'
import BomPrintPreview from './BomPrintPreview.vue'
import ProductSelector from '@/components/Selector/ProductSelector.vue'
import type { EngineeringBomFormData, EngineeringBomItem } from '@/types/product/bom'
import type { ProductItem } from '@/types/product'
import * as XLSX from 'xlsx'

interface Props {
  modelValue: boolean
  bomId?: number
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  bomId: undefined,
})

const emit = defineEmits<Emits>()

// 对话框可见性
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

// 响应式数据
const submitting = ref(false)
// BOM 打印预览弹窗
const printPreviewVisible = ref(false)
const activeTab = ref('basic')
const bomFormRef = ref<FormInstance>()
const bomItemEditorRef = ref()
const importUploadRef = ref<UploadInstance>()
const isFullscreen = ref(false)

// 切换全屏
const toggleFullscreen = () => {
  isFullscreen.value = !isFullscreen.value
}

// 标题
const title = computed(() => (props.bomId ? '修改BOM' : '新增BOM'))

// 编辑回填时，把当前产品对象传给 ProductSelector，让其能显示产品名称
const productOptions = computed<ProductItem[]>(() => {
  if (formData.productId && formData.productName) {
    return [{
      productId: formData.productId,
      productCode: formData.productCode,
      productName: formData.productName,
    } as ProductItem]
  }
  return []
})

// 表单数据
const formData = reactive<EngineeringBomFormData>({
  bomId: undefined,
  bomCode: '',
  bomName: '',
  bomVersion: '',
  productId: 0,
  productCode: '',
  productName: '',
  isCurrent: false,
  effectiveDate: '',
  expiryDate: '',
  remark: '',
  items: [],
})

// 表单验证规则
const rules = reactive<FormRules>({
  productId: [{ required: true, message: '请选择产品', trigger: 'change' }],
  bomVersion: [{ message: '请输入BOM版本', trigger: 'blur' }],
  bomName: [{ required: true, message: '请输入BOM名称', trigger: 'blur' }],
  bomCode: [{ required: true, message: '请输入BOM编码', trigger: 'blur' }],
  effectiveDate: [{ required: true, message: '请选择生效日期', trigger: 'change' }],
})

// 处理产品选择变化
const handleProductChange = (productId: number, product: any) => {
  if (product) {
    formData.productCode = product.productCode
    formData.productName = product.productName
    formData.productId = product.productId
    formData.bomName = `${product.productName}-BOM`
    formData.bomCode = `${product.productCode}-BOM`
  }
}

// 处理BOM明细变化
const handleItemsChange = (items: EngineeringBomItem[]) => {
  console.log('BOM明细已更新:', items)
}

// ==================== Excel 导入 ====================

/**
 * 文件选择变化 - 自动解析
 */
const handleImportFileChange = (uploadFile: UploadFile) => {
  if (!uploadFile.raw) return
  parseExcelFile(uploadFile.raw)
}

/**
 * 文件数量超出限制
 */
const handleImportExceed = () => {
  ElMessage.warning('每次只能上传一个文件')
}

/**
 * 解析 Excel 文件
 */
const parseExcelFile = (file: File) => {
  const reader = new FileReader()

  reader.onload = (e) => {
    try {
      const data = e.target?.result
      if (!data) {
        ElMessage.error('文件读取失败')
        return
      }

      const workbook = XLSX.read(data, { type: 'array' })
      const firstSheetName = workbook.SheetNames[0]
      if (!firstSheetName) {
        ElMessage.error('Excel 文件中没有工作表')
        return
      }

      const worksheet = workbook.Sheets[firstSheetName]
      const rows: any[][] = XLSX.utils.sheet_to_json(worksheet, { header: 1 })

      const items = parseRows(rows)
      if (items.length === 0) {
        ElMessage.warning('未解析到有效的物料数据，请检查文件格式')
        return
      }

      // 将解析后的数据填充到 formData.items
      formData.items = [...formData.items, ...items]
      ElMessage.success(`成功解析并添加 ${items.length} 项物料`)
    } catch (error) {
      console.error('解析 Excel 失败:', error)
      ElMessage.error('解析 Excel 文件失败，请检查文件格式')
    }
  }

  reader.onerror = () => {
    ElMessage.error('文件读取失败')
  }

  reader.readAsArrayBuffer(file)
}

/**
 * 解析行数据为 EngineeringBomItem 数组
 *
 * Excel 列结构（从第3行开始为数据行）：
 * 序号 | 项目名称 | 材料名称 | 单位 | 宽度 | 规格(乘/跳) | 长度 | 模数 | 单用量 | 基数 | 应用料 | 预计不良 | 最低投料 | 实际投料
 */
const parseRows = (rows: any[][]): EngineeringBomItem[] => {
  const items: EngineeringBomItem[] = []

  // 查找表头行（包含"序号"、"项目名称"等关键字的行）
  let headerRowIndex = -1
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i]
    if (!row || row.length === 0) continue

    const rowStr = row.map(String).join('')
    if (rowStr.includes('序号') && rowStr.includes('项目名称') && rowStr.includes('材料名称')) {
      headerRowIndex = i
      break
    }
  }

  if (headerRowIndex === -1) {
    ElMessage.warning('未找到表头行（需要包含"序号、项目名称、材料名称"等列）')
    return items
  }

  // 从表头下一行开始解析数据
  for (let i = headerRowIndex + 1; i < rows.length; i++) {
    const row = rows[i]
    if (!row || row.length < 3) continue

    // 检查是否为空行
    const materialName = String(row[2] || '').trim()
    if (!materialName) continue

    // 检查是否为汇总行（包含"合计"、"总计"等关键字）
    if (/合计|总计|小计/i.test(materialName)) continue

    // 解析各列
    // 列索引: 0=序号, 1=项目名称, 2=材料名称, 3=单位, 4=宽度, 5=规格(*/跳), 6=长度, 7=模数, 8=单用量, 9=基数, 10=应用料, 11=预计不良, 12=最低投料, 13=实际投料
    const seq = parseInt(String(row[0] || '0').trim()) || 0
    const itemName = String(row[1] || '').trim()
    const unit = String(row[3] || '').trim()
    // 第4/5/6列保留原始字符串，直接拼接为规格描述
    const col4 = String(row[4] || '').trim()
    const col5 = String(row[5] || '').trim()
    const col6 = String(row[6] || '').trim()
    const width = parseFloat(col4.replace(/[^\d.]/g, '')) || 0
    const length = parseFloat(col6.replace(/[^\d.]/g, '')) || 0
    const moduleQty = parseFloat(String(row[7] || '0').replace(/[^\d.]/g, '')) || 0
    const quantity = parseFloat(String(row[8] || '0').replace(/[^\d.]/g, '')) || 0
    const baseQty = parseFloat(String(row[9] || '1').replace(/[^\d.]/g, '')) || 1
    // 应用料（第10列）：有值直读，无值留空由后端计算
    const appliedRaw = String(row[10] ?? '').replace(/[^\d.]/g, '')
    const appliedQty = appliedRaw ? parseFloat(appliedRaw) : undefined
    // 预计不良：Excel中为小数（如0.1表示10%），转为百分制显示（如10）
    const lossRate = (parseFloat(String(row[11] || '0').replace(/[^\d.]/g, '')) || 0) * 100
    const minIssueQty = parseFloat(String(row[12] || '0').replace(/[^\d.]/g, '')) || 0
    // 实际投料（第13列）：有值直读，无值留空由后端计算
    const actualRaw = String(row[13] ?? '').replace(/[^\d.]/g, '')
    const actualIssueQty = actualRaw ? parseFloat(actualRaw) : undefined

    // 构建规格描述：第4/5/6列是什么就是什么，直接拼接
    const specification = col4 + col5 + col6

    const item: EngineeringBomItem = {
      itemId: undefined,
      materialId: 0,
      materialCode: '',
      materialName: materialName,
      specification: specification,
      unit: unit || 'PCS',
      quantity: quantity,
      appliedQty: appliedQty,
      actualIssueQty: actualIssueQty,
      lossRate: lossRate,
      baseQty: baseQty || 1,
      moduleQty: moduleQty || 1,
      minIssueQty: minIssueQty,
      widthMm: width,
      lengthMm: length,
      remark: itemName || '',
      sortOrder: items.length + 1,
    }

    items.push(item)
  }

  return items
}

// ==================== 表单操作 ====================

// 表单重置
const resetForm = () => {
  if (bomFormRef.value) {
    bomFormRef.value.resetFields()
  }
  Object.assign(formData, {
    bomId: undefined,
    bomCode: '',
    bomName: '',
    productId: 0,
    productCode: '',
    productName: '',
    bomVersion: '',
    approveStatus: 0,
    isCurrent: false,
    effectiveDate: '',
    expiryDate: '',
    remark: '',
    items: [],
  })
  activeTab.value = 'basic'
  // 清空上传文件
  if (importUploadRef.value) {
    importUploadRef.value.clearFiles()
  }
}

// 加载BOM数据
const loadBomData = async (bomId: number) => {
  try {
    const response = await productBomApi.getEngineeringBomInfo(bomId)
    Object.assign(formData, response.data)
    // 加载BOM明细
    const itemResponse = await productBomApi.listEngineeringBomItem(bomId)
    formData.items = itemResponse.data || []
  } catch (error) {
    console.error('加载BOM数据失败:', error)
    ElMessage.error('加载BOM数据失败')
  }
}

// 提交表单
const submitForm = () => {
  if (!bomFormRef.value) return

  bomFormRef.value.validate(async (valid) => {
    if (!valid) return

    submitting.value = true
    try {
      if (formData.bomId !== undefined) {
        await productBomApi.editEngineeringBom(formData as any)
        ElMessage.success('修改成功')
      } else {
        await productBomApi.addEngineeringBom(formData as any)
        ElMessage.success('新增成功')
      }
      visible.value = false
      emit('success')
    } catch (error) {
      console.error('保存BOM失败:', error)
      ElMessage.error('保存BOM失败')
    } finally {
      submitting.value = false
    }
  })
}

// 取消按钮
const handleCancel = () => {
  visible.value = false
  resetForm()
}

// 监听对话框打开
watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal) {
      if (props.bomId) {
        loadBomData(props.bomId)
      } else {
        resetForm()
      }
    }
  }
)
</script>

<style scoped>
.dialog-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dialog-title {
  font-size: 16px;
  font-weight: 600;
}

.dialog-footer {
  text-align: right;
}

.excel-import-area {
  margin-bottom: 16px;
  border: 1px dashed #dcdfe6;
  border-radius: 6px;
  padding: 8px;
  transition: border-color 0.3s;
}

.excel-import-area:hover {
  border-color: #409eff;
}

.excel-import-area :deep(.el-upload) {
  width: 100%;
}

.excel-import-area :deep(.el-upload-dragger) {
  width: 100%;
  height: 80px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 8px;
}

.upload-icon {
  margin-bottom: 4px;
}

.upload-text {
  color: #606266;
  font-size: 13px;

  em {
    color: #409eff;
    font-style: normal;
    text-decoration: underline;
    cursor: pointer;
  }
}

.upload-tip {
  color: #909399;
  font-size: 12px;
  margin-top: 4px;
}
</style>
