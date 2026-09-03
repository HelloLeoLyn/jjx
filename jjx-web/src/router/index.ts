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
  // ============ 移动端 H5（扫码B，DEV-981） ============
  {
    path: '/m/login',
    name: 'MobileLogin',
    component: () => import('@/views/mobile/login.vue'),
    meta: {
      title: '移动登录',
      hidden: true,
    },
  },
  {
    path: '/m/scan',
    name: 'MobileScan',
    component: () => import('@/views/mobile/scan.vue'),
    meta: {
      title: '扫码定位',
      hidden: true,
    },
  },
  {
    path: '/m/order',
    name: 'MobileOrder',
    component: () => import('@/views/mobile/order.vue'),
    meta: {
      title: '工单任务',
      hidden: true,
    },
  },
  {
    path: '/m/report',
    name: 'MobileReport',
    component: () => import('@/views/mobile/report.vue'),
    meta: {
      title: '报工',
      hidden: true,
    },
  },
  {
    path: '/m/reports',
    name: 'MobileReports',
    component: () => import('@/views/mobile/reports.vue'),
    meta: {
      title: '我的报工',
      hidden: true,
    },
  },
  {
    path: '/m/quality',
    name: 'MobileQuality',
    component: () => import('@/views/mobile/quality.vue'),
    meta: {
      title: '质检判定',
      hidden: true,
    },
  },
  {
    path: '/m/pick',
    name: 'MobilePick',
    component: () => import('@/views/mobile/pick.vue'),
    meta: {
      title: '生产领料',
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
    path: '/sales/reconcile/print',
    name: 'SalesReconcilePrint',
    component: () => import('@/views/sales/reconcile/print.vue'),
    meta: { title: '业务对账单打印', hidden: true },
  },
  {
    path: '/sales/receipt/print/:id',
    name: 'SalesReceiptPrint',
    component: () => import('@/views/sales/receipt/print.vue'),
    meta: { title: '收款单打印', hidden: true },
  },
  {
    path: '/sales/invoice/print/:id',
    name: 'SalesInvoicePrint',
    component: () => import('@/views/sales/invoice/print.vue'),
    meta: { title: '销售发票打印', hidden: true },
  },
  {
    path: '/sales/delivery/print',
    name: 'SalesDeliveryPrint',
    component: () => import('@/views/sales/delivery/print.vue'),
    meta: { title: '送货单打印', hidden: true },
  },
  {
    path: '/sales/inquiry/print',
    name: 'SalesInquiryPrint',
    component: () => import('@/views/sales/inquiry/print.vue'),
    meta: { title: '样品需求单打印', hidden: true },
  },
  {
    path: '/sales/order/review-print',
    name: 'SalesOrderReviewPrint',
    component: () => import('@/views/sales/order/review-print.vue'),
    meta: { title: '订单评审表打印', hidden: true },
  },
  {
    path: '/purchase/plan/print',
    name: 'PurchasePlanPrint',
    component: () => import('@/views/purchase/plan/print.vue'),
    meta: { title: '采购计划打印', hidden: true },
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
    path: '/print/requirement/:id',
    name: 'RequirementEcnPrint',
    component: () => import('@/views/biz/requirement/print.vue'),
    meta: {
      title: '工程变更通知打印',
      hidden: true,
    },
  },
  {
    path: '/print/inbound/:id',
    name: 'InboundPrint',
    component: () => import('@/views/inventory/inbound/print.vue'),
    meta: {
      title: '入库单打印',
      hidden: true,
    },
  },
  {
    path: '/print/outbound/:id',
    name: 'OutboundPrint',
    component: () => import('@/views/inventory/outbound/print.vue'),
    meta: {
      title: '出库单打印',
      hidden: true,
    },
  },
  {
    path: '/print/purchase-order/:id',
    name: 'PurchaseOrderPrint',
    component: () => import('@/views/purchase/order/print.vue'),
    meta: {
      title: '采购订单打印',
      hidden: true,
    },
  },
  {
    path: '/print/production-order/:id',
    name: 'ProductionOrderPrint',
    component: () => import('@/views/production/order/print.vue'),
    meta: {
      title: '生产工单打印',
      hidden: true,
    },
  },
  {
    path: '/print/labels',
    name: 'LabelPrint',
    component: () => import('@/views/production/label-print/index.vue'),
    meta: {
      title: '标签打印',
      hidden: true,
    },
  },
  {
    path: '/print/quality/:id',
    name: 'QualityPrint',
    component: () => import('@/views/production/quality/print.vue'),
    meta: {
      title: '质检报告打印',
      hidden: true,
    },
  },
  {
    path: '/production/quality-template',
    name: 'ProductionQualityTemplate',
    component: () => import('@/layout/index.vue'),
    meta: {
      title: '质量记录模板',
      hidden: true,
    },
    children: [
      {
        path: '',
        name: 'ProductionQualityTemplateIndex',
        component: () => import('@/views/production/quality-template/index.vue'),
        meta: { title: '质量记录模板', hidden: true },
      },
    ],
  },
  {
    path: '/production/quality-print',
    name: 'ProductionQualityPrint',
    component: () => import('@/layout/index.vue'),
    meta: { title: '质量记录打印中心', hidden: true },
    children: [
      {
        path: '',
        name: 'ProductionQualityPrintIndex',
        component: () => import('@/views/production/quality-print/index.vue'),
        meta: { title: '质量记录打印中心', hidden: true },
      },
    ],
  },
  {
    path: '/production/quality-print/print',
    name: 'ProductionQualityPrintPage',
    component: () => import('@/views/production/quality-print/print.vue'),
    meta: { title: '质量记录打印', hidden: true },
  },
  {
    path: '/production/report/print/:id',
    name: 'ProductionWorkReportPrint',
    component: () => import('@/views/production/report/print.vue'),
    meta: { title: '报工单打印', hidden: true },
  },
  {
    path: '/production/quality-print/fqc-report', name: 'ProductionFqcReportPrint',
    component: () => import('@/views/production/quality-print/fqc-report.vue'),
    meta: { title: '成品检验报告', hidden: true },
  },
  {
    path: '/production/quality-print/iqc-report', name: 'ProductionIqcReportPrint',
    component: () => import('@/views/production/quality-print/iqc-report.vue'),
    meta: { title: '进料检验报告', hidden: true },
  },
  {
    path: '/production/quality-print/daily-report', name: 'ProductionDailyReportPrint',
    component: () => import('@/views/production/quality-print/daily-report.vue'),
    meta: { title: '生产日报表', hidden: true },
  },
  {
    path: '/production/quality-print/first-piece', name: 'ProductionFirstPiecePrint',
    component: () => import('@/views/production/quality-print/first-piece.vue'),
    meta: { title: '首件检查表', hidden: true },
  },
  {
    path: '/production/quality-print/rework-form', name: 'ProductionReworkFormPrint',
    component: () => import('@/views/production/quality-print/rework-form.vue'),
    meta: { title: '返工返修单', hidden: true },
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
  // 工程打样工作台（隐藏路由：打样平台按钮进入，侧边栏不显示，标签页打开）
  {
    path: '/engineering-workbench',
    component: () => import('@/layout/index.vue'),
    meta: { hidden: true },
    children: [
      {
        path: 'workbench',
        name: 'SampleWorkbenchPage',
        component: () => import('@/views/engineering/sample-workbench/workbench.vue'),
        meta: { hidden: true, title: '工程打样工作台', permission: 'engineering:sample:workbench' },
      },
    ],
  },
  // 打样转标准·对照版全屏页（隐藏路由：轻量版弹窗「进入标准编辑」跳转，侧边栏不显示）
  {
    path: '/sample/transfer',
    component: () => import('@/layout/index.vue'),
    meta: { hidden: true },
    children: [
      {
        path: 'edit',
        name: 'SampleTransferEdit',
        component: () => import('@/views/sales/sample-order/transfer-edit.vue'),
        meta: { hidden: true, title: '打样转标准·对照编辑', permission: 'sales:sample:convert' },
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
  // 移动端路由守卫（DEV-981 扫码B）：/m/* 需登录，未登录跳 /m/login
  if (to.path.startsWith('/m/') && to.path !== '/m/login') {
    const token = localStorage.getItem('token')
    if (!token) {
      next({ path: '/m/login', query: { redirect: to.fullPath } })
      return
    }
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
