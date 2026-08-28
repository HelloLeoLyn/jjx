import type { Router } from 'vue-router'

export type LabelType = 'product' | 'material' | 'box'

export interface LabelPrintPayload {
  type: LabelType
  productIds?: number[]
  materialIds?: number[]
  outboundId?: string
  data?: unknown[]
}

const STORAGE_PREFIX = 'jjx:label-print:'

export function openLabelPrint(router: Router, payload: LabelPrintPayload) {
  const key = `${Date.now()}-${Math.random().toString(36).slice(2)}`
  sessionStorage.setItem(`${STORAGE_PREFIX}${key}`, JSON.stringify(payload))
  const href = router.resolve({
    path: '/print/labels',
    query: {
      key,
      type: payload.type,
      productIds: payload.productIds?.join(','),
      materialIds: payload.materialIds?.join(','),
      outboundId: payload.outboundId,
    },
  }).href
  // 保留同源 opener，使新窗口可读取当前标签批次的 sessionStorage 数据。
  window.open(href, '_blank')
}

export function readLabelPrintPayload(key: string): LabelPrintPayload | null {
  const raw = sessionStorage.getItem(`${STORAGE_PREFIX}${key}`)
  if (!raw) return null
  sessionStorage.removeItem(`${STORAGE_PREFIX}${key}`)
  try {
    return JSON.parse(raw) as LabelPrintPayload
  } catch {
    return null
  }
}
