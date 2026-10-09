import { ref } from 'vue'
import { suggestSampleColors, suggestSampleInks } from '@/api/sales/sampleOrder'
import { engineeringResourceApi } from '@/api/engineering/resource'
import { filmApi } from '@/api/product/film'

/**
 * 印刷工序字段联想（公共）——dev-20261009-026
 * 统一 色号 / 油墨 / 菲林 / 网框 的联想数据源，供 工艺路线·印刷工序、
 * 打样工作台·印刷工序、产品作业规范③ 等复用，避免各处各写一份。
 */
export interface PrintSuggestItem {
  value: string
  hint?: string
  statusLabel?: string
  materialId?: number | null
}

const FRAME_STATUS_LABEL: Record<string, string> = {
  EMPTY: '空框',
  PLATED: '已制版',
  MAINTENANCE: '维护中',
  SCRAPPED: '已报废',
}

export function frameStatusLabel(s: string): string {
  return FRAME_STATUS_LABEL[s] || s || ''
}

// 网框状态标签（异常高亮，供工序行"变动可感知"）
const FRAME_STATUS_TAG: Record<string, { label: string; type: 'success' | 'warning' | 'danger' | 'info' }> = {
  EMPTY: { label: '未制版', type: 'warning' },
  PLATED: { label: '已制版', type: 'success' },
  MAINTENANCE: { label: '维护中', type: 'warning' },
  SCRAPPED: { label: '已报废', type: 'danger' },
}

/** 网框编号兼容读取：接口返回下划线 frame_no（旧代码误读驼峰 frameNo）。 */
function frameNoOf(f: any): string {
  return String(f?.frame_no ?? f?.frameNo ?? '')
}

/** 按网框编号反查当前状态标签（未命中返回 null，兼容旧手输值） */
export function frameStatusOf(frameNo: string): { label: string; type: string } | null {
  if (!frameNo) return null
  const f = frameCache.value.find((x: any) => frameNoOf(x) === frameNo)
  if (!f) return null
  return FRAME_STATUS_TAG[f.status] || { label: frameStatusLabel(f.status), type: 'info' }
}

// 网框台账本地缓存（进程内复用）
const frameCache = ref<any[]>([])
const frameLoaded = ref(false)

export async function ensureFrames() {
  if (frameLoaded.value) return
  frameLoaded.value = true
  try {
    const res: any = await engineeringResourceApi.frames({ pageNum: 1, pageSize: 1000 })
    frameCache.value = res?.data?.records || []
  } catch {
    frameCache.value = []
  }
}

export async function suggestColors(query: string, cb: (items: PrintSuggestItem[]) => void) {
  try {
    const res: any = await suggestSampleColors(query || undefined, 10)
    cb((res?.data || []).map((value: string) => ({ value })))
  } catch {
    cb([])
  }
}

export async function suggestInks(query: string, cb: (items: PrintSuggestItem[]) => void) {
  try {
    const res: any = await suggestSampleInks(query || undefined, 10)
    cb((res?.data || []).map((x: any) => ({ value: x?.text ?? x, materialId: x?.materialId ?? null })))
  } catch {
    cb([])
  }
}

export async function suggestFilms(query: string, cb: (items: PrintSuggestItem[]) => void) {
  try {
    const res: any = await filmApi.list({ keyword: query || undefined })
    const list: any[] = res?.data?.records || res?.data || []
    cb(
      list.slice(0, 20).map((f: any) => ({
        value: f.filmCode,
        hint: [f.filmName, f.filmTypeName, f.version].filter(Boolean).join(' '),
      }))
    )
  } catch {
    cb([])
  }
}

export async function suggestFrames(query: string, cb: (items: PrintSuggestItem[]) => void) {
  const q = (query || '').trim()
  try {
    // 远端模糊搜索（dev-20261009-053）：台账 7291 条，本地只缓存了 1000 条，必须走接口
    const res: any = await engineeringResourceApi.frames({
      keyword: q || undefined,
      pageNum: 1,
      pageSize: 20,
    })
    const list: any[] = res?.data?.records || []
    if (list.length || q) {
      cb(list.map((f: any) => ({ value: frameNoOf(f), statusLabel: frameStatusLabel(f.status) })))
      return
    }
  } catch {
    // 接口异常时回退到本地缓存
  }
  const lq = q.toLowerCase()
  const list = frameCache.value
    .filter((f: any) => !lq || frameNoOf(f).toLowerCase().includes(lq))
    .slice(0, 20)
  cb(list.map((f: any) => ({ value: frameNoOf(f), statusLabel: frameStatusLabel(f.status) })))
}

export type PrintFieldKey = 'colorNo' | 'inkNo' | 'filmNo' | 'screenNo'

export const PRINT_FIELD_SUGGESTERS: Record<
  PrintFieldKey,
  (q: string, cb: (items: PrintSuggestItem[]) => void) => void
> = {
  colorNo: suggestColors,
  inkNo: suggestInks,
  filmNo: suggestFilms,
  screenNo: suggestFrames,
}

export function usePrintFieldSuggest() {
  return { ensureFrames, suggestColors, suggestInks, suggestFilms, suggestFrames, frameStatusLabel }
}
