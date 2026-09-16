<template>
  <el-drawer
    :model-value="modelValue"
    title="工序组件效果预览"
    size="560px"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <template v-if="process">
      <el-alert
        title="这里填写的内容仅用于预览，不会保存到标准工序或业务单据。"
        type="info"
        :closable="false"
        show-icon
      />

      <el-form class="preview-form" label-position="top">
        <el-form-item label="预览场景">
          <el-radio-group v-model="scene">
            <el-radio-button value="single">单工序</el-radio-button>
            <el-radio-button value="composite">复合工序中的子工序</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="作业说明（显示在工序图标下方）">
          <el-input
            v-model="workInstruction"
            clearable
            maxlength="80"
            show-word-limit
            placeholder="如：线路外形、冲窗口灯孔"
          />
        </el-form-item>
        <el-form-item label="数字下标（独立字段，不再读取 description）">
          <el-input-number v-model="indexNumber" :min="0" :max="999" controls-position="right" />
        </el-form-item>
        <el-form-item
          :label="scene === 'single' ? '工序备注（整道单工序）' : '父级备注（整道复合工序）'"
        >
          <el-input
            v-model="operationRemark"
            clearable
            maxlength="100"
            show-word-limit
            placeholder="如：一车二模"
          />
        </el-form-item>
      </el-form>

      <div class="preview-title">新版工艺组件 Demo</div>
      <ProcessOperation :items="previewItems" :remark="operationRemark" />

      <div class="preview-notes">
        <span>标准工序：{{ process.processName }}</span>
        <span>作业说明配置：{{ process.hasWorkInstruction === 1 ? '已开启' : '未开启' }}</span>
      </div>
    </template>
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import ProcessOperation from '@/components/ProcessOperation/index.vue'
import type { ProcessOperationItem } from '@/components/ProcessOperation/types'
import type { StandardProcessItem } from '@/types/product/standardProcess'

const props = defineProps<{
  modelValue: boolean
  process: StandardProcessItem | null
}>()

const emit = defineEmits<{
  (event: 'update:modelValue', value: boolean): void
}>()

const scene = ref<'single' | 'composite'>('single')
const indexNumber = ref<number | null>(4)
const workInstruction = ref('线路外形')
const operationRemark = ref('一车一模')

const previewItems = computed<ProcessOperationItem[]>(() => {
  if (!props.process) return []
  const items: ProcessOperationItem[] = [
    {
      key: props.process.processId,
      icon: props.process.icon,
      processName: props.process.processName,
      indexNumber: indexNumber.value,
      workInstruction: workInstruction.value,
    },
  ]
  if (scene.value === 'composite') {
    items.push({ key: 'secondary', processName: '其他标准子工序', workInstruction: '子工序说明' })
  }
  return items
})

watch(
  () => [props.modelValue, props.process?.processId] as const,
  ([visible]) => {
    if (!visible) return
    scene.value = 'single'
    indexNumber.value = 4
    workInstruction.value = '线路外形'
    operationRemark.value = '一车一模'
  }
)
</script>

<style scoped>
.preview-form {
  margin-top: 20px;
}

.preview-title {
  margin: 8px 0 12px;
  color: var(--el-text-color-primary);
  font-size: 14px;
  font-weight: 600;
}

.preview-notes {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  margin-top: 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
