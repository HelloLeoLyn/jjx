import { onMounted, reactive, ref } from 'vue'
import { sysConfigApi } from '@/api/system/sysConfig'

export interface CompanyConfig {
  name: string
  address: string
  phone: string
  email: string
  taxNo: string
  bank: string
  account: string
  legal: string
  website: string
  logo: string
}

const configKeys: Record<keyof CompanyConfig, string> = {
  name: 'company_name',
  address: 'company_address',
  phone: 'company_phone',
  email: 'company_email',
  taxNo: 'company_tax_no',
  bank: 'company_bank',
  account: 'company_account',
  legal: 'company_legal',
  website: 'company_website',
  logo: 'company_logo',
}

export function useCompanyConfig() {
  const company = reactive<CompanyConfig>({
    name: '',
    address: '',
    phone: '',
    email: '',
    taxNo: '',
    bank: '',
    account: '',
    legal: '',
    website: '',
    logo: '',
  })
  const loading = ref(false)

  async function loadCompanyConfig() {
    loading.value = true
    try {
      const res = await sysConfigApi.listByGroup('pdf_template')
      const configMap = new Map(
        (res.data || []).map((item) => [item.configKey, item.configValue || ''])
      )
      for (const key of Object.keys(configKeys) as (keyof CompanyConfig)[]) {
        company[key] = configMap.get(configKeys[key]) || ''
      }
    } catch (error) {
      console.error('加载公司配置失败:', error)
    } finally {
      loading.value = false
    }
  }

  onMounted(loadCompanyConfig)

  return { company, loading, loadCompanyConfig }
}
