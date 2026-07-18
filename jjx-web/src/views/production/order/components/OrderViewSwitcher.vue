<template>
  <div class="view-switcher">
    <el-radio-group v-model="currentView" @change="handleViewChange">
      <el-radio-button value="plan">计划视图</el-radio-button>
      <el-radio-button value="work_order">工单视图</el-radio-button>
      <el-radio-button value="all">全部视图</el-radio-button>
      <el-radio-button value="gantt">甘特图</el-radio-button>
    </el-radio-group>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'

interface Props {
  viewType: 'plan' | 'work_order' | 'all' | 'gantt'
}

interface Emits {
  (e: 'change', value: 'plan' | 'work_order' | 'all' | 'gantt'): void
}

const props = defineProps<Props>()
const emit = defineEmits<Emits>()

// 当前视图
const currentView = ref<Props['viewType']>(props.viewType)

// 监听props变化
watch(
  () => props.viewType,
  (newValue) => {
    currentView.value = newValue
  }
)

// 处理视图切换
const handleViewChange = (value: string | number | boolean | undefined) => {
  emit('change', value as 'plan' | 'work_order' | 'all' | 'gantt')
}
</script>

<style scoped>
.view-switcher {
  margin-bottom: 20px;
  display: flex;
  justify-content: center;
}
</style>
