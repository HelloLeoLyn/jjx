<template>
  <div
    class="doc-header"
    :class="[`doc-header--${variant}`, `doc-header--size-${size}`]"
    :aria-busy="loading"
  >
    <div class="company-head-row">
      <img
        v-if="variant !== 'compact' && company.logo"
        :src="company.logo"
        class="company-logo"
        alt="logo"
      />
      <div class="company-head-text">
        <div class="company-name">{{ company.name }}</div>
        <div v-if="variant !== 'compact' && companyContact" class="company-contact">
          {{ companyContact }}
        </div>
      </div>
    </div>
    <div v-if="variant !== 'compact' && companyExtra.length" class="company-extra">
      <span v-for="field in companyExtra" :key="field.key"
        >{{ field.label }}：{{ field.value }}</span
      >
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useCompanyConfig } from '@/composables/useCompanyConfig'

type HeaderVariant = 'left' | 'center' | 'compact'
type HeaderSize = 'normal' | 'compact'
type CompanyField = 'taxNo' | 'legal' | 'bank' | 'account' | 'website' | 'contact'

const props = withDefaults(
  defineProps<{
    variant?: HeaderVariant
    showFields?: CompanyField[]
    size?: HeaderSize
  }>(),
  {
    variant: 'left',
    showFields: () => [],
    size: 'normal',
  }
)

const { company, loading } = useCompanyConfig()
const showsAllFields = computed(() => props.showFields.length === 0)
const shows = (field: CompanyField) => showsAllFields.value || props.showFields.includes(field)

const companyContact = computed(() => {
  const parts: string[] = []
  if (shows('contact')) {
    if (company.address) parts.push(`地址：${company.address}`)
    if (company.phone) parts.push(`电话：${company.phone}`)
    if (company.email) parts.push(`邮箱：${company.email}`)
  }
  if (shows('website') && company.website) parts.push(`官网：${company.website}`)
  return parts.join(' ｜ ')
})

const companyExtra = computed(() =>
  [
    { key: 'taxNo' as const, label: '税号', value: company.taxNo },
    { key: 'legal' as const, label: '法人', value: company.legal },
    { key: 'bank' as const, label: '开户行', value: company.bank },
    { key: 'account' as const, label: '账号', value: company.account },
  ].filter((field) => shows(field.key) && field.value)
)
</script>

<style scoped>
.doc-header {
  margin-bottom: 6px;
}

.company-head-row {
  display: flex;
  align-items: center;
  gap: 12px;
}

.company-logo {
  max-height: 44px;
  max-width: 120px;
  object-fit: contain;
}

.company-name {
  color: #2b5aa7;
  font-size: 20px;
  font-weight: 700;
  letter-spacing: 2px;
}

.company-contact {
  margin-top: 2px;
  color: #888;
  font-size: 9px;
}

.company-extra {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin-top: 3px;
  color: #777;
  font-size: 9px;
}

.doc-header--left,
.doc-header--left .company-head-text {
  text-align: left;
}

.doc-header--left .company-head-row,
.doc-header--left .company-extra {
  justify-content: flex-start;
}

.doc-header--center,
.doc-header--center .company-head-text {
  text-align: center;
}

.doc-header--center .company-head-row,
.doc-header--center .company-extra {
  justify-content: center;
}

.doc-header--compact .company-head-row {
  justify-content: flex-start;
}

.doc-header--size-compact .company-head-row {
  gap: 8px;
}

.doc-header--size-compact .company-logo {
  max-height: 32px;
  max-width: 90px;
}

.doc-header--size-compact .company-name {
  font-size: 16px;
}
</style>
