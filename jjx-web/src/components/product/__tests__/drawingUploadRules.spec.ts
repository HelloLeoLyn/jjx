import { describe, expect, it } from 'vitest'
import { newDrawingIdentity, nextDrawingVersion } from '../drawingUploadRules'

describe('工程图集与结构图上传共用编号名称和版本规则', () => {
  it.each([
    ['STRUCTURE_DRAWING', 'ST', '结构图'],
    ['OUTLINE_DRAWING', 'OUT', '外形尺寸图'],
    ['PANEL_DRAWING', 'PNL', '面板图'],
    ['CIRCUIT_DRAWING', 'CIR', '线路图'],
    ['ASSEMBLY_DRAWING', 'ASM', '组装图'],
    ['PACKAGING_DRAWING', 'PKG', '包装图'],
    ['PRINT_GUIDE', 'PRT', '印刷指导图'],
    ['CONFIRM_DRAWING', 'CFM', '确认图'],
    ['FILM', 'FLM', '菲林'],
    ['MOLD_DRAWING', 'MLD', '模具'],
    ['DIE_PARAMETER_DRAWING', 'DIE', '刀模参数图'],
    ['ENGINEERING_DRAWING', 'ENG', '工程图'],
    ['DIE_DRAWING', 'DWD', '刀模图'],
  ])('%s 自动生成产品编码+类型代码，名称使用产品名+类型标签', (type, code, label) => {
    expect(newDrawingIdentity('P42', '产品', type, [])).toEqual({
      drawingNo: `P42-${code}`,
      drawingName: `产品${label}`,
    })
  })
  it('跨版本/原稿打印件只计同一张图，第二张起递增序号，历史不修改', () => {
    const existing = [
      { drawingNo: 'P42-ST', documentType: 'STRUCTURE_DRAWING', version: 'A' },
      { drawingNo: 'P42-ST', documentType: 'STRUCTURE_DRAWING', version: 'B' },
    ]
    expect(newDrawingIdentity('P42', '', 'STRUCTURE_DRAWING', existing)).toEqual({
      drawingNo: 'P42-ST-2',
      drawingName: 'P42结构图-2',
    })
    expect(existing[0].drawingNo).toBe('P42-ST')
    expect(
      newDrawingIdentity('P42', '产品', 'STRUCTURE_DRAWING', [
        ...existing,
        { drawingNo: 'P42-ST-9', documentType: 'STRUCTURE_DRAWING', version: 'A' },
      ]).drawingNo
    ).toBe('P42-ST-10')
  })
  it('历史手填身份保留，新图按同类型实际张数续号', () => {
    const existing = [{ drawingNo: '旧结构图', category: '结构图', version: 'A' }]
    expect(newDrawingIdentity('P42', '产品', 'STRUCTURE_DRAWING', existing).drawingNo).toBe(
      'P42-ST-2'
    )
    expect(existing[0].drawingNo).toBe('旧结构图')
  })
  it.each([
    [[], 'A'],
    [['A', 'C', 'B'], 'D'],
    [['Z'], 'AA'],
    [['AZ', 'ZZ'], 'AAA'],
    [['V1.0'], null],
    [[''], null],
  ])('版本列表%j递增为%s', (versions, expected) => {
    expect(nextDrawingVersion(versions as string[])).toBe(expected)
  })
})
