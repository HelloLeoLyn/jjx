import { assignMaterial, materialOwner, resolveMaterialOwners } from './sampleProcessGroups'
import { normalizeMaterial, projectIdsOf } from './sampleMaterials'
import { createCardEditSessions } from './planDraft'
import { ref, type Ref } from 'vue'

/** 工序材料草稿、回填及建档；远程搜索与分页统一由 BomMaterialSelector 负责。 */
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
      uid: genUid(),
      _matKw: '',
    }
  }

  // 材料行
  function addMaterialRow(pc: any, itemKey?: string) {
    if (readonlyMode.value) return
    const row = emptyRow(pc)
    const owner = itemKey ? pc.items?.find((item: any) => item.itemKey === itemKey) : materialOwner(pc, row)
    if (owner) assignMaterial(row, owner)
    pc.materialRows.push(row)
  }

  // 进入编辑：初始化材料行
  function startEdit(pc: any) {
    if (readonlyMode.value) return
    editSessions.begin(pc)
    pc.editing = true
    if (!pc.materialRows.length) {
      pc.materialRows = resolveMaterialOwners(pc, parseMaterials(pc.materials) || []).map((m: any) => emptyRow(pc, m))
      if (!pc.materialRows.length) addMaterialRow(pc)
    }
  }

  // 选中物料 → 只更新该行身份与档案快照；模数/基数等行数据不动
  function onMaterialSelected(m: any, value: any) {
    if (readonlyMode.value || !m || value == null) return
    const mat = value
    if (mat.materialId == null) return
    m.materialId = mat.materialId
    m.materialCode = mat.materialCode || ''
    m.name = mat.materialName || ''
    m.spec = mat.specification || ''
    m.unit = mat.unit || 'PCS'
  }

  // 显式清空：同步解除材料身份与关联快照，不留旧ID配新名称
  function clearMaterialRow(m: any) {
    if (readonlyMode.value || !m) return
    m.materialId = undefined
    m.materialCode = ''
    m.name = ''
    m.spec = ''
    m.unit = 'PCS'
    m._matKw = ''
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
      t.row._matKw = ''
    } else if (t?.card) {
      t.card.materialRows.push({
        baseQty: 1, moduleQty: 1, projectIds: projectIdsOf(t.card), projectInherited: true,
        name: mat.materialName,
        spec: mat.specification || '',
        qty: 1,
        unit: mat.unit || 'PCS',
        materialId: mat.materialId,
        materialCode: mat.materialCode || '',
        uid: genUid(),
        _matKw: '',
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

  return {
    addMaterialRow, startEdit, cancelEdit, finishEdit,
    onMaterialSelected, clearMaterialRow,
    materialCreateVisible, materialPreset, openMaterialCreate, onMaterialCreated, parseMaterials,
  }
}
