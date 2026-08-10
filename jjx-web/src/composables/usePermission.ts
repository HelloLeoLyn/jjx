// src/composables/usePermission.ts
import { computed } from 'vue'
import { useUserStore } from '@/store/modules/user'

export type Permission = string

export function usePermission() {
  const userStore = useUserStore()

  // 超级管理员通配：后端 superadmin 返回 ['*']（2026-08-10 起，兼容旧 '*:*:*'）
  const isSuper = (permissions: string[]) =>
    permissions.includes('*') || permissions.includes('*:*:*')

  const hasPermission = (permission: Permission): boolean => {
    const permissions = userStore.permissions
    return isSuper(permissions) || permissions.includes(permission)
  }

  const hasAnyPermission = (permissions: Permission[]): boolean => {
    return permissions.some((p) => hasPermission(p))
  }

  const hasAllPermissions = (permissions: Permission[]): boolean => {
    return permissions.every((p) => hasPermission(p))
  }

  const hasRole = (role: string): boolean => {
    const roles = userStore.roles
    return roles.includes(role) || roles.includes('admin')
  }

  return {
    hasPermission,
    hasAnyPermission,
    hasAllPermissions,
    hasRole,
  }
}
