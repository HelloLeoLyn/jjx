// src/permission.ts
import router from './router'
import { useUserStore } from '@/store/modules/user'
import { usePermissionStore } from '@/store/modules/permission'
import { ElMessage, ElLoading } from 'element-plus'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

NProgress.configure({ showSpinner: false })

const whiteList = ['/login', '/404', '/401', '/redirect']

/**
 * 初始化权限系统（加载动态路由）
 * 只执行一次，第二次调用直接返回
 */
let initPromise: Promise<void> | null = null

async function initPermissionSystem(): Promise<void> {
  if (initPromise) return initPromise

  initPromise = (async () => {
    const userStore = useUserStore()
    const permissionStore = usePermissionStore()

    const loading = ElLoading.service({
      fullscreen: true,
      text: '正在加载系统资源...',
      background: 'rgba(0, 0, 0, 0.7)',
    })

    try {
      // 1. 确保用户信息已加载
      if (!userStore.userInfo) {
        await userStore.getUserInfo()
      }

      // 2. 确保权限已加载
      if (userStore.permissions.length === 0) {
        const userInfoStr = localStorage.getItem('userInfo')
        if (userInfoStr) {
          try {
            const userInfo = JSON.parse(userInfoStr)
            if (userInfo.permissions?.length > 0) {
              userStore.setPermissions(userInfo.permissions)
            }
          } catch { /* ignore */ }
        }
      }
      if (userStore.permissions.length === 0) {
        userStore.setPermissions(['*:*:*'])
      }

      // 3. 从后端拉取并生成动态路由
      const { routes, routeNames } = await permissionStore.generateRoutes()

      // 4. 先添加新的动态路由（防止中间态路由断档）
      for (const route of routes) {
        router.addRoute(route)
      }

      // 5. 再删除旧的动态路由（新路由已就绪，不会断档）
      const oldNames = permissionStore.getTrackedRouteNames
      for (const name of oldNames) {
        if (router.hasRoute(name) && !routeNames.includes(name)) {
          router.removeRoute(name)
        }
      }

      // 6. 添加 404 兜底（如果已有则覆盖）
      if (!router.hasRoute('NotFound')) {
        router.addRoute({
          path: '/:pathMatch(.*)*',
          name: 'NotFound',
          component: () => import('@/views/error/404.vue'),
          meta: { title: '404', hidden: true },
        })
      }

      // 7. 记录本次添加的路由 name，方便下次清理
      permissionStore.setTrackedRouteNames(routeNames)
    } catch (error) {
      console.error('权限系统初始化失败:', error)
      ElMessage.error('系统初始化失败，请重新登录')
      throw error
    } finally {
      loading.close()
      initPromise = null
    }
  })()

  return initPromise
}

/**
 * 重置权限系统（登出时调用）
 */
export function resetPermissionSystem(): void {
  initPromise = null
  const permissionStore = usePermissionStore()
  // 清理已添加的动态路由
  const names = permissionStore.getTrackedRouteNames
  for (const name of names) {
    if (router.hasRoute(name)) {
      router.removeRoute(name)
    }
  }
  // 清理 404 路由
  if (router.hasRoute('NotFound')) {
    router.removeRoute('NotFound')
  }
  permissionStore.reset()
}

/**
 * 路由守卫
 */
router.beforeEach(async (to, from, next) => {
  NProgress.start()
  const userStore = useUserStore()
  const permissionStore = usePermissionStore()
  const hasToken = !!userStore.token

  // 页面标题
  if (to.meta?.title) {
    document.title = `${to.meta.title} - ERP管理系统`
  }

  // ── 登录页 ──
  if (to.path === '/login') {
    if (hasToken) {
      next({ path: '/' })
    } else {
      next()
    }
    return
  }

  // ── 白名单 ──
  if (whiteList.includes(to.path)) {
    next()
    return
  }

  // ── 未登录 ──
  if (!hasToken) {
    next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    return
  }

  // ── 已登录，路由未初始化 ──
  if (!permissionStore.isLoaded) {
    try {
      await initPermissionSystem()
      // 重点：重新进入目标路由（此时新路由已注册，Vue Router 能匹配到）
      next({ ...to, replace: true })
      return
    } catch {
      userStore.resetToken()
      resetPermissionSystem()
      next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
      return
    }
  }

  // ── 权限验证 ──
  if (to.meta?.permission) {
    const hasPerm = userStore.hasPermission(to.meta.permission as string)
    if (!hasPerm) {
      ElMessage.warning('您没有权限访问该页面')
      next('/401')
      return
    }
  }

  next()
})

// 路由完成后关闭进度条
router.afterEach(() => {
  NProgress.done()
})

// 路由错误处理
router.onError((error) => {
  console.error('路由错误:', error)
  if (/Loading chunk \d+ failed/g.test(error.message)) {
    window.location.reload()
  }
})
