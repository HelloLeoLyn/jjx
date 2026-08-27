<template>
  <div class="inbound-create">
    <!-- 基本信息 -->
    <el-form
      ref="formRef"
      :model="formData"
      :rules="formRules"
      label-width="120px"
      class="base-form"
    >
      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="入库类型" prop="inboundType">
            <el-select
              v-model="formData.inboundType"
              placeholder="请选择入库类型"
              style="width: 100%"
              @change="handleInboundTypeChange"
            >
              <el-option label="采购入库" value="purchase" />
              <el-option label="生产入库" value="production" />
              <el-option label="退货入库" value="return" />
              <el-option label="调拨入库" value="transfer" />
              <el-option label="其他入库" value="other" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="仓库" prop="warehouseId">
            <el-select
              v-model="formData.warehouseId"
              placeholder="请选择仓库"
              style="width: 100%"
              @change="handleWarehouseChange"
            >
              <el-option
                v-for="warehouse in warehouseList"
                :key="warehouse.warehouseId"
                :label="warehouse.warehouseName"
                :value="warehouse.warehouseId"
              />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="供应商" prop="supplierId" v-if="showSupplier">
            <SupplierSelector
              v-model="formData.supplierId"
              placeholder="请选择供应商"
              :active-only="true"
              :show-code="true"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="8">
          <el-form-item label="来源类型" prop="sourceType">
            <el-select
              v-model="formData.sourceType"
              placeholder="请选择来源类型"
              style="width: 100%"
              @change="handleSourceTypeChange"
            >
              <el-option label="采购订单" value="purchase_order" />
              <el-option label="生产工单" value="work_order" />
              <el-option label="销售退货" value="sales_return" />
              <el-option label="调拨单" value="transfer_order" />
              <el-option label="其他" value="other" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="来源单号" prop="sourceId">
            <el-input
              v-model="formData.sourceId"
              placeholder="请输入来源单号"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="备注">
            <el-input v-model="formData.remark" placeholder="请输入备注" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <!-- 物料明细 -->
    <div class="item-section">
      <div class="section-header">
        <h3>入库明细</h3>
        <div class="section-actions">
          <input
            ref="fileInputRef"
            type="file"
            accept=".xlsx,.xls"
            style="display: none"
            @change="handleFileChange"
          />
          <el-button type="success" size="small" @click="triggerFileInput">
            <el-icon><Upload /></el-icon>Excel导入
          </el-button>
          <el-button type="primary" @click="handleAddItem" size="small">
            <el-icon><Plus /></el-icon>添加物料
          </el-button>
        </div>
      </div>

      <el-table :data="formData.items" border style="width: 100%">
        <el-table-column label="序号" width="60" align="center">
          <template #default="{ $index }">
            {{ $index + 1 }}
          </template>
        </el-table-column>
        <el-table-column label="物料编码" width="180">
          <template #default="{ row, $index }">
            <MaterialSelector
              v-model="row.materialCode"
              value-type="materialCode"
              placeholder="搜索并选择材料"
              @change="handleChange"
            />
          </template>
        </el-table-column>

        <el-table-column label="物料名称" width="180">
          <template #default="{ row }">
            <el-input v-model="row.materialName" placeholder="请输入物料名称" />
          </template>
        </el-table-column>
        <el-table-column label="规格型号" width="120">
          <template #default="{ row }">
            <el-input v-model="row.specification" placeholder="规格型号" />
          </template>
        </el-table-column>
        <el-table-column label="单位" width="80" align="center">
          <template #default="{ row }">
            <el-input v-model="row.unit" placeholder="单位" />
          </template>
        </el-table-column>
        <el-table-column label="批次号" width="120">
          <template #default="{ row }">
            <el-input v-model="row.batchNo" placeholder="批次号" />
          </template>
        </el-table-column>
        <el-table-column label="数量" width="120" align="right">
          <template #default="{ row, $index }">
            <el-input-number
              v-model="row.quantity"
              :min="0.001"
              :precision="3"
              :step="1"
              controls-position="right"
              style="width: 100%"
              @change="handleQuantityChange(row, $index)"
            />
          </template>
        </el-table-column>
        <el-table-column label="单价" width="120" align="right">
          <template #default="{ row, $index }">
            <el-input-number
              v-model="row.unitPrice"
              :min="0"
              :precision="2"
              :step="0.01"
              controls-position="right"
              style="width: 100%"
              @change="handlePriceChange(row, $index)"
            />
          </template>
        </el-table-column>
        <el-table-column label="金额" width="120" align="right">
          <template #default="{ row }"> ¥ {{ formatCurrency(row.amount) }} </template>
        </el-table-column>
        <el-table-column label="库位" width="120">
          <template #default="{ row }">
            <el-select v-model="row.locationId" placeholder="请选择库位" style="width: 100%">
              <el-option
                v-for="location in locationList"
                :key="location.locationId"
                :label="location.locationCode"
                :value="location.locationId"
              />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="生产日期" width="120" align="center">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.productionDate"
              type="date"
              placeholder="选择日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="到期日期" width="120" align="center">
          <template #default="{ row }">
            <el-date-picker
              v-model="row.expiryDate"
              type="date"
              placeholder="选择日期"
              value-format="YYYY-MM-DD"
              style="width: 100%"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right" align="center">
          <template #default="{ $index }">
            <el-button
              link
              type="danger"
              @click="handleRemoveItem($index)"
              :disabled="formData.items.length <= 1"
            >
              删除
            </el-button>
            <el-button
              link
              type="primary"
              @click="handleValidateAndFill($index)"
              :loading="validating"
            >
              校验补全
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 汇总信息 -->
      <div class="summary-section">
        <el-row :gutter="20">
          <el-col :span="6">
            <div class="summary-item">
              <span class="label">物料种类：</span>
              <span class="value">{{ itemCount }}</span>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="summary-item">
              <span class="label">总数量：</span>
              <span class="value">{{ formatNumber(totalQuantity) }}</span>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="summary-item">
              <span class="label">总金额：</span>
              <span class="value">¥ {{ formatCurrency(totalAmount) }}</span>
            </div>
          </el-col>
          <el-col :span="6">
            <div class="summary-item">
              <span class="label">平均单价：</span>
              <span class="value">¥ {{ formatCurrency(averagePrice) }}</span>
            </div>
          </el-col>
        </el-row>
      </div>
      <div class="card-header">
        <span>新建入库单</span>
        <div class="header-actions">
          <el-button @click="handleCancel">取消</el-button>
          <el-button type="primary" @click="handleSaveDraft" :loading="loading">
            保存草稿
          </el-button>
          <el-button type="success" @click="handleSubmit" :loading="loading"> 提交审批 </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'InboundCreate',
})

import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Plus, Upload } from '@element-plus/icons-vue'
import * as XLSX from 'xlsx'
import { inboundApi } from '@/api/inventory/inbound'
import { warehouseApi } from '@/api/inventory/warehouse'
import { locationApi } from '@/api/inventory/location'
import { materialApi } from '@/api/inventory/material'
import { formatCurrency, formatNumber } from '@/utils/format'
import SupplierSelector from '@/components/Selector/SupplierSelector.vue'
import type { InboundCreateParams, InboundItemCreateParams } from '@/types/inventory/inbound'
import type { InventoryWarehouse } from '@/types/inventory/warehouse'
import MaterialSelector from '@/components/Selector/MaterialSelector.vue'
const router = useRouter()
const formRef = ref<FormInstance>()
const fileInputRef = ref<HTMLInputElement>()
const loading = ref(false)

// 表单数据
const formData = reactive<InboundCreateParams>({
  inboundType: 'purchase',
  warehouseId: '',
  supplierId: '',
  sourceType: '',
  sourceId: '',
  remark: '',
  items: [
    {
      materialId: '',
      materialCode: '',
      materialName: '',
      specification: '',
      unit: '',
      batchNo: '',
      quantity: 1,
      unitPrice: 0,
      amount: 0,
      locationId: '',
      productionDate: '',
      expiryDate: '',
      remark: '',
    },
  ],
})

// 表单验证规则
const formRules = {
  inboundType: [{ required: true, message: '请选择入库类型', trigger: 'change' }],
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
}

// 数据列表
const warehouseList = ref<InventoryWarehouse[]>([])
const locationList = ref<any[]>([])

// 计算属性
const showSupplier = computed(() => formData.inboundType === 'purchase')
const itemCount = computed(() => formData.items.length)
const totalQuantity = computed(() => formData.items.reduce((sum, item) => sum + item.quantity, 0))
const totalAmount = computed(() =>
  formData.items.reduce((sum, item) => sum + item.quantity * item.unitPrice, 0)
)
const averagePrice = computed(() =>
  totalQuantity.value > 0 ? totalAmount.value / totalQuantity.value : 0
)

// 初始化数据
onMounted(async () => {
  await loadWarehouses()
})

// 加载仓库列表
const loadWarehouses = async () => {
  try {
    const res = await warehouseApi.list({ current: 1, pageSize: 100 })
    warehouseList.value = res.data?.records || []
  } catch (error) {
    console.error('加载仓库列表失败:', error)
    ElMessage.error('加载仓库列表失败')
  }
}

// 入库类型变化
const handleInboundTypeChange = (value: string) => {
  if (value !== 'purchase') {
    formData.supplierId = ''
  }
}

// 来源类型变化
const handleSourceTypeChange = (value: string) => {
  formData.sourceId = ''
}

// 仓库变化：根据仓库ID加载库位列表
const handleWarehouseChange = async (warehouseId: string) => {
  // 清空已有库位选择
  formData.items.forEach((item) => {
    item.locationId = ''
  })
  locationList.value = []

  if (!warehouseId) return

  try {
    const res = await locationApi.getByWarehouse(Number(warehouseId))
    locationList.value = res.data || []
  } catch (error) {
    console.error('加载库位列表失败:', error)
    ElMessage.error('加载库位列表失败')
  }
}

// 数量变化
const handleQuantityChange = (row: any, index: number) => {
  calculateAmount(row)
}

// 单价变化
const handlePriceChange = (row: any, index: number) => {
  calculateAmount(row)
}

// 计算金额
const calculateAmount = (row: any) => {
  row.amount = row.quantity * row.unitPrice
}

// 添加物料行
const handleAddItem = () => {
  formData.items.push({
    materialId: '',
    materialCode: '',
    materialName: '',
    specification: '',
    unit: '',
    batchNo: '',
    quantity: 1,
    unitPrice: 0,
    amount: 0,
    locationId: '',
    productionDate: '',
    expiryDate: '',
    remark: '',
  })
}

// 删除物料行
const handleRemoveItem = (index: number) => {
  if (formData.items.length > 1) {
    formData.items.splice(index, 1)
  }
}

// ==================== 校验补全 ====================

const validating = ref(false)

/**
 * 校验补全：根据当前行的物料名称、规格、供应商查询后端 API，自动补全物料编码等数据
 * @param index 当前操作的行索引
 */
const handleValidateAndFill = async (index: number) => {
  const item = formData.items[index]
  if (!item) {
    ElMessage.warning('未找到当前物料行')
    return
  }

  // 如果已经有物料编码且已补全过，跳过
  if (item.materialId && item.materialCode) {
    ElMessage.info('当前行已补全，无需重复操作')
    return
  }

  // 构建查询参数：物料名称、规格、供应商
  const keyword = [item.materialName, item.specification, formData.supplierId]
    .filter(Boolean)
    .join(' ')

  if (!keyword.trim()) {
    ElMessage.warning('物料名称、规格、供应商均为空，无法查询')
    return
  }

  validating.value = true
  const params = {
    pageNum: 1,
    pageSize: 10,
    materialName: item.materialName,
    specification: item.specification,
    supplierId: formData.supplierId ? Number(formData.supplierId) : undefined,
  }
  try {
    const res = await materialApi.list(params)
    const materials = res.data
    if (materials && materials.length > 0) {
      // 取第一个匹配的物料
      const material = materials[0]
      item.materialId = String(material.materialId ?? '')
      item.materialCode = material.materialCode
      calculateAmount(item)
      ElMessage.success('校验补全成功')
    } else {
      ElMessage.warning('未找到匹配的物料')
    }
  } catch (error) {
    console.error('查询失败:', error)
    ElMessage.error('查询失败')
  } finally {
    validating.value = false
  }
}

// ==================== Excel 导入 ====================

/** 触发文件选择 */
const triggerFileInput = () => {
  fileInputRef.value?.click()
}

/** 处理文件选择变化 */
const handleFileChange = (event: Event) => {
  const input = event.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    handleExcelUpload(file)
  }
  // 重置 input 以便再次选择同一文件
  input.value = ''
}

/**
 * Excel 表头列名映射（支持中英文）
 * key: Excel 中的列名（支持多种写法）
 * value: 对应的字段名
 */
const EXCEL_COLUMN_MAPPING: Record<string, string> = {
  // 物料编码
  物料编码: 'materialCode',
  物料编号: 'materialCode',
  编码: 'materialCode',
  materialCode: 'materialCode',
  'material code': 'materialCode',
  // 物料名称
  物料名称: 'materialName',
  物料名: 'materialName',
  名称: 'materialName',
  材料: 'materialName',
  materialName: 'materialName',
  'material name': 'materialName',
  // 规格型号
  规格型号: 'specification',
  规格: 'specification',
  型号: 'specification',
  specification: 'specification',
  // 单位
  单位: 'unit',
  计量单位: 'unit',
  unit: 'unit',
  // 批次号
  批次号: 'batchNo',
  批号: 'batchNo',
  批次: 'batchNo',
  batchNo: 'batchNo',
  'batch no': 'batchNo',
  // 数量
  数量: 'quantity',
  入库数量: 'quantity',
  quantity: 'quantity',
  // 单价
  单价: 'unitPrice',
  入库单价: 'unitPrice',
  unitPrice: 'unitPrice',
  'unit price': 'unitPrice',
  // 金额
  金额: 'amount',
  总金额: 'amount',
  小计: 'amount',
  amount: 'amount',
  // 库位
  库位: 'locationId',
  库位编码: 'locationId',
  库位编号: 'locationId',
  locationId: 'locationId',
  'location code': 'locationId',
  // 生产日期
  生产日期: 'productionDate',
  生产日: 'productionDate',
  productionDate: 'productionDate',
  'production date': 'productionDate',
  // 到期日期
  到期日期: 'expiryDate',
  有效期: 'expiryDate',
  过期日期: 'expiryDate',
  expiryDate: 'expiryDate',
  'expiry date': 'expiryDate',
  // 备注
  备注: 'remark',
  说明: 'remark',
  remark: 'remark',
}

/**
 * 处理 Excel 文件上传
 */
const handleExcelUpload = (file: File) => {
  const fileName = file.name.toLowerCase()
  if (!fileName.endsWith('.xlsx') && !fileName.endsWith('.xls')) {
    ElMessage.error('请上传 .xlsx 或 .xls 格式的 Excel 文件')
    return false
  }

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
      // 将工作表转为 JSON（表头作为 key）
      const jsonData = XLSX.utils.sheet_to_json<any>(worksheet, { defval: '' })

      if (!jsonData || jsonData.length === 0) {
        ElMessage.warning('Excel 文件中没有数据')
        return
      }

      // 解析并填充数据
      const parsedItems = parseExcelData(jsonData)
      if (parsedItems.length === 0) {
        ElMessage.warning('未能从 Excel 中解析出有效数据，请检查表头列名')
        return
      }

      // 替换当前 items
      formData.items = parsedItems
      ElMessage.success(`成功导入 ${parsedItems.length} 条物料数据，请核对后提交`)
    } catch (error) {
      console.error('Excel 解析失败:', error)
      ElMessage.error('Excel 解析失败: ' + (error as Error).message)
    }
  }

  reader.readAsArrayBuffer(file)
  // 返回 false 阻止默认上传行为
  return false
}

/**
 * 解析 Excel 数据为入库明细
 */
const parseExcelData = (rows: any[]): InboundItemCreateParams[] => {
  // 获取表头映射
  const headers = Object.keys(rows[0])
  const columnMap: Record<string, string> = {}

  for (const header of headers) {
    const trimmedHeader = header.trim()
    const field = EXCEL_COLUMN_MAPPING[trimmedHeader]
    if (field) {
      columnMap[trimmedHeader] = field
    }
  }

  if (Object.keys(columnMap).length === 0) {
    // 尝试用序号列名匹配（如 A, B, C...）
    ElMessage.warning('未识别到标准表头，请确保表头包含：物料编码、物料名称、数量等列')
    return []
  }

  const items: InboundItemCreateParams[] = []
  const errors: string[] = []

  for (let i = 0; i < rows.length; i++) {
    const row = rows[i]
    try {
      const item = parseRow(row, columnMap)
      if (item) {
        items.push(item)
      }
    } catch (e) {
      errors.push(`第 ${i + 2} 行: ${(e as Error).message}`)
    }
  }

  if (errors.length > 0) {
    ElMessage.warning(
      `部分行解析失败:\n${errors.slice(0, 5).join('\n')}${errors.length > 5 ? `\n...还有 ${errors.length - 5} 个错误` : ''}`
    )
  }

  return items
}

/**
 * 解析单行数据
 */
const parseRow = (row: any, columnMap: Record<string, string>): InboundItemCreateParams | null => {
  const item: any = {
    materialId: '',
    batchNo: '',
    quantity: 1,
    unitPrice: 0,
    locationId: '',
    productionDate: '',
    expiryDate: '',
    remark: '',
  }

  let hasData = false

  for (const [header, field] of Object.entries(columnMap)) {
    let value = row[header]

    // 跳过空值
    if (value === undefined || value === null || value === '') {
      continue
    }

    // 转为字符串处理
    const strValue = String(value).trim()
    if (strValue === '') continue

    hasData = true

    switch (field) {
      case 'materialCode':
        item.materialCode = strValue
        break
      case 'materialName':
        item.materialName = strValue
        break
      case 'specification':
        item.specification = strValue
        break
      case 'unit':
        item.unit = strValue
        break
      case 'batchNo':
        item.batchNo = strValue
        break
      case 'quantity':
        item.quantity = parseNumber(strValue, '数量')
        break
      case 'unitPrice':
        item.unitPrice = parseNumber(strValue, '单价')
        break
      case 'amount':
        // 金额由数量和单价计算，不直接使用
        break
      case 'locationId':
        item.locationId = strValue
        break
      case 'productionDate':
        item.productionDate = parseDate(strValue)
        break
      case 'expiryDate':
        item.expiryDate = parseDate(strValue)
        break
      case 'remark':
        item.remark = strValue
        break
    }
  }

  if (!hasData) return null

  // 计算金额
  item.amount = item.quantity * item.unitPrice

  return item as InboundItemCreateParams
}

/**
 * 解析数字
 */
const parseNumber = (value: string, fieldName: string): number => {
  // 移除千分位逗号、空格等
  const cleaned = value.replace(/[,，\s]/g, '')
  const num = Number(cleaned)
  if (isNaN(num)) {
    throw new Error(`${fieldName} "${value}" 不是有效的数字`)
  }
  return num
}
const handleChange = (returnValue: any, material: any) => {
  // ✅ material 就是完整的 InventoryMaterial 对象
  console.log('returnValue:', returnValue)
  console.log('完整对象:', material)
}
/**
 * 解析日期
 */
const parseDate = (value: string): string => {
  // 尝试多种日期格式
  // 1. Excel 序列号（数字）
  const num = Number(value)
  if (!isNaN(num) && num > 1) {
    // Excel 日期序列号从 1900-01-01 开始
    const date = XLSX.SSF.parse_date_code(num)
    if (date) {
      return `${date.y}-${String(date.m).padStart(2, '0')}-${String(date.d).padStart(2, '0')}`
    }
  }

  // 2. 标准日期格式 yyyy-MM-dd
  const dateMatch1 = value.match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$/)
  if (dateMatch1) {
    return `${dateMatch1[1]}-${String(Number(dateMatch1[2])).padStart(2, '0')}-${String(Number(dateMatch1[3])).padStart(2, '0')}`
  }

  // 3. 日期格式 MM/dd/yyyy
  const dateMatch2 = value.match(/^(\d{1,2})[-/](\d{1,2})[-/](\d{4})$/)
  if (dateMatch2) {
    return `${dateMatch2[3]}-${String(Number(dateMatch2[1])).padStart(2, '0')}-${String(Number(dateMatch2[2])).padStart(2, '0')}`
  }

  // 4. 日期格式 yyyy年MM月dd日
  const dateMatch3 = value.match(/^(\d{4})年(\d{1,2})月(\d{1,2})日$/)
  if (dateMatch3) {
    return `${dateMatch3[1]}-${String(Number(dateMatch3[2])).padStart(2, '0')}-${String(Number(dateMatch3[3])).padStart(2, '0')}`
  }

  // 5. 日期格式 YY.MM.DD（如 25.03.12 → 2025-03-12）
  const dateMatch4 = value.match(/^(\d{2})\.(\d{1,2})\.(\d{1,2})$/)
  if (dateMatch4) {
    const year = Number(dateMatch4[1])
    const fullYear = year < 50 ? 2000 + year : 1900 + year
    return `${fullYear}-${String(Number(dateMatch4[2])).padStart(2, '0')}-${String(Number(dateMatch4[3])).padStart(2, '0')}`
  }

  // 无法解析，返回原值
  return value
}

// 取消
const handleCancel = () => {
  router.push('/inventory/inbound')
}

// 保存草稿
const handleSaveDraft = async () => {
  if (!(await validateForm())) return

  loading.value = true
  try {
    const res = await inboundApi.create(formData)
    const inboundId = (res as any)?.data?.data?.inboundId
    ElMessage.success('保存草稿成功')
    router.push(`/inventory/inbound/detail/${inboundId}`)
  } catch (error) {
    console.error('保存草稿失败:', error)
    ElMessage.error('保存草稿失败')
  } finally {
    loading.value = false
  }
}

// 提交审批
const handleSubmit = async () => {
  if (!(await validateForm())) return

  ElMessageBox.confirm('确认提交审批吗？', '提示', { type: 'warning' })
    .then(async () => {
      loading.value = true
      try {
        const res = await inboundApi.create(formData)
        const inboundId = (res as any)?.data?.data?.inboundId
        await inboundApi.submitApprove(inboundId)
        ElMessage.success('提交审批成功')
        router.push(`/inventory/inbound/detail/${inboundId}`)
      } catch (error) {
        console.error('提交审批失败:', error)
        ElMessage.error('提交审批失败')
      } finally {
        loading.value = false
      }
    })
    .catch(() => {})
}

// 表单验证
const validateForm = async (): Promise<boolean> => {
  if (!formRef.value) return false

  try {
    await formRef.value.validate()

    // 验证物料明细
    if (formData.items.length === 0) {
      ElMessage.warning('请至少添加一条物料明细')
      return false
    }

    for (const item of formData.items) {
      if (!item.materialId) {
        ElMessage.warning('请填写物料信息')
        return false
      }
      if (item.quantity <= 0) {
        ElMessage.warning('数量必须大于0')
        return false
      }
      if (item.unitPrice < 0) {
        ElMessage.warning('单价不能为负数')
        return false
      }
    }

    return true
  } catch (error) {
    return false
  }
}
</script>

<style scoped>
.inbound-create {
  padding: 20px;
  background-color: #fff;
}

.form-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.base-form {
  margin-bottom: 20px;
}

.item-section {
  margin-top: 20px;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.section-actions {
  display: flex;
  gap: 10px;
}

.summary-section {
  margin-top: 20px;
  padding: 15px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.summary-item .label {
  color: #606266;
  font-size: 14px;
}

.summary-item .value {
  color: #303133;
  font-size: 16px;
  font-weight: bold;
}
</style>
