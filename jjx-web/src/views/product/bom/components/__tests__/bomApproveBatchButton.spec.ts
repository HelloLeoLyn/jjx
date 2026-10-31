import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent } from 'vue'
import { flushPromises, mount } from '@vue/test-utils'
import BomApproveDialog from '../BomApproveDialog.vue'
import { BomStatusEnum, BomItemReviewStatusEnum } from '@/enums/product'

const api = vi.hoisted(() => ({
  getEngineeringBomInfo: vi.fn(),
  checkBomForApprove: vi.fn(),
  reviewBomItemsBatch: vi.fn(),
  reviewBomItem: vi.fn(),
  approveEngineeringBom: vi.fn(),
}))
vi.mock('@/api/product/bom', () => ({ productBomApi: api }))
vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal<typeof import('element-plus')>()),
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn(), info: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), prompt: vi.fn() },
}))

const ElDialogStub = defineComponent({
  name: 'ElDialog',
  props: ['modelValue', 'title', 'width', 'beforeClose'],
  template: '<div class="el-dialog-stub"><slot /><slot name="footer" /></div>',
})
const ElButtonStub = defineComponent({
  name: 'ElButton',
  props: ['disabled', 'loading', 'type'],
  emits: ['click'],
  template: '<button :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
})
const BomItemReviewTableStub = defineComponent({
  name: 'BomItemReviewTable',
  props: ['items', 'bomId', 'canReview', 'busyItemId', 'batchBusy', 'batchFailures'],
  emits: ['batch-approve', 'batch-link', 'review', 'link', 'unlink', 'create'],
  template: '<div class="review-table-stub" />',
})
const VoidStub = defineComponent({ name: 'VoidStub', props: ['modelValue', 'rows', 'submitting', 'detail', 'presetData', 'requireApproveRemark', 'maxLength', 'quickOpinions', 'disabled', 'fromBom'], emits: ['confirm', 'create', 'success'], template: '<div />' })

const bomDetail = (items: Array<Record<string, unknown>>) => ({
  data: {
    bomId: 9,
    bomCode: 'BOM-9',
    bomName: '测试BOM',
    productId: 1,
    productCode: 'P1',
    productName: '产品1',
    bomVersion: 'A',
    approveStatus: BomStatusEnum.REVIEWING.value,
    isCurrent: true,
    effectiveDate: '',
    expiryDate: '',
    remark: '',
    approveRemark: '',
    createTime: '',
    updateTime: '',
    createBy: '',
    updateBy: '',
    items,
  },
})

const mountDialog = () =>
  mount(BomApproveDialog, {
    props: { modelValue: true, bomId: 9 },
    global: {
      stubs: {
        ElDialog: ElDialogStub,
        ElButton: ElButtonStub,
        BomItemReviewTable: BomItemReviewTableStub,
        BomBasicInfo: VoidStub,
        ApprovalOpinionForm: VoidStub,
        BomMaterialBatchDialog: VoidStub,
        MaterialFormDialog: VoidStub,
      },
      directives: { hasPermi: { mounted: () => {} } },
    },
  })

const approveButton = (wrapper: ReturnType<typeof mountDialog>) =>
  wrapper.findAll('button').find((node) => node.text().includes('批准BOM'))

beforeEach(() => {
  api.getEngineeringBomInfo.mockReset()
  api.checkBomForApprove.mockReset()
  api.reviewBomItemsBatch.mockReset()
  api.reviewBomItem.mockReset()
  api.approveEngineeringBom.mockReset()
})

describe('BOM审核 批量通过后的批准按钮', () => {
  it('批量通过后回读体检结果，批准按钮恢复可点（不需关窗重开）', async () => {
    api.getEngineeringBomInfo.mockResolvedValue(
      bomDetail([{ itemId: 11, reviewStatus: BomItemReviewStatusEnum.PENDING.value }])
    )
    api.checkBomForApprove.mockResolvedValueOnce({ data: [{ itemId: 11, message: '未审核通过' }] })

    const wrapper = mountDialog()
    await flushPromises()
    expect(approveButton(wrapper)?.attributes('disabled')).toBeDefined()

    api.reviewBomItemsBatch.mockResolvedValue({ data: { successIds: [11], failed: [] } })
    api.getEngineeringBomInfo.mockResolvedValue(
      bomDetail([{ itemId: 11, reviewStatus: BomItemReviewStatusEnum.APPROVED.value }])
    )
    api.checkBomForApprove.mockResolvedValue({ data: [] })

    wrapper.findComponent(BomItemReviewTableStub).vm.$emit('batch-approve', [11])
    await flushPromises()

    expect(api.reviewBomItemsBatch).toHaveBeenCalledWith({ bomId: 9, itemIds: [11], action: 'APPROVE' })
    expect(api.checkBomForApprove).toHaveBeenCalledTimes(2)
    expect(approveButton(wrapper)?.attributes('disabled')).toBeUndefined()
  })

  it('批量通过后仍有待处理行时批准按钮保持禁用', async () => {
    api.getEngineeringBomInfo.mockResolvedValue(
      bomDetail([
        { itemId: 11, reviewStatus: BomItemReviewStatusEnum.PENDING.value },
        { itemId: 12, reviewStatus: BomItemReviewStatusEnum.PENDING.value },
      ])
    )
    api.checkBomForApprove.mockResolvedValueOnce({ data: [{ itemId: 11, message: '未审核通过' }] })

    const wrapper = mountDialog()
    await flushPromises()

    api.reviewBomItemsBatch.mockResolvedValue({ data: { successIds: [11], failed: [] } })
    api.checkBomForApprove.mockResolvedValue({ data: [{ itemId: 12, message: '未审核通过' }] })

    wrapper.findComponent(BomItemReviewTableStub).vm.$emit('batch-approve', [11])
    await flushPromises()

    expect(approveButton(wrapper)?.attributes('disabled')).toBeDefined()
  })

  it('体检接口失败不会把批准按钮永久禁用', async () => {
    api.getEngineeringBomInfo.mockResolvedValue(
      bomDetail([{ itemId: 11, reviewStatus: BomItemReviewStatusEnum.PENDING.value }])
    )
    api.checkBomForApprove.mockRejectedValue(new Error('体检接口超时'))

    const wrapper = mountDialog()
    await flushPromises()

    expect(approveButton(wrapper)?.attributes('disabled')).toBeUndefined()
  })
})
