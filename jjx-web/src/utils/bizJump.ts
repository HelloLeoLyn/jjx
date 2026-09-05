export interface BizJumpTarget {
  path: string
  query?: Record<string, string | number>
}

const DIRECT_PATHS = new Set([
  '/sales/quotation',
  '/sales/order',
  '/purchase/order',
  '/inventory/inbound',
  '/inventory/outbound',
])

/**
 * 路径已按现有菜单、静态路由和 router.push 用法核对。
 * /product/route 是兼容入口，会由静态路由重定向到 /engineering/route；
 * 菲林、BOM 的菜单实际位于 /engineering/film、/engineering/bom。
 */
export function resolveJump(
  eventCode: string,
  bizId?: string | number | null
): BizJumpTarget | null {
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
      '/inventory/warehouse',
    ],
    [
      (code) => code.startsWith('stock.') || code === 'inventory.alert.processed',
      '/inventory/alert',
    ],
    [(code) => code.startsWith('product.routing.'), '/product/route'],
    [(code) => code.startsWith('product.film.'), '/engineering/film'],
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
