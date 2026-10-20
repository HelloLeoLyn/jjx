vi.mock('element-plus/es/components/pagination/style/css', () => ({}))
vi.mock('@/components/Pagination/index.vue', () => ({ default: { template: '<div />' } }))
vi.mock('element-plus/theme-chalk/base.css', () => ({}))
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus/es/components/message-box/style/css', () => ({}))
vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/card/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/drawer/style/css', () => ({}))
vi.mock('element-plus/es/components/tabs/style/css', () => ({}))
vi.mock('element-plus/es/components/tab-pane/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions/style/css', () => ({}))
vi.mock('element-plus/es/components/descriptions-item/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/date-picker/style/css', () => ({}))
vi.mock('element-plus/es/components/empty/style/css', () => ({}))
vi.mock('element-plus/es/components/link/style/css', () => ({}))
vi.mock('element-plus/es/components/tooltip/style/css', () => ({}))
vi.mock('element-plus/es/components/switch/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox-group/style/css', () => ({}))
vi.mock('element-plus/es/components/radio/style/css', () => ({}))
vi.mock('element-plus/es/components/radio-group/style/css', () => ({}))
vi.mock('element-plus/es/components/form/style/css', () => ({}))
vi.mock('element-plus/es/components/form-item/style/css', () => ({}))

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h, inject, provide } from 'vue'
import Page from '../index.vue'
import { setupDirectives } from '@/directives'

const mocks = vi.hoisted(() => ({
  list: vi.fn(),
  batchStatus: vi.fn(),
  claimWorkSpec: vi.fn(),
  transferWorkSpec: vi.fn(),
  get: vi.fn(),
  responsibleUsers: vi.fn(),
  open: vi.fn(),
  confirm: vi.fn(),
  success: vi.fn(),
  error: vi.fn(),
  auth: { userId: 7, roles: [] as string[], permissions: ['product:edit'] },
}))
vi.mock('@/api/product', () => ({ listProductPage: mocks.list }))
vi.mock('@/api/product/workSpec', () => ({ productWorkSpecApi: mocks }))
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({
    ...mocks.auth,
    getPermissions: mocks.auth.permissions,
    getRoles: mocks.auth.roles,
  }),
}))
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }) }))
vi.mock('element-plus', async (original) => ({
  ...(await original<typeof import('element-plus')>()),
  ElMessage: { success: mocks.success, error: mocks.error },
  ElMessageBox: { confirm: mocks.confirm },
}))
vi.mock('@/components/product/ProductSpecDrawer.vue', () => ({
  default: {
    setup(_: unknown, { expose }: any) {
      expose({ open: mocks.open })
      return () => null
    },
  },
}))
vi.mock('../components/ProductWorkSpecVersionPanel.vue', () => ({
  default: { template: '<div />' },
}))
vi.mock('../components/ProductSpecPreview.vue', () => ({ default: { template: '<div />' } }))

// Render actual row slots; Element Plus table layout/teleports are outside this regression.
const Table = defineComponent({
  props: ['data'],
  setup(props, { slots }) {
    provide('rows', () => props.data)
    return () => h('div', slots.default?.())
  },
})
const Column = defineComponent({
  setup(_, { slots }) {
    const rows = inject<() => any[]>('rows')!
    return () =>
      h(
        'div',
        rows().map((row, $index) => slots.default?.({ row, $index }))
      )
  },
})
let wrapper: ReturnType<typeof mount>
let info: Record<string, unknown>
beforeEach(() => {
  vi.resetAllMocks()
  mocks.auth.roles = []
  mocks.auth.permissions = ['product:edit', 'product:work-spec:publish']
  info = { productId: 2 }
  mocks.list.mockResolvedValue({
    data: { records: [{ productId: 2, productCode: 'P2' }], total: 1 },
  })
  mocks.batchStatus.mockImplementation(async () => ({ data: [info] }))
  mocks.confirm.mockResolvedValue(true)
  mocks.claimWorkSpec.mockResolvedValue({})
  mocks.transferWorkSpec.mockResolvedValue({})
  mocks.get.mockResolvedValue({ data: { revision: 'r1' } })
  mocks.responsibleUsers.mockResolvedValue({
    data: [
      { id: 7, name: '原负责人' },
      { id: 9, name: '新负责人' },
    ],
  })
})
afterEach(() => wrapper?.unmount())
async function setup() {
  wrapper = mount(Page, {
    global: {
      plugins: [{ install: setupDirectives }],
      directives: { loading: () => {} },
      stubs: {
        ElTable: Table,
        ElTableColumn: Column,
        Pagination: true,
        ElDialog: {
          props: ['modelValue'],
          template: '<div v-if="modelValue"><slot /><slot name="footer" /></div>',
        },
        ElSelect: {
          props: ['modelValue', 'disabled'],
          emits: ['update:modelValue'],
          template: `<select :disabled="disabled" @change="$emit('update:modelValue', Number($event.target.value))"><option value="">请选择</option><slot /></select>`,
        },
        ElOption: {
          props: ['value', 'label'],
          template: '<option :value="value">{{ label }}</option>',
        },
      },
    },
  })
  await flushPromises()
}
const button = (label: string) => wrapper.findAll('button').find((b) => b.text() === label)
const actions = ['制定规范', '继续制定', '转交', '改派']
function expectActions(expected: string[]) {
  for (const label of actions) expect(!!button(label), label).toBe(expected.includes(label))
}
async function click(label: string) {
  await button(label)!.trigger('click')
  await flushPromises()
}

describe('产品作业规范列表制定状态与操作', () => {
  it('待制定仅显示制定规范，有 product:edit 才可制定', async () => {
    await setup()
    expect(wrapper.text()).toContain('待制定')
    expectActions(['制定规范'])
    wrapper.unmount()
    mocks.auth.permissions = []
    await setup()
    expectActions([])
  })
  it('负责人继续制定以可编辑模式打开，产品编码始终只读打开', async () => {
    info.preparedById = 7
    await setup()
    expect(wrapper.text()).toContain('制定中')
    expectActions(['继续制定', '转交'])
    expect(button('作业规范')).toBeUndefined()
    await click('P2')
    expect(mocks.open).toHaveBeenLastCalledWith(2, undefined, { readOnly: true })
    await click('继续制定')
    expect(mocks.open).toHaveBeenLastCalledWith(2, undefined, { readOnly: false })
  })
  it.each([[[]], [['admin']], [['engineering:all']]])(
    '非负责人角色 %j 的操作边界',
    async (...args) => {
      mocks.auth.roles = args[0] as string[]
      info.preparedById = 9
      await setup()
      expectActions(mocks.auth.roles.length ? ['改派'] : [])
    }
  )
  it.each([[[]], [['admin']], [['engineering:all']]])(
    '已定版角色 %j 无制定相关操作且只读打开',
    async (...args) => {
      mocks.auth.roles = args[0] as string[]
      info.preparedById = 7
      info.finalizedVersionId = 3
      await setup()
      expect(wrapper.text()).toContain('已定版')
      expectActions([])
      await click('P2')
      expect(mocks.open).toHaveBeenCalledWith(2, undefined, { readOnly: true })
    }
  )
  it('claim 成功后刷新列表状态并打开可编辑', async () => {
    await setup()
    mocks.claimWorkSpec.mockImplementation(async () => {
      info.preparedById = 7
    })
    await click('制定规范')
    expect(mocks.confirm).toHaveBeenCalledTimes(1)
    expect(mocks.claimWorkSpec).toHaveBeenCalledWith(2)
    expect(mocks.list).toHaveBeenCalledTimes(2)
    expect(mocks.batchStatus).toHaveBeenLastCalledWith([2])
    expectActions(['继续制定', '转交'])
    expect(mocks.open).toHaveBeenCalledWith(2, undefined, { readOnly: false })
    expect(mocks.batchStatus.mock.invocationCallOrder[1]).toBeLessThan(
      mocks.open.mock.invocationCallOrder[0]
    )
  })
  it('取消制定不调用 claim；claim 失败提示并刷新且不开编辑', async () => {
    await setup()
    mocks.confirm.mockRejectedValueOnce('cancel')
    await click('制定规范')
    expect(mocks.claimWorkSpec).not.toHaveBeenCalled()
    mocks.claimWorkSpec.mockRejectedValueOnce(new Error('领取冲突'))
    await click('制定规范')
    expect(mocks.error).toHaveBeenCalledWith('领取冲突')
    expect(mocks.list).toHaveBeenCalledTimes(2)
    expect(mocks.open).not.toHaveBeenCalled()
  })
  it.each([
    [false, false],
    [false, true],
    [true, false],
    [true, true],
  ])('转交/改派选择新负责人，改派=%s、失败=%s', async (reassign, fail) => {
    info.preparedById = 7
    if (reassign) {
      mocks.auth.roles = ['admin']
      info.preparedById = 8
    }
    await setup()
    await click(reassign ? '改派' : '转交')
    expect(mocks.get).toHaveBeenCalledWith(2)
    expect(mocks.responsibleUsers).toHaveBeenCalledWith(2)
    expect(button(reassign ? '确认改派' : '确认转交')!.attributes('disabled')).toBeDefined()
    if (!reassign)
      expect(wrapper.findAll('option').map((o) => o.attributes('value'))).not.toContain('7')
    await wrapper.find('select').setValue('9')
    if (fail) mocks.transferWorkSpec.mockRejectedValueOnce(new Error('revision冲突'))
    await click(reassign ? '确认改派' : '确认转交')
    expect(mocks.transferWorkSpec).toHaveBeenCalledWith(2, {
      revision: 'r1',
      preparedById: 9,
      ownerTransferConfirmed: true,
    })
    expect(mocks.list).toHaveBeenCalledTimes(2)
    if (fail) expect(mocks.error).toHaveBeenCalledWith('revision冲突')
    else {
      expect(mocks.success).toHaveBeenCalledWith(reassign ? '已改派制定负责人' : '已转交制定负责人')
      expect(wrapper.find('select').exists()).toBe(false)
    }
  })
  it('转交候选负责人加载失败提示并关闭弹窗', async () => {
    info.preparedById = 7
    mocks.responsibleUsers.mockRejectedValueOnce(new Error('加载失败'))
    await setup()
    await click('转交')
    expect(mocks.error).toHaveBeenCalledWith('加载失败')
    expect(wrapper.find('select').exists()).toBe(false)
    expect(mocks.transferWorkSpec).not.toHaveBeenCalled()
  })
})
