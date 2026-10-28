<template>
  <div class="material-admin-page">
    <el-card shadow="never">
      <template #header>
        <span style="font-weight: 600">属性模板</span>
        <span class="desc">按末级品类维护属性字段；precision 仅数值/整数生效（0–8），default 按 JSON 原生类型</span>
      </template>
      <MaterialMasterSettingsPanel
        initial-tab="template"
        focus-section="template"
        @success="onSuccess"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import MaterialMasterSettingsPanel from '@/components/inventory/MaterialMasterSettingsPanel.vue'

defineOptions({ name: 'MaterialAttributeTemplate' })

function onSuccess() {
  ElMessage.success('已保存')
}
</script>

<style scoped>
.material-admin-page { padding: 16px; }
.desc { margin-left: 8px; font-size: 12px; color: #909399; }
</style>
