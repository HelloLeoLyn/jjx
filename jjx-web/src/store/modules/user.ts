// src/store/modules/user.ts
import { defineStore } from 'pinia'
import { authApi } from '@/api/auth'
import type { LoginForm, UserState, LoginResponse, LoginUser } from '@/types/system'

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    token: localStorage.getItem('token') || '',
    userInfo: null,
    roles: [],
    permissions: [],
    sidebarCollapsed: false, // 侧边栏折叠状态
    isLogin: false, // 登录状态
  }),

  getters: {
    // 是否已登录
    isLoggedIn: (state) => !!state.token,

    // 用户ID
    userId: (state) => state.userInfo?.userId,

    // 用户名
    userName: (state) => state.userInfo?.userName || '',

    // 昵称
    nickName: (state) => state.userInfo?.nickName || '',

    // 头像
    avatar: (state) => state.userInfo?.avatar || '',

    // 部门ID
    deptId: (state) => state.userInfo?.deptId,

    // 角色列表
    getRoles: (state) => state.roles,

    // 权限列表
    getPermissions: (state) => state.permissions,

    // 判断是否有权限
    hasPermission: (state) => (permission: string) => {
      if (!permission) return true
      if (state.permissions.includes('*') || state.permissions.includes('*:*:*')) return true
      return state.permissions.includes(permission)
    },

    // 判断是否有任一权限
    hasAnyPermission: (state) => (permissions: string[]) => {
      if (state.permissions.includes('*') || state.permissions.includes('*:*:*')) return true
      return permissions.some((p) => state.permissions.includes(p))
    },

    // 判断是否有所有权限
    hasAllPermissions: (state) => (permissions: string[]) => {
      if (state.permissions.includes('*') || state.permissions.includes('*:*:*')) return true
      return permissions.every((p) => state.permissions.includes(p))
    },
  },

  actions: {
    /**
     * 设置Token
     */
    setToken(token: string) {
      this.token = token
      localStorage.setItem('token', token)
    },

    /**
     * 设置用户信息
     */
    setUserInfo(userInfo: LoginUser) {
      this.userInfo = userInfo
      localStorage.setItem('userInfo', JSON.stringify(userInfo))
    },

    /**
     * 设置角色
     */
    setRoles(roles: string[]) {
      this.roles = roles
    },
    toggleSidebar() {
      this.sidebarCollapsed = !this.sidebarCollapsed
      localStorage.setItem('sidebarCollapsed', String(this.sidebarCollapsed))
    },
    setPermissions(permissions: string[]) {
      this.permissions = permissions
    },

    async login(loginForm: LoginForm): Promise<LoginResponse> {
      try {
        const res = await authApi.login(loginForm)

        if (res.code === 200 && res.data) {
          const { token, userInfo, roles, permissions, isLogin } = res.data

          this.setToken(token)
          this.setUserInfo(userInfo)
          this.isLogin = isLogin

          // 优先使用登录响应中的权限
          if (permissions && Array.isArray(permissions) && permissions.length > 0) {
            this.setPermissions(permissions)
          }
          // 其次检查userInfo中是否有权限
          else if (
            userInfo?.permissions &&
            Array.isArray(userInfo.permissions) &&
            userInfo.permissions.length > 0
          ) {
            this.setPermissions(userInfo.permissions)
          }
          // 如果都没有，设置默认权限或空数组
          else {
            console.warn('登录响应中没有权限信息，使用默认权限')
            this.setPermissions(['dashboard:view'])
          }

          if (roles) {
            this.setRoles(roles)
          }
          return { token, userInfo, roles, permissions, isLogin }
        }
        return Promise.reject(new Error('登录失败'))
      } catch (error) {
        console.error('登录失败:', error)
        return Promise.reject(error)
      }
    },

    /**
     * 获取用户信息
     */
    async getUserInfo(): Promise<LoginUser> {
      try {
        const res = await authApi.getUserInfo()

        if (res.code === 200 && res.data) {
          const userInfo = res.data.userInfo

          // 保存用户信息
          this.setUserInfo(userInfo)

          // DEV-1018：刷新后从 /sessions/current 恢复权限（LoginVO 含 permissions），
          // 避免 permission.ts 恢复失败后兑底 '*' 导致权限指令全部放行
          if (res.data.permissions && Array.isArray(res.data.permissions) && res.data.permissions.length > 0) {
            this.setPermissions(res.data.permissions)
          } else if (userInfo?.permissions && Array.isArray(userInfo.permissions) && userInfo.permissions.length > 0) {
            this.setPermissions(userInfo.permissions)
          }

          return userInfo
        }

        return Promise.reject(new Error('获取用户信息失败'))
      } catch (error) {
        console.error('获取用户信息失败:', error)
        throw error
      }
    },

    /**
     * 登出
     */
    async logout(): Promise<void> {
      try {
        await authApi.logout()
      } catch (error) {
        console.error('登出失败:', error)
      } finally {
        this.resetToken()
        // 重置权限系统状态
        import('@/permission')
          .then(({ resetPermissionSystem }) => {
            resetPermissionSystem()
          })
          .catch(() => {
            console.warn('无法导入resetPermissionSystem，可能权限系统未初始化')
          })
      }
    },

    /**
     * 重置Token
     */
    resetToken(): void {
      this.token = ''
      this.userInfo = null
      this.roles = []
      this.permissions = []
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    },
  },
})
