// src/utils/routeHelper.ts
import type { Component } from 'vue'
import type { RouteRecordRaw } from 'vue-router'
import type { AsyncRouteConfig } from '@/types/system'

// 导入布局组件
import Layout from '@/layout/index.vue'

// 使用 Vite 的 Glob 导入预加载所有视图组件
const viewModules = import.meta.glob('@/views/**/*.vue') as Record<string, () => Promise<Component>>

// 组件缓存
const componentCache = new Map<string, () => Promise<Component>>()

// ========== 路径解析 ==========

function normalizePath(path: string): string {
  let cleanPath = path.trim()
  if (cleanPath.startsWith('../')) cleanPath = cleanPath.substring(3)
  else if (cleanPath.startsWith('./')) cleanPath = cleanPath.substring(2)
  if (cleanPath.startsWith('views/')) cleanPath = cleanPath.substring(6)
  if (cleanPath.endsWith('.vue')) cleanPath = cleanPath.substring(0, cleanPath.length - 4)
  return cleanPath
}

// ========== 组件加载 ==========

export const loadView = (path: string): (() => Promise<Component>) => {
  if (componentCache.has(path)) return componentCache.get(path)!

  const cleanPath = normalizePath(path)
  const globPath = `/src/views/${cleanPath}.vue`

  if (viewModules[globPath]) {
    const loader = viewModules[globPath]
    componentCache.set(path, loader)
    return loader
  }

  console.warn(`组件 ${path} 未在 Glob 导入中找到，尝试备用路径`)
  const fallbackLoader = () => import(/* @vite-ignore */ `@/views/${cleanPath}.vue`)
  componentCache.set(path, fallbackLoader)
  return fallbackLoader
}

export function resolveComponent(component: string | Component): Component | (() => Promise<Component>) {
  if (typeof component !== 'string') return component
  switch (component) {
    case 'Layout':
    case 'layout/index.vue':
    case '../views/layout/index.vue':
      return Layout
    default:
      return loadView(component)
  }
}

// ========== 路由名称补全 ==========

/**
 * 从路由 path 生成唯一的 name（用于 keep-alive 和路由管理）
 * 后端 route_name 可能为 NULL，需要自动兜底
 */
function ensureRouteName(route: AsyncRouteConfig, parentName?: string): string {
  if (route.name && typeof route.name === 'string' && route.name.trim()) {
    return route.name
  }
  // 从 path 生成：/product/list → ProductList
  const pathSegment = route.path.replace(/^\//, '').replace(/\/+/g, '-')
  const generated = pathSegment
    .split(/[-/]/)
    .filter(Boolean)
    .map(s => s.charAt(0).toUpperCase() + s.slice(1))
    .join('')
  if (!generated) {
    // 兜底：用父级名 + 随机后缀
    return parentName ? parentName + '_Route' : 'Route_' + Date.now()
  }
  return generated
}

/**
 * 递归补全路由名称
 */
function fillRouteNames(routes: AsyncRouteConfig[]): void {
  for (const route of routes) {
    route.name = ensureRouteName(route)
    if (route.children && route.children.length > 0) {
      fillRouteNames(route.children)
    }
  }
}

// ========== 过滤/转换 ==========

export function filterAsyncRouter(routes: AsyncRouteConfig[], type: boolean = false): AsyncRouteConfig[] {
  // 先补全所有 name
  fillRouteNames(routes)

  const validRoutes = routes.filter((route) => route.path)
  return validRoutes.map((route) => {
    const processedRoute = { ...route }

    if (type && processedRoute.children) {
      processedRoute.children = filterChildren(processedRoute.children)
    }

    if (processedRoute.component) {
      processedRoute.component = resolveComponent(processedRoute.component)
    }

    // 映射 keepAlive → noCache（tagsView 用 noCache 判断）
    if (processedRoute.meta) {
      const meta = processedRoute.meta as any
      if (meta.keepAlive !== undefined) {
        meta.noCache = !meta.keepAlive
      }
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

function filterChildren(children: AsyncRouteConfig[]): AsyncRouteConfig[] {
  // 子路由也需要补 name
  fillRouteNames(children)

  const validChildren = children.filter((child) => child.path)
  return validChildren.map((child) => {
    const processedChild = { ...child }
    if (processedChild.component) {
      processedChild.component = resolveComponent(processedChild.component)
    }
    if (processedChild.meta) {
      const meta = processedChild.meta as any
      if (meta.keepAlive !== undefined) {
        meta.noCache = !meta.keepAlive
      }
    }
    if (processedChild.children && processedChild.children.length > 0) {
      processedChild.children = filterChildren(processedChild.children)
    }
    return processedChild
  })
}

export function convertToRouteRecords(routes: AsyncRouteConfig[]): RouteRecordRaw[] {
  const result: RouteRecordRaw[] = []

  for (const route of routes) {
    if (!route.path) continue

    let routeRecord: RouteRecordRaw

    if (route.redirect && !route.component) {
      routeRecord = { path: route.path, redirect: route.redirect }
    } else {
      routeRecord = { path: route.path, component: route.component as Component }
    }

    if (route.name) {
      routeRecord.name = route.name
    }
    if (route.meta) {
      routeRecord.meta = route.meta
    }
    if (route.children && route.children.length > 0) {
      routeRecord.children = convertToRouteRecords(route.children)
    }

    result.push(routeRecord)
  }

  return result
}

// ========== 菜单树 ==========

export function generateMenuTree(routes: AsyncRouteConfig[]): any[] {
  const menus: any[] = []

  for (const route of routes) {
    if (route.meta?.hidden) continue

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
      if (children.length > 0) menu.children = children
    }

    menus.push(menu)
  }

  return menus.sort((a, b) => (a.sort || 0) - (b.sort || 0))
}

/**
 * 收集所有动态路由的 name（用于清理时 removeRoute）
 */
export function collectRouteNames(routes: AsyncRouteConfig[]): string[] {
  const names: string[] = []
  function walk(list: AsyncRouteConfig[]) {
    for (const r of list) {
      if (r.name) names.push(r.name as string)
      if (r.children) walk(r.children)
    }
  }
  walk(routes)
  return names
}
