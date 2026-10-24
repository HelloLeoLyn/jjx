<template>
  <el-dialog
    v-model="visible"
    :title="materialId ? '编辑物料' : '新增物料'"
    width="880px"
    append-to-body
    :close-on-click-modal="false"
  >
    <el-skeleton v-if="loading" :rows="8" animated />
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" />
    <el-form v-if="!loading" ref="formRef" :model="form" label-width="120px">
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
      <el-form-item v-if="form.materialCode" label="物料编码">{{ form.materialCode }}</el-form-item>
      <el-form-item label="原名称" required
        ><el-input v-model="form.originalName" maxlength="200" placeholder="物料真实名称，保留原貌"
      /></el-form-item>
      <el-form-item label="原规格"
        ><el-input
          v-model="form.originalSpecification"
          maxlength="500"
          type="textarea"
          :rows="2"
          placeholder="来源规格，按原文保留"
      /></el-form-item>
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
      <el-form-item label="尺寸（mm）"
        ><el-input
          v-model="form.size"
          maxlength="50"
          placeholder="宽或宽×长，例如914、300×305；厚度填下方属性"
      /></el-form-item>
      <el-form-item label="档案标记">
        <el-checkbox v-model="form.isCustomerSupplied">客供专用</el-checkbox
        ><el-checkbox v-model="form.isSample">样品专用</el-checkbox>
      </el-form-item>
      <template v-if="configuration">
        <el-divider content-position="left"
          >{{ configuration.category.categoryName }}属性</el-divider
        >
        <MaterialAttributeFields
          v-model="form.attributeJson"
          :schema="configuration.schema"
          :required-only="true"
        />
        <el-collapse v-model="expanded">
          <el-collapse-item title="选填属性" name="attributes">
            <MaterialAttributeFields
              v-model="form.attributeJson"
              :schema="configuration.schema"
              :required-only="false"
            />
          </el-collapse-item>
        </el-collapse>
        <el-form-item label="规格预览"
          ><span>{{ specificationPreview || '填写属性后自动生成' }}</span></el-form-item
        >
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
import {
  attributeVisible,
  changeMasterCategory,
  emptyMasterForm,
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
  previewMasterSpecification(configuration.value?.schema, form.attributeJson)
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
  configuration.value = undefined
  configurationLoading.value = true
  form.categoryId = id
  form.attributeJson = {}
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
      form.originalName =
        detail.source?.originalName ?? material.originalName ?? material.materialName ?? ''
      form.originalSpecification =
        detail.source?.originalSpecification ?? material.originalSpecification ?? ''
      form.attributeJson = detail.attributes
      form.tagIds = detail.tags.flatMap((tag) => (tag.tagId == null ? [] : [tag.tagId]))
      configuration.value = detail.configuration ?? undefined
      if (!detail.configuration) form.categoryId = null
    } else if (props.presetData) {
      form.originalName = props.presetData.materialName || ''
      form.originalSpecification = props.presetData.specification || ''
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
  if (
    saving.value ||
    !configuration.value ||
    loadError.value ||
    loading.value ||
    configurationLoading.value
  )
    return
  if (!form.originalName.trim()) return void ElMessage.warning('请填写原名称')
  if (!form.unit) return void ElMessage.warning('请选择基本单位')
  const missing = configuration.value.schema.fields.find(
    (f) =>
      f.required &&
      attributeVisible(f, form.attributeJson) &&
      (form.attributeJson[f.key] == null ||
        form.attributeJson[f.key] === '' ||
        (Array.isArray(form.attributeJson[f.key]) &&
          !(form.attributeJson[f.key] as string[]).length))
  )
  if (missing) return void ElMessage.warning(`请填写${missing.label}`)
  saving.value = true
  try {
    const response = await materialMasterApi.save({
      ...form,
      attributeJson: pruneAttributes(configuration.value.schema, form.attributeJson),
    })
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
</style>
