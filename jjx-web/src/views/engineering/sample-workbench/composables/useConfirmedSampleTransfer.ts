import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sampleOrderApi } from '@/api/sales/sampleOrder'
import { SampleRoundResultEnum } from '@/enums/sales/SampleRoundResultEnum'
import type {
  SampleMaterialItem,
  SampleProcessItem,
  SampleTransferConfirmResult,
  SampleTransferPreview,
} from '@/api/sales/sampleTransfer'

/** 打样转标准：只读预览 + 服务端自动匹配建档。 */
export function useSampleTransferStore() {
  const orderId = ref<number | null>(null)
  const orderNo = ref('')
  const loading = ref(false)
  const preview = ref<SampleTransferPreview | null>(null)
  const confirming = ref(false)
  const transferResult = ref<SampleTransferConfirmResult | null>(null)
  /** 每条材料确认的单一BOM项目 / 整单通用（dev-20261015-017） */
  const materialProjects = ref<Record<string, { processId: number | null; isCommon: boolean }>>({})

  function hasCustomParams(process: { customProcessParams?: string | null }): boolean {
    return !!process.customProcessParams?.trim()
  }

  /** 同一正数 processOrder 为一张组合卡；无有效顺序的作业项各自成组。 */
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
      let groupIndex = processOrder != null && processOrder > 0 ? indexMap.get(processOrder) : undefined
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

  const sampleProcessCount = computed(() => sampleProcessGroups.value.length)
  const sampleMaterials = computed<SampleMaterialItem[]>(() => preview.value?.sampleMaterials || [])
  const unmatchedProcesses = computed(() =>
    (preview.value?.sampleProcesses || []).filter(
      (process) => !process.matched && !hasCustomParams(process)
    )
  )
  const unmatchedMaterials = computed(() =>
    sampleMaterials.value.filter((material) => !material.matched)
  )
  const unmatchedProcessCount = computed(() => unmatchedProcesses.value.length)
  const unmatchedMaterialCount = computed(() => unmatchedMaterials.value.length)
  /** 组合项目材料：转移时必须选择一个标准BOM项目，不得默认第一道 */
  const combinationWarnings = computed(
    () => sampleMaterials.value.filter((material) => (material.projectIds?.length || 0) > 1)
  )
  /** 项目为空即公共物料；组合项目仍须选择其中一个工序。 */
  const materialProjectsValid = computed(() =>
    sampleMaterials.value.every((material) => {
      const choice = materialProjects.value[material.rowKey]
      return !!choice && ((material.projectIds?.length || 0) <= 1 || choice.processId != null)
    })
  )
  function initMaterialProjects() {
    const next: Record<string, { processId: number | null; isCommon: boolean }> = {}
    for (const material of sampleMaterials.value) {
      const ids = material.projectIds || []
      // 单一项目自动带入；空项目为公共物料，组合项目不默认第一道。
      next[material.rowKey] =
        ids.length === 1 ? { processId: ids[0], isCommon: false } : { processId: null, isCommon: true }
    }
    materialProjects.value = next
  }
  const allMatched = computed(
    () =>
      (preview.value?.sampleProcesses?.length || 0) > 0 &&
      unmatchedProcessCount.value === 0 &&
      unmatchedMaterialCount.value === 0
  )

  const confirmedRounds = ref<any[]>([])
  const selectedRoundId = ref<number | null>(null)
  let generation = 0
  let requestId = ''
  async function loadRounds(id: number) {
    reset()
    const token = generation
    selectedRoundId.value = null
    confirmedRounds.value = []
    const res = await sampleOrderApi.getRounds(id)
    if (token !== generation) return
    confirmedRounds.value = (res.data || []).filter((round: any) => round.result === SampleRoundResultEnum.CONFIRMED.value)
    if (confirmedRounds.value.length === 1) {
      selectedRoundId.value = confirmedRounds.value[0].roundId
      await loadPreview(id)
    }
  }
  async function loadPreview(id: number) {
    const roundId = selectedRoundId.value
    const token = ++generation
    preview.value = null
    requestId = ''
    if (roundId == null) { loading.value = false; return }
    loading.value = true
    try {
      const res = await sampleOrderApi.previewConfirmedRound(id, roundId)
      const data = res.data
      if (token !== generation) return
      if (data.orderId !== id || data.roundId !== roundId || data.confirmedSchemeId !== roundId) throw new Error('预览方案身份不一致')
      requestId = crypto.randomUUID()
      preview.value = data
      orderId.value = data.orderId
      orderNo.value = data.orderNo
      transferResult.value = null
      initMaterialProjects()
    } finally {
      if (token === generation) loading.value = false
    }
  }

  function reset() {
    generation++
    requestId = ''
    loading.value = false
    orderId.value = null
    orderNo.value = ''
    preview.value = null
    transferResult.value = null
    materialProjects.value = {}
  }

  async function confirmTransfer(): Promise<SampleTransferConfirmResult | null> {
    if (confirming.value || loading.value || !preview.value || !requestId || orderId.value == null || preview.value.roundId !== selectedRoundId.value) return null
    if (!allMatched.value) {
      ElMessage.warning('存在未匹配的工序或物料，请先完成标准库建档')
      return null
    }
    if (!materialProjectsValid.value) {
      ElMessage.warning('组合项目材料须选择其中一个BOM项目')
      return null
    }
    confirming.value = true
    const token = generation
    try {
      // 自动模式：工序/物料映射由服务端重建，本前端只提交用户对材料的项目确认。
      const res = await sampleOrderApi.confirmArchivedScheme({
        orderId: orderId.value,
        roundId: preview.value.roundId,
        confirmedSchemeId: preview.value.confirmedSchemeId,
        requestId,
        materialProjects: sampleMaterials.value.map((material) => {
          const choice = materialProjects.value[material.rowKey]
          return {
            rowKey: material.rowKey,
            processId: choice?.processId ?? null,
            isCommon: choice?.processId == null,
          }
        }),
      })
      if (token !== generation) return null
      transferResult.value = res.data
      return transferResult.value
    } finally {
      confirming.value = false
    }
  }

  return {
    confirmedRounds, selectedRoundId, loadRounds,
    orderId,
    orderNo,
    loading,
    preview,
    confirming,
    transferResult,
    allMatched,
    sampleProcessGroups,
    sampleProcessCount,
    sampleMaterials,
    unmatchedProcesses,
    unmatchedMaterials,
    unmatchedProcessCount,
    unmatchedMaterialCount,
    combinationWarnings,
    materialProjects,
    materialProjectsValid,
    loadPreview,
    reset,
    confirmTransfer,
  }
}
