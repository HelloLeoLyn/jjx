import request from '@/utils/request'
import type { R, PageResult } from '@/types'

/** 印刷资源搜索候选（dev-20261125-011） */
export interface PrintSearchRow {
  artworkId?: number
  artworkNo?: string
  name?: string
  versionId?: number
  versionNo?: string
  versionStatus?: string
  filmId?: number
  filmNo?: string
  filmStatus?: string
  plateId?: number
  sourcesJson?: string | null
  layout?: string | null
  plateNo?: string
  plateStatus?: string
  frameId?: number
  frameNo?: string
  frameStatus?: string
  qualified?: boolean
  matchedType?: string
  matchedCode?: string
  displayName?: string
  productCode?: string
  productName?: string
  purpose?: string
  stretchId?: number
  stretchTime?: string
  stretchMaterial?: string
  mesh?: number
  tensionNCm?: number
  position?: string
  availability?: boolean
  reasonCodes?: string[]
}

export interface PrintSearchParams {
  q: string
  pageNum: number
  pageSize: number
  scene?: string
  productId?: number
  sampleOrderId?: number
  specVersionId?: number
  routingItemId?: number
  executionId?: number
}

export const printingSearchApi = {
  plate(id: number) {
    return request.get<R<PrintSearchRow>>(`/engineering/printing/search/plates/${id}`)
  },
  search(params: PrintSearchParams) {
    return request.get<R<PageResult<PrintSearchRow>>>('/engineering/printing/search', { params })
  },
}
