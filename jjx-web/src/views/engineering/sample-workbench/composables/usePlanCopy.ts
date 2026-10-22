import { ref, type Ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { SampleProcessStatusEnum } from '@/enums/sales'
import { normalizeMaterial } from './sampleMaterials'

interface PlanCopyContext {
  orderId: Readonly<Ref<number>>
  readonlyMode: Readonly<Ref<boolean>>
  card: Ref<any>
  planList: Ref<any[]>
  printList: Ref<any[]>
  standaloneMaterials: Ref<any[]>
  allProcesses: Ref<any[]>
  makeCard: (items: any[], extra?: any) => any
  makePrintRow: (src?: any, order?: number) => any
  genUid: () => string
  parseMaterials: (json?: string | null) => any[]
}

/** 历史轮次、其他样品和成品方案复制；仅写入计划草稿。 */
export function usePlanCopy(context: PlanCopyContext) {
  const { orderId, readonlyMode, card, planList, printList, standaloneMaterials, allProcesses, makeCard, makePrintRow, genUid, parseMaterials } = context
  // 从历史打样复制
  const historyCopyVisible = ref(false)
  // 追加/覆盖（dev-20261012-012：给用户选择，默认追加）
  const copyOverwrite = ref(false)
  const historyOrders = ref<any[]>([])
  const historyLoading = ref(false)
  const historySelected = ref<any>(null)
  const historyCopying = ref(false)
  async function openHistoryCopy() {
    historyCopyVisible.value = true
    historySelected.value = null
    historySelectedRound.value = null
    copyOverwrite.value = false
    historyLoading.value = true
    loadProductPlan()
    try {
      // 2026-08-12 DEV-988：不再限已转量产(7)——查全部样品单（排除当前单），有工序即可复制
      const res: any = await sampleOrderApi.list({})
      historyOrders.value = (res.data || []).filter((o: any) => o.orderId !== orderId.value)
    } catch {
      historyOrders.value = []
    } finally {
      historyLoading.value = false
    }
  }
  // 共用：把「打样工序行形状」的列表应用到当前轮编辑区（追加或覆盖；印刷→印刷表、其余→卡片）
  function applyCopiedRows(list: any[], overwrite = false) {
    const sorted = [...list].sort(
      (a: any, b: any) =>
        (a.processOrder || 999) - (b.processOrder || 999) ||
        (a.processId || 0) - (b.processId || 0)
    )
    // 2026-08-12 DEV-988：拆分印刷(PRINT)与冲型组装(ASSEMBLY)——印刷行进印刷表格
    const printRows = sorted.filter((p: any) => p.majorCategory === 'PRINT')
    const assemblyRows = sorted.filter((p: any) => p.majorCategory !== 'PRINT')
    if (overwrite) {
      planList.value = []
      printList.value = []
    }
    // 按 processOrder 分组为卡片（不覆盖时追加到当前 planList 后面）
    const groups = new Map<number, any[]>()
    for (const p of assemblyRows) {
      const k = p.processOrder || 999
      if (!groups.has(k)) groups.set(k, [])
      groups.get(k)!.push(p)
    }
    let added = 0
    for (const [, rows] of groups) {
      const first = rows[0]
      const enriched = rows.map((r: any) => {
        const src2 = allProcesses.value.find((x) => x.processId === r.stdProcessId)
        return src2
          ? {
              ...r,
              processType: src2.processType,
              processCategory: src2.processCategory,
              icon: src2.icon,
            }
          : r
      })
      const pc = makeCard(enriched, {
        processOrder: 0, // 追加，保存时重新编号
        category: first.processCategory || '',
        status: SampleProcessStatusEnum.TODO.value,
        operationRemark: first.remark || '',
        // 打样材料是「卡片级」：卡片材料 = 卡内各行材料的并集（组合卡片需合并子工序材料，dev-20261012-012 修复）
        materials: mergeCardMaterials(rows),
      })
      planList.value.push(pc)
      added++
    }
    // 印刷行追加到 printList（参数色号/油墨/网框随行；重置待做状态 + uid 防 key 冲突）
    let addedPrint = 0
    for (const r of printRows) {
      const row = makePrintRow(r, printList.value.length + 1)
      row.status = SampleProcessStatusEnum.TODO.value
      row.uid = `new-${genUid()}`
      printList.value.push(row)
      addedPrint++
    }
    return { added, addedPrint }
  }

  // 合并卡内各行材料为卡片级材料（去重：materialId|name|spec）；无则 null
  function mergeCardMaterials(rows: any[]): string | null {
    const map = new Map<string, any>()
    for (const r of rows) {
      for (const m of parseMaterials(r.materials) || []) {
        const key = `${m?.materialId ?? ''}|${m?.name ?? ''}|${m?.spec ?? ''}`
        if (!map.has(key)) map.set(key, m)
      }
    }
    return map.size ? JSON.stringify([...map.values()]) : null
  }

  /**
   * 把源单/源轮的独立BOM材料（sales_sample_bom）带出到当前编辑区（dev-20261015-024）。
   * 与卡片材料一致：覆盖则先清空；追加则按 materialId|name|spec 去重，不重复计量。
   * 返回本次新增行数。
   */
  async function copyStandaloneMaterials(srcOrderId: number, srcRound: number | null, overwrite: boolean): Promise<number> {
    let rows: any[] = []
    try {
      const res: any = await sampleOrderApi.listBom(srcOrderId)
      rows = res.data || []
    } catch {
      return 0
    }
    // 目标轮次：显式给定则按轮次；否则取源单存在的最大轮次（复制其现行计划）
    const rounds = rows.map((r: any) => Number(r.roundNo) || 1)
    const round = srcRound != null ? srcRound : rounds.length ? Math.max(...rounds) : null
    const mapped = rows
      .filter((r: any) => round == null || (Number(r.roundNo) || 1) === round)
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
    if (overwrite) standaloneMaterials.value = []
    const keyOf = (m: any) => `${m.materialId ?? ''}|${m.name ?? ''}|${m.spec ?? ''}`
    const seen = new Set(standaloneMaterials.value.map(keyOf))
    let added = 0
    for (const m of mapped) {
      if (seen.has(keyOf(m))) continue
      standaloneMaterials.value.push(m)
      seen.add(keyOf(m))
      added++
    }
    return added
  }

  async function confirmHistoryCopy() {
    if (readonlyMode.value) return
    const src = historySelected.value
    if (!src) return
    historyCopying.value = true
    try {
      const res = await sampleOrderApi.listProcesses(src.orderId)
      const list: any[] = res.data || []
      if (!list.length) {
        ElMessage.warning('该样品单没有工序计划')
        return
      }
      const { added, addedPrint } = applyCopiedRows(list, copyOverwrite.value)
      const addedStandalone = await copyStandaloneMaterials(src.orderId, src.sampleRound ?? null, copyOverwrite.value)
      historyCopyVisible.value = false
      ElMessage.success(
        `已复制 ${added} 张卡片${addedPrint ? `、${addedPrint} 道印刷工序` : ''}${addedStandalone ? `、独立材料${addedStandalone}项` : ''}（${copyOverwrite.value ? '覆盖' : '追加到现有后'}，保存后生效）`
      )
    } catch (e: any) {
      ElMessage.error(e?.message || '复制失败')
    } finally {
      historyCopying.value = false
    }
  }

  // 复制本单指定历史轮工艺（同单多轮：Round N→任意历史轮，客户退回重做场景；追加到当前轮编辑区，保存后生效，DEV-20260905-007）
  const copyPrevLoading = ref(false)
  const historySelectedRound = ref<any>(null)
  async function copyRoundFrom(roundNo: number) {
    if (readonlyMode.value) return
    if (!roundNo || roundNo <= 0) return
    copyPrevLoading.value = true
    try {
      const res: any = await sampleOrderApi.listProcesses(orderId.value, roundNo)
      const list: any[] = res.data || []
      if (!list.length) {
        ElMessage.warning(`Round ${roundNo} 没有工序计划，无法复制`)
        return
      }
      // 与跨单复制同语义：PRINT→印刷表，其余→按 processOrder 分组为卡片
      const { added, addedPrint } = applyCopiedRows(list, copyOverwrite.value)
      const addedStandalone = await copyStandaloneMaterials(orderId.value, roundNo, copyOverwrite.value)
      ElMessage.success(
        `已从 Round ${roundNo} 复制 ${added} 张卡片${addedPrint ? `、${addedPrint} 道印刷工序` : ''}${addedStandalone ? `、独立材料${addedStandalone}项` : ''}（${copyOverwrite.value ? '覆盖' : '追加到当前轮'}，保存后生效）`
      )
      historyCopyVisible.value = false
    } catch (e: any) {
      ElMessage.error(e?.message || '复制失败')
    } finally {
      copyPrevLoading.value = false
    }
  }

  // 快捷入口：复制上一轮（同单 Round N→N+1）
  async function copyPreviousRound() {
    const cur = card.value?.sampleRound || 1
    if (cur <= 1) return
    await copyRoundFrom(cur - 1)
  }

  // ===== 复制打样方案：来源3「成品方案」（只读带出，dev-20261012-012）=====
  const productPlan = ref<any>(null)
  const productPlanLoading = ref(false)
  const productPlanCopying = ref(false)
  async function loadProductPlan() {
    if (!orderId.value) return
    productPlanLoading.value = true
    try {
      const res: any = await sampleOrderApi.getProductPlan(orderId.value)
      productPlan.value = res?.data || null
    } catch {
      productPlan.value = null
    } finally {
      productPlanLoading.value = false
    }
  }
  async function confirmProductPlanCopy() {
    if (readonlyMode.value) return
    const pp: any = productPlan.value
    if (!pp?.eligible) return
    const list: any[] = (pp.rows || []).map((r: any) => ({ ...r }))
    if (!list.length) {
      ElMessage.warning('成品方案没有可带出的工序')
      return
    }
    // BOM 材料不静默丢弃（硬要求）：无工序的材料一律暂挂到首道工序；区分「整单通用」与「未挂工序」两类提示
    const unlinked: any[] = pp.unlinkedMaterials || []
    const pendingUnlinked = unlinked.filter((m: any) => !m.isCommon) // 未挂工序（待核对）
    const commonMats = unlinked.filter((m: any) => !!m.isCommon) // 整单通用（合法）
    let unlinkedNote = ''
    if (unlinked.length) {
      const idx = list.findIndex((r: any) => r.majorCategory !== 'PRINT')
      const ti = idx >= 0 ? idx : 0
      const base = parseMaterials(list[ti].materials) || []
      const add = unlinked.map((m: any) => ({
        name: m.name,
        spec: m.spec || '',
        qty: m.qty ?? 1,
        unit: m.unit || 'PCS',
        materialId: m.materialId,
        materialCode: m.materialCode || '',
        baseQty: m.baseQty ?? 1,
        moduleQty: m.moduleQty ?? 1,
        split: m.split,
        projectIds: m.projectIds || [],
      }))
      list[ti].materials = JSON.stringify([...base, ...add])
      const parts: string[] = []
      if (commonMats.length) {
        parts.push(
          `${commonMats.length} 条整单通用材料（${commonMats.map((m: any) => m.name).join('、')}）已暂挂到首道工序「${list[ti].processName}」`,
        )
      }
      if (pendingUnlinked.length) {
        parts.push(
          `${pendingUnlinked.length} 条未挂工序材料（${pendingUnlinked.map((m: any) => m.name).join('、')}）已暂挂到首道工序「${list[ti].processName}」，请核对归属`,
        )
      }
      if (parts.length) unlinkedNote = `；另有 ${parts.join('；')}`
    }
    productPlanCopying.value = true
    try {
      const { added, addedPrint } = applyCopiedRows(list, copyOverwrite.value)
      historyCopyVisible.value = false
      if (unlinkedNote) {
        const hasPending = pendingUnlinked.length > 0
        ElMessageBox.alert(
          `${unlinkedNote.replace(/^；/, '')}`,
          hasPending ? '材料未挂工序提示' : '整单通用材料提示',
          {
            confirmButtonText: '知道了',
            type: hasPending ? 'warning' : 'info',
          },
        ).catch(() => {})
      }
      ElMessage.success(
        `已带出 ${added} 张卡片${addedPrint ? `、${addedPrint} 道印刷工序` : ''}（${copyOverwrite.value ? '覆盖' : '追加到现有后'}，保存后生效；产品后续升版不会自动同步）`
      )
    } finally {
      productPlanCopying.value = false
    }
  }


  return { historyCopyVisible, copyOverwrite, historyOrders, historyLoading, historySelected, historyCopying, openHistoryCopy, confirmHistoryCopy, copyPrevLoading, historySelectedRound, copyRoundFrom, copyPreviousRound, productPlan, productPlanLoading, productPlanCopying, loadProductPlan, confirmProductPlanCopy }
}
