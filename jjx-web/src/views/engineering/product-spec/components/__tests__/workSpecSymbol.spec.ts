import { describe, expect, it } from 'vitest'
import { workSpecSymbolText, workSpecStepSubscript } from '../docset'

/** dev-20261026-006：作业规范预览/打印的文字符号口径。 */
describe('作业规范文字符号', () => {
  it('空 icon：符号取作业说明，序号不拼进符号', () => {
    expect(workSpecSymbolText({ icon: '', workInstruction: '贴保护膜', indexNumber: 3 })).toBe('贴保护膜')
  })

  it('有 icon：不产生文字符号', () => {
    expect(workSpecSymbolText({ icon: 'icon-cut', workInstruction: '冲切' })).toBe('')
  })

  it('既有下标口径保持不变（有图标时序号+说明）', () => {
    expect(workSpecStepSubscript({ icon: 'icon-cut', indexNumber: 2, workInstruction: '冲切' })).toBe(
      '2 冲切'
    )
  })
})
