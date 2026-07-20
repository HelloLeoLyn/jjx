import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
import { ElLoading } from 'element-plus'

let loadingInstance: any = null

// 页面加载进度条
function startLoading() {
  loadingInstance = ElLoading.service({
    lock: false,
    text: '',
    background: 'transparent',
    customClass: 'route-loading',
  })
}

function endLoading() {
  if (loadingInstance) {
    loadingInstance.close()
    loadingInstance = null
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

// 路由切换加载指示
router.beforeEach((to, from, next) => {
  if (to.path !== from.path) {
    startLoading()
  }
  next()
})

router.afterEach(() => {
  endLoading()
})

export default router
