import { DeliveryStatusEnum } from '@/enums/sales/DeliveryEnum'
import { InspectionResult } from '@/enums/quality/InspectionEnum'

export interface MockDeliveryLine {
  id: number
  orderId: number
  orderNo: string
  customerOrderNo: string
  customerMaterialNo: string
  productCode: string
  productName: string
  quantity: number
  shipped: number
  occupied: number
  unitPrice: number
  unitWeight: number
  inspectionResult: string
}
export interface MockDeliveryOrder {
  id: number
  customerId: number
  addressId: number
  orderNo: string
  customerOrderNo: string
  dueDate: string
  currency: string
  lines: MockDeliveryLine[]
}
export interface MockMergedDelivery {
  deliveryNo: string
  deliveryStatus: number
  customerId: number
  addressId: number
  deliveryDate: string
  remark: string
  showAmount: boolean
  showWeight: boolean
  lines: Array<MockDeliveryLine & { sendQuantity: number | undefined }>
}
export const mockCustomers = [
  {
    id: 1,
    name: '友隆电器工业（深圳）有限公司',
    addresses: [
      {
        id: 11,
        label: '深圳工厂 · 仓库',
        address: '深圳市宝安区 · 友隆电器仓库（演示地址）',
        contact: '仓库收货员',
        phone: '135 9017 9711',
      },
      {
        id: 12,
        label: '东莞工厂 · 仓库',
        address: '东莞市长安镇 · 收货仓库（演示地址）',
        contact: '陈先生',
        phone: '138 0000 0022',
      },
    ],
  },
  {
    id: 2,
    name: '华星电子有限公司（演示）',
    addresses: [
      {
        id: 21,
        label: '深圳总仓',
        address: '深圳市龙岗区 · 华星总仓（演示地址）',
        contact: '李女士',
        phone: '139 0000 0033',
      },
    ],
  },
]
const makeLines = (
  orderId: number,
  orderNo: string,
  customerOrderNo: string,
  start: number,
  qty: number
) =>
  ['控制铭板', '上导风铭板', '中导风铭板'].map(
    (name, index): MockDeliveryLine => ({
      id: orderId * 10 + index,
      orderId,
      orderNo,
      customerOrderNo,
      customerMaterialNo: `B31-69006032-${String(start + index).padStart(2, '0')}`,
      productCode: `YL-DD016-${start + index}`,
      productName: `DD016-P0C0${name}`,
      quantity: qty + (index === 0 ? 100 : 0),
      shipped: index === 0 ? 60 : 0,
      occupied: index === 0 ? 40 : 0,
      unitPrice: 2.5 + index * 0.35,
      unitWeight: 8.2 + index * 0.4,
      inspectionResult: index === 2 ? InspectionResult.PENDING : InspectionResult.PASS,
    })
  )
export const mockOrders: MockDeliveryOrder[] = [
  {
    id: 101,
    customerId: 1,
    addressId: 11,
    orderNo: 'JY260843X',
    customerOrderNo: 'PO26090213',
    dueDate: '2026-10-08',
    currency: 'CNY',
    lines: makeLines(101, 'JY260843X', 'PO26090213', 1, 636),
  },
  {
    id: 102,
    customerId: 1,
    addressId: 11,
    orderNo: 'JY260677X',
    customerOrderNo: 'PO26082193',
    dueDate: '2026-10-12',
    currency: 'CNY',
    lines: makeLines(102, 'JY260677X', 'PO26082193', 5, 900),
  },
  {
    id: 103,
    customerId: 1,
    addressId: 12,
    orderNo: 'JY260901X',
    customerOrderNo: 'PO26100008',
    dueDate: '2026-10-10',
    currency: 'CNY',
    lines: makeLines(103, 'JY260901X', 'PO26100008', 8, 300),
  },
  {
    id: 201,
    customerId: 2,
    addressId: 21,
    orderNo: 'SO261008-DEMO',
    customerOrderNo: 'HX-PO-1008',
    dueDate: '2026-10-11',
    currency: 'CNY',
    lines: makeLines(201, 'SO261008-DEMO', 'HX-PO-1008', 1, 200),
  },
]
export const availableQuantity = (line: MockDeliveryLine) =>
  Math.max(0, line.quantity - line.shipped - line.occupied)
export const mockPendingStatus = DeliveryStatusEnum.PENDING.value
