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
import { MATERIAL_CATEGORY_SEED } from '../materialMasterFields'

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
  vi.mocked(request.get).mockResolvedValue({ data: MATERIAL_CATEGORY_SEED })
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
    it.each(['R', 'A'])('无品类 %s 行提交默认品类并显示结果提示', async (type) => {
      const { wrapper, state } = mountRow({ material_type: type })
      await state[handler]()
      await nextTick()
      expect(materialApi.add).toHaveBeenCalledWith(expect.objectContaining({ categoryId: type === 'A' ? 11 : 10, materialType: type }))
      expect(ElMessage.success).toHaveBeenCalledWith(expect.stringContaining('未匹配品类已归入其他'))
      expect(wrapper.find('el-alert-stub').attributes('title')).toContain('未匹配品类已归入其他 1 条')
      wrapper.unmount()
    })
    it('服务端建档失败显示失败原因，不计入成功归类', async () => {
      const error = vi.spyOn(console, 'error').mockImplementation(() => {})
      vi.mocked(materialApi.add).mockRejectedValueOnce(new Error('品类不存在'))
      const { wrapper, state } = mountRow({})
      await state[handler]()
      expect(ElMessage.error).toHaveBeenCalledWith(expect.stringContaining('品类不存在'))
      expect(state.fallbackCategoryCount).toBe(0)
      wrapper.unmount()
      error.mockRestore()
    })
  })
}
