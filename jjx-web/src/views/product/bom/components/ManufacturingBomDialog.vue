<template>
  <el-dialog
    v-model="visible"
    width="1280px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    :before-close="handleBeforeClose"
    class="manufacturing-bom-dialog"
  >
    <template #header>
      <div class="dialog-header">
        <span class="dialog-title">制造BOM</span>
        <span class="dialog-sub">{{ headerSub }}</span>
      </div>
    </template>

    <ManufacturingBomPath :nodes="pathNodes" @navigate="handlePathNavigate" />

    <el-alert
      v-if="!hasProductContext"
      class="context-alert"
      type="info"
      :closable="false"
      show-icon
      title="当前没有可靠的产品ID：可以先保存制造方案，但“设为主方案”需先选择并保存产品。"
    />

    <el-descriptions class="output-panel" :column="4" border size="small">
      <el-descriptions-item label="产出物料编码">{{
        current.material.materialCode || '—'
      }}</el-descriptions-item>
      <el-descriptions-item label="产出物料名称">{{
        current.material.materialName || '—'
      }}</el-descriptions-item>
      <el-descriptions-item label="档案规格">{{
        current.material.specification || '—'
      }}</el-descriptions-item>
      <el-descriptions-item label="单位">{{ current.material.unit || '—' }}</el-descriptions-item>
      <el-descriptions-item label="产出类型">MATERIAL（物料身份，不新建产品档案）</el-descriptions-item>
      <el-descriptions-item label="父产品">{{ productLabel }}</el-descriptions-item>
      <el-descriptions-item label="本层已采用">{{
        current.subBomId ? `方案 #${current.subBomId}` : '未采用'
      }}</el-descriptions-item>
      <el-descriptions-item label="本产品主方案">{{
        primaryBomId ? `方案 #${primaryBomId}` : '未设置'
      }}</el-descriptions-item>
    </el-descriptions>

    <div class="scheme-area">
      <div class="scheme-toolbar">
        <el-button v-if="canEdit" type="primary" plain size="small" @click="startNewScheme"
          >新建方案</el-button
        >
        <el-button size="small" :loading="loading" @click="reloadOptions">刷新候选</el-button>
        <span class="scheme-hint"
          >候选为真实已保存 BOM 版本与状态；保存与采用都不会自动审核、生效或改主表。</span
        >
      </div>
      <el-table
        :data="current.options"
        size="small"
        border
        row-key="bomId"
        empty-text="该产出物料尚无已保存的制造方案，可新建"
      >
        <el-table-column label="方案编码" prop="bomCode" min-width="150" show-overflow-tooltip />
        <el-table-column label="版本" prop="bomVersion" width="90" />
        <el-table-column label="状态" width="100">
          <template #default="scope">
            <el-tag size="small" :type="statusTag(scope.row.approveStatus)">{{
              statusLabel(scope.row.approveStatus)
            }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="投入行数" prop="itemCount" width="90" align="center" />
        <el-table-column label="主方案" width="90" align="center">
          <template #default="scope">
            <el-tag v-if="isPrimaryScheme(scope.row.bomId)" type="success" size="small"
              >主方案</el-tag
            >
            <span v-else>—</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="230">
          <template #default="scope">
            <el-tag
              v-if="current.selectedBomId === scope.row.bomId"
              type="info"
              size="small"
              style="margin-right: 6px"
              >当前查看</el-tag
            >
            <el-button
              v-else
              link
              type="primary"
              size="small"
              @click="selectSchemeByBomId(scope.row.bomId)"
              >编辑</el-button
            >
            <el-button
              link
              type="success"
              size="small"
              :disabled="!canEdit"
              @click="markPrimary(scope.row)"
              >设为主方案</el-button
            >
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-form class="draft-form" label-width="90px" size="small">
      <el-row :gutter="12">
        <el-col :span="8">
          <el-form-item label="方案编码">
            <el-input
              v-model="current.draft.bomCode"
              :disabled="!canEdit"
              maxlength="50"
              placeholder="必填"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="方案名称">
            <el-input
              v-model="current.draft.bomName"
              :disabled="!canEdit"
              maxlength="50"
              placeholder="必填"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="版本">
            <el-input
              v-model="current.draft.bomVersion"
              :disabled="!canEdit"
              maxlength="20"
              placeholder="留空由后端按历史版本递增"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="生效日期">
            <el-date-picker
              v-model="current.draft.effectiveDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="可空=无界"
              :disabled="!canEdit"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="失效日期">
            <el-date-picker
              v-model="current.draft.expiryDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="可空=无界"
              :disabled="!canEdit"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="备注">
            <el-input
              v-model="current.draft.remark"
              :disabled="!canEdit"
              maxlength="200"
              placeholder="选填"
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <ManufacturingBomItemTable
      ref="itemTableRef"
      v-model="current.draft.items"
      :readonly="!canEdit"
      @view-sub="drillDown"
    />

    <template #footer>
      <div class="dialog-footer">
        <span class="footer-tip">{{ footerTip }}</span>
        <el-button @click="handleCancel">关 闭</el-button>
        <el-button v-if="canEdit" type="primary" :loading="saving" @click="saveScheme"
          >保存制造BOM</el-button
        >
        <el-button v-if="canEdit" type="success" :disabled="!canAdopt" @click="adoptScheme"
          >采用此方案</el-button
        >
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 制造BOM 独立窗口（dev-20261111-005）。
 *
 * 边界：
 * 1）只保存独立制造方案（POST/PUT /engineering/bom/manufacturing），不自动发布、不改父 BOM、不生成库存/工单；
 * 2）“采用此方案”只生成采用结果并回填父 BOM 草稿行，父 BOM 保存才持久化关联；父取消不删除已保存方案；
 * 3）主方案按 product × material 独立，只由用户显式“设为主方案”触发，并随父保存提交；
 * 4）下层只看显式 sub_bom_id 引用，不按分组/规格推断；返回上层保留各层草稿；
 * 5）耗用/单位换算以后端为唯一权威，本窗口只展示参考用量。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import type { EngineeringBomItem, ManufacturingScheme } from '@/types/product/bom'
import { BomStatusEnum, ManufacturingOutputTypeEnum } from '@/enums/product'
import { hasPermi } from '@/directives'
import { productBomApi } from '@/api/product/bom'
import ManufacturingBomPath from './ManufacturingBomPath.vue'
import ManufacturingBomItemTable from './ManufacturingBomItemTable.vue'

/** 产出物料身份（来自父 BOM 行的物料，不从分组/规格猜）。 */
export interface ManufacturingMaterialContext {
  materialId: number
  materialCode?: string
  materialName?: string
  specification?: string
  unit?: string
}

interface ManufacturingDraft {
  bomId: number | null
  bomCode: string
  bomName: string
  bomVersion: string
  effectiveDate: string
  expiryDate: string
  remark: string
  items: EngineeringBomItem[]
}

interface Frame {
  key: string
  material: ManufacturingMaterialContext
  /** 该层已采用/已显式引用的方案 */
  subBomId: number | null
  options: ManufacturingScheme[]
  selectedBomId: number | null
  approveStatus: number | null
  draft: ManufacturingDraft
  baseline: string
}

interface Props {
  modelValue: boolean
  material?: ManufacturingMaterialContext
  productId?: number | null
  productCode?: string
  productName?: string
  /** 父 BOM 行当前已采用的方案 */
  subBomId?: number | null
  /** 本产品该物料已保存的主方案 */
  primaryBomId?: number | null
  readonly?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  material: () => ({ materialId: 0 }),
  productId: null,
  productCode: '',
  productName: '',
  subBomId: null,
  primaryBomId: null,
  readonly: false,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  /** 采用：只回填父 BOM 草稿，不落库、不设主方案 */
  (e: 'adopt', payload: { materialId: number; bomId: number; scheme: ManufacturingScheme | null }): void
  /** 用户显式设置主方案：随父 BOM 保存提交 */
  (e: 'primary-change', payload: { materialId: number; bomId: number }): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const loading = ref(false)
const saving = ref(false)
const itemTableRef = ref<{ validate?: () => string } | null>(null)
const primaryOverride = ref<{ materialId: number; bomId: number } | null>(null)

let frameSeq = 0
const nextFrameKey = () => `mfg-frame-${++frameSeq}`

const emptyDraft = (): ManufacturingDraft => ({
  bomId: null,
  bomCode: '',
  bomName: '',
  bomVersion: '',
  effectiveDate: '',
  expiryDate: '',
  remark: '',
  items: [],
})

const dateText = (value: unknown): string => {
  if (value == null || value === '') return ''
  const parsed = dayjs(value as string)
  return parsed.isValid() ? parsed.format('YYYY-MM-DD') : ''
}

const normalizeItem = (item: Partial<EngineeringBomItem>): EngineeringBomItem => ({
  ...item,
  materialId: Number(item.materialId) || 0,
  materialCode: item.materialCode ?? '',
  materialName: item.materialName ?? '',
  specification: item.specification ?? '',
  unit: item.unit ?? '',
  quantity: Number(item.quantity ?? 0) || 0,
  moduleQty: Number(item.moduleQty ?? 0) || 0,
  baseQty: Number(item.baseQty ?? 0) || 0,
  lossRate: Number(item.lossRate ?? 0) || 0,
  sortOrder: Number(item.sortOrder ?? item.itemOrder ?? 0) || 0,
  split: item.split ?? '',
  minIssueQty: Number(item.minIssueQty ?? 0) || 0,
  subBomId: item.subBomId ?? null,
  sourceType: item.sourceType ?? null,
})

/** 只比较用户可编辑内容；临时行号和派生快照不参与，避免误报未保存。 */
const serializeDraft = (draft: ManufacturingDraft): string =>
  JSON.stringify({
    bomId: draft.bomId ?? null,
    bomCode: draft.bomCode || '',
    bomName: draft.bomName || '',
    bomVersion: draft.bomVersion || '',
    effectiveDate: draft.effectiveDate || '',
    expiryDate: draft.expiryDate || '',
    remark: draft.remark || '',
    items: (draft.items || []).map((item) => ({
      materialId: Number(item.materialId) || 0,
      materialCode: item.materialCode || '',
      materialName: item.materialName || '',
      specification: item.specification || '',
      unit: item.unit || '',
      moduleQty: Number(item.moduleQty) || 0,
      baseQty: Number(item.baseQty) || 0,
      split: item.split || '',
      lossRate: Number(item.lossRate) || 0,
      minIssueQty: Number(item.minIssueQty) || 0,
      subBomId: item.subBomId ?? null,
      remark: item.remark || '',
    })),
  })

const createFrame = (material: ManufacturingMaterialContext, subBomId: number | null): Frame => {
  const draft = emptyDraft()
  return {
    key: nextFrameKey(),
    material: { ...material },
    subBomId: Number(subBomId) > 0 ? Number(subBomId) : null,
    options: [],
    selectedBomId: null,
    approveStatus: null,
    draft,
    baseline: serializeDraft(draft),
  }
}

const fallbackFrame = createFrame({ materialId: 0 }, null)
const stack = ref<Frame[]>([fallbackFrame])
const current = computed<Frame>(() => stack.value[stack.value.length - 1] ?? fallbackFrame)

const frameDirty = (frame: Frame): boolean => serializeDraft(frame.draft) !== frame.baseline
const anyDirty = (): boolean => stack.value.some((frame) => frameDirty(frame))
const currentDirty = computed(() => frameDirty(current.value))

const hasProductContext = computed(() => Number(props.productId) > 0)
/** 保存/采用/设主方案需 BOM 新增或编辑权限；只读用户仍可查看候选与投入明细。 */
const canEdit = computed(
  () => !props.readonly && hasPermi(['engineering:bom:add', 'engineering:bom:edit'])
)
const productLabel = computed(() => {
  if (!hasProductContext.value) return '未选择产品（未保存）'
  const code = String(props.productCode || '').trim()
  const name = String(props.productName || '').trim()
  if (code || name) return `${name}${code ? ` (${code})` : ''}`.trim()
  return `#${props.productId}`
})

const headerSub = computed(() => {
  const code = current.value.material.materialCode || ''
  const name = current.value.material.materialName || ''
  return [code, name].filter(Boolean).join(' ')
})

const isPrimaryScheme = (bomId: number): boolean => {
  const override = primaryOverride.value
  const materialId = Number(current.value.material.materialId)
  if (override && Number(override.materialId) === materialId) {
    return Number(override.bomId) === Number(bomId)
  }
  return Number(props.primaryBomId) > 0 && Number(props.primaryBomId) === Number(bomId)
}

const primaryBomId = computed(() => {
  const materialId = Number(current.value.material.materialId)
  const override = primaryOverride.value
  if (override && Number(override.materialId) === materialId) return Number(override.bomId)
  return Number(props.primaryBomId) > 0 ? Number(props.primaryBomId) : null
})

const pathNodes = computed(() =>
  stack.value.map((frame) => ({
    materialId: frame.material.materialId,
    bomId: frame.selectedBomId ?? frame.subBomId,
    materialCode: frame.material.materialCode,
    materialName: frame.material.materialName,
  }))
)

const canAdopt = computed(() => {
  const draft = current.value.draft
  return (
    canEdit.value &&
    Number(draft.bomId) > 0 &&
    Number(current.value.material.materialId) > 0 &&
    !currentDirty.value
  )
})

const footerTip = computed(() => {
  if (currentDirty.value) return '当前方案有未保存的修改'
  return '保存只落该制造方案；采用后还需在父 BOM 点保存才会持久化关联'
})

const statusLabel = (value: unknown): string => BomStatusEnum.getLabel(Number(value) || 0)
const statusTag = (value: unknown) =>
  BomStatusEnum.getTagProps(Number(value) || 0).type || 'info'

// ==================== 加载 ====================

const refreshOptions = async (frame: Frame): Promise<void> => {
  if (!Number(frame.material.materialId)) {
    frame.options = []
    return
  }
  const response = await productBomApi.manufacturingOptions({
    outputType: ManufacturingOutputTypeEnum.MATERIAL,
    outputId: Number(frame.material.materialId),
    productId: hasProductContext.value ? Number(props.productId) : undefined,
  })
  frame.options = response.data || []
}

const loadScheme = async (frame: Frame, bomId: number): Promise<boolean> => {
  const detail = await productBomApi.getEngineeringBomInfo(bomId)
  const items = await productBomApi.listEngineeringBomItem(bomId)
  const data = detail?.data || null
  frame.selectedBomId = bomId
  frame.approveStatus = data?.approveStatus ?? null
  frame.draft = {
    bomId,
    bomCode: data?.bomCode || frame.draft.bomCode || '',
    bomName: data?.bomName || frame.draft.bomName || '',
    bomVersion: data?.bomVersion || data?.version || '',
    effectiveDate: dateText(data?.effectiveDate),
    expiryDate: dateText(data?.expiryDate),
    remark: data?.remark || '',
    items: (items?.data || []).map(normalizeItem),
  }
  frame.baseline = serializeDraft(frame.draft)
  return true
}

const resetDraft = (frame: Frame): void => {
  const code = String(frame.material.materialCode || '').trim()
  const name = String(frame.material.materialName || code || '').trim()
  frame.selectedBomId = null
  frame.approveStatus = null
  frame.draft = {
    ...emptyDraft(),
    bomCode: code ? `${code}-MFG` : '',
    bomName: name ? `${name}制造方案` : '',
  }
  frame.baseline = serializeDraft(frame.draft)
}

const loadFrame = async (frame: Frame): Promise<void> => {
  loading.value = true
  try {
    await refreshOptions(frame)
    const preferred =
      frame.subBomId ??
      frame.options.find((option) => Number(option.bomId) > 0)?.bomId ??
      null
    if (preferred) await loadScheme(frame, preferred)
    else resetDraft(frame)
  } catch (error) {
    console.error('加载制造方案候选失败:', error)
    ElMessage.error('加载制造方案候选失败')
    resetDraft(frame)
    frame.baseline = serializeDraft(frame.draft)
  } finally {
    loading.value = false
  }
}

const resetStack = (): void => {
  stack.value = [createFrame(props.material || { materialId: 0 }, props.subBomId ?? null)]
  primaryOverride.value = null
}

// ==================== 未保存处理 ====================

/** 保存并继续 / 放弃修改 / 继续编辑；未保存时直接放行。 */
const resolveDirty = async (
  saveLabel: string,
  discardLabel: string
): Promise<'saved' | 'discard' | 'cancel'> => {
  if (!frameDirty(current.value)) return 'saved'
  try {
    await ElMessageBox.confirm('当前制造方案有未保存的修改。', '未保存提醒', {
      distinguishCancelAndClose: true,
      confirmButtonText: saveLabel,
      cancelButtonText: discardLabel,
      type: 'warning',
      closeOnClickModal: false,
      closeOnPressEscape: false,
    })
    const saved = await saveScheme()
    return saved ? 'saved' : 'cancel'
  } catch (action) {
    return action === 'cancel' ? 'discard' : 'cancel'
  }
}

const resolveLeave = async (): Promise<boolean> => {
  if (!anyDirty()) return true
  const ancestorDirty = stack.value
    .slice(0, -1)
    .some((frame) => frameDirty(frame))
  if (ancestorDirty) {
    try {
      await ElMessageBox.confirm(
        '上层制造方案还有未保存的修改，关闭会丢弃这些未保存内容；已保存的方案不受影响。',
        '未保存提醒',
        {
          distinguishCancelAndClose: true,
          confirmButtonText: '放弃修改',
          cancelButtonText: '继续编辑',
          type: 'warning',
          closeOnClickModal: false,
          closeOnPressEscape: false,
        }
      )
      return true
    } catch {
      return false
    }
  }
  const result = await resolveDirty('保存并关闭', '放弃修改')
  if (result === 'cancel') return false
  if (result === 'discard') return true
  return !anyDirty()
}

const handleBeforeClose = async (done: () => void): Promise<void> => {
  if (saving.value) return
  if (await resolveLeave()) done()
}

const handleCancel = async (): Promise<void> => {
  if (saving.value) return
  if (!(await resolveLeave())) return
  visible.value = false
}

// ==================== 交互 ====================

const selectSchemeByBomId = async (bomId: number): Promise<void> => {
  const frame = current.value
  if (Number(frame.selectedBomId) === Number(bomId)) return
  const resolved = await resolveDirty('保存并切换', '放弃修改')
  if (resolved === 'cancel') return
  try {
    loading.value = true
    await loadScheme(frame, Number(bomId))
  } catch (error) {
    console.error('加载制造方案失败:', error)
    ElMessage.error('加载制造方案失败')
  } finally {
    loading.value = false
  }
}

const startNewScheme = async (): Promise<void> => {
  if (!canEdit.value) return
  const resolved = await resolveDirty('保存并新建', '放弃修改')
  if (resolved === 'cancel') return
  resetDraft(current.value)
}

const reloadOptions = async (): Promise<void> => {
  try {
    loading.value = true
    await refreshOptions(current.value)
  } catch (error) {
    console.error('刷新制造方案候选失败:', error)
    ElMessage.error('刷新制造方案候选失败')
  } finally {
    loading.value = false
  }
}

const drillDown = async (row: EngineeringBomItem): Promise<void> => {
  const subBomId = Number(row?.subBomId)
  if (!subBomId) return
  // 进入下层不动上层草稿：上层未保存内容原样保留，只有保存/关闭时才提示
  stack.value.push(
    createFrame(
      {
        materialId: Number(row.materialId) || 0,
        materialCode: row.materialCode,
        materialName: row.materialName,
        specification: row.specification,
        unit: row.unit,
      },
      subBomId
    )
  )
  await loadFrame(current.value)
}

const handlePathNavigate = async (index: number): Promise<void> => {
  if (index < 0 || index >= stack.value.length - 1) return
  const resolved = await resolveDirty('保存并返回', '放弃修改')
  if (resolved === 'cancel') return
  stack.value = stack.value.slice(0, index + 1)
}

// ==================== 保存 / 采用 / 主方案 ====================

const buildPayload = () => {
  const frame = current.value
  return {
    bomId: frame.draft.bomId ?? undefined,
    bomCode: String(frame.draft.bomCode || '').trim(),
    bomName: String(frame.draft.bomName || '').trim(),
    bomVersion: String(frame.draft.bomVersion || '').trim() || undefined,
    outputType: ManufacturingOutputTypeEnum.MATERIAL,
    outputId: Number(frame.material.materialId),
    effectiveDate: frame.draft.effectiveDate || null,
    expiryDate: frame.draft.expiryDate || null,
    remark: frame.draft.remark || '',
    items: (frame.draft.items || []).map((item) => ({
      ...normalizeItem(item),
      children: undefined,
      isGroup: undefined,
    })),
  }
}

/** 前端只做必填/正数/精度提示；耗用、单位、环与批准资格一律以后端校验为准。 */
const validateDraft = (): string => {
  const frame = current.value
  if (!Number(frame.material.materialId)) return '缺少产出物料，无法保存制造方案'
  if (!String(frame.draft.bomCode || '').trim()) return '请填写方案编码'
  if (!String(frame.draft.bomName || '').trim()) return '请填写方案名称'
  if (!(frame.draft.items || []).length) return '制造BOM至少需要一行投入材料'
  const local = itemTableRef.value?.validate?.()
  if (local) return local
  return ''
}

const saveScheme = async (): Promise<boolean> => {
  if (!canEdit.value || saving.value) return false
  const frame = current.value
  const message = validateDraft()
  if (message) {
    ElMessage.warning(message)
    return false
  }
  saving.value = true
  try {
    const payload = buildPayload()
    const response = frame.draft.bomId
      ? await productBomApi.updateManufacturingBom(frame.draft.bomId, payload)
      : await productBomApi.saveManufacturingBom(payload)
    const detail = response?.data || null
    if (!detail || !Number(detail.bomId)) {
      ElMessage.error('保存返回缺少方案ID，请刷新后核对')
      return false
    }
    frame.draft.bomId = Number(detail.bomId)
    frame.selectedBomId = Number(detail.bomId)
    frame.approveStatus = detail.approveStatus ?? null
    if (Array.isArray(detail.items)) frame.draft.items = detail.items.map(normalizeItem)
    frame.baseline = serializeDraft(frame.draft)
    ElMessage.success('制造BOM方案已保存')
    try {
      await refreshOptions(frame)
    } catch (error) {
      console.error('刷新制造方案候选失败:', error)
    }
    return true
  } catch (error) {
    console.error('保存制造BOM方案失败:', error)
    ElMessage.error('保存制造BOM方案失败')
    return false
  } finally {
    saving.value = false
  }
}

const emitAdopt = (scheme: ManufacturingScheme | null): void => {
  const frame = current.value
  emit('adopt', {
    materialId: Number(frame.material.materialId),
    bomId: Number(frame.draft.bomId),
    scheme,
  })
}

const adoptScheme = async (): Promise<boolean> => {
  const frame = current.value
  if (!canEdit.value) return false
  if (!Number(frame.draft.bomId)) {
    ElMessage.warning('请先保存制造方案；未保存的方案不能被采用')
    return false
  }
  if (frameDirty(frame)) {
    ElMessage.warning('请先保存当前修改，再采用该方案')
    return false
  }
  try {
    const response = await productBomApi.adoptManufacturingBom(Number(frame.draft.bomId))
    frame.subBomId = Number(frame.draft.bomId)
    emitAdopt(response?.data ?? null)
    ElMessage.success('已采用该制造方案；保存产品BOM后才会持久化关联')
    return true
  } catch (error) {
    console.error('采用制造方案失败:', error)
    ElMessage.error('采用制造方案失败')
    return false
  }
}

const markPrimary = (scheme?: ManufacturingScheme): void => {
  if (!canEdit.value) return
  if (!hasProductContext.value) {
    ElMessage.warning('当前没有可靠的产品ID：请先选择并保存产品，再设置主方案')
    return
  }
  const materialId = Number(current.value.material.materialId)
  const bomId = Number(scheme?.bomId ?? current.value.draft.bomId)
  if (!materialId || !bomId) {
    ElMessage.warning('请先保存制造方案，再将其设为主方案')
    return
  }
  primaryOverride.value = { materialId, bomId }
  emit('primary-change', { materialId, bomId })
  ElMessage.success('已记为本产品该物料的主方案；保存产品BOM后生效')
}

// ==================== 生命周期 ====================

watch(
  () => props.modelValue,
  async (value) => {
    if (!value) return
    resetStack()
    await loadFrame(current.value)
  },
  { immediate: true }
)

defineExpose({
  stack,
  current,
  pathNodes,
  primaryBomId,
  canAdopt,
  anyDirty,
  currentDirty,
  validateDraft,
  saveScheme,
  adoptScheme,
  markPrimary,
  drillDown,
  handlePathNavigate,
  selectSchemeByBomId,
  startNewScheme,
  reloadOptions,
  resolveLeave,
  handleBeforeClose,
  handleCancel,
  serializeDraft,
  resetStack,
  loadFrame,
})
</script>

<style scoped>
.dialog-header {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.dialog-title {
  font-size: 16px;
  font-weight: 600;
}

.dialog-sub {
  font-size: 13px;
  color: var(--el-text-color-secondary, #909399);
}

.context-alert,
.output-panel,
.scheme-area,
.draft-form {
  margin-top: 10px;
}

.scheme-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.scheme-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}

.dialog-footer {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.footer-tip {
  margin-right: auto;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}
</style>
