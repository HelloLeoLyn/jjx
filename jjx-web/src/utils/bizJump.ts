import type { Router, RouteLocationRaw } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'

export interface BizJumpTarget {
  path: string
  query?: Record<string, string | number>
  hash?: string
}

const DIRECT_PATHS = new Set([
  '/sales/quotation',
  '/sales/order',
  '/purchase/order',
  '/inventory/inbound',
  '/inventory/outbound',
])

/**
 * jumpPath 优先；下方前缀表冻结为旧通知 fallback，新事件一律配置于 sys_event_config。
 * 实际跳转必须经过 resolveJumpSafe 校验当前账号路由。
 */
export function resolveJump(
  eventCode: string,
  bizId?: string | number | null,
  jumpPath?: string | null
): BizJumpTarget | null {
  if (jumpPath?.trim()) {
    // 不把已配置但不可达的路径偷偷换成旧映射，交由安全出口明确提示。
    if (!/^\/(?!\/)[A-Za-z0-9_/-]+(?:\?[^#\s\\{}]*)?(?:#[^\s\\{}]*)?$/.test(jumpPath)) return null
    const url = new URL(jumpPath, 'https://jump.local')
    return { path: url.pathname, query: Object.fromEntries(url.searchParams), hash: url.hash }
  }
  const mappings: Array<[predicate: (code: string) => boolean, path: string]> = [
    [(code) => code.startsWith('quotation.'), '/sales/quotation'],
    [(code) => code.startsWith('order.'), '/sales/order'],
    [(code) => code.startsWith('inquiry.'), '/sales/inquiry'],
    [(code) => code.startsWith('sample.'), '/sales/sample-order'],
    [(code) => code.startsWith('purchase.'), '/purchase/order'],
    [(code) => code.startsWith('inventory.inbound.'), '/inventory/inbound'],
    [(code) => code.startsWith('inventory.outbound.'), '/inventory/outbound'],
    [(code) => code.startsWith('inventory.transfer.'), '/inventory/transfer'],
    [(code) => code.startsWith('inventory.stocktake.'), '/inventory/stocktake'],
    [(code) => code.startsWith('inventory.material.'), '/inventory/material'],
    [
      (code) => code.startsWith('inventory.warehouse.') || code.startsWith('storage_location.'),
      '/inventory/warehouse/list',
    ],
    [
      (code) => code.startsWith('stock.') || code === 'inventory.alert.processed',
      '/inventory/alert',
    ],
    [(code) => code.startsWith('product.routing.'), '/engineering/route'],
    [(code) => code.startsWith('product.film.'), '/engineering/resource/film'],
    [(code) => code.startsWith('quality.iqc.'), '/inventory/iqc'],
    ...(['iqc', 'fqc', 'oqc'] as const).map((type): [(code: string) => boolean, string] => [
      (code) =>
        code.startsWith('quality.lot.') &&
        code.slice('quality.lot.'.length).split('.').includes(type),
      `/quality/lot/${type}`,
    ]),
    [(code) => code.startsWith('quality.ncr.'), '/quality/ncr'],
    [(code) => code.startsWith('quality.scrap.'), '/quality/scrap-order'],
    [(code) => code.startsWith('quality.capa.'), '/quality/capa'],
    [(code) => code.startsWith('bom.'), '/engineering/bom'],
    [(code) => code.startsWith('product.'), '/product/list'],
    [(code) => code.startsWith('production.'), '/production/order'],
    [(code) => code.startsWith('biz.requirement.'), '/biz/requirement'],
    [(code) => code.startsWith('sales.customer.'), '/sales/customer'],
  ]

  const path = mappings.find(([matches]) => matches(eventCode))?.[1]
  if (!path) return null
  if (DIRECT_PATHS.has(path) && bizId !== null && bizId !== undefined && String(bizId) !== '') {
    return { path, query: { bizId } }
  }
  return { path }
}

/** 检查最终页面，避免静态重定向、纯分组或 catch-all 被误判为有权限的业务页。 */
function isReachable(router: Router, target: RouteLocationRaw): boolean {
  const visited = new Set<string>()
  for (let depth = 0; depth < 10; depth++) {
    const route = router.resolve(target)
    if (visited.has(route.fullPath)) return false
    visited.add(route.fullPath)
    const leaf = route.matched[route.matched.length - 1]
    if (
      !leaf ||
      /:(?:pathMatch|catchAll)\b/.test(leaf.path) ||
      ['/404', '/401', '/login'].includes(route.path)
    )
      return false
    if (leaf.redirect) {
      const redirect =
        typeof leaf.redirect === 'function'
          ? leaf.redirect(route, router.currentRoute.value)
          : leaf.redirect
      target = typeof redirect === 'string' ? redirect : { ...redirect }
      continue
    }
    return Boolean(leaf.components && Object.keys(leaf.components).length)
  }
  return false
}

/** 无权访问时停留原页，由用户选择经过同样校验的落点。 */
export async function resolveJumpSafe(
  router: Router,
  eventCode: string,
  bizId?: string | number | null,
  fallbackPath?: string | null,
  jumpPath?: string | null
): Promise<BizJumpTarget | null> {
  const target =
    resolveJump(eventCode, bizId, jumpPath) ||
    (!jumpPath?.trim() && fallbackPath ? { path: fallbackPath } : null)
  if (target && isReachable(router, target)) return target

  const fallback = [fallbackPath, '/dashboard/index']
    .filter((path): path is string => Boolean(path) && path !== target?.path)
    .find((path) => isReachable(router, { path }))
  const message = '当前账号无该模块权限（或页面不存在）'
  if (!fallback) {
    ElMessage.warning(message)
    return null
  }
  const label = fallback === '/dashboard/index' ? '首页' : '模块列表'
  try {
    await ElMessageBox.confirm(`${message}，可前往${label}。`, '无法跳转', {
      type: 'warning',
      confirmButtonText: `前往${label}`,
      cancelButtonText: '留在当前页',
    })
    // 弹窗期间账号路由可能发生变化，确认后再次检查。
    return isReachable(router, { path: fallback }) ? { path: fallback } : null
  } catch (action) {
    if (action !== 'cancel' && action !== 'close') throw action
    return null
  }
}

export function resolveModulePage(module: string): string | null {
  const paths: Record<string, string> = {
    sales: '/sales/order',
    purchase: '/purchase/order',
    inventory: '/inventory/stock',
    product: '/product/list',
    production: '/production/order',
    sample: '/sales/sample-order',
    biz: '/biz/requirement',
  }
  return paths[module] ?? null
}
