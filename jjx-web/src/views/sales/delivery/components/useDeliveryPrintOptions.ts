import { ref, watch } from 'vue'

const storageKey = 'sales-delivery-print-options'
const initial = (() => {
  try {
    return typeof window === 'undefined' ? {} : JSON.parse(window.localStorage.getItem(storageKey) || '{}')
  } catch {
    return {}
  }
})()
const showAmount = ref(initial?.showAmount === true)
const showWeight = ref(initial?.showWeight !== false)
watch([showAmount, showWeight], () => {
  try {
    window.localStorage.setItem(storageKey, JSON.stringify({ showAmount: showAmount.value, showWeight: showWeight.value }))
  } catch (error) {
    console.warn('送货单打印选项保存失败', error)
  }
})

/** 建单预览、正式打印与Excel导出共用同一组显示选项。 */
export function useDeliveryPrintOptions() {
  return { showAmount, showWeight }
}
