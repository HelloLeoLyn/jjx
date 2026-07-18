// src/store/modules/permission.ts
import { defineStore } from 'pinia'
import type { RouteRecordRaw } from 'vue-router'
import type { AsyncRouteConfig } from '@/types/system'
import { filterAsyncRouter, convertToRouteRecords, generateMenuTree } from '@/utils/routeHelper'
import { menuApi } from '@/api/system/menu'
import { useUserStore } from './user'
import { constantRoutes } from '@/router/index'

// 定义状态接口
interface PermissionState {
  routes: RouteRecordRaw[] // 动态路由
  menus: any[] // 菜单树
  dynamicRoutes: AsyncRouteConfig[] // 原始动态路由配置
  loaded: boolean // 是否已加载
  loading: boolean // 是否加载中
}

// 异步路由配置（从后端获取）
const asyncRoutes: AsyncRouteConfig[] = []

export const usePermissionStore = defineStore('permission', {
  state: (): PermissionState => ({
    routes: [],
    menus: [],
    dynamicRoutes: [],
    loaded: false,
    loading: false,
  }),

  getters: {
    // 获取路由
    getRoutes: (state) => state.routes,
    // 获取菜单
    getMenus: (state) => state.menus,
    // 是否已加载
    isLoaded: (state) => state.loaded,
  },

  actions: {
    /**
     * 生成动态路由
     */
    async generateRoutes(): Promise<RouteRecordRaw[]> {
      if (this.loaded) {
        return this.routes
      }

      if (this.loading) {
        return new Promise((resolve) => {
          const timer = setInterval(() => {
            if (!this.loading) {
              clearInterval(timer)
              resolve(this.routes)
            }
          }, 100)
        })
      }

      this.loading = true

      try {
        const userStore = useUserStore()

        // ✅ 详细打印权限状态
        const permissions = userStore.permissions

        // 从后端获取菜单路由
        const res = await menuApi.getRouters()

        let routerMap: AsyncRouteConfig[] = []

        if (res.code === 200 && res.data) {
          routerMap = res.data
          // 调试：打印所有路由的组件路径
          // const logRoutes = (routes: AsyncRouteConfig[], indent = '') => {
          //   routes.forEach((route, index) => {
          //     if (route.children && route.children.length > 0) {
          //       logRoutes(route.children, indent + '  ')
          //     }
          //   })
          // }
          // logRoutes(routerMap)
        } else {
          routerMap = asyncRoutes
        }

        // 根据权限过滤路由
        const filteredRoutes = this.filterRoutesByPermission(routerMap, permissions)

        // 处理路由（转换组件）
        const processedRoutes = filterAsyncRouter(filteredRoutes, true)

        // 转换为Vue Router路由
        const routes = convertToRouteRecords(processedRoutes)

        // 生成动态路由的菜单树
        const dynamicMenus = generateMenuTree(processedRoutes)

        // 生成静态路由的菜单树（从 constantRoutes 中提取）
        const staticMenus = this.generateStaticMenus()

        // 合并菜单：静态菜单 + 动态菜单
        const menus = [...staticMenus, ...dynamicMenus]

        this.routes = routes
        this.menus = menus
        this.dynamicRoutes = processedRoutes
        this.loaded = true

        return routes
      } catch (error) {
        console.error('生成动态路由失败:', error)
        throw error
      } finally {
        this.loading = false
      }
    },

    /**
     * 根据权限过滤路由
     */
    filterRoutesByPermission(
      routes: AsyncRouteConfig[],
      permissions: string[]
    ): AsyncRouteConfig[] {
      const hasPermission = (perms: string | undefined): boolean => {
        if (!perms) return true
        if (permissions.includes('*:*:*')) {
          return true
        }

        return permissions.includes(perms)
      }

      const result: AsyncRouteConfig[] = []

      for (const route of routes) {
        // 检查路由权限
        if (route.meta?.permission && !hasPermission(route.meta.permission)) {
          continue
        }

        const filteredRoute = { ...route }

        // 递归处理子路由
        if (route.children && route.children.length > 0) {
          filteredRoute.children = this.filterRoutesByPermission(route.children, permissions)

          // 如果过滤后没有子路由，删除children
          if (filteredRoute.children.length === 0) {
            delete filteredRoute.children
            delete filteredRoute.redirect
          }
        }

        result.push(filteredRoute)
      }

      return result
    },

    /**
     * 重置权限状态
     */
    reset() {
      this.routes = []
      this.menus = []
      this.dynamicRoutes = []
      this.loaded = false
      this.loading = false
    },

    /**
     * 设置动态路由
     */
    setDynamicRoutes(routes: AsyncRouteConfig[]) {
      this.dynamicRoutes = routes
    },

    /**
     * 设置菜单
     */
    setMenus(menus: any[]) {
      this.menus = menus
    },

    /**
     * 生成静态路由的菜单
     */
    generateStaticMenus(): any[] {
      const staticMenus: any[] = []

      // 遍历 constantRoutes，提取菜单信息
      for (const route of constantRoutes) {
        // 跳过登录页、404页等隐藏路由
        if (route.meta?.hidden) {
          continue
        }

        // 构建菜单项
        const menu: any = {
          path: route.path,
          name: route.name || '',
          title: route.meta?.title || '',
          icon: route.meta?.icon,
          permission: route.meta?.permission,
          hidden: route.meta?.hidden,
          sort: route.meta?.sort || 0,
        }

        // 处理子路由（如 dashboard 有子路由）
        if (route.children && route.children.length > 0) {
          const children: any[] = []
          for (const child of route.children) {
            if (child.meta?.hidden) {
              continue
            }

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

      // 按 sort 排序
      return staticMenus.sort((a, b) => (a.sort || 0) - (b.sort || 0))
    },
  },
})
