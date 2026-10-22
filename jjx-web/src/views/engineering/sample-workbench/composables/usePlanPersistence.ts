import { materialValues } from './planDraft'
import { normalizeMaterial, projectIdsOf, serializeMaterial, validateMaterials } from './sampleMaterials'
import { planFingerprint } from './planDraft'
import { ref, computed, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { SampleProcessStatusEnum } from '@/enums/sales'

interface PlanPersistenceContext {
  readonlyMode: Readonly<Ref<boolean>>
  orderId: Readonly<Ref<number>>
  card: Ref<any>
  note: Ref<string>
  canSave?: () => boolean
  planList: Ref<any[]>
  printList: Ref<any[]>
  standaloneMaterials: Ref<any[]>
  allProcesses: Ref<any[]>
  makeCard: (items: any[], extra?: any) => any
  makePrintRow: (src?: any, order?: number) => any
  loadBom: () => Promise<void>
  refreshCard: () => Promise<void>
  loadSummary: () => Promise<void>
  /** 确保标准工序列表已就绪（同一页面只请求一次，失败不阻塞） */
  ensureProcessesLoaded?: () => Promise<void>
  /** 按 id 单条补齐标准工序（列表缺失/停用/>100 条时兜底），结果缓存 */
  resolveProcessById?: (id: number) => Promise<any>
}

/** 计划的加载与整单保存边界；返回值明确表示保存是否成功。 */
export function usePlanPersistence(context: PlanPersistenceContext) {
  const { readonlyMode, orderId, card, planList, printList, standaloneMaterials, allProcesses, makeCard, makePrintRow, loadBom, refreshCard, loadSummary, note, canSave, ensureProcessesLoaded, resolveProcessById } = context
  const savingPlan = ref(false)
  const currentFingerprint = () => JSON.stringify([planFingerprint(planList.value, printList.value), standaloneMaterials.value])
  const savedFingerprint = ref(JSON.stringify([planFingerprint([], []), []]))
  const savedNote = ref('')
  const hasPlanChanges = computed(() => currentFingerprint() !== savedFingerprint.value)
  const hasUnsavedChanges = computed(() => hasPlanChanges.value || note.value !== savedNote.value)
  /** 反查兜底缓存：列表里没有的 stdProcessId，按 id 单条补齐后缓存（dev-20261018-014） */
  const resolvedProcesses = new Map<number, any>()

  const processOf = (id: any) => {
    if (id == null || id === '') return undefined
    const key = Number(id)
    return allProcesses.value.find((x: any) => Number(x.processId) === key) || resolvedProcesses.get(key)
  }

  /** 只查缺失项，限制并发 4；单条失败不影响其它行 */
  async function resolveMissingProcesses(ids: number[]) {
    if (!resolveProcessById) return
    const queue = Array.from(new Set(ids.filter((id) => Number.isFinite(id) && id > 0)))
      .filter((id) => !processOf(id))
    if (!queue.length) return
    const workers = Array.from({ length: Math.min(4, queue.length) }, async () => {
      while (queue.length) {
        const id = queue.shift() as number
        try {
          const item = await resolveProcessById(id)
          if (item && item.processId != null) resolvedProcesses.set(Number(item.processId), item)
        } catch {
          // 单条失败保留占位，后续刷新会重试
        }
      }
    })
    await Promise.all(workers)
  }

  /** 加载完成后统一建立一次保存快照（计划与独立材料都加载完再调用，避免误判未保存）。 */
  function markSaved() {
    savedFingerprint.value = currentFingerprint()
    savedNote.value = note.value
  }
  // 保存工序计划（整单覆盖当前轮次；卡片展开为多行，同卡片同行序）
  async function savePlan(): Promise<boolean> {
    if (readonlyMode.value || savingPlan.value || canSave?.() === false) return false
    if (!orderId.value) return false
    const planChanged = hasPlanChanges.value
    const submittedNote = note.value
    if (!planChanged && submittedNote === savedNote.value) return true
    const validPrints = printList.value.filter((r) => (r.printName || '').trim())
    if (planChanged && planList.value.some((pc) => !pc.items.length)) {
      ElMessage.warning('有卡片未选择工序，请补充或删除空卡片')
      return false
    }
    // 保存状态：全部标记为保存中
    if (planChanged) planList.value.forEach((pc) => (pc.saveState = 'saving'))
    savingPlan.value = true
    try {
      const items: any[] = []
      let independent: any[] = []
      if (planChanged) {
        for (const pc of [...planList.value, ...validPrints]) {
          const rows = materialValues(pc).map(row => normalizeMaterial(row, projectIdsOf(pc)))
          const error = validateMaterials(rows)
          if (error) throw new Error(error)
          pc.materials = rows.length ? JSON.stringify(rows.map(serializeMaterial)) : null
        }
        independent = standaloneMaterials.value.map(row => normalizeMaterial(row))
        if (independent.some(row => !String(row.name || '').trim())) throw new Error('请为新增BOM行选择材料或删除空行')
        const independentError = validateMaterials(independent)
        if (independentError) throw new Error(independentError)
        planList.value.forEach((pc, i) => {
          const order = i + 1
          pc.items.forEach((it: any) => {
            items.push({
              processOrder: order,
              processId: it.processId ?? undefined,
              stdProcessId: it.stdProcessId ?? undefined,
              processName: it.processName,
              processCategory: pc.category || undefined,
              majorCategory: 'ASSEMBLY',
              materials: pc.materials,
              processNote: it.workInstruction || null,
              remark: pc.operationRemark || null,
              status: pc.status ?? SampleProcessStatusEnum.TODO.value,
              indexNumber: it.indexNumber ?? undefined, // DEV-777：下标数字
            })
          })
        })
        // 印刷行（dev-20260811-009）：processOrder 接在冲型组装后面
        const baseOrder = planList.value.length
        validPrints.forEach((r, i) => {
          const params: any = { printName: r.printName }
          if (r.colorNo) params.colorNo = r.colorNo
          if (r.colorNoLabel) params.colorNoLabel = r.colorNoLabel
          if (r.inkMaterialId != null) params.inkMaterialId = r.inkMaterialId
          if (r.inkNo) params.inkNo = r.inkNo
          if (r.screenNo) params.screenNo = r.screenNo
          items.push({
            processOrder: baseOrder + i + 1,
            processId: r.processId ?? undefined,
            stdProcessId: null,
            processName: r.printName,
            processCategory: r.category || 'OTHER',
            majorCategory: 'PRINT',
            customProcessParams: JSON.stringify(params),
            materials: r.materials,
            status: r.status ?? SampleProcessStatusEnum.TODO.value,
          })
        })
      }
      await sampleOrderApi.saveScheme(orderId.value, { engineeringNote: submittedNote !== savedNote.value ? submittedNote : undefined, roundNo: card.value?.sampleRound || 1, items: planChanged ? items : undefined, standaloneMaterials: planChanged ? independent.map(row => ({
        layerName: row.layerName || '', materialName: row.name, specification: row.spec, quantity: row.qty, unit: row.unit,
        materialId: row.materialId, materialCode: row.materialCode, projectIds: JSON.stringify(row.projectIds || []), moduleQty: row.moduleQty, baseQty: row.baseQty, split: row.split,
      })) : undefined })
      savedNote.value = submittedNote
      card.value.engineeringNote = submittedNote
      if (planChanged) {
        planList.value.forEach((pc) => (pc.saveState = 'synced'))
        await loadPlan()
        await loadBom()
        savedFingerprint.value = currentFingerprint()
      }
      await refreshCard()
      await loadSummary()
      ElMessage.success('打样方案已保存（含工序、印刷、BOM和工程备注）')
      return true
    } catch (e: any) {
      if (planChanged) planList.value.forEach((pc) => (pc.saveState = 'error'))
      ElMessage.error(e?.message || '保存打样方案失败')
      return false
    } finally {
      savingPlan.value = false
    }
  }

  // 加载工序计划（当前轮次，按 processOrder 分组为卡片）
  async function loadPlan() {
    if (!orderId.value) return
    try {
      // A（dev-20261018-014）：先确保标准工序列表就绪，再 enrich，避免计划先渲染成「工」占位
      // 列表加载失败不得阻塞计划本身；失败时由下方按 id 兜底
      try {
        await ensureProcessesLoaded?.()
      } catch {
        // 忽略：继续加载计划，图标走单条补齐
      }
      const res = await sampleOrderApi.listProcesses(
        orderId.value,
        card.value?.sampleRound || undefined
      )
      const list: any[] = res.data || []
      list.sort(
        (a, b) =>
          (a.processOrder || 999) - (b.processOrder || 999) ||
          (a.processId || 0) - (b.processId || 0)
      )
      // B：列表缺失项（停用 / 超过 pageSize / 加载失败）按 id 单条补齐后再 enrich
      await resolveMissingProcesses(list.map((p: any) => Number(p.stdProcessId)))
      // 一级大类拆分（dev-20260811-009）：PRINT → printList（表格），其余 → planList（卡片）
      const printRows = list.filter((p) => p.majorCategory === 'PRINT')
      const assemblyRows = list.filter((p) => p.majorCategory !== 'PRINT')
      printList.value = printRows.map((p, i) => makePrintRow(p, i + 1))
      // 按 processOrder 分组（同卡片多行组合）
      const groups = new Map<number, any[]>()
      for (const p of assemblyRows) {
        const k = p.processOrder || 999
        if (!groups.has(k)) groups.set(k, [])
        groups.get(k)!.push(p)
      }
      planList.value = Array.from(groups.entries()).map(([order, rows]) => {
        const first = rows[0]
        const enriched = rows.map((r: any) => {
          const src = processOf(r.stdProcessId)
          return src
            ? {
                ...r,
                processType: src.processType,
                processCategory: src.processCategory,
                icon: src.icon,
                hasIndex: src.hasIndex ?? 0,
                hasWorkInstruction: src.hasWorkInstruction ?? 0,
              }
            : { ...r, hasIndex: r.hasIndex ?? 0, hasWorkInstruction: r.hasWorkInstruction ?? 0 }
        })
        return makeCard(enriched, {
          uid: `db-${order}`,
          processOrder: order,
          category: first.processCategory || '',
          status: first.status ?? SampleProcessStatusEnum.TODO.value,
          operationRemark: first.remark || '',
          materials: first.materials || null,
          durationMinutes: first.durationMinutes ?? null,
          startTime: first.startTime || null,
          endTime: first.endTime || null,
          operator: first.operator || '',
        })
      })
      savedFingerprint.value = currentFingerprint()
    } catch {
      planList.value = []
    }
  }

  return { savingPlan, hasUnsavedChanges, savePlan, loadPlan, markSaved }
}
