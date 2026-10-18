import { normalizeMaterial, projectIdsOf } from './sampleMaterials'
import { createCardEditSessions } from './planDraft'
import { ref, onScopeDispose, type Ref } from 'vue'
import { debounce } from 'lodash-es'
import { materialApi } from '@/api/inventory/material'

/**
 * 工序材料输入、搜索及建档回填；材料草稿仍归工序计划所有。
 *
 * 搜索文字与已选材料身份分离（dev-20261018-035）：
 * 1）输入框文字只是关键词，改文字不动 materialId / 编码 / 规格 / 单位；
 * 2）只有选中新结果才更新该行身份与档案快照，显式清空才解绑身份；
 * 3）每行独立请求序号：快速输入、旧请求晚返回、删行/切工序都不会写回错误行；
 * 4）搜索失败置 searchFailed 供重试，不把失败冒充成零结果诱导新建。
 */
export function useProcessMaterials(readonlyMode: Readonly<Ref<boolean>>, genUid: () => string) {
  const editSessions = createCardEditSessions()
  function cancelEdit(pc: any) { if (!readonlyMode.value) editSessions.cancel(pc) }
  function finishEdit(pc: any) { if (!readonlyMode.value) editSessions.finish(pc) }

  function emptyRow(pc: any, source: any = {}) {
    return {
      ...normalizeMaterial(source, projectIdsOf(pc)),
      name: source.name || '',
      spec: source.spec || '',
      qty: source.qty ?? 1,
      unit: source.unit || 'PCS',
      materialId: source.materialId,
      materialCode: source.materialCode || '',
      options: [],
      loading: false,
      loadingMore: false,
      searchFailed: false,
      uid: genUid(),
      pageNum: 1,
      total: 0,
      lastQuery: '',
      _matKw: '',
      _searchSeq: 0,
    }
  }

  // 材料行
  function addMaterialRow(pc: any) {
    if (readonlyMode.value) return
    pc.materialRows.push(emptyRow(pc))
  }

  // 进入编辑：初始化材料行
  function startEdit(pc: any) {
    if (readonlyMode.value) return
    editSessions.begin(pc)
    pc.editing = true
    if (!pc.materialRows.length) {
      pc.materialRows = (parseMaterials(pc.materials) || []).map((m: any) => emptyRow(pc, m))
      if (!pc.materialRows.length) addMaterialRow(pc)
    }
  }

  // 远程搜索物料档案（分页）；seq 用于丢弃晚返回的旧请求
  async function searchMaterials(query: string, m: any, pageNum = 1, append = false) {
    if (!m) return
    const seq = (m._searchSeq || 0) + 1
    m._searchSeq = seq
    m.lastQuery = (query || '').trim()
    m.loading = true
    m.searchFailed = false
    try {
      const params: any = { pageNum, pageSize: 20 }
      if (m.lastQuery) params.materialName = m.lastQuery
      const res: any = await materialApi.search(params)
      if (m._searchSeq !== seq) return // 旧请求晚返回：整包丢弃，不覆盖新结果
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
      if (m._searchSeq !== seq) return
      // 失败不冒充零结果：保留上一次候选，标记失败并给重试入口，同时不提供“新建”诱导
      m.searchFailed = true
    } finally {
      if (m._searchSeq === seq) {
        m.loading = false
        m.loadingMore = false
      }
    }
  }

  // DEV-1020：材料搜索防抖（每行材料选择器独立 300ms，避免输入即请求）
  const materialSearchDebouncers = new Map<string, ReturnType<typeof debounce>>()
  function debouncedSearchMaterials(query: string, m: any, pageNum = 1, append = false) {
    if (!m) return
    const key = m.uid || 'material'
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

  // 展开搜索面板：没有候选时按当前关键词取第一页（原 el-select 打开即搜的等价行为）
  function onSelectVisibleChange(m: any, visible: boolean) {
    if (!m || !visible) return
    if (!m.options?.length && !m.loading) {
      searchMaterials(m.lastQuery || '', m, 1, false)
    }
  }

  // 加载更多：沿用同一关键词，防重复翻页
  async function loadMoreMaterials(m: any) {
    if (!m || m.loading || m.loadingMore) return
    if (!m.total || (m.options?.length || 0) >= m.total) return
    m.loadingMore = true
    await searchMaterials(m.lastQuery || '', m, (m.pageNum || 1) + 1, true)
  }

  // 选中物料 → 只更新该行身份与档案快照；模数/基数等行数据不动
  function onMaterialSelected(m: any, value: any) {
    if (!m || value == null) return
    const id = typeof value === 'object' ? value.materialId : value
    if (id == null) return
    const mat = typeof value === 'object' ? value : (m.options || []).find((o: any) => o.materialId === id)
    if (!mat) return
    m.materialId = mat.materialId
    m.materialCode = mat.materialCode || ''
    m.name = mat.materialName || ''
    m.spec = mat.specification || ''
    m.unit = mat.unit || 'PCS'
    if (!(m.options || []).some((o: any) => o.materialId === mat.materialId)) {
      m.options = [mat, ...(m.options || [])]
    }
  }

  // 显式清空：同步解除材料身份与关联快照，作废在途请求，不留旧ID配新名称
  function clearMaterialRow(m: any) {
    if (!m) return
    materialSearchDebouncers.get(m.uid || 'material')?.cancel()
    materialSearchDebouncers.delete(m.uid || 'material')
    m._searchSeq = (m._searchSeq || 0) + 1
    m.materialId = undefined
    m.materialCode = ''
    m.name = ''
    m.spec = ''
    m.unit = 'PCS'
    m.options = []
    m.total = 0
    m.pageNum = 1
    m.lastQuery = ''
    m._matKw = ''
    m.loading = false
    m.loadingMore = false
    m.searchFailed = false
  }

  // 建档弹窗
  const materialCreateVisible = ref(false)
  const materialPreset = ref<any>({})
  const materialTarget = ref<any>(null)
  function openMaterialCreate(pc: any, m: any) {
    const keyword = String(m?._matKw || '').trim()
    materialTarget.value = { card: pc, row: m }
    materialPreset.value = {
      materialName: keyword || m?.name || '',
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
      t.row._matKw = ''
      t.row.searchFailed = false
      t.row.total = 1
    } else if (t?.card) {
      t.card.materialRows.push({
        baseQty: 1, moduleQty: 1, projectIds: projectIdsOf(t.card), projectInherited: true,
        name: mat.materialName,
        spec: mat.specification || '',
        qty: 1,
        unit: mat.unit || 'PCS',
        materialId: mat.materialId,
        materialCode: mat.materialCode || '',
        options: [mat],
        loading: false,
        loadingMore: false,
        searchFailed: false,
        uid: genUid(),
        pageNum: 1,
        total: 1,
        lastQuery: '',
        _matKw: '',
        _searchSeq: 0,
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
  return {
    addMaterialRow, startEdit, cancelEdit, finishEdit, searchMaterials, debouncedSearchMaterials,
    onSelectVisibleChange, loadMoreMaterials, onMaterialSelected, clearMaterialRow,
    materialCreateVisible, materialPreset, openMaterialCreate, onMaterialCreated, parseMaterials,
  }
}
