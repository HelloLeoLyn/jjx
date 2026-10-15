import { createCardEditSessions } from './planDraft'
import { ref, nextTick, onScopeDispose, type Ref } from 'vue'
import { debounce } from 'lodash-es'
import { materialApi } from '@/api/inventory/material'

/** 工序材料输入、搜索及建档回填；材料草稿仍归工序计划所有。 */
export function useProcessMaterials(readonlyMode: Readonly<Ref<boolean>>, genUid: () => string) {
  const editSessions = createCardEditSessions()
  function cancelEdit(pc: any) { if (!readonlyMode.value) editSessions.cancel(pc) }
  function finishEdit(pc: any) { if (!readonlyMode.value) editSessions.finish(pc) }

  // 材料行
  function addMaterialRow(pc: any) {
    if (readonlyMode.value) return
    pc.materialRows.push({
      name: '',
      spec: '',
      qty: 1,
      unit: 'PCS',
      materialId: undefined as number | undefined,
      materialCode: '',
      options: [],
      loading: false,
      uid: genUid(),
      pageNum: 1,
      total: 0,
      lastQuery: '',
    })
  }

  // 进入编辑：初始化材料行
  function startEdit(pc: any) {
    if (readonlyMode.value) return
    editSessions.begin(pc)
    pc.editing = true
    if (!pc.materialRows.length) {
      pc.materialRows = (parseMaterials(pc.materials) || []).map((m: any) => ({
        name: m.name || '',
        spec: m.spec || '',
        qty: m.qty ?? 1,
        unit: m.unit || 'PCS',
        materialId: m.materialId,
        materialCode: m.materialCode || '',
        options: [],
        loading: false,
        uid: genUid(),
        pageNum: 1,
        total: 0,
        lastQuery: '',
      }))
      if (!pc.materialRows.length) addMaterialRow(pc)
    }
  }

  // 远程搜索物料档案（分页）
  async function searchMaterials(query: string, m: any, pageNum = 1, append = false) {
    m.loading = true
    m.lastQuery = (query || '').trim()
    try {
      const params: any = { pageNum, pageSize: 20 }
      if (m.lastQuery) params.materialName = m.lastQuery
      const res: any = await materialApi.search(params)
      const records = res?.data?.records || res?.data || []
      if (append) {
        const seen = new Set((m.options || []).map((o: any) => o.materialId))
        for (const r of records) {
          if (!seen.has(r.materialId)) {
            m.options.push(r)
            seen.add(r.materialId)
          }
        }
      } else {
        m.options = records
      }
      m.total = res?.data?.total ?? records.length
      m.pageNum = pageNum
    } catch {
      if (!append) m.options = []
    } finally {
      m.loading = false
    }
  }

  // DEV-1020：材料搜索防抖（每行材料选择器独立 300ms，避免 remote-method 输入即请求）
  const materialSearchDebouncers = new Map<string, ReturnType<typeof debounce>>()
  function debouncedSearchMaterials(query: string, m: any, pageNum = 1, append = false) {
    const key = m?.uid || m?.materialId || 'material'
    let fn = materialSearchDebouncers.get(key)
    if (!fn) {
      fn = debounce((q: string, row: any, p: number, ap: boolean) => {
        materialSearchDebouncers.delete(key)
        searchMaterials(q, row, p, ap)
      }, 300)
      materialSearchDebouncers.set(key, fn)
    }
    fn(query, m, pageNum, append)
  }

  // 下拉滚动加载下一页
  function onSelectVisibleChange(m: any, visible: boolean) {
    if (!visible) return
    if (!m.options?.length && !m.loading) {
      searchMaterials(m.lastQuery || '', m, 1, false)
    }
    nextTick(() => {
      const wrap = document.querySelector(
        `.material-popper-${m.uid} .el-select-dropdown__wrap`
      ) as HTMLElement | null
      if (!wrap) return
      wrap.onscroll = () => {
        if (wrap.scrollTop + wrap.clientHeight >= wrap.scrollHeight - 30) {
          loadMoreMaterials(m)
        }
      }
    })
  }

  async function loadMoreMaterials(m: any) {
    if (m.loading) return
    if (!m.total || (m.options?.length || 0) >= m.total) return
    await searchMaterials(m.lastQuery || '', m, (m.pageNum || 1) + 1, true)
  }

  // 选中物料 → 自动填名称/规格/单位
  function onMaterialSelected(m: any, materialId: number) {
    const mat = (m.options || []).find((o: any) => o.materialId === materialId)
    if (!mat) return
    m.name = mat.materialName
    m.spec = mat.specification || ''
    m.unit = mat.unit || 'PCS'
    m.materialCode = mat.materialCode || ''
  }

  // 建档弹窗
  const materialCreateVisible = ref(false)
  const materialPreset = ref<any>({})
  const materialTarget = ref<any>(null)
  function openMaterialCreate(pc: any, m: any) {
    materialTarget.value = { card: pc, row: m }
    materialPreset.value = {
      materialName: m?.name || '',
      specification: m?.spec || '',
      unit: m?.unit || 'PCS',
    }
    materialCreateVisible.value = true
  }

  // 建档成功 → 填入目标行（无目标行则加到卡片材料表）
  function onMaterialCreated(mat: any) {
    if (readonlyMode.value) return
    const t = materialTarget.value
    if (t?.row) {
      t.row.materialId = mat.materialId
      t.row.materialCode = mat.materialCode || ''
      t.row.name = mat.materialName
      t.row.spec = mat.specification || ''
      t.row.unit = mat.unit || 'PCS'
      t.row.options = [mat]
    } else if (t?.card) {
      t.card.materialRows.push({
        name: mat.materialName,
        spec: mat.specification || '',
        qty: 1,
        unit: mat.unit || 'PCS',
        materialId: mat.materialId,
        materialCode: mat.materialCode || '',
        options: [mat],
        loading: false,
        uid: genUid(),
        pageNum: 1,
        total: 0,
        lastQuery: '',
      })
    }
  }

  // 解析材料JSON
  function parseMaterials(json?: string | null) {
    if (!json) return []
    try {
      const arr = JSON.parse(json)
      return Array.isArray(arr) ? arr : []
    } catch {
      return []
    }
  }


  onScopeDispose(() => {
    materialSearchDebouncers.forEach((search) => search.cancel())
    materialSearchDebouncers.clear()
  })
  return { addMaterialRow, startEdit, cancelEdit, finishEdit, searchMaterials, debouncedSearchMaterials, onSelectVisibleChange, loadMoreMaterials, onMaterialSelected, materialCreateVisible, materialPreset, openMaterialCreate, onMaterialCreated, parseMaterials }
}
