import { onBeforeUnmount, ref } from 'vue'
import { engineeringResourceApi } from '@/api/engineering/resource'

export interface PlateLoadState {
  loading: boolean
  error: string
  plate?: any
}

/** 只补读当前页的网版资料；新分页与刷新立即使旧请求失效。 */
export function useCurrentPlates() {
  const states = ref<Record<number, PlateLoadState>>({})
  const stretches = ref<Record<number, { loading: boolean; error: string; stretch?: any }>>({})
  let generation = 0

  async function fetchPlate(id: number, frameId: number, current: number) {
    try {
      const response = await engineeringResourceApi.getPrintPlate(id)
      const plate = response.data as any
      if (!plate || Number(plate.plateId) !== id || Number(plate.frameId) !== frameId) {
        throw new Error('网版与网框关联资料不一致')
      }
      if (current === generation) states.value[id] = { loading: false, error: '', plate }
    } catch (error: any) {
      if (current === generation) states.value[id] = { loading: false, error: error.message || '网版资料读取失败' }
    }
  }

  async function fetchStretch(row: any, current: number) {
    const id = Number(row.frameId)
    try {
      const response = await engineeringResourceApi.getPrintFrame(id)
      const frame = response.data as any
      if (!frame || Number(frame.frameId) !== id || Number(frame.stretchId) !== Number(row.stretchId)) {
        throw new Error('绷网资料已变化，请刷新列表')
      }
      const stretch = frame.stretches?.find((item: any) => Number(item.stretchId) === Number(frame.stretchId))
      if (!stretch) throw new Error('当前绷网资料缺失')
      if (current === generation) stretches.value[id] = { loading: false, error: '', stretch }
    } catch (error: any) {
      if (current === generation) stretches.value[id] = { loading: false, error: error.message || '绷网资料读取失败' }
    }
  }

  function invalidate() {
    generation++
    states.value = {}
    stretches.value = {}
  }

  async function load(rows: any[]) {
    invalidate()
    const current = generation
    const pending: (() => Promise<void>)[] = []
    rows.forEach(row => {
      if (row.plateId) {
        states.value[Number(row.plateId)] = { loading: true, error: '' }
        pending.push(() => fetchPlate(Number(row.plateId), Number(row.frameId), current))
      }
      if (row.stretchId) {
        stretches.value[Number(row.frameId)] = { loading: true, error: '' }
        pending.push(() => fetchStretch(row, current))
      }
    })
    let next = 0
    await Promise.all(Array.from({ length: Math.min(4, pending.length) }, async () => {
      while (next < pending.length && current === generation) {
        await pending[next++]()
      }
    }))
  }

  async function retryStretch(row: any) {
    if (stretches.value[row.frameId]?.loading) return
    stretches.value[row.frameId] = { loading: true, error: '' }
    await fetchStretch(row, generation)
  }

  async function retry(row: any) {
    const id = Number(row.plateId)
    if (!id || states.value[id]?.loading) return
    states.value[id] = { loading: true, error: '' }
    await fetchPlate(id, Number(row.frameId), generation)
  }

  onBeforeUnmount(invalidate)
  return { states, stretches, load, retry, retryStretch, invalidate }
}
