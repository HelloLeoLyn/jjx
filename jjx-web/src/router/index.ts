import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'

// 简易顶部进度条，不阻塞 UI
let progressTimer: ReturnType<typeof setTimeout> | null = null
let progressEl: HTMLElement | null = null

function startProgress() {
  // 如果已有进度条，重置
  if (progressEl) progressEl.remove()
  
  progressEl = document.createElement('div')
  progressEl.className = 'route-progress'
  progressEl.style.cssText = 'position:fixed;top:0;left:0;width:0;height:2px;background:#409eff;z-index:99999;transition:width 0.2s ease;'
  document.body.appendChild(progressEl)
  
  // 动画推进到 80%
  requestAnimationFrame(() => {
    if (progressEl) progressEl.style.width = '80%'
  })
  
  // 超时保护：5 秒后强制完成
  progressTimer = setTimeout(() => endProgress(), 5000)
}

function endProgress() {
  if (progressTimer) { clearTimeout(progressTimer); progressTimer = null }
  if (progressEl) {
    progressEl.style.width = '100%'
    setTimeout(() => {
      if (progressEl) { progressEl.remove(); progressEl = null }
    }, 300)
  }
}
export const constantRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: {
      title: '登录',
      hidden: true,
    },
  },
  {
    path: '/404',
    name: 'NotFound',
    component: () => import('@/views/error/404.vue'),
    meta: {
      title: '404',
      hidden: true,
    },
  },
  {
    path: '/redirect/:path(.*)',
    name: 'Redirect',
    component: () => import('@/layout/redirect.vue'),
    meta: {
      hidden: true,
    },
  },
  // 首页作为静态路由，不需要权限控制
  {
    path: '/dashboard',
    name: 'Dashboard',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard/index',
    meta: {
      title: '首页',
      icon: 'HomeFilled',
      affix: true,
      hidden: false,
      sort: 0,
    },
    children: [
      {
        path: 'index',
        name: 'DashboardIndex',
        component: () => import('@/views/dashboard/index.vue'),
        meta: {
          title: '首页',
          icon: 'HomeFilled',
          affix: true,
          hidden: true,
        },
      },
    ],
  },
  // 任务看板（jjx-kanban 合并）放在首页下面
  // ⚠️ name 不能叫 Kanban：数据库“车间看板”菜单 path=kanban 会自动生成同名路由，addRoute 会覆盖静态路由
  {
    path: '/kanban',
    name: 'TaskKanban',
    component: () => import('@/layout/index.vue'),
    redirect: '/kanban/index',
    meta: {
      title: '任务看板',
      icon: 'DataBoard',
      hidden: false,
      sort: 1,
    },
    children: [
      {
        path: 'index',
        name: 'TaskKanbanIndex',
        component: () => import('@/views/kanban/index.vue'),
        meta: {
          title: '任务看板',
          icon: 'DataBoard',
          hidden: true,
        },
      },
    ],
  },
  // 消息通知（放在任务看板下面，所有登录用户可见）
  {
    path: '/notification',
    name: 'Notification',
    component: () => import('@/layout/index.vue'),
    redirect: '/notification/index',
    meta: {
      title: '消息通知',
      icon: 'Bell',
      hidden: false,
      sort: 2,
    },
    children: [
      {
        path: 'index',
        name: 'NotificationIndex',
        component: () => import('@/views/notification/index.vue'),
        meta: {
          title: '消息通知',
          icon: 'Bell',
          hidden: true,
        },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes: [
    // 根路径重定向到dashboard
    {
      path: '/',
      redirect: '/dashboard',
    },
    ...constantRoutes, // 默认加载静态路由
  ],
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else {
      return { top: 0 }
    }
  },
})

// 路由切换顶部进度条
router.beforeEach((to, from, next) => {
  if (to.path !== from.path) {
    startProgress()
  }
  next()
})

router.afterEach(() => {
  endProgress()
})

router.onError(() => {
  endProgress()
})

export default router
