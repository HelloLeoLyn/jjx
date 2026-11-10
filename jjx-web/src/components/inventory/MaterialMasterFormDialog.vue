<template>
  <el-dialog
    v-model="visible"
    :title="materialId ? '编辑物料' : '新增物料'"
    width="min(1280px, calc(100vw - 24px))"
    append-to-body
    :close-on-click-modal="false"
  >
    <el-skeleton v-if="loading" :rows="8" animated />
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" />
    <el-alert
      v-if="copySourceName"
      :title="`复制自「${copySourceName}」：已带入档案信息，请修改型号/尺寸等差异参数后保存`"
      type="info"
      :closable="false"
      style="margin-bottom: 8px"
    />
    <div v-if="!loading">
      <el-form ref="formRef" :model="form" label-width="120px" class="master-form master-layout">
        <section class="master-fields" aria-label="物料录入依据">
          <!-- 品类 + 子类两级（dev-20261104-008）：只有该品类存在子类时才出现第二个选择器，落库只存子类 id -->
          <el-form-item label="品类" required>
            <el-select
              data-test="material-category"
              :model-value="parentCategoryId ?? undefined"
              filterable
              placeholder="选择品类"
              style="width: 100%"
              :disabled="saving"
              @change="selectParentCategory"
            >
              <el-option
                v-for="category in parentCategoryOptions"
                :key="category.categoryId"
                :label="category.pathName"
                :value="category.categoryId"
              />
            </el-select>
          </el-form-item>
          <el-form-item v-if="subCategoryOptions.length" label="子类" required>
            <el-select
              :model-value="form.categoryId ?? undefined"
              filterable
              placeholder="选择子类"
              style="width: 100%"
              :disabled="saving"
              @change="selectCategory"
            >
              <el-option
                v-for="category in subCategoryOptions"
                :key="category.categoryId"
                :label="category.categoryName"
                :value="category.categoryId"
              />
            </el-select>
          </el-form-item>
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="主要供应商">
                <SupplierSelector
                  :model-value="form.supplierId ?? undefined"
                  :remote="true"
                  style="width: 100%"
                  @update:model-value="onSupplierChange"
                /> </el-form-item
            ></el-col>
            <el-col :span="12">
              <el-form-item label="供应商品名"
                ><el-input
                  v-model="form.supplierMaterialName"
                  maxlength="200"
                  placeholder="供应商自己的叫法/料号，采购单据与来料核对用" /></el-form-item
            ></el-col>
          </el-row>
          <el-form-item v-if="form.materialCode" label="物料编码">{{
            form.materialCode
          }}</el-form-item>
          <el-row :gutter="16">
            <el-col :span="24">
              <el-form-item label="材料显示名" :required="commonFieldRequired('material_grade')">
                {{ materialDisplayName || '选择材质和形态后生成' }}
              </el-form-item>
            </el-col>
            <el-col v-if="!categoryMaterialMeta" :span="12"
              ><el-form-item label="材质" :required="configuration?.schema.meta_required">
                <el-select
                  v-model="form.materialMeta"
                  clearable
                  :disabled="!materialOptions.length"
                  placeholder="该品类允许的材质"
                  style="width: 100%"
                >
                  <el-option
                    v-for="option in materialOptions"
                    :key="option.code"
                    :label="option.label"
                    :value="option.code"
                  />
                </el-select> </el-form-item
            ></el-col>
            <el-col :span="12"
              ><el-form-item label="形态">
                <el-select v-model="form.materialForm" clearable style="width: 100%"
                  ><el-option
                    v-for="o in dictionaries.form"
                    :key="o.code"
                    :value="o.code"
                    :label="o.label"
                /></el-select> </el-form-item
            ></el-col>
            <el-col :span="12"
              ><el-form-item label="颜色" :required="commonFieldRequired('color')">
                <el-select v-model="form.color" clearable filterable style="width: 100%"
                  ><el-option
                    v-for="o in dictionaries.color"
                    :key="o.code"
                    :value="o.code"
                    :label="o.label"
                /></el-select> </el-form-item
            ></el-col>
            <el-col :span="12"
              ><el-form-item label="品牌" :required="commonFieldRequired('brand')">
                <el-select v-model="form.brand" clearable filterable style="width: 100%"
                  ><el-option
                    v-for="o in dictionaries.brand"
                    :key="o.code"
                    :value="o.code"
                    :label="o.label"
                /></el-select> </el-form-item
            ></el-col>
            <el-col :span="12"
              ><el-form-item label="基本单位" required>
                <el-select v-model="form.unit" filterable style="width: 100%"
                  ><el-option
                    v-for="o in dictionaries.unit"
                    :key="o.code"
                    :value="o.code"
                    :label="`${o.label}/${o.code}`"
                /></el-select> </el-form-item
            ></el-col>
          </el-row>
          <el-form-item label="尺寸">
            <MaterialSizeEditor
              v-model="dimensions"
              :dimension-fields="configuration?.schema.spec_pattern?.fields"
              @valid-change="sizeValid = $event"
            />
          </el-form-item>
          <el-row :gutter="12">
            <el-col :span="8">
              <el-form-item label="克重(g/m²)">
                <el-input-number
                  v-model="form.grammage"
                  :min="0"
                  :precision="2"
                  :controls="false"
                  style="width: 100%"
                  aria-label="克重"
                />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="厚度公差(±mm)">
                <el-input-number
                  v-model="form.thicknessTolerance"
                  :min="0"
                  :precision="4"
                  :controls="false"
                  style="width: 100%"
                  aria-label="厚度公差"
                />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="规格口径">
                <el-input :model-value="specBasisLabel" readonly aria-label="规格口径" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="档案标记">
            <el-checkbox v-model="form.isCustomerSupplied">客供专用</el-checkbox
            ><el-checkbox v-model="form.isSample">样品专用</el-checkbox>
          </el-form-item>
          <template v-if="configuration">
            <el-divider content-position="left">规格属性</el-divider>
            <!-- 单块渲染全部属性：不再按常用/低频分组，避免字段被分组逻辑吃掉（dev-20261030-013） -->
            <MaterialAttributeFields v-model="form.attributeJson" :schema="configuration.schema" />
          </template>
          <el-collapse v-model="expanded">
            <el-collapse-item title="库存、采购与保质期参数" name="parameters">
              <el-row :gutter="16">
                <el-col v-for="field in numericFields" :key="field.key" :span="12">
                  <el-form-item :label="field.label"
                    ><el-input-number
                      v-model="form[field.key]"
                      :min="0"
                      :precision="field.precision"
                  /></el-form-item>
                </el-col>
              </el-row>
              <el-form-item label="批次管理"
                ><el-switch v-model="form.batchControl"
              /></el-form-item>
            </el-collapse-item>
          </el-collapse>

          <el-form-item label="业务备注"
            ><el-input v-model="form.remark" type="textarea" maxlength="1000" :rows="2"
          /></el-form-item>
        </section>

        <!-- 三项结果集中编辑/查看；生成规则仍由现有服务提供。 -->
        <aside class="master-preview" data-test="master-preview">
          <div class="preview-title">物料信息</div>
          <div class="preview-sub result-intro">填写左侧资料，在此核对名称、型号与规格。</div>
          <el-form-item label="名称" label-position="top" required data-test="material-name-field">
            <el-input
              :model-value="form.materialName"
              type="textarea"
              :autosize="{ minRows: 2, maxRows: 4 }"
              maxlength="200"
              data-test="material-name"
              @update:model-value="onNameInput"
            />
            <div class="field-hint" :class="{ manual: form.nameManual }">
              {{
                form.nameManual
                  ? '已手动修改，名称不再自动同步。'
                  : '自动生成，随录入资料更新；也可直接修改。'
              }}
            </div>
            <el-button
              v-if="form.nameManual"
              size="small"
              link
              type="primary"
              data-test="material-name-generate"
              @click="restoreAutoName"
              >按规则生成</el-button
            >
            <el-button
              v-if="canRevertName"
              size="small"
              link
              type="primary"
              data-test="material-name-revert"
              @click="revertName"
              >撤销修改</el-button
            >
            <details data-test="naming-rule-preview" class="rule-details">
              <summary>查看命名规则</summary>
              <div class="preview-sub">
                {{ nameRuleText || '该品类未配置命名规则，使用默认命名方式。' }}
              </div>
            </details>
          </el-form-item>
          <el-form-item
            label="型号"
            label-position="top"
            :required="commonFieldRequired('model')"
            data-test="material-model-field"
          >
            <el-input
              v-model="form.model"
              maxlength="100"
              data-test="material-model"
              @input="onModelInput"
              @blur="onModelBlur"
            >
              <template v-if="canRegenerateModel" #append>
                <el-button data-test="material-model-regenerate" @click="regenerateModel">
                  {{ form.materialId ? '按规则重算' : '重新生成' }}
                </el-button>
              </template>
            </el-input>
            <div v-if="modelDuplicate" class="field-hint model-duplicate">
              型号已存在（物料编码 {{ modelDuplicate.materialCode }}）
            </div>
            <div class="field-hint" :class="{ manual: modelManual }">{{ modelHint }}</div>
            <div class="model-restore">
              <el-button
                v-if="!form.materialId && modelManual && canRestoreAutoModel"
                link
                type="primary"
                size="small"
                data-test="material-model-restore-auto"
                @click="restoreAutoModel"
                >恢复按规则生成</el-button
              >
              <el-button
                v-if="canRevertModel"
                link
                type="primary"
                size="small"
                data-test="material-model-revert"
                @click="revertModel"
                >{{ form.materialId ? '撤销修改' : '撤销手改' }}</el-button
              >
            </div>
            <div v-if="modelBasisText" class="field-hint">依据：{{ modelBasisText }}</div>
            <el-button v-if="!modelPatternFields.length" link type="primary" @click="goNamingRule">
              去配置「型号规则」
            </el-button>
          </el-form-item>
          <el-form-item
            label="规格"
            label-position="top"
            :required="commonFieldRequired('specification')"
            data-test="material-spec-field"
          >
            <div class="preview-value specification-result" role="status">
              {{ specificationPreview || '填写尺寸与规格属性后生成' }}
            </div>
            <div class="field-hint">由规则生成；请在左侧修改尺寸和规格属性。</div>
          </el-form-item>
          <div class="preview-block">
            <div class="preview-label">分类 / 材质</div>
            <div class="preview-sub">{{ configuration?.path?.[0]?.categoryName || '未归类' }}</div>
            <div class="preview-sub">{{ materialLabel || '材质未选' }}</div>
          </div>
          <div class="preview-note">
            生成结果与保存使用相同规则，保存时仍会校验必填项与型号唯一性。
          </div>
        </aside>
      </el-form>
    </div>
    <template #footer>
      <el-button :disabled="saving" @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="saving"
        :disabled="loading || configurationLoading || !!loadError || !configuration"
        @click="save"
        >保存物料</el-button
      >
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { materialMasterApi } from '@/api/inventory/materialMaster'
import { materialApi } from '@/api/inventory/material'
import type { InventoryMaterial } from '@/types/inventory/material'
import type { SysTag } from '@/types/system/tag'
import type {
  MaterialDictionaries,
  MaterialMasterCategory,
  MaterialMasterConfiguration,
  MaterialMasterForm,
  MaterialRequiredFields,
  MaterialRequiredFieldKey,
} from '@/types/inventory/materialMaster'
import MaterialAttributeFields from './MaterialAttributeFields.vue'
import MaterialSizeEditor from './MaterialSizeEditor.vue'
import SupplierSelector from '@/components/Selector/SupplierSelector.vue'
import { emptyMaterialSize, parseMaterialSize, renderMaterialDimensions } from './materialSize'
import type { MaterialSizeValue } from './materialSize'
import {
  applyCopySource,
  attributeVisible,
  changeMasterCategory,
  effectiveMasterName,
  emptyMasterForm,
  previewMasterName,
  previewMasterSpecification,
  pruneAttributes,
} from './materialMasterModel'
import { el } from 'element-plus/es/locale/index.mjs'

const router = useRouter()

const props = defineProps<{
  modelValue: boolean
  materialId?: number | null
  /** 复制新增：带入该物料档案信息，清空编码/名称/型号（dev-20261107-016） */
  copyFromId?: number | null
  /** 来自 BOM 快速建档：后端对该入口放宽“颜色必填”（dev-20261029-016） */
  fromBom?: boolean
  presetData?: {
    materialName?: string
    specification?: string
    unit?: string
    supplierName?: string
  }
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success', material: InventoryMaterial): void
}>()
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
const form = reactive<MaterialMasterForm>({ ...emptyMasterForm(), ...emptyMaterialSize() })
const dimensions = computed<MaterialSizeValue>({
  get: () => ({
    widthMm: form.widthMm ?? null,
    lengthMm: form.lengthMm ?? null,
    thicknessMm: form.thicknessMm ?? null,
  }),
  set: (value) => Object.assign(form, value),
})
const categories = ref<MaterialMasterCategory[]>([])
const dictionaries = ref<MaterialDictionaries>({})
const configuration = ref<MaterialMasterConfiguration>()
const requiredFields = ref<MaterialRequiredFields>()
function commonFieldRequired(key: MaterialRequiredFieldKey) {
  return (
    !props.materialId &&
    !form.materialId &&
    !(props.fromBom && key === 'color') &&
    requiredFields.value?.[key] === true
  )
}
const materialDisplayName = computed(() => {
  const label = (dict: string, code: string) =>
    code ? dictionaries.value[dict]?.find((o) => o.code === code)?.label || code : ''
  return `${label('material_meta', form.materialMeta)}${label('form', form.materialForm)}`.trim()
})
const tags = ref<SysTag[]>([])
const loading = ref(false)
const configurationLoading = ref(false)
const saving = ref(false)
const sizeValid = ref(true)
/** 口径由服务端按「填了克重→GRAMMAGE，有厚度→THICKNESS，否则 SIZE」判定，这里只回显。 */
function onSupplierChange(id?: string | number | null) {
  form.supplierId = id == null || id === '' ? null : Number(id)
  if (form.supplierId == null) form.supplierName = ''
}

const specBasisLabel = computed(() => {
  if (form.grammage) return '克重（GRAMMAGE）'
  return form.thicknessMm != null ? '厚度（THICKNESS）' : '仅尺寸（SIZE）'
})
const loadError = ref('')
const copySourceName = ref('')
const expanded = ref<string[]>([])
let session = 0
let categoryRequest = 0
const leafCategories = computed(() => categories.value.filter((c) => c.selectable))
/** 品类下拉：可选叶子 + 有子类的父级（第 4 级子类只在子类下拉里出现）。 */
const parentCategoryId = ref<number | null>(null)
const hasChildren = (id?: number | null) =>
  id != null && categories.value.some((c) => c.parentId === id)
const parentCategoryOptions = computed(() =>
  categories.value.filter((c) => {
    const level = c.categoryLevel ?? 0
    // 品类下拉只到第 3 级：第 4 级子类只在「子类」下拉里出现，避免父子重复
    return (level === 2 || level === 3) && (c.selectable || hasChildren(c.categoryId))
  })
)
const subCategoryOptions = computed(() =>
  parentCategoryId.value == null
    ? []
    : categories.value.filter((c) => c.parentId === parentCategoryId.value && c.selectable)
)
/** 选品类：有子类则等子类再加载模板，没有子类就直接按叶子加载。 */
async function selectParentCategory(id: number) {
  parentCategoryId.value = id
  if (hasChildren(id)) {
    categoryRequest++
    modelRequest++
    configuration.value = undefined
    requiredFields.value = undefined
    configurationLoading.value = false
    form.categoryId = null
    form.attributeJson = {}
    return
  }
  await selectCategory(id)
}
const materialOptions = computed(() =>
  (dictionaries.value.material_meta || []).filter((o) =>
    configuration.value?.schema.meta_options.includes(o.code)
  )
)
/** 仅控制表单显示与草稿同步，最终落值由后端按模板确定。 */
const categoryMaterialMeta = computed(() => {
  const schema = configuration.value?.schema
  return schema?.meta_required === true &&
    schema.meta_options.length === 1 &&
    schema.default_meta === schema.meta_options[0]
    ? schema.default_meta
    : null
})
watch(
  () => [categoryMaterialMeta.value, form.materialMeta],
  () => {
    if (categoryMaterialMeta.value) form.materialMeta = categoryMaterialMeta.value
  }
)
/** 型号预览只用于新建；手改后停止同步，异步旧响应不能覆盖手改或新会话。 */
const modelManual = ref(false)
/** 最近一次按规则生成的型号：「撤销手改」的本地回退点（新建态）。 */
const modelAutoValue = ref('')
/** 打开弹窗时的原型号：「撤销修改」的回退点（编辑态）。 */
const modelBaseline = ref('')
const modelHint = computed(() => {
  if (modelManual.value) return '已手动修改，型号不再自动同步。'
  if (props.materialId || form.materialId)
    return '保留现有型号；修改其他资料不会自动重生成，可直接修改型号。'
  return configuration.value?.schema.model_pattern?.fields.length
    ? '按当前品类规则自动生成，也可直接修改。'
    : '该品类未配置型号生成规则，请直接填写。'
})
const modelDuplicate = ref<{ materialCode?: string } | null>(null)
const modelPatternFields = computed(() => configuration.value?.schema.model_pattern?.fields || [])
const modelBasisText = computed(() => {
  if (!modelPatternFields.value.length) return ''
  const prefix = configuration.value?.category?.modelPrefix || ''
  return [prefix ? '前缀 ' + prefix : '', modelPatternFields.value.join(' + ')].filter(Boolean).join('，')
})
const canRegenerateModel = computed(() => {
  if (!modelPatternFields.value.length) return false
  if (form.materialId) return true
  return modelManual.value
})
/** 只有新建态能「恢复自动生成」：编辑态永不自动重算，避免历史型号被悄悄改掉。 */
const canRestoreAutoModel = computed(
  () => !form.materialId && modelPatternFields.value.length > 0
)
/** 撤销入口只在「当前值与回退点不同」时出现，避免无意义的按钮。 */
const canRevertModel = computed(() => {
  const baseline = form.materialId ? modelBaseline.value : modelAutoValue.value
  return !!baseline && form.model !== baseline
})
/** 恢复按规则生成：回到自动同步并立即按当前资料重算（无二次确认）。 */
async function restoreAutoModel() {
  if (!canRestoreAutoModel.value) return
  modelManual.value = false
  modelDuplicate.value = null
  modelRequest++
  if (!(await refreshModel(true))) return
  void onModelBlur()
}
/** 撤销手改：只回退到回退点，不请求规则接口（程序赋值不触发 blur，需主动查重）。 */
function revertModel() {
  const baseline = form.materialId ? modelBaseline.value : modelAutoValue.value
  if (!baseline) return
  modelRequest++
  form.model = baseline
  modelDuplicate.value = null
  if (!form.materialId) modelManual.value = false
  void onModelBlur()
}
async function onModelBlur() {
  const value = form.model.trim()
  if (!value) {
    modelDuplicate.value = null
    return
  }
  try {
    const response = await materialMasterApi.checkModel(value, form.materialId)
    modelDuplicate.value = response.data || null
  } catch {
    modelDuplicate.value = null
  }
}
async function regenerateModel() {
  if (form.materialId) {
    try {
      await ElMessageBox.confirm('按当前品类型号规则重算型号？保存后仍会校验唯一性。', '按规则重算型号', { type: 'warning' })
    } catch {
      return
    }
  }
  modelManual.value = false
  modelDuplicate.value = null
  await refreshModel(true)
}
function goNamingRule() {
  router.push('/inventory/material-admin/naming-rule')
}
let modelRequest = 0
let modelTimer: ReturnType<typeof setTimeout> | undefined
function onModelInput() {
  modelManual.value = true
  modelRequest++
}
function modelDraft() {
  return {
    materialMeta: form.materialMeta,
    materialForm: form.materialForm,
    unit: form.unit,
    brand: form.brand,
    color: form.color,
    widthMm: form.widthMm,
    lengthMm: form.lengthMm,
    thicknessMm: form.thicknessMm,
    grammage: form.grammage,
    attributeJson: form.attributeJson,
    supplierId: form.supplierId,
    supplierName: form.supplierName,
    isCustomerSupplied: form.isCustomerSupplied,
    isSample: form.isSample,
  }
}
async function refreshModel(force = false) {
  if (
    (!force && (props.materialId || form.materialId)) ||
    (!force && modelManual.value) ||
    !form.categoryId ||
    !configuration.value?.schema.model_pattern?.fields.length
  )
    return true
  const request = ++modelRequest
  const currentSession = session
  const categoryId = form.categoryId
  const response = await materialMasterApi.renderModel(categoryId, modelDraft())
  if (
    request === modelRequest &&
    currentSession === session &&
    categoryId === form.categoryId &&
    (force || (!props.materialId && !form.materialId && !modelManual.value)) &&
    visible.value
  ) {
    form.model = response.data || ''
    modelAutoValue.value = form.model
    return true
  }
  return (
    currentSession === session &&
    categoryId === form.categoryId &&
    visible.value &&
    modelManual.value
  )
}
watch(
  () => [
    visible.value,
    form.categoryId,
    JSON.stringify(modelDraft()),
    JSON.stringify(configuration.value?.schema.model_pattern),
  ],
  () => {
    modelRequest++
    if (modelTimer) clearTimeout(modelTimer)
    if (!visible.value || loading.value || props.materialId || form.materialId || modelManual.value)
      return
    modelTimer = setTimeout(() => {
      void refreshModel().catch((error) =>
        ElMessage.error(error instanceof Error ? error.message : '型号生成失败')
      )
    }, 300)
  }
)
onBeforeUnmount(() => {
  modelRequest++
  if (modelTimer) clearTimeout(modelTimer)
  if (ruleTimer) clearTimeout(ruleTimer)
})
/** 规格模板生成结果（后端统一实现，dev-20261029-027） */
const specText = ref('')
const specificationPreview = computed(() => {
  if (configuration.value?.schema?.spec_pattern?.fields?.length) return specText.value
  const schema = configuration.value?.schema
  if (!schema?.fields.length) return ''
  const attributes = { ...form.attributeJson }
  delete attributes.thickness_mm
  const text = previewMasterSpecification(schema, attributes, {
    thickness: String(form.grammage ?? form.thicknessMm ?? ''),
  })
  const size = renderMaterialDimensions(
    dimensions.value,
    false,
    configuration.value?.schema.spec_pattern?.fields
  )
  return [text, size ? `尺寸:${size}` : ''].filter(Boolean).join('；')
})
/** 命名规则文本：字段名带各自 1 字符分隔符，便于操作员看懂当前规则 */
const nameRuleText = computed(() => {
  const pattern = configuration.value?.schema?.name_pattern
  if (!pattern || !pattern.fields?.length) return ''
  const labelOf = (key: string) => {
    const fixed: Record<string, string> = {
      brand: '品牌',
      model: '型号',
      color: '颜色',
      meta: '材质',
      form: '形态',
      unit: '单位',
      width: '宽',
      length: '长',
      thickness_mm: '厚',
      grammage: '克重',
    }
    return (
      fixed[key] || configuration.value?.schema?.fields?.find((f) => f.key === key)?.label || key
    )
  }
  return pattern.fields
    .map((key, index) => {
      const sep = pattern.separators?.[key] ?? (index === 0 ? '' : (pattern.separator ?? ' '))
      const token = sep === ' ' ? '␠' : sep
      return `${token}${labelOf(key)}`
    })
    .join(' ')
})

/** 按规则生成的名字（后端无状态渲染，单一实现） */
const ruleName = ref('')
let ruleTimer: ReturnType<typeof setTimeout> | undefined
watch(
  () => [
    form.categoryId,
    form.materialMeta,
    form.materialForm,
    form.model,
    form.color,
    form.brand,
    form.unit,
    form.widthMm,
    form.lengthMm,
    form.thicknessMm,
    form.grammage,
    JSON.stringify(form.attributeJson || {}),
    JSON.stringify(configuration.value?.schema.spec_pattern),
  ],
  () => {
    if (
      !form.categoryId ||
      (!configuration.value?.schema?.name_pattern &&
        !configuration.value?.schema?.spec_pattern?.fields.length)
    ) {
      ruleName.value = ''
      specText.value = ''
      return
    }
    if (ruleTimer) clearTimeout(ruleTimer)
    ruleTimer = setTimeout(async () => {
      try {
        const draft = {
          materialMeta: form.materialMeta,
          materialForm: form.materialForm,
          model: form.model,
          color: form.color,
          brand: form.brand,
          unit: form.unit,
          widthMm: form.widthMm ?? null,
          lengthMm: form.lengthMm ?? null,
          thicknessMm: form.thicknessMm ?? null,
          grammage: form.grammage ?? null,
          attributeJson: form.attributeJson || {},
        }
        try {
          const specResponse = await materialMasterApi.renderSpec(form.categoryId!, draft)
          specText.value = specResponse.data || ''
        } catch {
          specText.value = ''
        }
        if (configuration.value?.schema.name_pattern) {
          const response = await materialMasterApi.renderNaming(form.categoryId!, draft)
          ruleName.value = response.data || ''
        } else ruleName.value = ''
      } catch {
        ruleName.value = ''
      }
    }, 300)
  },
  { immediate: true }
)
const materialLabel = computed(
  () =>
    dictionaries.value.material_meta?.find((o) => o.code === form.materialMeta)?.label ||
    form.materialMeta ||
    ''
)

// 编辑回退到打开时名称；新增保留第一次手改前的名称和自动同步状态。
const nameBaseline = ref('')
const nameUndo = ref<{ value: string; manual: boolean } | null>(null)
function rememberName() {
  if (!form.materialId && !nameUndo.value)
    nameUndo.value = { value: form.materialName, manual: !!form.nameManual }
}
function onNameInput(value: string) {
  rememberName()
  form.nameManual = true
  form.materialName = value
}
/** 自动命名：优先按品类命名规则生成，未配置规则才回落旧逻辑（dev-20261029-026） */
const autoName = computed(() => ruleName.value || previewMasterName(form, dictionaries.value))
const nameRevertValue = computed(() =>
  form.materialId
    ? nameBaseline.value
    : nameUndo.value?.manual ? nameUndo.value.value : autoName.value
)
const canRevertName = computed(() =>
  !!(form.materialId || nameUndo.value) && form.materialName !== nameRevertValue.value
)

function revertName() {
  const value = nameRevertValue.value
  // 历史名称必须原样保留，不能因当前规则不同而再次被自动命名覆盖。
  form.nameManual = form.materialId ? true : (nameUndo.value?.manual ?? false)
  form.materialName = value
  nameUndo.value = null
}

function restoreAutoName() {
  rememberName()
  form.nameManual = false
  form.materialName = autoName.value
}

// 未手动命名时随材料/型号/颜色/品牌实时同步（dev-20261024-004）
watch(
  () =>
    [
      form.materialMeta,
      form.model,
      form.color,
      form.brand,
      form.nameManual,
      autoName.value,
    ] as const,
  () => {
    if (!form.nameManual) form.materialName = autoName.value
  },
  { immediate: true }
)
type NumericKey =
  | 'safeStock'
  | 'maxStock'
  | 'reorderPoint'
  | 'standardPrice'
  | 'leadTime'
  | 'shelfLife'
  | 'expiryAlertDays'
const numericFields: { key: NumericKey; label: string; precision: number }[] = [
  { key: 'safeStock', label: '安全库存', precision: 4 },
  { key: 'maxStock', label: '最高库存', precision: 4 },
  { key: 'reorderPoint', label: '再订货点', precision: 4 },
  { key: 'standardPrice', label: '标准单价', precision: 4 },
  { key: 'leadTime', label: '采购周期(天)', precision: 0 },
  { key: 'shelfLife', label: '保质期(天)', precision: 0 },
  { key: 'expiryAlertDays', label: '预警天数', precision: 0 },
]

async function selectCategory(id: number) {
  const request = ++categoryRequest
  const currentSession = session
  const hadAttributes = Object.keys(form.attributeJson || {}).length > 0
  configuration.value = undefined
  requiredFields.value = undefined
  configurationLoading.value = true
  form.categoryId = id
  form.attributeJson = {}
  if (hadAttributes) ElMessage.info('已按新分类清理不适用的属性，请重新选择该分类的属性')
  try {
    const [response, requiredResponse] = await Promise.all([
      materialMasterApi.configuration(id),
      props.materialId || form.materialId
        ? Promise.resolve(null)
        : materialMasterApi.requiredFields(id),
    ])
    if (request !== categoryRequest || currentSession !== session) return
    if (!response.data) throw Error('品类模板加载失败')
    if (!props.materialId && !form.materialId && !requiredResponse?.data)
      throw Error('必填字段加载失败，请重新选择品类')
    requiredFields.value = requiredResponse?.data ?? undefined
    configuration.value = response.data
    changeMasterCategory(form, response.data)
  } catch (error) {
    if (request === categoryRequest && currentSession === session)
      ElMessage.error(error instanceof Error ? error.message : '品类模板加载失败')
  } finally {
    if (request === categoryRequest && currentSession === session)
      configurationLoading.value = false
  }
}

async function initialize() {
  sizeValid.value = true
  nameBaseline.value = ''
  nameUndo.value = null
  modelManual.value = false
  modelAutoValue.value = ''
  modelBaseline.value = ''
  modelRequest++
  if (modelTimer) clearTimeout(modelTimer)
  const currentSession = ++session
  categoryRequest++
  loading.value = true
  loadError.value = ''
  configuration.value = undefined
  requiredFields.value = undefined
  configurationLoading.value = false
  Object.assign(form, emptyMasterForm(), emptyMaterialSize(), {
    materialId: undefined,
    materialCode: undefined,
    supplierId: null,
    defaultWarehouseId: null,
    defaultLocationId: null,
  })
  expanded.value = []
  copySourceName.value = ''
  parentCategoryId.value = null
  try {
    const [categoryResponse, dictResponse, tagResponse, detailResponse] = await Promise.all([
      materialMasterApi.categories(),
      materialMasterApi.dictionaries(),
      materialApi.getTags(),
      props.materialId
        ? materialMasterApi.detail(props.materialId)
        : props.copyFromId
          ? materialMasterApi.detail(props.copyFromId)
          : Promise.resolve(null),
    ])
    if (currentSession !== session) return
    categories.value = categoryResponse.data || []
    dictionaries.value = dictResponse.data || {}
    tags.value = tagResponse.data || []
    if (detailResponse?.data && !props.materialId && props.copyFromId) {
      const detail = detailResponse.data
      applyCopySource(form, detail)
      configuration.value = detail.configuration ?? undefined
      copySourceName.value = detail.material.materialName || ''
    } else if (detailResponse?.data) {
      const detail = detailResponse.data
      const material = detail.material
      for (const key of Object.keys(form) as (keyof MaterialMasterForm)[]) {
        if (key in material)
          Object.assign(form, { [key]: material[key as keyof typeof material] ?? form[key] })
      }
      form.materialId = material.materialId
      form.materialCode = material.materialCode
      form.materialName = material.materialName || ''
      nameBaseline.value = form.materialName
      form.nameManual =
        !!material.materialName &&
        material.materialName !== previewMasterName(form, dictionaries.value)
      form.attributeJson = detail.attributes
      delete form.attributeJson.thickness_mm
      form.tagIds = detail.tags.flatMap((tag) => (tag.tagId == null ? [] : [tag.tagId]))
      configuration.value = detail.configuration ?? undefined
      if (!detail.configuration) form.categoryId = null
      // 回显：物料挂在子类时，品类下拉显示其父级
      const self = categories.value.find((c) => c.categoryId === form.categoryId)
      parentCategoryId.value =
        self?.parentId != null && hasChildren(self.parentId)
          ? self.parentId
          : (form.categoryId ?? null)
      modelBaseline.value = form.model || ''
    } else if (props.presetData) {
      if (props.presetData.materialName) {
        form.materialName = props.presetData.materialName
        form.nameManual = true
      }
      form.unit = props.presetData.unit || ''
      form.supplierName = props.presetData.supplierName || ''
      // BOM 行的规格（如 340*305mm）解析到宽/长/厚/单位（dev-20261029-016）
      if (props.presetData.specification) {
        const parsedSpec = parseMaterialSize(props.presetData.specification)
        if (parsedSpec) {
          Object.assign(form, {
            widthMm: parsedSpec.width ? Number(parsedSpec.width) : null,
            lengthMm: parsedSpec.length ? Number(parsedSpec.length) : null,
            thicknessMm: parsedSpec.thickness ? Number(parsedSpec.thickness) : null,
          })
        }
      }
    }
  } catch (error) {
    if (currentSession === session)
      loadError.value = error instanceof Error ? error.message : '物料数据加载失败，请关闭后重试'
  } finally {
    if (currentSession === session) loading.value = false
  }
}

async function save() {
  if (!form.categoryId)
    return void ElMessage.warning(parentCategoryId.value != null ? '请选择子类' : '请选择品类')
  if (!sizeValid.value) return void ElMessage.warning('请先修正尺寸')
  if (form.grammage && form.thicknessMm != null)
    return void ElMessage.warning('克重与厚度只能选一种口径，请清掉尺寸里的厚度')
  if (
    saving.value ||
    !configuration.value ||
    loadError.value ||
    loading.value ||
    configurationLoading.value
  )
    return
  // 保存前刷新尚未手改的型号，避免在 300ms 预览防抖窗口内提交旧值。
  saving.value = true
  try {
    if (modelTimer) clearTimeout(modelTimer)
    if (!(await refreshModel())) return void ElMessage.warning('物料信息已变化，请重试保存')
    if (
      !form.materialId &&
      configuration.value.schema.model_pattern?.fields.length &&
      !form.nameManual &&
      configuration.value.schema.name_pattern
    ) {
      const response = await materialMasterApi.renderNaming(form.categoryId, {
        ...modelDraft(),
        model: form.model,
      })
      ruleName.value = response.data || ''
    }
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '型号预览失败，请重试')
    return
  } finally {
    saving.value = false
  }
  const finalName =
    (form.nameManual ? '' : ruleName.value || '').trim() ||
    effectiveMasterName(form, dictionaries.value).trim()
  if (!finalName) return void ElMessage.warning('请填写名称，或先选择材料/型号/颜色自动生成')
  if (!form.unit) return void ElMessage.warning('请选择基本单位')
  if (!props.materialId && !form.materialId) {
    if (!requiredFields.value) return void ElMessage.warning('必填字段尚未加载，请重新选择品类')
    const values: Record<MaterialRequiredFieldKey, string> = {
      material_grade: materialDisplayName.value,
      model: form.model,
      color: form.color,
      specification: '',
      brand: form.brand,
    }
    if (commonFieldRequired('specification')) {
      // 保存前取当前草稿的后端规格，避免防抖中的旧预览导致误报。
      saving.value = true
      try {
        const response = await materialMasterApi.renderSpec(form.categoryId, { ...form })
        values.specification = response.data || ''
      } catch (error) {
        ElMessage.error(error instanceof Error ? error.message : '规格预览失败，请重试')
        return
      } finally {
        saving.value = false
      }
    }
    const labels: Record<MaterialRequiredFieldKey, string> = {
      material_grade: '材料显示名',
      model: '型号',
      color: '颜色',
      specification: '规格',
      brand: '品牌',
    }
    const missingField = (Object.keys(labels) as MaterialRequiredFieldKey[]).find(
      (key) => commonFieldRequired(key) && !values[key]?.trim()
    )
    if (missingField)
      return void ElMessage.warning(
        `${labels[missingField]}不能为空（${configuration.value.category.categoryName} 必填）`
      )
  }
  const missing = configuration.value.schema.fields.find(
    (f) =>
      f.required &&
      f.key !== 'thickness_mm' &&
      attributeVisible(f, form.attributeJson) &&
      (form.attributeJson[f.key] == null ||
        form.attributeJson[f.key] === '' ||
        (Array.isArray(form.attributeJson[f.key]) &&
          !(form.attributeJson[f.key] as string[]).length))
  )
  if (missing) return void ElMessage.warning(`请填写${missing.label}`)
  saving.value = true
  try {
    const { size: _size, nameManual: _nameManual, ...fields } = form
    const payload = {
      ...fields,
      materialName: finalName,
      attributeJson: pruneAttributes(configuration.value.schema, form.attributeJson),
      fromBom: props.fromBom === true,
    }
    delete payload.attributeJson.thickness_mm
    const response = await materialMasterApi.save(payload)
    if (!response.data?.materialId) throw Error('保存响应缺少物料信息')
    ElMessage.success('物料保存成功')
    visible.value = false
    emit('success', response.data)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '物料保存失败')
  } finally {
    saving.value = false
  }
}

watch(
  () => [props.modelValue, props.materialId, props.copyFromId] as const,
  ([open]) => {
    if (open) void initialize()
    else {
      session++
      categoryRequest++
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.el-collapse {
  margin-bottom: 18px;
}
.master-layout {
  display: flex;
  gap: 16px;
  align-items: flex-start;
}
.master-form {
  width: 100%;
}
.master-fields {
  flex: 1 1 auto;
  min-width: 0;
}
.master-preview {
  flex: 0 0 340px;
  box-sizing: border-box;
  position: sticky;
  top: 12px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px;
  background: #fafcff;
}
.preview-title {
  font-weight: 600;
  margin-bottom: 10px;
}
.preview-block {
  margin-bottom: 12px;
}
.preview-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.preview-value {
  font-size: 14px;
  color: #303133;
  word-break: break-all;
  line-height: 1.5;
}
.preview-sub {
  font-size: 12px;
  color: #606266;
}
.preview-tag {
  display: inline-block;
  margin-top: 4px;
  font-size: 11px;
  color: #e6a23c;
}
.preview-note {
  font-size: 12px;
  color: #606266;
  line-height: 1.6;
}
.result-intro {
  margin-bottom: 16px;
}
.rule-details {
  width: 100%;
  margin-top: 8px;
}
.rule-details summary {
  cursor: pointer;
  font-size: 12px;
  color: #606266;
}
.specification-result {
  width: 100%;
}
@media (max-width: 1000px) {
  .master-layout {
    flex-direction: column;
  }
  .master-fields,
  .master-preview {
    width: 100%;
    box-sizing: border-box;
  }
  .master-preview {
    flex: none;
    position: static;
  }
}
@media (max-width: 600px) {
  .master-fields :deep(.el-col) {
    flex: 0 0 100%;
    max-width: 100%;
  }
}
.model-duplicate {
  color: var(--el-color-danger);
}

.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.field-hint.manual {
  color: #e6a23c;
}
.model-restore {
  display: flex;
  gap: 12px;
  margin-top: 2px;
}
</style>
