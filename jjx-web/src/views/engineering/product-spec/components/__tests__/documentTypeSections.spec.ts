import { describe, expect, it } from 'vitest'
import { defaultDocumentFiles } from '../docset'
import { sectionForDocumentType, snapshotFileToDocFile } from '../versionDocset'
import type { DocFile } from '../versionDocset'
import { duplicateFingerprints, fileFingerprint } from '@/components/product/productFileCategories'

function doc(overrides: Partial<DocFile>): DocFile {
  return {
    key: '1',
    id: 1,
    name: 'drawing.pdf',
    category: '',
    section: 'other',
    kind: 'pdf',
    productFile: true,
    ...overrides,
  }
}

describe('二级资料类型分栏映射（dev-20261019-018）', () => {
  it('客供类 / 作业规范 / 印刷规范 / 分色检查表各有明确栏目', () => {
    expect(sectionForDocumentType('CUSTOMER_DRAWING')).toBe('customer')
    expect(sectionForDocumentType('CUSTOMER_APPROVAL')).toBe('customer')
    expect(sectionForDocumentType('PRODUCT_SPECIFICATION')).toBe('spec')
    expect(sectionForDocumentType('WORK_INSTRUCTION')).toBe('spec')
    expect(sectionForDocumentType('PRINT_GUIDE')).toBe('print')
    expect(sectionForDocumentType('COLOR_CHECK')).toBe('color')
  })

  it('工程图类二级类型归工程图集', () => {
    const engineering = [
      'ENGINEERING_DRAWING', 'STRUCTURE_DRAWING', 'OUTLINE_DRAWING', 'PANEL_DRAWING', 'CIRCUIT_DRAWING',
      'ASSEMBLY_DRAWING', 'PACKAGING_DRAWING', 'MOLD_DRAWING', 'DIE_DRAWING', 'DIE_PARAMETER_DRAWING',
      'CONFIRM_DRAWING', 'FILM',
    ]
    for (const type of engineering) expect(sectionForDocumentType(type), type).toBe('atlas')
  })

  it('未知类型与缺失类型都归其他资料，不默认落工程图集', () => {
    expect(sectionForDocumentType('BOM')).toBe('other')
    expect(sectionForDocumentType('SOMETHING_NEW')).toBe('other')
    expect(sectionForDocumentType('')).toBe('other')
    expect(sectionForDocumentType(undefined, '结构图')).toBe('other')
    expect(sectionForDocumentType(null, '产品图集')).toBe('other')
  })

  it('旧数据仅对非图纸栏目回退到原类别，图纸无类型不落图集', () => {
    expect(sectionForDocumentType('', '客供资料')).toBe('customer')
    expect(sectionForDocumentType('', '分色检查表')).toBe('color')
    expect(sectionForDocumentType('', '结构图')).toBe('other')
  })

  it('旧快照无 documentType 时沿用已固化的 section', () => {
    const legacy = snapshotFileToDocFile({ attachmentId: 10, fileName: 'old.jpg', section: 'atlas', category: '结构图' })
    expect(legacy.section).toBe('atlas')
    expect(legacy.documentType).toBeUndefined()
    const typed = snapshotFileToDocFile({ attachmentId: 11, fileName: 'new.jpg', documentType: 'PANEL_DRAWING' })
    expect(typed.section).toBe('atlas')
    expect(typed.documentType).toBe('PANEL_DRAWING')
    const untyped = snapshotFileToDocFile({ attachmentId: 12, fileName: 'misc.jpg' })
    expect(untyped.section).toBe('other')
  })
})

describe('打印文档集默认勾选（dev-20261019-018）', () => {
  it('未分类文件不默认勾选打印', () => {
    const untyped = doc({
      id: 1, drawingNo: 'DWG-1', version: 'A', fileRole: 'PRINT', isCurrent: 1, released: 1,
    })
    expect(defaultDocumentFiles([untyped])).toEqual([])
  })

  it('分色检查表与未分类（other）都不默认勾选，工程图集打印件默认选中', () => {
    const color = doc({ id: 2, section: 'color', documentType: 'COLOR_CHECK' })
    const other = doc({ id: 3, section: 'other', documentType: 'BOM' })
    const atlas = doc({
      id: 4, section: 'atlas', documentType: 'STRUCTURE_DRAWING',
      drawingNo: 'DWG-4', version: 'A', fileRole: 'PRINT', isCurrent: 1, released: 1,
    })
    expect(defaultDocumentFiles([color, other])).toEqual([])
    expect(defaultDocumentFiles([color, other, atlas]).map((file) => file.id)).toEqual([4])
  })

  it('同版本仅有原稿时默认选原稿（可预览）', () => {
    const original = doc({
      id: 5, section: 'atlas', documentType: 'FILM',
      drawingNo: 'DWG-5', version: 'A', fileRole: 'ORIGINAL', isCurrent: 1, released: 1,
    })
    expect(defaultDocumentFiles([original]).map((file) => file.id)).toEqual([5])
  })
})

describe('上传判重只看文件指纹（dev-20261019-018）', () => {
  it('同一图纸编号+版本的不同文件全部可存，不产生重复提示', () => {
    // fingerprint 不接收 drawingNo/version —— 同编号同版本多文件天然允许。
    const rows = [
      { fileName: 'a.jpg', fileSize: 1000, lastModified: 1 },
      { fileName: 'b.jpg', fileSize: 1000, lastModified: 1 },
      { fileName: 'c.jpg', fileSize: 1000, lastModified: 1 },
    ]
    expect(duplicateFingerprints(rows)).toEqual([])
  })

  it('同名同大小同修改时间才提示“可能重复”，且只提示不阻断', () => {
    const rows = [
      { fileName: 'a.jpg', fileSize: 1000, lastModified: 1 },
      { fileName: 'b.jpg', fileSize: 1000, lastModified: 1 },
      { fileName: 'a.jpg', fileSize: 1000, lastModified: 1 },
    ]
    expect(duplicateFingerprints(rows)).toEqual([fileFingerprint('a.jpg', 1000, 1)])
    // 同名不同大小 / 同名不同修改时间均不视为重复
    expect(duplicateFingerprints([
      { fileName: 'a.jpg', fileSize: 1000, lastModified: 1 },
      { fileName: 'a.jpg', fileSize: 2000, lastModified: 1 },
      { fileName: 'a.jpg', fileSize: 1000, lastModified: 2 },
    ])).toEqual([])
  })
})
