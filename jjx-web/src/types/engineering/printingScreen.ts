/** 网框/绷网/网版（dev-20261125-013，对接 P04 /engineering/printing）。 */

export interface ScreenFrame {
  frameId: number
  frameNo: string
  frameType?: string
  mesh?: number
  location?: string
  status?: string
  revision?: number
  [key: string]: any
}

export interface ScreenStretch {
  stretchId: number
  mesh?: number
  tensionNcm?: number
  material?: string
  qualified?: boolean
  stretchTime?: string
  endTime?: string | null
}

export interface ScreenPlate {
  plateId: number
  plateNo: string
  frameId?: number
  versionId?: number
  filmCopyId?: number
  stretchId?: number
  position?: string
  status?: string
  releasedTime?: string
  washedTime?: string
  revision?: number
  [key: string]: any
}

export interface ScreenHistoryRow {
  actionType?: string
  beforeStatus?: string
  afterStatus?: string
  description?: string
  operator?: string
  operateTime?: string
}

export interface ScreenFrameView extends ScreenFrame {
  stretches?: ScreenStretch[]
  stretchId?: number | null
  plateId?: number | null
  history?: ScreenHistoryRow[]
}

/** P04 统一命令体：requestId + expectedRevision 必填。 */
export interface ScreenCommand {
  requestId: string
  expectedRevision: number
  frameNo?: string
  frameType?: string
  location?: string
  mesh?: number
  tensionNcm?: number
  material?: string
  qualified?: boolean
  frameId?: number
  stretchId?: number
  filmId?: number
  versionId?: number
  plateNo?: string
  position?: string
  inspection?: string
  reason?: string
}
