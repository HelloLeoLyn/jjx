import { describe, expect, it } from 'vitest'
import {
  canActivate,
  canApprove,
  canCancel,
  canSubmit,
  statusLabel,
  statusType,
} from '../status'

describe('BOM变更单状态与按钮可见性（dev-20261112-009）', () => {
  it('状态文案覆盖五种状态', () => {
    expect(statusLabel('DRAFT')).toBe('草稿')
    expect(statusLabel('PENDING')).toBe('待审批')
    expect(statusLabel('APPROVED')).toBe('已批准')
    expect(statusLabel('EFFECTIVE')).toBe('已生效')
    expect(statusLabel('CANCELLED')).toBe('已取消')
    expect(statusLabel('')).toBe('—')
  })

  it('动作按钮只在对应状态下出现', () => {
    expect(canSubmit('DRAFT')).toBe(true)
    expect(canSubmit('PENDING')).toBe(false)
    expect(canApprove('PENDING')).toBe(true)
    expect(canApprove('APPROVED')).toBe(false)
    expect(canActivate('APPROVED')).toBe(true)
    expect(canActivate('EFFECTIVE')).toBe(false)
    expect(canCancel('DRAFT')).toBe(true)
    expect(canCancel('CANCELLED')).toBe(false)
    expect(canCancel('EFFECTIVE')).toBe(false)
  })

  it('状态标签类型：生效成功、待审/已批准警告、取消信息', () => {
    expect(statusType('EFFECTIVE')).toBe('success')
    expect(statusType('PENDING')).toBe('warning')
    expect(statusType('APPROVED')).toBe('warning')
    expect(statusType('CANCELLED')).toBe('info')
    expect(statusType('DRAFT')).toBeUndefined()
  })
})
