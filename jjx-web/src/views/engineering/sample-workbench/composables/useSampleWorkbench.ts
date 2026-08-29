import { ref, reactive, computed, watch, nextTick } from 'vue'
import { useRoute, useRouter, onBeforeRouteLeave } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadProps } from 'element-plus'
import { debounce } from 'lodash-es'
import request from '@/utils/request'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { materialApi } from '@/api/inventory/material'
import { useDict } from '@/composables/useDict'

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
  const savingPlan = ref(false)
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
        return '⏳ 未同步'
      case 'saving':
        return '🔄 保存中'
      case 'error':
        return '❌ 保存失败'
      default:
        return '✅ 已同步'
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

  // 从历史打样复制
  const historyCopyVisible = ref(false)
  const historyOrders = ref<any[]>([])
  const historyLoading = ref(false)
  const historySelected = ref<any>(null)
  const historyCopying = ref(false)
  async function openHistoryCopy() {
    historyCopyVisible.value = true
    historySelected.value = null
    historyLoading.value = true
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
  async function confirmHistoryCopy() {
    if (readonlyMode.value) return
    const src = historySelected.value
    if (!src) return
    historyCopying.value = true
    try {
      const res = await sampleOrderApi.listProcesses(src.orderId)
      const list: any[] = (res.data || []).sort(
        (a: any, b: any) =>
          (a.processOrder || 999) - (b.processOrder || 999) ||
          (a.processId || 0) - (b.processId || 0)
      )
      if (!list.length) {
        ElMessage.warning('该样品单没有工序计划')
        return
      }
      // 2026-08-12 DEV-988：拆分印刷(PRINT)与冲型组装(ASSEMBLY)——印刷行追加到印刷表格
      const printRows = list.filter((p: any) => p.majorCategory === 'PRINT')
      const assemblyRows = list.filter((p: any) => p.majorCategory !== 'PRINT')
      // 按 processOrder 分组为卡片，追加到当前 planList 后面（不覆盖）
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
          status: 0,
          processNote: first.processNote || '',
          materials: first.materials || null,
        })
        planList.value.push(pc)
        added++
      }
      // 印刷行追加到 printList（参数色号/油墨/网框随行）
      let addedPrint = 0
      for (const r of printRows) {
        printList.value.push(makePrintRow(r, printList.value.length + 1))
        addedPrint++
      }
      historyCopyVisible.value = false
      ElMessage.success(
        `已复制 ${added} 张卡片${addedPrint ? `、${addedPrint} 道印刷工序` : ''}（追加到现有后，保存后生效）`
      )
    } catch (e: any) {
      ElMessage.error(e?.message || '复制失败')
    } finally {
      historyCopying.value = false
    }
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
        indexNumber: i.indexNumber ?? null,
      })),
      category: extra.category ?? '',
      majorCategory: extra.majorCategory ?? 'ASSEMBLY',
      draggingOver: false,
      status: extra.status ?? 0,
      processNote: extra.processNote || '',
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
      colorNo: params.colorNo || '',
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

  // 印刷行状态推进：0待做 → 1进行中 → 2完成
  async function advancePrint(row: any) {
    if (readonlyMode.value) return
    if (!orderId.value) return
    if (!row.processId) {
      ElMessage.warning('该印刷工序还未保存，请先保存计划')
      return
    }
    const next = row.status === 1 ? 2 : 1
    try {
      await sampleOrderApi.updateProcessItemStatus(orderId.value, row.processId, { status: next })
      row.status = next
      ElMessage.success(next === 2 ? '印刷工序已完成' : '印刷工序已开始')
      await refreshCard()
      await loadSummary()
    } catch (e: any) {
      ElMessage.error(e?.message || '操作失败')
    }
  }

  // 保存工序计划（整单覆盖当前轮次；卡片展开为多行，同卡片同行序）
  async function savePlan() {
    if (readonlyMode.value) return
    if (!orderId.value) return
    const validPrints = printList.value.filter((r) => (r.printName || '').trim())
    if (!planList.value.length && !validPrints.length) {
      ElMessage.warning('工序计划为空，请先勾选标准工序或添加印刷工序')
      return
    }
    // 保存状态：全部标记为保存中
    planList.value.forEach((pc) => (pc.saveState = 'saving'))
    savingPlan.value = true
    try {
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
            processNote: pc.processNote,
            status: pc.status ?? 0,
            indexNumber: it.indexNumber ?? undefined, // DEV-777：下标数字
          })
        })
      })
      // 印刷行（dev-20260811-009）：processOrder 接在冲型组装后面
      const baseOrder = planList.value.length
      validPrints.forEach((r, i) => {
        const params: any = { printName: r.printName }
        if (r.colorNo) params.colorNo = r.colorNo
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
          status: r.status ?? 0,
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
    } catch (e: any) {
      planList.value.forEach((pc) => (pc.saveState = 'error'))
      ElMessage.error(e?.message || '保存工序计划失败')
    } finally {
      savingPlan.value = false
    }
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
  // DEV-777：从标准工序库补 hasIndex（拖入的标准工序是否带下标）
  function enrichProcess(data: any) {
    let hasIndex = 0
    if (data.processId) {
      const src = allProcesses.value.find((x: any) => x.processId === data.processId)
      hasIndex = src?.hasIndex ?? 0
    }
    return { ...data, hasIndex }
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
  async function advancePlan(pc: any) {
    if (readonlyMode.value) return
    if (!orderId.value) return
    const next = pc.status === 1 ? 2 : 1
    pc.advancing = true
    try {
      const targetIds = pc.items.map((i: any) => i.processId).filter(Boolean)
      for (const pid of targetIds) {
        await sampleOrderApi.updateProcessItemStatus(orderId.value, pid, { status: next })
      }
      ElMessage.success(next === 2 ? '工序已完成' : '工序已开始')
      await loadPlan()
      await refreshCard()
      await loadSummary()
    } catch (e: any) {
      ElMessage.error(e?.message || '操作失败')
    } finally {
      pc.advancing = false
    }
  }

  // 保存卡片（整单保存，数据一致）
  async function saveCard(pc: any) {
    if (readonlyMode.value) return
    pc.savingCard = true
    pc.saveState = 'saving'
    try {
      // 材料行 → JSON
      const validMats = (pc.materialRows || [])
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
      pc.editing = false
      await savePlan()
      pc.saveState = 'synced'
      ElMessage.success('已保存')
    } catch (e: any) {
      pc.saveState = 'error'
      ElMessage.error(e?.message || '保存失败')
    } finally {
      pc.savingCard = false
    }
  }

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

  // ===== 汇总 =====
  const doneCount = computed(() => planList.value.filter((p) => p.status === 2).length)
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
      if (obj.colorNo) out['色号'] = obj.colorNo
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
  const bomList = ref<any[]>([])
  const engUploadRef = ref()
  const engFileList = ref<any[]>([])

  function goBack() {
    router.push('/engineering/sample-workbench')
  }

  // 有未同步卡片时离开拦截（路由离开守卫）
  onBeforeRouteLeave(async () => {
    const dirty = planList.value.some((pc) => pc.saveState === 'dirty' || pc.saveState === 'error')
    if (!dirty) return true
    try {
      await ElMessageBox.confirm(
        '有卡片尚未同步保存，确定离开吗？未保存的修改将丢失。',
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
        loadEngFiles(),
        loadSummary(),
        loadAllProcesses(),
        loadFrequentMaterials(),
      ])
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

  // 图纸
  const engBeforeUpload: UploadProps['beforeUpload'] = (file) => {
    if (file.size > 10 * 1024 * 1024) {
      ElMessage.warning('文件不能超过10MB')
      return false
    }
    return true
  }
  async function engUploadFile(options: any) {
    if (readonlyMode.value) return
    if (!orderId.value) return
    const fd = new FormData()
    fd.append('file', options.file)
    fd.append('bizType', 'sample')
    fd.append('bizId', String(orderId.value))
    if (card.value?.traceId) {
      fd.append('traceId', card.value.traceId)
    }
    try {
      const res = await request.post('/system/attachment/upload', fd)
      if (res.code === 200 || res.code === 0) {
        ElMessage.success('上传成功')
        await loadEngFiles()
      }
    } catch (e: any) {
      ElMessage.error(e?.message || '上传失败')
    }
  }
  async function engRemoveFile(file: any) {
    if (readonlyMode.value) return
    if (file.id) {
      try {
        await request.delete(`/system/attachment/${file.id}`)
      } catch {
        /* ignore */
      }
    }
    engFileList.value = engFileList.value.filter((f) => f.uid !== file.uid)
  }
  async function loadEngFiles() {
    if (!orderId.value) return
    try {
      const res = await request.get(`/system/attachment/list?bizType=sample&bizId=${orderId.value}`)
      engFileList.value = (res.data || []).map((a: any) => ({
        uid: a.id,
        name: a.fileName,
        url: a.filePath,
        id: a.id,
      }))
    } catch {
      engFileList.value = []
    }
  }

  // 标记完成
  async function handleMarkReady() {
    if (readonlyMode.value) return
    if (!orderId.value) return
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
              }
            : { ...r, hasIndex: r.hasIndex ?? 0 }
        })
        return makeCard(enriched, {
          uid: `db-${order}`,
          processOrder: order,
          category: first.processCategory || '',
          status: first.status ?? 0,
          processNote: first.processNote || '',
          materials: first.materials || null,
          durationMinutes: first.durationMinutes ?? null,
          startTime: first.startTime || null,
          endTime: first.endTime || null,
          operator: first.operator || '',
        })
      })
    } catch {
      planList.value = []
    }
  }

  async function loadBom() {
    if (!orderId.value) return
    try {
      const res = await sampleOrderApi.listProcesses(orderId.value)
      const procs = res.data || []
      const agg: any[] = []
      for (const p of procs) {
        if (!p.materials) continue
        try {
          const mats = JSON.parse(p.materials)
          for (const m of mats) {
            agg.push({
              process: p.processName,
              name: m.name,
              spec: m.spec,
              qty: m.qty,
              unit: m.unit,
            })
          }
        } catch {
          /* ignore */
        }
      }
      bomList.value = agg
    } catch {
      bomList.value = []
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
          processNote: pc.processNote,
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

  return {
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
    saveCard,
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
    engUploadRef,
    engFileList,
    goBack,
    loadDetail,
    formatTime,
    handleAccept,
    handleReject,
    saveNote,
    handleTransfer,
    transferDialogVisible,
    onTransferSuccess,
    engBeforeUpload,
    engUploadFile,
    engRemoveFile,
    loadEngFiles,
    handleMarkReady,
    loadPlan,
    loadBom,
    refreshCard,
    readonlyMode,
  }
}
