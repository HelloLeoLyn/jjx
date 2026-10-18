/**
 * 联系电话统一口径（dev-20261018-026，覆盖 dev-20261014-012 的格式校验部分）。
 *
 * 规则：可选、最长 50 个字符、字符白名单（数字 0-9、+、-、空格、(、)、#），
 * 不做大陆手机号/座机格式判断，接受国际号码、分机号等业务写法。
 * 后端同源规则：jjx-server .../com/jjx/common/validation/ContactPhoneRule.java
 * —— 两处 REGEX 字符串必须完全一致；客户表单引用本文件，禁止在页面里另写正则。
 */
export const CONTACT_PHONE_MAX_LENGTH = 50

/** 与后端 ContactPhoneRule.REGEX 同一字符串（字符类里 - 置于末尾表示字面量） */
export const CONTACT_PHONE_REGEX_SOURCE = '^[0-9+()# -]*$'

const CONTACT_PHONE_REGEX = new RegExp(CONTACT_PHONE_REGEX_SOURCE)

export const CONTACT_PHONE_LENGTH_MESSAGE = '联系电话长度不能超过50个字符'

export const CONTACT_PHONE_CHARSET_MESSAGE = '联系电话只能包含数字、+、-、空格、括号和#'

/** 纯函数校验：空值合法（可选字段），只判长度与字符白名单 */
export function isValidContactPhone(value: string | null | undefined): boolean {
  if (!value) return true
  return value.length <= CONTACT_PHONE_MAX_LENGTH && CONTACT_PHONE_REGEX.test(value)
}

/** Element Plus / async-validator 自定义校验器（为空不拦截，因此可同时用于新增与编辑） */
export function validateContactPhone(
  _rule: unknown,
  value: string | null | undefined,
  callback: (error?: Error) => void
): void {
  const phone = value ?? ''
  if (!phone) {
    callback()
    return
  }
  if (phone.length > CONTACT_PHONE_MAX_LENGTH) {
    callback(new Error(CONTACT_PHONE_LENGTH_MESSAGE))
    return
  }
  if (!CONTACT_PHONE_REGEX.test(phone)) {
    callback(new Error(CONTACT_PHONE_CHARSET_MESSAGE))
    return
  }
  callback()
}
