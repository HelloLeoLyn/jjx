import { afterEach, describe, expect, it, vi } from 'vitest'
import { enableAutoUnmount, mount } from '@vue/test-utils'
import type { InventoryMaterial } from '@/types/inventory/material'

vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))

import MaterialSearchOption from '../MaterialSearchOption.vue'

enableAutoUnmount(afterEach)

function material(overrides: Partial<InventoryMaterial> = {}): InventoryMaterial {
  return {
    materialId: 1,
    materialName: 'PET保护膜',
    materialCode: 'MAT-PET',
    specification: '0.08mm',
    categoryName: '保护膜',
    unit: '卷',
    safeStock: 0,
    maxStock: 0,
    reorderPoint: 0,
    batchControl: false,
    expiryAlertDays: 0,
    status: '0',
    ...overrides,
  }
}

const spanTexts = (wrapper: ReturnType<typeof mount>, row: string) =>
  wrapper.get(row).findAll('span').map(span => span.text())

describe('物料搜索候选行展示', () => {
  it('第一行依次显示名称、编码、规格，第二行按定稿顺序显示信息且不重复规格', () => {
    const wrapper = mount(MaterialSearchOption, { props: { item: material({
      model: 'PET-008',
      supplierName: '甲供应商',
      supplierMaterialName: '透明基膜',
      size: '1000×2000mm',
      materialMeta: '聚酯',
    }) } })

    expect(spanTexts(wrapper, '.row-main')).toEqual(['PET保护膜', 'MAT-PET', '规格：0.08mm'])
    expect(spanTexts(wrapper, '.row-meta')).toEqual([
      '品类：保护膜', '型号：PET-008', '供应商：甲供应商', '供应商品名：透明基膜',
      '尺寸：1000×2000mm', '单位：卷', '材质：聚酯',
    ])
    expect(wrapper.text().match(/规格：/g)).toHaveLength(1)
    expect(wrapper.find('.row-remark').exists()).toBe(false)
    expect(wrapper.findAll('.missing')).toHaveLength(0)
  })

  it('缺少名称、编码、品类和规格时保留灰字占位，其他缺值信息不显示', () => {
    const wrapper = mount(MaterialSearchOption, { props: { item: material({
      materialName: '', materialCode: '', categoryName: '', specification: '', unit: '',
      model: '', supplierName: '', supplierMaterialName: '', size: '', materialMeta: '',
    }) } })

    expect(spanTexts(wrapper, '.row-main')).toEqual(['未填写名称', '无编码', '规格：未填写'])
    expect(spanTexts(wrapper, '.row-meta')).toEqual(['品类：未归类'])
    expect(wrapper.findAll('.missing').map(span => span.text())).toEqual([
      '未填写名称', '无编码', '规格：未填写', '品类：未归类',
    ])
  })

  it.each([
    { supplierName: '甲供应商', supplierMaterialName: '透明基膜', expected: ['供应商：甲供应商', '供应商品名：透明基膜'] },
    { supplierName: '甲供应商', supplierMaterialName: '', expected: ['供应商：甲供应商'] },
    { supplierName: '', supplierMaterialName: '透明基膜', expected: ['供应商品名：透明基膜'] },
    { supplierName: undefined, supplierMaterialName: undefined, expected: [] },
  ])('独立显示供应商与供应商品名：$supplierName / $supplierMaterialName', ({ expected, ...fields }) => {
    const wrapper = mount(MaterialSearchOption, { props: { item: material(fields) } })
    expect(spanTexts(wrapper, '.row-meta').filter(text => text.startsWith('供应商'))).toEqual(expected)
  })

  it('型号缺省不显示占位，传入型号后显示在第二行', async () => {
    const wrapper = mount(MaterialSearchOption, { props: { item: material() } })
    expect(wrapper.get('.row-meta').text()).not.toContain('型号')
    await wrapper.setProps({ item: material({ model: 'PET-008' }) })
    expect(wrapper.get('.row-meta').text()).toContain('型号：PET-008')
    expect(wrapper.get('.row-main').text()).not.toContain('型号')
  })

  it.each([
    { isCustomerSupplied: true, isSample: true, expected: ['客供', '样品'] },
    { isCustomerSupplied: true, isSample: false, expected: ['客供'] },
    { isCustomerSupplied: false, isSample: true, expected: ['样品'] },
    { isCustomerSupplied: false, isSample: false, expected: [] },
  ])('保持客供/样品标签：$isCustomerSupplied / $isSample', ({ expected, ...flags }) => {
    const wrapper = mount(MaterialSearchOption, { props: { item: material(flags) } })
    expect(wrapper.findAll('.row-main .badge').map(badge => badge.text())).toEqual(expected)
  })

  it('备注默认收起，可展开再收起，点击不会冒泡选择候选物料', async () => {
    const onClick = vi.fn()
    const remark = '首行备注\n第二行备注'
    const wrapper = mount(MaterialSearchOption, { props: { item: material({ remark }) }, attrs: { onClick } })
    const button = wrapper.get('button')

    expect(button.text()).toBe('展开备注')
    expect(wrapper.find('.remark-text').exists()).toBe(false)
    await button.trigger('click')
    expect(button.text()).toBe('收起备注')
    expect(wrapper.get('.remark-text').element.textContent).toBe(remark)
    await button.trigger('click')
    expect(button.text()).toBe('展开备注')
    expect(wrapper.find('.remark-text').exists()).toBe(false)
    expect(onClick).not.toHaveBeenCalled()
  })
})
