<template>
  <div class="at-select">
    <el-select v-if="showCategory || categoryOnly" :model-value="categoryValue" size="small" clearable
      :placeholder="attachmentFields.category.label" :style="{ width: `${attachmentFields.category.width}px` }"
      :disabled="disabled" @update:model-value="changeCategory">
      <el-option v-for="c in selectableCategories" :key="c.key" :label="c.label" :value="c.key" />
    </el-select>
    <el-select v-if="!categoryOnly" :model-value="modelValue || ''" size="small" clearable
      :placeholder="attachmentFields.documentType.label"
      :style="{ width: `${attachmentFields.documentType.width}px` }" :disabled="disabled"
      @update:model-value="changeType">
      <el-option-group v-for="g in secondaryGroupsFor(categoryValue)" :key="g.key" :label="g.label">
        <el-option v-for="t in g.options" :key="t.item_key" :label="t.label" :value="t.item_key" />
      </el-option-group>
    </el-select>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { AttachmentDocumentTypeItem } from '@/api/system/attachmentRef'
import { useAttachmentDocTypes } from './useAttachmentDocTypes'
import { attachmentFields } from './attachmentFields'
const props = withDefaults(defineProps<{
  modelValue?: string | null
  documentTypes: AttachmentDocumentTypeItem[]
  sourceType: string
  showCategory?: boolean
  /** 只渲染一级（分类）下拉：用于把一级/二级拆到两列（dev-20261015-033） */
  categoryOnly?: boolean
  disabled?: boolean
  category?: string
}>(), { showCategory: false, categoryOnly: false, disabled: false })
const emit = defineEmits<{
  'update:modelValue': [value: string | null]
  'update:category': [value: string]
}>()
const { categoryKeyOfType, selectableCategories, secondaryGroupsFor } = useAttachmentDocTypes(
  computed(() => props.documentTypes), () => props.sourceType)
const localCategory = ref('')
const categoryValue = computed(() => props.category ?? (localCategory.value || categoryKeyOfType(props.modelValue)))
watch(() => props.modelValue, value => {
  if (value && localCategory.value && categoryKeyOfType(value) !== localCategory.value) localCategory.value = ''
})
function changeCategory(value: string | null) {
  const next = value || ''
  localCategory.value = next
  emit('update:category', next)
  if (props.modelValue && categoryKeyOfType(props.modelValue) !== next) emit('update:modelValue', null)
}
function changeType(value: string | null) { emit('update:modelValue', value || null) }
</script>
<style scoped>
.at-select { display: inline-flex; align-items: center; gap: 8px; }
</style>
