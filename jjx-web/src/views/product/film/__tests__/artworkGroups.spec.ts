import { describe, expect, it } from 'vitest'
import { groupArtworkRows } from '../artworkGroups'

const rows = [
  {
    artworkId: 10,
    filmCode: 'PW-0001',
    filmName: '面板图案',
    productId: 1,
    productCode: 'P001',
    productName: '样品一',
    versionId: 101,
    version: 'v1',
    versionStatus: 'DISABLED',
    isCurrent: 0,
  },
  {
    artworkId: 10,
    filmCode: 'PW-0001',
    filmName: '面板图案',
    productId: 1,
    productCode: 'P001',
    productName: '样品一',
    versionId: 102,
    version: 'v2',
    versionStatus: 'RELEASED',
    isCurrent: 1,
  },
  {
    artworkId: 11,
    filmCode: 'PW-0002',
    filmName: '上线图案',
    productId: 1,
    versionId: 201,
    version: 'v1',
    versionStatus: 'DRAFT',
    isCurrent: 0,
  },
]

describe('groupArtworkRows', () => {
  it('按图案归并版本，并标记当前生效版本', () => {
    const groups = groupArtworkRows(rows)
    expect(groups).toHaveLength(2)
    const first = groups.find((g) => g.artworkId === 10)!
    expect(first.versions.map((v) => v.versionNo)).toEqual(['v1', 'v2'])
    expect(first.currentVersionId).toBe(102)
    expect(first.currentVersionNo).toBe('v2')
    expect(first.latestStatus).toBe('RELEASED')
  })

  it('无生效版本时 currentVersion 为空，默认类别为面板', () => {
    const groups = groupArtworkRows(rows)
    const second = groups.find((g) => g.artworkId === 11)!
    expect(second.currentVersionId).toBeUndefined()
    expect(second.family).toBe('面板')
    expect(second.latestStatus).toBe('DRAFT')
  })

  it('忽略缺少 artworkId 的脏行', () => {
    const groups = groupArtworkRows([{ artworkId: null, versionId: 1 }])
    expect(groups).toEqual([])
  })
})
