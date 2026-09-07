<template>
  <el-card shadow="never" class="widget-card" :class="`widget-${tone}`">
    <div class="widget-header">{{ title }}</div>
    <slot />
  </el-card>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    tone?: 'sales' | 'production' | 'admin' | 'todo'
  }>(),
  { tone: 'todo' }
)
</script>

<style scoped>
.widget-card {
  height: 100%;
  box-sizing: border-box;
  border-radius: 12px;
  border: 1px solid #e8eaef;
}
.widget-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 12px;
  font-size: 14px;
  font-weight: 600;
}
.widget-sales {
  border-top: 3px solid #409eff;
}
.widget-production {
  border-top: 3px solid #f56c6c;
}
.widget-admin {
  border-top: 3px solid #67c23a;
}
.widget-todo {
  border-top: 3px solid #909399;
}
</style>
