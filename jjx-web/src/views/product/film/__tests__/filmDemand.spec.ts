import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import Page from '../index.vue'
const api = vi.hoisted(() => ({ listFilms: vi.fn(), list: vi.fn(), detail: vi.fn(), requestFilms: vi.fn(), receiveFilm: vi.fn() }))
vi.mock('@/api/product/film', () => ({ printingArtworkApi: api }))
vi.mock('@/api/product', () => ({ listProductPage: vi.fn().mockResolvedValue({ data: { records: [] } }), searchProduct: vi.fn() }))
vi.mock('@/api/engineering/printingSearch', () => ({ printingSearchApi: { search: vi.fn() } }))
vi.mock('@/api/system/attachment', () => ({ attachmentApi: { downloadUrl: vi.fn() } }))
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }))
vi.mock('@/components/attachment/AttachmentCenter.vue', () => ({ default: { template: '<div/>' } }))
vi.mock('@/components/Selector/PrintResourceSelector.vue', () => ({ default: { template: '<div/>' } }))
vi.mock('element-plus', async original => ({ ...(await original<typeof import('element-plus')>()), ElMessage: { success: vi.fn(), warning: vi.fn() } }))
function render() {
  return shallowMount(Page, { global: { renderStubDefaultSlot: true,
    directives: { hasPermi: () => {}, loading: () => {} }, stubs: {
      ElCard: { template: '<section><slot/></section>' }, ElTable: { template: '<table/>', props: ['data'] },
      ElDrawer: { template: '<aside/>' }, ElDialog: { template: '<aside/>' },
      ElButton: { template: '<button @click="$emit(\'click\')"><slot/></button>' },
    } } })
}
beforeEach(() => {
  vi.clearAllMocks()
  api.listFilms.mockResolvedValue({ data: { records: [], total: 0 } })
  api.requestFilms.mockResolvedValue({ data: {} })
  api.receiveFilm.mockResolvedValue({ data: {} })
  api.detail.mockResolvedValue({ data: { versions: [{ versionId: 7, status: 'RELEASED', revision: 2 }] } })
})
describe('菲林片单列表', () => {
  it('打开即查菲林片，新建需求不要求先打开设计详情', async () => {
    const w=render(); await flushPromises()
    expect(api.listFilms).toHaveBeenCalledOnce(); expect(api.list).not.toHaveBeenCalled()
    expect(w.findAll('table')).toHaveLength(1)
    await w.findAll('button').find(b=>b.text()==='新建需求')!.trigger('click')
    const s=(w.vm as any).$.setupState
    expect(s.filmDialogVisible).toBe(true)
    await s.pickDemandVersion({ artworkId: 1, versionId: 7 })
    Object.assign(s.filmForm,{ material:'PET',thicknessMm:0.125,size:'A4',quantity:2,dueDate:'2026-12-01' })
    s.filmFormRef={ validate:()=>Promise.resolve(true) }
    await s.saveFilm()
    expect(api.requestFilms).toHaveBeenCalledWith(7,expect.objectContaining({ quantity:2,dueDate:'2026-12-01',expectedRevision:2 }))
    w.unmount()
  })
  it('回片冲突保留输入和原revision', async () => {
    const w=render(); await flushPromises(); const s=(w.vm as any).$.setupState
    s.openFilmDialog({ filmId:9,versionId:7,revision:3,filmNo:'F9',material:'PET',thicknessMm:0.125,size:'A4' })
    s.filmForm.location='A架';s.filmFormRef={ validate:()=>Promise.resolve(true) }
    api.receiveFilm.mockRejectedValueOnce(new Error('冲突'));await s.saveFilm()
    expect(api.receiveFilm).toHaveBeenCalledWith(9,expect.objectContaining({ expectedRevision:3,location:'A架' }))
    expect(s.filmDialogVisible).toBe(true);expect(s.filmForm.location).toBe('A架');w.unmount()
  })
  it('切换版本丢弃迟到详情', async () => {
    const w=render();await flushPromises();const s=(w.vm as any).$.setupState;s.openFilmDialog()
    let resolveOld!: (value:any)=>void
    api.detail.mockReturnValueOnce(new Promise(resolve=>{resolveOld=resolve}))
    const old=s.pickDemandVersion({artworkId:1,versionId:5})
    await s.pickDemandVersion({artworkId:2,versionId:7})
    resolveOld({data:{versions:[{versionId:5,revision:99,status:'RELEASED'}]}});await old
    expect(s.filmForm.versionId).toBe(7);expect(s.filmForm.revision).toBe(2);w.unmount()
  })
  it('清空版本时取消绑定并作废迟到查询，不发送需求', async () => {
    const w=render();await flushPromises();const s=(w.vm as any).$.setupState;s.openFilmDialog()
    let resolveOld!: (value:any)=>void
    api.detail.mockReturnValueOnce(new Promise(resolve=>{resolveOld=resolve}))
    const old=s.pickDemandVersion({artworkId:1,versionId:7})
    await s.pickDemandVersion(null)
    resolveOld({data:{versions:[{versionId:7,revision:2,status:'RELEASED'}]}});await old
    expect(s.filmForm.versionId).toBeUndefined()
    s.filmFormRef={validate:()=>Promise.resolve(true)};await s.saveFilm()
    expect(api.requestFilms).not.toHaveBeenCalled();w.unmount()
  })

})
