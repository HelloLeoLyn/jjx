<template>
  <el-card class="col-note" shadow="never">
    <template #header><span style="font-weight:600">工艺参数 / 工程备注</span></template>
    <el-input v-model="note" type="textarea" :rows="3"
      placeholder="填写工艺参数/材料规格/丝印要求/模切尺寸等"
      maxlength="2000" show-word-limit />
    <el-button type="primary" size="small" style="margin-top:8px" @click="$emit('save')" :loading="saving">💾 保存工艺参数</el-button>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 工艺参数卡（dev-20260811-008 组件化）
 * 图纸上传已挪到 SampleInfoCard，此处仅保留工艺参数
 */
const props = defineProps<{
  modelValue: string
  saving: boolean
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', v: string): void
  (e: 'save'): void
}>()

const note = computed({
  get: () => props.modelValue,
  set: (v: string) => emit('update:modelValue', v),
})
</script>

<style scoped>
.col-note {
  flex: 1.2;
  min-width: 0;
}
</style>
