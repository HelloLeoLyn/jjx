<template>
  <el-form-item
    v-for="field in fields"
    :key="field.key"
    :label="field.label"
    :required="field.required"
  >
    <el-select
      v-if="field.type === 'enum' || field.type === 'multienum'"
      :model-value="values[field.key]"
      :multiple="field.type === 'multienum'"
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
      @update:model-value="setValue(field.key, $event)"
    >
      <el-option label="是" :value="true" /><el-option label="否" :value="false" />
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
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { AttributeValue, MaterialAttributeSchema } from '@/types/inventory/materialMaster'
import { attributeVisible, pruneAttributes } from './materialMasterModel'

const props = defineProps<{
  schema: MaterialAttributeSchema
  modelValue: Record<string, AttributeValue>
  requiredOnly?: boolean
}>()
const emit = defineEmits<{
  (e: 'update:modelValue', value: Record<string, AttributeValue>): void
}>()
const values = computed(() => props.modelValue)
const fields = computed(() =>
  props.schema.fields.filter(
    (f) =>
      attributeVisible(f, values.value) &&
      (props.requiredOnly === undefined || f.required === props.requiredOnly)
  )
)
function setValue(key: string, value: AttributeValue) {
  emit('update:modelValue', pruneAttributes(props.schema, { ...values.value, [key]: value }))
}
</script>

<style scoped>
.numeric-field {
  display: flex;
  align-items: center;
  gap: 10px;
}
</style>
