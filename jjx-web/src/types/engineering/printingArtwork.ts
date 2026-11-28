/** 图案/版本/实体底片（dev-20261125-012，对接 P03 /engineering/printing）。 */

export interface ArtworkVersion {
  versionId: number
  versionNo: string
  status?: string
  drawingFileId?: number | null
  drawingFileName?: string | null
  drawingFileType?: string | null
  contentDigest?: string | null
  contentAlgorithm?: string | null
  drawingSha256?: string | null
  technicalSpec?: string | null
  designerId?: string | null
  approvedBy?: string | null
  approvedTime?: string | null
  releasedBy?: string | null
  releasedTime?: string | null
  revision?: number
}

export interface ArtworkFilm {
  filmId: number
  versionId: number
  filmNo: string
  filmRequestId?: number | null
  location?: string | null
  status?: string
  material?: string | null
  thicknessMm?: number | null
  size?: string | null
  outputTime?: string | null
  destroyTime?: string | null
  revision?: number
}

export interface ArtworkView {
  artworkId: number
  artworkNo: string
  name: string
  productsText?: string | null
  purpose?: string | null
  family?: string | null
  setCode?: string | null
  sampleOrderId?: number | null
  defaultVersionId?: number | null
  revision?: number
  productIds?: number[]
  versions?: ArtworkVersion[]
  films?: ArtworkFilm[]
}

/** P03 统一命令体：requestId + expectedRevision 必填。 */
export interface ArtworkCommand {
  requestId: string
  expectedRevision: number
  artworkNo?: string
  name?: string
  purpose?: string
  family?: string
  setCode?: string
  productIds?: number[]
  sampleOrderId?: number
  versionNo?: string
  drawingFileId?: number
  drawingDocumentType?: string
  technicalSpec?: string
  reason?: string
  disablePrevious?: boolean
  impactToken?: string
  filmNo?: string
  location?: string
  material?: string
  thicknessMm?: number
  size?: string
  quantity?: number
  dueDate?: string
}

/** 菲林主档分页，不以图纸/版本/菲林片是否存在过滤主档。 */
export interface ArtworkListRow {
  artworkId: number
  artworkNo: string
  name: string
  family: string
  purpose?: string
  productsText?: string | null
  defaultVersionId?: number | null
  defaultVersionNo?: string | null
  releasedVersionCount?: number
  versionCount?: number
  copyCount?: number | null
  pendingFilmCount?: number | null
}

export interface FilmRequest {
  requestId: number
  requestNo: string
  versionId: number
  requestedQuantity: number
  receivedQuantity: number
  remainingQuantity: number
  progress: string
  material: string
  thicknessMm: number
  size: string
  dueDate: string
  remark?: string
  revision: number
  createBy?: string
  createTime?: string
}
export interface FilmRequestCommand {
  requestId: string
  expectedRevision: number
  quantity?: number
  material?: string
  thicknessMm?: number
  size?: string
  dueDate?: string
  remark?: string
  copies?: { filmNo: string; location: string }[]
}
