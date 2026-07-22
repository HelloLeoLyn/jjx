// src/store/modules/permission.ts
import { defineStore } from 'pinia'
import type { RouteRecordRaw } from 'vue-router'
import type { AsyncRouteConfig } from '@/types/system'
import { filterAsyncRouter, convertToRouteRecords, generateMenuTree, collectRouteNames } from '@/utils/routeHelper'
import { menuApi } from '@/api/system/menu'
import { useUserStore } from './user'
import { constantRoutes } from '@/router/index'

interface PermissionState {
  routes: RouteRecordRaw[]
  menus: any[]
  dynamicRoutes: AsyncRouteConfig[]
  loaded: boolean
  /** 已由 addRoute 添加的路由 name 列表 */
  trackedRouteNames: string[]
}

export const usePermissionStore = defineStore('permission', {
  state: (): PermissionState => ({
    routes: [],
    menus: [],
    dynamicRoutes: [],
    loaded: false,
    trackedRouteNames: [],
  }),

  getters: {
    getRoutes: (state) => state.routes,
    getMenus: (state) => state.menus,
    isLoaded: (state) => state.loaded,
    getTrackedRouteNames: (state) => state.trackedRouteNames,
  },

  actions: {
    /**
     * 生成动态路由
     */
    async generateRoutes(): Promise<{ routes: RouteRecordRaw[]; routeNames: string[] }> {
      const userStore = useUserStore()
      const permissions = userStore.permissions

      // 从后端获取菜单路由
      const res = await menuApi.getRouters()
      let routerMap: AsyncRouteConfig[] = []

      if (res.code === 200 && res.data) {
        routerMap = res.data
      }

      // 根据权限过滤路由
      const filteredRoutes = this.filterRoutesByPermission(routerMap, permissions)

      // 处理路由（转换组件 + 补 name + 映射 meta）
      const processedRoutes = filterAsyncRouter(filteredRoutes, true)

      // 转换为 Vue Router 路由
      const routes = convertToRouteRecords(processedRoutes)

      // 生成菜单树
      const dynamicMenus = generateMenuTree(processedRoutes)
      const staticMenus = this.generateStaticMenus()
      const menus = [...staticMenus, ...dynamicMenus]

      // 收集所有路由 name（用于后续 cleanup）
      const routeNames = collectRouteNames(processedRoutes)

      this.routes = routes
      this.menus = menus
      this.dynamicRoutes = processedRoutes
      this.loaded = true

      return { routes, routeNames }
    },

    /**
     * 重置权限状态（登出时调用）
     */
    reset() {
      this.routes = []
      this.menus = []
      this.dynamicRoutes = []
      this.loaded = false
      this.trackedRouteNames = []
    },

    /**
     * 设置已跟踪的路由名称列表（由 permission.ts 在 addRoute 后调用）
     */
    setTrackedRouteNames(names: string[]) {
      this.trackedRouteNames = names
    },

    /**
     * 根据权限过滤路由
     */
    filterRoutesByPermission(routes: AsyncRouteConfig[], permissions: string[]): AsyncRouteConfig[] {
      const hasPermission = (perms: string | undefined): boolean => {
        if (!perms) return true
        if (permissions.includes('*:*:*')) return true
        return permissions.includes(perms)
      }

      const result: AsyncRouteConfig[] = []

      for (const route of routes) {
        if (route.meta?.permission && !hasPermission(route.meta.permission as string)) {
          continue
        }

        const filteredRoute = { ...route }

        if (route.children && route.children.length > 0) {
          filteredRoute.children = this.filterRoutesByPermission(route.children, permissions)
          if (filteredRoute.children.length === 0) {
            delete filteredRoute.children
            delete filteredRoute.redirect
          }
        }

        result.push(filteredRoute)
      }

      return result
    },

    generateStaticMenus(): any[] {
      const staticMenus: any[] = []

      for (const route of constantRoutes) {
        if (route.meta?.hidden) continue

        const menu: any = {
          path: route.path,
          name: route.name || '',
          title: route.meta?.title || '',
          icon: route.meta?.icon,
          permission: route.meta?.permission,
          hidden: route.meta?.hidden,
          sort: route.meta?.sort || 0,
        }

        if (route.children && route.children.length > 0) {
          const children: any[] = []
          for (const child of route.children) {
            if (child.meta?.hidden) continue
            children.push({
              path: child.path.startsWith('/') ? child.path : `${route.path}/${child.path}`,
              name: child.name || '',
              title: child.meta?.title || '',
              icon: child.meta?.icon,
              permission: child.meta?.permission,
              hidden: child.meta?.hidden,
              sort: child.meta?.sort || 0,
            })
          }
          if (children.length > 0) {
            menu.children = children.sort((a, b) => (a.sort || 0) - (b.sort || 0))
          }
        }

        staticMenus.push(menu)
      }

      return staticMenus.sort((a, b) => (a.sort || 0) - (b.sort || 0))
    },
  },
})
