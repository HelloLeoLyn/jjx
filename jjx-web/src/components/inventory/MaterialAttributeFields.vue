<template>
  <div class="attribute-container">
    <div class="attribute-grid">
      <el-form-item
        v-for="field in fields"
        :key="field.key"
        :label="field.label"
        :required="field.required"
        class="attribute-field"
        :class="{ 'attribute-field--wide': isWideField(field) }"
        :data-attribute-key="field.key"
      >
        <el-select
          v-if="field.type === 'enum' || field.type === 'multienum'"
          :model-value="values[field.key]"
          :multiple="field.type === 'multienum'"
          :collapse-tags="field.type === 'multienum'"
          :collapse-tags-tooltip="field.type === 'multienum'"
          :max-collapse-tags="2"
          clearable
          filterable
          style="width: 100%"
          @update:model-value="setValue(field.key, $event)"
        >
          <el-option
            v-for="option in field.options"
            :key="option.code"
            :label="option.label"
            :value="option.code"
          />
        </el-select>
        <el-select
          v-else-if="field.type === 'boolean'"
          :model-value="values[field.key]"
          clearable
          placeholder="未确认可留空"
          style="width: 100%"
          @update:model-value="setValue(field.key, $event)"
        >
          <el-option :label="field.true_label || '是'" :value="true" />
          <el-option :label="field.false_label || '否'" :value="false" />
        </el-select>
        <div v-else-if="field.type === 'integer' || field.type === 'decimal'" class="numeric-field">
          <el-input-number
            :model-value="
              typeof values[field.key] === 'number' ? (values[field.key] as number) : undefined
            "
            :min="field.min ?? undefined"
            :max="field.max ?? undefined"
            :precision="field.type === 'integer' ? 0 : (field.precision ?? 4)"
            @update:model-value="setValue(field.key, $event ?? null)"
          />
          <span>{{ field.unit }}</span>
        </div>
        <el-input
          v-else
          :model-value="typeof values[field.key] === 'string' ? (values[field.key] as string) : ''"
          maxlength="200"
          clearable
          @update:model-value="setValue(field.key, $event)"
        />
      </el-form-item>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AttributeValue, MaterialAttributeField, MaterialAttributeSchema } from '@/types/inventory/materialMaster'
import { attributeVisible, pruneAttributes } from './materialMasterModel'

const props = defineProps<{
  schema: MaterialAttributeSchema
  modelValue: Record<string, AttributeValue>
  requiredOnly?: boolean
  /** 只渲染这些 key（用于「常用属性 / 低频属性」分组，dev-20261024-004） */
  keys?: string[]
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, AttributeValue>): void
}>()
const values = computed(() => props.modelValue)
const fields = computed(() =>
  props.schema.fields.filter(
    (f) =>
      attributeVisible(f, values.value) &&
      // 注意：Vue 对未传的 Boolean 属性会补成 false，所以只能按“显式 true 才过滤必填”处理（dev-20261030-014）
      (!props.requiredOnly || f.required === true) &&
      (!props.keys || props.keys.includes(f.key))
  )
)
// 模板目前只有 string 类型，按实际长内容扩展，不新增模板字段或改变属性值。
function isWideField(field: MaterialAttributeField): boolean {
  const value = values.value[field.key]
  return field.type === 'multienum' ||
    (field.type === 'string' && typeof value === 'string' && (value.length > 32 || value.includes('\n')))
}
function setValue(key: string, value: AttributeValue) {
  emit('update:modelValue', pruneAttributes(props.schema, { ...values.value, [key]: value }))
}
</script>

<style scoped>
.attribute-container {
  container: material-attributes / inline-size;
  width: 100%;
  min-width: 0;
}
.attribute-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  column-gap: 16px;
}
.attribute-field {
  min-width: 0;
}
.attribute-field--wide {
  grid-column: 1 / -1;
}
.attribute-field :deep(.el-form-item__content) {
  min-width: 0;
}
@container material-attributes (min-width: 640px) {
  .attribute-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
.numeric-field {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
}
.numeric-field :deep(.el-input-number) {
  flex: 1;
  width: 100%;
  min-width: 0;
}
.numeric-field span {
  flex-shrink: 0;
  white-space: nowrap;
}
</style>
