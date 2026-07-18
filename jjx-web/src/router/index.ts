import { createRouter, createWebHistory } from 'vue-router'
import type { RouteRecordRaw } from 'vue-router'
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
  // 演示页面
  {
    path: '/demo',
    name: 'Demo',
    component: () => import('@/layout/index.vue'),
    redirect: '/demo/jjx-icon',
    meta: {
      title: '演示',
      icon: 'Tools',
      hidden: false,
      sort: 99,
    },
    children: [
      {
        path: 'jjx-icon',
        name: 'DemoJJXIcon',
        component: () => import('@/views/demo/jjx-icon-demo.vue'),
        meta: {
          title: 'JJX图标演示',
          icon: 'PictureFilled',
        },
      },
      {
        path: 'permission',
        name: 'DemoPermission',
        component: () => import('@/views/demo/permission-demo.vue'),
        meta: {
          title: '权限演示',
          icon: 'PictureFilled',
        },
      },
    ],
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

export default router
