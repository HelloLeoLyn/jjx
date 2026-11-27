import { ArtworkVersionStatusEnum, FilmCopyStatusEnum } from '@/enums/product/film'
import { FilmPrototypeRequestProgressEnum as Progress } from '@/enums/product/filmPrototype'

export interface PrototypeVersion {
  id: number
  no: string
  status: string
  drawingName: string
  drawingUrl?: string
  drawingMime?: string
  technicalSpec: string
}
export interface PrototypeFilm {
  id: number
  no: string
  name: string
  family: string
  products: string
  purpose: string
  currentVersionId?: number
  versions: PrototypeVersion[]
}
export interface PrototypeRequest {
  id: number
  no: string
  filmId: number
  versionId: number
  quantity: number
  material: string
  thickness: number
  size: string
  dueDate: string
  note: string
}
export interface PrototypeCopy {
  id: number
  filmId: number
  versionId: number
  requestId?: number
  no: string
  location: string
  status: string
  material: string
  thickness: number
  size: string
  receivedDate: string
}
export interface PrototypeState {
  films: PrototypeFilm[]
  requests: PrototypeRequest[]
  copies: PrototypeCopy[]
}

export function createPrototypeState(): PrototypeState {
  return {
    films: [
      { id: 1, no: 'PW-001', name: '设备控制面板', family: '面板', products: 'P-1001 控制面板 / P-1002 控制面板B型', purpose: '面板文字与按键区域印刷', currentVersionId: 12, versions: [
        { id: 12, no: 'V2', status: ArtworkVersionStatusEnum.RELEASED.value, drawingName: 'PW-001-V2-面板图.svg', technicalSpec: '开关文字更新；外形尺寸 300 × 200 mm。' },
        { id: 11, no: 'V1', status: ArtworkVersionStatusEnum.DISABLED.value, drawingName: 'PW-001-V1-面板图.svg', technicalSpec: '原开关文字，保留历史图纸。' },
      ] },
      { id: 2, no: 'PW-002', name: '设备控制面板上层线路', family: '上线', products: 'P-1001 控制面板', purpose: '上层导电线路印刷', currentVersionId: 21, versions: [
        { id: 21, no: 'V1', status: ArtworkVersionStatusEnum.RELEASED.value, drawingName: 'PW-002-V1-线路图.svg', technicalSpec: '银浆线路；定位孔按图。' },
      ] },
      { id: 3, no: 'PW-003', name: '设备控制面板下层线路', family: '下线', products: 'P-1001 控制面板', purpose: '下层导电线路印刷', currentVersionId: 31, versions: [
        { id: 31, no: 'V1', status: ArtworkVersionStatusEnum.RELEASED.value, drawingName: 'PW-003-V1-线路图.svg', technicalSpec: '下层线路，注意方向标识。' },
      ] },
      { id: 4, no: 'PW-004', name: '温控器面板', family: '面板', products: 'P-2001 温控器', purpose: '新产品面板', versions: [
        { id: 41, no: 'V1', status: ArtworkVersionStatusEnum.DRAFT.value, drawingName: '', technicalSpec: '图纸待补充。' },
      ] },
      { id: 5, no: 'PW-005', name: '新款显示面板', family: '面板', products: 'P-3001 显示面板', purpose: '刚建立的菲林档案', versions: [] },
    ],
    requests: [
      { id: 1, no: 'CP-001', filmId: 1, versionId: 12, quantity: 2, material: 'PET', thickness: 0.125, size: '300 × 200 mm', dueDate: '2026-12-01', note: '两张分批回片。' },
      { id: 2, no: 'CP-002', filmId: 1, versionId: 11, quantity: 1, material: 'PET', thickness: 0.125, size: '300 × 200 mm', dueDate: '2026-11-20', note: '历史记录。' },
      { id: 3, no: 'CP-003', filmId: 2, versionId: 21, quantity: 1, material: 'PET', thickness: 0.1, size: '300 × 200 mm', dueDate: '2026-12-02', note: '' },
    ],
    copies: [
      { id: 1, no: 'FL-001', filmId: 1, versionId: 12, requestId: 1, location: 'A架 · 01格', status: FilmCopyStatusEnum.AVAILABLE.value, material: 'PET', thickness: 0.125, size: '300 × 200 mm', receivedDate: '2026-11-27' },
      { id: 2, no: 'FL-002', filmId: 1, versionId: 11, requestId: 2, location: '历史资料柜 · 02格', status: FilmCopyStatusEnum.ARCHIVED.value, material: 'PET', thickness: 0.125, size: '300 × 200 mm', receivedDate: '2026-11-20' },
    ],
  }
}

export function receivedCount(state: PrototypeState, request: PrototypeRequest): number {
  return state.copies.filter(copy => copy.requestId === request.id && copy.filmId === request.filmId && copy.versionId === request.versionId).length
}
export function requestProgress(state: PrototypeState, request: PrototypeRequest) {
  const received = receivedCount(state, request)
  return received === 0 ? Progress.PENDING : received < request.quantity ? Progress.PARTIAL : Progress.RECEIVED
}

export function addRequest(state: PrototypeState, input: Omit<PrototypeRequest, 'id' | 'no'>): PrototypeRequest {
  const film = state.films.find(row => row.id === input.filmId)
  const version = film?.versions.find(row => row.id === input.versionId)
  if (!version || version.status !== ArtworkVersionStatusEnum.RELEASED.value) throw new Error('请选择该菲林已发布的版本')
  if (!Number.isInteger(input.quantity) || input.quantity < 1) throw new Error('申请张数须为正整数')
  if (!input.material.trim() || !input.size.trim() || !input.dueDate || !Number.isFinite(input.thickness) || input.thickness <= 0) throw new Error('请填写材质、厚度、尺寸和回片日期')
  const id = Math.max(0, ...state.requests.map(row => row.id)) + 1
  const request = { ...input, material: input.material.trim(), size: input.size.trim(), id, no: `CP-${String(id).padStart(3, '0')}` }
  state.requests.unshift(request)
  return request
}

export function receiveCopies(state: PrototypeState, requestId: number, rows: { no: string; location: string }[]): void {
  const request = state.requests.find(row => row.id === requestId)
  if (!request) throw new Error('找不到这条出片申请')
  if (!rows.length || rows.length > request.quantity - receivedCount(state, request)) throw new Error('本次回片张数不能超过未回张数')
  const existing = new Set(state.copies.map(row => row.no.toUpperCase()))
  for (const row of rows) {
    const no = row.no.trim().toUpperCase()
    if (!no || !row.location.trim()) throw new Error('每张菲林片都要填写编号和位置')
    if (existing.has(no)) throw new Error(`菲林片编号 ${row.no.trim()} 已存在或重复`)
    existing.add(no)
  }
  const firstId = Math.max(0, ...state.copies.map(row => row.id)) + 1
  state.copies.push(...rows.map((row, index): PrototypeCopy => ({
    id: firstId + index, no: row.no.trim(), location: row.location.trim(), filmId: request.filmId,
    versionId: request.versionId, requestId, status: FilmCopyStatusEnum.AVAILABLE.value,
    material: request.material, thickness: request.thickness, size: request.size, receivedDate: new Date().toLocaleDateString('sv-SE'),
  })))
}
