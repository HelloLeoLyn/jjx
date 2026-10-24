<template>
  <el-dialog v-model="visible" title="重新生成物料信息" width="860px" append-to-body>
    <el-skeleton v-if="loading" :rows="6" animated />
    <template v-else>
      <el-descriptions title="参考（只读）" :column="2" border size="small">
        <el-descriptions-item label="原名称">{{ refs.name }}</el-descriptions-item>
        <el-descriptions-item label="原规格">{{ refs.specification }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">
          <div class="remark-block">{{ original?.remark || '-' }}</div>
        </el-descriptions-item>
      </el-descriptions>

      <el-divider content-position="left">可编辑属性</el-divider>

      <el-form ref="formRef" :model="form" :rules="rules" label-width="92px">
        <el-row :gutter="20">
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
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="大类">
              <el-input :model-value="materialTypeText" readonly placeholder="由品类带出" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item
              label="材料"
              prop="materialGrade"
              :required="isRequired('materialGrade')"
              :error="fieldErrors.materialGrade"
            >
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
              <el-input v-model="form.model" placeholder="如 PET125、3M5604" @input="clearFieldError('model')" />
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
              <el-input v-model="form.brand" placeholder="选填，如 3M" @input="clearFieldError('brand')" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="别名">
              <el-input v-model="form.searchKeywords" placeholder="选填：常用叫法/旧料号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="尺寸" prop="size" :error="sizeError">
              <el-input
                v-model="form.size"
                clearable
                placeholder="如 300 / 300×305 / 300×1.0 / 0.3m×1.0，缺省 mm"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item
          label="规格"
          prop="specification"
          :required="isRequired('specification')"
          :error="fieldErrors.specification"
        >
          <el-input
            v-model="form.specification"
            type="textarea"
            :rows="2"
            placeholder="如 0.38mm 幼砂加硬 / 120g 白色 / 离型力 10-15g"
            @input="clearFieldError('specification')"
          />
          <div class="regen-hint">
            填工艺 / 材质描述：厚度或克重、砂面（中砂/幼砂/雾面）、离型力、胶系、耐温、封装等；
            尺寸（宽×长/厚）请填上面的「尺寸」框，不要在这里重复写宽×长。
          </div>
        </el-form-item>

        <el-form-item label="名称" prop="materialName">
          <el-input v-model="form.materialName" placeholder="默认按规则自动生成，可手改" @input="handleNameInput">
            <template #append>
              <el-button @click="restoreOriginalName">恢复原名称</el-button>
              <el-button @click="regenerateName">重新生成</el-button>
            </template>
          </el-input>
          <div class="regen-hint" :class="{ manual: manualNamed }">
            {{ manualNamed ? '手动命名：已停止自动同步（点「重新生成」按规则重算，或点「恢复原名称」回到原名称）' : '自动命名：随材料/型号/颜色/品牌实时更新' }}
          </div>
        </el-form-item>
      </el-form>

      <el-divider content-position="left">预览</el-divider>
      <el-descriptions :column="1" border size="small">
        <el-descriptions-item label="生成名称">
          {{ autoName || '-' }}
          <el-tag v-if="manualNamed" size="small" type="warning" class="regen-tag">手动命名</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="尺寸串">{{ sizePreview || '-' }}</el-descriptions-item>
      </el-descriptions>
    </template>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { materialApi } from '@/api/inventory/material'
import { useUserStore } from '@/store/modules/user'
import {
  MATERIAL_COLOR_OPTIONS,
  MATERIAL_FIELD_LABELS,
  MATERIAL_GRADE_OPTIONS,
  findMissingRequiredFields,
  loadMaterialCategoryOptions,
  requiredMaterialFields,
  type MaterialCategoryOption,
} from './materialMasterFields'
import {
  appendRegenerateTrace,
  buildAutoName,
  buildSizePreview,
  readOriginalRefs,
  type OriginalRefs,
} from './materialRegenerate'
import type { InventoryMaterial, MaterialUpdateDTO } from '@/types/inventory/material'

interface Props {
  modelValue: boolean
  materialId?: number | null
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  materialId: null,
})

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

const userStore = useUserStore()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const formRef = ref()
const loading = ref(false)
const submitting = ref(false)
const manualNamed = ref(false)
const original = ref<InventoryMaterial | null>(null)
const refs = ref<OriginalRefs>({ name: '-', specification: '-' })
const categoryOptions = ref<MaterialCategoryOption[]>([])
const fieldErrors = reactive<Record<string, string>>({})

const form = reactive({
  categoryId: null as number | null,
  materialGrade: '',
  model: '',
  color: '',
  brand: '',
  searchKeywords: '',
  size: '',
  specification: '',
  materialName: '',
})

const rules = {
  materialName: [{ required: true, message: '请填写物料名称', trigger: 'blur' }],
}

const categoryCode = computed(
  () =>
    categoryOptions.value.find((item) => item.categoryId === form.categoryId)?.categoryCode || ''
)
const materialTypeText = computed(() => categoryOptions.value.find((item) => item.categoryId === form.categoryId)?.categoryRootName || '未归类')

const autoName = computed(() =>
  buildAutoName({
    materialGrade: form.materialGrade,
    model: form.model,
    color: form.color,
    brand: form.brand,
  })
)
const sizePreview = computed(() => buildSizePreview(form.size, categoryCode.value))
const sizeError = computed(() => {
  const preview = sizePreview.value
  return preview === '' || preview.includes('mm') ? '' : preview
})

const isRequired = (key: string) => requiredMaterialFields(categoryCode.value).includes(key)

const clearFieldError = (key: string) => {
  delete fieldErrors[key]
}

watch(autoName, (value) => {
  if (!manualNamed.value) form.materialName = value
})

const handleNameInput = () => {
  manualNamed.value = true
}

/** 按规则重算名称，并恢复自动同步。 */
const regenerateName = () => {
  manualNamed.value = false
  form.materialName = autoName.value
}

/** 回到参考区原名称（remark 的【原名称】优先，否则当前名称），保持手动命名态。 */
const restoreOriginalName = () => {
  const original = refs.value.name === '-' ? '' : refs.value.name
  manualNamed.value = true
  form.materialName = original
}

const handleCategoryChange = () => {
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
}

const loadCategoryOptions = async () => {
  categoryOptions.value = await loadMaterialCategoryOptions()
}

const loadDetail = async (materialId: number) => {
  loading.value = true
  try {
    const res = await materialApi.getInfo(String(materialId))
    const data = res.data
    if (!data) {
      ElMessage.error('未找到物料信息')
      visible.value = false
      return
    }
    original.value = data
    form.categoryId = data.categoryId ?? null
    form.materialGrade = data.materialGrade || ''
    form.model = data.model || ''
    form.color = data.color || ''
    form.brand = data.brand || ''
    form.searchKeywords = data.searchKeywords || ''
    form.size = data.size || ''
    form.specification = data.specification || ''
    form.materialName = data.materialName || ''
    refs.value = readOriginalRefs(data.remark, data.materialName, data.specification)
    // 历史名称与规则不一致时默认视为手动命名，避免保存时被静默改名
    manualNamed.value = !!data.materialName && data.materialName !== autoName.value
  } catch (error) {
    console.error('加载物料信息失败:', error)
    ElMessage.error('加载物料信息失败')
    visible.value = false
  } finally {
    loading.value = false
  }
}

const validateLocal = () => {
  Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
  const missing = findMissingRequiredFields(
    {
      materialGrade: form.materialGrade,
      model: form.model,
      color: form.color,
      brand: form.brand,
      specification: form.specification,
    },
    categoryCode.value
  )
  missing.forEach((key) => {
    fieldErrors[key] = `${MATERIAL_FIELD_LABELS[key] || key}必填`
  })
  if (missing.length) {
    const firstKey = missing[0]
    ElMessage.error(`请补全：${missing.map((key) => MATERIAL_FIELD_LABELS[key] || key).join('、')}`)
    formRef.value?.scrollToField?.(firstKey)
    return false
  }
  if (sizeError.value) {
    ElMessage.error(sizeError.value)
    formRef.value?.scrollToField?.('size')
    return false
  }
  if (!form.materialName.trim()) {
    ElMessage.error('请填写物料名称')
    formRef.value?.scrollToField?.('materialName')
    return false
  }
  return true
}

const submit = () => {
  formRef.value.validate(async (valid: boolean) => {
    if (!valid) return
    if (!validateLocal()) return
    if (!original.value) return

    submitting.value = true
    try {
      const operator = userStore.nickName || userStore.userName || 'system'
      const updateData: MaterialUpdateDTO = {
        materialId: original.value.materialId,
        materialName: form.materialName.trim(),
        categoryId: form.categoryId,
        materialGrade: form.materialGrade || null,
        model: form.model || null,
        color: form.color || null,
        brand: form.brand || null,
        searchKeywords: form.searchKeywords || null,
        size: form.size.trim() || null,
        specification: form.specification || null,
        remark: appendRegenerateTrace(original.value.remark, operator),
      }
      await materialApi.update(updateData)
      ElMessage.success('已重新生成')
      visible.value = false
      emit('success')
    } catch (error) {
      console.error('保存失败:', error)
    } finally {
      submitting.value = false
    }
  })
}

watch(
  () => [props.modelValue, props.materialId] as const,
  ([isOpen, id]) => {
    if (!isOpen) return
    Object.keys(fieldErrors).forEach((key) => delete fieldErrors[key])
    manualNamed.value = false
    original.value = null
    loadCategoryOptions()
    if (id) loadDetail(id)
  },
  { immediate: true }
)
</script>

<style scoped>
.regen-hint {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}

.regen-hint.manual {
  color: #e6a23c;
}

.regen-tag {
  margin-left: 8px;
}

.remark-block {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.5;
}
</style>
