import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import MaterialAttributeFields from '../MaterialAttributeFields.vue'

const FormItem = defineComponent({
  props: ['label', 'required'],
  template: '<div class="fi">{{ label }}<slot /></div>',
})

describe('属性字段渲染', () => {
  it('未传 requiredOnly 时必填与选填都渲染（Vue 会把未传 Boolean 补成 false，不能当成"只渲染选填"）', () => {
    const schema = {
      version: 1,
      meta_options: [],
      fields: [
        { key: 'component_type', label: '元件类型', type: 'enum', required: true, options: [{ code: 'LED', label: 'LED' }] },
        { key: 'package', label: '封装', type: 'string', required: false },
      ],
    }
    const wrapper = mount(MaterialAttributeFields, {
      props: { schema: schema as any, modelValue: {} },
      global: {
        stubs: {
          ElFormItem: FormItem,
          ElSelect: { template: '<div><slot /></div>' },
          ElOption: { template: '<div />' },
          ElInput: { template: '<input />' },
          ElInputNumber: { template: '<input />' },
        },
      },
    })
    const labels = wrapper.findAll('.fi').map((node) => node.text())
    expect(labels).toContain('元件类型')
    expect(labels).toContain('封装')
  })
})

const Select = defineComponent({
  props: ['modelValue', 'multiple', 'collapseTags', 'collapseTagsTooltip', 'maxCollapseTags'],
  emits: ['update:modelValue'],
  template: '<div class="select"><slot /></div>',
})
const TextInput = defineComponent({
  props: ['modelValue'], emits: ['update:modelValue'],
  template: '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
})
function attributes(schema: any, modelValue: any = {}) {
  return mount(MaterialAttributeFields, {
    props: { schema, modelValue },
    global: { stubs: { ElFormItem: FormItem, ElSelect: Select, ElOption: true, ElInput: TextInput, ElInputNumber: true } },
  })
}

describe('属性双列与条件字段交互', () => {
  const schema = { version: 1, meta_options: [], fields: [
    { key: 'surface', label: '表面处理', type: 'multienum', required: false, options: [{ code: 'A', label: '亮面' }] },
    { key: 'film', label: '双面复膜', type: 'boolean', required: true },
    { key: 'remark', label: '说明', type: 'string', required: false },
    { key: 'weight', label: '克重', type: 'decimal', required: false, unit: 'g/m²' },
    { key: 'detail', label: '附加项', type: 'string', required: false, visible_when: { film: 'true' } },
  ] }

  it('多选整行且可查看折叠标签，普通字段不跨列；保留顺序及必填', () => {
    const wrapper = attributes(schema)
    expect(wrapper.findAll('[data-attribute-key]').map(node => node.attributes('data-attribute-key')))
      .toEqual(['surface', 'film', 'remark', 'weight'])
    expect(wrapper.get('[data-attribute-key="surface"]').classes()).toContain('attribute-field--wide')
    expect(wrapper.get('[data-attribute-key="film"]').classes()).not.toContain('attribute-field--wide')
    expect(wrapper.findAllComponents(FormItem)[1].props('required')).toBe(true)
    const select = wrapper.get('[data-attribute-key="surface"]').findComponent(Select)
    expect(select.props()).toMatchObject({ multiple: true, collapseTags: true, collapseTagsTooltip: true, maxCollapseTags: 2 })
    expect(wrapper.get('.numeric-field').text()).toContain('g/m²')
    wrapper.unmount()
  })

  it('长文本扩展整行时不重建输入框或改变属性值', async () => {
    const wrapper = attributes(schema, { remark: '短文本' })
    const input = wrapper.get('[data-attribute-key="remark"]').find('input').element
    await wrapper.setProps({ modelValue: { remark: '长内容'.repeat(12) } })
    expect(wrapper.get('[data-attribute-key="remark"]').classes()).toContain('attribute-field--wide')
    expect(wrapper.get('[data-attribute-key="remark"]').find('input').element).toBe(input)
    expect(wrapper.emitted('update:modelValue')).toBeUndefined()
    wrapper.unmount()
  })

  it('父属性改变后仍清理不再适用值，显隐及筛选不受布局影响', async () => {
    const wrapper = attributes(schema, { film: true, detail: '历史内容', remark: '保留内容' })
    expect(wrapper.find('[data-attribute-key="detail"]').exists()).toBe(true)
    wrapper.get('[data-attribute-key="film"]').findComponent(Select).vm.$emit('update:modelValue', false)
    expect(wrapper.emitted('update:modelValue')?.[0]).toEqual([{ film: false, remark: '保留内容' }])
    await wrapper.setProps({ modelValue: { film: false, remark: '保留内容' } })
    expect(wrapper.find('[data-attribute-key="detail"]').exists()).toBe(false)
    await wrapper.setProps({ requiredOnly: true })
    expect(wrapper.findAll('[data-attribute-key]').map(node => node.attributes('data-attribute-key'))).toEqual(['film'])
    wrapper.unmount()
  })
})
