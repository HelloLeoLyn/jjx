<template>
  <el-dialog :title="title" v-model="visible" width="820px" append-to-body>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="物料编码" prop="materialCode">
            <el-input
              v-model="form.materialCode"
              placeholder="系统自动生成"
              :readonly="!!form.materialId"
            >
              <template #append>
                <el-button :icon="Search" @click="handleGenerateCode()" />
              </template>
            </el-input>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="品类" prop="categoryId">
            <el-select
              v-model="form.categoryId"
              placeholder="请选择品类"
              filterable
              clearable
              style="width: 100%"
              @change="handleCategoryChange"
            >
              <el-option
                v-for="item in categoryOptions"
                :key="item.categoryId"
                :label="item.categoryName"
                :value="item.categoryId"
              />
            </el-select>
            <div v-if="categoryHint" class="field-hint">{{ categoryHint }}</div>
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="大类">
            <el-input :model-value="materialTypeLabel" readonly placeholder="由品类带出" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="机种" prop="materialNameEn">
            <el-input v-model="form.materialNameEn" placeholder="如：JST263" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">主数据</el-divider>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="材料" prop="materialGrade" :required="isRequired('materialGrade')" :error="fieldErrors.materialGrade">
            <el-select
              v-model="form.materialGrade"
              placeholder="请选择材料"
              filterable
              clearable
              style="width: 100%"
              @change="clearFieldError('materialGrade')"
            >
              <el-option v-for="item in MATERIAL_GRADE_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="型号" prop="model" :required="isRequired('model')" :error="fieldErrors.model">
            <el-input
              v-model="form.model"
              placeholder="如：PET125、3M5604、M1.6*3"
              @input="clearFieldError('model')"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="颜色" prop="color" :required="isRequired('color')" :error="fieldErrors.color">
            <el-select
              v-model="form.color"
              placeholder="请选择颜色"
              filterable
              clearable
              style="width: 100%"
              @change="clearFieldError('color')"
            >
              <el-option v-for="item in MATERIAL_COLOR_OPTIONS" :key="item" :label="item" :value="item" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="品牌" prop="brand" :required="isRequired('brand')" :error="fieldErrors.brand">
            <el-input
              v-model="form.brand"
              placeholder="选填，如：3M"
              @input="clearFieldError('brand')"
            />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="别名">
        <el-input v-model="form.searchKeywords" placeholder="选填：常用叫法/旧料号，便于搜索" />
      </el-form-item>

      <el-form-item label="物料名称">
        <el-input :model-value="displayName" readonly placeholder="由 材料+型号+颜色（品牌） 自动生成" />
        <div class="field-hint">只读：材料 + 型号 + 颜色（品牌），无空格，缺项自动跳过</div>
      </el-form-item>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="尺寸" prop="size" :error="fieldErrors.size">
            <el-input
              v-model="form.size"
              clearable
              placeholder="如 300 / 300×305 / 300×1.0 / 0.3m×1.0，缺省 mm"
              @input="clearFieldError('size')"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="单位" prop="unit">
            <el-input v-model="form.unit" placeholder="如：PCS、KG" />
          </el-form-item>
        </el-col>
      </el-row>

      <template v-if="specAttrMode">
        <el-divider content-position="left">规格（按品类属性填写）</el-divider>
        <MaterialSpecFields
          v-model="specInputs"
          v-model:supplement="specSupplement"
          :defs="specDefs"
          :preview-fallback="legacySpecText"
        />
        <el-alert
          v-if="specLegacyHint"
          :title="specLegacyHint"
          type="info"
          :closable="false"
          show-icon
          class="spec-legacy-hint"
        />
      </template>
      <el-form-item
        v-else
        label="规格"
        prop="specification"
        :required="isRequired('specification')"
        :error="fieldErrors.specification"
      >
        <el-input
          v-model="form.specification"
          type="textarea"
          :rows="2"
          placeholder="工艺描述：中砂面 / 雾面 / 离型力 / 克重等"
          @input="clearFieldError('specification')"
        />
      </el-form-item>

      <el-form-item label="物料标签">
        <el-select
          v-model="form.tagIds"
          multiple
          filterable
          clearable
          collapse-tags
          placeholder="请选择物料标签"
          style="width: 100%"
        >
          <el-option
            v-for="tag in tagOptions"
            :key="tag.tagId"
            :label="tag.tagName"
            :value="tag.tagId"
          />
        </el-select>
      </el-form-item>

      <el-divider content-position="left">库存参数</el-divider>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="安全库存" prop="safeStock">
            <el-input-number v-model="form.safeStock" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="最高库存" prop="maxStock">
            <el-input-number v-model="form.maxStock" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="再订货点" prop="reorderPoint">
            <el-input-number v-model="form.reorderPoint" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="批次管理" prop="batchControl">
            <el-switch v-model="form.batchControl" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-divider content-position="left">采购信息</el-divider>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="标准单价" prop="standardPrice">
            <el-input-number
              v-model="form.standardPrice"
              :min="0"
              :precision="4"
              style="width: 100%"
            />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="采购周期(天)" prop="leadTime">
            <el-input-number v-model="form.leadTime" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="主要供应商" prop="supplierName">
        <el-input v-model="form.supplierName" placeholder="请输入供应商名称" />
      </el-form-item>

      <el-divider content-position="left">保质期管理</el-divider>

      <el-row :gutter="20">
        <el-col :span="12">
          <el-form-item label="保质期(天)" prop="shelfLife">
            <el-input-number v-model="form.shelfLife" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="预警提前天数" prop="expiryAlertDays">
            <el-input-number v-model="form.expiryAlertDays" :min="0" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="备注" prop="remark">
        <el-input v-model="form.remark" type="textarea" :rows="2" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submitForm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { materialApi } from '@/api/inventory/material'
import { MaterialTypeEnum } from '@/enums/inventory/MaterialEnum'
import {
  MATERIAL_DEFAULT_EXPIRY_ALERT_DAYS,
  MATERIAL_DEFAULT_TYPE,
  MATERIAL_DEFAULT_UNIT,
} from './materialCreateDefaults'
import {
  MATERIAL_COLOR_OPTIONS,
  MATERIAL_FIELD_LABELS,
  MATERIAL_GRADE_OPTIONS,
  composeMaterialName,
  composeSpecification,
  deriveMaterialType,
  findMissingRequiredFields,
  findMissingSpecAttrs,
  guessSpecValues,
  loadMaterialCategoryOptions,
  requiredMaterialFields,
  specValueToInput,
  splitLegacyMaterialName,
  type MaterialCategoryOption,
  type MaterialSpecInputValue,
} from './materialMasterFields'
import MaterialSpecFields from './MaterialSpecFields.vue'
import type {
  MaterialSaveDTO,
  MaterialUpdateDTO,
  InventoryMaterial,
  MaterialSpecAttrDef,
} from '@/types/inventory/material'

interface Props {
  modelValue: boolean
  materialId?: number | null
  /** 预填数据（用于从BOM编辑器快速建档时传入物料名称、规格等） */
  presetData?: {
    materialName?: string
    specification?: string
    unit?: string
    supplierName?: string
    materialType?: string
  }
}

interface Emits {
  (e: 'update:modelValue', value: boolean): void
  (e: 'success', material: InventoryMaterial): void
}

interface MasterSnapshot {
  categoryId: number | null
  materialGrade: string
  model: string
  color: string
  brand: string
  specification: string
  size: string
  searchKeywords: string
  materialName: string
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  materialId: null,
  presetData: undefined,
})

const emit = defineEmits<Emits>()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const title = computed(() => (props.materialId ? '编辑物料' : '新增物料'))

const submitting = ref(false)
const formRef = ref()
const tagOptions = ref<Array<{ tagId: number; tagName: string }>>([])
const categoryOptions = ref<MaterialCategoryOption[]>([])
const original = ref<MasterSnapshot | null>(null)
const presetName = ref('')
const fieldErrors = reactive<Record<string, string>>({})

const form = reactive({
  materialId: null as number | null,
  materialCode: '',
  materialName: '',
  materialType: MATERIAL_DEFAULT_TYPE,
  categoryId: null as number | null,
  tagIds: [] as number[],
  materialNameEn: '',
  materialGrade: '',
  model: '',
  color: '',
  brand: '',
  searchKeywords: '',
  size: '',
  specification: '',
  unit: MATERIAL_DEFAULT_UNIT,
  safeStock: 0,
  maxStock: 0,
  reorderPoint: 0,
  standardPrice: null as number | null,
  leadTime: null as number | null,
  supplierName: '',
  batchControl: false,
  shelfLife: null as number | null,
  expiryAlertDays: MATERIAL_DEFAULT_EXPIRY_ALERT_DAYS,
  remark: '',
})

const rules = computed(() => ({
  materialCode: [{ required: true, message: '请输入物料编码', trigger: 'blur' }],
  categoryId: form.materialId
    ? []
    : [{ required: true, message: '请选择品类', trigger: 'change' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }],
}))

const currentCategory = computed(
  () => categoryOptions.value.find((item) => item.categoryId === form.categoryId) || null
)
const categoryCode = computed(() => currentCategory.value?.categoryCode || '')
const materialTypeLabel = computed(() => {
  const type = deriveMaterialType(categoryCode.value) || form.materialType
  return type ? `${type} · ${MaterialTypeEnum.getLabel(type)}` : ''
})
const categoryHint = computed(() =>
  form.materialId && !form.categoryId ? '历史物料未设品类，可留空保存' : ''
)

const isRequired = (key: string) => requiredMaterialFields(categoryCode.value).includes(key)

// ==================== 规格属性（dev-20261023-022） ====================
// 规格由品类属性组合成文本后写回 inventory_material.specification；
// 没有配置属性的品类（其他/耗材工具）仍走自由填写，特殊物料不阻断。
const specDefs = ref<MaterialSpecAttrDef[]>([])
const specInputs = ref<Record<string, MaterialSpecInputValue>>({})
const specSupplement = ref('')
const loadedSpecCode = ref('')

const specAttrMode = computed(() => specDefs.value.length > 0)
const composedSpec = computed(() =>
  specAttrMode.value
    ? composeSpecification(specDefs.value, specInputs.value, specSupplement.value)
    : ''
)
/** 未填属性时保留历史规格文本（历史物料尚未结构化，不能被空属性清空）。 */
const legacySpecText = computed(() => {
  if (!form.materialId || composedSpec.value) return ''
  return (original.value?.specification || '').trim()
})
const specLegacyHint = computed(() => {
  if (!form.materialId) return ''
  const before = (original.value?.specification || '').trim()
  if (!before) return ''
  if (!composedSpec.value) {
    return `历史规格尚未结构化：「${before}」。填了属性才会替换，不填则原样保留。`
  }
  if (before !== composedSpec.value) {
    return `保存后规格将由属性组合生成（原文本：「${before}」）。`
  }
  return ''
})

const loadSpecValues = async (materialId: number) => {
  try {
    const res = await materialApi.getSpecValues(materialId)
    const data = res.data
    specDefs.value = data?.defs || []
    const savedByCode = new Map((data?.values || []).map((item) => [item.attrCode, item]))
    const inputs: Record<string, MaterialSpecInputValue> = {}
    specDefs.value.forEach((def) => {
      inputs[def.attrCode] = specValueToInput(def, savedByCode.get(def.attrCode))
    })
    specInputs.value = inputs
    specSupplement.value = data?.supplement || ''
    loadedSpecCode.value = data?.categoryCode || categoryCode.value
  } catch (error) {
    console.warn('[material] 规格属性加载失败，回退自由填写', error)
    specDefs.value = []
    specInputs.value = {}
    specSupplement.value = ''
  }
}

const loadSpecForCreate = async (code: string) => {
  try {
    const res = await materialApi.getSpecAttrDefs(code)
    specDefs.value = res.data || []
    specInputs.value = {}
    specSupplement.value = ''
    if (specDefs.value.length) {
      // 存量与新增都先按型号/名称/尺寸反推草稿，人工确认；不落库。
      specInputs.value = guessSpecValues(specDefs.value, {
        model: form.model,
        size: form.size,
        materialName: displayName.value || presetName.value,
      })
      form.specification = composeSpecification(specDefs.value, specInputs.value, '')
    } else {
      form.specification = form.materialId ? form.specification : ''
    }
  } catch (error) {
    console.warn('[material] 规格属性定义加载失败，回退自由填写', error)
    specDefs.value = []
    specInputs.value = {}
    specSupplement.value = ''
  }
}

/** 品类变化才重取属性定义；编辑时同时回显已存取值。 */
watch(categoryCode, async (code) => {
  if (!visible.value) return
  if (!code) {
    specDefs.value = []
    specInputs.value = {}
    specSupplement.value = ''
    loadedSpecCode.value = ''
    return
  }
  if (code === loadedSpecCode.value) return
  loadedSpecCode.value = code
  if (form.materialId) {
    await loadSpecValues(form.materialId)
  } else {
    await loadSpecForCreate(code)
  }
})

/** 属性变化 → 规格文本跟着变；编辑老物料且属性全空时保留原文本。 */
watch(composedSpec, (value) => {
  if (!specAttrMode.value) return
  if (value) {
    form.specification = value
    clearFieldError('specification')
  } else if (!form.materialId) {
    form.specification = ''
  }
})

const persistSpecValues = async (materialId: number | null, materialCode: string) => {
  if (!specAttrMode.value) return
  const attributes = specDefs.value.map((def) => {
    const input = specInputs.value[def.attrCode] || {}
    return { attrCode: def.attrCode, value: input.value || '', value2: input.value2 || '' }
  })
  try {
    const res = await materialApi.saveSpecValues({
      materialId: materialId || undefined,
      materialCode,
      categoryCode: categoryCode.value,
      supplement: specSupplement.value.trim(),
      createMode: !materialId,
      attributes,
    })
    const saved = res.data?.specification
    if (saved) form.specification = saved
  } catch (error) {
    // 物料主数据已保存，规格文本也已随主表单写入；这里失败只影响属性明细与统计。
    console.error('规格属性保存失败:', error)
    ElMessage.warning('物料已保存，但规格属性未写入；请重新打开编辑后再保存一次')
  }
}

/** 名称只读自动生成；编辑老物料且主数据没动时保留原名称，避免历史名称被改写。 */
const generatedName = computed(() =>
  composeMaterialName({
    materialGrade: form.materialGrade,
    model: form.model,
    color: form.color,
    brand: form.brand,
  })
)
const masterDirty = computed(() => {
  if (!original.value) return true
  return (['materialGrade', 'model', 'color', 'brand'] as const).some(
    (key) => (form[key] || '') !== (original.value?.[key] || '')
  )
})
const displayName = computed(() => {
  if (form.materialId) {
    if (!masterDirty.value) return original.value?.materialName || generatedName.value
    return generatedName.value || original.value?.materialName || ''
  }
  return generatedName.value || presetName.value
})

const clearFieldError = (key: string) => {
  delete fieldErrors[key]
}

const resetForm = () => {
  form.materialId = null
  form.materialCode = ''
  form.materialName = ''
  form.materialType = MATERIAL_DEFAULT_TYPE
  form.categoryId = null
  form.tagIds = []
  form.materialNameEn = ''
  form.materialGrade = ''
  form.model = ''
  form.color = ''
  form.brand = ''
  form.searchKeywords = ''
  form.size = ''
  form.specification = ''
  form.unit = MATERIAL_DEFAULT_UNIT
  form.safeStock = 0
  form.maxStock = 0
  form.reorderPoint = 0
  form.standardPrice = null
  form.leadTime = null
  form.supplierName = ''
  form.batchControl = false
  form.shelfLife = null
  form.expiryAlertDays = MATERIAL_DEFAULT_EXPIRY_ALERT_DAYS
  form.remark = ''
  original.value = null
  presetName.value = ''
  specDefs.value = []
  specInputs.value = {}
  specSupplement.value = ''
  loadedSpecCode.value = ''
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
}

const handleGenerateCode = async (materialType?: string) => {
  try {
    const res = await materialApi.generateCode(materialType || form.materialType)
    if (res.data) {
      form.materialCode = res.data
    }
  } catch (error) {
    ElMessage.error('生成物料编码失败')
  }
}

/** 品类决定大类（只读带出）与必填矩阵；新增时按大类重取编码。 */
const handleCategoryChange = () => {
  const type = deriveMaterialType(categoryCode.value)
  if (type) form.materialType = type
  if (!form.materialId) handleGenerateCode()
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
}

const loadTagOptions = async () => {
  const res = await materialApi.getTags()
  tagOptions.value = (res.data || []).flatMap((tag) =>
    tag.tagId == null
      ? []
      : [{ tagId: tag.tagId, tagName: tag.tagName || tag.tagCode || String(tag.tagId) }]
  )
}

const loadCategoryOptions = async () => {
  categoryOptions.value = await loadMaterialCategoryOptions()
}

const ensureCategoryOption = (categoryId?: number | null, categoryName?: string) => {
  if (!categoryId) return
  if (categoryOptions.value.some((item) => item.categoryId === categoryId)) return
  categoryOptions.value = [
    ...categoryOptions.value,
    {
      categoryId,
      categoryCode: '',
      categoryName: categoryName || `品类#${categoryId}`,
      materialType: '',
    },
  ]
}

const loadMaterialInfo = async (materialId: number) => {
  try {
    const res = await materialApi.getInfo(String(materialId))
    Object.assign(form, res.data)
    form.categoryId = res.data?.categoryId ?? null
    form.materialGrade = res.data?.materialGrade || ''
    form.model = res.data?.model || ''
    form.color = res.data?.color || ''
    form.brand = res.data?.brand || ''
    form.searchKeywords = res.data?.searchKeywords || ''
    form.size = res.data?.size || ''
    form.specification = res.data?.specification || ''
    form.tagIds = (res.data?.tags || []).flatMap((tag) => (tag.tagId == null ? [] : [tag.tagId]))
    ensureCategoryOption(res.data?.categoryId, res.data?.categoryName)
    original.value = {
      categoryId: form.categoryId,
      materialGrade: form.materialGrade,
      model: form.model,
      color: form.color,
      brand: form.brand,
      specification: form.specification,
      size: form.size,
      searchKeywords: form.searchKeywords,
      materialName: res.data?.materialName || '',
    }
    // 编辑：回显规格属性（品类没有属性配置时自动回退自由填写）
    await loadSpecValues(materialId)
  } catch (error) {
    ElMessage.error('加载物料信息失败')
  }
}

const collectMasterValues = () => ({
  materialGrade: form.materialGrade,
  model: form.model,
  color: form.color,
  brand: form.brand,
  specification: form.specification,
})

/** 编辑提交：空值区分「原本就空（不提交）」与「本次清空（提交 null）」。 */
const patchValue = (value: string | undefined, before: string | undefined) => {
  const current = (value || '').trim()
  if (current) return current
  if ((before || '').trim()) return null
  return undefined
}

const submitForm = () => {
  formRef.value.validate(async (valid: boolean) => {
    if (!valid) return

    Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
    const name = displayName.value
    if (!name) {
      ElMessage.error('请先填写材料/型号/颜色以自动生成物料名称')
      return
    }

    const categoryChanged =
      !!form.materialId && !!original.value && original.value.categoryId !== form.categoryId
    const missing = findMissingRequiredFields(
      collectMasterValues(),
      categoryCode.value,
      form.materialId ? original.value || undefined : undefined,
      categoryChanged
    )
    if (missing.length) {
      missing.forEach((key) => {
        fieldErrors[key] = `${MATERIAL_FIELD_LABELS[key] || key}必填`
      })
      ElMessage.error(`请补全：${missing.map((key) => MATERIAL_FIELD_LABELS[key] || key).join('、')}`)
      return
    }

    // 新增时按品类属性必填校验（编辑老物料不阻塞：历史数据没有属性值）
    if (specAttrMode.value && !form.materialId) {
      const missingAttrs = findMissingSpecAttrs(specDefs.value, specInputs.value)
      if (missingAttrs.length) {
        ElMessage.error(`请补全规格属性：${missingAttrs.map((def) => def.attrName).join('、')}`)
        return
      }
    }

    submitting.value = true
    try {
      if (form.materialId) {
        const before = original.value
        const updateData: MaterialUpdateDTO = {
          materialId: form.materialId,
          materialCode: form.materialCode,
          materialName: name,
          materialType: form.materialType,
          specification: patchValue(form.specification, before?.specification),
          unit: form.unit,
          safeStock: form.safeStock,
          maxStock: form.maxStock,
          reorderPoint: form.reorderPoint,
          standardPrice: form.standardPrice || undefined,
          leadTime: form.leadTime || undefined,
          supplierName: form.supplierName,
          batchControl: form.batchControl,
          shelfLife: form.shelfLife || undefined,
          expiryAlertDays: form.expiryAlertDays,
          remark: form.remark,
          tagIds: form.tagIds,
          materialGrade: patchValue(form.materialGrade, before?.materialGrade),
          model: patchValue(form.model, before?.model),
          color: patchValue(form.color, before?.color),
          brand: patchValue(form.brand, before?.brand),
          searchKeywords: patchValue(form.searchKeywords, before?.searchKeywords),
          size: patchValue(form.size, before?.size),
        }
        // 历史物料本无品类时留空不改，避免被后端「品类不能为空」拦住
        if (form.categoryId != null || before?.categoryId != null) {
          updateData.categoryId = form.categoryId
        }
        await materialApi.update(updateData)
        await persistSpecValues(form.materialId, form.materialCode)
        ElMessage.success('修改成功')
        visible.value = false
        emit('success', { ...form, materialName: name } as unknown as InventoryMaterial)
      } else {
        const saveData: MaterialSaveDTO = {
          materialCode: form.materialCode,
          materialName: name,
          materialType: form.materialType,
          categoryId: form.categoryId ?? undefined,
          specification: form.specification,
          unit: form.unit,
          safeStock: form.safeStock,
          maxStock: form.maxStock,
          reorderPoint: form.reorderPoint,
          standardPrice: form.standardPrice || undefined,
          leadTime: form.leadTime || undefined,
          supplierName: form.supplierName,
          batchControl: form.batchControl,
          shelfLife: form.shelfLife || undefined,
          expiryAlertDays: form.expiryAlertDays,
          remark: form.remark,
          tagIds: form.tagIds,
          materialGrade: form.materialGrade,
          model: form.model,
          color: form.color,
          brand: form.brand,
          searchKeywords: form.searchKeywords,
          size: form.size,
        }
        await materialApi.add(saveData)
        await persistSpecValues(null, form.materialCode)
        ElMessage.success('新增成功')
        visible.value = false
        // 新增后查询完整物料信息返回
        const res = await materialApi.getByCode(form.materialCode)
        emit('success', res.data || ({ ...form, materialName: name } as unknown as InventoryMaterial))
      }
    } catch (error) {
      console.error('提交失败:', error)
    } finally {
      submitting.value = false
    }
  })
}

const handleCancel = () => {
  visible.value = false
}

watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal) {
      resetForm()
      loadTagOptions()
      loadCategoryOptions()
      if (props.materialId) {
        loadMaterialInfo(props.materialId)
      } else {
        // 自动生成编码
        handleGenerateCode()
        // 如果有预填数据，把旧名称反解成结构化字段（旧模板/BOM 行兼容）
        if (props.presetData) {
          if (props.presetData.materialName) {
            presetName.value = props.presetData.materialName
            const parsed = splitLegacyMaterialName(props.presetData.materialName)
            if (parsed.materialGrade) form.materialGrade = parsed.materialGrade
            if (parsed.model) form.model = parsed.model
            if (parsed.color) form.color = parsed.color
            if (parsed.brand) form.brand = parsed.brand
          }
          if (props.presetData.specification) form.specification = props.presetData.specification
          if (props.presetData.unit) form.unit = props.presetData.unit
          if (props.presetData.supplierName) form.supplierName = props.presetData.supplierName
          if (props.presetData.materialType) form.materialType = props.presetData.materialType
        }
      }
    }
  }
)
</script>

<style scoped>
.field-hint {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}

.spec-legacy-hint {
  margin: 0 0 12px;
}
</style>
