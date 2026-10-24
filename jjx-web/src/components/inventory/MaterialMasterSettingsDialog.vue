<template>
  <el-dialog
    v-model="visible"
    title="物料分类与属性模板"
    width="1000px"
    append-to-body
    :close-on-click-modal="false"
  >
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-tabs v-model="tab">
      <el-tab-pane label="分类" name="categories">
        <el-form label-width="110px">
          <el-form-item label="维护分类">
            <el-select
              v-model="categoryEditId"
              clearable
              placeholder="留空新增分类"
              @change="editCategory"
            >
              <el-option
                v-for="c in editableCategories"
                :key="c.categoryId"
                :label="c.pathName"
                :value="c.categoryId"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="父分类" required
            ><el-select v-model="categoryForm.parentId" filterable>
              <el-option
                v-for="c in parentCategories"
                :key="c.categoryId"
                :label="c.pathName"
                :value="c.categoryId"
              /> </el-select
          ></el-form-item>
          <el-form-item label="分类编码" required
            ><el-input
              v-model="categoryForm.categoryCode"
              :disabled="!!categoryEditId"
              placeholder="大写字母、数字和下划线"
          /></el-form-item>
          <el-form-item label="分类名称" required
            ><el-input v-model="categoryForm.categoryName" maxlength="100"
          /></el-form-item>
          <el-form-item label="排序"
            ><el-input-number v-model="categoryForm.sortOrder"
          /></el-form-item>
          <el-form-item label="启用"
            ><el-switch
              v-model="categoryForm.status"
              :active-value="CommonStatusEnum.NORMAL.value"
              :inactive-value="CommonStatusEnum.DISABLED.value"
          /></el-form-item>
          <el-form-item
            ><el-button type="primary" :loading="busy" @click="saveCategory"
              >保存分类</el-button
            ></el-form-item
          >
        </el-form>
      </el-tab-pane>
      <el-tab-pane label="属性模板" name="template">
        <el-form label-width="110px">
          <el-form-item label="末级品类" required
            ><el-select v-model="templateCategoryId" filterable @change="loadTemplate">
              <el-option
                v-for="c in leaves"
                :key="c.categoryId"
                :label="c.pathName"
                :value="c.categoryId"
              /> </el-select
          ></el-form-item>
          <template v-if="templateCategoryId">
            <el-form-item label="模板编码"><el-input v-model="templateCode" /></el-form-item>
            <el-form-item label="模板名称"><el-input v-model="templateName" /></el-form-item>
            <el-form-item label="启用"
              ><el-switch
                v-model="templateStatus"
                :active-value="CommonStatusEnum.NORMAL.value"
                :inactive-value="CommonStatusEnum.DISABLED.value"
            /></el-form-item>
            <el-form-item label="允许材质"
              ><el-select v-model="schema.meta_options" multiple filterable>
                <el-option
                  v-for="o in dictionaries.material_meta"
                  :key="o.code"
                  :label="o.label"
                  :value="o.code"
                /> </el-select
            ></el-form-item>
            <el-form-item label="材质必填"
              ><el-checkbox v-model="schema.meta_required"
            /></el-form-item>
            <el-form-item label="默认材质"
              ><el-select v-model="schema.default_meta" clearable>
                <el-option
                  v-for="o in allowedMeta"
                  :key="o.code"
                  :label="o.label"
                  :value="o.code"
                /> </el-select
            ></el-form-item>
            <el-form-item label="默认形态"
              ><el-select v-model="schema.default_form" clearable>
                <el-option
                  v-for="o in dictionaries.form"
                  :key="o.code"
                  :label="o.label"
                  :value="o.code"
                /> </el-select
            ></el-form-item>
            <el-form-item label="默认单位" required
              ><el-select v-model="schema.default_unit">
                <el-option
                  v-for="o in dictionaries.unit"
                  :key="o.code"
                  :label="o.label"
                  :value="o.code"
                /> </el-select
            ></el-form-item>
            <el-table :data="schema.fields" border>
              <el-table-column label="属性编码" width="150"
                ><template #default="{ row }"><el-input v-model="row.key" /></template
              ></el-table-column>
              <el-table-column label="名称" width="130"
                ><template #default="{ row }"><el-input v-model="row.label" /></template
              ></el-table-column>
              <el-table-column label="类型" width="125"
                ><template #default="{ row }"
                  ><el-select v-model="row.type">
                    <el-option
                      v-for="o in MaterialAttributeTypeEnum.items"
                      :key="o.value"
                      :value="o.value"
                      :label="o.label"
                    /> </el-select></template
              ></el-table-column>
              <el-table-column label="单位" width="90"
                ><template #default="{ row }"><el-input v-model="row.unit" /></template
              ></el-table-column>
              <el-table-column label="必填" width="70"
                ><template #default="{ row }"><el-checkbox v-model="row.required" /></template
              ></el-table-column>
              <el-table-column label="常用" width="70">
                <template #default="{ row }"><el-checkbox :model-value="isCommonAttribute(row)" @update:model-value="value => row.common = !!value" /></template>
              </el-table-column>
              <el-table-column label="参与规格" width="90">
                <template #default="{ row }"><el-checkbox :model-value="participatesInSpecification(row)" @update:model-value="value => row.in_spec = !!value" /></template>
              </el-table-column>
              <el-table-column label="规格排序" width="110">
                <template #default="{ row }"><el-input-number v-model="row.spec_order" :min="0" :max="10000" :controls="false" style="width: 85px" /></template>
              </el-table-column>
              <el-table-column label="显示条件" width="200">
                <template #default="{ row }">
                  <el-select :model-value="conditionKey(row)" clearable placeholder="始终显示" @update:model-value="value => setConditionKey(row, value)">
                    <el-option v-for="field in schema.fields.filter(f => f.key !== row.key && !Object.keys(f.visible_when || {}).length)"
                      :key="field.key" :value="field.key" :label="field.label" />
                  </el-select>
                  <el-select v-if="conditionKey(row) && conditionOptions(row).length" :model-value="conditionValue(row)"
                    placeholder="满足此选项时显示" @update:model-value="value => setConditionValue(row, value)">
                    <el-option v-for="option in conditionOptions(row)" :key="option.code" :value="option.code" :label="option.label" />
                  </el-select>
                  <el-input v-else-if="conditionKey(row)" :model-value="conditionValue(row)" placeholder="满足此值时显示"
                    @update:model-value="value => setConditionValue(row, value)" />
                </template>
              </el-table-column>
              <el-table-column label="是／否显示文字" width="150">
                <template #default="{ row }">
                  <template v-if="row.type === 'boolean'">
                    <el-input v-model="row.true_label" placeholder="是，例如带点" maxlength="50" />
                    <el-input v-model="row.false_label" placeholder="否，例如无点" maxlength="50" />
                  </template>
                </template>
              </el-table-column>
              <el-table-column label="选项（每行编码=名称）"
                ><template #default="{ row }">
                  <el-input
                    v-if="row.type === 'enum' || row.type === 'multienum'"
                    :model-value="optionText(row)"
                    type="textarea"
                    :rows="2"
                    @change="setOptions(row, $event)"
                  />
                  <template v-else-if="row.type === 'integer' || row.type === 'decimal'">
                    <el-input-number
                      v-model="row.min"
                      placeholder="下限"
                      :controls="false"
                      style="width: 90px"
                    />
                    <el-input-number
                      v-model="row.max"
                      placeholder="上限"
                      :controls="false"
                      style="width: 90px"
                    />
                  </template>
                  <span v-else>—</span>
                </template></el-table-column
              >
              <el-table-column width="65"
                ><template #default="{ $index }"
                  ><el-button link type="danger" @click="schema.fields.splice($index, 1)"
                    >删除</el-button
                  ></template
                ></el-table-column
              >
            </el-table>
            <el-button style="margin-top: 12px" @click="addField">增加属性</el-button>
            <el-button style="margin-top: 12px" @click="addCommonFields">补充本品类常用属性</el-button>
            <el-alert
              title="已有物料的模板可新增无默认值的可选属性、调整显示设置；删除、改类型或单位等需另行处理。常用属性补入草稿后，检查并保存才生效。"
              type="info"
              :closable="false"
              style="margin-top: 12px"
            />
            <el-button
              type="primary"
              :loading="busy"
              :disabled="templateLoading"
              style="margin-top: 12px"
              @click="saveTemplate"
              >保存模板</el-button
            >
            <el-button type="danger" plain :disabled="busy" @click="removeTemplate"
              >移除未使用模板</el-button
            >
          </template>
        </el-form>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CommonStatusEnum } from '@/enums/common/StatusEnum'
import { MaterialAttributeTypeEnum } from '@/enums/inventory/MaterialAttributeEnum'
import { materialMasterApi } from '@/api/inventory/materialMaster'
import { isCommonAttribute } from './materialMasterModel'
import { addRecommendedAttributes } from './materialAttributePresets'
import { participatesInSpecification } from '../bomSpecification'
import type {
  MaterialAttributeField,
  MaterialAttributeSchema,
  MaterialDictionaries,
  MaterialMasterCategory,
} from '@/types/inventory/materialMaster'
const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void; (e: 'success'): void }>()
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
const tab = ref('categories'),
  error = ref(''),
  busy = ref(false)
const categories = ref<MaterialMasterCategory[]>([]),
  dictionaries = ref<MaterialDictionaries>({})
const categoryEditId = ref<number>(),
  templateCategoryId = ref<number>()
const categoryForm = reactive({
  parentId: undefined as number | undefined,
  categoryCode: '',
  categoryName: '',
  sortOrder: 0,
  status: CommonStatusEnum.NORMAL.value as number,
})
const templateCode = ref(''),
  templateName = ref('')
const templateStatus = ref<number>(CommonStatusEnum.NORMAL.value)
const templateLoading = ref(false)
const schema = ref<MaterialAttributeSchema>({ version: 1, meta_options: [], fields: [] })
const editableCategories = computed(() => categories.value.filter((c) => c.categoryLevel > 1))
const leaves = computed(() =>
  categories.value.filter((c) => !categories.value.some((child) => child.parentId === c.categoryId))
)
const parentCategories = computed(() =>
  categories.value.filter(
    (c) => c.categoryLevel < 3 && !c.selectable && c.categoryId !== categoryEditId.value
  )
)
const allowedMeta = computed(() =>
  (dictionaries.value.material_meta || []).filter((o) => schema.value.meta_options.includes(o.code))
)
let templateRequest = 0
async function refresh() {
  const [categoryResponse, dictionaryResponse] = await Promise.all([
    materialMasterApi.categories(true),
    materialMasterApi.dictionaries(),
  ])
  categories.value = categoryResponse.data || []
  dictionaries.value = dictionaryResponse.data || {}
}
watch(
  () => props.modelValue,
  async (open) => {
    if (!open) {
      templateRequest++
      return
    }
    error.value = ''
    try {
      await refresh()
    } catch (e) {
      error.value = e instanceof Error ? e.message : '加载配置失败'
    }
  }
)
function editCategory(id?: number) {
  const category = categories.value.find((c) => c.categoryId === id)
  Object.assign(categoryForm, {
    parentId: category?.parentId ?? undefined,
    categoryCode: category?.categoryCode || '',
    categoryName: category?.categoryName || '',
    sortOrder: category?.sortOrder ?? 0,
    status: category?.status ?? CommonStatusEnum.NORMAL.value,
  })
}
async function saveCategory() {
  busy.value = true
  error.value = ''
  try {
    await materialMasterApi.saveCategory({ ...categoryForm, categoryId: categoryEditId.value })
    await refresh()
    ElMessage.success('分类已保存')
    emit('success')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '分类保存失败'
  } finally {
    busy.value = false
  }
}
async function loadTemplate(id: number) {
  const request = ++templateRequest
  templateLoading.value = true
  schema.value = { version: 1, meta_options: [], fields: [] }
  templateCode.value = ''
  templateName.value = ''
  const category = categories.value.find((c) => c.categoryId === id)
  try {
    const response = await materialMasterApi.template(id)
    if (request !== templateRequest) return
    if (!response.data) {
      templateName.value = `${category?.categoryName || ''}属性模板`
      templateStatus.value = CommonStatusEnum.NORMAL.value
      return
    }
    schema.value = response.data.schema
    templateCode.value = response.data.template.templateCode
    templateName.value = response.data.template.templateName
    templateStatus.value = response.data.template.status
  } catch (e) {
    if (request === templateRequest) error.value = e instanceof Error ? e.message : '模板加载失败'
  } finally {
    if (request === templateRequest) templateLoading.value = false
  }
}
function addField() {
  schema.value.fields.push({ key: '', label: '', type: 'string', required: false, common: true, in_spec: true, spec_order: schema.value.fields.length + 1 })
}

function addCommonFields() {
  const category = categories.value.find(c => c.categoryId === templateCategoryId.value)
  const added = addRecommendedAttributes(schema.value, category?.categoryCode || '')
  ElMessage.success(`已补充 ${added} 项可选属性，请检查后保存模板`)
}
const conditionKey = (field: MaterialAttributeField) => Object.keys(field.visible_when || {})[0] || ''
const conditionValue = (field: MaterialAttributeField) => field.visible_when?.[conditionKey(field)] || ''
function setConditionKey(field: MaterialAttributeField, key?: string) {
  field.visible_when = key ? { [key]: '' } : undefined
}
function setConditionValue(field: MaterialAttributeField, value: string) {
  const key = conditionKey(field)
  if (key) field.visible_when = { ...field.visible_when, [key]: value }
}
function conditionOptions(field: MaterialAttributeField) {
  const dependency = schema.value.fields.find(f => f.key === conditionKey(field))
  if (dependency?.type === 'boolean') return [{ code: 'true', label: dependency.true_label || '是' }, { code: 'false', label: dependency.false_label || '否' }]
  return dependency?.options || []
}
function optionText(field: MaterialAttributeField) {
  return (field.options || []).map((o) => `${o.code}=${o.label}`).join('\n')
}
function setOptions(field: MaterialAttributeField, value: string) {
  field.options = value
    .split('\n')
    .filter((line) => line.trim())
    .map((line) => {
      const index = line.indexOf('=')
      return {
        code: (index < 0 ? line : line.slice(0, index)).trim(),
        label: (index < 0 ? line : line.slice(index + 1)).trim(),
      }
    })
}
async function saveTemplate() {
  if (templateLoading.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await materialMasterApi.saveTemplate({
      categoryId: templateCategoryId.value,
      templateCode: templateCode.value,
      templateName: templateName.value,
      attributeSchema: schema.value,
      status: templateStatus.value,
    })
    await refresh()
    ElMessage.success('模板已保存')
    emit('success')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '模板保存失败'
  } finally {
    busy.value = false
  }
}
async function removeTemplate() {
  if (!templateCategoryId.value) return
  try {
    await ElMessageBox.confirm(
      '移除未使用模板后可在此分类下继续建子分类，已有物料引用的模板无法移除。',
      '移除属性模板'
    )
  } catch {
    return
  }
  busy.value = true
  error.value = ''
  try {
    await materialMasterApi.removeUnusedTemplate(templateCategoryId.value)
    await refresh()
    await loadTemplate(templateCategoryId.value)
    emit('success')
  } catch (e) {
    error.value = e instanceof Error ? e.message : '模板移除失败'
  } finally {
    busy.value = false
  }
}
</script>
