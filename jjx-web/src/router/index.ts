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
    path: '/demo/a4-print',
    name: 'A4PrintDemo',
    component: () => import('@/views/demo/A4PrintDemo.vue'),
    meta: {
      title: 'A4打印演示',
      hidden: true,
    },
  },
  {
    path: '/print/quotation/:id',
    name: 'QuotationPrint',
    component: () => import('@/views/sales/quotation/print.vue'),
    meta: {
      title: '报价单打印',
      hidden: true,
    },
  },
  {
    path: '/print/transfer/:id',
    name: 'TransferPrint',
    component: () => import('@/views/inventory/transfer/print.vue'),
    meta: {
      title: '调拨单打印',
      hidden: true,
    },
  },
  {
    path: '/print/stocktake/:id',
    name: 'StocktakePrint',
    component: () => import('@/views/inventory/stocktake/print.vue'),
    meta: {
      title: '盘点单打印',
      hidden: true,
    },
  },
  {
    path: '/print/sample-order/:id',
    name: 'SampleOrderPrint',
    component: () => import('@/views/sales/sample-order/print.vue'),
    meta: {
      title: '样品单打印',
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
  // 个人中心（顶栏头像下拉入口，不进菜单）
  {
    path: '/profile',
    name: 'Profile',
    component: () => import('@/layout/index.vue'),
    redirect: '/profile/index',
    meta: {
      title: '个人中心',
      icon: 'User',
      hidden: true,
    },
    children: [
      {
        path: 'index',
        name: 'ProfileIndex',
        component: () => import('@/views/system/user/profile/index.vue'),
        meta: {
          title: '个人中心',
          hidden: true,
        },
      },
    ],
  },
  // 产品工程独立子页（工艺路线/标准工序 新增编辑，无菜单项，静态注册隐藏路由）
  // 包裹在隐藏 Layout 下：保持左侧菜单/顶栏框架，页面内 router.push 使用 /product 前缀
  {
    path: '/product',
    component: () => import('@/layout/index.vue'),
    meta: { hidden: true },
    children: [
      {
        path: 'route',
        redirect: '/engineering/route',
        meta: { hidden: true },
      },
      {
        path: 'route/add',
        name: 'ProductRouteAdd',
        component: () => import('@/views/product/route/add.vue'),
        meta: { hidden: true, title: '新增工艺路线', permission: 'engineering:routing:add' },
      },
      {
        path: 'route/edit/:routingId',
        name: 'ProductRouteEdit',
        component: () => import('@/views/product/route/edit.vue'),
        meta: { hidden: true, title: '编辑工艺路线', permission: 'engineering:routing:edit' },
      },
      {
        path: 'standard-process',
        redirect: '/engineering/standard-processes',
        meta: { hidden: true },
      },
      {
        path: 'standard-process/add',
        name: 'StandardProcessAdd',
        component: () => import('@/views/product/standard-process/add.vue'),
        meta: { hidden: true, title: '新增标准工序', permission: 'engineering:standard-process:add' },
      },
      {
        path: 'standard-process/edit/:processId',
        name: 'StandardProcessEdit',
        component: () => import('@/views/product/standard-process/edit.vue'),
        meta: { hidden: true, title: '编辑标准工序', permission: 'engineering:standard-process:edit' },
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
