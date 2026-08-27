<template>
  <div v-if="branch" class="env-badge" :class="envClass">
    {{ branch }}
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const branch = import.meta.env.VITE_APP_BRANCH as string | undefined

const envClass = computed(() => {
  if (!branch) return ''
  const b = branch.toLowerCase()
  if (b === 'dev' || b === 'develop') return 'env-dev'
  if (b === 'main' || b === 'master') return 'env-prod'
  if (b.includes('release') || b.includes('rc')) return 'env-rc'
  if (b.includes('feature') || b.includes('ai/')) return 'env-feat'
  return 'env-other'
})
</script>

<style scoped>
.env-badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.3px;
  white-space: nowrap;
  cursor: default;
  user-select: none;
}

.env-dev {
  background: #ecf5ff;
  color: #409eff;
  border: 1px solid #d9ecff;
}

.env-prod {
  background: #f0f9eb;
  color: #67c23a;
  border: 1px solid #e1f3d8;
}

.env-rc {
  background: #fdf6ec;
  color: #e6a23c;
  border: 1px solid #faecd8;
}

.env-feat {
  background: #f4f0ff;
  color: #7c3aed;
  border: 1px solid #e8e0ff;
}

.env-other {
  background: #f4f4f5;
  color: #909399;
  border: 1px solid #e9e9eb;
}
</style>
