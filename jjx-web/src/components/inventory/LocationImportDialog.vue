<template>
  <el-dialog
    title="导入库位"
    v-model="dialogVisible"
    width="75%"
    append-to-body
    :close-on-click-modal="false"
    @close="handleCancel"
  >
    <!-- 上传区域 -->
    <div v-if="!parsed" class="import-upload-area">
      <el-alert
        title="导入说明"
        type="info"
        :closable="false"
        show-icon
        style="margin-bottom: 16px"
      >
        <template #default>
          <p>
            1. 上传的Excel只需包含
            <strong>仓库名称</strong> 一列（表头需包含"仓库名称"或"名称"等关键词）
          </p>
          <p>2. 系统将根据仓库名称自动解析区域代码，并按规则生成库位编码</p>
          <p>
            3. 库位类型默认为 <strong>normal</strong>，容量默认 <strong>1000</strong>，尺寸默认
            <strong>100×50×50 cm</strong>
          </p>
          <p>4. 支持从您提供的库存Excel中直接导入（会自动识别"摆放/区域"列）</p>
        </template>
      </el-alert>
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
      </div>
      <el-table :data="dataList" border max-height="500" style="width: 100%; margin-top: 12px">
        <el-table-column type="index" label="序号" width="55" align="center" fixed />
        <el-table-column label="原始仓库名称" prop="rawName" width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <el-input v-model="row.rawName" size="small" @input="handleRowChange(row)" />
          </template>
        </el-table-column>
        <el-table-column label="区域代码" prop="areaCode" width="90" align="center">
          <template #default="{ row }">
            <el-tag size="small">{{ row.areaCode || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="库位类型" prop="locationType" width="90" align="center">
          <template #default="{ row }">
            <el-tag
              size="small"
              :type="
                row.locationType === 'pallet'
                  ? 'warning'
                  : row.locationType === 'rack'
                    ? 'primary'
                    : 'info'
              "
            >
              {{ locationTypeMap[row.locationType] || row.locationType }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="库位编码" prop="locationCode" width="180">
          <template #default="{ row }">
            <el-input v-model="row.locationCode" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="库位名称" prop="locationName" width="160">
          <template #default="{ row }">
            <el-input v-model="row.locationName" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="容量" prop="capacity" width="80" align="right">
          <template #default="{ row }">
            <el-input-number v-model="row.capacity" :min="0" size="small" style="width: 80px" />
          </template>
        </el-table-column>
        <el-table-column label="宽度(cm)" prop="width" width="80" align="right">
          <template #default="{ row }">
            <el-input-number
              v-model="row.width"
              :min="0"
              :precision="1"
              size="small"
              style="width: 80px"
            />
          </template>
        </el-table-column>
        <el-table-column label="高度(cm)" prop="height" width="80" align="right">
          <template #default="{ row }">
            <el-input-number
              v-model="row.height"
              :min="0"
              :precision="1"
              size="small"
              style="width: 80px"
            />
          </template>
        </el-table-column>
        <el-table-column label="深度(cm)" prop="depth" width="80" align="right">
          <template #default="{ row }">
            <el-input-number
              v-model="row.depth"
              :min="0"
              :precision="1"
              size="small"
              style="width: 80px"
            />
          </template>
        </el-table-column>
        <el-table-column label="备注" prop="remark" width="140">
          <template #default="{ row }">
            <el-input v-model="row.remark" size="small" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="70" fixed="right">
          <template #default="{ row, $index }">
            <el-button link type="danger" size="small" @click="handleDeleteRow($index)"
              >删除</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <!-- 底部操作栏 -->
      <div class="import-footer">
        <div class="import-footer-left">
          <el-button @click="handleBackToUpload">重新选择文件</el-button>
        </div>
        <div class="import-footer-right">
          <el-button type="primary" :loading="importLoading" @click="handleImport">
            开始导入
          </el-button>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import { locationApi } from '@/api/inventory/location'
import * as XLSX from 'xlsx'
import type { UploadInstance } from 'element-plus'

// ==================== 类型定义 ====================
export interface LocationImportRow {
  rawName: string // 原始仓库名称
  areaCode: string // 区域代码 (A-I, CORR, MISC, UNKNOWN)
  locationType: string // 库位类型: pallet(卡板), rack(架子), temp(临时)
  locationCode: string // 自动生成的库位编码
  locationName: string // 库位名称
  capacity: number // 容量
  width: number // 宽度(cm)
  height: number // 高度(cm)
  depth: number // 深度(cm)
  sortOrder: number // 排序
  remark: string // 备注
}

// ==================== 常量 ====================
// 区域代码映射
const AREA_KEYWORDS: Record<string, string> = {
  A区: 'A',
  'A 区': 'A',
  B区: 'B',
  'B 区': 'B',
  C区: 'C',
  'C 区': 'C',
  D区: 'D',
  'D 区': 'D',
  E区: 'E',
  'E 区': 'E',
  F区: 'F',
  'F 区': 'F',
  G区: 'G',
  'G 区': 'G',
  H区: 'H',
  'H 区': 'H',
  I区: 'I',
  'I 区': 'I',
}

// 库位类型映射
const locationTypeMap: Record<string, string> = {
  pallet: '卡板',
  rack: '架子',
  temp: '临时',
}

// 默认值
const DEFAULT_CAPACITY = 1000
const DEFAULT_WIDTH = 100
const DEFAULT_HEIGHT = 50
const DEFAULT_DEPTH = 50

// 区域计数器（用于生成流水号）
const areaCounters: Record<
  string,
  { pallet: Record<string, number>; rack: Record<string, number>; temp: number }
> = {}

// ==================== Props & Emits ====================
const props = defineProps<{
  visible: boolean
  warehouseId: number
}>()

const emit = defineEmits<{
  (e: 'update:visible', val: boolean): void
  (e: 'success'): void
}>()

// ==================== 响应式状态 ====================
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

const parsed = ref(false)
const parsingLoading = ref(false)
const importLoading = ref(false)
const createLocation = ref(false)
const uploadRef = ref<UploadInstance>()
const importFile = ref<File | null>(null)
const dataList = ref<LocationImportRow[]>([])

// ==================== 核心解析逻辑 ====================

/**
 * 从仓库名称中提取区域代码
 */
function extractAreaCode(rawName: string): string {
  // 先尝试匹配已知区域关键词
  for (const [keyword, code] of Object.entries(AREA_KEYWORDS)) {
    if (rawName.includes(keyword)) {
      return code
    }
  }

  // 匹配走廊
  if (rawName.includes('走廊') || rawName.includes('CORR') || rawName.includes('corr')) {
    return 'CORR'
  }

  // 匹配杂项（LED灯、柜子后面等）
  if (rawName.includes('LED') || rawName.includes('柜子') || rawName.includes('杂')) {
    return 'MISC'
  }

  return 'UNKNOWN'
}

/**
 * 判断库位类型
 */
function determineLocationType(rawName: string): 'pallet' | 'rack' | 'temp' {
  if (rawName.includes('卡板') || rawName.includes('Pallet') || rawName.includes('pallet')) {
    return 'pallet'
  }
  if (rawName.includes('架') || rawName.includes('Rack') || rawName.includes('rack')) {
    return 'rack'
  }
  return 'temp'
}

/**
 * 从仓库名称中提取卡板编号
 * 例如: "A区 卡板 1" → 1, "B区 卡板 2" → 2
 */
function extractPalletNumber(rawName: string): number {
  const match = rawName.match(/卡板\s*(\d+)/)
  if (match) {
    return parseInt(match[1])
  }
  return 1
}

/**
 * 从仓库名称中提取架子信息
 * 例如: "F区架一，二层" → { rack: 1, level: 2, grid: 1 }
 * "D区架一，一层-1格" → { rack: 1, level: 1, grid: 1 }
 * "F区架二，三层-1格" → { rack: 2, level: 3, grid: 1 }
 */
function extractRackInfo(rawName: string): { rack: number; level: number; grid: number } {
  let rack = 1
  let level = 1
  let grid = 1

  // 提取架号：架一、架二、架三...
  const rackMatch = rawName.match(/架([一二三四五六七八九十\d]+)/)
  if (rackMatch) {
    rack = chineseToNumber(rackMatch[1])
  }

  // 提取层：一层、二层、三层... 或 层-1、层-2...
  const levelMatch = rawName.match(/([一二三四五六七八九十\d]+)层/)
  if (levelMatch) {
    level = chineseToNumber(levelMatch[1])
  }

  // 提取格：1格、2格... 或 格-1、格-2...
  const gridMatch = rawName.match(/(\d+)格/)
  if (gridMatch) {
    grid = parseInt(gridMatch[1])
  }

  return { rack, level, grid }
}

/**
 * 中文数字转阿拉伯数字
 */
function chineseToNumber(chinese: string): number {
  const map: Record<string, number> = {
    一: 1,
    二: 2,
    三: 3,
    四: 4,
    五: 5,
    六: 6,
    七: 7,
    八: 8,
    九: 9,
    十: 10,
  }
  if (map[chinese]) return map[chinese]
  const num = parseInt(chinese)
  if (!isNaN(num)) return num
  return 1
}

/**
 * 生成库位编码
 */
function generateLocationCode(
  areaCode: string,
  type: 'pallet' | 'rack' | 'temp',
  rawName: string,
  existingCodes: Set<string>
): string {
  // 初始化计数器
  if (!areaCounters[areaCode]) {
    areaCounters[areaCode] = { pallet: {}, rack: {}, temp: 0 }
  }

  let code = ''
  let attempt = 0
  const maxAttempts = 1000

  do {
    attempt++
    if (type === 'pallet') {
      const palletNum = extractPalletNumber(rawName)
      const palletKey = `P${String(palletNum).padStart(2, '0')}`
      if (!areaCounters[areaCode].pallet[palletKey]) {
        areaCounters[areaCode].pallet[palletKey] = 0
      }
      areaCounters[areaCode].pallet[palletKey]++
      const seq = String(areaCounters[areaCode].pallet[palletKey]).padStart(2, '0')
      code = `RAW-${areaCode}-${palletKey}-${seq}`
    } else if (type === 'rack') {
      const { rack, level, grid } = extractRackInfo(rawName)
      const rackKey = `R${String(rack).padStart(2, '0')}`
      const levelStr = String(level).padStart(2, '0')
      const gridStr = String(grid).padStart(2, '0')
      code = `RAW-${areaCode}-${rackKey}-${levelStr}-${gridStr}`
    } else {
      areaCounters[areaCode].temp++
      const seq = String(areaCounters[areaCode].temp).padStart(4, '0')
      code = `RAW-${areaCode}-${seq}`
    }

    // 如果编码已存在，增加序号重试
    if (existingCodes.has(code)) {
      if (type === 'pallet') {
        areaCounters[areaCode].pallet[`P${String(extractPalletNumber(rawName)).padStart(2, '0')}`]++
      } else if (type === 'temp') {
        areaCounters[areaCode].temp++
      }
    }
  } while (existingCodes.has(code) && attempt < maxAttempts)

  existingCodes.add(code)
  return code
}

/**
 * 生成库位名称
 */
function generateLocationName(rawName: string, areaCode: string, type: string): string {
  // 如果原始名称已经比较清晰，直接使用
  if (rawName && rawName.length > 1) {
    return rawName.trim()
  }
  // 否则根据区域和类型生成
  const typeName = locationTypeMap[type] || type
  return `${areaCode}区-${typeName}`
}

/**
 * 解析单行仓库名称，生成完整的库位数据
 */
function parseWarehouseName(
  rawName: string,
  existingCodes: Set<string>,
  index: number
): LocationImportRow | null {
  const name = String(rawName || '').trim()
  if (!name) return null

  const areaCode = extractAreaCode(name)
  const locationType = determineLocationType(name)
  const locationCode = generateLocationCode(areaCode, locationType, name, existingCodes)
  const locationName = generateLocationName(name, areaCode, locationType)

  return {
    rawName: name,
    areaCode,
    locationType,
    locationCode,
    locationName,
    capacity: DEFAULT_CAPACITY,
    width: DEFAULT_WIDTH,
    height: DEFAULT_HEIGHT,
    depth: DEFAULT_DEPTH,
    sortOrder: index + 1,
    remark: '',
  }
}

/**
 * 当用户修改原始名称时，重新解析
 */
function handleRowChange(row: LocationImportRow) {
  // 重新解析
  const areaCode = extractAreaCode(row.rawName)
  const locationType = determineLocationType(row.rawName)
  row.areaCode = areaCode
  row.locationType = locationType
  // 不自动重新生成编码，让用户手动修改
}

// ==================== 文件处理 ====================

const handleFileChange = (uploadFile: any) => {
  importFile.value = uploadFile.raw
  if (importFile.value) {
    handleParseFile()
  }
}

const handleExceed = () => {
  ElMessage.warning('每次只能上传一个文件')
}

const handleDownloadTemplate = async () => {
  try {
    const res = await locationApi.downloadImportTemplate()
    const blob = new Blob([res as any], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = '库位导入模板.xlsx'
    link.click()
    URL.revokeObjectURL(link.href)
  } catch (error) {
    ElMessage.error('下载模板失败')
  }
}

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

    // 重置计数器
    Object.keys(areaCounters).forEach((key) => delete areaCounters[key])
    const existingCodes = new Set<string>()

    // 智能解析：尝试匹配仓库名称列
    const parsedRows: LocationImportRow[] = []
    let index = 0

    for (const item of jsonData) {
      const keys = Object.keys(item)
      if (keys.length === 0) continue

      // 过滤掉分类标题行
      const firstVal = String(item[keys[0]] || '').trim()
      if (firstVal.startsWith('下面是') || firstVal.startsWith('请示')) continue

      // 智能匹配仓库名称列
      let nameKey = keys.find(
        (k) =>
          k.includes('仓库名称') ||
          k.includes('仓库名') ||
          k.includes('摆放') ||
          k.includes('区域') ||
          k === '摆放 / 区域' ||
          k === '摆放/区域' ||
          k === '摆放区域' ||
          k === '名称' ||
          k === '位置'
      )

      // 如果找不到标准表头，尝试用第一个非空值作为仓库名称
      if (!nameKey) {
        // 检查第一个 key 的值是否像仓库名称
        const firstKey = keys[0]
        const firstVal = String(item[firstKey] || '').trim()
        if (
          firstKey !== '规格' &&
          !firstKey.includes('规格') &&
          !firstKey.includes('库存') &&
          !firstKey.includes('备注') &&
          !firstKey.includes('材料') &&
          !firstKey.includes('物料')
        ) {
          nameKey = firstKey
        }
      }

      if (!nameKey) continue

      const rawName = String(item[nameKey] || '').trim()
      if (!rawName) continue

      // 尝试从备注/说明列提取额外信息
      let remarkKey = keys.find(
        (k) => k.includes('备注') || k.includes('说明') || k === '备注 / 说明' || k === '备注/说明'
      )
      const remark = remarkKey ? String(item[remarkKey] || '').trim() : ''

      const row = parseWarehouseName(rawName, existingCodes, index)
      if (row) {
        if (remark) {
          row.remark = remark
        }
        parsedRows.push(row)
        index++
      }
    }

    // 按仓库名称去重（保留第一次出现的记录）
    const seenNames = new Set<string>()
    const dedupedRows: LocationImportRow[] = []
    let dupCount = 0
    for (const row of parsedRows) {
      if (seenNames.has(row.rawName)) {
        dupCount++
      } else {
        seenNames.add(row.rawName)
        dedupedRows.push(row)
      }
    }

    dataList.value = dedupedRows
    parsed.value = true

    if (dupCount > 0) {
      ElMessage.warning(
        `解析完成，已自动去重 ${dupCount} 条重复的仓库名称，剩余 ${dataList.value.length} 条`
      )
    } else {
      ElMessage.success(`成功解析 ${dataList.value.length} 条数据`)
    }
  } catch (error) {
    console.error('解析文件失败:', error)
    ElMessage.error('解析文件失败，请检查文件格式')
  } finally {
    parsingLoading.value = false
  }
}

const readFileAsArrayBuffer = (file: File): Promise<ArrayBuffer> => {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => resolve(e.target!.result as ArrayBuffer)
    reader.onerror = (e) => reject(e)
    reader.readAsArrayBuffer(file)
  })
}

// ==================== 操作处理 ====================

const handleBackToUpload = () => {
  parsed.value = false
  dataList.value = []
  uploadRef.value?.clearFiles()
}

const handleCancel = () => {
  dialogVisible.value = false
}

const handleDeleteRow = (index: number) => {
  dataList.value.splice(index, 1)
  ElMessage.success('已删除该行')
}

const handleImport = async () => {
  const emptyNameRows = dataList.value.filter((row) => !row.rawName)
  if (emptyNameRows.length > 0) {
    await ElMessageBox.confirm(
      `有 ${emptyNameRows.length} 条数据缺少仓库名称，是否跳过这些数据继续导入？`,
      '提示',
      { type: 'warning', confirmButtonText: '跳过并继续', cancelButtonText: '取消' }
    )
  }

  importLoading.value = true
  try {
    const validData = dataList.value.filter((row) => row.rawName && row.locationCode)
    if (validData.length === 0) {
      ElMessage.warning('没有可导入的数据')
      return
    }

    // 构建导入数据（与后端 StorageLocationImportDTO 字段对应）
    const importData = validData.map((row) => ({
      locationCode: row.locationCode,
      locationName: row.locationName,
      locationType: 'normal',
      capacity: String(row.capacity),
      width: String(row.width),
      height: String(row.height),
      depth: String(row.depth),
      sortOrder: String(row.sortOrder),
      remark: row.remark || `来源: ${row.rawName}`,
    }))

    // 直接发送 JSON 数据到后端
    const res = await locationApi.importLocation(importData, props.warehouseId)
    ElMessage.success(res.data || `成功导入 ${validData.length} 条数据`)
    dialogVisible.value = false
    emit('success')
  } catch (error) {
    console.error('导入失败:', error)
    ElMessage.error('导入失败')
  } finally {
    importLoading.value = false
  }
}

// ==================== 重置 ====================
const resetState = () => {
  parsed.value = false
  parsingLoading.value = false
  importLoading.value = false
  importFile.value = null
  dataList.value = []
  uploadRef.value?.clearFiles()
  Object.keys(areaCounters).forEach((key) => delete areaCounters[key])
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
