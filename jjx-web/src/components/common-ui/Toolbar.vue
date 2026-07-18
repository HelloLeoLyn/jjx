<!-- src/components/common/Toolbar.vue -->
<template>
  <el-card class="toolbar-card" shadow="never">
    <div class="toolbar-container">
      <div class="toolbar-left">
        <template v-for="btn in buttons" :key="btn.key">
          <el-button
            v-if="checkPermission(btn)"
            :type="btn.type || 'primary'"
            :plain="btn.plain"
            :disabled="btn.disabled"
            :icon="btn.icon"
            :loading="btn.loading"
            @click="handleClick(btn.key)"
          >
            {{ btn.label }}
          </el-button>
        </template>
        <slot name="left" />
      </div>

      <div class="toolbar-right">
        <slot name="right">
          <el-tooltip v-if="showRefresh" content="刷新" placement="top">
            <el-button :icon="Refresh" circle @click="handleRefresh" />
          </el-tooltip>
        </slot>
      </div>
    </div>

    <div class="batch-bar">
      <div class="batch-info">
        已选择 <span class="batch-count">{{ selectedCount }}</span> 项
      </div>
      <div class="batch-actions">
        <slot name="batch-actions" />
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { Refresh } from '@element-plus/icons-vue'
import { usePermission } from '@/composables/usePermission'
import type { ToolbarOptions } from '@/components/common-ui/type'
interface Props {
  buttons?: ToolbarOptions[]
  showRefresh?: boolean
  showBatchBar?: boolean
  selectedCount?: number
}

interface Emits {
  (e: 'click', key: string): void
  (e: 'refresh'): void
}

const props = withDefaults(defineProps<Props>(), {
  buttons: () => [],
  showRefresh: true,
  showBatchBar: false,
  selectedCount: 0,
})

const emit = defineEmits<Emits>()

const { hasPermission, hasAllPermissions, hasRole } = usePermission()

const checkPermission = (btn: ToolbarOptions): boolean => {
  if (!btn.permission && !btn.permissions && !btn.role) return true
  if (btn.permission && !hasPermission(btn.permission)) return false
  if (btn.permissions && !hasAllPermissions(btn.permissions)) return false
  if (btn.role && !hasRole(btn.role)) return false
  return true
}

const handleClick = (key: string) => {
  const btn = props.buttons.find((b) => b.key === key)
  if (btn?.onClick) {
    btn.onClick()
  } else {
    emit('click', key)
  }
}

const handleRefresh = () => {
  emit('refresh')
}
</script>

<style scoped>
.toolbar-card {
  margin-bottom: 16px;
}

.toolbar-container {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar-left {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-right {
  display: flex;
  gap: 8px;
}

.batch-bar {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #ebeef5;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.batch-info {
  font-size: 14px;
  color: #606266;
}

.batch-count {
  color: #409eff;
  font-weight: bold;
  margin: 0 4px;
}

.batch-actions {
  display: flex;
  gap: 12px;
}
</style>
