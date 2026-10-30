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
