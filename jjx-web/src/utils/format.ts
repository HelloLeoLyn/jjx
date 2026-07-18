/**
 * 时间格式化工具
 */

/**
 * 格式化时间
 * @param time 时间戳或日期字符串
 * @param pattern 格式模式，默认 'yyyy-MM-dd HH:mm:ss'
 * @returns 格式化后的时间字符串
 */
export function parseTime(
  time: string | number | Date | undefined | null,
  pattern?: string
): string {
  if (!time) {
    return ''
  }

  const format = pattern || 'yyyy-MM-dd HH:mm:ss'
  let date: Date

  if (typeof time === 'object') {
    date = time
  } else {
    if (typeof time === 'string' && /^\d+$/.test(time)) {
      time = parseInt(time)
    }
    if (typeof time === 'number' && time.toString().length === 10) {
      time = time * 1000
    }
    date = new Date(time)
  }

  const formatObj: Record<string, number> = {
    y: date.getFullYear(),
    M: date.getMonth() + 1,
    d: date.getDate(),
    H: date.getHours(),
    m: date.getMinutes(),
    s: date.getSeconds(),
    a: date.getDay(),
  }
  const timeStr = format.replace(/(y|M|d|H|m|s|a)+/g, (result, key) => {
    let value = formatObj[key]
    if (key === 'a') {
      return ['日', '一', '二', '三', '四', '五', '六'][value]
    }
    if (result.length > 0 && value < 10) {
      return '0' + value.toString()
    }
    return value.toString() || '0'
  })

  return timeStr
}

export function parseDate(time: string | number | Date, pattern?: string): string {
  return parseTime(time, pattern || 'yyyy-MM-dd')
}

// Define formatNumber locally
export const formatNumber = (num: number) => {
  return num.toLocaleString()
}
/**
 * 格式化货币
 * @param value 金额
 * @returns 格式化后的货币字符串
 */
export function formatCurrency(value: number | undefined): string {
  if (value === undefined || value === null) return '0.00'
  return value.toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })
}

/**
 * 下载文件
 * @param data 文件数据
 * @param filename 文件名
 */
export function download(data: any, filename?: string): void {
  if (!data) return

  const url = window.URL.createObjectURL(new Blob([data]))
  const link = document.createElement('a')
  link.href = url
  link.setAttribute('download', filename || 'file.xlsx')
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  window.URL.revokeObjectURL(url)
}

/**
 * 获取文件大小格式化
 * @param bytes 字节数
 * @returns 格式化后的文件大小
 */
export function formatFileSize(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

/**
 * 格式化百分比
 * @param value 百分比值（0-1或0-100）
 * @param isDecimal 是否为小数形式（0-1）
 * @returns 格式化后的百分比字符串
 */
export function formatPercent(value: number, isDecimal: boolean = false): string {
  const percentValue = isDecimal ? value * 100 : value
  return percentValue.toFixed(2) + '%'
}

/**
 * 格式化手机号
 * @param phone 手机号
 * @returns 格式化后的手机号
 */
export function formatPhone(phone: string): string {
  if (!phone || phone.length !== 11) return phone
  return phone.replace(/(\d{3})(\d{4})(\d{4})/, '$1****$3')
}

/**
 * 格式化身份证号
 * @param idCard 身份证号
 * @returns 格式化后的身份证号
 */
export function formatIdCard(idCard: string): string {
  if (!idCard || idCard.length < 15) return idCard
  if (idCard.length === 15) {
    return idCard.replace(/(\d{6})(\d{6})(\d{3})/, '$1******$3')
  }
  return idCard.replace(/(\d{6})(\d{8})(\d{4})/, '$1********$3')
}

/**
 * 格式化银行卡号
 * @param bankCard 银行卡号
 * @returns 格式化后的银行卡号
 */
export function formatBankCard(bankCard: string): string {
  if (!bankCard) return ''
  const cleaned = bankCard.replace(/\s/g, '')
  const groups = cleaned.match(/.{1,4}/g)
  return groups ? groups.join(' ') : cleaned
}
