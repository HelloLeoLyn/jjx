import { useSampleBom } from './useSampleBom'
import { projectIdsOf } from './sampleMaterials'
import { useProcessMaterials } from './useProcessMaterials'
import { usePlanCopy } from './usePlanCopy'
import { useProcessExecution } from './useProcessExecution'
import { usePlanPersistence } from './usePlanPersistence'
import { ref, reactive, computed, watch } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadProps } from 'element-plus'
import request from '@/utils/request'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { materialApi } from '@/api/inventory/material'
import { useDict } from '@/composables/useDict'
import { SampleProcessStatusEnum } from '@/enums/sales'

/**
 * 打样工作台组合式函数（dev-20260811-008 组件化第 1 步）
 * 集中 workbench.vue 全部状态与逻辑，组件化后各组件共享
 */
export function useSampleWorkbench() {
  const route = useRoute()
  const router = useRouter()

  const card = ref<any>({})
  const orderId = computed(() => Number(route.query.orderId))
  const readonlyMode = computed(() => route.query.readonly === 'true')

  const saving = ref(false)
  const form = reactive({ note: '' })

  // ===== 工序计划（方案A：卡片 = 一个工序单元，可挂多个标准工序）=====
  const planList = ref<any[]>([])

  // ===== 一级大类（dev-20260811-009）：ASSEMBLY冲型组装 / PRINT印刷 =====
  const majorCategory = ref<'ASSEMBLY' | 'PRINT'>('ASSEMBLY')
  // 印刷工序列表（表格输入，无标准工序全自定义；每行一道印刷）
  const printList = ref<any[]>([])

  // ===== 批次3：常用物料 / 保存状态 / 批量编辑 / 历史复制 =====

  // 常用物料快捷区（历史高频物料 Top10）
  const frequentMaterials = ref<any[]>([])

  // 保存状态：synced 已同步 / dirty 未同步 / saving 保存中 / error 失败
  function saveStateText(pc: any): string {
    switch (pc.saveState) {
      case 'dirty':
        return '未保存'
      case 'saving':
        return '🔄 保存中'
      case 'error':
        return '❌ 保存失败'
      default:
        return '已保存'
    }
  }

  // 标记卡片已修改（未同步）
  function markDirty(pc: any) {
    if (pc.saveState !== 'saving') pc.saveState = 'dirty'
  }

  // 批量编辑模式
  const batchMode = ref(false)
  const batchSelected = ref<Set<string>>(new Set())
  const batchCategory = ref<string | null>(null)
  function toggleBatchMode() {
    batchMode.value = !batchMode.value
    batchSelected.value = new Set()
    batchCategory.value = null
  }
  function toggleBatchSelect(pc: any, v: boolean) {
    const s = new Set(batchSelected.value)
    if (v) s.add(pc.uid)
    else s.delete(pc.uid)
    batchSelected.value = s
  }
  /** 全选当前 tab 下所有卡片（dev-20260811-008） */
  function toggleBatchSelectAll() {
    const tabCards = cardsByTab(activePlanTab.value)
    const allSelected =
      tabCards.length > 0 && tabCards.every((pc: any) => batchSelected.value.has(pc.uid))
    const s = new Set(batchSelected.value)
    if (allSelected) {
      tabCards.forEach((pc: any) => s.delete(pc.uid))
    } else {
      tabCards.forEach((pc: any) => s.add(pc.uid))
    }
    batchSelected.value = s
  }
  function batchSelectedCards(): any[] {
    return planList.value.filter((pc) => batchSelected.value.has(pc.uid))
  }
  // 统一设置工序类别
  function applyBatchCategory(cat: string | undefined) {
    if (readonlyMode.value) return
    if (!cat) return
    batchSelectedCards().forEach((pc) => {
      pc.category = cat
      pc.items.forEach((it: any) => (it.processCategory = cat))
      markDirty(pc)
    })
    batchCategory.value = null
  }
  // 批量删除
  function batchDelete() {
    if (readonlyMode.value) return
    const n = batchSelected.value.size
    if (!n) return
    ElMessageBox.confirm(`确定删除选中的 ${n} 张卡片？`, '批量删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
      .then(() => {
        planList.value = planList.value.filter((pc) => !batchSelected.value.has(pc.uid))
        batchSelected.value = new Set()
      })
      .catch(() => {})
  }
  // 批量添加材料
  const batchMaterialVisible = ref(false)
  const batchMaterialId = ref<number | null>(null)
  const batchMaterialOptions = ref<any[]>([])
  const batchMaterialLoading = ref(false)
  function openBatchMaterial() {
    batchMaterialVisible.value = true
    batchMaterialId.value = null
    batchMaterialOptions.value = []
    searchBatchMaterial('')
  }
  async function searchBatchMaterial(query: string) {
    batchMaterialLoading.value = true
    try {
      const params: any = { pageNum: 1, pageSize: 20 }
      if (query.trim()) params.materialName = query.trim()
      const res: any = await materialApi.search(params)
      batchMaterialOptions.value = res?.data?.records || res?.data || []
    } catch {
      batchMaterialOptions.value = []
    } finally {
      batchMaterialLoading.value = false
    }
  }
  function confirmBatchMaterial() {
    if (readonlyMode.value) return
    if (!batchMaterialId.value) return
    const mat = batchMaterialOptions.value.find((o) => o.materialId === batchMaterialId.value)
    if (!mat) return
    batchSelectedCards().forEach((pc) => {
      const mats = parseMaterials(pc.materials) || []
      mats.push({
        name: mat.materialName,
        spec: mat.specification || '',
        qty: 1,
        unit: mat.unit || 'PCS',
        materialId: mat.materialId,
        materialCode: mat.materialCode || '',
        baseQty: 1,
        moduleQty: 1,
        projectIds: projectIdsOf(pc),
      })
      pc.materials = JSON.stringify(mats)
      // 编辑态同步到 materialRows
      if (pc.editing) {
        pc.materialRows.push({
          name: mat.materialName,
          spec: mat.specification || '',
          qty: 1,
          unit: mat.unit || 'PCS',
          materialId: mat.materialId,
          materialCode: mat.materialCode || '',
          baseQty: 1,
          moduleQty: 1,
          projectIds: projectIdsOf(pc),
          projectInherited: true,
          options: [],
          loading: false,
          uid: genUid(),
          pageNum: 1,
          total: 0,
          lastQuery: '',
        })
      }
      markDirty(pc)
    })
    batchMaterialVisible.value = false
    batchMaterialId.value = null
    ElMessage.success('已批量添加材料')
  }

  // 清空当前轮次编辑区（未保存内容丢弃；需再次保存才同步后端覆盖，2026-09-05 用户提出）
  function clearCurrentRound() {
    if (readonlyMode.value) return
    planList.value = []
    printList.value = []
    clearStandalone()
    ElMessage.success('已清空当前轮次内容（保存后生效）')
  }

  // 常用物料统计：优先产品线历史（同产品），不足则客户历史
  async function loadFrequentMaterials() {
    if (!orderId.value) return
    try {
      const all: any[] = (await sampleOrderApi.list({})).data || []
      const others = all.filter((o: any) => o.orderId !== orderId.value && o.sampleStatus === 7)
      const customerId = card.value?.customerId
      let candidates = others
      // 产品线优先：先按客户筛（SalesOrder 无产品字段，客户维度最可靠），有足够数据用客户
      if (customerId) {
        const byCustomer = others.filter((o: any) => o.customerId === customerId)
        if (byCustomer.length >= 1) candidates = byCustomer
      }
      const freq = new Map<string, any>()
      for (const o of candidates.slice(0, 10)) {
        try {
          const procs = (await sampleOrderApi.listProcesses(o.orderId)).data || []
          for (const p of procs) {
            if (!p.materials) continue
            const mats = parseMaterials(p.materials)
            for (const m of mats) {
              const key = m.materialId ? `id:${m.materialId}` : `name:${m.name}`
              const cur = freq.get(key)
              if (cur) cur.count++
              else freq.set(key, { name: m.name, spec: m.spec, materialId: m.materialId, count: 1 })
            }
          }
        } catch {
          /* ignore */
        }
      }
      frequentMaterials.value = Array.from(freq.values())
        .sort((a, b) => b.count - a.count)
        .slice(0, 10)
    } catch {
      frequentMaterials.value = []
    }
  }
  // 常用物料点击 → 加入当前编辑/激活卡片
  function addFrequentMaterial(fm: any) {
    if (readonlyMode.value) return
    const tabCards = cardsByTab(activePlanTab.value)
    const target = tabCards.find((c: any) => c.editing) || tabCards[tabCards.length - 1]
    if (!target) {
      ElMessage.warning('请先添加工序卡片')
      return
    }
    const mats = parseMaterials(target.materials) || []
    mats.push({
      name: fm.name,
      spec: fm.spec || '',
      qty: 1,
      unit: 'PCS',
      materialId: fm.materialId,
      materialCode: '',
      baseQty: 1,
      moduleQty: 1,
      projectIds: projectIdsOf(target),
    })
    target.materials = JSON.stringify(mats)
    if (target.editing) {
      target.materialRows.push({
        name: fm.name,
        spec: fm.spec || '',
        qty: 1,
        unit: 'PCS',
        materialId: fm.materialId,
        materialCode: '',
        baseQty: 1,
        moduleQty: 1,
        projectIds: projectIdsOf(target),
        projectInherited: true,
        options: [],
        loading: false,
        uid: genUid(),
        pageNum: 1,
        total: 0,
        lastQuery: '',
      })
    }
    markDirty(target)
    ElMessage.success(
      `已添加 ${fm.name} 到「${target.items.map((i: any) => i.processName).join('+') || '未命名'}」`
    )
  }

  // 计划标签：面板/上线/下线/未分类（卡片属于哪个标签 = 它的项目结构）
  const planTabs = [
    { value: 'PANEL', label: '面板' },
    { value: 'UP_LINE', label: '上线' },
    { value: 'DOWN_LINE', label: '下线' },
    { value: '', label: '未分类' },
  ]
  const activePlanTab = ref('PANEL')

  function cardsByTab(value: string) {
    return planList.value.filter((pc) => (pc.category || '') === value)
  }

  const { options: typeOptions } = useDict('process_type')
  const { options: categoryOptions } = useDict('process_category')

  function typeLabel(value: string): string {
    return typeOptions.value.find((i: any) => i.itemValue === value)?.label || value || ''
  }
  function categoryLabel(value: string): string {
    return categoryOptions.value.find((i: any) => i.itemValue === value)?.label || value || ''
  }

  const allProcesses = ref<any[]>([])
  const { standaloneMaterials, bomList, stateOf: bomStateOf, loadBom, updateMaterial, removeMaterial, addMaterial, clearStandalone, debouncedSearchBomMaterials, onBomMaterialSelected, onBomVisible } = useSampleBom({ orderId, card, planList, printList, readonlyMode, markOwnerDirty: markDirty })


  const { addMaterialRow, startEdit, cancelEdit, finishEdit, searchMaterials, debouncedSearchMaterials, onSelectVisibleChange,
    loadMoreMaterials, onMaterialSelected, materialCreateVisible, materialPreset, openMaterialCreate,
    onMaterialCreated, parseMaterials } = useProcessMaterials(readonlyMode, genUid)
  const { historyCopyVisible, copyOverwrite, historyOrders, historyLoading, historySelected,
    historyCopying, openHistoryCopy, confirmHistoryCopy, copyPrevLoading, historySelectedRound,
    copyRoundFrom, copyPreviousRound, productPlan, productPlanLoading, productPlanCopying,
    loadProductPlan, confirmProductPlanCopy } = usePlanCopy({
      orderId, readonlyMode, card, planList, printList, allProcesses, makeCard, makePrintRow, genUid, parseMaterials,
    })
  const { savingPlan, hasUnsavedChanges, savePlan, loadPlan, markSaved } = usePlanPersistence({
    readonlyMode, orderId, card, planList, printList, standaloneMaterials, allProcesses, makeCard, makePrintRow,
    loadBom, refreshCard, loadSummary,
  })
  const { advancePrint, advancePlan, blockReason: execBlockReason, printBlockReason } = useProcessExecution({
    readonlyMode, orderId, savingPlan, refreshCard, loadSummary,
  })
  async function loadAllProcesses() {
    try {
      const res = await request.get('/engineering/standard-processes/page', {
        params: {
          pageNum: 1,
          pageSize: 100,
          isEnabled: 1,
          orderByColumn: 'displayOrder',
          isAsc: 'asc',
        },
      })
      allProcesses.value = res.data?.records || []
    } catch {
      allProcesses.value = []
    }
  }

  function genUid() {
    return Date.now().toString(36) + Math.random().toString(36).slice(2, 6)
  }

  // 新建卡片（items: 标准工序数组）
  function makeCard(items: any[], extra: any = {}) {
    return {
      uid: extra.uid || `new-${genUid()}`,
      processOrder: extra.processOrder ?? 0,
      items: items.map((i: any) => ({
        stdProcessId: i.stdProcessId ?? i.processId ?? null,
        processName: i.processName,
        processType: i.processType || '',
        processCategory: i.processCategory || '',
        icon: i.icon || '',
        processId: i.processId ?? null,
        // DEV-777：下标（hasIndex 来自标准工序，indexNumber 用户输入）
        hasIndex: i.hasIndex ?? 0,
        hasWorkInstruction: i.hasWorkInstruction ?? 0,
        indexNumber: i.indexNumber ?? null,
        workInstruction: i.workInstruction ?? i.processNote ?? '',
      })),
      category: extra.category ?? '',
      majorCategory: extra.majorCategory ?? 'ASSEMBLY',
      draggingOver: false,
      status: extra.status ?? 0,
      operationRemark: extra.operationRemark ?? extra.remark ?? '',
      remarkEditing: false,
      materials: extra.materials || null,
      durationMinutes: extra.durationMinutes ?? null,
      startTime: extra.startTime || null,
      endTime: extra.endTime || null,
      operator: extra.operator || '',
      materialRows: [] as any[],
      editing: false,
      advancing: false,
      savingCard: false,
      // 批次3：保存状态（synced/dirty/saving/error）
      saveState: extra.saveState || 'synced',
    }
  }

  // 印刷表格行（dev-20260811-009）：无标准工序全自定义，表格输入
  function makePrintRow(src: any = {}, order = 1) {
    let params: any = {}
    if (src.customProcessParams) {
      try {
        params =
          typeof src.customProcessParams === 'string'
            ? JSON.parse(src.customProcessParams)
            : src.customProcessParams
      } catch {
        params = {}
      }
    }
    return {
      uid: src.processId ? `dbp-${src.processId}` : `new-${genUid()}`,
      processId: src.processId ?? null,
      processOrder: src.processOrder ?? order,
      category: src.processCategory || '',
      status: src.status ?? 0,
      materials: src.materials || null,
      durationMinutes: src.durationMinutes ?? null,
      startTime: src.startTime || null,
      endTime: src.endTime || null,
      operator: src.operator || '',
      printName: params.printName || src.processName || '',
      colorNo: params.colorNo ?? '',
      colorNoLabel: params.colorNoLabel ?? '',
      inkMaterialId: params.inkMaterialId ?? null,
      inkNo: params.inkNo || '',
      screenNo: params.screenNo || '',
      saveState: 'synced',
    }
  }

  // 新增印刷行（表格追加）
  function addPrintRow(category = '') {
    if (readonlyMode.value) return
    printList.value.push(makePrintRow({ processCategory: category }, printList.value.length + 1))
  }

  // 删除印刷行
  function removePrintRow(row: any) {
    if (readonlyMode.value) return
    const i = printList.value.indexOf(row)
    if (i >= 0) {
      printList.value.splice(i, 1)
      ElMessage.success('已删除该印刷工序（保存后生效）')
    }
  }

  // 印刷行上移/下移（dir: -1 上移 / 1 下移）
  function movePrintRow(row: any, dir: number) {
    if (readonlyMode.value) return
    const i = printList.value.indexOf(row)
    const j = i + dir
    if (i < 0 || j < 0 || j >= printList.value.length) return
    const arr = printList.value
    ;[arr[i], arr[j]] = [arr[j], arr[i]]
    ElMessage.success('已调整顺序（保存后生效）')
  }

  // 卡片内追加标准工序（弹窗多选，任意结构）
  const cardPickerVisible = ref(false)
  const cardPickerTarget = ref<any>(null)
  const cardPickerIds = ref<number[]>([])

  function openCardPicker(pc: any) {
    cardPickerTarget.value = pc
    cardPickerIds.value = pc.items
      .map((i: any) => i.stdProcessId)
      .filter((id: any): id is number => !!id)
    cardPickerVisible.value = true
  }

  // 弹窗确认：追加到卡片（去重，保留自定义项）
  function onCardPickerConfirm(items: any[]) {
    if (readonlyMode.value) return
    cardPickerVisible.value = false
    const target = cardPickerTarget.value
    if (!target) return
    const existing = new Set(target.items.map((i: any) => i.stdProcessId).filter(Boolean))
    for (const i of items) {
      if (existing.has(i.processId)) continue
      target.items.push({
        stdProcessId: i.processId,
        processName: i.processName,
        processType: i.processType,
        processCategory: i.processCategory,
        icon: i.icon,
        processId: target.processId ?? null,
      })
      existing.add(i.processId)
    }
    markDirty(target)
  }

  // ===== 拖拽接收（左侧工序 → 右侧卡片组合）=====
  // 从标准工序库补实例扩展能力（数字下标/作业说明）。
  function enrichProcess(data: any) {
    let hasIndex = 0
    let hasWorkInstruction = 0
    if (data.processId) {
      const src = allProcesses.value.find((x: any) => x.processId === data.processId)
      hasIndex = src?.hasIndex ?? 0
      hasWorkInstruction = src?.hasWorkInstruction ?? 0
    }
    return { ...data, hasIndex, hasWorkInstruction }
  }

  // 下标输入弹窗（DEV-777，仿工艺路线）
  const indexDialogVisible = ref(false)
  const indexDialogValue = ref<number | null>(null)
  const indexDialogName = ref('')
  let pendingIndexItem: any = null

  function openIndexDialog(item: any) {
    pendingIndexItem = item
    indexDialogName.value = item.processName || ''
    indexDialogValue.value = item.indexNumber ?? null
    indexDialogVisible.value = true
  }

  function confirmIndexDialog() {
    if (pendingIndexItem && indexDialogValue.value != null && indexDialogValue.value > 0) {
      pendingIndexItem.indexNumber = Math.floor(indexDialogValue.value)
      // 找到所属卡片标记未同步
      planList.value.forEach((pc: any) => {
        if (pc.items.includes(pendingIndexItem)) markDirty(pc)
      })
    }
    indexDialogVisible.value = false
    pendingIndexItem = null
  }

  // 带下标标准工序拖入后弹窗输数字
  function maybePromptIndex(item: any) {
    if (item.hasIndex === 1) openIndexDialog(item)
  }

  // IconStepBadge 点击图标改下标数字
  function onUpdateIndex(pc: any, item: any, n: number) {
    item.indexNumber = Math.floor(n)
    markDirty(pc)
  }

  function parseDragData(e: DragEvent): any {
    try {
      const raw = e.dataTransfer?.getData('application/json')
      return raw ? JSON.parse(raw) : null
    } catch {
      return null
    }
  }

  // 拖到空白区 → 新建卡片（自动进入编辑状态）
  function onPlanDrop(e: DragEvent) {
    if (readonlyMode.value) return
    const data = parseDragData(e)
    if (!data) return
    const enriched = enrichProcess(data)
    // 在哪个标签拖入，卡片就属于哪个项目结构（未分类标签=不设结构）
    const pc = makeCard([enriched], {
      category: activePlanTab.value === '' ? undefined : activePlanTab.value,
    })
    planList.value.push(pc)
    startEdit(pc)
    markDirty(pc)
    clearDragOver()
    maybePromptIndex(pc.items[0])
  }

  // 拖到卡片 → 追加组合（去重），并自动进入编辑状态
  function onCardDrop(e: DragEvent, pc: any) {
    if (readonlyMode.value) return
    const data = parseDragData(e)
    pc.draggingOver = false
    if (!data) return
    if (!pc.items.some((i: any) => i.stdProcessId === data.processId)) {
      const enriched = enrichProcess(data)
      pc.items.push({
        stdProcessId: enriched.processId,
        processName: enriched.processName,
        processType: enriched.processType || '',
        processCategory: enriched.processCategory || '',
        icon: enriched.icon || '',
        processId: pc.processId ?? null,
        hasIndex: enriched.hasIndex,
        hasWorkInstruction: enriched.hasWorkInstruction,
        indexNumber: null,
      })
      markDirty(pc)
      maybePromptIndex(pc.items[pc.items.length - 1])
    }
    if (!pc.editing) startEdit(pc)
  }

  function onCardDragOver(pc: any) {
    pc.draggingOver = true
  }

  function onCardDragLeave(pc: any) {
    pc.draggingOver = false
  }

  function clearDragOver() {
    planList.value.forEach((pc: any) => (pc.draggingOver = false))
  }

  // 移除卡片内标准工序
  function removeCardItem(pc: any, idx: number) {
    if (readonlyMode.value) return
    // 只移除标准工序，卡片保留（可再拖入）；删卡片走右下角删除按钮
    pc.items.splice(idx, 1)
    markDirty(pc)
  }

  // 删除整张卡片（保存计划时生效）
  function removePlanCard(pc: any) {
    if (readonlyMode.value) return
    planList.value = planList.value.filter((x) => x !== pc)
    if (batchSelected.value.has(pc.uid)) {
      const s = new Set(batchSelected.value)
      s.delete(pc.uid)
      batchSelected.value = s
    }
  }
  // ===== 汇总 =====
  // 完成进度 = 组装卡片 + 印刷行（dev-1788002406240：展示用，与后端校验口径对齐）
  const doneCount = computed(
    () =>
      planList.value.filter((p) => p.status === SampleProcessStatusEnum.DONE.value).length +
      printList.value.filter((r) => r.status === SampleProcessStatusEnum.DONE.value).length
  )
  const totalCount = computed(() => planList.value.length + printList.value.length)
  const summary = ref<any>({})
  async function loadSummary() {
    if (!orderId.value) return
    try {
      const res = await request.get(`/sales/sample-order/summary/${orderId.value}`)
      summary.value = res.data || {}
    } catch {
      summary.value = {}
    }
  }

  // ===== 轮次展示（DEV-500）=====
  const roundList = ref<any[]>([])
  const activeRound = ref('')
  const isCurrentRound = computed(
    () => Number(activeRound.value) === (card.value?.sampleRound || 1)
  )
  const activeRoundData = computed(
    () => roundList.value.find((r) => String(r.roundNo) === activeRound.value) || null
  )
  const activeRoundProcesses = computed(() => {
    const d = activeRoundData.value
    if (!d?.processSnapshot) return []
    try {
      const arr = JSON.parse(d.processSnapshot)
      return Array.isArray(arr) ? arr : []
    } catch {
      return []
    }
  })
  const activeRoundBom = computed(() => {
    const d = activeRoundData.value
    if (!d?.bomSnapshot) return []
    try {
      const arr = JSON.parse(d.bomSnapshot)
      return Array.isArray(arr) ? arr : []
    } catch {
      return []
    }
  })
  // 历史轮次印刷工序（dev-20260811-009）：从轮次快照过滤 PRINT 大类
  const activeRoundPrintList = computed(() =>
    activeRoundProcesses.value.filter((p: any) => p.majorCategory === 'PRINT')
  )
  // 印刷参数解析：customProcessParams JSON → 键值对
  function printParamsOf(row: any): Record<string, string> {
    if (!row?.customProcessParams) return {}
    try {
      const obj =
        typeof row.customProcessParams === 'string'
          ? JSON.parse(row.customProcessParams)
          : row.customProcessParams
      const out: Record<string, string> = {}
      if (obj.printName) out['印刷名称'] = obj.printName
      if (obj.colorNo) out['色号'] = obj.colorNoLabel || obj.colorNo
      if (obj.inkNo) out['油墨编号'] = obj.inkNo
      if (obj.screenNo) out['网框编号'] = obj.screenNo
      return out
    } catch {
      return {}
    }
  }
  async function loadRounds() {
    if (!orderId.value) return
    try {
      const res = await sampleOrderApi.getRounds(orderId.value)
      const rounds: any[] = res.data || []
      const current = card.value?.sampleRound || 1
      if (!rounds.some((r: any) => r.roundNo === current)) {
        rounds.push({ roundNo: current, result: 'pending' })
      }
      roundList.value = rounds.sort((a, b) => a.roundNo - b.roundNo)
      activeRound.value = String(current)
    } catch {
      roundList.value = [{ roundNo: card.value?.sampleRound || 1, result: 'pending' }]
      activeRound.value = String(card.value?.sampleRound || 1)
    }
  }

  function goBack() {
    router.push('/engineering/sample-workbench')
  }

  // 有未同步卡片时离开拦截（路由离开守卫）
  onBeforeRouteLeave(async () => {
    const dirty = hasUnsavedChanges.value
    if (!dirty) return true
    try {
      await ElMessageBox.confirm(
        '工序计划有未保存修改，确定离开吗？未保存的修改将丢失。',
        '未保存修改',
        {
          confirmButtonText: '仍要离开',
          cancelButtonText: '留下继续编辑',
          type: 'warning',
        }
      )
      return true
    } catch {
      return false
    }
  })

  async function loadDetail() {
    if (!orderId.value) return
    try {
      const res = await sampleOrderApi.getInfo(orderId.value)
      card.value = res.data || {}
      form.note = card.value.engineeringNote || ''
      await Promise.all([
        loadRounds(),
        loadPlan(),
        loadBom(),
        loadCustomerFiles(),
        loadSummary(),
        loadAllProcesses(),
        loadFrequentMaterials(),
      ])
      markSaved()
    } catch (e: any) {
      ElMessage.error(e?.message || '加载样品单失败')
    }
  }

  function formatTime(t?: string) {
    if (!t) return ''
    return t.replace('T', ' ').slice(0, 16)
  }

  // 接单：确认弹窗由调用方负责（消除双重确认），成功返回 true，失败提示并返回 false
  async function handleAccept(orderId: number): Promise<boolean> {
    if (readonlyMode.value) return false
    if (!orderId) return false
    try {
      await sampleOrderApi.acceptEngineering(orderId)
      return true
    } catch (e: any) {
      ElMessage.error(e?.message || '接单失败')
      return false
    }
  }

  // 拒单
  async function handleReject() {
    if (readonlyMode.value) return
    if (!orderId.value) return
    try {
      const { value } = await ElMessageBox.prompt('请填写拒单原因', '工程拒单', {
        confirmButtonText: '确认拒单',
        cancelButtonText: '取消',
        type: 'warning',
        inputPlaceholder: '拒单原因（必填）',
        inputValidator: (v: string) => (v && v.trim() ? true : '拒单原因不能为空'),
      })
      await sampleOrderApi.rejectEngineering(orderId.value, value.trim())
      ElMessage.success('已拒单，退回待审核')
      goBack()
    } catch (e: any) {
      if (e !== 'cancel') ElMessage.error(e?.message || '拒单失败')
    }
  }

  // 保存工艺参数
  async function saveNote() {
    if (readonlyMode.value) return
    if (!orderId.value) return
    saving.value = true
    try {
      await sampleOrderApi.startEngineering(orderId.value, form.note)
      card.value.engineeringNote = form.note
      ElMessage.success('工艺参数已保存')
    } catch (e: any) {
      ElMessage.error(e?.message || '保存失败')
    } finally {
      saving.value = false
    }
  }

  // 资料转移（DEV-764：改为打开轻量版弹窗，复用样品单列表同一组件/store）
  function handleTransfer() {
    if (readonlyMode.value) return
    if (!orderId.value) return
    transferDialogVisible.value = true
  }

  // 打样转标准·轻量版弹窗
  const transferDialogVisible = ref(false)
  function onTransferSuccess() {
    transferDialogVisible.value = false
    ElMessage.success('资料转移完成')
    // 刷新当前数据（BOM/路线已建档，刷新汇总）
    loadSummary()
  }

  // 往来附件（客户/销售上传：退回修改/送样登记等，bizType=sample_order；2026-09-05 轻量版只读展示）
  const customerFileList = ref<any[]>([])
  async function loadCustomerFiles() {
    if (!orderId.value) return
    try {
      const res = await request.get(
        `/system/attachment/list?bizType=sample_order&bizId=${orderId.value}`
      )
      customerFileList.value = (res.data || []).map((a: any) => ({
        id: a.id,
        name: a.fileName,
        url: a.filePath,
        createBy: a.createBy || a.create_by || '',
        time: a.createTime ? String(a.createTime).replace('T', ' ').slice(0, 16) : '',
      }))
    } catch {
      customerFileList.value = []
    }
  }

  // 标记完成（dev-1788002406240：全部工序完成后才允许）
  async function handleMarkReady() {
    if (readonlyMode.value) return
    if (!orderId.value) return
    // 前置检测：当前轮次所有工序必须已完成（与后端 markSampleReady 强制校验一致）
    // 2026-09-02 修复：卡片显示卡内工序名（不用 category 分组名冒充工序名）；
    // 印刷空行（printName 未填的占位行）不计入未完成（与 savePlan validPrints 语义一致）
    const unfinished: string[] = []
    for (const pc of planList.value) {
      if (pc.status !== SampleProcessStatusEnum.DONE.value) {
        const names = (pc.items || []).map((it: any) => it.processName).filter(Boolean)
        unfinished.push(names.length ? names.join('、') : '工序')
      }
    }
    for (const r of printList.value) {
      const pn = (r.printName || '').trim()
      if (!pn) continue // 空行占位，跳过
      if (r.status !== SampleProcessStatusEnum.DONE.value) unfinished.push(pn || '印刷工序')
    }
    if (unfinished.length) {
      ElMessage.error(`还有 ${unfinished.length} 道工序未完成（${unfinished.slice(0, 3).join('、')}${unfinished.length > 3 ? '…' : ''}），请先完成全部工序`)
      return
    }
    try {
      await ElMessageBox.confirm('确认样品制作完成？将进入待送样状态', '标记完成', {
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        type: 'success',
      })
      await sampleOrderApi.markReady(orderId.value)
      ElMessage.success('已标记完成，待送样')
      goBack()
    } catch (e: any) {
      if (e !== 'cancel') ElMessage.error(e?.message || '操作失败')
    }
  }

  async function refreshCard() {
    if (!orderId.value) return
    try {
      const res = await sampleOrderApi.getInfo(orderId.value)
      card.value = res.data
    } catch {
      /* ignore */
    }
  }

  // 卡片内容修改自动标记未同步（材料行/描述/标准工序等 v-model 直接绑定）
  // 用 deep watch 检测：editing 中的卡片内容变化 → dirty
  watch(
    () =>
      planList.value.map((pc: any) =>
        JSON.stringify({
          items: pc.items,
          materials: pc.editing ? pc.materialRows : pc.materials,
          operationRemark: pc.operationRemark,
          category: pc.category,
        })
      ),
    () => {
      planList.value.forEach((pc: any) => {
        if (pc.editing && pc.saveState === 'synced') markDirty(pc)
      })
    },
    { deep: true }
  )

  // 仅计划面板所需的能力；内部 refs 保持响应式，不复制业务数据。
  const planEditor = reactive({ batchMode, batchSelected, batchCategory, savingPlan, hasUnsavedChanges, frequentMaterials, planTabs, categoryOptions, cardsByTab, saveStateText, parseMaterials, toggleBatchMode, toggleBatchSelect, toggleBatchSelectAll, savePlan, addFrequentMaterial, onPlanDrop, onCardDrop, onCardDragOver, onCardDragLeave, removeCardItem, removePlanCard, cancelEdit, finishEdit, addMaterialRow, startEdit, openCardPicker, openBatchMaterial, batchDelete, applyBatchCategory, onUpdateIndex, searchMaterials: debouncedSearchMaterials, onMaterialSelected, onSelectVisibleChange, openMaterialCreate })

  return {
    planEditor,
    card,
    orderId,
    saving,
    savingPlan,
    form,
    planList,
    majorCategory,
    printList,
    makePrintRow,
    addPrintRow,
    removePrintRow,
    movePrintRow,
    advancePrint,
    execBlockReason,
    printBlockReason,
    frequentMaterials,
    saveStateText,
    markDirty,
    batchMode,
    batchSelected,
    batchCategory,
    toggleBatchMode,
    toggleBatchSelect,
    toggleBatchSelectAll,
    batchSelectedCards,
    applyBatchCategory,
    batchDelete,
    batchMaterialVisible,
    batchMaterialId,
    batchMaterialOptions,
    batchMaterialLoading,
    openBatchMaterial,
    searchBatchMaterial,
    confirmBatchMaterial,
    historyCopyVisible,
    historyOrders,
    historyLoading,
    historySelected,
    historyCopying,
    openHistoryCopy,
    confirmHistoryCopy,
    copyPreviousRound,
    copyRoundFrom,
    copyPrevLoading,
    historySelectedRound,
    copyOverwrite,
    productPlan,
    productPlanLoading,
    productPlanCopying,
    loadProductPlan,
    confirmProductPlanCopy,
    clearCurrentRound,
    loadFrequentMaterials,
    addFrequentMaterial,
    planTabs,
    activePlanTab,
    cardsByTab,
    typeOptions,
    categoryOptions,
    typeLabel,
    categoryLabel,
    allProcesses,
    loadAllProcesses,
    genUid,
    makeCard,
    savePlan,
    cardPickerVisible,
    cardPickerTarget,
    cardPickerIds,
    openCardPicker,
    onCardPickerConfirm,
    enrichProcess,
    indexDialogVisible,
    indexDialogValue,
    indexDialogName,
    openIndexDialog,
    confirmIndexDialog,
    maybePromptIndex,
    onUpdateIndex,
    parseDragData,
    onPlanDrop,
    onCardDrop,
    onCardDragOver,
    onCardDragLeave,
    clearDragOver,
    removeCardItem,
    removePlanCard,
    advancePlan,
    cancelEdit,
    finishEdit,
    addMaterialRow,
    startEdit,
    searchMaterials,
    debouncedSearchMaterials,
    onSelectVisibleChange,
    loadMoreMaterials,
    onMaterialSelected,
    materialCreateVisible,
    materialPreset,
    openMaterialCreate,
    onMaterialCreated,
    parseMaterials,
    doneCount,
    totalCount,
    summary,
    loadSummary,
    roundList,
    activeRound,
    isCurrentRound,
    activeRoundData,
    activeRoundProcesses,
    activeRoundBom,
    activeRoundPrintList,
    printParamsOf,
    loadRounds,
    bomList,
    standaloneMaterials,
    updateMaterial,
    removeMaterial,
    addMaterial,
    bomStateOf,
    debouncedSearchBomMaterials,
    onBomMaterialSelected,
    onBomVisible,
    customerFileList,
    goBack,
    loadDetail,
    formatTime,
    handleAccept,
    handleReject,
    saveNote,
    handleTransfer,
    transferDialogVisible,
    onTransferSuccess,
    loadCustomerFiles,
    handleMarkReady,
    loadPlan,
    loadBom,
    refreshCard,
    readonlyMode,
  }
}
