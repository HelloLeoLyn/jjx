<template>
  <el-dialog
    v-model="dialogVisible"
    title="批量校验导入库存（模式③）"
    width="980px"
    top="5vh"
    destroy-on-close
    :close-on-click-modal="false"
  >
    <!-- 步骤1：选择文件 -->
    <div v-if="step === 1">
      <el-upload
        drag
        :auto-upload="false"
        :limit="1"
        accept=".xlsx,.xls"
        :on-change="handleFileChange"
        :on-exceed="handleExceed"
        style="width: 100%"
      >
        <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
        <template #tip>
          <div class="el-upload__tip">
            支持 .xlsx / .xls，建议使用「库存导入模板」格式。大文件（数千行）请使用本模式。
          </div>
        </template>
      </el-upload>

      <div v-if="parsed" class="parse-result">
        <el-alert
          :title="`解析成功：共 ${parsedCount} 条数据`"
          type="success"
          show-icon
          :closable="false"
          style="margin-top: 12px"
        />
        <div style="margin-top: 12px; text-align: right">
          <el-button @click="resetFile">重新选择</el-button>
          <el-button type="primary" :loading="checking" @click="handleBatchCheck">
            开始校验（{{ parsedCount }} 行）
          </el-button>
        </div>
      </div>
    </div>

    <!-- 步骤2：校验报告 -->
    <div v-else-if="step === 2" v-loading="checking">
      <!-- 统计卡片 -->
      <el-row :gutter="12">
        <el-col :span="6">
          <el-card shadow="never" class="stat-card">
            <div class="stat-value">{{ report.total }}</div>
            <div class="stat-label">总行数</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card ok">
            <div class="stat-value">{{ report.okCount }}</div>
            <div class="stat-label">校验通过</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card warn">
            <div class="stat-value">{{ report.notFoundCount }}</div>
            <div class="stat-label">物料未建档</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="never" class="stat-card err">
            <div class="stat-value">{{ report.errorCount }}</div>
            <div class="stat-label">其他错误</div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 操作栏 -->
      <div class="report-actions">
        <el-button type="success" :loading="creating" :disabled="report.notFoundCount === 0" @click="handleCreateMaterials">
          一键建档未建档物料（{{ report.notFoundCount }}）
        </el-button>
        <el-button @click="handleDownloadErrors">下载错误行</el-button>
        <el-button @click="handleRecheck">重新校验</el-button>
        <el-button type="primary" :loading="importing" :disabled="report.okCount === 0" @click="handleImportOk">
          仅导入校验通过的行（{{ report.okCount }}）
        </el-button>
      </div>

      <!-- 错误明细（可编辑修正） -->
      <div v-if="errorRows.length > 0" class="error-section">
        <div class="section-title">
          <el-icon><WarningFilled /></el-icon>
          错误明细（{{ errorRows.length }} 行，可编辑修正后点击「重新校验」）
        </div>
        <el-table :data="pagedErrorRows" border max-height="400" size="small" style="width: 100%">
          <el-table-column label="原行号" prop="rowIndex" width="70" align="center" />
          <el-table-column label="物料名称" width="160">
            <template #default="{ row }">
              <el-input v-model="row.materialName" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="规格" width="100">
            <template #default="{ row }">
              <el-input v-model="row.specification" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="数量" width="90">
            <template #default="{ row }">
              <el-input-number v-model="row.quantity" :min="0" :precision="4" size="small" style="width: 100%" />
            </template>
          </el-table-column>
          <el-table-column label="供应商" width="110">
            <template #default="{ row }">
              <el-input v-model="row.supplierName" size="small" />
            </template>
          </el-table-column>
          <el-table-column label="仓库" width="120">
            <template #default="{ row }">
              <el-select v-model="row.warehouseName" size="small" filterable style="width: 100%">
                <el-option v-for="w in warehouseOptions" :key="w.warehouseId" :label="w.warehouseName" :value="w.warehouseName" />
              </el-select>
            </template>
          </el-table-column>
          <el-table-column label="错误原因" min-width="200">
            <template #default="{ row }">
              <span class="error-msg">{{ getRowError(row) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination
          v-if="errorRows.length > 50"
          small
          layout="prev, pager, next, total"
          :total="errorRows.length"
          :page-size="50"
          v-model:current-page="errorPage"
          style="margin-top: 8px; justify-content: flex-end"
        />
      </div>

      <!-- 全部通过提示 -->
      <el-alert
        v-if="report.errorCount === 0 && report.notFoundCount === 0 && report.total > 0"
        title="全部校验通过，可直接导入"
        type="success"
        show-icon
        :closable="false"
        style="margin-top: 12px"
      />
    </div>

    <!-- 步骤3：导入结果 -->
    <div v-else-if="step === 3">
      <el-result
        icon="success"
        :title="`导入完成：成功 ${importResult?.successCount ?? 0} 条`"
        :sub-title="importResult && importResult.failCount > 0 ? `失败 ${importResult.failCount} 条` : '全部导入成功'"
      >
        <template #extra>
          <el-table
            v-if="importResult && importResult.failDetails && importResult.failDetails.length > 0"
            :data="importResult.failDetails"
            border
            max-height="300"
            size="small"
            style="width: 100%"
          >
            <el-table-column label="行号" prop="rowIndex" width="80" align="center" />
            <el-table-column label="物料" prop="materialName" width="200" />
            <el-table-column label="失败原因" prop="reason" />
          </el-table>
          <div style="margin-top: 16px">
            <el-button type="primary" @click="handleDone">完成</el-button>
          </div>
        </template>
      </el-result>
    </div>

    <template #footer>
      <template v-if="step === 1">
        <el-button @click="handleClose">取消</el-button>
      </template>
      <template v-else-if="step === 2">
        <el-button @click="handleBack">返回重选文件</el-button>
        <el-button type="primary" @click="handleImportOk" :disabled="report.okCount === 0" :loading="importing">
          确认导入（{{ report.okCount }} 行）
        </el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, WarningFilled } from '@element-plus/icons-vue'
import * as XLSX from 'xlsx'
import { stockApi, type StockBatchCheckItemVO } from '@/api/inventory/stock'
import { warehouseApi } from '@/api/inventory/warehouse'
import { materialApi } from '@/api/inventory/material'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{ 'update:visible': [boolean]; success: [] }>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (v) => emit('update:visible', v),
})

// 步骤：1=选文件 2=校验报告 3=导入结果
const step = ref(1)
const importFile = ref<File | null>(null)
const parsed = ref(false)
const parsedCount = ref(0)
const checking = ref(false)
const creating = ref(false)
const importing = ref(false)

// 原始数据行（含 rowIndex）
const rawRows = ref<any[]>([])
// 校验结果
const checkResults = ref<StockBatchCheckItemVO[]>([])
// 错误行数据（可编辑）
const errorRows = ref<any[]>([])
const errorPage = ref(1)
// 仓库选项（错误行修正用）
const warehouseOptions = ref<any[]>([])

const report = computed(() => {
  const total = checkResults.value.length
  const okCount = checkResults.value.filter((r) => r.status === 'ok').length
  const notFoundCount = checkResults.value.filter((r) => r.errorType === 'NOT_FOUND').length
  const errorCount = total - okCount
  return { total, okCount, notFoundCount, errorCount }
})

const pagedErrorRows = computed(() => {
  const start = (errorPage.value - 1) * 50
  return errorRows.value.slice(start, start + 50)
})

const importResult = ref<any>(null)

watch(
  () => props.visible,
  (v) => {
    if (v) {
      step.value = 1
      importFile.value = null
      parsed.value = false
      rawRows.value = []
      checkResults.value = []
      errorRows.value = []
      importResult.value = null
      loadWarehouseOptions()
    }
  }
)

const loadWarehouseOptions = async () => {
  try {
    const res = await warehouseApi.getAllEnabled()
    warehouseOptions.value = res.data || []
  } catch (error) {
    console.error('加载仓库失败:', error)
  }
}

const handleExceed = () => {
  ElMessage.warning('每次只能上传一个文件')
}

// 解析文件（与 StockImportDialog 一致：XLSX 解析 + 智能匹配字段）
const handleFileChange = (uploadFile: any) => {
  importFile.value = uploadFile.raw
  if (importFile.value) {
    parseFile()
  }
}

const parseFile = async () => {
  if (!importFile.value) return
  try {
    const data = await readFileAsArrayBuffer(importFile.value)
    const workbook = XLSX.read(data, { type: 'array' })
    const sheetName = workbook.SheetNames[0]
    const sheet = workbook.Sheets[sheetName]
    const jsonData: any[] = XLSX.utils.sheet_to_json(sheet, { defval: '' })

    const rows: any[] = []
    jsonData
      .filter((item: any) => {
        const keys = Object.keys(item)
        if (keys.length === 0) return false
        const firstVal = String(item[keys[0]] || '').trim()
        if (firstVal.startsWith('下面是') || firstVal.startsWith('请示')) return false
        return true
      })
      .forEach((item: any, idx: number) => {
        const keys = Object.keys(item)

        // 智能匹配物料名称
        let nameKey = keys.find(
          (k) => k.includes('物料名称') || k.includes('材料名称') || k.includes('物料名') || k.includes('材料名')
        )
        if (!nameKey) {
          const firstKey = keys[0]
          const firstVal = String(item[firstKey] || '').trim()
          if (
            firstKey !== '规格' &&
            !firstKey.includes('规格') &&
            !firstKey.includes('库存') &&
            !firstKey.includes('备注') &&
            !firstKey.includes('摆放')
          ) {
            nameKey = firstKey
          }
        }

        // 智能匹配规格
        let specKey = keys.find((k) => k.includes('规格'))
        if (!specKey && keys.length > 1) specKey = keys[1]

        // 智能匹配数量
        let qtyKey = keys.find((k) => k.includes('库存数量') || k.includes('库存量') || k === '数量' || k === '本月结存')
        if (!qtyKey && keys.length > 2) qtyKey = keys[2]

        // 供应商
        let supKey = keys.find((k) => k.includes('供应商'))
        // 摆放区域
        let locKey = keys.find((k) => k.includes('摆放') || k.includes('区域'))
        // 仓库
        let whKey = keys.find((k) => k.includes('仓库'))

        const name = nameKey ? String(item[nameKey] || '').trim() : ''
        const spec = specKey ? String(item[specKey] || '').trim() : ''
        const qtyRaw = qtyKey ? item[qtyKey] : ''
        const qty = qtyRaw === '' || qtyRaw === null ? 0 : Number(qtyRaw) || 0
        const supplier = supKey ? String(item[supKey] || '').trim() : ''
        const locationDesc = locKey ? String(item[locKey] || '').trim() : ''
        const warehouseName = whKey ? String(item[whKey] || '').trim() : '原料仓'

        // 过滤空行
        if (name === '' && qty <= 0) return

        rows.push({
          rowIndex: idx + 1,
          materialName: name,
          specification: spec,
          quantity: qty,
          supplierName: supplier,
          locationDesc,
          warehouseName,
          // 原始数据保留（导入时用）
          _raw: { ...item },
        })
      })

    rawRows.value = rows
    parsedCount.value = rows.length
    parsed.value = true
    if (rows.length === 0) {
      ElMessage.warning('未解析到有效数据，请检查文件格式')
      parsed.value = false
    }
  } catch (error) {
    console.error('解析文件失败:', error)
    ElMessage.error('解析文件失败，请检查文件格式')
  }
}

const readFileAsArrayBuffer = (file: File): Promise<ArrayBuffer> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => resolve(e.target?.result as ArrayBuffer)
    reader.onerror = reject
    reader.readAsArrayBuffer(file)
  })
}

const resetFile = () => {
  importFile.value = null
  parsed.value = false
  rawRows.value = []
}

// 批量校验（一次请求全部行）
const handleBatchCheck = async () => {
  if (rawRows.value.length === 0) return
  checking.value = true
  try {
    const payload = rawRows.value.map((r) => ({
      rowIndex: r.rowIndex,
      materialName: r.materialName,
      specification: r.specification,
      quantity: r.quantity,
      supplierName: r.supplierName,
      locationDesc: r.locationDesc,
      warehouseName: r.warehouseName,
    }))
    const res = await stockApi.batchCheck(payload)
    checkResults.value = res.data || []

    // 构造错误行（可编辑数据，含校验结果）
    errorRows.value = rawRows.value
      .map((r) => {
        const result = checkResults.value.find((c) => c.rowIndex === r.rowIndex)
        return {
          ...r,
          _checkResult: result,
          _errors: result?.errors || [],
        }
      })
      .filter((r) => r._checkResult?.status === 'error')

    errorPage.value = 1
    step.value = 2
    if (errorRows.value.length > 0) {
      ElMessage.warning(`校验完成：${report.value.okCount} 行通过，${errorRows.value.length} 行有错误`)
    } else {
      ElMessage.success(`校验完成：${report.value.okCount} 行全部通过`)
    }
  } catch (error) {
    console.error('批量校验失败:', error)
    ElMessage.error('批量校验失败')
  } finally {
    checking.value = false
  }
}

const getRowError = (row: any) => {
  return (row._errors || []).map((e: any) => e.message).join('；') || '未知错误'
}

// 一键建档未建档物料（NOT_FOUND 行）
const handleCreateMaterials = async () => {
  const notFoundRows = errorRows.value.filter((r) =>
    (r._checkResult?.errors || []).some((e: any) => e.type === 'NOT_FOUND')
  )
  if (notFoundRows.length === 0) return
  creating.value = true
  let successCount = 0
  let failCount = 0
  try {
    for (const row of notFoundRows) {
      try {
        const codeRes = await materialApi.generateCode()
        await materialApi.add({
          materialCode: codeRes.data || '',
          materialName: row.materialName,
          materialType: 'R',
          specification: row.specification,
          unit: 'PCS',
          supplierName: row.supplierName || '',
        } as any)
        // 更新本行校验结果为 ok（重新校验时会用）
        row.materialName = row.materialName
        row._forceOk = true
        successCount++
      } catch (e) {
        console.error(`建档 "${row.materialName}" 失败:`, e)
        failCount++
      }
    }
    ElMessage.success(`建档完成：成功 ${successCount} 条，失败 ${failCount} 条`)
    // 建档后重新校验（未建档行应变为 ok）
    await handleBatchCheck()
  } finally {
    creating.value = false
  }
}

// 重新校验（修正错误行后）
const handleRecheck = async () => {
  // 把修正后的错误行数据合并回 rawRows
  const errorMap = new Map(errorRows.value.map((r) => [r.rowIndex, r]))
  rawRows.value = rawRows.value.map((r) => {
    const fixed = errorMap.get(r.rowIndex)
    return fixed ? { ...r, materialName: fixed.materialName, specification: fixed.specification, quantity: fixed.quantity, supplierName: fixed.supplierName, warehouseName: fixed.warehouseName } : r
  })
  await handleBatchCheck()
}

// 下载错误行 Excel
const handleDownloadErrors = () => {
  if (errorRows.value.length === 0) return
  const wb = XLSX.utils.book_new()
  const data = errorRows.value.map((r) => ({
    原行号: r.rowIndex,
    物料名称: r.materialName,
    规格: r.specification,
    数量: r.quantity,
    供应商: r.supplierName,
    仓库: r.warehouseName,
    错误原因: getRowError(r),
  }))
  const ws = XLSX.utils.json_to_sheet(data)
  XLSX.utils.book_append_sheet(wb, ws, '错误行')
  XLSX.writeFile(wb, `库存导入错误行_${new Date().toISOString().slice(0, 10)}.xlsx`)
}

// 仅导入校验通过的行
const handleImportOk = async () => {
  const okRows = rawRows.value.filter((r) => {
    const result = checkResults.value.find((c) => c.rowIndex === r.rowIndex)
    return result?.status === 'ok'
  })
  if (okRows.length === 0) {
    ElMessage.warning('没有校验通过的行可导入')
    return
  }
  importing.value = true
  try {
    const payload = okRows.map((r) => ({
      materialName: r.materialName,
      specification: r.specification,
      quantity: r.quantity,
      remark: '',
      locationDesc: r.locationDesc,
      supplierName: r.supplierName,
      warehouseName: r.warehouseName,
    }))
    const res = await stockApi.batchImport(payload)
    importResult.value = res.data
    step.value = 3
    emit('success')
  } catch (error) {
    console.error('导入失败:', error)
    ElMessage.error('导入失败')
  } finally {
    importing.value = false
  }
}

const handleBack = () => {
  step.value = 1
  parsed.value = false
  rawRows.value = []
  checkResults.value = []
  errorRows.value = []
}

const handleDone = () => {
  dialogVisible.value = false
}

const handleClose = () => {
  dialogVisible.value = false
}
</script>

<style scoped>
.stat-card {
  text-align: center;
  padding: 4px 0;
}
.stat-value {
  font-size: 22px;
  font-weight: 600;
}
.stat-label {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.stat-card.ok .stat-value {
  color: #67c23a;
}
.stat-card.warn .stat-value {
  color: #e6a23c;
}
.stat-card.err .stat-value {
  color: #f56c6c;
}
.report-actions {
  margin: 12px 0;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.error-section {
  margin-top: 8px;
}
.section-title {
  font-weight: 600;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
  color: #f56c6c;
}
.error-msg {
  color: #f56c6c;
  font-size: 12px;
}
.parse-result {
  margin-top: 8px;
}
</style>
