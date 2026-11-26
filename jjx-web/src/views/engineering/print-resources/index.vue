<template>
  <div class="print-resources-page">
    <el-card shadow="never">
      <template #header>
        <div class="header">
          <span>印刷资源 · 网框与网版</span>
          <span class="tip">按框号检索 → 绷网/制版/放行/洗版，操作留痕可追溯</span>
        </div>
      </template>
      <ScreenWorkPanel />
    </el-card>
  </div>
</template>

<script setup lang="ts">
/** 印刷资源独立页面（dev-20261125-013）：网框与网版作业入口。 */
import ScreenWorkPanel from '../resource/components/ScreenWorkPanel.vue'

defineOptions({ name: 'PrintResources' })
</script>

<style scoped>
.print-resources-page { padding: 16px; }
.header { display: flex; align-items: baseline; gap: 12px; }
.tip { font-size: 12px; color: var(--el-text-color-secondary); }
</style>
