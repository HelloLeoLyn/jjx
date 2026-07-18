// src/store/modules/user.ts
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { userApi } from '@/api/system/user'
import { authApi } from '@/api/auth'
import type { SysUser } from '@/types/system'
import type { RouteRecordRaw } from 'vue-router'
import { resetRouter } from '@/router'

export interface MenuItem {
  path: string
  name: string
  title: string
  icon?: string
  permission?: string
  external?: string
  affix?: boolean
  hidden?: boolean
  children?: MenuItem[]
  sort?: number
}

// 定义返回类型接口
interface UserStoreReturn {
  // state
  token: import('vue').Ref<string>
  userInfo: import('vue').Ref<SysUser | null>
  roles: import('vue').Ref<string[]>
  permissions: import('vue').Ref<string[]>
  accessibleRoutes: import('vue').Ref<RouteRecordRaw[]>
  menuList: import('vue').Ref<MenuItem[]>
  sidebarCollapsed: import('vue').Ref<boolean>
  // getters
  isLoggedIn: import('vue').ComputedRef<boolean>
  getUserName: import('vue').ComputedRef<string>
  getNickName: import('vue').ComputedRef<string>
  getAvatar: import('vue').ComputedRef<string>
  getRoles: import('vue').ComputedRef<string[]>
  getPermissions: import('vue').ComputedRef<string[]>
  getAccessibleRoutes: import('vue').ComputedRef<RouteRecordRaw[]>
  getMenuList: import('vue').ComputedRef<MenuItem[]>
  getSidebarCollapsed: import('vue').ComputedRef<boolean>
  // actions
  setToken: (token: string) => void
  setUserInfo: (userInfo: SysUser) => void
  setRoles: (roles: string[]) => void
  setPermissions: (permissions: string[]) => void
  setAccessibleRoutes: (routes: RouteRecordRaw[]) => void
  setMenuList: (menus: MenuItem[]) => void
  toggleSidebar: () => void
  generateMenuTree: (routes: RouteRecordRaw[]) => MenuItem[]
  loadUserPermissions: () => Promise<void>
  getUserInfo: (forceRefresh?: boolean) => Promise<SysUser>
  login: (loginForm: {
    username: string
    password: string
    captcha: string
    rememberMe: boolean
  }) => Promise<any>
  resetToken: () => void
  logout: () => Promise<void>
  updatePassword: (oldPassword: string, newPassword: string) => Promise<any>
  updateProfile: (profile: Partial<SysUser>) => Promise<any>
  updateAvatar: (avatar: string) => Promise<any>
}

// 权限加载状态标志
let permissionsLoaded = false
let isLoadingPermissions = false

export const resetPermissionsLoaded = (): void => {
  permissionsLoaded = false
}

export const isPermissionsLoaded = (): boolean => permissionsLoaded

export const setPermissionsLoaded = (loaded: boolean): void => {
  permissionsLoaded = loaded
}

export const useUserStore = defineStore('user', (): UserStoreReturn => {
  // state
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref<SysUser | null>(JSON.parse(localStorage.getItem('userInfo') || 'null'))
  const roles = ref<string[]>([])
  const permissions = ref<string[]>([])
  const accessibleRoutes = ref<RouteRecordRaw[]>([])
  const menuList = ref<MenuItem[]>([])
  const sidebarCollapsed = ref(localStorage.getItem('sidebarCollapsed') === 'true' || false)

  // getters
  const isLoggedIn = computed(() => !!token.value)
  const getUserName = computed(() => userInfo.value?.userName || '')
  const getNickName = computed(() => userInfo.value?.nickName || '')
  const getAvatar = computed(() => userInfo.value?.avatar || '')
  const getRoles = computed(() => roles.value)
  const getPermissions = computed(() => permissions.value)
  const getAccessibleRoutes = computed(() => accessibleRoutes.value)
  const getMenuList = computed(() => menuList.value)
  const getSidebarCollapsed = computed(() => sidebarCollapsed.value)

  // 生成菜单树
  function generateMenuTree(routes: RouteRecordRaw[]): MenuItem[] {
    const menus: MenuItem[] = []

    for (const route of routes) {
      if (route.meta?.hidden === true) {
        continue
      }

      if (!route.component && (!route.children || route.children.length === 0)) {
        continue
      }

      const menuItem: MenuItem = {
        path: route.path,
        name: route.name as string,
        title: (route.meta?.title as string) || '',
        icon: route.meta?.icon as string,
        permission: route.meta?.permission as string,
        external: route.meta?.external as string,
        affix: route.meta?.affix as boolean,
        hidden: route.meta?.hidden as boolean,
        sort: route.meta?.sort as number,
      }

      if (route.children && route.children.length > 0) {
        const childMenus = generateMenuTree(route.children)
        if (childMenus.length > 0) {
          menuItem.children = childMenus
        }
      }

      if (menuItem.children && menuItem.children.length === 0) {
        continue
      }

      menus.push(menuItem)
    }

    return menus.sort((a, b) => (a.sort || 0) - (b.sort || 0))
  }

  // actions
  function setToken(newToken: string): void {
    token.value = newToken
    localStorage.setItem('token', newToken)
  }

  function setUserInfo(info: SysUser): void {
    userInfo.value = info
    localStorage.setItem('userInfo', JSON.stringify(info))
  }

  function setRoles(roleList: string[]): void {
    roles.value = roleList
  }

  function setPermissions(perms: string[]): void {
    permissions.value = perms
  }

  function setAccessibleRoutes(routes: RouteRecordRaw[]): void {
    accessibleRoutes.value = routes
  }

  function setMenuList(menus: MenuItem[]): void {
    menuList.value = menus
  }

  function toggleSidebar(): void {
    sidebarCollapsed.value = !sidebarCollapsed.value
    localStorage.setItem('sidebarCollapsed', String(sidebarCollapsed.value))
  }

  async function getPermissionsFromDatabase(): Promise<string[]> {
    try {
      const res = await userApi.getPermissions()
      if (res.data && Array.isArray(res.data)) {
        return res.data
      }
      return ['*:*:*']
    } catch (error) {
      console.error('获取权限API失败:', error)
      return ['*:*:*']
    }
  }

  async function loadUserPermissions(): Promise<void> {
    if (isLoadingPermissions) {
      return new Promise((resolve, reject) => {
        const checkInterval = setInterval(() => {
          if (!isLoadingPermissions) {
            clearInterval(checkInterval)
            if (permissions.value.length > 0) {
              resolve()
            } else {
              reject(new Error('权限加载失败'))
            }
          }
        }, 100)
      })
    }

    isLoadingPermissions = true

    try {
      if (userInfo.value?.permissions && userInfo.value.permissions.length > 0) {
        setPermissions(userInfo.value.permissions)
        setPermissionsLoaded(true)
        isLoadingPermissions = false
        return
      }

      const perms = await getPermissionsFromDatabase()
      setPermissions(perms)
      setPermissionsLoaded(true)
      isLoadingPermissions = false
    } catch (error) {
      console.error('加载权限失败:', error)
      const defaultPerms = ['*:*:*']
      setPermissions(defaultPerms)
      setPermissionsLoaded(true)
      isLoadingPermissions = false
      throw error
    }
  }

  async function getUserInfo(forceRefresh = false): Promise<SysUser> {
    try {
      if (userInfo.value && !forceRefresh) {
        return Promise.resolve(userInfo.value)
      }

      const res = await userApi.getCurrentInfo()

      if (res.data) {
        setUserInfo(res.data)

        if (res.data.roles) {
          const roleKeys = res.data.roles.map((role: any) => role.roleKey)
          setRoles(roleKeys)
        }

        await loadUserPermissions()

        return Promise.resolve(res.data)
      } else {
        return Promise.reject(new Error('获取用户信息失败'))
      }
    } catch (error) {
      return Promise.reject(error)
    }
  }

  async function login(loginForm: {
    username: string
    password: string
    captcha: string
    rememberMe: boolean
  }): Promise<any> {
    try {
      const res = await authApi.login(loginForm)
      if (res.data) {
        setToken(res.data.token)
        setUserInfo(res.data.userInfo)

        try {
          await loadUserPermissions()
        } catch (permError) {
          console.error('权限加载失败，使用默认权限:', permError)
          const defaultPerms = ['*:*:*']
          setPermissions(defaultPerms)
          setPermissionsLoaded(true)
        }

        return Promise.resolve(res)
      } else {
        return Promise.reject(new Error('登录失败'))
      }
    } catch (error) {
      return Promise.reject(error)
    }
  }

  function resetToken(): void {
    token.value = ''
    userInfo.value = null
    roles.value = []
    permissions.value = []
    accessibleRoutes.value = []
    menuList.value = []
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
    resetPermissionsLoaded()
    resetRouter()
  }

  async function logout(): Promise<void> {
    try {
      await authApi.logout()
    } catch (error) {
      console.error('登出失败:', error)
    } finally {
      resetToken()
    }
  }

  async function updatePassword(oldPassword: string, newPassword: string): Promise<any> {
    try {
      const res = await userApi.updatePwd(oldPassword, newPassword)
      return Promise.resolve(res)
    } catch (error) {
      return Promise.reject(error)
    }
  }

  async function updateProfile(profile: Partial<SysUser>): Promise<any> {
    try {
      const res = await userApi.profile(profile as SysUser)
      if (userInfo.value) {
        setUserInfo({ ...userInfo.value, ...profile })
      }
      return Promise.resolve(res)
    } catch (error) {
      return Promise.reject(error)
    }
  }

  async function updateAvatar(avatar: string): Promise<any> {
    try {
      const res = await userApi.avatar(avatar)
      if (userInfo.value) {
        setUserInfo({ ...userInfo.value, avatar })
      }
      return Promise.resolve(res)
    } catch (error) {
      return Promise.reject(error)
    }
  }

  return {
    // state
    token,
    userInfo,
    roles,
    permissions,
    accessibleRoutes,
    menuList,
    sidebarCollapsed,
    // getters
    isLoggedIn,
    getUserName,
    getNickName,
    getAvatar,
    getRoles,
    getPermissions,
    getAccessibleRoutes,
    getMenuList,
    getSidebarCollapsed,
    // actions
    setToken,
    setUserInfo,
    setRoles,
    setPermissions,
    setAccessibleRoutes,
    setMenuList,
    toggleSidebar,
    generateMenuTree,
    loadUserPermissions,
    getUserInfo,
    login,
    resetToken,
    logout,
    updatePassword,
    updateProfile,
    updateAvatar,
  }
})
