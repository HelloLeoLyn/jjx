<template>
  <div class="filter-bar">
    <el-input
      v-model="localFilter.keyword"
      placeholder="搜索工单号/任务名/负责人..."
      clearable
      style="width: 260px"
      @input="onFilterChange"
      @clear="onFilterChange"
    >
      <template #prefix>
        <el-icon><Search /></el-icon>
      </template>
    </el-input>

    <el-select
      v-model="localFilter.assignee"
      placeholder="负责人"
      clearable
      style="width: 130px"
      @change="onFilterChange"
    >
      <el-option label="张三" value="张三" />
      <el-option label="李四" value="李四" />
      <el-option label="王五" value="王五" />
      <el-option label="赵六" value="赵六" />
      <el-option label="陈七" value="陈七" />
    </el-select>

    <el-select
      v-model="localFilter.priority"
      placeholder="优先级"
      clearable
      style="width: 120px"
      @change="onFilterChange"
    >
      <el-option label="🔥 紧急" value="urgent" />
      <el-option label="⏫ 高" value="high" />
      <el-option label="➖ 普通" value="normal" />
      <el-option label="⬇️ 低" value="low" />
    </el-select>

    <el-button @click="onReset">
      重置
    </el-button>

    <div class="filter-stats">
      <el-tag type="info" effect="plain" size="small">
        共 {{ totalCards }} 张卡片
      </el-tag>
    </div>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'
import type { BoardFilter } from '@/types/board'

const props = defineProps<{
  filter: BoardFilter
  totalCards: number
}>()

const emit = defineEmits<{
  change: [filter: BoardFilter]
  reset: []
}>()

const localFilter = reactive<BoardFilter>({
  keyword: '',
  assignee: '',
  priority: undefined,
})

watch(() => props.filter, (f) => {
  Object.assign(localFilter, f)
}, { immediate: true })

function onFilterChange() {
  emit('change', { ...localFilter })
}

function onReset() {
  localFilter.keyword = ''
  localFilter.assignee = ''
  localFilter.priority = undefined
  emit('reset')
}
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  flex-wrap: wrap;
}

.filter-stats {
  margin-left: auto;
}
</style>
