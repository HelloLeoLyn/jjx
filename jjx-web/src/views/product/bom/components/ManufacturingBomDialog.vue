<template>
  <el-dialog
    v-model="visible"
    width="1360px"
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
      title="尚未选择产品：可以先保存制造方案；“设为主方案”需要先选择产品。"
    />

    <!-- 编辑占用（dev-20261111-018）：本层方案被其他窗口占用或编辑权失效时只读，草稿保留 -->
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
        </div>
      </template>
    </el-alert>

    <el-descriptions class="output-panel" :column="4" border size="small">
      <el-descriptions-item label="产出物料编码">{{ outputInfo.materialCode || '—' }}</el-descriptions-item>
      <el-descriptions-item label="产出物料名称">{{ outputInfo.materialName || '—' }}</el-descriptions-item>
      <el-descriptions-item label="档案规格">{{ outputInfo.specification || '—' }}</el-descriptions-item>
      <el-descriptions-item label="档案单位">{{ outputInfo.unit || '—' }}</el-descriptions-item>
      <el-descriptions-item label="所属产品">{{ productLabel }}</el-descriptions-item>
      <el-descriptions-item label="本层已选方案">{{ currentSchemeLabel }}</el-descriptions-item>
      <el-descriptions-item label="本产品主方案">{{ primarySchemeLabel }}</el-descriptions-item>
      <el-descriptions-item label="本层方案状态">{{ currentStatusLabel }}</el-descriptions-item>
    </el-descriptions>

    <div class="scheme-area">
      <div class="scheme-toolbar">
        <el-button v-if="canCreate" type="primary" plain size="small" @click="startNewScheme"
          >新建方案</el-button
        >
        <el-button size="small" :loading="loading" @click="reloadOptions">刷新候选</el-button>
        <span class="scheme-hint"
          >候选为已保存的制造方案；保存与采用都不会自动审核或生效。</span
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
        <el-table-column label="操作" width="250">
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
              >查看/编辑</el-button
            >
            <el-button
              link
              type="success"
              size="small"
              :disabled="!canSetPrimary"
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
              :disabled="!editable"
              maxlength="50"
              placeholder="必填"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="方案名称">
            <el-input
              v-model="current.draft.bomName"
              :disabled="!editable"
              maxlength="50"
              placeholder="必填"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="版本">
            <el-input
              v-model="current.draft.bomVersion"
              :disabled="!editable"
              maxlength="20"
              placeholder="留空则自动按已有版本续号"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="生效日期">
            <el-date-picker
              v-model="current.draft.effectiveDate"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="留空表示不限"
              :disabled="!editable"
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
              placeholder="留空表示不限"
              :disabled="!editable"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="8">
          <el-form-item label="备注">
            <el-input
              v-model="current.draft.remark"
              :disabled="!editable"
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
      :readonly="!editable"
      :scheme-labels="schemeLabels"
      @open-sub="drillDown"
    />

    <template #footer>
      <div class="dialog-footer">
        <span class="footer-tip">{{ footerTip }}</span>
        <el-button @click="handleCancel">关 闭</el-button>
        <el-button
          v-if="canCreate || canEditStored"
          type="primary"
          :loading="saving"
          :disabled="!editable"
          @click="saveScheme"
          >保存制造BOM</el-button
        >
        <el-button v-if="canAdoptVisible" type="success" :disabled="!canAdopt" @click="adoptScheme"
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
 * 2）采用：顶层把结果交给产品BOM草稿行；下层只回填直接上层投入行（subBomId/sourceType），
 *    由上层保存后才持久化，父/上层取消不删除已保存方案；
 * 3）主方案按 product × material 独立，只由用户显式“设为主方案”触发；深层设主先记录，
 *    等显式引用链逐层保存、回到根且图可达后才随产品BOM保存提交；
 * 4）下层只看显式 sub_bom_id 引用，不按分组/规格推断；已建档投入物料即使没有 sub_bom_id 也能进入
 *    该物料的候选并新建（只读用户不允许新建）；
 * 5）耗用/单位换算以后端为唯一权威：本窗口不计算用量、只展示保存后回读的参考值。
 */
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import type {
  EngineeringBomItem,
  ManufacturingScheme,
  ManufacturingSchemeLabel,
  ManufacturingBomSaveItem,
  ManufacturingBomSavePayload,
} from '@/types/product/bom'
import { BomStatusEnum, ManufacturingOutputTypeEnum } from '@/enums/product'
import { hasPermi } from '@/directives'
import { productBomApi } from '@/api/product/bom'
import { materialApi } from '@/api/inventory/material'
import ManufacturingBomPath from './ManufacturingBomPath.vue'
import ManufacturingBomItemTable from './ManufacturingBomItemTable.vue'
import { useBomEditSession, isContentConflict } from '../composables/useBomEditSession'

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
  /** 服务端内容指纹（dev-20261111-018）；保存时回传，客户端不计算。 */
  contentRevision: string | null
}

/** 父层投入行的稳定身份：itemId 优先，materialId 兜底（禁止回填到兄弟材料）。 */
interface ParentRowRef {
  frameKey: string
  itemId: number | null
  materialId: number
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
  /** 直接上层的投入行身份；顶层为 null */
  parent: ParentRowRef | null
  /** 物料档案（编码/名称/规格/单位），用于产出头部，不被父 BOM 行覆盖值冒充 */
  archive: ManufacturingMaterialContext | null
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
  /** 顶层采用：只回填产品 BOM 草稿，不落库、不设主方案 */
  (e: 'adopt', payload: { materialId: number; bomId: number; scheme: ManufacturingScheme | null }): void
  /** 用户显式设置主方案：随产品 BOM 保存提交 */
  (e: 'primary-change', payload: { materialId: number; bomId: number }): void
}>()

const visible = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit('update:modelValue', value),
})

const loading = ref(false)
const saving = ref(false)
const itemTableRef = ref<{ validate?: (items?: EngineeringBomItem[]) => string } | null>(null)
/** 当前产品上下文下的主方案覆盖（按 materialId 记录，跨层生效） */
const primaryOverride = ref<{ materialId: number; bomId: number } | null>(null)
/** 深层设主先挂起：只有回到根且显式引用链已保存可达才随产品BOM提交 */
const pendingPrimary = ref<Record<number, { materialId: number; bomId: number; frameKey: string }>>({})

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
  contentRevision: null,
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
  unitFactor: item.unitFactor == null || String(item.unitFactor) === '' ? null : Number(item.unitFactor),
  unitFactorBasis: item.unitFactorBasis ?? '',
  unitFactorSource: item.unitFactorSource ?? '',
  archiveUnit: item.archiveUnit ?? '',
  quantityStale: item.quantityStale === true,
})

/** 只比较用户可编辑内容；临时行号、档案回显与派生快照不参与，避免误报未保存。 */
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
      sourceType: item.sourceType || '',
      unitFactor: item.unitFactor == null ? null : Number(item.unitFactor),
      unitFactorBasis: item.unitFactorBasis || '',
      unitFactorSource: item.unitFactorSource || '',
      remark: item.remark || '',
    })),
  })

const createFrame = (
  material: ManufacturingMaterialContext,
  subBomId: number | null,
  parent: ParentRowRef | null = null
): Frame => {
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
    parent,
    archive: null,
  }
}

const fallbackFrame = createFrame({ materialId: 0 }, null)
const stack = ref<Frame[]>([fallbackFrame])
/**
 * 当前帧用 activeIndex 指针而不是直接截断数组：上层返回后下层帧仍保留在栈里，
 * 这样“深层设主”等待显式引用链可达时才提交时，链上各层仍可核对。
 */
const activeIndex = ref(0)
const current = computed<Frame>(() => stack.value[activeIndex.value] ?? fallbackFrame)

// 编辑占用（dev-20261111-018）：只占当前打开的这一层 bomId；父/子方案各有 bomId 时分别占用
const editSession = useBomEditSession(() => current.value?.draft.bomId ?? null)
// 切换当前层（钻取/返回/换方案/新建）时释放上一层、申请当前层，避免长期占着不再编辑的方案
watch(
  () => current.value?.draft.bomId ?? null,
  async (bomId, previous) => {
    if (bomId === previous) return
    // 保存流程已为同一 bomId 取得租约时不再“释放再取”，避免刚拿到的 token 被换掉
    if (bomId && bomId === editSession.boundBomId.value) return
    await editSession.release()
    if (props.modelValue && bomId && bomId === current.value?.draft.bomId) {
      await editSession.acquire(bomId)
    }
  }
)

const frameDirty = (frame: Frame): boolean => serializeDraft(frame.draft) !== frame.baseline
const anyDirty = (): boolean => stack.value.some((frame) => frameDirty(frame))
const currentDirty = computed(() => frameDirty(current.value))

const hasProductContext = computed(() => Number(props.productId) > 0)
/**
 * 权限矩阵（沿用既有 BOM 权限枚举）：
 * - canCreate：有新增权限且非只读，可新建制造方案并采用；
 * - canEditStored：有编辑权限且非只读，可编辑已存方案、设主方案；
 * - 采用 = 新增或编辑任一；只读=仅查看。
 */
const canCreate = computed(() => !props.readonly && hasPermi('engineering:bom:add'))
const canEditStored = computed(() => !props.readonly && hasPermi('engineering:bom:edit'))
const canSetPrimary = computed(() => canEditStored.value)
/** 新建/已存方案分开判断：add-only 只能新建，edit-only 只能改已存方案。 */
const editable = computed(
  () =>
    !editSession.readOnly.value &&
    (Number(current.value.draft.bomId) ? canEditStored.value : canCreate.value)
)
const canAdoptVisible = computed(() => canCreate.value || canEditStored.value)

const productLabel = computed(() => {
  if (!hasProductContext.value) return '未选择产品'
  const code = String(props.productCode || '').trim()
  const name = String(props.productName || '').trim()
  if (code || name) return `${name}${code ? ` (${code})` : ''}`.trim()
  return ''
})

/** 产出头部以物料档案为准，不拿父 BOM 行覆盖后的单位/规格冒充档案值。 */
const outputInfo = computed<ManufacturingMaterialContext>(() => {
  const archive = current.value.archive
  const material = current.value.material
  return {
    materialId: material.materialId,
    materialCode: archive?.materialCode || material.materialCode || '',
    materialName: archive?.materialName || material.materialName || '',
    // 规格/单位只认物料档案：父 BOM 行被覆盖过的值不能冒充档案单位
    specification: archive?.specification || '',
    unit: archive?.unit || '',
  }
})

const headerSub = computed(() => {
  const code = outputInfo.value.materialCode || ''
  const name = outputInfo.value.materialName || ''
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

/** 方案显示优先用“编码+版本”，不把数据库自增ID当主标题。 */
const schemeLabels = computed<Record<number, ManufacturingSchemeLabel>>(() => {
  const labels: Record<number, ManufacturingSchemeLabel> = {}
  stack.value.forEach((frame) => {
    frame.options.forEach((option) => {
      if (!Number(option.bomId)) return
      labels[Number(option.bomId)] = {
        bomCode: option.bomCode,
        bomVersion: option.bomVersion,
      }
    })
    if (Number(frame.draft.bomId) > 0) {
      labels[Number(frame.draft.bomId)] = {
        bomCode: frame.draft.bomCode,
        bomVersion: frame.draft.bomVersion,
      }
    }
  })
  return labels
})

const schemeText = (bomId?: number | null): string => {
  const id = Number(bomId)
  if (!id) return '未选择'
  const label = schemeLabels.value[id]
  const code = String(label?.bomCode || '').trim()
  const version = String(label?.bomVersion || '').trim()
  const text = [code, version].filter(Boolean).join(' ')
  return text || '已选方案'
}

const currentSchemeLabel = computed(() =>
  schemeText(current.value.selectedBomId ?? current.value.subBomId ?? current.value.draft.bomId)
)

const primarySchemeLabel = computed(() => schemeText(primaryBomId.value))

const currentStatusLabel = computed(() => {
  const status = current.value.approveStatus
  return status == null ? '—' : statusLabel(status)
})

const pathNodes = computed(() =>
  stack.value.slice(0, activeIndex.value + 1).map((frame) => ({
    materialId: frame.material.materialId,
    bomId: frame.selectedBomId ?? frame.subBomId,
    materialCode: frame.archive?.materialCode || frame.material.materialCode,
    materialName: frame.archive?.materialName || frame.material.materialName,
  }))
)

const canAdopt = computed(() => {
  const draft = current.value.draft
  return (
    canAdoptVisible.value &&
    Number(draft.bomId) > 0 &&
    Number(current.value.material.materialId) > 0 &&
    !currentDirty.value
  )
})

const footerTip = computed(() => {
  if (currentDirty.value) return '当前方案有未保存的修改'
  if (activeIndex.value > 0) {
    return '采用只回填直接上层投入行；上层保存后才持久化关联，产品BOM最后保存'
  }
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
    contentRevision: data?.contentRevision ?? null,
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

/**
 * 读取物料档案（编码/名称/规格/单位）。产出头部用档案单位，投入行区分档案单位与 BOM 投入单位。
 * 失败不影响编辑，退回父 BOM 行已带的编码/名称（这里不订正物料档案）。
 */
const loadArchive = async (frame: Frame): Promise<void> => {
  const ids = new Set<number>()
  if (Number(frame.material.materialId)) ids.add(Number(frame.material.materialId))
  ;(frame.draft.items || []).forEach((item) => {
    if (Number(item.materialId)) ids.add(Number(item.materialId))
  })
  if (!ids.size) return
  await Promise.all(
    [...ids].map(async (id) => {
      try {
        const response = await materialApi.getInfo(String(id))
        const data: any = response?.data || null
        if (!data) return
        if (Number(frame.material.materialId) === id) {
          frame.archive = {
            materialId: id,
            materialCode: data.materialCode || '',
            materialName: data.materialName || '',
            specification: data.specification || '',
            unit: data.unit || '',
          }
        }
        ;(frame.draft.items || []).forEach((item) => {
          if (Number(item.materialId) !== id) return
          item.archiveUnit = data.unit || item.archiveUnit || ''
        })
      } catch (error) {
        console.error('读取物料档案失败:', error)
      }
    })
  )
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
  // 档案读取放在目录就绪之后，避免覆盖草稿；自身失败不改脚本状态
  void loadArchive(frame)
}

const resetStack = (): void => {
  stack.value = [createFrame(props.material || { materialId: 0 }, props.subBomId ?? null)]
  activeIndex.value = 0
  primaryOverride.value = null
  pendingPrimary.value = {}
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
    .slice(0, activeIndex.value)
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
    void loadArchive(frame)
  } catch (error) {
    console.error('加载制造方案失败:', error)
    ElMessage.error('加载制造方案失败')
  } finally {
    loading.value = false
  }
}

const startNewScheme = async (): Promise<void> => {
  if (!canCreate.value) return
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

/**
 * 进入下层。已选方案=查看下层方案；未选方案但已建档的投入物料=进入该物料候选并新建。
 * 帧保存直接上层投入行的稳定身份（itemId+materialId），采用时只回填那一行。
 * 只读用户不允许在此新建层级。
 */
const drillDown = async (row: EngineeringBomItem): Promise<void> => {
  if (!row) return
  const materialId = Number(row.materialId)
  const subBomId = Number(row.subBomId) > 0 ? Number(row.subBomId) : null
  if (!materialId) return
  // 只读 / 无新增无编辑权限：没有显式下层时不允许在这里新建层级
  if (!subBomId && !canCreate.value && !canEditStored.value) return
  const parentFrame = current.value
  const frame = createFrame(
    {
      materialId,
      materialCode: row.materialCode,
      materialName: row.materialName,
      specification: row.materialSpecification || row.specification,
      unit: row.unit,
    },
    subBomId,
    {
      frameKey: parentFrame.key,
      itemId: row.itemId == null ? null : Number(row.itemId),
      materialId,
    }
  )
  // 从上层重新下钻另一分支时，丢弃已不在路径上的旧下层帧及其待提交主方案
  stack.value = [...stack.value.slice(0, activeIndex.value + 1), frame]
  activeIndex.value = stack.value.length - 1
  prunePendingPrimary()
  // 用响应式代理继续加载，保证表格/表单随草稿更新
  await loadFrame(current.value)
}

const prunePendingPrimary = (): void => {
  const keys = new Set(stack.value.map((frame) => frame.key))
  const next: Record<number, { materialId: number; bomId: number; frameKey: string }> = {}
  Object.entries(pendingPrimary.value).forEach(([materialId, entry]) => {
    if (keys.has(entry.frameKey)) next[Number(materialId)] = entry
  })
  pendingPrimary.value = next
}

const findParentRow = (frame: Frame, parent: ParentRowRef): EngineeringBomItem | null => {
  const items = frame.draft.items || []
  if (parent.itemId != null) {
    const byId = items.find((item) => Number(item.itemId) === Number(parent.itemId))
    if (byId) {
      if (Number(byId.materialId) !== Number(parent.materialId)) return null
      return byId
    }
  }
  return items.find((item) => Number(item.materialId) === Number(parent.materialId)) || null
}

/** 显式引用链是否已经逐层保存且被上层行引用（决定深层主方案能否随产品BOM保存）。 */
const chainReachable = (frameKey: string): boolean => {
  const index = stack.value.findIndex((frame) => frame.key === frameKey)
  if (index <= 0) return false
  for (let i = index; i >= 1; i--) {
    const child = stack.value[i]
    const parent = stack.value[i - 1]
    if (!Number(child.draft.bomId) || frameDirty(child)) return false
    if (!Number(parent.draft.bomId) || frameDirty(parent)) return false
    const row = findParentRow(parent, child.parent as ParentRowRef)
    if (!row || Number(row.subBomId) !== Number(child.draft.bomId)) return false
  }
  return true
}

const flushPendingPrimary = (): void => {
  Object.entries(pendingPrimary.value).forEach(([materialId, entry]) => {
    if (!chainReachable(entry.frameKey)) return
    emit('primary-change', { materialId: entry.materialId, bomId: entry.bomId })
    const rest = { ...pendingPrimary.value }
    delete rest[Number(materialId)]
    pendingPrimary.value = rest
  })
}

const handlePathNavigate = async (index: number): Promise<void> => {
  if (index < 0 || index >= activeIndex.value) return
  const resolved = await resolveDirty('保存并返回', '放弃修改')
  if (resolved === 'cancel') return
  activeIndex.value = index
  prunePendingPrimary()
  flushPendingPrimary()
}

// ==================== 保存 / 采用 / 主方案 ====================

/** 提交行：去掉前端展示标记；未保存的比例改动不把旧参考用量当新值提交。 */
const toPayloadItem = (item: EngineeringBomItem): ManufacturingBomSaveItem => {
  const normalized = normalizeItem(item)
  const loss = Number(normalized.lossRate ?? 0)
  return {
    ...normalized,
    children: undefined,
    isGroup: undefined,
    archiveUnit: undefined,
    quantityStale: undefined,
    quantity: normalized.quantityStale ? undefined : normalized.quantity,
    lossRate: Number.isFinite(loss) ? loss : 0,
    unitFactor: normalized.unitFactor == null ? null : Number(normalized.unitFactor),
    unitFactorBasis: normalized.unitFactorBasis || null,
    unitFactorSource: normalized.unitFactorSource || null,
  }
}

const buildPayload = () => {
  const frame = current.value
  const payload: ManufacturingBomSavePayload = {
    bomId: frame.draft.bomId ?? undefined,
    bomCode: String(frame.draft.bomCode || '').trim(),
    bomName: String(frame.draft.bomName || '').trim(),
    bomVersion: String(frame.draft.bomVersion || '').trim() || undefined,
    outputType: ManufacturingOutputTypeEnum.MATERIAL,
    outputId: Number(frame.material.materialId),
    effectiveDate: frame.draft.effectiveDate || null,
    expiryDate: frame.draft.expiryDate || null,
    remark: frame.draft.remark || '',
    items: (frame.draft.items || []).map(toPayloadItem),
  }
  if (frame.draft.bomId) {
    // 编辑已保存方案：带服务端内容指纹 + 本层编辑占用 token（与父 BOM 同一套后端校验）
    payload.expectedContentRevision = frame.draft.contentRevision ?? null
    payload.editToken = editSession.token.value
  }
  return payload
}

/** 前端只做必填/正数/精度/换算依据提示；耗用、单位、环与批准资格一律以后端校验为准。 */
const validateDraft = (): string => {
  const frame = current.value
  if (!Number(frame.material.materialId)) return '缺少产出物料，无法保存制造方案'
  if (!String(frame.draft.bomCode || '').trim()) return '请填写方案编码'
  if (!String(frame.draft.bomName || '').trim()) return '请填写方案名称'
  if (!(frame.draft.items || []).length) return '制造BOM至少需要一行投入材料'
  const local = itemTableRef.value?.validate?.(frame.draft.items)
  if (local) return local
  return ''
}

const saveScheme = async (): Promise<boolean> => {
  if (!editable.value || saving.value) return false
  // 只读（占用失败/编辑权失效）不得保存
  if (!editSession.ensureEditable()) return false
  const frame = current.value
  const isNewScheme = !Number(frame.draft.bomId)
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
    // 保存成功：更新内容指纹并保持编辑权（不释放租约，也不后台接管）
    frame.draft.contentRevision = detail.contentRevision ?? frame.draft.contentRevision ?? null
    editSession.applyRevision(detail.contentRevision)
    // 新建方案首次保存后必须立刻申请该 bomId 的租约；否则第二次保存会缺 token 被后端 409。
    // 申请失败（被其他窗口占用/占用服务不可用）→ 只读并保留草稿，不静默放行。
    if (isNewScheme) {
      const granted = await editSession.acquire(Number(detail.bomId))
      if (!granted) {
        ElMessage.warning(
          '方案已保存，但未取得该方案的编辑占用：当前为只读，草稿已保留；请重新申请编辑权后再继续编辑'
        )
      }
    }
    if (Array.isArray(detail.items)) {
      // 保存后回读：参考用量以本次返回的值为准（后端重算）
      const archiveUnits = new Map(
        (frame.draft.items || []).map((item) => [Number(item.materialId), item.archiveUnit])
      )
      frame.draft.items = detail.items.map((item) => {
        const next = normalizeItem(item)
        next.archiveUnit = archiveUnits.get(Number(next.materialId)) || next.archiveUnit || ''
        return next
      })
    }
    frame.baseline = serializeDraft(frame.draft)
    if (editSession.readOnly.value) {
      ElMessage.success('制造BOM方案已保存（当前只读）')
    } else {
      ElMessage.success('制造BOM方案已保存')
    }
    try {
      await refreshOptions(frame)
    } catch (error) {
      console.error('刷新制造方案候选失败:', error)
    }
    flushPendingPrimary()
    return true
  } catch (error) {
    console.error('保存制造BOM方案失败:', error)
    if (isContentConflict(error)) {
      editSession.handleRequestError(error)
      ElMessage.error('制造方案已被其他人保存，当前修改未提交；草稿已保留，请重新申请编辑权或重新加载后再试')
      return false
    }
    const leaseOutcome = editSession.handleRequestError(error)
    if (leaseOutcome === 'leaseLost' || leaseOutcome === 'unavailable') {
      ElMessage.error(editSession.message.value)
      return false
    }
    ElMessage.error('保存制造BOM方案失败')
    return false
  } finally {
    saving.value = false
  }
}

/** 用户显式重新申请本层编辑权（不后台接管）。 */
const handleReacquire = async () => {
  await editSession.reacquire()
}

/**
 * 采用方案：顶层交给产品BOM草稿行；下层只回填直接上层投入行的 subBomId/sourceType，
 * 让父草稿变 dirty，返回上层保存后才持久化。不改兄弟材料、不删除独立方案。
 */
const applyAdoptToParent = (frame: Frame, bomId: number): boolean => {
  const parent = frame.parent
  if (!parent) return false
  const parentFrame = stack.value.find((item) => item.key === parent.frameKey)
  if (!parentFrame) return false
  const row = findParentRow(parentFrame, parent)
  if (!row) {
    ElMessage.warning('上层投入行已变化，未回填；请返回上层重新确认该行物料')
    return false
  }
  row.subBomId = bomId
  row.sourceType = 'make'
  return true
}

const adoptScheme = async (): Promise<boolean> => {
  const frame = current.value
  if (!canAdoptVisible.value) return false
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
    const isRoot = activeIndex.value === 0
    if (isRoot) {
      emit('adopt', {
        materialId: Number(frame.material.materialId),
        bomId: Number(frame.draft.bomId),
        scheme: response?.data ?? null,
      })
      ElMessage.success('已采用该制造方案；保存产品BOM后才会持久化关联')
    } else if (applyAdoptToParent(frame, Number(frame.draft.bomId))) {
      ElMessage.success('已回填上层投入行；返回上层保存后才持久化关联')
    }
    flushPendingPrimary()
    return true
  } catch (error) {
    console.error('采用制造方案失败:', error)
    ElMessage.error('采用制造方案失败')
    return false
  }
}

/**
 * 设为主方案：顶层显式调整直接交给产品BOM；深层先记录（保持原始产品上下文），
 * 待显式引用链逐层保存、回到根且图可达后才随产品BOM保存提交。
 */
const markPrimary = (scheme?: ManufacturingScheme): void => {
  if (!canSetPrimary.value) {
    ElMessage.warning('当前用户没有编辑产品BOM的权限，不能设置主方案')
    return
  }
  if (!hasProductContext.value) {
    ElMessage.warning('尚未选择产品：请先选择产品，再设置主方案')
    return
  }
  const frame = current.value
  const materialId = Number(frame.material.materialId)
  const bomId = Number(scheme?.bomId ?? frame.draft.bomId)
  if (!materialId || !bomId) {
    ElMessage.warning('请先保存制造方案，再将其设为主方案')
    return
  }
  primaryOverride.value = { materialId, bomId }
  if (activeIndex.value === 0) {
    emit('primary-change', { materialId, bomId })
    ElMessage.success('已记为本产品该物料的主方案；保存产品BOM后生效')
    return
  }
  pendingPrimary.value = {
    ...pendingPrimary.value,
    [materialId]: { materialId, bomId, frameKey: frame.key },
  }
  ElMessage.success('已记录该物料的主方案；返回顶层并保存产品BOM后生效')
  flushPendingPrimary()
}

// ==================== 生命周期 ====================

watch(
  () => props.modelValue,
  async (value) => {
    if (!value) {
      // 关闭：compare-token 释放本层编辑权（保存成功不在此释放）
      await editSession.release()
      return
    }
    resetStack()
    await loadFrame(current.value)
  },
  { immediate: true }
)

defineExpose({
  stack,
  editSession,
  activeIndex,
  current,
  pathNodes,
  primaryBomId,
  pendingPrimary,
  canCreate,
  canEditStored,
  canSetPrimary,
  editable,
  canAdopt,
  canAdoptVisible,
  anyDirty,
  currentDirty,
  currentSchemeLabel,
  primarySchemeLabel,
  outputInfo,
  schemeLabels,
  validateDraft,
  saveScheme,
  adoptScheme,
  markPrimary,
  drillDown,
  handlePathNavigate,
  chainReachable,
  selectSchemeByBomId,
  startNewScheme,
  reloadOptions,
  resolveLeave,
  handleBeforeClose,
  handleCancel,
  serializeDraft,
  buildPayload,
  resetStack,
  loadFrame,
  flushPendingPrimary,
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
.lease-alert,
.output-panel,
.scheme-area,
.draft-form {
  margin-top: 10px;
}

.lease-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 6px;
  font-size: 12px;
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
