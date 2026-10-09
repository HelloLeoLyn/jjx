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
            <!-- 2026-09-15：BOM 建档放开产品状态（除 停产/取消 外均可选），破除「发布需BOM、建BOM需已发布」死锁 -->
            <!-- 2026-09-15：allow-remote-with-options —— productOptions 只是回填当前产品用于显示，
                 不能因此禁用远程搜索（否则修改BOM改选不了产品、新增时选错后搜不出别的产品） -->
            <ProductSelector
              v-model="formData.productId"
              valueType="productId"
              status-scope="active"
              allow-remote-with-options
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
          <div class="upload-tip">仅支持 .xlsx / .xls 格式，解析后自动填充到物料明细表格。<b>导入的物料全部为根节点</b>，如需层级结构请在页面上用「子物料」按钮手动调整</div>
        </template>
      </el-upload>
      <div class="template-row">
        <el-button link type="primary" @click="downloadImportTemplate">
          <el-icon><Download /></el-icon>下载导入模板（.xlsx）
        </el-button>
        <span class="template-hint">必填：材料名称、规格（乘/跳）、模数；其余列可空。列顺序：序号｜项目名称｜材料名称｜单位｜宽度｜规格（乘/跳）｜长度｜模数｜单用量｜基数｜应用料｜预计不良｜最低投料｜实际投料（首行表头，数据从第 2 行起；预计不良填小数，如 0.05＝5%）</span>
      </div>
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
import { FullScreen, UploadFilled, Download } from '@element-plus/icons-vue'
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
const handleProductChange = (productId: number | null, product: any) => {
  if (!product) {
    // 2026-09-15：清空产品时同步清掉回填字段——残留会让 productOptions 非空，
    // 且提交时 productCode/productName 与 productId 不一致
    const oldCode = formData.productCode
    const oldName = formData.productName
    if (oldCode && formData.bomCode === `${oldCode}-BOM`) {
      formData.bomCode = ''
    }
    if (oldName && formData.bomName === `${oldName}-BOM`) {
      formData.bomName = ''
    }
    formData.productCode = ''
    formData.productName = ''
    return
  }
  formData.productCode = product.productCode
  formData.productName = product.productName
  formData.productId = product.productId
  formData.bomName = `${product.productName}-BOM`
  formData.bomCode = `${product.productCode}-BOM`
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
  // 解析后清空上传列表，避免 :limit=1 槽位被占用、再次上传触发 on-exceed（dev-20261009-034）
  importUploadRef.value?.clearFiles()
}

/**
 * 文件数量超出限制
 */
const handleImportExceed = () => {
  ElMessage.warning('每次只能上传一个文件')
}

/**
 * 下载导入模板（前端直接生成，列顺序与 parseRows 解析器对齐）
 */
const downloadImportTemplate = () => {
  const header = [
    '序号', '项目名称', '材料名称（必填）', '单位', '宽度', '规格（乘/跳）（必填）', '长度',
    '模数（必填）', '单用量', '基数', '应用料', '预计不良', '最低投料', '实际投料',
  ]
  const sample = [
    [1, '示例-主体', '白卡纸 300g', '张', 787, '*', 1092, 1, 1, 1000, '', 0.05, '', ''],
    [2, '示例-内衬', '瓦楞纸板', '张', 500, '/', 700, 1, 2, 1000, '', 0.03, '', ''],
  ]
  const sheet = XLSX.utils.aoa_to_sheet([header, ...sample])
  sheet['!cols'] = header.map(() => ({ wch: 12 }))
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, sheet, 'BOM明细')
  XLSX.writeFile(wb, 'BOM领料单导入模板.xlsx')
  ElMessage.success('模板已下载，按表头填写后拖回此处导入')
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
      ElMessage.success(`成功解析并添加 ${items.length} 项物料（全部为根节点，如需层级请用「子物料」按钮手动调整）`)
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
 * 按【表头名】映射列（不再按固定列号）：文件里只要有「品名 / 规格 / 模数」三列即可导入，
 * 其余列有就取、没有就留空（用户口径：必要的那几项有就行，其他按实际情况来）。
 * 表头别名：品名=原料品名/材料名称/品名；品类=项目/项目名称；数量=单用量/实发数量/数量。
 */
const parseRows = (rows: any[][]): EngineeringBomItem[] => {
  const items: EngineeringBomItem[] = []
  const missingRows: number[] = []

  const ALIAS: Record<string, string[]> = {
    seq: ['项次', '序号'],
    name: ['原料品名', '材料名称', '品名'],
    item: ['项目', '项目名称'],
    spec: ['规格'],
    unit: ['单位'],
    module: ['模数'],
    qty: ['单用量', '实发数量', '数量'],
    base: ['基数'],
    applied: ['应用料'],
    loss: ['预计不良'],
    minIssue: ['最低投料'],
    actualIssue: ['实际投料'],
    width: ['宽度'],
    length: ['长度'],
    remark: ['备注'],
  }
  const matchKey = (cell: any): string | null => {
    const t = String(cell ?? '').trim()
    if (!t) return null
    for (const key of Object.keys(ALIAS)) {
      if (ALIAS[key].some((n) => t === n || t.includes(n))) return key
    }
    return null
  }

  // 找表头行：能同时匹配到 品名 + 规格 + 模数 的那一行
  let headerRowIndex = -1
  let col: Record<string, number> = {}
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i]
    if (!row || row.length === 0) continue
    const m: Record<string, number> = {}
    row.forEach((cell, idx) => {
      const k = matchKey(cell)
      if (k && m[k] === undefined) m[k] = idx
    })
    if (m.name !== undefined && m.spec !== undefined && m.module !== undefined) {
      headerRowIndex = i
      col = m
      break
    }
  }
  if (headerRowIndex === -1) {
    ElMessage.warning('未找到表头行（至少需要「品名」「规格」「模数」三列）')
    return items
  }

  const cellOf = (row: any[], key: string): string => {
    const idx = col[key]
    return idx === undefined ? '' : String(row[idx] ?? '').trim()
  }
  const num = (s: string, dflt = 0): number => {
    const v = parseFloat(String(s).replace(/[^\d.]/g, ''))
    return Number.isNaN(v) ? dflt : v
  }

  for (let i = headerRowIndex + 1; i < rows.length; i++) {
    const row = rows[i]
    if (!row || row.length === 0) continue

    const materialName = cellOf(row, 'name')
    if (!materialName) continue
    if (/合计|总计|小计/i.test(materialName)) continue

    // 必填：品名 / 规格 / 模数；缺任一则该行不导入
    const spec = cellOf(row, 'spec')
    const moduleRaw = cellOf(row, 'module')
    if (!spec || !moduleRaw) {
      missingRows.push(i + 1)
      continue
    }

    const itemName = cellOf(row, 'item')
    const appliedRaw = cellOf(row, 'applied')
    const actualRaw = cellOf(row, 'actualIssue')

    const item: EngineeringBomItem = {
      itemId: undefined,
      bomId: undefined,
      parentMaterialId: null, // 导入全部为根节点，层级由页面「子物料」手动调整
      materialId: 0,
      materialCode: '',
      materialName,
      specification: spec,
      unit: cellOf(row, 'unit') || 'PCS',
      quantity: num(cellOf(row, 'qty')),
      appliedQty: appliedRaw ? num(appliedRaw) : undefined,
      actualIssueQty: actualRaw ? num(actualRaw) : undefined,
      lossRate: num(cellOf(row, 'loss')) * 100,
      baseQty: num(cellOf(row, 'base'), 1) || 1,
      moduleQty: num(moduleRaw, 1) || 1,
      minIssueQty: num(cellOf(row, 'minIssue')),
      widthMm: num(cellOf(row, 'width')),
      lengthMm: num(cellOf(row, 'length')),
      remark: cellOf(row, 'remark') || itemName || '',
      sortOrder: items.length + 1,
    }

    items.push(item)
  }

  if (missingRows.length) {
    ElMessage.warning(`有 ${missingRows.length} 行缺少必填项（品名/规格/模数），已跳过：第 ${missingRows.join('、')} 行`)
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

    // 直接读取编辑器，避免选料后立即保存遗漏防抖中的回填。
    formData.items = bomItemEditorRef.value.getItems()
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
