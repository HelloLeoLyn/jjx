<template>
  <div class="material-admin-page">
    <el-card shadow="never">
      <template #header>
        <span style="font-weight: 600">命名规则</span>
        <span class="desc">勾选参与命名的字段并按顺序拼装；预览命名与批量改名</span>
      </template>
      <MaterialMasterSettingsPanel
        initial-tab="template"
        focus-section="naming"
        @success="onSuccess"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import MaterialMasterSettingsPanel from '@/components/inventory/MaterialMasterSettingsPanel.vue'

defineOptions({ name: 'MaterialNamingRule' })

function onSuccess() {
  ElMessage.success('已保存')
}
</script>

<style scoped>
.material-admin-page { padding: 16px; }
.desc { margin-left: 8px; font-size: 12px; color: #909399; }
</style>
