<template>
  <el-dialog
    v-model="visible"
    width="1300px"
    append-to-body
    :fullscreen="isFullscreen"
    destroy-on-close
    :close-on-click-modal="false"
    :before-close="handleBeforeClose"
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
    <!-- 编辑占用提示（dev-20261111-018）：占用失败/心跳失效时只读，草稿保留，不后台接管 -->
    <el-alert
      v-if="editSession.readOnly.value"
      class="lease-alert"
      type="warning"
      :closable="false"
      show-icon
      :title="editSession.message.value"
    >
      <template #default>
        <div class="lease-actions">
          <span v-if="editSession.ownerName.value">当前占用：{{ editSession.ownerName.value }}</span>
          <el-button size="small" @click="handleReacquire">重新申请编辑权</el-button>
          <el-button size="small" type="primary" plain @click="handleReloadLatest">
            重新加载最新内容
          </el-button>
        </div>
      </template>
    </el-alert>
    <!-- 只读（其他窗口占用/编辑权失效）时整表单禁用，不只是禁保存按钮（dev-20261111-018 修复卡 F6b） -->
    <el-form
      ref="bomFormRef"
      :model="formData"
      :rules="rules"
      :disabled="editSession.readOnly.value"
      label-width="120px"
    >
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
          <div class="upload-tip">
            仅支持 .xlsx / .xls
            格式，解析后自动填充到物料明细表格。导入的物料<b>按行顺序追加到末尾</b>，可在表格中拖拽调整顺序
          </div>
        </template>
      </el-upload>
      <div class="template-row">
        <el-button link type="primary" @click="downloadImportTemplate">
          <el-icon><Download /></el-icon>下载导入模板（.xlsx）
        </el-button>
        <span class="template-hint"
          >必填：材料名称、规格（乘/跳）、模数；其余列可空。也支持单列「规格及模数」（如
          340*305mm=8PCS、315宽跳75mm=2PCS、6个/PCS；基数缺省
          1），此时不需要单独的规格/模数列。列顺序：序号｜项目名称｜材料名称｜单位｜宽度｜规格（乘/跳）｜长度｜模数｜基数｜预计不良｜最低投料｜实际投料（首行表头，数据从第
          2 行起；预计不良填小数，如
          0.05＝5%）。用量由基数÷模数计算，应用料再计入损耗；Excel的单用量、实发数量、数量、应用料列不作为计算输入。</span
        >
      </div>
    </div>

    <BomItemEditor
      v-model="formData.items"
      :bom-id="formData.bomId"
      :readonly="editSession.readOnly.value"
      @change="handleItemsChange"
      @manufacturing="handleManufacturing"
      ref="bomItemEditorRef"
    />

    <template #footer>
      <div class="dialog-footer">
        <el-button v-if="formData.items?.length" icon="Printer" @click="printPreviewVisible = true"
          >打印预览</el-button
        >
        <el-button @click="handleCancel">关 闭</el-button>
        <el-button
          type="primary"
          :loading="submitting"
          :disabled="editSession.readOnly.value"
          @click="submitForm"
          >保存</el-button
        >
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

    <!-- 独立制造BOM窗口（dev-20261111-005）：只保存独立方案/回填草稿行，父保存才持久化 -->
    <ManufacturingBomDialog
      v-model="manufacturingVisible"
      :material="manufacturingContext"
      :product-id="formData.productId"
      :product-code="formData.productCode"
      :product-name="formData.productName"
      :sub-bom-id="manufacturingSubBomId"
      :primary-bom-id="manufacturingPrimaryBomId"
      @adopt="handleSchemeAdopt"
      @primary-change="handlePrimaryChange"
    />
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch, nextTick, onMounted, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules, UploadFile, UploadInstance } from 'element-plus'
import { FullScreen, UploadFilled, Download } from '@element-plus/icons-vue'
import { productBomApi } from '@/api/product/bom'
import { getProductInfo } from '@/api/product'
import BomItemEditor from '@/components/BomItemEditor.vue'
import ManufacturingBomDialog from './ManufacturingBomDialog.vue'
import BomPrintPreview from './BomPrintPreview.vue'
import ProductSelector from '@/components/Selector/ProductSelector.vue'
import type {
  EngineeringBomFormData,
  EngineeringBomItem,
  ManufacturingSchemeChoice,
} from '@/types/product/bom'
import { useBomEditSession, isContentConflict } from '../composables/useBomEditSession'
import type { ProductItem, ProductVo } from '@/types/product'
import * as XLSX from 'xlsx'
import { parseSpecModule } from './bomSpecImport'
import { calculateBomQuantity } from '@/utils/bomQuantity'

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
    return [
      {
        productId: formData.productId,
        productCode: formData.productCode,
        productName: formData.productName,
      } as ProductItem,
    ]
  }
  return []
})

// 表单数据
const formData = reactive<EngineeringBomFormData>({
  bomId: undefined,
  bomCode: '',
  bomName: '',
  bomVersion: '',
  productId: null,
  productCode: '',
  productName: '',
  isCurrent: false,
  effectiveDate: '',
  expiryDate: '',
  remark: '',
  items: [],
})

// 编辑占用（dev-20261111-018）：同一 bomId 只允许一个编辑会话；占用失败即只读
const editSession = useBomEditSession(() => formData.bomId)

// 表单验证规则
const rules = reactive<FormRules>({
  productId: [
    {
      required: true,
      trigger: 'change',
      validator: (_rule, value, callback) => {
        if (typeof value === 'number' && Number.isSafeInteger(value) && value > 0) callback()
        else callback(new Error('请选择有效产品'))
      },
    },
  ],
  bomVersion: [{ message: '请输入BOM版本', trigger: 'blur' }],
  bomName: [{ required: true, message: '请输入BOM名称', trigger: 'blur' }],
  bomCode: [{ required: true, message: '请输入BOM编码', trigger: 'blur' }],
  effectiveDate: [{ required: true, message: '请选择生效日期', trigger: 'change' }],
})

// 处理产品选择变化
const handleProductChange = (productId: number | null, product: any) => {
  const oldCode = formData.productCode
  const oldName = formData.productName
  // 空白或仍沿用自动名称时，随产品更新；手工名称与编辑回填不被覆盖。
  const hasDefaultName =
    !formData.bomName.trim() ||
    (!!oldCode && formData.bomName === `${oldCode}-BOM`) ||
    (!!oldName && formData.bomName === `${oldName}-BOM`)
  if (!product) {
    formData.productId = null
    resetSchemeContext()
    // 2026-09-15：清空产品时同步清掉回填字段——残留会让 productOptions 非空，
    // 且提交时 productCode/productName 与 productId 不一致
    if (oldCode && formData.bomCode === `${oldCode}-BOM`) {
      formData.bomCode = ''
    }
    if (hasDefaultName) {
      formData.bomName = ''
    }
    formData.productCode = ''
    formData.productName = ''
    return
  }
  formData.productCode = product.productCode
  formData.productName = product.productName
  const productChanged = Number(formData.productId) !== Number(product.productId)
  formData.productId = product.productId
  // 产品隔离：换产品后，上一产品的主方案回显与草稿调整不能沿用到新产品
  if (productChanged) {
    resetSchemeContext()
    // 新建 BOM 首次设主方案要用真实版本：选定产品后先读一次（迟到响应由 token+产品校验丢弃）
    void fetchProductSchemeRevision(Number(product.productId))
  }
  if (hasDefaultName) formData.bomName = `${product.productCode}-BOM`
  formData.bomCode = `${product.productCode}-BOM`
}

// ==================== 制造BOM方案（dev-20261111-005） ====================
// 边界：采用只回填父 BOM 草稿行；主方案按 product×material 独立，只有用户显式调整才随父保存；
// 从不提交 isPrimary=false 覆盖原主；编辑已保存 BOM 时必须带 getBomDetail 的 schemeRevision。

const manufacturingVisible = ref(false)
const manufacturingRow = ref<EngineeringBomItem | null>(null)
/** getBomDetail 返回的 product.scheme_revision（提交主方案时用于乐观锁，防并发覆盖） */
const schemeRevision = ref<number | null>(null)
/** 新建 BOM 时用 GET /product/{id} 读到的产品主方案版本（首次设主必带真实值，不能默认 0） */
const productSchemeRevision = ref<number | null>(null)
/** 异步选产品的迟到响应保护：只有最后一次请求且产品未变才写回 */
let productRevisionToken = 0
/** 产品已保存的主方案选择（只读回显，不整表回写） */
const savedSchemeChoices = ref<ManufacturingSchemeChoice[]>([])
/** 用户本次显式调整的主方案：materialId → {materialId,bomId}；未调整的物料不提交 */
const primaryChoices = ref<Record<number, { materialId: number; bomId: number }>>({})

const manufacturingContext = computed(() => ({
  materialId: Number(manufacturingRow.value?.materialId) || 0,
  materialCode: manufacturingRow.value?.materialCode || '',
  materialName: manufacturingRow.value?.materialName || '',
  specification:
    manufacturingRow.value?.materialSpecification || manufacturingRow.value?.specification || '',
  unit: manufacturingRow.value?.unit || '',
}))

const manufacturingSubBomId = computed(() => {
  const subBomId = Number(manufacturingRow.value?.subBomId)
  return subBomId > 0 ? subBomId : null
})

const manufacturingPrimaryBomId = computed(() => {
  const materialId = manufacturingContext.value.materialId
  if (!materialId) return null
  const pending = primaryChoices.value[materialId]
  if (pending) return Number(pending.bomId)
  const saved = savedSchemeChoices.value.find(
    (choice) => Number(choice.materialId) === materialId && choice.isPrimary === true
  )
  return saved ? Number(saved.bomId) : null
})

/** 随父保存提交的主方案选择：只有用户显式调整过的物料；不追加 isPrimary=false。 */
const schemeChoicesPayload = (): ManufacturingSchemeChoice[] =>
  Object.values(primaryChoices.value).map((choice) => ({
    materialId: Number(choice.materialId),
    bomId: Number(choice.bomId),
    isPrimary: true,
  }))

const mergeSavedChoices = (
  existing: ManufacturingSchemeChoice[],
  choices: ManufacturingSchemeChoice[]
): ManufacturingSchemeChoice[] => {
  const rest = existing.filter(
    (item) => !choices.some((choice) => Number(choice.materialId) === Number(item.materialId))
  )
  return [...rest, ...choices.map((choice) => ({ ...choice, isPrimary: true }))]
}

const handleManufacturing = (row: EngineeringBomItem) => {
  manufacturingRow.value = row
  manufacturingVisible.value = true
}

/**
 * 采用制造方案：只把本行的 subBomId/sourceType 写回父 BOM 草稿（父保存才持久化）。
 * 不设主方案、不改其他行、不删除任何独立方案。
 */
const handleSchemeAdopt = (payload: { materialId: number; bomId: number }) => {
  const materialId = Number(payload?.materialId)
  const bomId = Number(payload?.bomId)
  if (!materialId || !bomId) return
  const row = manufacturingRow.value
  if (!row || Number(row.materialId) !== materialId) {
    ElMessage.warning('该行物料已变化，请重新打开制造BOM窗口后再采用')
    return
  }
  row.subBomId = bomId
  row.sourceType = 'make'
  bomItemEditorRef.value?.updateItem?.(Number(row.itemId), { subBomId: bomId, sourceType: 'make' })
  recomputeDirty()
}

/** 用户显式“设为主方案”：只记录草稿调整，随父 BOM 保存同一事务持久化。 */
const handlePrimaryChange = (payload: { materialId: number; bomId: number }) => {
  const materialId = Number(payload?.materialId)
  const bomId = Number(payload?.bomId)
  if (!materialId || !bomId) return
  if (!Number(formData.productId)) {
    ElMessage.warning('请先选择并保存产品，再设置主方案')
    return
  }
  primaryChoices.value = { ...primaryChoices.value, [materialId]: { materialId, bomId } }
  recomputeDirty()
}

/** 清除本任务引入的制造方案上下文（重置/换产品时必须清空，避免产品之间串选择）。 */
const resetSchemeContext = () => {
  manufacturingRow.value = null
  manufacturingVisible.value = false
  schemeRevision.value = null
  productSchemeRevision.value = null
  // 换产品/重置后，之前发出的版本请求结果一律作废
  productRevisionToken += 1
  savedSchemeChoices.value = []
  primaryChoices.value = {}
}

/** scheme_revision 解析：0 是合法初值，null/空/非法都视为“未加载到”，不能当成 0。 */
const parseSchemeRevision = (value: unknown): number | null => {
  if (value === null || value === undefined || value === '') return null
  const revision = Number(value)
  return Number.isInteger(revision) && revision >= 0 ? revision : null
}

/**
 * 新建 BOM 首次设主方案时的真实版本：走现有 GET /product/{id}（ProductVo.schemeRevision），
 * 不新增表/列/接口，也不默认 0。迟到响应（A 之后又选了 B）按 token+当前产品丢弃。
 */
const fetchProductSchemeRevision = async (productId: number): Promise<number | null> => {
  const target = Number(productId)
  if (!Number.isSafeInteger(target) || target <= 0) return null
  // 旧调用方/测试桩可能未提供该接口：按“未读到”处理，绝不默认 0
  if (typeof getProductInfo !== 'function') return null
  const token = ++productRevisionToken
  let revision: number | null = null
  try {
    const response: any = await getProductInfo(target)
    const data: ProductVo | null = response?.data ?? null
    revision = parseSchemeRevision(data?.schemeRevision)
  } catch (error) {
    console.error('读取产品主方案版本失败:', error)
    revision = null
  }
  if (token !== productRevisionToken) return null
  if (Number(formData.productId) !== target) return null
  productSchemeRevision.value = revision
  return revision
}

// ==================== 未保存离开拦截（dev-20261021-015，沿用工艺路线范式） ====================
const text = (value: unknown) => (value == null ? '' : String(value).trim())
const num = (value: unknown) => {
  const n = Number(value)
  return Number.isFinite(n) ? n : 0
}
/** 明细只取用户可编辑列；应用料/实际投料等派生值不计入，负临时 itemId 不参与比较。 */
const snapshotItems = (items: EngineeringBomItem[]) =>
  (items || []).map((item) => ({
    materialId: num(item.materialId),
    materialCode: text(item.materialCode),
    materialName: text(item.materialName),
    specification: text(item.specification),
    // processId 由项目名称异步回填（useEnabledProcesses），不作为 dirty 依据；用户改选会同时改 processName。
    processName: text(item.processName),
    unit: text(item.unit),
    quantity: num(item.quantity),
    lossRate: num(item.lossRate),
    remark: text(item.remark),
    moduleQty: num(item.moduleQty),
    split: text(item.split),
    baseQty: num(item.baseQty),
    minIssueQty: num(item.minIssueQty),
    widthMm: num(item.widthMm),
    lengthMm: num(item.lengthMm),
    // 制造方案引用也是用户可编辑结果（采用/取消采用必须触发未保存提示）
    subBomId: num(item.subBomId),
    sourceType: text(item.sourceType),
  }))

/** 完整快照：表头可编辑字段 + 明细关键列；明细取编辑器当前值，避免只 watch formData 漏判。 */
const fullFormSnapshot = (): string =>
  JSON.stringify({
    productId: formData.productId ?? null,
    bomCode: text(formData.bomCode),
    bomName: text(formData.bomName),
    bomVersion: text(formData.bomVersion),
    isCurrent: !!formData.isCurrent,
    effectiveDate: text(formData.effectiveDate),
    expiryDate: text(formData.expiryDate),
    remark: text(formData.remark),
    items: snapshotItems(bomItemEditorRef.value?.getItems?.() || formData.items || []),
    schemeChoices: schemeChoicesPayload(),
  })

const baseline = ref('')
const isDirty = ref(false)
const suppressGuard = ref(false)
const setBaseline = () => {
  baseline.value = fullFormSnapshot()
  isDirty.value = false
}
const recomputeDirty = () => {
  if (!baseline.value) {
    isDirty.value = false
    return
  }
  isDirty.value = fullFormSnapshot() !== baseline.value
}

// 表头任意变更都重算；明细在编辑器内部，靠 change 事件触发（内部取 getItems）。
watch(formData, recomputeDirty, { deep: true })

// 处理BOM明细变化
const handleItemsChange = () => {
  recomputeDirty()
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
    '序号',
    '项目名称',
    '材料名称（必填）',
    '单位',
    '宽度',
    '规格（乘/跳）（必填）',
    '长度',
    '模数（必填）',
    '基数',
    '预计不良',
    '最低投料',
    '实际投料',
  ]
  const sample = [
    [1, '示例-主体', '白卡纸 300g', '张', 787, '*', 1092, 1, 1000, 0.05, '', ''],
    [2, '示例-内衬', '瓦楞纸板', '张', 500, '/', 700, 1, 1000, 0.03, '', ''],
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
      ElMessage.success(`成功解析并添加 ${items.length} 项物料，可在表格中拖拽调整顺序`)
      const noProject = items.filter(
        (it) => it.processId == null && !!it.processName?.trim()
      ).length
      if (noProject > 0) {
        ElMessageBox.alert(
          `本次导入有 ${noProject} 行项目名称尚未匹配工序，请选择对应项目，或清空项目作为公共物料。`,
          '有物料未挂工序',
          { confirmButtonText: '知道了', type: 'warning' }
        ).catch(() => {})
      }
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
 * 用量、应用料属于系统计算结果，不映射Excel单用量/实发数量/数量/应用料列；其余列有就取、没有就留空（用户口径：必要的那几项有就行，其他按实际情况来）。
 * 表头别名：品名=原料品名/材料名称/品名；项目=项目/项目名称，独立于备注。
 */
const parseRows = (rows: any[][]): EngineeringBomItem[] => {
  const items: EngineeringBomItem[] = []
  const missingRows: number[] = []

  const ALIAS: Record<string, string[]> = {
    seq: ['项次', '序号'],
    name: ['原料品名', '材料名称', '品名', '材料'],
    item: ['项目', '项目名称'],
    combined: ['规格及模数'],
    spec: ['规格'],
    unit: ['单位'],
    module: ['模数'],
    base: ['基数'],
    loss: ['预计不良'],
    minIssue: ['最低投料'],
    actualIssue: ['实际投料'],
    width: ['宽度'],
    length: ['长度'],
    split: ['跳距', 'split'],
    remark: ['备注'],
  }
  const matchKey = (cell: any): string | null => {
    const t = String(cell ?? '').trim()
    if (!t) return null
    // 先精确匹配，避免「规格及模数」被「规格」抢先命中。
    for (const key of Object.keys(ALIAS)) {
      if (ALIAS[key].some((n) => t === n)) return key
    }
    for (const key of Object.keys(ALIAS)) {
      if (ALIAS[key].some((n) => t.includes(n))) return key
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
    if (
      m.name !== undefined &&
      ((m.spec !== undefined && m.module !== undefined) || m.combined !== undefined)
    ) {
      headerRowIndex = i
      col = m
      break
    }
  }
  if (headerRowIndex === -1) {
    ElMessage.warning(
      '未找到表头行（需要「品名/材料」+「规格」「模数」，或「品名/材料」+「规格及模数」）'
    )
    return items
  }

  const cellOf = (row: any[], key: string, trim = true): string => {
    const idx = col[key]
    const value = idx === undefined ? '' : String(row[idx] ?? '')
    return trim ? value.trim() : value
  }
  const num = (s: string, dflt = 0): number => {
    const v = parseFloat(String(s).replace(/[^\d.]/g, ''))
    return Number.isNaN(v) ? dflt : v
  }
  // 规格纯零值（0/0.0/0.00）按空处理，避免落库后打印 0=1PCS。
  const normalizeSpec = (value: string): string => (/^0+(?:\.0+)?$/.test(value.trim()) ? '' : value)

  for (let i = headerRowIndex + 1; i < rows.length; i++) {
    const row = rows[i]
    if (!row || row.length === 0) continue

    const materialName = cellOf(row, 'name')
    if (!materialName) continue
    if (/合计|总计|小计/i.test(materialName)) continue

    // 分列模式：品名/规格/模数；单列模式：品名/规格及模数
    const combinedRaw = cellOf(row, 'combined')
    let spec = normalizeSpec(cellOf(row, 'spec'))
    let moduleRaw = cellOf(row, 'module')
    let split = cellOf(row, 'split')
    let baseRaw = cellOf(row, 'base')
    let fromCombined = false

    if (combinedRaw) {
      const parsed = parseSpecModule(combinedRaw)
      if (!parsed) {
        missingRows.push(i + 1)
        continue
      }
      spec = normalizeSpec(parsed.specification)
      moduleRaw = String(parsed.moduleQty)
      split = parsed.split
      baseRaw = String(parsed.baseQty)
      fromCombined = true
    }

    // 规格不必填（dev-20261022-020）；分列/单列都只要求模数可解析。
    if (!moduleRaw) {
      missingRows.push(i + 1)
      continue
    }

    const itemName = cellOf(row, 'item', false)
    const actualRaw = cellOf(row, 'actualIssue')
    const baseQty = num(baseRaw, 1) || 1
    const moduleQty = num(moduleRaw, 1) || 1

    const item: EngineeringBomItem = {
      itemId: undefined,
      bomId: undefined,
      parentMaterialId: null, // 平铺模式（2026-10-10 dev-20261010-017：取消“子物料”层级）
      materialId: 0,
      materialCode: '',
      materialName,
      processName: itemName, // 标准工序匹配由编辑器处理；未匹配名称原样保留。
      // 项目为空即公共物料；未匹配的项目名称保留并提示核对。
      isCommon: !String(itemName || '').trim(),
      specification: spec,
      split,
      unit: cellOf(row, 'unit') || 'PCS',
      quantity: calculateBomQuantity(baseQty, moduleQty) ?? 0,
      actualIssueQty: actualRaw ? num(actualRaw) : undefined,
      lossRate: num(cellOf(row, 'loss')) * 100,
      baseQty,
      moduleQty,
      minIssueQty: num(cellOf(row, 'minIssue')),
      // 单列「规格及模数」不拆宽长，整段留在 specification。
      widthMm: fromCombined ? 0 : num(cellOf(row, 'width')),
      lengthMm: fromCombined ? 0 : num(cellOf(row, 'length')),
      remark: cellOf(row, 'remark'),
      sortOrder: items.length + 1,
    }

    items.push(item)
  }

  if (missingRows.length) {
    ElMessage.warning(
      `有 ${missingRows.length} 行缺少模数或规格及模数无法解析，已跳过：第 ${missingRows.join('、')} 行`
    )
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
    productId: null,
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
  // 制造方案上下文随表单一起重置（换产品/新增绝不沿用上一产品选择）
  resetSchemeContext()
  setBaseline()
}

// 加载BOM数据
const loadBomData = async (bomId: number) => {
  try {
    const response = await productBomApi.getEngineeringBomInfo(bomId)
    Object.assign(formData, response.data)
    // 服务端内容指纹（dev-20261111-018）：保存时原样回传，客户端不计算版本号
    formData.expectedContentRevision = response?.data?.contentRevision ?? null
    // 主方案上下文：revision 用于乐观锁，choices 只作只读回显
    schemeRevision.value = parseSchemeRevision(response?.data?.schemeRevision)
    savedSchemeChoices.value = Array.isArray(response?.data?.schemeChoices)
      ? response.data.schemeChoices
      : []
    primaryChoices.value = {}
    // 加载BOM明细
    const itemResponse = await productBomApi.listEngineeringBomItem(bomId)
    formData.items = itemResponse.data || []
    // 明细回填到编辑器后再建基线，避免“刚打开就提示未保存”。
    await nextTick()
    setBaseline()
    // 已保存 BOM 打开即申请编辑权；失败/占用服务不可用时只读，但内容仍可查看与复制
    await editSession.acquire(bomId)
  } catch (error) {
    console.error('加载BOM数据失败:', error)
    ElMessage.error('加载BOM数据失败')
  }
}

/** 用户显式重新申请编辑权（不做后台接管）。 */
const handleReacquire = async () => {
  await editSession.reacquire()
}

/** 用户显式重新加载服务端最新内容（会替换当前草稿，因此只在用户点击时执行）。 */
const handleReloadLatest = async () => {
  if (!formData.bomId) return
  await loadBomData(Number(formData.bomId))
  if (!editSession.readOnly.value) ElMessage.success('已加载最新内容，可继续编辑')
}

/** 保存（供「确定」与「保存并离开」共用）；成功返回 true，失败/校验不过返回 false 且不清数据。 */
const doSave = async (): Promise<boolean> => {
  if (!bomFormRef.value || submitting.value) return false
  // 只读（占用失败/编辑权失效/占用服务不可用）一律不允许保存
  if (!editSession.ensureEditable()) return false
  submitting.value = true
  try {
    try {
      await bomFormRef.value.validate()
    } catch {
      return false
    }
    // 等待选料生成完成，并核对最新物料属性；失败时保留编辑内容供重试。
    if (
      bomItemEditorRef.value?.prepareSpecifications &&
      !(await bomItemEditorRef.value.prepareSpecifications())
    )
      return false
    // 保存前按当前模式校验（快速模式材料名称必填；常规模式必须选物料）
    if (!bomItemEditorRef.value?.validateItems()) return false
    // 直接读取编辑器，避免选料后立即保存遗漏防抖中的回填。
    formData.items = bomItemEditorRef.value.getItems()
    const longProject = formData.items.find((item) => (item.processName?.length || 0) > 200)
    if (longProject) {
      ElMessage.warning(`物料 "${longProject.materialName}" 的项目名称不能超过200个字符`)
      return false
    }
    // 基线对应本次实际提交的内容，不能把请求期间新增的修改误记为已保存。
    const savedSnapshot = fullFormSnapshot()
    const payload: any = { ...formData, items: formData.items }
    const schemeChoices = schemeChoicesPayload()
    const isEditingBom = formData.bomId !== undefined && formData.bomId !== null
    if (isEditingBom) {
      // 编辑：带服务端内容指纹 + 编辑占用 token；两者都由服务端给出，客户端不计算
      payload.expectedContentRevision = formData.expectedContentRevision ?? null
      payload.editToken = editSession.token.value
    } else {
      // 新建：还没有内容基线，也没有租约
      delete payload.expectedContentRevision
      delete payload.editToken
    }
    if (schemeChoices.length) {
      // 编辑已保存 BOM 用 getBomDetail 的 schemeRevision；新建 BOM 用 GET /product/{id} 的真实版本。
      // 两条路径都拿不到真实版本时必须阻止保存主方案，不能默认 0 或指望后端兜底。
      let revision: number | null = null
      if (isEditingBom) {
        revision =
          schemeRevision.value != null && Number.isInteger(Number(schemeRevision.value))
            ? Number(schemeRevision.value)
            : null
        if (revision == null) {
          ElMessage.warning('缺少产品主方案版本信息，请关闭后重新打开产品BOM再保存主方案')
          return false
        }
      } else {
        revision = await fetchProductSchemeRevision(Number(formData.productId))
        if (revision == null) {
          ElMessage.warning('未读取到产品主方案的当前版本，暂时不能保存主方案；请重新选择产品后再试')
          return false
        }
      }
      payload.schemeChoices = schemeChoices
      payload.schemeRevision = revision
    } else {
      delete payload.schemeChoices
      delete payload.schemeRevision
    }
    if (isEditingBom) {
      const response = await productBomApi.editEngineeringBom(payload)
      if (schemeChoices.length) {
        const revision = parseSchemeRevision(response?.data?.schemeRevision)
        if (revision != null) schemeRevision.value = revision
        savedSchemeChoices.value = mergeSavedChoices(savedSchemeChoices.value, schemeChoices)
      }
      // 保存成功：只更新内容指纹，不释放租约（保持到关闭/取消），也不改其他窗口状态
      const nextRevision = response?.data?.contentRevision
      if (nextRevision) formData.expectedContentRevision = nextRevision
      editSession.applyRevision(nextRevision)
      ElMessage.success('修改成功')
    } else {
      const response = await productBomApi.addEngineeringBom(payload)
      const bomId = Number(response?.data)
      if (Number.isSafeInteger(bomId) && bomId > 0) {
        formData.bomId = bomId
        // 新建成功后取得该 bomId 的编辑权，并回读服务端内容指纹供后续保存使用
        await editSession.acquire(bomId)
        try {
          const created = await productBomApi.getEngineeringBomInfo(bomId)
          formData.expectedContentRevision = created?.data?.contentRevision ?? null
        } catch (revisionError) {
          console.warn('回读新建BOM内容指纹失败:', revisionError)
        }
      }
      if (schemeChoices.length) {
        savedSchemeChoices.value = mergeSavedChoices(savedSchemeChoices.value, schemeChoices)
        // 新建保存后重新读一次真实版本，供同一会话后续保存主方案使用
        await fetchProductSchemeRevision(Number(formData.productId))
      }
      ElMessage.success('新增成功')
    }
    baseline.value = savedSnapshot
    recomputeDirty()
    emit('success')
    return true
  } catch (error: any) {
    console.error('保存BOM失败:', error)
    // 内容版本冲突（409 BOM_CONTENT_STALE）：保留草稿、立即只读，不忽略后继续提交
    if (isContentConflict(error)) {
      editSession.handleRequestError(error)
      ElMessage.error('BOM 已被其他人保存，当前修改未提交；草稿已保留，请重新加载最新内容或重新申请编辑权')
      return false
    }
    const leaseOutcome = editSession.handleRequestError(error)
    if (leaseOutcome === 'leaseLost' || leaseOutcome === 'unavailable') {
      ElMessage.error(editSession.message.value)
      return false
    }
    // HTTP 层用独立变量名，避免与企业状态魔法值规则混淆
    const httpCode = Number(error?.response?.status ?? error?.status ?? error?.code)
    const message = String(error?.response?.data?.msg || error?.message || '')
    if (httpCode === 409 || message.includes('CONCURRENT_MODIFICATION')) {
      // 并发冲突：保留当前草稿，重新取版本号后可再保存
      ElMessage.error('产品主方案已被其他会话修改，当前编辑内容已保留；请核对后重试')
      try {
        await fetchProductSchemeRevision(Number(formData.productId))
      } catch (refreshError) {
        console.error('并发冲突后刷新主方案版本失败:', refreshError)
      }
    } else {
      ElMessage.error('保存BOM失败')
    }
    return false
  } finally {
    submitting.value = false
  }
}

// 提交表单：保存成功后关闭，不重复触发未保存提示
const submitForm = async () => {
  const ok = await doSave()
  if (!ok) return
  recomputeDirty()
  if (isDirty.value) return
  suppressGuard.value = true
  visible.value = false
}

/** 未保存离开拦截：true=允许离开，false=继续编辑。 */
const resolveLeave = async (): Promise<boolean> => {
  if (suppressGuard.value) return true
  // 明细在编辑器内部防抖回传，关闭前重新取一次 getItems()，避免 300ms 内关闭漏判。
  recomputeDirty()
  if (!isDirty.value) return true
  try {
    await ElMessageBox.confirm('当前 BOM 有未保存的修改。', '未保存提醒', {
      distinguishCancelAndClose: true,
      confirmButtonText: '保存并离开',
      cancelButtonText: '放弃修改',
      type: 'warning',
      closeOnClickModal: false,
      closeOnPressEscape: false,
    })
    // 保存并离开：保存成功才离开；校验不过/接口失败留在弹窗。
    const saved = await doSave()
    recomputeDirty()
    return saved && !isDirty.value
  } catch (action) {
    // 放弃修改 → 离开；X / Esc（close）→ 继续编辑
    return action === 'cancel'
  }
}

/** 弹窗关闭前拦截（× / Esc / 取消共用） */
const handleBeforeClose = async (done: () => void) => {
  if (submitting.value) return
  if (await resolveLeave()) {
    suppressGuard.value = true
    done()
  }
}

// 取消按钮
const handleCancel = async () => {
  if (submitting.value) return
  if (await resolveLeave()) {
    suppressGuard.value = true
    visible.value = false
    resetForm()
  }
}

/** 浏览器刷新/关闭标签页原生提示（仅确有未保存修改时） */
const handleBeforeUnload = (e: BeforeUnloadEvent) => {
  if (props.modelValue) recomputeDirty()
  if (props.modelValue && isDirty.value) {
    e.preventDefault()
    e.returnValue = ''
  }
}
onMounted(() => window.addEventListener('beforeunload', handleBeforeUnload))
onBeforeUnmount(() => window.removeEventListener('beforeunload', handleBeforeUnload))

// 供父级路由离开保护使用
defineExpose({
  formIsDirty: () => {
    recomputeDirty()
    return isDirty.value
  },
  resolveLeave,
})

// 监听对话框打开
watch(
  () => props.modelValue,
  async (newVal) => {
    if (!newVal) {
      // 关闭/取消：compare-token 释放编辑权（保存成功不在此释放）
      await editSession.release()
      return
    }
    suppressGuard.value = false
    if (props.bomId) {
      await loadBomData(props.bomId)
    } else {
      resetForm()
      await nextTick()
      setBaseline()
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
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 8px;
  position: sticky;
  bottom: 0;
  z-index: 2;
  padding: 10px 0;
  background: var(--el-bg-color, #fff);
  text-align: right;
}

/* 编辑占用提示（dev-20261111-018）：只读时提示重申请/重载，不改变原有布局 */
.lease-alert {
  margin-bottom: 10px;
}

.lease-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
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
