// src/composables/usePermission.ts
import { computed } from 'vue'
import { useUserStore } from '@/store/modules/user'

export type Permission = string

export function usePermission() {
  const userStore = useUserStore()

  const hasPermission = (permission: Permission): boolean => {
    const permissions = userStore.permissions
    return permissions.includes(permission) || permissions.includes('*:*:*')
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
