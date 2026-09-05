import { ref, type Ref } from 'vue'
import {
  createQualityTemplatePrintLog,
  getQualityTemplatePage,
  type QualityTemplate,
} from '@/api/production/qualityTemplate'
import { QualityTemplateStatus } from '@/enums/production/QualityTemplateEnum'

export interface PrintLayoutOption<T extends string = string> {
  value: T
  label: string
}

export interface PrintColumnOption {
  key: string
  label: string
}

type PrintableQualityTemplate = QualityTemplate & {
  printMode?: string
  printComponent?: string
}

const templateCache = new Map<string, Promise<PrintableQualityTemplate | undefined>>()

function readStoredArray(storageKey: string, fallback: string[]): string[] {
  try {
    const raw = localStorage.getItem(storageKey)
    if (raw === null) return [...fallback]
    const value: unknown = JSON.parse(raw)
    return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [...fallback]
  } catch {
    return [...fallback]
  }
}

export function usePrintLayout<T extends string>(
  storageKey: string,
  options: readonly PrintLayoutOption<T>[]
): {
  layout: Ref<T>
  setLayout: (value: T) => void
  layouts: readonly PrintLayoutOption<T>[]
} {
  if (!options.length) throw new Error('usePrintLayout requires at least one layout option')

  const values = options.map((option) => option.value)
  const stored = localStorage.getItem(storageKey) as T | null
  const initial = stored && values.includes(stored) ? stored : options[0].value
  const layout = ref<T>(initial) as Ref<T>

  function setLayout(value: T) {
    if (!values.includes(value)) return
    layout.value = value
    localStorage.setItem(storageKey, value)
  }

  return { layout, setLayout, layouts: options }
}

export function usePrintColumns<T extends PrintColumnOption>(
  storageKey: string,
  columns: readonly T[]
): {
  enabledKeys: Ref<string[]>
  toggle: (key: string, enabled?: boolean) => void
  allKeys: string[]
} {
  const allKeys = columns.map((column) => column.key)
  const validKeys = new Set(allKeys)
  const stored = readStoredArray(storageKey, allKeys)
  const enabledKeys = ref(stored.filter((key) => validKeys.has(key)))

  function toggle(key: string, enabled?: boolean) {
    if (!validKeys.has(key)) return
    const next = new Set(enabledKeys.value)
    const shouldEnable = enabled ?? !next.has(key)
    if (shouldEnable) next.add(key)
    else next.delete(key)
    enabledKeys.value = allKeys.filter((columnKey) => next.has(columnKey))
    localStorage.setItem(storageKey, JSON.stringify(enabledKeys.value))
  }

  return { enabledKeys, toggle, allKeys }
}

async function findPrintTemplate(bizType: string): Promise<PrintableQualityTemplate | undefined> {
  const response: any = await getQualityTemplatePage({
    pageNum: 1,
    pageSize: 1000,
    status: QualityTemplateStatus.ACTIVE,
  })
  const data = response?.data
  const rows: PrintableQualityTemplate[] = data?.records || data?.list || (Array.isArray(data) ? data : [])
  const matches = rows.filter(
    (row) => row.bizType === bizType && row.status === QualityTemplateStatus.ACTIVE
  )
  return matches.find((row) => row.printMode || row.printComponent) || matches[0]
}

export function usePrintLog(bizType: string): {
  log: (bizId: number) => Promise<void>
} {
  async function getTemplate() {
    let request = templateCache.get(bizType)
    if (!request) {
      request = findPrintTemplate(bizType)
      templateCache.set(bizType, request)
      request.catch(() => templateCache.delete(bizType))
    }
    return request
  }

  async function log(bizId: number) {
    const template = await getTemplate()
    if (!template?.id) return
    await createQualityTemplatePrintLog(template.id, bizType, bizId)
  }

  return { log }
}
