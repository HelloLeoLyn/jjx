import type { DeliveryArrangeLine } from '@/api/sales/delivery'

export type DeliveryQuantityLine = DeliveryArrangeLine & { sendQuantity: number | undefined }
const value = (quantity: number) => Math.max(0, Number(quantity) || 0)
const key = (line: DeliveryArrangeLine) => `${line.productId}:${line.orderId}`

/** 自有实预留先分配到各来源行，再分配共享成品，同产品预算只使用一次。 */
export function defaultDeliveryQuantities(lines: DeliveryArrangeLine[]): number[] {
  const owned = new Map<string, number>(), shared = new Map<number, number>(), product = new Map<number, number>()
  for (const line of lines) {
    owned.set(key(line), Math.min(owned.get(key(line)) ?? Infinity, value(line.ownStockAvailable)))
    shared.set(line.productId, Math.min(shared.get(line.productId) ?? Infinity, value(line.sharedStockAvailable)))
    product.set(line.productId, Math.min(product.get(line.productId) ?? Infinity, value(line.productStockAvailable)))
  }
  const quantities = lines.map(line => {
    const quantity = Math.floor(Math.min(line.availableQuantity, owned.get(key(line)) || 0, product.get(line.productId) || 0))
    owned.set(key(line), (owned.get(key(line)) || 0) - quantity)
    product.set(line.productId, (product.get(line.productId) || 0) - quantity)
    return quantity
  })
  lines.forEach((line, index) => {
    const additional = Math.floor(Math.min(line.availableQuantity - quantities[index]!, shared.get(line.productId) || 0, product.get(line.productId) || 0))
    quantities[index]! += additional
    shared.set(line.productId, (shared.get(line.productId) || 0) - additional)
    product.set(line.productId, (product.get(line.productId) || 0) - additional)
  })
  return quantities
}

export function deliveryQuantityError(lines: DeliveryQuantityLine[]): string {
  const products = new Map<number, { available: number; shared: number; orders: Map<number, { owned: number; quantity: number }> }>()
  for (const line of lines) {
    let product = products.get(line.productId)
    if (!product) {
      product = { available: value(line.productStockAvailable), shared: value(line.sharedStockAvailable), orders: new Map() }
      products.set(line.productId, product)
    }
    product.available = Math.min(product.available, value(line.productStockAvailable))
    product.shared = Math.min(product.shared, value(line.sharedStockAvailable))
    const order = product.orders.get(line.orderId) || { owned: value(line.ownStockAvailable), quantity: 0 }
    order.owned = Math.min(order.owned, value(line.ownStockAvailable))
    order.quantity += value(line.sendQuantity || 0)
    product.orders.set(line.orderId, order)
  }
  for (const product of products.values()) {
    const orders = [...product.orders.values()]
    const total = orders.reduce((sum, order) => sum + order.quantity, 0)
    const sharedNeeded = orders.reduce((sum, order) => sum + Math.max(0, order.quantity - order.owned), 0)
    if (total > product.available || sharedNeeded > product.shared) return '同产品明细合计超过可用成品量，请调整本次数量或移除明细'
  }
  return ''
}
