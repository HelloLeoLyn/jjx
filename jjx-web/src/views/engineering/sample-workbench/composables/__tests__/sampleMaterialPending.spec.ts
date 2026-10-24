import { describe, expect, it } from 'vitest'
import { serializeMaterial, validateMaterials } from '../sampleMaterials'

describe('sampleMaterials 未确认阻断（dev-20261024-019 wave B）', () => {
  const base = { name: 'PET', baseQty: 1, moduleQty: 1, split: '' }

  it('有未确认搜索文字时，保存校验返回错误', () => {
    const msg = validateMaterials([{ ...base, __matDraftPending: true, __matDraftText: 'PE' }])
    expect(msg).toContain('搜索尚未确认')
  })

  it('正常行不受影响', () => {
    expect(validateMaterials([{ ...base }])).toBeNull()
  })

  it('序列化时剥掉未确认标记，不写入 materials JSON', () => {
    const out = serializeMaterial({ ...base, __matDraftPending: true, __matDraftText: 'PE' })
    expect(out.__matDraftPending).toBeUndefined()
    expect(out.__matDraftText).toBeUndefined()
  })
})
