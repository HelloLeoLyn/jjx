<template>
  <el-dialog
    title="导入库存"
    v-model="dialogVisible"
    width="85%"
    append-to-body
    :close-on-click-modal="false"
    @close="handleCancel"
  >
    <!-- 上传区域 -->
    <div v-if="!parsed" class="import-upload-area">
      <el-upload
        ref="uploadRef"
        :auto-upload="false"
        :limit="1"
        :on-change="handleFileChange"
        :on-exceed="handleExceed"
        accept=".xlsx,.xls"
        drag
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">将文件拖到此处，或<em>点击上传</em></div>
        <template #tip>
          <div class="el-upload__tip">
            <p>仅支持 .xlsx / .xls 格式的Excel文件</p>
            <p>选择文件后将自动解析并预览数据</p>
            <el-button link type="primary" @click="handleDownloadTemplate">
              下载导入模板
            </el-button>
          </div>
        </template>
      </el-upload>
      <div v-if="parsingLoading" style="text-align: center; margin-top: 16px">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span style="margin-left: 8px">正在解析文件...</span>
      </div>
    </div>

    <!-- 预览表格 -->
    <div v-else>
      <div class="import-preview-header">
        <span class="import-preview-title">
          共解析到 <strong>{{ dataList.length }}</strong> 条数据
        </span>
        <div class="import-preview-actions">
          <span class="batch-warehouse-label">批量设置仓库：</span>
          <WarehouseSelector
            v-model="warehouseId"
            size="small"
            style="width: 200px"
            :active-only="true"
            @change="handleWarehouseChange"
          />
          <el-button :loading="checkAllLoading" @click="handleCheckAll">校验全部</el-button>
          <el-button :loading="createAllLoading" @click="handleCreateAll">建档全部</el-button>
          <el-button @click="handleBackToUpload">重新选择文件</el-button>
        </div>
      </div>
      <div class="import-preview-toolbar">
        <el-button link type="info" size="small" @click="showExtraFields = !showExtraFields">
          {{ showExtraFields ? '收起' : '展开' }}高级字段
          <el-icon><ArrowDown v-if="!showExtraFields" /><ArrowUp v-else /></el-icon>
        </el-button>
      </div>
      <el-table :data="dataList" border max-height="500" style="width: 100%; margin-top: 12px">
        <el-table-column type="index" label="序号" width="55" align="center" fixed />
        <el-table-column label="材料名称" prop="materialName" width="160">
          <template #default="{ row }">
            <el-input v-model="row.materialName" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="物料编码" prop="materialCode" width="130">
          <template #default="{ row }">
            <el-input v-model="row.materialCode" size="small" placeholder="校验后自动填充" />
          </template>
        </el-table-column>
        <el-table-column label="规格" prop="specification" width="140">
          <template #default="{ row }">
            <el-input v-model="row.specification" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="供应商" prop="supplierName" width="120">
          <template #default="{ row }">
            <el-input v-model="row.supplierName" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="库存数量" prop="quantity" width="100" align="right">
          <template #default="{ row }">
            <el-input-number v-model="row.quantity" :min="0" size="small" style="width: 90px" />
          </template>
        </el-table-column>
        <el-table-column label="备注/说明" prop="remark" width="160">
          <template #default="{ row }">
            <el-input v-model="row.remark" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="摆放/区域" prop="locationDesc" width="160">
          <template #default="{ row }">
            <el-input v-model="row.locationDesc" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="仓库" prop="warehouseName" width="160">
          <template #default="{ row }">
            <WarehouseSelector
              v-model="row.warehouseId"
              size="small"
              width="100%"
              :active-only="true"
              @change="
                (warehouse: any) => {
                  row.warehouseName = warehouse?.warehouseName || ''
                }
              "
            />
          </template>
        </el-table-column>
        <el-table-column label="库位" prop="locationCode" width="130">
          <template #default="{ row }">
            <el-input v-model="row.locationCode" size="small" />
          </template>
        </el-table-column>

        <el-table-column v-if="showExtraFields" label="批次号" prop="batchNo" width="120">
          <template #default="{ row }">
            <el-input v-model="row.batchNo" size="small" />
          </template>
        </el-table-column>
        <el-table-column
          v-if="showExtraFields"
          label="单位成本"
          prop="unitCost"
          width="100"
          align="right"
        >
          <template #default="{ row }">
            <el-input-number
              v-model="row.unitCost"
              :min="0"
              :precision="4"
              size="small"
              style="width: 90px"
            />
          </template>
        </el-table-column>
        <el-table-column
          v-if="showExtraFields"
          label="生产日期"
          prop="productionDate"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            <el-date-picker
              v-model="row.productionDate"
              type="date"
              value-format="YYYY-MM-DD"
              size="small"
              style="width: 110px"
            />
          </template>
        </el-table-column>
        <el-table-column
          v-if="showExtraFields"
          label="到期日期"
          prop="expiryDate"
          width="120"
          align="center"
        >
          <template #default="{ row }">
            <el-date-picker
              v-model="row.expiryDate"
              type="date"
              value-format="YYYY-MM-DD"
              size="small"
              style="width: 110px"
            />
          </template>
        </el-table-column>
        <el-table-column label="校验状态" width="100" align="center" fixed="right">
          <template #default="{ row }">
            <el-tag v-if="row.checked === true" type="success" size="small">已建档</el-tag>
            <el-tag v-else-if="row.checked === false" type="danger" size="small">未建档</el-tag>
            <el-tag v-else type="info" size="small">待校验</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row, $index }">
            <el-button
              link
              type="primary"
              size="small"
              :loading="row.checking"
              @click="handleCheckRow(row)"
              >校验</el-button
            >
            <el-button
              v-if="row.checked === false"
              link
              type="success"
              size="small"
              @click="handleCreateMaterial(row)"
              >建档</el-button
            >
            <el-button link type="danger" size="small" @click="handleDeleteRow($index)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <!-- 底部操作栏 -->
      <div class="import-footer">
        <div class="import-footer-left"></div>
        <div class="import-footer-right">
          <el-checkbox v-model="importDTO.createMaterial">建档勾选</el-checkbox>
          <el-button type="primary" :loading="importLoading" @click="handleImport">
            开始导入
          </el-button>
        </div>
      </div>
    </div>

    <!-- 物料建档对话框 -->
    <el-dialog
      title="新建物料"
      v-model="createDialogVisible"
      width="500px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
        <el-form-item label="物料编码" prop="materialCode">
          <el-input v-model="createForm.materialCode" placeholder="系统自动生成" readonly>
            <template #append>
              <el-button :icon="Search" @click="handleGenerateCode" />
            </template>
          </el-input>
        </el-form-item>
        <el-form-item label="物料名称" prop="materialName">
          <el-input v-model="createForm.materialName" placeholder="请输入物料名称" />
        </el-form-item>
        <el-form-item label="物料类型" prop="materialType">
          <EnumSelect
            v-model="createForm.materialType"
            :enum-obj="MaterialEnum.type"
            placeholder="请选择"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="规格型号" prop="specification">
          <el-input v-model="createForm.specification" placeholder="请输入规格型号" />
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="createForm.unit" placeholder="如：PCS、KG" />
        </el-form-item>
        <el-form-item label="供应商" prop="supplierName">
          <el-input v-model="createForm.supplierName" placeholder="请输入供应商名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creatingMaterial" @click="submitCreateMaterial">
          确定
        </el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, Search, ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import { stockApi } from '@/api/inventory/stock'
import { materialApi } from '@/api/inventory/material'
import { MaterialEnum } from '@/enums/inventory'
import * as XLSX from 'xlsx'
import type { UploadInstance } from 'element-plus'

// 导入行数据类型
export interface ImportRow {
  materialName: string
  specification: string
  quantity: number
  remark: string
  locationDesc: string
  materialCode: string
  warehouseId?: number | string
  warehouseName: string
  locationCode: string
  batchNo: string
  unitCost: number
  productionDate: string
  expiryDate: string
  supplierName: string
  checked: boolean | null
  checking: boolean
  materialId?: string
}
const warehouseId = ref() // 全局选择的仓库ID，默认为1，可以根据需要调整
const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success'): void
}>()

const dialogVisible = ref(props.visible)
watch(
  () => props.visible,
  (val) => {
    dialogVisible.value = val
    if (!val) {
      resetState()
    }
  }
)
watch(dialogVisible, (val) => {
  emit('update:visible', val)
})

// 状态
const parsed = ref(false)
const parsingLoading = ref(false)
const importLoading = ref(false)
const uploadRef = ref<UploadInstance>()
const importFile = ref<File | null>(null)
const dataList = ref<ImportRow[]>([])
const importDTO = reactive({
  dataList: [] as ImportRow[],
  createMaterial: false,
})
// 校验全部加载状态
const checkAllLoading = ref(false)
// 建档全部加载状态
const createAllLoading = ref(false)
// 高级字段展开/收起
const showExtraFields = ref(false)

// 建档相关
const createDialogVisible = ref(false)
const creatingMaterial = ref(false)
const createFormRef = ref()
const currentRow = ref<ImportRow | null>(null)
const createForm = reactive({
  materialCode: '',
  materialName: '',
  materialType: 'R',
  specification: '',
  unit: 'PCS',
  supplierName: '',
})
const createRules = {
  materialName: [{ required: true, message: '请输入物料名称', trigger: 'blur' }],
  materialType: [{ required: true, message: '请选择物料类型', trigger: 'change' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }],
}

// 重置状态
const resetState = () => {
  parsed.value = false
  parsingLoading.value = false
  importLoading.value = false
  importFile.value = null
  dataList.value = []
  uploadRef.value?.clearFiles()
}

// 文件选择变更 - 选择后自动解析
const handleFileChange = (uploadFile: any) => {
  importFile.value = uploadFile.raw
  // 自动触发解析
  if (importFile.value) {
    handleParseFile()
  }
}

// 文件数量超出限制
const handleExceed = () => {
  ElMessage.warning('每次只能上传一个文件')
}

// 下载导入模板（DEV-672：改调后端接口，模板统一后端生成，不再用静态文件）
const handleDownloadTemplate = async () => {
  try {
    const res = await stockApi.downloadImportTemplate()
    const blob = new Blob([res as any], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = '库存导入模板.xlsx'
    link.click()
    URL.revokeObjectURL(link.href)
  } catch (error) {
    ElMessage.error('下载模板失败')
  }
}

// 解析文件
const handleParseFile = async () => {
  if (!importFile.value) {
    ElMessage.warning('请先选择要导入的文件')
    return
  }

  parsingLoading.value = true
  try {
    const data = await readFileAsArrayBuffer(importFile.value)
    const workbook = XLSX.read(data, { type: 'array' })
    const sheetName = workbook.SheetNames[0]
    const sheet = workbook.Sheets[sheetName]
    const jsonData: any[] = XLSX.utils.sheet_to_json(sheet, { defval: '' })

    // 智能解析：尝试从多个可能的表头名称中匹配字段
    // 处理 Excel 可能有多行表头的情况（如第一行是日期，第二行才是字段名）
    dataList.value = jsonData
      .filter((item: any) => {
        // 过滤掉非数据行（如分类标题行 "下面是; xxx"、空行等）
        const keys = Object.keys(item)
        if (keys.length === 0) return false
        const firstVal = String(item[keys[0]] || '').trim()
        // 跳过分类标题行（以"下面是"开头）和空行
        if (firstVal.startsWith('下面是') || firstVal.startsWith('请示')) return false
        return true
      })
      .map((item: any) => {
        // 尝试从所有可能的 key 中匹配字段
        const keys = Object.keys(item)

        // 智能匹配材料名称：查找包含"材料名称"或"材料名"或"物料名称"的 key
        let nameKey = keys.find(
          (k) =>
            k.includes('材料名称') ||
            k.includes('材料名') ||
            k.includes('物料名称') ||
            k.includes('物料名') ||
            k === '名称'
        )
        // 如果找不到标准表头，尝试用第一个非空值作为名称（处理第一行是日期的情况）
        if (!nameKey) {
          // 检查第一个 key 的值是否像材料名称（不包含"规格"、"库存"等）
          const firstKey = keys[0]
          const firstVal = String(item[firstKey] || '').trim()
          // 如果第一个 key 本身不是标准字段名，尝试用它的值作为名称
          if (
            firstKey !== '规格' &&
            !firstKey.includes('规格') &&
            !firstKey.includes('库存') &&
            !firstKey.includes('备注') &&
            !firstKey.includes('摆放') &&
            !firstKey.includes('区域')
          ) {
            nameKey = firstKey
          }
        }

        // 智能匹配规格
        let specKey = keys.find((k) => k.includes('规格') || k === '规格型号' || k === '规格/型号')
        if (!specKey) {
          // 尝试找第二个字段作为规格
          const secondKey = keys[1]
          if (secondKey && secondKey !== nameKey) {
            specKey = secondKey
          }
        }

        // 智能匹配库存数量
        let qtyKey = keys.find(
          (k) => k.includes('库存数量') || k.includes('库存量') || k === '数量' || k === '上月结存'
        )
        if (!qtyKey) {
          const thirdKey = keys[2]
          if (thirdKey && thirdKey !== nameKey && thirdKey !== specKey) {
            qtyKey = thirdKey
          }
        }

        // 智能匹配备注
        let remarkKey = keys.find(
          (k) =>
            k.includes('备注') || k.includes('说明') || k === '备注 / 说明' || k === '备注/说明'
        )

        // 智能匹配摆放/区域
        let locationKey = keys.find(
          (k) =>
            k.includes('摆放') ||
            k.includes('区域') ||
            k === '摆放 / 区域' ||
            k === '摆放/区域' ||
            k === '摆放区域'
        )

        const name = nameKey ? String(item[nameKey] || '').trim() : ''
        const spec = specKey ? String(item[specKey] || '').trim() : ''
        const qty = parseFloat(item[qtyKey || ''] || 0)
        const remark = remarkKey ? String(item[remarkKey] || '').trim() : ''
        const location = locationKey ? String(item[locationKey] || '').trim() : ''

        // 从物料名称中提取供应商简写（括号内的内容）
        // 例如 "0.125中砂PC(尚昇)" → "尚昇"
        // 例如 "0.125中砂PC（地博）" → "地博"
        const supplierMatch = name.match(/[（(]([^）)]+)[）)]$/)
        const supplierName = supplierMatch ? supplierMatch[1] : ''

        return {
          materialName: name,
          specification: spec,
          quantity: isNaN(qty) ? 0 : qty,
          remark: remark,
          locationDesc: location,
          materialCode: '',
          warehouseId: '',
          warehouseName: '',
          locationCode: '',
          batchNo: '',
          unitCost: 0,
          productionDate: '',
          expiryDate: '',
          supplierName,
          checked: null,
          checking: false,
        }
      })
      .filter((row: any) => {
        // 过滤掉空行（材料名称为空且数量为0）
        return row.materialName !== '' || row.quantity > 0
      })

    parsed.value = true
    ElMessage.success(`成功解析 ${dataList.value.length} 条数据`)
  } catch (error) {
    console.error('解析文件失败:', error)
    ElMessage.error('解析文件失败，请检查文件格式')
  } finally {
    parsingLoading.value = false
  }
}

// 读取文件为 ArrayBuffer
const readFileAsArrayBuffer = (file: File): Promise<ArrayBuffer> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => resolve(e.target!.result as ArrayBuffer)
    reader.onerror = (e) => reject(e)
    reader.readAsArrayBuffer(file)
  })
}

// 返回上传页面
const handleBackToUpload = () => {
  parsed.value = false
  dataList.value = []
  uploadRef.value?.clearFiles()
}

// 取消
const handleCancel = () => {
  dialogVisible.value = false
}

// 删除行
const handleDeleteRow = (index: number) => {
  dataList.value.splice(index, 1)
  ElMessage.success('已删除该行')
}

// 批量设置仓库 - 将全局选择的仓库应用到所有行
const handleWarehouseChange = (warehouse: any) => {
  if (!warehouse) {
    // 清空时重置所有行的仓库
    dataList.value.forEach((row) => {
      row.warehouseId = 0
      row.warehouseName = ''
    })
    return
  }
  dataList.value.forEach((row) => {
    row.warehouseId = warehouse.warehouseId
    row.warehouseName = warehouse.warehouseName || ''
  })
}

// 校验单行物料，返回校验结果
const checkMaterial = async (row: ImportRow): Promise<{ success: boolean; created: boolean }> => {
  if (!row.materialName) {
    return { success: false, created: false }
  }

  row.checking = true
  try {
    const res = await stockApi.check({
      materialName: row.materialName,
      specification: row.specification,
      locationDesc: row.locationDesc,
      warehouseId: row.warehouseId ? Number(row.warehouseId) : undefined,
    })
    // 自动填充仓库和库位
    if (res.data && res.data.warehouseName) {
      row.warehouseName = res.data.warehouseName || ''
    }
    if (res.data && res.data.locationCode) {
      row.locationCode = res.data.locationCode
    }
    if (res.data && res.data.materialId) {
      row.checked = true
      row.materialCode = res.data.materialCode || ''
      row.materialId = String(res.data.materialId)
      return { success: true, created: true }
    } else {
      row.checked = false
      return { success: true, created: false }
    }
  } catch (error) {
    console.error(`校验 "${row.materialName}" 失败:`, error)
    row.checked = null
    return { success: false, created: false }
  } finally {
    row.checking = false
  }
}

// 校验单行物料
const handleCheckRow = async (row: ImportRow) => {
  const result = await checkMaterial(row)
  if (!result.success) {
    ElMessage.error(`校验 "${row.materialName}" 失败`)
  } else if (result.created) {
    ElMessage.success(`"${row.materialName}" 已建档`)
  } else {
    ElMessage.warning(`"${row.materialName}" 未建档，请先建档`)
  }
}

// 校验全部物料
const handleCheckAll = async () => {
  const uncheckedRows = dataList.value.filter((row) => row.checked === null)
  if (uncheckedRows.length === 0) {
    ElMessage.info('所有数据已校验')
    return
  }

  checkAllLoading.value = true
  let successCount = 0
  let failCount = 0
  let uncreatedCount = 0

  for (const row of uncheckedRows) {
    const result = await checkMaterial(row)
    if (!result.success) {
      failCount++
    } else if (result.created) {
      successCount++
    } else {
      uncreatedCount++
    }
  }

  checkAllLoading.value = false

  const msgParts: string[] = []
  if (successCount > 0) msgParts.push(`已建档 ${successCount} 条`)
  if (uncreatedCount > 0) msgParts.push(`未建档 ${uncreatedCount} 条`)
  if (failCount > 0) msgParts.push(`校验失败 ${failCount} 条`)
  ElMessage.success(`校验完成：${msgParts.join('，')}`)
}

// 建档全部物料
const handleCreateAll = async () => {
  const uncreatedRows = dataList.value.filter((row) => row.checked === false)
  if (uncreatedRows.length === 0) {
    ElMessage.info('没有需要建档的数据')
    return
  }

  createAllLoading.value = true
  let successCount = 0
  let failCount = 0

  for (const row of uncreatedRows) {
    try {
      // 生成物料编码
      const codeRes = await materialApi.generateCode()
      const saveData = {
        materialCode: codeRes.data || '',
        materialName: row.materialName,
        materialType: 'R',
        specification: row.specification,
        unit: 'PCS',
        supplierName: row.supplierName || '',
      }
      await materialApi.add(saveData as any)
      row.checked = true
      row.materialCode = saveData.materialCode
      successCount++
    } catch (error) {
      console.error(`建档 "${row.materialName}" 失败:`, error)
      failCount++
    }
  }

  createAllLoading.value = false
  ElMessage.success(`建档完成：成功 ${successCount} 条，失败 ${failCount} 条`)
}

// 打开建档对话框
const handleCreateMaterial = (row: ImportRow) => {
  currentRow.value = row
  createForm.materialCode = ''
  createForm.materialName = row.materialName
  createForm.materialType = 'R'
  createForm.specification = row.specification
  createForm.unit = 'PCS'
  createForm.supplierName = row.supplierName || ''
  createDialogVisible.value = true
}

// 生成物料编码
const handleGenerateCode = async () => {
  try {
    const res = await materialApi.generateCode()
    if (res.data) {
      createForm.materialCode = res.data
    }
  } catch (error) {
    ElMessage.error('生成物料编码失败')
  }
}

// 提交建档
const submitCreateMaterial = async () => {
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return

  creatingMaterial.value = true
  try {
    const saveData = {
      materialCode: createForm.materialCode,
      materialName: createForm.materialName,
      materialType: createForm.materialType,
      specification: createForm.specification,
      unit: createForm.unit,
      supplierName: createForm.supplierName,
    }
    await materialApi.add(saveData as any)
    ElMessage.success('物料建档成功')

    if (currentRow.value) {
      currentRow.value.checked = true
      currentRow.value.materialCode = createForm.materialCode
    }

    createDialogVisible.value = false
  } catch (error) {
    console.error('建档失败:', error)
    ElMessage.error('建档失败')
  } finally {
    creatingMaterial.value = false
  }
}

// 执行导入
const handleImport = async () => {
  const uncheckedRows = dataList.value.filter((row) => row.checked === null)
  if (uncheckedRows.length > 0) {
    await ElMessageBox.confirm(
      `还有 ${uncheckedRows.length} 条数据未校验，是否继续导入？`,
      '提示',
      { type: 'warning', confirmButtonText: '继续导入', cancelButtonText: '取消' }
    )
  }

  const uncreatedRows = dataList.value.filter((row) => row.checked === false)
  if (uncreatedRows.length > 0) {
    await ElMessageBox.confirm(
      `有 ${uncreatedRows.length} 条数据的物料未建档，是否跳过这些数据继续导入？`,
      '提示',
      { type: 'warning', confirmButtonText: '跳过并继续', cancelButtonText: '取消' }
    )
  }

  // 检测未匹配到库位的摆放区域，询问是否自动创建
  let autoCreateLocation = false
  const noLocationRows = dataList.value.filter(
    (row) => row.checked !== false && row.locationDesc && !row.locationCode
  )
  if (noLocationRows.length > 0) {
    const locationDescs = [...new Set(noLocationRows.map((row) => row.locationDesc))]
    try {
      await ElMessageBox.confirm(
        `检测到 ${noLocationRows.length} 条数据的摆放区域（${locationDescs.slice(0, 5).join('、')}${locationDescs.length > 5 ? ' 等' : ''}）没有对应库位，是否自动创建库位？`,
        '提示',
        {
          type: 'info',
          confirmButtonText: '自动创建',
          cancelButtonText: '不创建',
          distinguishCancelAndClose: true,
        }
      )
      autoCreateLocation = true
    } catch {
      // 用户选择不创建：正常导入，库位留空
      autoCreateLocation = false
    }
  }

  importLoading.value = true
  try {
    const validData = dataList.value.filter((row) => row.checked !== false)
    if (validData.length === 0) {
      ElMessage.warning('没有可导入的数据')
      return
    }

    await stockApi.batchImport(validData, autoCreateLocation)
    ElMessage.success(`成功导入 ${validData.length} 条数据`)
    dialogVisible.value = false
    emit('success')
  } catch (error) {
    console.error('导入失败:', error)
    ElMessage.error('导入失败')
  } finally {
    importLoading.value = false
  }
}
</script>

<style scoped>
.import-preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.import-preview-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.import-preview-title {
  font-size: 14px;
  color: #666;
}

.import-upload-area {
  padding: 20px 0;
}

.import-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid #ebeef5;
}

.import-footer-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.import-footer-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
