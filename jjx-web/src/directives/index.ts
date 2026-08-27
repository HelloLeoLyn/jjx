// src/directives/index.ts
import type { App } from 'vue'
import { useUserStore } from '@/store/modules/user'

// 权限指令（仅用于普通元素；组件根节点非元素时指令不生效，改用 hasPermi() 函数 + v-if）
const hasPermiDirective = {
  mounted(el: HTMLElement, binding: any) {
    const { value } = binding

    if (value && value instanceof Array && value.length > 0) {
      if (!hasPermi(value) && el.parentNode) {
        el.parentNode.removeChild(el)
      }
    } else {
      console.warn(
        '权限指令需要传入权限数组，如 v-hasPermi="[\'system:user:add\']"',
      )
      if (el.parentNode) {
        el.parentNode.removeChild(el)
      }
    }
  },
}

// 角色权限指令
const hasRoleDirective = {
  mounted(el: HTMLElement, binding: any) {
    const { value } = binding

    if (value && value instanceof Array && value.length > 0) {
      if (!hasRole(value) && el.parentNode) {
        el.parentNode.removeChild(el)
      }
    } else {
      console.warn('角色指令需要传入角色数组，如 v-hasRole="[\'admin\']"')
      if (el.parentNode) {
        el.parentNode.removeChild(el)
      }
    }
  },
}

// 2026-08-18：权限判断函数（供 v-if 使用）——v-hasPermi 指令不能用于组件根节点
// （如 el-dropdown-item 根节点非元素，指令不生效并报 Vue 警告，权限控制会失效）
export function hasPermi(perms: string | string[]): boolean {
  const userStore = useUserStore()
  const permissions = userStore.getPermissions
  if (!perms) return false
  const list = Array.isArray(perms) ? perms : [perms]
  if (list.length === 0) return false
  return permissions.some((p: string) => p === '*:*:*' || p === '*' || list.includes(p))
}

export function hasRole(roles: string | string[]): boolean {
  const userStore = useUserStore()
  const roleList = userStore.getRoles
  if (!roles) return false
  const list = Array.isArray(roles) ? roles : [roles]
  if (list.length === 0) return false
  return roleList.some((r: string) => r === 'admin' || list.includes(r))
}

// 注册指令
export function setupDirectives(app: App) {
  app.directive('hasPermi', hasPermiDirective)
  app.directive('hasRole', hasRoleDirective)
}
