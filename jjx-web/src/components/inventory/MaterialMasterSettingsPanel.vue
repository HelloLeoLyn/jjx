<template>
  <div class="material-settings-panel">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-tabs v-model="tab" :class="{ 'single-section': singleSection }">
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
          <template v-if="focusSection !== 'naming'">
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
                  :label="`${o.label}/${o.code}`"
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
              <el-table-column label="精度" width="90">
                <template #default="{ row }">
                  <el-input-number
                    v-model="row.precision"
                    :min="0"
                    :max="8"
                    :precision="0"
                    :controls="false"
                    :disabled="!isNumericType(row.type)"
                    style="width: 72px"
                    placeholder="-"
                  />
                </template>
              </el-table-column>
              <el-table-column label="默认值" width="150">
                <template #default="{ row }">
                  <el-select
                    v-if="row.type === 'boolean'"
                    v-model="row.default"
                    clearable
                    style="width: 100%"
                  >
                    <el-option label="是" :value="true" />
                    <el-option label="否" :value="false" />
                  </el-select>
                  <el-input-number
                    v-else-if="isNumericType(row.type)"
                    v-model="row.default"
                    :controls="false"
                    style="width: 100%"
                  />
                  <el-select
                    v-else-if="row.type === 'enum' || row.type === 'multienum'"
                    v-model="row.default"
                    clearable
                    filterable
                    style="width: 100%"
                  >
                    <el-option
                      v-for="o in row.options || []"
                      :key="o.code"
                      :label="o.label"
                      :value="o.code"
                    />
                  </el-select>
                  <el-input v-else v-model="row.default" clearable />
                </template>
              </el-table-column>
              <el-table-column label="单位" width="120"
                ><template #default="{ row }"
                  ><el-select
                    v-model="row.unit"
                    clearable
                    filterable
                    allow-create
                    default-first-option
                    placeholder="无量纲"
                    style="width: 100%"
                    ><el-option v-for="unit in UNIT_OPTIONS" :key="unit" :label="unit" :value="unit" /></el-select></template
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
              <el-table-column label="顺序" width="110">
                <template #default="{ $index }">
                  <el-button
                    link
                    :disabled="$index === 0"
                    aria-label="上移属性"
                    @click="moveField($index, -1)"
                    >上移</el-button
                  >
                  <el-button
                    link
                    :disabled="$index === schema.fields.length - 1"
                    aria-label="下移属性"
                    @click="moveField($index, 1)"
                    >下移</el-button
                  >
                </template>
              </el-table-column>
            </el-table>
            <el-button style="margin-top: 12px" @click="addField">增加属性</el-button>
            <el-button style="margin-top: 12px" @click="addCommonFields">补充本品类常用属性</el-button>
          </template>
            <el-divider v-if="focusSection !== 'template'" content-position="left">命名规则（勾选参与命名的字段，按勾选顺序拼装）</el-divider>
            <div v-show="focusSection !== 'template'" class="naming-rule">
              <div class="naming-tokens">
                <el-checkbox
                  v-for="token in namingTokens"
                  :key="token.key"
                  :model-value="namePattern.fields.includes(token.key)"
                  @update:model-value="(value) => toggleNameField(token.key, !!value)"
                  >{{ token.label }}</el-checkbox
                >
              </div>
              <div class="naming-actions">
                <el-button link type="primary" @click="selectAllNaming">全选</el-button>
                <el-button link @click="resetNaming">重置</el-button>
              </div>
              <div v-if="namePattern.fields.length" class="naming-order">
                <div
                  v-for="(field, index) in namePattern.fields"
                  :key="field"
                  class="naming-order-row"
                  draggable="true"
                  :class="{ dragging: dragState?.kind === 'naming' && dragState?.index === index }"
                  @dragstart="startDrag('naming', index)"
                  @dragover.prevent
                  @drop="dropAt('naming', index)"
                  @dragend="endDrag"
                >
                  <span class="naming-order-no">{{ index + 1 }}</span>
                  <span class="naming-order-label">{{ namingLabel(field) }}</span>
                  <el-input
                    :model-value="fieldSeparator(field)"
                    maxlength="1"
                    class="naming-sep-input"
                    placeholder="分隔"
                    title="该字段前的分隔符，只能 1 个字符；留空=紧贴上一个字段"
                    @update:model-value="(value: any) => setNameSeparator(field, value)"
                  />
                  <el-button link @click="setNameSeparator(field, ' ')">空格</el-button>
                  <el-button link @click="setNameSeparator(field, '-')">-</el-button>
                  <el-button link @click="setNameSeparator(field, '×')">×</el-button>
                  <el-button link @click="setNameSeparator(field, '*')">*</el-button>
                  <el-button link @click="setNameSeparator(field, '')">无</el-button>
                  <el-button
                    link
                    :icon="ArrowUp"
                    :disabled="index === 0"
                    aria-label="上移"
                    title="上移"
                    @click="moveNameField(index, -1)"
                  />
                  <el-button
                    link
                    :icon="ArrowDown"
                    :disabled="index === namePattern.fields.length - 1"
                    aria-label="下移"
                    title="下移"
                    @click="moveNameField(index, 1)"
                  />
                  <el-button link type="danger" @click="toggleNameField(field, false)">移除</el-button>
                </div>
              </div>
              <el-form-item label="分隔符">
                <el-radio-group v-model="namePattern.separator">
                  <el-radio-button label=" ">空格</el-radio-button>
                  <el-radio-button label="-">-</el-radio-button>
                  <el-radio-button label="">无</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="缺值处理">
                <el-radio-group v-model="namePattern.empty">
                  <el-radio-button label="skip">跳过</el-radio-button>
                  <el-radio-button label="placeholder">占位 —</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-button :loading="namingLoading" @click="previewNaming()">刷新效果示例</el-button>
              <span v-if="namingMessage" class="naming-message">{{ namingMessage }}</span>
              <el-divider content-position="left">效果示例（该品类最近 5 条物料）</el-divider>
              <div v-if="!namingSamples.length" class="naming-message">
                该品类暂无可预览的物料；规则仍可保存，仅对以后新建/改名生效。
              </div>
              <el-table
                v-if="namingSamples.length"
                :data="namingSamples"
                border
                size="small"
                style="margin-top: 8px"
              >
                <el-table-column label="物料编码" prop="materialCode" width="150" />
                <el-table-column label="当前名称" prop="oldName" />
                <el-table-column label="按规则生成" prop="newName" />
              </el-table>
              <el-divider />
              <el-button :loading="renameLoading" @click="previewRename">预览改名差异</el-button>
              <el-button
                type="danger"
                plain
                :disabled="!renameReport || !renameReport.changeable"
                @click="applyRename"
                >按规则批量改名（{{ renameReport?.changeable ?? 0 }} 条）</el-button
              >
              <span v-if="renameReport" class="naming-message">{{ renameReport.message }}</span>
              <el-table
                v-if="renameReport?.samples.length"
                :data="renameReport.samples"
                border
                size="small"
                max-height="260"
                style="margin-top: 8px"
              >
                <el-table-column label="物料编码" prop="materialCode" width="150" />
                <el-table-column label="当前名称" prop="oldName" />
                <el-table-column label="按规则生成" prop="newName" />
                <el-table-column label="说明" width="120">
                  <template #default="{ row }">{{ skipText(row.skipReason) }}</template>
                </el-table-column>
              </el-table>
            </div>
            <el-divider v-if="focusSection !== 'template'" content-position="left">规格模板（生成规格的字段顺序与分隔符）</el-divider>
            <div v-show="focusSection !== 'template'" class="naming-rule">
              <div class="naming-tokens">
                <el-checkbox
                  v-for="token in specTokens"
                  :key="token.key"
                  :model-value="specPattern.fields.some((item) => item.key === token.key)"
                  @update:model-value="(value) => toggleSpecField(token.key, !!value)"
                  >{{ token.label }}</el-checkbox
                >
              </div>
              <div class="naming-actions">
                <el-button link type="primary" @click="selectAllSpec">全选</el-button>
                <el-button link @click="resetSpec">重置</el-button>
              </div>
              <div v-if="specPattern.fields.length" class="naming-order">
                <div
                  v-for="(item, index) in specPattern.fields"
                  :key="item.key"
                  class="naming-order-row"
                  draggable="true"
                  :class="{ dragging: dragState?.kind === 'spec' && dragState?.index === index }"
                  @dragstart="startDrag('spec', index)"
                  @dragover.prevent
                  @drop="dropAt('spec', index)"
                  @dragend="endDrag"
                >
                  <span class="naming-order-no">{{ index + 1 }}</span>
                  <span class="naming-order-label">{{ specLabel(item.key) }}</span>
                  <el-input
                    :model-value="item.sep"
                    maxlength="1"
                    class="naming-sep-input"
                    placeholder="分隔"
                    title="该字段前的分隔符，只能 1 个字符；留空=紧贴上一个字段"
                    @update:model-value="(value: any) => setSpecSep(index, value)"
                  />
                  <el-button link @click="setSpecSep(index, '；')">；</el-button>
                  <el-button link @click="setSpecSep(index, ',')">,</el-button>
                  <el-button link @click="setSpecSep(index, '×')">×</el-button>
                  <el-button link @click="setSpecSep(index, '*')">*</el-button>
                  <el-button link @click="setSpecSep(index, '')">无</el-button>
                  <el-button
                    link
                    :icon="ArrowUp"
                    :disabled="index === 0"
                    aria-label="上移"
                    title="上移"
                    @click="moveSpecField(index, -1)"
                  />
                  <el-button
                    link
                    :icon="ArrowDown"
                    :disabled="index === specPattern.fields.length - 1"
                    aria-label="下移"
                    title="下移"
                    @click="moveSpecField(index, 1)"
                  />
                  <el-button link type="danger" @click="toggleSpecField(item.key, false)">移除</el-button>
                </div>
              </div>
              <el-form-item label="显示字段名">
                <el-switch v-model="specPattern.show_label" />
              </el-form-item>
              <el-form-item label="单位位置">
                <el-radio-group v-model="specPattern.unit_position">
                  <el-radio-button label="end">只在末尾</el-radio-button>
                  <el-radio-button label="each">每项都带</el-radio-button>
                  <el-radio-button label="none">不带</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <span class="naming-message">留空字段不输出；未配置时沿用旧的“字段:值；尺寸”拼接。</span>
              <el-divider content-position="left">效果示例（该品类最近 5 条物料）</el-divider>
              <div v-if="!specSamples.length" class="naming-message">该品类暂无可预览的物料；模板只影响以后生成。</div>
              <el-table v-if="specSamples.length" :data="specSamples" border size="small" style="margin-top: 8px">
                <el-table-column label="物料编码" prop="materialCode" width="150" />
                <el-table-column label="当前规格" prop="oldSpec" />
                <el-table-column label="按模板生成" prop="newSpec" />
              </el-table>
            </div>
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
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowUp, ArrowDown } from '@element-plus/icons-vue'
import { CommonStatusEnum } from '@/enums/common/StatusEnum'
import { MaterialAttributeTypeEnum } from '@/enums/inventory/MaterialAttributeEnum'
import { materialMasterApi } from '@/api/inventory/materialMaster'
import { isCommonAttribute } from './materialMasterModel'
import { addRecommendedAttributes } from './materialAttributePresets'
import { participatesInSpecification } from '../bomSpecification'
import type {
  MaterialAttributeField,
  MaterialAttributeSchema,
  MaterialNamingPattern,
  MaterialNamingPreview,
  MaterialRenameReport,
  MaterialDictionaries,
  MaterialMasterCategory,
} from '@/types/inventory/materialMaster'
const props = withDefaults(
  defineProps<{
    /** 独立页面使用：隐藏分类/属性模板的 tab 头，只显示 initialTab 指向的那一页（dev-20261029-022） */
    singleSection?: boolean
    initialTab?: 'categories' | 'template'
    /** 只显示模板字段或只显示命名规则 */
    focusSection?: 'template' | 'naming'
  }>(),
  { singleSection: false, initialTab: 'categories', focusSection: 'template' }
)
const emit = defineEmits<{ (e: 'success'): void }>()
const tab = ref(props.initialTab),
  error = ref(''),
  busy = ref(false)
const isNumericType = (type?: string) => type === 'decimal' || type === 'integer'
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
/** 规格模板：字段顺序 + 每字段 1 字符分隔符 + 是否显示字段名/带单位，存入 attribute_schema.spec_pattern（dev-20261029-027）。 */
const specPattern = ref<{
  fields: { key: string; sep: string }[]
  show_label: boolean
  with_unit: boolean
  unit_position: 'each' | 'end' | 'none'
}>({
  fields: [],
  show_label: false,
  with_unit: true,
  unit_position: 'end',
})
const specSamples = ref<{ materialId: number; materialCode: string; oldSpec: string | null; newSpec: string | null }[]>([])
const specLoading = ref(false)
let specTimer: ReturnType<typeof setTimeout> | undefined
async function previewSpecification(quiet = false) {
  if (!templateCategoryId.value) return
  specLoading.value = true
  try {
    const response = await materialMasterApi.specPreview(templateCategoryId.value)
    specSamples.value = response.data?.samples || []
  } catch (e) {
    if (!quiet) ElMessage.error(e instanceof Error ? e.message : '规格预览失败')
  } finally {
    specLoading.value = false
  }
}
watch(
  () => [templateCategoryId.value, JSON.stringify(specPattern.value)],
  () => {
    if (!templateCategoryId.value) return
    if (specTimer) clearTimeout(specTimer)
    specTimer = setTimeout(() => void previewSpecification(true), 300)
  },
  { immediate: true }
)

const SPEC_DIM_TOKENS = [
  { key: 'width', label: '宽' },
  { key: 'length', label: '长' },
  { key: 'thickness_mm', label: '厚' },
]
const specTokens = computed(() => {
  if (fieldOptions.value.length) return fieldOptions.value.map((o) => ({ key: o.key, label: o.label }))
  return [
    ...SPEC_DIM_TOKENS,
    ...schema.value.fields.filter((f) => f.key).map((f) => ({ key: f.key, label: f.label || f.key })),
  ]
})
function specLabel(key: string) {
  return specTokens.value.find((token) => token.key === key)?.label || key
}
function toggleSpecField(key: string, checked: boolean) {
  const rest = specPattern.value.fields.filter((item) => item.key !== key)
  specPattern.value.fields = checked ? [...rest, { key, sep: rest.length ? '；' : '' }] : rest
}
function moveSpecField(index: number, offset: -1 | 1) {
  const fields = specPattern.value.fields
  const target = index + offset
  if (target < 0 || target >= fields.length) return
  const [item] = fields.splice(index, 1)
  fields.splice(target, 0, item)
}
function setSpecSep(index: number, value: string | number) {
  const next = String(value ?? '').slice(0, 1)
  specPattern.value.fields[index] = { ...specPattern.value.fields[index], sep: next }
}

/** 命名/规格可选字段（后端 /naming/fields 提供，新增字段无需改前端） */
const fieldOptions = ref<{ key: string; label: string; group: string }[]>([])
async function loadFieldOptions(categoryId?: number) {
  try {
    const response = await materialMasterApi.fieldOptions(categoryId)
    fieldOptions.value = response.data || []
  } catch {
    fieldOptions.value = []
  }
}

/** 命名规则：勾选字段 + 顺序 + 分隔符 + 缺值处理，保存进 attribute_schema.name_pattern。 */
const namePattern = ref<MaterialNamingPattern>({ fields: [], separator: ' ', empty: 'skip' })
const namingLoading = ref(false)
const namingSamples = ref<MaterialNamingPreview['samples']>([])
const namingMessage = ref('')
const COMMON_NAMING_TOKENS = [
  { key: 'brand', label: '品牌' },
  { key: 'model', label: '型号' },
  { key: 'color', label: '颜色' },
  { key: 'meta', label: '材质' },
  { key: 'form', label: '形态' },
  { key: 'unit', label: '单位' },
  { key: 'thickness_mm', label: '厚度(mm)' },
  { key: 'width', label: '宽(mm)' },
  { key: 'length', label: '长(mm)' },
  { key: 'grammage', label: '克重' },
  { key: 'customersupplied', label: '客供' },
  { key: 'sampleonly', label: '样品' },
  { key: 'category', label: '品类' },
]
/** 属性单位白名单：与后端 MaterialAttributeSchema.UNITS 一致，避免 mm/MM/毫米 混写。 */
const UNIT_OPTIONS = ['%', 'g/m²', 'g', 'kg/m³', 'mm', 'μm', 'N', 'gf', '℃', 's']
/** 可选字段 = 公共字段 + 本品类属性字段（品类不同，可勾选的属性就不同）。 */
const namingTokens = computed(() => {
  if (fieldOptions.value.length) return fieldOptions.value.map((o) => ({ key: o.key, label: o.label }))
  return [
    ...COMMON_NAMING_TOKENS,
    ...schema.value.fields
      .filter((field) => field.key && !COMMON_NAMING_TOKENS.some((token) => token.key === field.key))
      .map((field) => ({ key: field.key, label: field.label || field.key })),
  ]
})
const namingLabel = (key: string) => namingTokens.value.find((token) => token.key === key)?.label || key
function toggleNameField(key: string, checked: boolean) {
  const rest = namePattern.value.fields.filter((field) => field !== key)
  namePattern.value.fields = checked ? [...rest, key] : rest
}
/** 命名规则：全选当前品类的全部可选项 */
function selectAllNaming() {
  namePattern.value.fields = namingTokens.value.map((token) => token.key)
}
/** 命名规则：清空选择，分隔符与缺值恢复默认 */
function resetNaming() {
  namePattern.value = { fields: [], separator: ' ', empty: 'skip', separators: {} }
}
/** 规格模板：全选当前品类的全部可选项（首项无分隔符，其余默认 ；） */
function selectAllSpec() {
  specPattern.value.fields = specTokens.value.map((token, index) => ({
    key: token.key,
    sep: index === 0 ? '' : '；',
  }))
}
/** 规格模板：清空选择并恢复默认显示设置 */
function resetSpec() {
  specPattern.value = { fields: [], show_label: false, with_unit: true, unit_position: 'end' }
}

/** 该字段当前生效的分隔符（未单配时取全局分隔符） */
function fieldSeparator(field: string): string {
  const map = namePattern.value.separators || {}
  return Object.prototype.hasOwnProperty.call(map, field)
    ? map[field]
    : (namePattern.value.separator ?? ' ')
}

/** 设置单字段分隔符：只允许 1 个字符，空串表示紧贴上一个字段 */
function setNameSeparator(field: string, value: string | number) {
  const next = String(value ?? '').slice(0, 1)
  namePattern.value.separators = { ...(namePattern.value.separators || {}), [field]: next }
}

const dragState = ref<{ kind: 'naming' | 'spec'; index: number } | null>(null)
function startDrag(kind: 'naming' | 'spec', index: number) {
  dragState.value = { kind, index }
}
function endDrag() {
  dragState.value = null
}
/** 拖拽落位：同类型列表内把 from 移到 to（dev-20261030-004） */
function dropAt(kind: 'naming' | 'spec', index: number) {
  const from = dragState.value
  dragState.value = null
  if (!from || from.kind !== kind || from.index === index) return
  const list: any[] = kind === 'naming' ? namePattern.value.fields : (specPattern.value.fields as any[])
  const [item] = list.splice(from.index, 1)
  list.splice(index, 0, item)
}

function moveNameField(index: number, offset: -1 | 1) {
  const fields = namePattern.value.fields
  const target = index + offset
  if (target < 0 || target >= fields.length) return
  const [field] = fields.splice(index, 1)
  fields.splice(target, 0, field)
}
async function previewNaming(quiet = false) {
  if (!templateCategoryId.value) return
  namingLoading.value = true
  try {
    const response = await materialMasterApi.previewNaming(templateCategoryId.value, namePattern.value)
    namingSamples.value = response.data?.samples || []
    namingMessage.value = response.data?.message || ''
  } catch (e) {
    if (!quiet) ElMessage.error(e instanceof Error ? e.message : '命名预览失败')
  } finally {
    namingLoading.value = false
  }
}

// 效果示例自动跟着规则变：改品类/勾选/排序/分隔符后 300ms 自动重算（dev-20261029-021）
let previewTimer: ReturnType<typeof setTimeout> | undefined
watch(
  () => [
    templateCategoryId.value,
    namePattern.value.fields.join(','),
    namePattern.value.separator,
    namePattern.value.empty,
  ],
  () => {
    if (!templateCategoryId.value) return
    if (previewTimer) clearTimeout(previewTimer)
    previewTimer = setTimeout(() => void previewNaming(true), 300)
  },
  { immediate: true }
)
const renameLoading = ref(false)
const renameReport = ref<MaterialRenameReport>()
const skipText = (reason: string | null) =>
  reason === 'blank' ? '规则为空，跳过' : reason === 'same' ? '已一致' : '将改名'
async function previewRename() {
  if (!templateCategoryId.value) return
  renameLoading.value = true
  try {
    const response = await materialMasterApi.renameChanges(templateCategoryId.value)
    renameReport.value = response.data ?? undefined
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '改名预演失败')
  } finally {
    renameLoading.value = false
  }
}
async function applyRename() {
  if (!templateCategoryId.value || !renameReport.value?.changeable) return
  try {
    await ElMessageBox.confirm(
      `将按当前命名规则改 ${renameReport.value.changeable} 条物料名称，逐条留痕，是否继续？`,
      '批量改名'
    )
  } catch {
    return
  }
  renameLoading.value = true
  try {
    const response = await materialMasterApi.applyRename(templateCategoryId.value)
    ElMessage.success(`已改名 ${response.data ?? 0} 条`)
    emit('success')
    renameLoading.value = false
    await previewRename()
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '批量改名失败')
    renameLoading.value = false
  }
}
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
onMounted(async () => {
  error.value = ''
  try {
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载配置失败'
  }
})
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
  namePattern.value = { fields: [], separator: ' ', empty: 'skip', separators: {} }
  void loadFieldOptions(id)
  specPattern.value = { fields: [], show_label: false, with_unit: true, unit_position: 'end' }
  namingSamples.value = []
  namingMessage.value = ''
  renameReport.value = undefined
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
    void loadFieldOptions(id)
    specPattern.value = response.data.schema.spec_pattern
      ? {
          fields: (response.data.schema.spec_pattern.fields || []).map((item: any) => ({ key: item.key, sep: item.sep || '' })),
          show_label: !!response.data.schema.spec_pattern.show_label,
          with_unit: response.data.schema.spec_pattern.with_unit !== false,
          unit_position: (response.data.schema.spec_pattern.unit_position as 'each' | 'end' | 'none') || 'end',
        }
      : { fields: [], show_label: false, with_unit: true, unit_position: 'end' }
    namePattern.value = response.data.schema.name_pattern
      ? {
          fields: [...response.data.schema.name_pattern.fields],
          separator: response.data.schema.name_pattern.separator || ' ',
          separators: { ...(response.data.schema.name_pattern.separators || {}) },
          empty: response.data.schema.name_pattern.empty === 'placeholder' ? 'placeholder' : 'skip',
        }
      : { fields: [], separator: ' ', empty: 'skip' }
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

/** 调整属性显示顺序；规格排序（spec_order）由用户在该列单独维护，不随移动自动改写。 */
function moveField(index: number, offset: -1 | 1) {
  const fields = schema.value.fields
  const target = index + offset
  if (target < 0 || target >= fields.length) return
  const [field] = fields.splice(index, 1)
  fields.splice(target, 0, field)
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
      attributeSchema: { ...schema.value, name_pattern: namePattern.value, spec_pattern: specPattern.value },
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

<style scoped>
/* 独立页面（物料分类 / 属性模板 / 命名规则）只显示自己那一页，不出现平级 tab 头 */
.single-section :deep(.el-tabs__header) {
  display: none;
}

.naming-rule { margin-top: 8px; }
.naming-tokens { display: flex; flex-wrap: wrap; gap: 4px 12px; margin-bottom: 8px; }
.naming-order { display: flex; flex-direction: column; gap: 2px; margin-bottom: 8px; }
.naming-order-row { display: flex; align-items: center; gap: 4px; }
.naming-order-label { min-width: 90px; color: var(--el-text-color-regular); }
.naming-sep-input { width: 56px; }
.naming-actions { display: flex; gap: 8px; margin: -4px 0 8px; }
.naming-order-no { min-width: 18px; text-align: right; color: var(--el-text-color-placeholder); font-size: 12px; }
.naming-order-row { cursor: grab; }
.naming-order-row.dragging { background: var(--el-color-primary-light-9); }
.naming-order-row .el-button { opacity: 0.35; }
.naming-order-row:hover .el-button { opacity: 1; }
.naming-message { margin-left: 8px; color: var(--el-text-color-secondary); font-size: 12px; }
</style>
