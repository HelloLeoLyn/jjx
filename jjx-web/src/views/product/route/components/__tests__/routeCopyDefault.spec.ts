import { describe, it, expect, vi } from 'vitest'
import { defineComponent } from 'vue'
import { mount } from '@vue/test-utils'
import RouteCopyDialog from '../RouteCopyDialog.vue'
const Slot = defineComponent({ template: '<div><slot /><slot name="footer" /></div>' })
const Form = defineComponent({
  setup(_, { expose }) {
    expose({ validate: vi.fn().mockResolvedValue(true), resetFields() {} })
    return {}
  },
  template: '<div><slot /></div>',
})
describe('复制默认版本号', () => {
  it('不手输即可提交服务端建议版本，失败后可修改重试，再次打开更新建议', async () => {
    const wrapper = mount(RouteCopyDialog, {
      props: { modelValue: true, currentVersion: 'V1.0', suggestedVersion: 'V1.1' },
      global: {
        stubs: {
          ElDialog: Slot,
          ElForm: Form,
          ElFormItem: Slot,
          ElInput: true,
          ElButton: Slot,
          ElAlert: true,
        },
      },
    })
    const vm = wrapper.vm as any
    expect(vm.form.newVersion).toBe('V1.1')
    await vm.handleSubmit()
    expect(wrapper.emitted('confirm')?.[0]).toEqual(['V1.1'])
    await wrapper.setProps({ error: '同产品版本号已存在：V1.1' })
    vm.form.newVersion = 'V1.2'
    await vm.handleSubmit()
    expect(wrapper.emitted('confirm')?.[1]).toEqual(['V1.2'])
    await wrapper.setProps({ modelValue: false })
    await wrapper.setProps({ modelValue: true, currentVersion: 'V9.9', suggestedVersion: 'V10.0' })
    expect(vm.form.newVersion).toBe('V10.0')
    wrapper.unmount()
  })
})
