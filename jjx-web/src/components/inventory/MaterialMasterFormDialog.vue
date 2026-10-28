<template>
  <el-dialog
    v-model="visible"
    :title="materialId ? '编辑物料' : '新增物料'"
    width="1280px"
    append-to-body
    :close-on-click-modal="false"
  >
    <el-skeleton v-if="loading" :rows="8" animated />
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" />
    <div v-if="!loading" class="master-layout">
      <el-form ref="formRef" :model="form" label-width="120px" class="master-form">
        <el-form-item label="品类" required>
          <el-select
            :model-value="form.categoryId"
            filterable
            placeholder="选择末级品类"
            style="width: 100%"
            :disabled="saving"
            @change="selectCategory"
          >
            <el-option
              v-for="category in leafCategories"
              :key="category.categoryId"
              :label="category.pathName"
              :value="category.categoryId"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">{{
          configuration?.path[0]?.categoryName || '选择品类后自动确定'
        }}</el-form-item>
        <el-form-item v-if="form.materialCode" label="物料编码">{{
          form.materialCode
        }}</el-form-item>
        <el-form-item label="名称" required>
          <el-input v-model="form.materialName" maxlength="200" @input="onNameInput">
            <template #append>
              <el-button :disabled="!form.nameManual" @click="restoreAutoName">恢复自动</el-button>
            </template>
          </el-input>
          <div class="field-hint" :class="{ manual: form.nameManual }">
            {{
              form.nameManual
                ? '手动命名：已停止自动同步（点「恢复自动」回到 材料+型号+颜色（品牌））'
                : '自动命名：材料 + 型号 + 颜色（品牌），无空格、缺项跳过'
            }}
          </div>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12"
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
            ><el-form-item label="型号"
              ><el-input v-model="form.model" maxlength="100" /></el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="颜色">
              <el-select v-model="form.color" clearable filterable style="width: 100%"
                ><el-option
                  v-for="o in dictionaries.color"
                  :key="o.code"
                  :value="o.code"
                  :label="o.label"
              /></el-select> </el-form-item
          ></el-col>
          <el-col :span="12"
            ><el-form-item label="品牌">
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
                  :label="o.label"
              /></el-select> </el-form-item
          ></el-col>
        </el-row>
        <el-form-item label="尺寸">
          <MaterialSizeEditor v-model="form.size" @valid-change="sizeValid = $event" />
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
          <MaterialAttributeFields
            v-model="form.attributeJson"
            :schema="configuration.schema"
            :keys="commonKeys"
          />
          <el-collapse v-if="lowKeys.length" v-model="expanded">
            <el-collapse-item :title="`低频属性（${lowKeys.length}）`" name="low">
              <MaterialAttributeFields
                v-model="form.attributeJson"
                :schema="configuration.schema"
                :keys="lowKeys"
              />
            </el-collapse-item>
          </el-collapse>
        </template>
        <el-form-item label="别名"
          ><el-input v-model="form.searchKeywords" maxlength="500"
        /></el-form-item>
        <el-form-item label="物料标签">
          <el-select v-model="form.tagIds" multiple filterable clearable style="width: 100%">
            <el-option
              v-for="tag in tags"
              :key="tag.tagId"
              :label="tag.tagName"
              :value="tag.tagId!"
            />
          </el-select>
        </el-form-item>
        <el-collapse v-model="expanded">
          <el-collapse-item title="库存、采购与保质期参数" name="parameters">
            <el-row :gutter="16">
              <el-col v-for="field in numericFields" :key="field.key" :span="12">
                <el-form-item :label="field.label"
                  ><el-input-number v-model="form[field.key]" :min="0" :precision="field.precision"
                /></el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="批次管理"><el-switch v-model="form.batchControl" /></el-form-item>
          </el-collapse-item>
        </el-collapse>
        <el-form-item label="主要供应商"
          ><el-input v-model="form.supplierName" maxlength="200" @input="form.supplierId = null"
        /></el-form-item>
        <el-form-item label="业务备注"
          ><el-input v-model="form.remark" type="textarea" maxlength="1000" :rows="2"
        /></el-form-item>
      </el-form>

      <!-- 右侧固定预览（dev-20261024-004） -->
      <aside class="master-preview" data-test="master-preview">
        <div class="preview-title">预览</div>
        <div class="preview-block">
          <div class="preview-label">最终名称</div>
          <div class="preview-value">{{ previewName || '待填写材料/型号/颜色' }}</div>
          <div v-if="form.nameManual" class="preview-tag">手动命名</div>
        </div>
        <div class="preview-block">
          <div class="preview-label">规格</div>
          <div class="preview-value">{{ specificationPreview || '填写属性后自动生成' }}</div>
        </div>
        <div class="preview-block">
          <div class="preview-label">分类 / 材质</div>
          <div class="preview-sub">{{ configuration?.path?.[0]?.categoryName || '未归类' }}</div>
          <div class="preview-sub">{{ materialLabel || '材质未选' }}</div>
        </div>
        <div class="preview-note">预览规则与后端保存同源；保存后以此为准。</div>
      </aside>
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
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { materialMasterApi } from '@/api/inventory/materialMaster'
import { materialApi } from '@/api/inventory/material'
import type { InventoryMaterial } from '@/types/inventory/material'
import type { SysTag } from '@/types/system/tag'
import type {
  MaterialDictionaries,
  MaterialMasterCategory,
  MaterialMasterConfiguration,
  MaterialMasterForm,
} from '@/types/inventory/materialMaster'
import MaterialAttributeFields from './MaterialAttributeFields.vue'
import MaterialSizeEditor from './MaterialSizeEditor.vue'
import { mergeLegacyThickness, parseMaterialSize } from './materialSize'
import {
  isCommonAttribute,
  attributeVisible,
  changeMasterCategory,
  effectiveMasterName,
  emptyMasterForm,
  previewMasterName,
  previewMasterSpecification,
  pruneAttributes,
} from './materialMasterModel'

const props = defineProps<{
  modelValue: boolean
  materialId?: number | null
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
const form = reactive<MaterialMasterForm>(emptyMasterForm())
const categories = ref<MaterialMasterCategory[]>([])
const dictionaries = ref<MaterialDictionaries>({})
const configuration = ref<MaterialMasterConfiguration>()
const tags = ref<SysTag[]>([])
const loading = ref(false)
const configurationLoading = ref(false)
const saving = ref(false)
const sizeValid = ref(true)
/** 口径由服务端按「填了克重→GRAMMAGE，有厚度→THICKNESS，否则 SIZE」判定，这里只回显。 */
const specBasisLabel = computed(() => {
  if (form.grammage) return '克重（GRAMMAGE）'
  const parsed = parseMaterialSize(form.size)
  return parsed?.thickness ? '厚度（THICKNESS）' : '仅尺寸（SIZE）'
})
const loadError = ref('')
const expanded = ref<string[]>([])
let session = 0
let categoryRequest = 0
const leafCategories = computed(() => categories.value.filter((c) => c.selectable))
const materialOptions = computed(() =>
  (dictionaries.value.material_meta || []).filter((o) =>
    configuration.value?.schema.meta_options.includes(o.code)
  )
)
const specificationPreview = computed(() =>
  previewMasterSpecification(configuration.value?.schema, form.attributeJson, { widthSize: form.size })
)
const previewName = computed(() => effectiveMasterName(form, dictionaries.value))
const materialLabel = computed(
  () =>
    dictionaries.value.material_meta?.find((o) => o.code === form.materialMeta)?.label ||
    form.materialMeta ||
    ''
)
const commonKeys = computed(() =>
  (configuration.value?.schema.fields || [])
    .filter((f) => f.key !== 'thickness_mm' && isCommonAttribute(f))
    .map((f) => f.key)
)
const lowKeys = computed(() =>
  (configuration.value?.schema.fields || [])
    .filter((f) => f.key !== 'thickness_mm' && !isCommonAttribute(f))
    .map((f) => f.key)
)

function onNameInput() {
  form.nameManual = true
}
function restoreAutoName() {
  form.nameManual = false
  form.materialName = previewMasterName(form, dictionaries.value)
}

// 未手动命名时随材料/型号/颜色/品牌实时同步（dev-20261024-004）
watch(
  () => [form.materialMeta, form.model, form.color, form.brand, form.nameManual] as const,
  () => {
    if (!form.nameManual) form.materialName = previewMasterName(form, dictionaries.value)
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
  configurationLoading.value = true
  form.categoryId = id
  form.attributeJson = {}
  if (hadAttributes) ElMessage.info('已按新分类清理不适用的属性，请重新选择该分类的属性')
  try {
    const response = await materialMasterApi.configuration(id)
    if (request !== categoryRequest || currentSession !== session) return
    if (!response.data) throw Error('品类模板加载失败')
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
  const currentSession = ++session
  categoryRequest++
  loading.value = true
  loadError.value = ''
  configuration.value = undefined
  configurationLoading.value = false
  Object.assign(form, emptyMasterForm(), {
    materialId: undefined,
    materialCode: undefined,
    supplierId: null,
    defaultWarehouseId: null,
    defaultLocationId: null,
  })
  expanded.value = []
  try {
    const [categoryResponse, dictResponse, tagResponse, detailResponse] = await Promise.all([
      materialMasterApi.categories(),
      materialMasterApi.dictionaries(),
      materialApi.getTags(),
      props.materialId ? materialMasterApi.detail(props.materialId) : Promise.resolve(null),
    ])
    if (currentSession !== session) return
    categories.value = categoryResponse.data || []
    dictionaries.value = dictResponse.data || {}
    tags.value = tagResponse.data || []
    if (detailResponse?.data) {
      const detail = detailResponse.data
      const material = detail.material
      for (const key of Object.keys(form) as (keyof MaterialMasterForm)[]) {
        if (key in material)
          Object.assign(form, { [key]: material[key as keyof typeof material] ?? form[key] })
      }
      form.materialId = material.materialId
      form.materialCode = material.materialCode
      form.materialName = material.materialName || ''
      form.nameManual =
        !!material.materialName &&
        material.materialName !== previewMasterName(form, dictionaries.value)
      form.attributeJson = detail.attributes
      form.size = mergeLegacyThickness(form.size, detail.attributes.thickness_mm)
      form.tagIds = detail.tags.flatMap((tag) => (tag.tagId == null ? [] : [tag.tagId]))
      configuration.value = detail.configuration ?? undefined
      if (!detail.configuration) form.categoryId = null
    } else if (props.presetData) {
      if (props.presetData.materialName) {
        form.materialName = props.presetData.materialName
        form.nameManual = true
      }
      form.unit = props.presetData.unit || ''
      form.supplierName = props.presetData.supplierName || ''
    }
  } catch (error) {
    if (currentSession === session)
      loadError.value = error instanceof Error ? error.message : '物料数据加载失败，请关闭后重试'
  } finally {
    if (currentSession === session) loading.value = false
  }
}

async function save() {
  if (!sizeValid.value) return void ElMessage.warning('请先修正尺寸')
  if (form.grammage && parseMaterialSize(form.size)?.thickness)
    return void ElMessage.warning('克重与厚度只能选一种口径，请清掉尺寸里的厚度')
  if (
    saving.value ||
    !configuration.value ||
    loadError.value ||
    loading.value ||
    configurationLoading.value
  )
    return
  const finalName = effectiveMasterName(form, dictionaries.value).trim()
  if (!finalName) return void ElMessage.warning('请填写名称，或先选择材料/型号/颜色自动生成')
  if (!form.unit) return void ElMessage.warning('请选择基本单位')
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
    const payload = {
      ...form,
      materialName: finalName,
      attributeJson: pruneAttributes(configuration.value.schema, form.attributeJson),
    } as MaterialMasterForm
    if (parseMaterialSize(form.size)) delete payload.attributeJson.thickness_mm
    delete payload.nameManual
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
  () => [props.modelValue, props.materialId] as const,
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
  flex: 1 1 auto;
  min-width: 0;
}
.master-preview {
  flex: 0 0 240px;
  position: sticky;
  top: 0;
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
  font-size: 11px;
  color: #c0c4cc;
}
.field-hint {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.field-hint.manual {
  color: #e6a23c;
}
</style>
