<template>
  <el-dialog
    v-model="visible"
    title="物料分类与属性模板"
    width="1000px"
    append-to-body
    :close-on-click-modal="false"
  >
    <MaterialMasterSettingsPanel
      :initial-tab="initialTab"
      :focus-section="focusSection"
      @success="emit('success')"
    />
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 物料分类与属性模板弹窗（dev-20261028-010 退回修复）：
 * 内容抽成 MaterialMasterSettingsPanel，弹窗与独立页面共用同一份面板，弹窗功能不退化。
 */
import { computed } from 'vue'
import MaterialMasterSettingsPanel from './MaterialMasterSettingsPanel.vue'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    initialTab?: 'categories' | 'template'
    focusSection?: 'template' | 'naming'
  }>(),
  { initialTab: 'categories', focusSection: 'template' }
)
const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
</script>
