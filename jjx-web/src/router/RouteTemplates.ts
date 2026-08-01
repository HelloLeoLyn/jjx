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
        path: 'order/detail/:orderId',
        name: 'SalesOrderDetail',
        component: () => import('@/views/sales/order/detail/index.vue'),
        meta: {
          title: '订单详情',
          icon: 'Document',
          permission: 'sales:order:view',
          hidden: true,
        },
      },
    ],
  },
  // 任务看板（jjx-kanban 合并）作为静态路由，放在首页下面
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
        },
      },
    ],
  },
]
