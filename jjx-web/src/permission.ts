// src/permission.ts
import router from './router'
import { useUserStore } from '@/store/modules/user'
import { usePermissionStore } from '@/store/modules/permission'
import { ElMessage, ElLoading } from 'element-plus'

// 白名单路由（不需要登录）
const whiteList = ['/login', '/404', '/401', '/redirect']

// 初始化状态 - 基于用户ID，确保不同用户重新初始化
let initializedUserId: number | null | undefined = null
let initPromise: Promise<void> | null = null

// 检查权限系统是否已为当前用户初始化
function checkIsInitialized(): boolean {
  const userStore = useUserStore()
  const currentUserId = userStore.userInfo?.userId
  return initializedUserId === currentUserId
}

/**
 * 初始化权限系统
 */
async function initPermissionSystem(): Promise<void> {
  const userStore = useUserStore()
  const currentUserId = userStore.userInfo?.userId

  // 如果已经为当前用户初始化过，直接返回
  if (initializedUserId === currentUserId) {
    return
  }

  // 如果正在初始化，等待完成
  if (initPromise) {
    return initPromise
  }

  initPromise = (async () => {
    const permissionStore = usePermissionStore()

    // 显示加载提示
    const loading = ElLoading.service({
      fullscreen: true,
      text: '正在加载系统资源...',
      background: 'rgba(0, 0, 0, 0.7)',
    })

    try {
      // 1. 获取用户信息（如果没有）
      if (!userStore.userInfo) {
        await userStore.getUserInfo()
      }

      // 2. 确保权限已加载
      // 如果权限为空，尝试从localStorage恢复
      if (userStore.permissions.length === 0) {
        const userInfoStr = localStorage.getItem('userInfo')
        if (userInfoStr) {
          try {
            const userInfo = JSON.parse(userInfoStr)
            if (userInfo.permissions && userInfo.permissions.length > 0) {
              userStore.setPermissions(userInfo.permissions)
            }
          } catch (e) {
            console.error('解析localStorage用户信息失败:', e)
          }
        }
      }

      // 如果权限仍然为空，使用默认权限
      if (userStore.permissions.length === 0) {
        userStore.setPermissions(['*:*:*'])
      }

      // 3. 重置之前的动态路由（如果有）
      permissionStore.reset()

      // 4. 生成新的动态路由（基于当前用户的权限）
      const routes = await permissionStore.generateRoutes()

      // 5. 动态添加路由到router
      routes.forEach((route) => {
        router.addRoute(route)
      })

      // 6. 添加404路由（必须在最后）
      router.addRoute({
        path: '/:pathMatch(.*)*',
        name: 'NotFound',
        component: () => import('@/views/error/404.vue'),
        meta: { title: '404', hidden: true },
      })

      // 7. 标记为当前用户已初始化
      initializedUserId = currentUserId
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
  initializedUserId = null
  initPromise = null

  const permissionStore = usePermissionStore()
  permissionStore.reset()
}

/**
 * 路由守卫
 */
router.beforeEach(async (to, from, next) => {
  const userStore = useUserStore()
  const hasToken = !!userStore.token

  // 设置页面标题
  if (to.meta && to.meta.title) {
    document.title = `${to.meta.title} - ERP管理系统`
  }

  // 1. 登录页特殊处理
  if (to.path === '/login') {
    if (hasToken && checkIsInitialized()) {
      // 已登录且已初始化，跳转到首页
      next({ path: '/' })
    } else if (hasToken && !checkIsInitialized()) {
      // 已登录但未初始化，尝试初始化后跳转
      try {
        await initPermissionSystem()
        next({ path: '/' })
      } catch {
        next()
      }
    } else {
      next()
    }
    return
  }

  // 2. 白名单路由（不需要登录）
  if (whiteList.includes(to.path)) {
    next()
    return
  }

  // 3. 未登录处理
  if (!hasToken) {
    next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    return
  }

  // 4. 已登录但权限系统未初始化
  const initialized = checkIsInitialized()

  if (!initialized) {
    try {
      await initPermissionSystem()
      // 重新进入目标路由
      next({ ...to, replace: true })
    } catch (error) {
      console.error('权限系统初始化失败:', error)
      userStore.resetToken()
      resetPermissionSystem()
      next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    }
    return
  } else {
  }

  // 5. 权限验证（基于菜单权限）
  if (to.meta && to.meta.permission) {
    const hasPermission = userStore.hasPermission(to.meta.permission as string)
    if (hasPermission) {
      next()
    } else {
      ElMessage.warning('您没有权限访问该页面')
      next('/401')
    }
  } else {
    // 调试：记录访问的路由
    next()
  }
})

// 路由错误处理
router.onError((error) => {
  console.error('路由错误:', error)
  const pattern = /Loading chunk (\d)+ failed/g
  if (pattern.test(error.message)) {
    // 动态加载失败，刷新页面
    window.location.reload()
  }
})

// 路由完成后关闭loading（可选）
router.afterEach(() => {
  // 可以在这里做一些清理工作
})
