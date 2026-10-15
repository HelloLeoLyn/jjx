import { planFingerprint } from './planDraft'
import { ref, computed, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { SampleProcessStatusEnum } from '@/enums/sales'

interface PlanPersistenceContext {
  readonlyMode: Readonly<Ref<boolean>>
  orderId: Readonly<Ref<number>>
  card: Ref<any>
  planList: Ref<any[]>
  printList: Ref<any[]>
  allProcesses: Ref<any[]>
  makeCard: (items: any[], extra?: any) => any
  makePrintRow: (src?: any, order?: number) => any
  loadBom: () => Promise<void>
  refreshCard: () => Promise<void>
  loadSummary: () => Promise<void>
}

/** 计划的加载与整单保存边界；返回值明确表示保存是否成功。 */
export function usePlanPersistence(context: PlanPersistenceContext) {
  const { readonlyMode, orderId, card, planList, printList, allProcesses, makeCard, makePrintRow, loadBom, refreshCard, loadSummary } = context
  const savingPlan = ref(false)
  const savedFingerprint = ref(planFingerprint([], []))
  const hasUnsavedChanges = computed(() => planFingerprint(planList.value, printList.value) !== savedFingerprint.value)
  // 保存工序计划（整单覆盖当前轮次；卡片展开为多行，同卡片同行序）
  async function savePlan(): Promise<boolean> {
    if (readonlyMode.value || savingPlan.value) return false
    if (!orderId.value) return false
    const validPrints = printList.value.filter((r) => (r.printName || '').trim())
    if (planList.value.some((pc) => !pc.items.length)) {
      ElMessage.warning('有卡片未选择工序，请补充或删除空卡片')
      return false
    }
    // 保存状态：全部标记为保存中
    planList.value.forEach((pc) => (pc.saveState = 'saving'))
    savingPlan.value = true
    try {
      // 2026-09-04 修复：编辑态卡片挂的材料暂存在 materialRows，未点卡片内“保存”时需先同步到
      // pc.materials，否则直接点“保存工序计划”会丢掉新挂材料（SP2609040006 现场：工序存上材料 NULL）
      planList.value.forEach((pc) => {
        if (pc.editing && Array.isArray(pc.materialRows)) {
          const validMats = (pc.materialRows as any[])
            .filter((m: any) => m.name && m.name.trim())
            .map((m: any) => ({
              name: m.name,
              spec: m.spec || '',
              qty: m.qty ?? 1,
              unit: m.unit || 'PCS',
              materialId: m.materialId,
              materialCode: m.materialCode || '',
            }))
          pc.materials = validMats.length ? JSON.stringify(validMats) : null
        }
      })
      const items: any[] = []
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
      await sampleOrderApi.saveProcessPlan(orderId.value, { items })
      planList.value.forEach((pc) => (pc.saveState = 'synced'))
      ElMessage.success(
        `工序计划已保存（${planList.value.length + validPrints.length}道，含印刷${validPrints.length}）`
      )
      await loadPlan()
      await loadBom()
      await refreshCard()
      await loadSummary()
      return true
    } catch (e: any) {
      planList.value.forEach((pc) => (pc.saveState = 'error'))
      ElMessage.error(e?.message || '保存工序计划失败')
      return false
    } finally {
      savingPlan.value = false
    }
  }

  // 加载工序计划（当前轮次，按 processOrder 分组为卡片）
  async function loadPlan() {
    if (!orderId.value) return
    try {
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
          const src = allProcesses.value.find((x) => x.processId === r.stdProcessId)
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
      savedFingerprint.value = planFingerprint(planList.value, printList.value)
    } catch {
      planList.value = []
    }
  }

  return { savingPlan, hasUnsavedChanges, savePlan, loadPlan }
}
