// src/directives/index.ts
import type { App } from 'vue'
import { useUserStore } from '@/store/modules/user'

// 权限指令
const hasPermi = {
  mounted(el: HTMLElement, binding: any) {
    const { value } = binding
    const all_permission = '*:*:*'
    const all_permission_short = '*' // 后端 superadmin 新版通配（2026-08-10）

    const userStore = useUserStore()
    const permissions = userStore.getPermissions

    if (value && value instanceof Array && value.length > 0) {
      const hasPermissions = permissions.some((permission: string) => {
        return all_permission === permission || all_permission_short === permission || value.includes(permission)
      })

      if (!hasPermissions && el.parentNode) {
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
const hasRole = {
  mounted(el: HTMLElement, binding: any) {
    const { value } = binding
    const super_admin = 'admin'

    const userStore = useUserStore()
    const roles = userStore.getRoles

    if (value && value instanceof Array && value.length > 0) {
      const hasRole = roles.some((role: string) => {
        return super_admin === role || value.includes(role)
      })

      if (!hasRole && el.parentNode) {
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

// 注册指令
export function setupDirectives(app: App) {
  app.directive('hasPermi', hasPermi)
  app.directive('hasRole', hasRole)
}

export { hasPermi, hasRole }
