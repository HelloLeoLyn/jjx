import { describe, expect, it } from 'vitest'
import {
  appendRegenerateTrace,
  buildSizePreview,
  isRollCategory,
  readOriginalRefs,
} from '../materialRegenerate'
import { composeMaterialName } from '../materialMasterFields'

describe('readOriginalRefs', () => {
  it('优先取 remark 的【原名称】【原规格】', () => {
    const remark = '【原名称】PC膜旧名\n【原规格】0.38mm 幼砂'
    expect(readOriginalRefs(remark, 'PC膜新名', '规格X')).toEqual({
      name: 'PC膜旧名',
      specification: '0.38mm 幼砂',
    })
  })

  it('remark 没有【原名称】【原规格】时用当前值', () => {
    expect(readOriginalRefs('普通备注', 'PC膜11008', '透明')).toEqual({
      name: 'PC膜11008',
      specification: '透明',
    })
  })

  it('都为空时显示占位符', () => {
    expect(readOriginalRefs(undefined, '', '')).toEqual({ name: '-', specification: '-' })
  })
})

describe('appendRegenerateTrace', () => {
  const at = new Date(2026, 9, 23, 18, 45, 0)

  it('保留原备注并追加时间与操作人', () => {
    expect(appendRegenerateTrace('【原名称】旧名', '大黄', at)).toBe(
      '【原名称】旧名\n【重生成】2026-10-23 18:45 大黄'
    )
  })

  it('原备注为空时只写留痕行', () => {
    expect(appendRegenerateTrace('', 'admin', at)).toBe('【重生成】2026-10-23 18:45 admin')
  })

  it('操作人为空时回落 system', () => {
    expect(appendRegenerateTrace(undefined, '  ', at)).toBe('【重生成】2026-10-23 18:45 system')
  })
})

describe('buildSizePreview', () => {
  it('单值按 mm', () => {
    expect(buildSizePreview('300', 'RM-FILM')).toBe('宽 300 mm')
  })

  it('片材两段=宽×长', () => {
    expect(buildSizePreview('300×305', 'RM-FILM')).toBe('宽 300 mm × 长 305 mm')
  })

  it('卷料两段=宽×厚', () => {
    expect(buildSizePreview('300×0.1', 'RM-ADHESIVE')).toBe('宽 300 mm × 厚 0.1 mm')
  })

  it('厘米/米换算成 mm', () => {
    // 每段单位独立：1.0 无单位 → 按 mm
    expect(buildSizePreview('0.3m×1.0', 'RM-FILM')).toBe('宽 300 mm × 长 1 mm')
    expect(buildSizePreview('0.3m×1m', 'RM-FILM')).toBe('宽 300 mm × 长 1000 mm')
    expect(buildSizePreview('30cm×40cm', 'RM-FILM')).toBe('宽 300 mm × 长 400 mm')
  })

  it('三段=宽×长×厚', () => {
    expect(buildSizePreview('300×305×1.5', 'RM-FILM')).toBe(
      '宽 300 mm × 长 305 mm × 厚 1.5 mm'
    )
  })

  it('格式错误与超限给出提示', () => {
    expect(buildSizePreview('abc', 'RM-FILM')).toBe('尺寸格式错误：abc')
    expect(buildSizePreview('300×305×1×2', 'RM-FILM')).toBe('尺寸最多三维：宽×长/厚×厚')
    expect(buildSizePreview('0', 'RM-FILM')).toBe('尺寸必须大于0')
  })

  it('空值返回空串', () => {
    expect(buildSizePreview('  ', 'RM-FILM')).toBe('')
  })

  it('卷料品类判定与后端一致', () => {
    expect(isRollCategory('RM-ADHESIVE')).toBe(true)
    expect(isRollCategory('AUX-ADHESIVE')).toBe(true)
    expect(isRollCategory('RM-PAPER')).toBe(true)
    expect(isRollCategory('RM-FILM')).toBe(false)
  })
})

describe('名称口径复用', () => {
  it('与 materialMasterFields 的组合规则一致', () => {
    expect(
      composeMaterialName({ materialGrade: 'PET膜', model: 'PET125', color: '', brand: '' })
    ).toBe('PET膜PET125')
    expect(composeMaterialName({ materialGrade: '胶带', model: '5604', brand: '3M' })).toBe(
      '胶带5604（3M）'
    )
  })
})
