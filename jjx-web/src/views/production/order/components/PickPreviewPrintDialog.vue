<template>
  <el-dialog
    :model-value="modelValue"
    title="领料单 - 打印预览"
    width="900px"
    append-to-body
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <iframe
      ref="printFrame"
      class="pick-print-frame"
      title="领料单 A4 打印预览"
      :srcdoc="printHtml"
      @load="ready = true"
    />
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">关闭</el-button>
      <el-button type="primary" :disabled="!ready" @click="handlePrint">打印</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { buildPickPreviewHtml, type PickPreviewPrintData } from './pickPreviewPrint'

const props = defineProps<PickPreviewPrintData & { modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean] }>()
const printFrame = ref<HTMLIFrameElement>()
const ready = ref(false)
const printHtml = computed(() => buildPickPreviewHtml(props))
watch(printHtml, () => {
  ready.value = false
})

function handlePrint() {
  if (!ready.value || !printFrame.value?.contentWindow) return
  printFrame.value.contentWindow.focus()
  printFrame.value.contentWindow.print()
}
</script>

<style scoped>
.pick-print-frame {
  display: block;
  width: 100%;
  height: 65vh;
  border: 1px solid #dcdfe6;
}
</style>
