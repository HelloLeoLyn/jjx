<template>
  <div class="material-spec-fields">
    <el-row :gutter="20">
      <el-col v-for="def in defs" :key="def.attrCode" :xs="24" :sm="12">
        <el-form-item :label="labelOf(def)" :required="!!def.required">
          <el-input
            v-if="def.inputType === 'number'"
            :model-value="valueOf(def).value || ''"
            clearable
            :placeholder="numberPlaceholder(def)"
            @update:model-value="(val: string) => updateValue(def.attrCode, 'value', val)"
          />
          <div v-else-if="def.inputType === 'range'" class="range-input">
            <el-input
              :model-value="valueOf(def).value || ''"
              clearable
              :placeholder="numberPlaceholder(def)"
              @update:model-value="(val: string) => updateValue(def.attrCode, 'value', val)"
            />
            <span class="range-dash">-</span>
            <el-input
              :model-value="valueOf(def).value2 || ''"
              clearable
              :placeholder="numberPlaceholder(def)"
              @update:model-value="(val: string) => updateValue(def.attrCode, 'value2', val)"
            />
          </div>
          <el-select
            v-else-if="def.inputType === 'select'"
            :model-value="valueOf(def).value || ''"
            clearable
            filterable
            placeholder="请选择"
            style="width: 100%"
            @update:model-value="(val: string) => updateValue(def.attrCode, 'value', val)"
          >
            <el-option v-for="option in def.options || []" :key="option" :label="option" :value="option" />
          </el-select>
          <el-input
            v-else
            :model-value="valueOf(def).value || ''"
            clearable
            placeholder="选填"
            @update:model-value="(val: string) => updateValue(def.attrCode, 'value', val)"
          />
        </el-form-item>
      </el-col>
    </el-row>

    <el-form-item :label="supplementLabel">
      <el-input
        :model-value="supplement"
        :maxlength="maxSupplement"
        show-word-limit
        clearable
        placeholder="选填：属性里没有的特殊说明，≤30 字"
        @update:model-value="(val: string) => emit('update:supplement', val)"
      />
    </el-form-item>

    <el-form-item label="规格预览">
      <el-input :model-value="preview" readonly placeholder="按上面属性自动生成，保存后写入规格" />
      <div class="field-hint">
        规格由属性自动组合，不再手写；尺寸填「尺寸」、颜色选「颜色」、厂牌型号填「型号」，不要重复写进属性。
      </div>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { MaterialSpecAttrDef } from '@/types/inventory/material'
import {
  MATERIAL_SPEC_MAX_SUPPLEMENT,
  composeSpecification,
  type MaterialSpecInputValue,
} from './materialMasterFields'

/**
 * 规格属性输入区（dev-20261023-022）。
 * 只负责渲染品类属性与回填取值，组合规则与预览文本由 materialMasterFields 统一提供。
 */
const props = withDefaults(
  defineProps<{
    defs: MaterialSpecAttrDef[]
    modelValue: Record<string, MaterialSpecInputValue>
    supplement: string
    required?: boolean
    supplementLabel?: string
    /** 属性全空时展示的历史规格文本（仅提示，不改写） */
    previewFallback?: string
  }>(),
  {
    defs: () => [],
    modelValue: () => ({}),
    supplement: '',
    required: false,
    supplementLabel: '规格补充',
    previewFallback: '',
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, MaterialSpecInputValue>): void
  (e: 'update:supplement', value: string): void
}>()

const maxSupplement = MATERIAL_SPEC_MAX_SUPPLEMENT

const preview = computed(
  () => composeSpecification(props.defs, props.modelValue, props.supplement) || props.previewFallback
)

const valueOf = (def: MaterialSpecAttrDef): MaterialSpecInputValue =>
  props.modelValue?.[def.attrCode] || { value: '' }

const labelOf = (def: MaterialSpecAttrDef) =>
  def.unit ? `${def.attrName}（${def.unit}）` : def.attrName

const numberPlaceholder = (def: MaterialSpecAttrDef) =>
  def.inputType === 'range'
    ? def.unit
      ? `数值，缺省 ${def.unit}`
      : '数值'
    : def.unit
      ? `如 0.38（缺省 ${def.unit}）`
      : '数值'

const updateValue = (attrCode: string, field: 'value' | 'value2', value: string) => {
  const current = props.modelValue?.[attrCode] || {}
  emit('update:modelValue', {
    ...props.modelValue,
    [attrCode]: { ...current, [field]: value },
  })
}
</script>

<style scoped>
.field-hint {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
  line-height: 1.4;
}

.range-input {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}

.range-dash {
  color: #909399;
}
</style>
