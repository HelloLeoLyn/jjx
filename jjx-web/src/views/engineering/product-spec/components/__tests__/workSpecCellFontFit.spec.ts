import { describe, expect, it } from 'vitest'
import {
  fitWorkSpecOperationCells, parseWorkSpecCellFonts, serializeWorkSpecCellFonts, workSpecCellKey,
} from '../workSpecCellFont'

describe('作业流程单格字号与宽度适配', () => {
  it('工序标识跨分页保持稳定，分类和无ID行互相隔离', () => {
    expect(workSpecCellKey(0, { itemId: 41 }, 1)).toBe(workSpecCellKey(0, { itemId: 41 }, 15))
    expect(workSpecCellKey(0, { itemId: 41 }, 1)).not.toBe(workSpecCellKey(1, { itemId: 41 }, 1))
    expect(workSpecCellKey(1, {}, 7)).toBe('flow1-row-7')
    expect(workSpecCellKey(1, { itemId: null }, 8)).toBe('flow1-row-8')
  })
  it('打印URL往返保留单格覆盖并拒绝非法键/字号', () => {
    const fonts = { 'flow0-id-41': 8, 'flow1-id-42': 16 }
    const params = new URLSearchParams({ cellFonts: serializeWorkSpecCellFonts(fonts) })
    expect(parseWorkSpecCellFonts(new URLSearchParams(params.toString()).get('cellFonts'))).toEqual(fonts)
    expect(parseWorkSpecCellFonts('flow0-id-1:4;flow0-id-2:49;flow0-id-3:8.5;__proto__:8;flow3-id-4:9;flow0-id-5:;flow0-id-6:9:')).toEqual({})
    expect(parseWorkSpecCellFonts(undefined)).toEqual({})
  })
})

function fixture(width: number, length: number, requested = 12) {
  const root = document.createElement('div')
  root.innerHTML = `<table><tr><td data-work-spec-cell="flow0-id-41" data-cell-font="${requested}"><div class="operation"><span class="symbol"><svg width="18" height="18"></svg><span class="symbol-text">${'字'.repeat(length)}</span></span></div></td></tr></table>`
  const cell = root.querySelector<HTMLElement>('td')!
  const operation = root.querySelector<HTMLElement>('.operation')!
  const text = root.querySelector<HTMLElement>('.symbol-text')!
  Object.defineProperty(operation, 'clientWidth', { configurable: true, get: () => width })
  Object.defineProperty(operation, 'scrollWidth', { get: () => 18 + length * parseFloat(text.style.fontSize) })
  return { root, cell, operation, text }
}

describe('单行文字宽度测量算法（模拟尺寸，实际布局另验）', () => {
  it('超宽缩小文字，保留SVG尺寸和完整原文；重新变宽时恢复请求字号', () => {
    const { root, cell, operation, text } = fixture(100, 10)
    fitWorkSpecOperationCells(root)
    expect(Number(cell.dataset.fittedFont)).toBeLessThan(12)
    expect(operation.scrollWidth).toBeLessThanOrEqual(101)
    expect(root.querySelector('svg')!.getAttribute('width')).toBe('18')
    expect(text.textContent).toBe('字'.repeat(10))
    Object.defineProperty(operation, 'clientWidth', { get: () => 200 })
    fitWorkSpecOperationCells(root)
    expect(text.style.fontSize).toBe('12px')
  })
  it('最小字号仍超宽时标记；没有可测宽度时不误改', () => {
    const { root, cell } = fixture(30, 30)
    fitWorkSpecOperationCells(root)
    expect(cell.dataset.fittedFont).toBe('5')
    expect(cell.hasAttribute('data-cell-font-overflow')).toBe(true)
    const hidden = fixture(0, 30)
    fitWorkSpecOperationCells(hidden.root)
    expect(hidden.text.style.fontSize).toBe('')
  })
})
