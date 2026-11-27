import { describe, expect, it } from 'vitest'
import { ArtworkVersionStatusEnum, FilmCopyStatusEnum } from '@/enums/product/film'
import { FilmPrototypeRequestProgressEnum as Progress } from '@/enums/product/filmPrototype'
import { addRequest, createPrototypeState, receiveCopies, receivedCount, requestProgress } from '../prototypeData'

describe('菲林原型的主数据与申请回片关系', () => {
  it('无版本、无菲林片的菲林仍有完整主档，申请不会自动成为实物', () => {
    const state = createPrototypeState()
    expect(state.films.find(row => row.no === 'PW-005')?.versions).toHaveLength(0)
    const before = state.copies.length
    const request = addRequest(state, { filmId: 1, versionId: 12, quantity: 2, material: 'PET', thickness: 0.125, size: 'A4', dueDate: '2026-12-01', note: '' })
    expect(state.copies).toHaveLength(before)
    expect(receivedCount(state, request)).toBe(0)
    expect(requestProgress(state, request)).toBe(Progress.PENDING)
  })
  it('同一申请分两次回片，原申请量不变，每张实物关联正确菲林与版本', () => {
    const state = createPrototypeState()
    const request = addRequest(state, { filmId: 1, versionId: 12, quantity: 2, material: 'PET', thickness: 0.125, size: 'A4', dueDate: '2026-12-01', note: '' })
    receiveCopies(state, request.id, [{ no: 'FL-003', location: 'A架2格' }])
    expect(request.quantity).toBe(2)
    expect(receivedCount(state, request)).toBe(1)
    expect(requestProgress(state, request)).toBe(Progress.PARTIAL)
    receiveCopies(state, request.id, [{ no: 'FL-004', location: 'A架3格' }])
    expect(requestProgress(state, request)).toBe(Progress.RECEIVED)
    expect(state.copies.filter(row => row.requestId === request.id)).toEqual([
      expect.objectContaining({ filmId: 1, versionId: 12, no: 'FL-003', status: FilmCopyStatusEnum.AVAILABLE.value }),
      expect.objectContaining({ filmId: 1, versionId: 12, no: 'FL-004', status: FilmCopyStatusEnum.AVAILABLE.value }),
    ])
    expect(state.films[0].versions[0].status).toBe(ArtworkVersionStatusEnum.RELEASED.value)
  })
  it('空编号、缺位置、批内重复和跨记录重复均不产生半批实物', () => {
    for (const rows of [
      [{ no: '', location: 'A架' }], [{ no: 'NEW', location: '' }],
      [{ no: 'NEW', location: 'A架' }, { no: 'new ', location: 'B架' }],
      [{ no: 'FL-001', location: 'B架' }],
    ]) {
      const state = createPrototypeState()
      const request = addRequest(state, { filmId: 1, versionId: 12, quantity: 2, material: 'PET', thickness: 0.125, size: 'A4', dueDate: '2026-12-01', note: '' })
      const before = JSON.stringify(state.copies)
      expect(() => receiveCopies(state, request.id, rows)).toThrow()
      expect(JSON.stringify(state.copies)).toBe(before)
    }
  })
  it('不能超收，也不能把其他菲林或未发布版本用于申请', () => {
    const state = createPrototypeState()
    expect(() => receiveCopies(state, 1, [{ no: 'NEW1', location: 'A架' }, { no: 'NEW2', location: 'B架' }])).toThrow('不能超过')
    for (const [filmId, versionId] of [[1, 21], [1, 11], [4, 41], [5, 12]]) {
      expect(() => addRequest(state, { filmId, versionId, quantity: 1, material: 'PET', thickness: 0.125, size: 'A4', dueDate: '2026-12-01', note: '' })).toThrow('已发布')
    }
  })
  it('重新打开原型恢复示例，临时操作不污染初始数据', () => {
    const first = createPrototypeState()
    receiveCopies(first, 1, [{ no: 'NEW', location: 'A架' }])
    expect(requestProgress(first, first.requests[0])).toBe(Progress.RECEIVED)
    const refreshed = createPrototypeState()
    expect(refreshed.copies).toHaveLength(2)
    expect(requestProgress(refreshed, refreshed.requests[0])).toBe(Progress.PARTIAL)
  })
})
