import { describe, it, expect } from 'vitest'
import {
  CONTACT_PHONE_CHARSET_MESSAGE,
  CONTACT_PHONE_LENGTH_MESSAGE,
  CONTACT_PHONE_MAX_LENGTH,
  CONTACT_PHONE_REGEX_SOURCE,
  isValidContactPhone,
  validateContactPhone,
} from '../contactPhone'

/**
 * dev-20261018-026：联系电话统一口径（可选、≤50 字符、字符白名单）。
 * 与后端 ContactPhoneValidationTest 共用同一批验收样例，防止前后端再次漂移。
 */
const ACCEPTED = [
  '',
  '+86 13800000000',
  '+852 9123 4567',
  '0755-12345678',
  '12345678-123',
  '(021) 5555 1234 #808',
  '13800138000',
  '1'.repeat(50),
]

const REJECTED_LENGTH = '1'.repeat(51)
const REJECTED_CHARS = ['13800000000转123', 'phone', '138\u0001000000', '13800000000；']

function validate(value: string): Error | null {
  let error: Error | null = null
  validateContactPhone(undefined, value, (err?: Error) => {
    error = err ?? null
  })
  return error
}

describe('contactPhone 统一规则', () => {
  it('正则字符串与后端 ContactPhoneRule.REGEX 一致（含 - 置于字符类末尾）', () => {
    expect(CONTACT_PHONE_REGEX_SOURCE).toBe('^[0-9+()# -]*$')
    expect(CONTACT_PHONE_MAX_LENGTH).toBe(50)
  })

  it('卡面验收样例均可通过（含留空、国际号、空格、分机、50 字）', () => {
    for (const sample of ACCEPTED) {
      expect(isValidContactPhone(sample), sample).toBe(true)
      expect(validate(sample), sample).toBeNull()
    }
  })

  it('null / undefined 视为留空，不拦截', () => {
    expect(isValidContactPhone(null)).toBe(true)
    expect(isValidContactPhone(undefined)).toBe(true)
    expect(validate(undefined as unknown as string)).toBeNull()
  })

  it('51 字符提示长度超限', () => {
    expect(isValidContactPhone(REJECTED_LENGTH)).toBe(false)
    expect(validate(REJECTED_LENGTH)?.message).toBe(CONTACT_PHONE_LENGTH_MESSAGE)
  })

  it('中文、字母、控制符、中文标点提示字符不合法', () => {
    for (const sample of REJECTED_CHARS) {
      expect(isValidContactPhone(sample), sample).toBe(false)
      expect(validate(sample)?.message, sample).toBe(CONTACT_PHONE_CHARSET_MESSAGE)
    }
  })

  it('不再按大陆手机号/座机格式拦截（如 +1 号码、纯分机）', () => {
    for (const sample of ['+1 415 555 0100', '001-8876#']) {
      expect(isValidContactPhone(sample), sample).toBe(true)
    }
  })
})
