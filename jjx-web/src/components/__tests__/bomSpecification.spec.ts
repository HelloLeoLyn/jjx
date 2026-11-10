import { describe, expect, it } from 'vitest'
import { generateBomSpecification, specificationSource } from '../bomSpecification'
import type { MaterialMasterDetail } from '@/types/inventory/materialMaster'

describe('BOM规格以档案为准', () => {
  it('直接返回原始档案规格，不 trim、不拼尺寸属性', () => {
    expect(generateBomSpecification({ specification: ' 450*225mm ', size: '别的尺寸', attributes: { coating: '单面加硬' } })).toBe(' 450*225mm ')
  })
  it.each([null, undefined, ''])('档案规格为空 %s 时不拼属性兜底', specification => {
    expect(generateBomSpecification({ specification, size: '450*225mm', attributes: { coating: '单面加硬' } })).toBe('')
  })
  it('详情使用档案规格', () => {
    expect(generateBomSpecification(specificationSource({ material: { specification: '450*225mm' }, attributes: {} } as MaterialMasterDetail))).toBe('450*225mm')
  })
})
