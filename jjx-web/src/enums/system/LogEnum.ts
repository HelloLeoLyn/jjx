// src/enums/system/LogEnum.ts
import { createEnum } from '../base'

/**
 * 业务类型枚举（与后端 @Log#businessType 枚举对齐：BusinessType.java）
 * 操作日志/流水标题统一按此映射，不做 URL 语义判断
 */
export const BusinessTypeEnum = createEnum({
  items: [
    { value: 1, label: '新增', tagProps: { type: 'primary' } },
    { value: 2, label: '修改', tagProps: { type: 'warning' } },
    { value: 3, label: '删除', tagProps: { type: 'danger' } },
    { value: 4, label: '导出', tagProps: { type: 'success' } },
    { value: 5, label: '导入', tagProps: { type: 'success' } },
    { value: 6, label: '审批', tagProps: { type: 'warning' } },
    { value: 7, label: '登录', tagProps: { type: 'info' } },
    { value: 8, label: '登出', tagProps: { type: 'info' } },
    { value: 9, label: '其他', tagProps: { type: 'info' } },
    { value: 10, label: '重置密码', tagProps: { type: 'warning' } },
    { value: 11, label: '转换', tagProps: { type: 'primary' } },
  ],
  defaultTag: { type: 'info' },
})

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
