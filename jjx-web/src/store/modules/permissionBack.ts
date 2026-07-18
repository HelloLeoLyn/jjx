import { defineStore } from 'pinia'
import { menuApi } from '@/api/system/menu'
import type { SysMenu } from '@/types/system'

interface PermissionState {
  routes: SysMenu[]
  addRoutes: SysMenu[]
  sidebarRouters: SysMenu[]
}

export const usePermissionStore = defineStore('permission', {
  state: (): PermissionState => ({
    routes: [],
    addRoutes: [],
    sidebarRouters: [],
  }),

  getters: {
    getRoutes: (state) => state.routes,
    getAddRoutes: (state) => state.addRoutes,
    getSidebarRouters: (state) => state.sidebarRouters,
  },

  actions: {
    // 设置路由
    setRoutes(routes: SysMenu[]) {
      this.routes = routes
    },

    // 设置动态添加的路由
    setAddRoutes(routes: SysMenu[]) {
      this.addRoutes = routes
    },

    // 设置侧边栏路由
    setSidebarRouters(routes: SysMenu[]) {
      this.sidebarRouters = routes
    },

    // 生成路由
    async generateRoutes() {
      try {
        const res = await menuApi.getRouters()
        if (res.data) {
          const accessedRoutes = this.filterAsyncRoutes(res.data as any)
          this.setRoutes(accessedRoutes)
          this.setAddRoutes(accessedRoutes)
          return accessedRoutes
        } else {
          throw new Error('获取路由失败')
        }
      } catch (error) {
        throw error
      }
    },

    // 过滤异步路由
    filterAsyncRoutes(routes: SysMenu[]): SysMenu[] {
      const res: SysMenu[] = []

      routes.forEach((route) => {
        const tmp = { ...route }
        if (tmp.children) {
          tmp.children = this.filterAsyncRoutes(tmp.children)
        }
        res.push(tmp)
      })

      return res
    },

    // 构建侧边栏路由
    async buildSidebarRouters() {
      try {
        const res = await menuApi.getRouters()
        if (res.data) {
          const sidebarRouters = this.filterSidebarRoutes(res.data as any)
          this.setSidebarRouters(sidebarRouters)
          return sidebarRouters
        } else {
          throw new Error('获取侧边栏路由失败')
        }
      } catch (error) {
        throw error
      }
    },

    // 过滤侧边栏路由（只显示目录和菜单，不显示按钮）
    filterSidebarRoutes(routes: SysMenu[]): SysMenu[] {
      const res: SysMenu[] = []

      routes.forEach((route) => {
        // 只显示目录和菜单，不显示按钮
        if (route.menuType !== 'F' && route.visible === '0') {
          const tmp = { ...route }
          if (tmp.children && tmp.children.length > 0) {
            tmp.children = this.filterSidebarRoutes(tmp.children)
          }
          res.push(tmp)
        }
      })

      return res
    },

    // 根据权限过滤路由
    filterRoutesByPermission(routes: SysMenu[], permissions: string[]): SysMenu[] {
      const res: SysMenu[] = []

      routes.forEach((route) => {
        const tmp = { ...route }

        // 如果有权限字符串，检查权限
        if (tmp.perms) {
          const hasPermission = permissions.some(
            (permission) => permission === tmp.perms || permission === '*:*:*'
          )
          if (!hasPermission) {
            return
          }
        }

        if (tmp.children) {
          tmp.children = this.filterRoutesByPermission(tmp.children, permissions)
        }

        res.push(tmp)
      })

      return res
    },

    // 重置权限
    resetPermission() {
      this.routes = []
      this.addRoutes = []
      this.sidebarRouters = []
    },
  },
})
