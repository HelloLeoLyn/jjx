<template>
  <el-dialog
    :title="`字典项管理 - ${dictName}`"
    v-model="visible"
    width="800px"
    append-to-body
    :before-close="handleCancel"
  >
    <DictItemPanel :active="visible" :dict-code="dictCode" :readonly="readonly" />
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import DictItemPanel from './DictItemPanel.vue'

const props = withDefaults(defineProps<{
  visible: boolean
  dictCode: string
  dictName: string
  readonly?: boolean
}>(), { visible: false, dictCode: '', dictName: '', readonly: false })
const emit = defineEmits<{ 'update:visible': [value: boolean] }>()
const visible = computed({
  get: () => props.visible,
  set: (value) => emit('update:visible', value),
})
const handleCancel = () => { visible.value = false }
</script>
