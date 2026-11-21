import { describe, expect, it } from 'vitest'
import { buildPickPreviewHtml, formatPickRatio } from '../pickPreviewPrint'

describe('生成前领料打印数据', () => {
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
