import request from '@/utils/request'
import type { R } from '@/types'

export interface ExchangeRateSnapshot {
  base: string
  source: 'live' | 'fallback'
  rates: Record<string, number>
}

export function getLatestExchangeRates() {
  return request.get<R<ExchangeRateSnapshot>>('/system/exchange-rate/latest')
}
