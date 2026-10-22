import { assignMaterial, materialOwner } from './sampleProcessGroups'
import { computed, reactive, ref, type Ref } from 'vue'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { materialApi } from '@/api/inventory/material'
import { debounce } from 'lodash-es'
import { materialValues } from './planDraft'
import { normalizeMaterial, projectIdsOf } from './sampleMaterials'

interface SampleBomContext {
  orderId: Readonly<Ref<number>>
  card: Ref<any>
  planList: Ref<any[]>
  printList: Ref<any[]>
  readonlyMode: Readonly<Ref<boolean>>
  markOwnerDirty?: (pc: any) => void
}

interface BomRowState {
  options: any[]
  loading: boolean
  total: number
  pageNum: number
  kw: string
}

/**
 * BOM 是「卡片材料草稿 + 独立新增材料」的同一份草稿投影：
 * - 卡片行（ownerUid 非空）写回对应卡片的 materialRows/materials；
 * - 独立行（ownerUid 为空）写回 standaloneMaterials，随计划一起保存回读。
 * 搜索态按 uid 存在独立的 reactive 表里，避免投影行每次重算丢失下拉状态。
 */
export function useSampleBom(context: SampleBomContext) {
  const { orderId, card, planList, printList, readonlyMode, markOwnerDirty } = context
  const standaloneMaterials = ref<any[]>([])
  const owners = () => [...planList.value, ...printList.value]

  const searchStates = reactive<Record<string, BomRowState>>({})
  function stateOf(uid: string): BomRowState {
    if (!searchStates[uid]) {
      searchStates[uid] = { options: [], loading: false, total: 0, pageNum: 1, kw: '' }
    }
    return searchStates[uid]
  }

  /** 用于渲染/写回的源数组：编辑态用 materialRows，否则用已存材料的解析结果。 */
  function rowsFor(pc: any): any[] {
    return pc.editing && Array.isArray(pc.materialRows) ? pc.materialRows : materialValues(pc)
  }

  function applyRows(pc: any, rows: any[]) {
    if (pc.editing) {
      pc.materialRows = rows
    } else {
      const kept = rows.filter((r: any) => String(r.name || '').trim())
      pc.materials = kept.length ? JSON.stringify(kept.map(stripUi)) : null
    }
  }

  function stripUi(row: any) {
    const { options, loading, uid, pageNum, total, lastQuery, _matKw, projectInherited, ...value } = row
    return value
  }

  // 稳定行 uid 缓存（内容键 → uid）：删行后索引位移不串搜索态（dev-20261015-026）
  const uidCache = new Map<string, string>()
  let uidSeq = 0
  function stableUid(key: string): string {
    let uid = uidCache.get(key)
    if (!uid) {
      uid = `bm${++uidSeq}`
      if (uidCache.size > 1000) uidCache.clear()
      uidCache.set(key, uid)
    }
    return uid
  }

  const bomList = computed(() => {
    const out: any[] = []
    for (const pc of owners()) {
      rowsFor(pc).forEach((row, index) => {
        if (!String(row?.name || '').trim()) return
        // 稳定 uid：按内容键缓存，删行后索引位移不会串搜索态（dev-20261015-026）
        const key = `${pc.uid}|${row.processItemKey || ''}|${row.materialId ?? ''}|${row.name ?? ''}|${row.spec ?? ''}`
        out.push({
          ...normalizeMaterial(row, projectIdsOf(pc)),
          ownerUid: pc.uid,
          srcIndex: index,
          uid: stableUid(key),
        })
      })
    }
    standaloneMaterials.value.forEach((row, index) => {
      const key = `bom|${row.materialId ?? ''}|${row.name ?? ''}|${row.spec ?? ''}`
      out.push({ ...normalizeMaterial(row), ownerUid: null, srcIndex: index, uid: stableUid(key) })
    })
    return out
  })

  function updateMaterial(row: any) {
    if (readonlyMode.value) return
    const { ownerUid, srcIndex, uid, ...value } = row
    if (ownerUid == null) {
      const arr = [...standaloneMaterials.value]
      if (srcIndex < 0 || srcIndex >= arr.length) return
      arr[srcIndex] = normalizeMaterial(value)
      standaloneMaterials.value = arr
      return
    }
    const pc = owners().find((p) => p.uid === ownerUid)
    if (!pc) return
    const rows = [...rowsFor(pc)]
    if (srcIndex < 0 || srcIndex >= rows.length) return
    const previous = rows[srcIndex]
    const next = { ...previous, ...normalizeMaterial(value, projectIdsOf(pc)) }
    if (pc.items && JSON.stringify(previous.projectIds || []) !== JSON.stringify(next.projectIds || [])) {
      delete next.processItemKey
      const owner = materialOwner(pc, next)
      if (owner) assignMaterial(next, owner)
    }
    rows[srcIndex] = next
    applyRows(pc, rows)
    markOwnerDirty?.(pc)
  }

  function removeMaterial(row: any) {
    if (readonlyMode.value) return
    if (row.ownerUid == null) {
      const arr = [...standaloneMaterials.value]
      if (row.srcIndex < 0 || row.srcIndex >= arr.length) return
      arr.splice(row.srcIndex, 1)
      standaloneMaterials.value = arr
      return
    }
    const pc = owners().find((p) => p.uid === row.ownerUid)
    if (!pc) return
    const rows = [...rowsFor(pc)]
    rows.splice(row.srcIndex, 1)
    applyRows(pc, rows)
    markOwnerDirty?.(pc)
  }

  function addMaterial() {
    if (readonlyMode.value) return
    standaloneMaterials.value.push({
      name: '',
      spec: '',
      unit: 'PCS',
      qty: 1,
      baseQty: 1,
      moduleQty: 1,
      projectIds: [],
      projectInherited: false,
      materialId: undefined,
      materialCode: '',
      layerName: '',
    })
  }

  function clearStandalone() {
    standaloneMaterials.value = []
    Object.keys(searchStates).forEach((k) => delete searchStates[k])
    uidCache.clear()
  }

  // ===== BOM 行内材料搜索（复用物料档案接口，分页） =====
  async function searchBomMaterials(row: any, query: string, pageNum = 1, append = false) {
    const state = stateOf(row.uid)
    state.kw = (query || '').trim()
    state.loading = true
    try {
      const params: any = { pageNum, pageSize: 20 }
      if (state.kw) params.materialName = state.kw
      const res: any = await materialApi.search(params)
      const records = res?.data?.records || res?.data || []
      if (append) {
        const seen = new Set((state.options || []).map((o: any) => o.materialId))
        for (const r of records) {
          if (!seen.has(r.materialId)) {
            state.options.push(r)
            seen.add(r.materialId)
          }
        }
      } else {
        state.options = records
      }
      state.total = res?.data?.total ?? records.length
      state.pageNum = pageNum
    } catch {
      if (!append) state.options = []
    } finally {
      state.loading = false
    }
  }

  const debouncers = new Map<string, ReturnType<typeof debounce>>()
  function debouncedSearchBomMaterials(row: any, query: string) {
    const key = row.uid
    let fn = debouncers.get(key)
    if (!fn) {
      fn = debounce((q: string, r: any) => {
        debouncers.delete(key)
        searchBomMaterials(r, q, 1, false)
      }, 300)
      debouncers.set(key, fn)
    }
    fn(query, row)
  }

  function onBomMaterialSelected(row: any, materialId: number) {
    if (readonlyMode.value) return
    const state = stateOf(row.uid)
    const mat = (state.options || []).find((o: any) => o.materialId === materialId)
    if (!mat) return
    row.name = mat.materialName
    row.spec = mat.specification || ''
    row.unit = mat.unit || 'PCS'
    row.materialCode = mat.materialCode || ''
    row.materialId = mat.materialId
    updateMaterial(row)
  }

  async function onBomVisible(row: any, visible: boolean) {
    if (!visible) return
    const state = stateOf(row.uid)
    if (!state.options.length && !state.loading) {
      await searchBomMaterials(row, state.kw || '', 1, false)
    }
  }

  async function loadBom() {
    if (!orderId.value) return
    const res = await sampleOrderApi.listBom(orderId.value)
    standaloneMaterials.value = (res.data || [])
      .filter((row: any) => row.roundNo === (card.value.sampleRound || 1))
      .map((row: any) => {
        let projectIds: number[] = []
        try {
          const parsed = JSON.parse(row.projectIds || '[]')
          if (Array.isArray(parsed)) projectIds = parsed
        } catch {
          projectIds = []
        }
        return normalizeMaterial({
          name: row.materialName,
          spec: row.specification,
          qty: row.quantity,
          unit: row.unit,
          materialId: row.materialId,
          materialCode: row.materialCode,
          baseQty: row.baseQty,
          moduleQty: row.moduleQty,
          split: row.split,
          projectIds,
          projectInherited: false,
          layerName: row.layerName || '',
        })
      })
  }

  return {
    standaloneMaterials,
    bomList,
    stateOf,
    loadBom,
    updateMaterial,
    removeMaterial,
    addMaterial,
    clearStandalone,
    searchBomMaterials,
    debouncedSearchBomMaterials,
    onBomMaterialSelected,
    onBomVisible,
  }
}
