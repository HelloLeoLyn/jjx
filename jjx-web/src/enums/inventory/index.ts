// src/enums/inventory/index.ts
import { WarehouseEnum } from './WarehouseEnum'
import { LocationEnum } from './LocationEnum'
import { StockEnum } from './StockEnum'
import { StockItemEnum } from './StockItemEnum'
import { TransactionEnum } from './TransactionEnum'
import { InboundEnum } from './InboundEnum'
import { OutboundEnum } from './OutboundEnum'
import { StocktakeEnum } from './StocktakeEnum'
import { TransferEnum } from './TransferEnum'
import { AlertEnum } from './AlertEnum'
import { MaterialEnum } from './MaterialEnum'

// 重新导出所有内容
export * from './WarehouseEnum'
export * from './LocationEnum'
export * from './StockEnum'
export * from './StockItemEnum'
export * from './TransactionEnum'
export * from './InboundEnum'
export * from './OutboundEnum'
export * from './StocktakeEnum'
export * from './TransferEnum'
export * from './AlertEnum'
export * from './MaterialEnum'

// 重新导出统一对象
export {
  WarehouseEnum,
  LocationEnum,
  StockEnum,
  StockItemEnum,
  TransactionEnum,
  InboundEnum,
  OutboundEnum,
  StocktakeEnum,
  TransferEnum,
  AlertEnum,
  MaterialEnum,
}

/**
 * 库存模块所有枚举的统一导出对象
 */
export const InventoryEnum = {
  material: MaterialEnum,
  warehouse: WarehouseEnum,
  location: LocationEnum,
  stock: StockEnum,
  stockItem: StockItemEnum,
  transaction: TransactionEnum,
  inbound: InboundEnum,
  outbound: OutboundEnum,
  stocktake: StocktakeEnum,
  transfer: TransferEnum,
  alert: AlertEnum,
}
