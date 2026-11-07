import { describe, expect, it } from 'vitest'
import { isValidAbbr, parseAbbr, parseExtObject, withAbbr } from '../dictExtData'

describe('品牌字典 ext_data.abbr 读写', () => {
  it('读取缩写：有 abbr 时返回；无/坏 JSON/非字符串时返回空串', () => {
    expect(parseAbbr('{"abbr":"GC"}')).toBe('GC')
    expect(parseAbbr('{"abbr":"CHL","other":1}')).toBe('CHL')
    expect(parseAbbr('{}')).toBe('')
    expect(parseAbbr(undefined)).toBe('')
    expect(parseAbbr('not-json')).toBe('')
    expect(parseAbbr('{"abbr":123}')).toBe('')
  })

  it('写入缩写：转大写、保留其它键；清空时删除 abbr；空对象不写 JSON', () => {
    expect(withAbbr(undefined, 'gc')).toBe('{"abbr":"GC"}')
    expect(withAbbr('{"parent":"x"}', ' gc ')).toBe('{"parent":"x","abbr":"GC"}')
    expect(withAbbr('{"abbr":"GC"}', '')).toBeUndefined()
    expect(withAbbr('{"parent":"x"}', '')).toBe('{"parent":"x"}')
    expect(withAbbr('not-json', 'BM')).toBe('{"abbr":"BM"}')
  })

  it('格式校验：空串合法；字母数字 1-8 位合法（大小写均可）；超长或含符号不合法', () => {
    expect(isValidAbbr('')).toBe(true)
    expect(isValidAbbr('GC')).toBe(true)
    expect(isValidAbbr('chl')).toBe(true)
    expect(isValidAbbr('3M')).toBe(true)
    expect(isValidAbbr('CXG20261X')).toBe(false)
    expect(isValidAbbr('G-C')).toBe(false)
    expect(isValidAbbr('贯昌')).toBe(false)
  })

  it('ext_data 解析：非对象/空值回落空对象', () => {
    expect(parseExtObject(undefined)).toEqual({})
    expect(parseExtObject('')).toEqual({})
    expect(parseExtObject('[]')).toEqual({})
    expect(parseExtObject('{"abbr":"GC"}')).toEqual({ abbr: 'GC' })
  })
})
