import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { sampleTransferApi } from '@/api/sales/sampleTransfer'
import type {
  SampleTransferPreview,
  SampleProcessItem,
  SampleMaterialItem,
  StandardProcessOption,
  ProcessMapping,
  MaterialMapping,
  SampleTransferConfirmResult,
} from '@/api/sales/sampleTransfer'

/**
 * 打样转标准 Store（轻量版弹窗 + 对照版全屏页共享）
 * - 打样数据固定（预览接口加载一次）
 * - 工序映射 / 物料映射为编辑状态，两页面切换不丢失
 */
export const useSampleTransferStore = defineStore('sampleTransfer', () => {
  // ==================== 状态 ====================

  /** 样品单ID */
  const orderId = ref<number | null>(null)
  /** 样品单号 */
  const orderNo = ref('')
  /** 加载中 */
  const loading = ref(false)
  /** 预览原始数据（打样数据固定，不随编辑变化） */
  const preview = ref<SampleTransferPreview | null>(null)

  /** 工序映射（编辑状态） */
  const processMappings = ref<ProcessMapping[]>([])
  /** 物料映射（编辑状态） */
  const materialMappings = ref<MaterialMapping[]>([])

  /** 确认转移中 */
  const confirming = ref(false)
  /** 最近一次确认结果 */
  const transferResult = ref<SampleTransferConfirmResult | null>(null)

  /** 标准工序库（下拉选项） */
  const standardProcesses = computed(() => preview.value?.standardProcesses || [])
  /** 标准物料库（下拉选项） */
  const standardMaterials = computed(() => preview.value?.standardMaterials || [])

  // ==================== getters ====================

  /** 是否所有工序/物料都已选择标准项（全部匹配才可确认转移；2026-08-12：带自定义参数的印刷工序豁免） */
  const allMatched = computed(() => {
    return (
      processMappings.value.length > 0 &&
      processMappings.value.every((p) => p.stdProcessId != null || hasCustomParams(p)) &&
      materialMappings.value.every((m) => m.materialId != null)
    )
  })

  /** 是否带自定义参数（印刷工序：可不选标准工序，参数原样转入工艺路线） */
  function hasCustomParams(p: { customProcessParams?: string | null }): boolean {
    return !!(p.customProcessParams && p.customProcessParams.trim())
  }

  /**
   * 打样工序分组（左侧只读数据用）
   * 同一正数 processOrder 归为一组；无有效 processOrder 的作业项各自独立成组
   */
  const sampleProcessGroups = computed(() => {
    const groups: {
      processOrder: number | null
      groupOrder: number
      items: SampleProcessItem[]
      processName: string
      itemCount: number
      hasCustomProcessParams: boolean
    }[] = []
    const indexMap = new Map<number, number>()

    for (const process of preview.value?.sampleProcesses || []) {
      const processOrder = process.processOrder
      let groupIndex: number | undefined
      if (processOrder != null && processOrder > 0) {
        groupIndex = indexMap.get(processOrder)
      }
      if (groupIndex === undefined) {
        groupIndex = groups.length
        if (processOrder != null && processOrder > 0) indexMap.set(processOrder, groupIndex)
        groups.push({
          processOrder,
          groupOrder: groupIndex + 1,
          items: [],
          processName: '',
          itemCount: 0,
          hasCustomProcessParams: false,
        })
      }
      groups[groupIndex].items.push(process)
    }

    return groups.map((group) => ({
      ...group,
      processName: group.items.map((item) => item.processName).join(' + '),
      itemCount: group.items.length,
      hasCustomProcessParams: group.items.some(hasCustomParams),
    }))
  })

  /** 打样工序道数（组合按一计算） */
  const sampleProcessCount = computed(() => sampleProcessGroups.value.length)

  /** 未匹配的物料数量（标红提示用） */
  const unmatchedMaterialCount = computed(
    () => materialMappings.value.filter((m) => m.materialId == null).length
  )

  /**
   * 组合工序分组视图（对照版全屏页用）
   * 同一 groupId（非空）的工序归为一组；groupId 为 null 的独立成组
   * 组合作为整体，不可拆散
   */
  const groupedProcesses = computed(() => {
    const groups: { groupId: number | null; groupName: string; items: ProcessMapping[] }[] = []
    const indexMap = new Map<number, number>()
    for (const p of processMappings.value) {
      if (p.groupId != null) {
        let gi = indexMap.get(p.groupId)
        if (gi === undefined) {
          gi = groups.length
          indexMap.set(p.groupId, gi)
          groups.push({ groupId: p.groupId, groupName: p.groupName || '', items: [] })
        }
        groups[gi].items.push(p)
      } else {
        groups.push({ groupId: null, groupName: p.groupName || '', items: [p] })
      }
    }
    return groups
  })

  /** 未匹配的组合工序数量（组合内任一非印刷豁免项未匹配，该组合计一） */
  const unmatchedProcessCount = computed(
    () =>
      groupedProcesses.value.filter((group) =>
        group.items.some((p) => p.stdProcessId == null && !hasCustomParams(p))
      ).length
  )

  // ==================== actions ====================

  /** 临时负数组合ID生成器（同组合共享） */
  let tempGroupIdCounter = 0
  function generateTempGroupId(): number {
    tempGroupIdCounter++
    return -(Date.now() + tempGroupIdCounter)
  }

  /** 工序类别 → 组合名称（与后端 processCategoryToGroupName 一致） */
  function categoryToGroupName(category?: string | null): string | null {
    if (!category) return null
    switch (category) {
      case 'PANEL':
        return '面板组'
      case 'UP_LINE':
        return '上线组'
      case 'DOWN_LINE':
        return '下线组'
      case 'OTHER':
        return '其他组'
      default:
        return category
    }
  }

  /** 加载预览数据并初始化映射（打样数据固定） */
  async function loadPreview(id: number) {
    loading.value = true
    try {
      const res = await sampleTransferApi.preview(id)
      const data = res.data as unknown as SampleTransferPreview
      preview.value = data
      orderId.value = data.orderId
      orderNo.value = data.orderNo

      // 初始化工序映射：同 process_order 的工序归为同一组合（临时负数 groupId）
      tempGroupIdCounter = 0
      const orderGroupMap = new Map<number, number>()
      // 标准工序 id → 图标（下标展示用）
      const stdIconMap = new Map<number, string>()
      ;(data.standardProcesses || []).forEach((s: StandardProcessOption) => {
        if (s.processId && s.icon) stdIconMap.set(s.processId, s.icon)
      })
      processMappings.value = (data.sampleProcesses || []).map((sp: SampleProcessItem) => {
        let groupId: number | null = null
        let groupName: string | null = null
        const po = sp.processOrder
        if (po != null && po > 0) {
          if (!orderGroupMap.has(po)) {
            orderGroupMap.set(po, generateTempGroupId())
          }
          groupId = orderGroupMap.get(po)!
          groupName = categoryToGroupName(sp.processCategory)
        }
        return {
          sampleProcessId: sp.processId,
          stdProcessId: sp.matchedStdProcessId, // 系统匹配推荐，用户可改
          processName: sp.processName,
          processOrder: sp.processOrder ?? 0,
          groupId,
          groupOrder: null,
          groupName,
          processCategory: sp.processCategory,
          processNote: sp.processNote,
          customProcessParams: sp.customProcessParams, // 印刷工序参数透传（2026-08-12）
          durationMinutes: sp.durationMinutes,
          // 下标：透传预览返回（hasIndex + indexNumber）
          hasIndex: sp.hasIndex ?? 0,
          indexNumber: sp.indexNumber ?? null,
          // 图标：优先从标准工序库取（带下标工序展示用）
          icon: (sp.matchedStdProcessId != null ? stdIconMap.get(sp.matchedStdProcessId) : null) || null,
        }
      })

      // 初始化物料映射：每个打样物料一行，用系统匹配推荐
      materialMappings.value = (data.sampleMaterials || []).map((m: SampleMaterialItem) => ({
        rowKey: m.rowKey,
        sourceProcessId: m.sourceProcessId,
        sourceProcessName: m.sourceProcessName,
        materialId: m.matchedMaterialId ?? 0, // 0=未匹配，前端标红
        materialName: m.matchedMaterialName || m.name,
        spec: m.spec,
        qty: m.qty ?? 1,
        unit: m.unit,
      }))

      transferResult.value = null
    } finally {
      loading.value = false
    }
  }

  /** 重置整个 Store */
  function reset() {
    orderId.value = null
    orderNo.value = ''
    preview.value = null
    processMappings.value = []
    materialMappings.value = []
    transferResult.value = null
  }

  // ===== 工序映射操作 =====

  /** 更新某工序选择的标准工序 */
  function updateProcessMapping(processId: number, stdProcessId: number) {
    const target = processMappings.value.find((p) => p.sampleProcessId === processId)
    if (!target) return
    target.stdProcessId = stdProcessId
    const std = standardProcesses.value.find((s) => s.processId === stdProcessId)
    if (std) {
      target.processName = std.processName
      target.processCategory = std.processCategory
      target.icon = std.icon || null
      target.hasIndex = std.hasIndex ?? 0
      // 改选后带下标则补 indexNumber（沿用顺序号），否则清空
      target.indexNumber = target.hasIndex === 1 ? target.processOrder : null
    }
  }

  /** 替换某工序为另一标准工序（对照版用，按行定位） */
  function replaceProcess(index: number, stdProcessId: number) {
    const target = processMappings.value[index]
    if (!target) return
    target.stdProcessId = stdProcessId
    const std = standardProcesses.value.find((s) => s.processId === stdProcessId)
    if (std) {
      target.processName = std.processName
      target.processCategory = std.processCategory
      target.icon = std.icon || null
      target.hasIndex = std.hasIndex ?? 0
      // 改选后带下标则补 indexNumber（沿用顺序号），否则清空
      target.indexNumber = target.hasIndex === 1 ? target.processOrder : null
    }
  }

  /** 删除工序（对照版用；组合内删除会拆散该组合，提示由页面层处理） */
  function removeProcess(index: number) {
    processMappings.value.splice(index, 1)
  }

  /** 删除整组工序（对照版用：按引用删除组内所有工序，保持组合完整删除） */
  function removeGroupItems(items: ProcessMapping[]) {
    if (!items || !items.length) return
    const removeSet = new Set(items)
    processMappings.value = processMappings.value.filter((p) => !removeSet.has(p))
    reorderProcesses()
  }

  /** 新增工序（对照版用：从标准工序库选，独立工序） */
  function addProcess(stdProcessId: number) {
    const std = standardProcesses.value.find((s) => s.processId === stdProcessId)
    if (!std) return
    processMappings.value.push({
      sampleProcessId: null,
      stdProcessId,
      processName: std.processName,
      processOrder: processMappings.value.length + 1,
      groupId: null,
      groupOrder: null,
      groupName: null,
      processCategory: std.processCategory,
      processNote: null,
      customProcessParams: null,
      durationMinutes: null,
      hasIndex: std.hasIndex ?? 0,
      indexNumber: (std.hasIndex ?? 0) === 1 ? processMappings.value.length + 1 : null,
      icon: std.icon || null,
    })
    reorderProcesses()
  }

  /** 组合整体移动（对照版拖拽）：移动一组到目标位置 */
  function moveGroup(fromGroupIndex: number, toGroupIndex: number) {
    const groups = groupedProcesses.value
    if (fromGroupIndex === toGroupIndex || fromGroupIndex < 0 || toGroupIndex < 0) return
    if (fromGroupIndex >= groups.length || toGroupIndex >= groups.length) return
    const moving = groups[fromGroupIndex].items
    // 从原位置移除
    const fromStart = processMappings.value.indexOf(moving[0])
    processMappings.value.splice(fromStart, moving.length)
    // 计算目标位置（toGroupIndex 在移除后的索引）
    const toGroups = groupedProcesses.value
    const targetFirst = toGroups[toGroupIndex]?.items[0]
    const insertAt = targetFirst ? processMappings.value.indexOf(targetFirst) : processMappings.value.length
    processMappings.value.splice(insertAt, 0, ...moving)
    reorderProcesses()
  }

  /** 重新生成全局顺序 */
  function reorderProcesses() {
    processMappings.value.forEach((p, i) => {
      p.processOrder = i + 1
    })
  }

  // ===== 物料映射操作 =====

  /** 更新某物料选择的标准物料 */
  function updateMaterialMapping(rowKey: string, materialId: number) {
    const target = materialMappings.value.find((m) => m.rowKey === rowKey)
    if (!target) return
    target.materialId = materialId
    const mat = standardMaterials.value.find((s) => s.materialId === materialId)
    if (mat) {
      target.materialName = mat.materialName
      target.spec = mat.specification || target.spec
      target.unit = mat.unit || target.unit
    }
  }

  /** 修改用量 */
  function updateMaterialQty(rowKey: string, qty: number) {
    const target = materialMappings.value.find((m) => m.rowKey === rowKey)
    if (target) target.qty = qty
  }

  /** 新增物料（对照版用，挂到指定工序下） */
  function addMaterial(sourceProcessId: number, sourceProcessName: string, materialId: number) {
    const mat = standardMaterials.value.find((s) => s.materialId === materialId)
    if (!mat) return
    materialMappings.value.push({
      rowKey: `${sourceProcessId}_new_${Date.now()}`,
      sourceProcessId,
      sourceProcessName,
      materialId,
      materialName: mat.materialName,
      spec: mat.specification,
      qty: 1,
      unit: mat.unit,
    })
  }

  /** 删除物料 */
  function removeMaterial(rowKey: string) {
    materialMappings.value = materialMappings.value.filter((m) => m.rowKey !== rowKey)
  }

  // ===== 确认转移 =====

  /** 调用确认转移接口 */
  async function confirmTransfer(): Promise<SampleTransferConfirmResult | null> {
    if (orderId.value == null) return null
    if (!allMatched.value) {
      ElMessage.warning(
        `还有 ${unmatchedProcessCount.value} 道组合工序、${unmatchedMaterialCount.value} 项物料未选择标准项`
      )
      return null
    }
    confirming.value = true
    try {
      const res = await sampleTransferApi.confirm({
        orderId: orderId.value,
        processMappings: processMappings.value.map((p) => ({
          ...p,
          stdProcessId: p.stdProcessId!,
        })),
        materialMappings: materialMappings.value.map((m) => ({
          ...m,
          materialId: m.materialId!,
          qty: m.qty ?? 1,
        })),
      })
      transferResult.value = res.data as unknown as SampleTransferConfirmResult
      return transferResult.value
    } finally {
      confirming.value = false
    }
  }

  return {
    // state
    orderId,
    orderNo,
    loading,
    preview,
    processMappings,
    materialMappings,
    confirming,
    transferResult,
    // getters
    standardProcesses,
    standardMaterials,
    allMatched,
    sampleProcessGroups,
    sampleProcessCount,
    unmatchedProcessCount,
    unmatchedMaterialCount,
    groupedProcesses,
    // actions
    loadPreview,
    reset,
    updateProcessMapping,
    replaceProcess,
    removeProcess,
    removeGroupItems,
    addProcess,
    moveGroup,
    reorderProcesses,
    updateMaterialMapping,
    updateMaterialQty,
    addMaterial,
    removeMaterial,
    confirmTransfer,
  }
})
