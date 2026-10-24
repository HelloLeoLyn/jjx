import { describe, expect, it } from 'vitest'
import {
  PICKER_DEFAULT_WIDTH,
  PICKER_MAX_WIDTH,
  PICKER_MIN_WIDTH,
  clampPickerWidth,
  readPickerWidth,
  savePickerWidth,
} from '../samplePlanLayout'

function memoryStorage(initial: Record<string, string> = {}) {
  const map = new Map(Object.entries(initial))
  return {
    getItem: (key: string) => map.get(key) ?? null,
    setItem: (key: string, value: string) => {
      map.set(key, value)
    },
  }
}

describe('samplePlanLayout 标准工序左栏宽度', () => {
  it('默认 300，上限 400，下限 220', () => {
    expect(PICKER_DEFAULT_WIDTH).toBe(300)
    expect(PICKER_MAX_WIDTH).toBe(400)
    expect(PICKER_MIN_WIDTH).toBe(220)
    expect(clampPickerWidth(300)).toBe(300)
    expect(clampPickerWidth(500)).toBe(400)
    expect(clampPickerWidth(100)).toBe(220)
  })

  it('非法值回落默认值', () => {
    expect(clampPickerWidth(undefined)).toBe(300)
    expect(clampPickerWidth('abc')).toBe(300)
    expect(clampPickerWidth(null)).toBe(300)
    expect(clampPickerWidth(320.6)).toBe(321)
  })

  it('无存储时读默认值，写入后再读能取回并夹取', () => {
    expect(readPickerWidth(null)).toBe(300)
    const storage = memoryStorage()
    expect(readPickerWidth(storage)).toBe(300)
    savePickerWidth(storage, 380)
    expect(readPickerWidth(storage)).toBe(380)
    savePickerWidth(storage, 999)
    expect(readPickerWidth(storage)).toBe(400)
  })

  it('存储异常不影响交互', () => {
    const broken = {
      getItem: () => {
        throw new Error('blocked')
      },
      setItem: () => {
        throw new Error('blocked')
      },
    }
    expect(readPickerWidth(broken)).toBe(300)
    expect(() => savePickerWidth(broken, 320)).not.toThrow()
  })
})
