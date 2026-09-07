<template>
  <div class="quick-actions">
    <el-button
      v-for="action in visibleActions"
      :key="action.path"
      :type="action.type"
      @click="router.push(action.path)"
    >
      <el-icon v-if="resolveIcon(action.icon)"
        ><component :is="resolveIcon(action.icon)"
      /></el-icon>
      {{ action.label }}
    </el-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'

export interface QuickAction {
  label: string
  icon?: string
  path: string
  perms?: string[]
  type?: 'primary' | 'success' | 'warning' | 'danger' | 'info'
}

const props = defineProps<{ actions: QuickAction[] }>()
const router = useRouter()
const userStore = useUserStore()

const visibleActions = computed(() =>
  props.actions.filter((action) => {
    if (!action.perms?.length) return true
    const perms = userStore.getPermissions
    if (!perms.length || perms.includes('*') || perms.includes('*:*:*')) return true
    return action.perms.some((permission) => perms.includes(permission))
  })
)

function resolveIcon(name?: string) {
  return name ? (ElementPlusIconsVue as Record<string, unknown>)[name] : undefined
}
</script>

<style scoped>
.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.quick-actions :deep(.el-button) {
  margin-left: 0;
}
</style>
