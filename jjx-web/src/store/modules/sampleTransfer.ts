import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { sampleTransferApi } from '@/api/sales/sampleTransfer'
import type {
  SampleMaterialItem,
  SampleProcessItem,
  SampleTransferConfirmDTO,
  SampleTransferConfirmResult,
  SampleTransferPreview,
} from '@/api/sales/sampleTransfer'

/** 打样转标准：只读预览 + 服务端自动匹配建档。 */
export const useSampleTransferStore = defineStore('sampleTransfer', () => {
  const orderId = ref<number | null>(null)
  const orderNo = ref('')
  const loading = ref(false)
  const preview = ref<SampleTransferPreview | null>(null)
  const confirming = ref(false)
  const transferResult = ref<SampleTransferConfirmResult | null>(null)

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
  const allMatched = computed(
    () =>
      (preview.value?.sampleProcesses?.length || 0) > 0 &&
      unmatchedProcessCount.value === 0 &&
      unmatchedMaterialCount.value === 0
  )

  async function loadPreview(id: number) {
    loading.value = true
    try {
      const res = await sampleTransferApi.preview(id)
      const data = res.data as unknown as SampleTransferPreview
      preview.value = data
      orderId.value = data.orderId
      orderNo.value = data.orderNo
      transferResult.value = null
    } finally {
      loading.value = false
    }
  }

  function reset() {
    orderId.value = null
    orderNo.value = ''
    preview.value = null
    transferResult.value = null
  }

  async function confirmTransfer(): Promise<SampleTransferConfirmResult | null> {
    if (orderId.value == null) return null
    if (!allMatched.value) {
      ElMessage.warning('存在未匹配的工序或物料，请先完成标准库建档')
      return null
    }
    confirming.value = true
    try {
      // 自动模式只传 orderId，映射由服务端从最新轮次重建。
      const res = await sampleTransferApi.confirm({ orderId: orderId.value } as SampleTransferConfirmDTO)
      transferResult.value = res.data as unknown as SampleTransferConfirmResult
      return transferResult.value
    } finally {
      confirming.value = false
    }
  }

  return {
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
    loadPreview,
    reset,
    confirmTransfer,
  }
})
