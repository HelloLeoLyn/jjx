// src/enums/system/LogEnum.ts
import { createEnum } from '../base'

/**
 * 操作类型枚举
 */
export const OperTypeEnum = createEnum({
  items: [
    { value: 'insert', label: '新增', tagProps: { type: 'primary' } },
    { value: 'update', label: '修改', tagProps: { type: 'warning' } },
    { value: 'delete', label: '删除', tagProps: { type: 'danger' } },
    { value: 'export', label: '导出', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 操作结果枚举
 */
export const OperResultEnum = createEnum({
  items: [
    { value: 'success', label: '成功', tagProps: { type: 'success' } },
    { value: 'fail', label: '失败', tagProps: { type: 'danger' } },
    { value: 'error', label: '异常', tagProps: { type: 'danger' } },
    { value: 'timeout', label: '超时', tagProps: { type: 'warning' } },
    { value: 'unknown', label: '未知', tagProps: { type: 'info' } },
    { value: 'cancel', label: '取消', tagProps: { type: 'info' } },
    { value: 'reject', label: '驳回', tagProps: { type: 'danger' } },
    { value: 'approve', label: '通过', tagProps: { type: 'success' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 登录结果枚举
 */
export const LoginResultEnum = createEnum({
  items: [
    { value: 'success', label: '成功', tagProps: { type: 'success' } },
    { value: 'fail', label: '失败', tagProps: { type: 'danger' } },
  ],
  defaultTag: { type: 'info' },
})

/**
 * 日志相关枚举统一导出
 */
export const LogEnum = {
  operType: OperTypeEnum,
  operResult: OperResultEnum,
  loginResult: LoginResultEnum,
}
