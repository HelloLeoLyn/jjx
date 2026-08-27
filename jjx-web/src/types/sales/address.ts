/** 国际地址结构 */
export interface InternationalAddress {
  /** 国家 */
  country: string
  /** 省份/州 */
  province: string
  /** 城市 */
  city: string
  /** 街道/详细地址 */
  street: string
  /** 邮政编码 */
  zipCode: string
}

/** 国家选项 */
export interface CountryOption {
  value: string
  label: string
  labelEn: string
}

/** 常用国家列表 */
export const COMMON_COUNTRIES: CountryOption[] = [
  { value: 'CN', label: '中国', labelEn: 'China' },
  { value: 'US', label: '美国', labelEn: 'United States' },
  { value: 'JP', label: '日本', labelEn: 'Japan' },
  { value: 'KR', label: '韩国', labelEn: 'South Korea' },
  { value: 'GB', label: '英国', labelEn: 'United Kingdom' },
  { value: 'DE', label: '德国', labelEn: 'Germany' },
  { value: 'FR', label: '法国', labelEn: 'France' },
  { value: 'CA', label: '加拿大', labelEn: 'Canada' },
  { value: 'AU', label: '澳大利亚', labelEn: 'Australia' },
  { value: 'SG', label: '新加坡', labelEn: 'Singapore' },
  { value: 'HK', label: '中国香港', labelEn: 'Hong Kong' },
  { value: 'TW', label: '中国台湾', labelEn: 'Taiwan' },
  { value: 'MO', label: '中国澳门', labelEn: 'Macau' },
  { value: 'IT', label: '意大利', labelEn: 'Italy' },
  { value: 'ES', label: '西班牙', labelEn: 'Spain' },
  { value: 'NL', label: '荷兰', labelEn: 'Netherlands' },
  { value: 'SE', label: '瑞典', labelEn: 'Sweden' },
  { value: 'CH', label: '瑞士', labelEn: 'Switzerland' },
  { value: 'TH', label: '泰国', labelEn: 'Thailand' },
  { value: 'VN', label: '越南', labelEn: 'Vietnam' },
  { value: 'MY', label: '马来西亚', labelEn: 'Malaysia' },
  { value: 'IN', label: '印度', labelEn: 'India' },
  { value: 'RU', label: '俄罗斯', labelEn: 'Russia' },
  { value: 'BR', label: '巴西', labelEn: 'Brazil' },
  { value: 'AE', label: '阿联酋', labelEn: 'United Arab Emirates' },
]

/** 将 InternationalAddress 序列化为 JSON 字符串 */
export function serializeAddress(address: InternationalAddress): string {
  return JSON.stringify(address)
}

/** 将 JSON 字符串反序列化为 InternationalAddress */
export function deserializeAddress(jsonStr: string): InternationalAddress {
  try {
    const parsed = JSON.parse(jsonStr)
    return {
      country: parsed.country || '',
      province: parsed.province || '',
      city: parsed.city || '',
      street: parsed.street || '',
      zipCode: parsed.zipCode || '',
    }
  } catch {
    // 如果是旧数据（纯文本），尝试智能解析
    return {
      country: '',
      province: '',
      city: '',
      street: jsonStr,
      zipCode: '',
    }
  }
}

/** 获取地址的显示文本 */
export function getAddressDisplayText(address: InternationalAddress): string {
  const parts = [
    address.country,
    address.province,
    address.city,
    address.street,
  ].filter(Boolean)
  const zip = address.zipCode ? ` ${address.zipCode}` : ''
  return parts.join(' ') + zip
}
