vi.mock('element-plus/es/components/alert/style/css', () => ({}))
vi.mock('element-plus/es/components/base/style/css', () => ({}))
vi.mock('element-plus/es/components/button/style/css', () => ({}))
vi.mock('element-plus/es/components/card/style/css', () => ({}))
vi.mock('element-plus/es/components/checkbox/style/css', () => ({}))
vi.mock('element-plus/es/components/col/style/css', () => ({}))
vi.mock('element-plus/es/components/date-picker/style/css', () => ({}))
vi.mock('element-plus/es/components/dialog/style/css', () => ({}))
vi.mock('element-plus/es/components/icon/style/css', () => ({}))
vi.mock('element-plus/es/components/input/style/css', () => ({}))
vi.mock('element-plus/es/components/input-number/style/css', () => ({}))
vi.mock('element-plus/es/components/loading/style/css', () => ({}))
vi.mock('element-plus/es/components/message/style/css', () => ({}))
vi.mock('element-plus/es/components/message-box/style/css', () => ({}))
vi.mock('element-plus/es/components/option/style/css', () => ({}))
vi.mock('element-plus/es/components/pagination/style/css', () => ({}))
vi.mock('element-plus/es/components/result/style/css', () => ({}))
vi.mock('element-plus/es/components/row/style/css', () => ({}))
vi.mock('element-plus/es/components/select/style/css', () => ({}))
vi.mock('element-plus/es/components/table/style/css', () => ({}))
vi.mock('element-plus/es/components/table-column/style/css', () => ({}))
vi.mock('element-plus/es/components/tag/style/css', () => ({}))
vi.mock('element-plus/es/components/upload/style/css', () => ({}))
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { materialApi } from '@/api/inventory/material'
import request from '@/utils/request'
import StockImportDialog from '../StockImportDialog.vue'
import StockBatchImportDialog from '../StockBatchImportDialog.vue'

// 分类树收口后前端不再内置品类镜像（dev-20261024-006）；测试自备最小夹具。
const TEST_CATEGORIES = [
  { categoryId: 6, categoryCode: 'RAW_AUX_ELECTRONIC', categoryName: '电子元件', rootType: 'R' },
]


vi.mock('@/utils/request', () => ({ default: { get: vi.fn() } }))
vi.mock('@/api/inventory/material', () => ({ materialApi: { generateCode: vi.fn(), add: vi.fn() } }))
vi.mock('@/api/inventory/stock', () => ({ stockApi: { batchCheck: vi.fn() } }))
vi.mock('@/api/inventory/warehouse', () => ({ warehouseApi: {} }))
vi.mock('../MaterialFormDialog.vue', () => ({ default: { template: '<div />' } }))
vi.mock('element-plus', async (importOriginal) => {
  const original = await importOriginal<typeof import('element-plus')>()
  return { ...original, ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() } }
})

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(request.get).mockResolvedValue({ data: TEST_CATEGORIES })
  vi.mocked(materialApi.generateCode).mockResolvedValue({ data: 'TEST015' } as any)
  vi.mocked(materialApi.add).mockResolvedValue({ code: 200 } as any)
})

for (const [name, component, rowsKey, handler] of [
  ['快速导入', StockImportDialog, 'dataList', 'handleCreateAll'],
  ['批量校验导入', StockBatchImportDialog, 'errorRows', 'handleCreateMaterials'],
] as const) {
  describe(name, () => {
    function mountRow(row: Record<string, any>) {
      const wrapper = shallowMount(component as any, {
        props: { visible: true },
        global: { stubs: { ElDialog: { template: '<div><slot /></div>' } } },
      })
      const state = (wrapper.vm as any).$.setupState
      state[rowsKey] = [{ materialName: '无品类测试', checked: false, _checkResult: { errors: [{ type: 'NOT_FOUND' }] }, ...row }]
      return { wrapper, state }
    }
    it('缺少品类时拒绝建档，不分配编码和默认类型', async () => {
      const error = vi.spyOn(console, 'error').mockImplementation(() => {})
      const { wrapper, state } = mountRow({})
      await state[handler]()
      expect(materialApi.generateCode).not.toHaveBeenCalled()
      expect(materialApi.add).not.toHaveBeenCalled()
      expect(ElMessage.error).toHaveBeenCalledWith(expect.stringContaining('未归类'))
      wrapper.unmount()
      error.mockRestore()
    })
    it('显式品类先传分类ID生成编码，保存载荷不含旧类型', async () => {
      const { wrapper, state } = mountRow({ categoryId: 6 })
      await state[handler]()
      await nextTick()
      expect(materialApi.generateCode).toHaveBeenCalledWith(6)
      expect(materialApi.add).toHaveBeenCalledWith(expect.objectContaining({ categoryId: 6, materialCode: 'TEST015' }))
      expect(vi.mocked(materialApi.add).mock.calls[0][0]).not.toHaveProperty('materialType')
      wrapper.unmount()
    })
    it('服务端建档失败显示失败原因，不计入成功归类', async () => {
      const error = vi.spyOn(console, 'error').mockImplementation(() => {})
      vi.mocked(materialApi.add).mockRejectedValueOnce(new Error('品类不存在'))
      const { wrapper, state } = mountRow({ categoryId: 6 })
      await state[handler]()
      expect(ElMessage.error).toHaveBeenCalledWith(expect.stringContaining('品类不存在'))
      expect(state.fallbackCategoryCount).toBe(0)
      wrapper.unmount()
      error.mockRestore()
    })
  })
}
