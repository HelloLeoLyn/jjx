import { describe, expect, it } from 'vitest'
import { buildPickPreviewHtml, formatPickRatio } from '../pickPreviewPrint'

describe('生成前领料打印数据', () => {
  it('选列保持表头、单元格、空表跨列一致，剩余列宽总和100%，树层级仍在', () => {
    const html = buildPickPreviewHtml({
      orderNo: 'WO7',
      visibleColumns: ['material', 'unit', 'material', 'invalid'],
      rows: [
        {
          materialName: '3M9080A',
          depth: 1,
          sequence: '2.1',
          unit: 'M',
          isBomDetail: true,
        },
      ],
    })
    const doc = new DOMParser().parseFromString(html, 'text/html')
    expect([...doc.querySelectorAll('th')].map((node) => node.textContent)).toEqual([
      '物料编码/原料品名',
      '单位',
    ])
    expect(doc.querySelectorAll('tbody td')).toHaveLength(2)
    expect(doc.querySelector('tbody tr')?.getAttribute('data-depth')).toBe('1')
    expect(doc.querySelector('tbody td')?.textContent).toContain('↳')
    const widths = [...doc.querySelectorAll('col')].map((col) => parseFloat(col.style.width))
    expect(widths.reduce((a, b) => a + b, 0)).toBeCloseTo(100)
    const empty = new DOMParser().parseFromString(
      buildPickPreviewHtml({ orderNo: 'WO7', visibleColumns: ['unit'], rows: [] }),
      'text/html'
    )
    expect(empty.querySelector('tbody td')?.getAttribute('colspan')).toBe('1')
  })

  it('制造投入只读且无需求/库存数字，按已有参数显示，引用错误也带入打印', () => {
    const doc = new DOMParser().parseFromString(
      buildPickPreviewHtml({
        orderNo: 'WO7',
        rows: [
          {
            materialName: '3M9080A',
            unit: 'M',
            isBomDetail: true,
            sequence: '2.1',
            depth: 1,
            detailNote: '下层方案不可读',
          },
        ],
      }),
      'text/html'
    )
    const cells = [...doc.querySelectorAll('tbody td')].map((node) => node.textContent)
    expect(cells[0]).toBe('2.1')
    expect(cells.slice(5, 9)).toEqual(['- / -', '-', '-', '-'])
    expect(cells[9]).toContain('制造BOM明细；下层方案不可读')
  })
  it('模数和基数不做除法，保留四位小数及空值', () => {
    expect(formatPickRatio({ moduleQty: 0.0001, baseQty: 0.0025 })).toBe('0.0001 / 0.0025')
    expect(formatPickRatio({ moduleQty: null })).toBe('- / -')
    expect(formatPickRatio({ moduleQty: 0, baseQty: 'abc' })).toBe('0 / -')
  })

  it('引用领料单版式，区分拟领与实发；替代料缺少参数时不冒用主料参数', () => {
    const html = buildPickPreviewHtml({
      orderNo: 'WO7',
      rows: [
        {
          materialName: '替代材料',
          substitute: true,
          substituteOf: 'M11',
          qtyPick: 10,
        },
      ],
    })
    const doc = new DOMParser().parseFromString(html, 'text/html')
    expect(doc.body.textContent).toContain('深圳市精捷信科技有限公司')
    expect(doc.body.textContent).toContain('未生成单据 · 未发料')
    const cells = [...doc.querySelectorAll('tbody td')].map((node) => node.textContent)
    expect(cells[5]).toBe('- / -')
    expect(cells[7]).toBe('自动补足')
    expect(cells[9]).toBe('替代 M11')
    expect(doc.querySelectorAll('input, button, script')).toHaveLength(0)
    expect(html).toContain('size: A4 portrait')
    expect(html).toContain('display: table-header-group')
  })

  it('工单号和物料等内容均转义为文本，不能注入HTML或脚本', () => {
    const attack = '</title><script>alert(1)</script><img src=x onerror="alert(1)">'
    const html = buildPickPreviewHtml({
      orderNo: attack,
      productName: attack,
      rows: [
        {
          materialCode: attack,
          materialName: attack,
          specification: attack,
          processName: attack,
          unit: attack,
          substitute: true,
          substituteOf: attack,
        },
      ],
    })
    const doc = new DOMParser().parseFromString(html, 'text/html')
    expect(doc.querySelectorAll('script, img')).toHaveLength(0)
    expect(doc.title).toBe(`领料单预览 ${attack}`)
    expect(doc.querySelector('tbody')?.textContent).toContain(attack)
  })
})
