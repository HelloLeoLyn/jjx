import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getConfigModule } from '@/api/config'
import {
  ConfigModule,
  type ConfigLoadState,
  type ConfigRecord,
  type PdfTemplateState,
} from '@/types/config'

/** 默认值（与后端 PdfTemplateConfig 保持一致，接口异常时兜底） */
export function createDefaultPdfTemplateState(): PdfTemplateState {
  return {
    companyName: '',
    companyAddress: '',
    companyPhone: '',
    companyEmail: '',
    themeColor: '#2B5AA7',
    showHeader: true,
    showFooter: true,
    signatureLabel1: '销售负责人',
    signatureLabel2: '客户确认',
    signatureLabel3: '日期',
    companyTaxNo: '',
    companyBank: '',
    companyAccount: '',
    companyLegal: '',
    companyWebsite: '',
    companyLogo: '',
  }
}

/** '1'/'true'/'yes' → true；空值用 fallback；其余 false */
function toBoolean(value: string | undefined, fallback = true): boolean {
  if (value === undefined || value === null || value === '') return fallback
  const v = value.trim().toLowerCase()
  return v === '1' || v === 'true' || v === 'yes'
}

/** 后端键值对 → 驼峰 State */
function toState(data: ConfigRecord): PdfTemplateState {
  return {
    companyName: data.company_name ?? '',
    companyAddress: data.company_address ?? '',
    companyPhone: data.company_phone ?? '',
    companyEmail: data.company_email ?? '',
    themeColor: data.theme_color ?? '#2B5AA7',
    showHeader: toBoolean(data.show_header, true),
    showFooter: toBoolean(data.show_footer, true),
    signatureLabel1: data.signature_label1 ?? '销售负责人',
    signatureLabel2: data.signature_label2 ?? '客户确认',
    signatureLabel3: data.signature_label3 ?? '日期',
    companyTaxNo: data.company_tax_no ?? '',
    companyBank: data.company_bank ?? '',
    companyAccount: data.company_account ?? '',
    companyLegal: data.company_legal ?? '',
    companyWebsite: data.company_website ?? '',
    companyLogo: data.company_logo ?? '',
  }
}

export const usePdfTemplateStore = defineStore('pdfTemplate', () => {
  /** 配置 State（默认值兜底） */
  const state = ref<PdfTemplateState>(createDefaultPdfTemplateState())
  /** 是否已成功加载 */
  const loaded = ref(false)
  /** 加载状态 */
  const loadState = ref<ConfigLoadState>('idle')
  /** 最近一次加载错误信息 */
  const error = ref('')

  /**
   * 加载配置；已加载且未强制刷新时直接返回
   */
  async function init(forceRefresh = false): Promise<void> {
    if (!forceRefresh && loaded.value) return
    loadState.value = 'loading'
    error.value = ''
    try {
      const res = await getConfigModule(ConfigModule.PdfTemplate)
      state.value = toState(res.data ?? {})
      loaded.value = true
      loadState.value = 'loaded'
    } catch (e) {
      // 失败降级默认值，不阻断业务
      state.value = createDefaultPdfTemplateState()
      loaded.value = false
      loadState.value = 'error'
      error.value = e instanceof Error ? e.message : String(e)
      console.error('[pdfTemplate] 配置加载失败，使用默认值', e)
    }
  }

  /** 重置为默认值 */
  function reset(): void {
    state.value = createDefaultPdfTemplateState()
    loaded.value = false
    loadState.value = 'idle'
    error.value = ''
  }

  return { state, loaded, loadState, error, init, reset }
})
