/**
 * dev-20261020-005：报价单弹窗切换客户后，联系人/联系电话跟随新客户档案
 *
 * 覆盖面（对应卡面验收 A–E）：
 * A）客户 A → 客户 B：联系人/电话变为 B 的值；
 * B）切到无联系人/电话的客户 C：两字段置空，不残留 B 的值；
 * C）同一客户重选不重置用户手工修改；切到另一客户按新档案覆盖；
 * D）清空客户：联系人/电话一并清空；
 * E）保存时提交的 formData（父组件原样展开进 payload）是当前显示的新客户联系人/电话。
 * 另含编辑回填不覆盖单据自带联系人的反例（程序性赋值不触发 change）。
 */
// element-plus 组件样式在 jsdom 下不需要真实 CSS：与本目录既有用例同款处理
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/date-picker/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/divider/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/radio-group/style/css', () => ({}))
vi.mock('element-plus/es/components/radio/style/css', () => ({}))
vi.mock('element-plus/es/components/row/style/css', () => ({}))
vi.mock('element-plus/es/components/col/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('element-plus/es/components/scrollbar/style/css', () => ({}))

import { describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { reactive, nextTick } from 'vue'

/** el-form 桩：本卡不改校验规则，只验证提交取值链路；jsdom 下真实异步校验会卡在 validating */
const ElFormStub = {
  name: 'ElForm',
  template: '<form><slot /></form>',
  methods: {
    validate(cb: (valid: boolean) => void) {
      cb(true)
    },
    clearValidate() {},
    resetFields() {},
  },
}

vi.mock('@/components/attachment/AttachmentManager.vue', () => ({
  default: { name: 'AttachmentManager', template: '<div />' },
}))
vi.mock('@/components/attachment/AttachmentUpload.vue', () => ({
  default: { name: 'AttachmentUpload', template: '<div />' },
}))
vi.mock('../QuotationCodeGenDialog.vue', () => ({
  default: { name: 'QuotationCodeGenDialog', template: '<div />' },
}))
vi.mock('@/api/sales/quotation', () => ({ quotationApi: { getExchangeRate: vi.fn() } }))
vi.mock('@/api/product', () => ({ listProduct: vi.fn().mockResolvedValue({ data: [] }) }))

import QuotationFormDialog from '../QuotationFormDialog.vue'
import CustomerSelector from '@/components/Selector/CustomerSelector.vue'

const customer = (extra: Record<string, any> = {}) => ({
  customerId: 1,
  customerCode: 'C001',
  customerName: '客户甲',
  customerShortName: '甲',
  contactPerson: '张三',
  contactPhone: '13800000001',
  email: '',
  address: '',
  creditLimit: 0,
  status: 1,
  remark: '',
  ...extra,
})

const customerA = customer()
const customerB = customer({
  customerId: 2,
  customerCode: 'C002',
  customerName: '客户乙',
  customerShortName: '乙',
  contactPerson: '李四',
  contactPhone: '13900000002',
})
const customerC = customer({
  customerId: 3,
  customerCode: 'C003',
  customerName: '客户丙',
  customerShortName: '丙',
  contactPerson: '',
  contactPhone: '',
})

function baseForm(extra: Record<string, any> = {}) {
  return {
    quotationId: 0,
    quotationNo: '',
    quotationType: 1,
    customerId: 1,
    customerName: '客户甲',
    contactPerson: '张三',
    contactPhone: '13800000001',
    quotationDate: '2026-10-20',
    validUntil: '2026-11-19',
    currency: 'CNY',
    exchangeRate: 1,
    subtotalAmount: 10,
    taxRate: 0,
    taxAmount: 0,
    totalAmount: 10,
    discountAmount: 0,
    finalAmount: 10,
    quotationStatus: 0,
    remark: '',
    traceId: '',
    attachmentIds: [],
    items: [
      {
        productId: undefined,
        productCode: 'P001',
        productName: '产品A',
        quantity: 1,
        unitPrice: 10,
        amount: 10,
        unit: 'PCS',
      },
    ],
    ...extra,
  }
}



function mountDialog(extra: Record<string, any> = {}) {
  const formData = reactive(baseForm(extra))
  const wrapper = mount(QuotationFormDialog, {
    attachTo: document.body,
    props: {
      modelValue: true,
      title: '修改报价单【Q1】',
      formData,
      currencyOptions: [{ value: 'CNY', label: '人民币' }],
      session: {} as any,
      draftId: 'test-draft',
    },
    global: { stubs: { ElForm: ElFormStub } },
  })
  return { wrapper, formData }
}

/** 模拟 CustomerSelector 的 v-model + change（与组件 set 内两次 emit 顺序一致） */
async function selectCustomer(wrapper: any, next: any | null) {
  const selector = wrapper.findComponent(CustomerSelector)
  expect(selector.exists()).toBe(true)
  const id = next ? next.customerId : null
  selector.vm.$emit('update:modelValue', id)
  selector.vm.$emit('change', id, next)
  await nextTick()
}

async function clickSave(wrapper: any) {
  // el-dialog 内容 teleport 到 body：按组件树找「保存」按钮，避免命中上一个用例残留的 DOM
  const button = wrapper
    .findAllComponents({ name: 'ElButton' })
    .find((btn: any) => (btn.text() || '').replace(/\s/g, '') === '保存')
  expect(button).toBeTruthy()
  await button!.trigger('click')
  await flushPromises()
  await new Promise((r) => setTimeout(r, 100))
  await flushPromises()
}

describe('QuotationFormDialog 切换客户后联系人/电话（dev-20261020-005）', () => {
  it('A）客户 A → 客户 B：联系人/电话变为 B 的档案值', async () => {
    const { wrapper, formData } = mountDialog()
    await flushPromises()
    expect(formData.contactPerson).toBe('张三')

    await selectCustomer(wrapper, customerB)
    expect(formData.customerId).toBe(2)
    expect(formData.customerName).toBe('客户乙')
    expect(formData.contactPerson).toBe('李四')
    expect(formData.contactPhone).toBe('13900000002')
    wrapper.unmount()
  })

  it('B）切到无联系人/电话的客户 C：两字段置空，不残留 B 的值', async () => {
    const { wrapper, formData } = mountDialog()
    await flushPromises()
    await selectCustomer(wrapper, customerB)
    expect(formData.contactPerson).toBe('李四')

    await selectCustomer(wrapper, customerC)
    expect(formData.customerName).toBe('客户丙')
    expect(formData.contactPerson).toBe('')
    expect(formData.contactPhone).toBe('')
    wrapper.unmount()
  })

  it('C）同一客户重选不重置手工修改；切到另一客户按新档案覆盖', async () => {
    const { wrapper, formData } = mountDialog()
    await flushPromises()
    await selectCustomer(wrapper, customerB)
    expect(formData.contactPerson).toBe('李四')

    formData.contactPerson = '手工联系人'
    formData.contactPhone = '010-88888888'
    await selectCustomer(wrapper, customerB)
    expect(formData.contactPerson).toBe('手工联系人')
    expect(formData.contactPhone).toBe('010-88888888')

    await selectCustomer(wrapper, customerA)
    expect(formData.contactPerson).toBe('张三')
    expect(formData.contactPhone).toBe('13800000001')
    wrapper.unmount()
  })

  it('D）清空客户：联系人/电话一并清空，产品行仍按原逻辑清空', async () => {
    const { wrapper, formData } = mountDialog()
    await flushPromises()
    await selectCustomer(wrapper, customerB)

    await selectCustomer(wrapper, null)
    expect(formData.customerId).toBeNull()
    expect(formData.customerName).toBe('')
    expect(formData.contactPerson).toBe('')
    expect(formData.contactPhone).toBe('')
    expect(formData.items[0].productCode).toBe('')
    wrapper.unmount()
  })

  it('编辑回填（程序性赋值）不覆盖单据自带联系人', async () => {
    const { wrapper, formData } = mountDialog({
      contactPerson: '单据联系人',
      contactPhone: '010-11112222',
    })
    await flushPromises()
    expect(formData.contactPerson).toBe('单据联系人')
    expect(formData.contactPhone).toBe('010-11112222')
    wrapper.unmount()
  })

  it('E）换客户后保存：提交的是当前显示的新客户联系人/电话', async () => {
    const { wrapper, formData } = mountDialog()
    await flushPromises()
    await selectCustomer(wrapper, customerB)
    // 换客户会按既有逻辑清空产品行（卡面 D 要求保留），用户需重新选产品后才能保存
    formData.items[0].productCode = 'P002'
    formData.items[0].productName = '产品B'
    formData.items[0].productId = 22

    await clickSave(wrapper)
    expect(wrapper.emitted('submit')).toHaveLength(1)
    // 父组件 submitForm 用 { ...form } 作为 payload ⇒ formData 即提交源
    expect(formData.contactPerson).toBe('李四')
    expect(formData.contactPhone).toBe('13900000002')
    expect(formData.customerId).toBe(2)
    wrapper.unmount()
  })
})
