import { afterEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ManufacturingBomPath from '../ManufacturingBomPath.vue'

const mounted: ReturnType<typeof mount>[] = []
function path(nodes: any[]) {
  const wrapper = mount(ManufacturingBomPath, { props: { nodes } })
  mounted.push(wrapper)
  return wrapper
}

afterEach(() => {
  mounted.splice(0).forEach((wrapper) => wrapper.unmount())
})

describe('制造BOM层路径（只展示显式引用，不推断制造关系）', () => {
  it('单层只显示当前产出，不显示“返回上层”', () => {
    const wrapper = path([{ materialId: 349, materialCode: 'SFG000345', materialName: '中间件' }])
    expect(wrapper.text()).toContain('SFG000345 中间件')
    expect(wrapper.find('.path-back').exists()).toBe(false)
    expect(wrapper.findAll('.path-link')).toHaveLength(0)
  })

  it('多层用编码+名称显示，点击上层节点发出 navigate(index)', async () => {
    const wrapper = path([
      { materialId: 349, materialCode: 'SFG000345', materialName: '中间件', bomId: 124 },
      { materialId: 350, materialCode: 'SFG000350', materialName: '小片', bomId: 200 },
    ])
    const links = wrapper.findAll('.path-link')
    expect(links).toHaveLength(1)
    expect(links[0].text()).toBe('SFG000345 中间件')
    await links[0].trigger('click')
    expect(wrapper.emitted('navigate')?.at(-1)).toEqual([0])
  })

  it('“返回上层”回到上一层，不跳根', async () => {
    const wrapper = path([
      { materialId: 349, materialCode: 'A', materialName: '甲' },
      { materialId: 350, materialCode: 'B', materialName: '乙' },
      { materialId: 351, materialCode: 'C', materialName: '丙' },
    ])
    await wrapper.get('.path-back').trigger('click')
    expect(wrapper.emitted('navigate')?.at(-1)).toEqual([1])
  })

  it('当前层是纯文本，不发出跳转；缺编码名称时如实回退，不伪造名称', async () => {
    const wrapper = path([
      { materialId: 349, materialCode: 'A', materialName: '甲' },
      { materialId: 350 },
    ])
    expect(wrapper.get('.path-current').text()).toBe('物料 350')
    await wrapper.get('.path-current').trigger('click')
    expect(wrapper.emitted('navigate')).toBeUndefined()
  })
})
