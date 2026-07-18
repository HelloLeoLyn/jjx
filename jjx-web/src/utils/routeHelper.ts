// src/utils/routeHelper.ts
import type { Component } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import type { AsyncRouteConfig } from '@/types/system'

// 导入布局组件
import Layout from '@/layout/index.vue'

// 使用 Vite 的 Glob 导入预加载所有视图组件
// 这会匹配 src/views 目录下的所有 .vue 文件
// 使用类型断言确保类型安全
const viewModules = import.meta.glob('@/views/**/*.vue') as Record<string, () => Promise<Component>>

// 组件缓存
const componentCache = new Map<string, () => Promise<Component>>()

/**
 * 规范化组件路径
 * @param path 原始路径
 * @returns 规范化后的路径（相对于 src/views 的路径）
 */
function normalizePath(path: string): string {
  let cleanPath = path.trim()

  // 1. 移除开头的 ../ 或 ./
  if (cleanPath.startsWith('../')) {
    cleanPath = cleanPath.substring(3)
  } else if (cleanPath.startsWith('./')) {
    cleanPath = cleanPath.substring(2)
  }

  // 2. 如果路径以 views/ 开头，移除它（因为我们要添加到 @/views/）
  if (cleanPath.startsWith('views/')) {
    cleanPath = cleanPath.substring(6) // 移除 "views/"
  }

  // 3. 如果路径已经以 .vue 结尾，移除它
  if (cleanPath.endsWith('.vue')) {
    cleanPath = cleanPath.substring(0, cleanPath.length - 4)
  }

  return cleanPath
}

/**
 * 动态加载视图组件（使用 Vite Glob 导入）
 * @param path 组件路径
 * @returns 返回一个Promise，解析为组件
 */
export const loadView = (path: string): (() => Promise<Component>) => {
  // 检查缓存
  if (componentCache.has(path)) {
    return componentCache.get(path)!
  }

  // 规范化路径
  const cleanPath = normalizePath(path)

  // 构建在 Glob 中的路径
  // Glob 导入的路径格式是：/src/views/xxx/xxx.vue
  const globPath = `/src/views/${cleanPath}.vue`

  // 检查是否在 Glob 模块中
  if (viewModules[globPath]) {
    const loader = viewModules[globPath]
    componentCache.set(path, loader)
    return loader
  }

  // 如果找不到，尝试其他可能的路径格式
  console.warn(`组件 ${path} 未在 Glob 导入中找到，尝试备用路径`)

  // 备用方案：尝试直接导入（带 @vite-ignore）
  const importPath = `@/views/${cleanPath}.vue`
  const fallbackLoader = () => import(/* @vite-ignore */ importPath)
  componentCache.set(path, fallbackLoader)
  return fallbackLoader
}

/**
 * 解析组件字符串为实际组件
 * @param component 组件路径字符串或组件对象
 * @returns 返回解析后的组件
 */
export function resolveComponent(
  component: string | Component
): Component | (() => Promise<Component>) {
  if (typeof component !== 'string') {
    return component
  }

  // 处理特殊组件
  switch (component) {
    case 'Layout':
    case 'layout/index.vue':
    case '../views/layout/index.vue':
      return Layout
    default:
      return loadView(component)
  }
}

/**
 * 过滤异步路由，处理组件转换
 * @param routes 异步路由配置数组
 * @param type 是否过滤子路由
 * @returns 处理后的路由配置数组
 */
export function filterAsyncRouter(
  routes: AsyncRouteConfig[],
  type: boolean = false
): AsyncRouteConfig[] {
  const validRoutes = routes.filter((route) => route.path)

  return validRoutes.map((route) => {
    const processedRoute = { ...route }

    if (type && processedRoute.children) {
      processedRoute.children = filterChildren(processedRoute.children)
    }

    if (processedRoute.component) {
      processedRoute.component = resolveComponent(processedRoute.component)
    }

    if (processedRoute.children && processedRoute.children.length > 0) {
      processedRoute.children = filterAsyncRouter(processedRoute.children, type)
    } else {
      delete processedRoute.children
      delete processedRoute.redirect
    }

    return processedRoute
  })
}

/**
 * 过滤子路由（内部使用）
 */
function filterChildren(children: AsyncRouteConfig[]): AsyncRouteConfig[] {
  const validChildren = children.filter((child) => child.path)

  return validChildren.map((child) => {
    const processedChild = { ...child }

    if (processedChild.component) {
      processedChild.component = resolveComponent(processedChild.component)
    }

    if (processedChild.children && processedChild.children.length > 0) {
      processedChild.children = filterChildren(processedChild.children)
    }

    return processedChild
  })
}

/**
 * 将异步路由配置转换为Vue Router可用的路由记录
 * @param routes 异步路由配置数组
 * @returns Vue Router路由记录数组
 */
export function convertToRouteRecords(routes: AsyncRouteConfig[]): RouteRecordRaw[] {
  const result: RouteRecordRaw[] = []

  for (const route of routes) {
    // 必须有 path 和 component 或 redirect
    if (!route.path) continue

    // 构建基础路由对象
    let routeRecord: RouteRecordRaw

    // 如果有 redirect，使用重定向路由
    if (route.redirect && !route.component) {
      routeRecord = {
        path: route.path,
        redirect: route.redirect,
      }
    } else {
      // 普通路由必须有 component
      routeRecord = {
        path: route.path,
        component: route.component as Component,
      }
    }

    // 添加 name
    if (route.name) {
      routeRecord.name = route.name
    }

    // 添加 meta
    if (route.meta) {
      routeRecord.meta = route.meta
    }

    // 递归处理子路由
    if (route.children && route.children.length > 0) {
      routeRecord.children = convertToRouteRecords(route.children)
    }

    result.push(routeRecord)
  }

  return result
}

/**
 * 生成菜单树（用于侧边栏）
 */
export function generateMenuTree(routes: AsyncRouteConfig[]): any[] {
  const menus: any[] = []

  for (const route of routes) {
    if (route.meta?.hidden) {
      continue
    }

    const menu: any = {
      path: route.path,
      name: route.name || '',
      title: route.meta?.title || '',
      icon: route.meta?.icon,
      permission: route.meta?.permission,
      hidden: route.meta?.hidden,
      sort: route.meta?.sort,
    }

    if (route.children && route.children.length > 0) {
      const children = generateMenuTree(route.children)
      if (children.length > 0) {
        menu.children = children
      }
    }

    menus.push(menu)
  }

  return menus.sort((a, b) => (a.sort || 0) - (b.sort || 0))
}
