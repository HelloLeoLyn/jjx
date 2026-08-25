import type { TaskCandidate, TaskTreeRow } from '@/types/production/task'
import { FLOW_ACTION_LABEL, STATUS_TAG, STATUS_TEXT, type TagType } from './taskConstants'

export function fmtQty(v?: number | string | null): string {
  if (v === null || v === undefined || v === '') return '0'
  return String(Number(v))
}

export function fmtTime(t?: string | null): string {
  if (!t) return '-'
  return t.replace('T', ' ').slice(0, 16)
}

export function completed(row: TaskTreeRow | null): number {
  return Number(row?.completedQuantity || 0)
}

export function pending(row: TaskTreeRow): number {
  return Number(row.pendingQuantity || 0)
}

export function remaining(row: TaskTreeRow): number {
  return Number(row.remainingQuantity || 0)
}

export function orderProcessLabel(row: TaskTreeRow): string {
  const order = row.orderNo || '-'
  const process = row.processName || ''
  return process ? `${order} · ${process}` : order
}

export function statusLabel(row: TaskTreeRow): string {
  if (row.statusLabel) return row.statusLabel
  return STATUS_TEXT[row.status || ''] || row.status || '-'
}

export function statusTag(status?: string): TagType {
  return STATUS_TAG[status || ''] || 'info'
}

export function candidateName(c: TaskCandidate): string {
  return c.nickName || c.userName
}

export function candidateRoles(c: TaskCandidate): string {
  return c.roleName || c.roleKey || '-'
}

export function flowActionLabel(action: string): string {
  return FLOW_ACTION_LABEL[action] || action
}

const FLOW_TAG: Record<string, TagType> = {
  ASSIGN: 'primary',
  RECALL: 'info',
  RETURN: 'warning',
  COMPLETE: 'success',
}

export function flowTagType(action: string): TagType {
  return FLOW_TAG[action] || 'info'
}
