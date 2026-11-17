import { afterEach, describe, expect, it } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import Panel from '../WorkSpecIssuesPanel.vue'
import { buildWorkSpecIssues } from '../workSpecIssues'
const wrappers: ReturnType<typeof mount>[] = []
const context = { productId: 42, productCode: 'P42', files: [{ attachmentId: 8, fileName: '结构图.pdf', version: 'A' }] }
function render(extra = {}) {
  const wrapper = mount(Panel, {
    props: { context, issues: buildWorkSpecIssues(['结构图已失效'], context), canView: true, locatable: true, ...extra },
    global: { stubs: {
      ElButton: { template: '<button :disabled="disabled"><slot /></button>', props: ['disabled'] },
      ElDialog: { template: '<div v-if="modelValue"><h3>{{ title }}</h3><slot /></div>', props: ['modelValue', 'title'] },
      ElAlert: { template: '<p>{{title}}</p>', props: ['title'] },
      ElTable: { template: '<div><slot /><slot name="empty" /></div>', props: ['data'] },
      ElTableColumn: true,
    } },
  })
  wrappers.push(wrapper)
  return wrapper
}
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()))
describe('问题查看与定位保持只读', () => {
  it('查看后原错误保留，仅明确点击定位才发出当前对象目标', async () => {
    const wrapper = render()
    await wrapper.get('button').trigger('click')
    expect(wrapper.text()).toContain('只读')
    expect(wrapper.emitted('locate')).toBeUndefined()
    await wrapper.findAll('button').at(-1)!.trigger('click')
    expect(wrapper.emitted('locate')).toEqual([['structure']])
    expect(wrapper.text()).toContain('结构图已失效')
  })
  it('无查看权限或正在处理时不会打开或定位', async () => {
    const denied = render({ canView: false })
    expect(denied.findAll('button')).toHaveLength(0)
    expect(denied.text()).toContain('无资料查看权限')
    const blocked = render({ blocked: true })
    await blocked.get('button').trigger('click')
    expect(blocked.text()).not.toContain('已加载资料（只读）')
    expect(blocked.emitted('locate')).toBeUndefined()
  })
  it('切换产品会关闭旧产品对象窗口', async () => {
    const wrapper = render()
    await wrapper.get('button').trigger('click')
    await wrapper.setProps({ context: { productId: 43 }, issues: buildWorkSpecIssues(['BOM来源异常'], { productId: 43 }) })
    await flushPromises()
    expect(wrapper.text()).not.toContain('只读')
    expect(wrapper.text()).not.toContain('P42')
  })
  it('对象读取失败与没有查看权限分别提示，不把网络失败当作无权限', async () => {
    const wrapper = render({ contextReady: false })
    expect(wrapper.text()).toContain('尚未成功读取')
    expect(wrapper.text()).not.toContain('无资料查看权限')
    await wrapper.get('button').trigger('click')
    expect(wrapper.text()).not.toContain('只读')
  })
})
