import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { reactive } from 'vue'
import PrintResources from '../index.vue'
import { ArtworkVersionStatusEnum, FilmCopyStatusEnum } from '@/enums/product/film'
import { ScreenFrameStatusEnum, ScreenPlateStatusEnum } from '@/enums/engineering/resource'

const api=vi.hoisted(()=>({listPrintFrames:vi.fn(),getPrintFrame:vi.fn(),createPrintFrame:vi.fn(),stretchPrintFrame:vi.fn(),createPrintPlate:vi.fn(),getPrintPlate:vi.fn(),releasePrintPlate:vi.fn(),failPrintPlate:vi.fn(),washPrintPlate:vi.fn(),editPrintFrameRemark:vi.fn()}))
const artworks=vi.hoisted(()=>({detail:vi.fn()}))
const messages=vi.hoisted(()=>({warning:vi.fn(),success:vi.fn(),error:vi.fn()}))
const routing=vi.hoisted(()=>({current:undefined as any}))
vi.mock('@/api/engineering/resource',()=>({engineeringResourceApi:api}))
vi.mock('@/api/product/film',()=>({printingArtworkApi:artworks}))
vi.mock('vue-router',()=>({useRoute:()=>routing.current}))
vi.mock('element-plus',async original=>({...await original<typeof import('element-plus')>(),ElMessage:messages,ElMessageBox:{prompt:vi.fn()}}))
vi.mock('@/components/Selector/PrintResourceSelector.vue',()=>({default:{template:'<div/>'}}))
vi.mock('@/components/common-ui/TableActionColumn/index.vue',()=>({default:{template:'<div/>'}}))
const frame={frameId:5,frameNo:'WK-001',revision:2,status:ScreenFrameStatusEnum.READY.value,stretchId:7,plateId:null,stretches:[]}
const artwork={artworkId:11,artworkNo:'PW-001',name:'真实菲林',versions:[
 {versionId:21,versionNo:'V1',status:ArtworkVersionStatusEnum.RELEASED.value},
 {versionId:22,versionNo:'V2',status:ArtworkVersionStatusEnum.DRAFT.value},
],films:[
 {filmId:31,filmNo:'FL-001',versionId:21,status:FilmCopyStatusEnum.AVAILABLE.value},
 {filmId:32,filmNo:'OTHER-VERSION',versionId:22,status:FilmCopyStatusEnum.AVAILABLE.value},
 {filmId:33,filmNo:'ARCHIVED',versionId:21,status:FilmCopyStatusEnum.ARCHIVED.value},
]}
function render() {return shallowMount(PrintResources,{global:{renderStubDefaultSlot:true,directives:{hasPermi:()=>{},loading:()=>{}},stubs:{ElDialog:{template:'<aside/>'},ElDrawer:{template:'<aside/>'},ElTable:{template:'<table/>',props:['data']},ElButton:{template:'<button @click="$emit(\'click\')"><slot/></button>'}}}})}
beforeEach(()=>{
 vi.clearAllMocks();routing.current=reactive({query:{}})
 api.listPrintFrames.mockResolvedValue({data:{records:[],total:0}})
 api.getPrintFrame.mockResolvedValue({data:{...frame}})
 api.createPrintFrame.mockResolvedValue({data:{frameId:5}})
 api.stretchPrintFrame.mockResolvedValue({data:{frameId:5}})
 api.createPrintPlate.mockResolvedValue({data:{plateId:41}})
 api.getPrintPlate.mockResolvedValue({data:{plateId:41,plateNo:'PL-001',status:ScreenPlateStatusEnum.PENDING.value,revision:3}})
 api.releasePrintPlate.mockResolvedValue({data:{plateId:41}});api.failPrintPlate.mockResolvedValue({data:{plateId:41}})
 artworks.detail.mockImplementation(async()=>({data:artwork}))
})
describe('网版作业完整录入入口',()=>{
 it('空列表也能新增真实网框；失败保留输入及请求号，成功刷新定位新框',async()=>{
  const w=render();await flushPromises();await w.findAll('button').find(b=>b.text()==='新增网框')!.trigger('click')
  const s=(w.vm as any).$.setupState;expect(s.frameCreateVisible).toBe(true)
  await s.submitFrameCreate();expect(api.createPrintFrame).not.toHaveBeenCalled()
  Object.assign(s.frameForm,{frameNo:' WK-001 ',frameType:' 大框 ',location:' A区 '})
  api.createPrintFrame.mockRejectedValueOnce(new Error('框号已存在'));await s.submitFrameCreate()
  expect(s.frameCreateVisible).toBe(true);expect(s.frameForm.frameNo).toBe(' WK-001 ');expect(s.frameCreateError).toBe('框号已存在')
  const command=api.createPrintFrame.mock.calls[0][0];await s.submitFrameCreate()
  expect(api.createPrintFrame.mock.calls[1][0]).toEqual(command)
  expect(command).toMatchObject({frameNo:'WK-001',frameType:'大框',location:'A区',expectedRevision:0})
  expect(s.frameCreateVisible).toBe(false);expect(api.listPrintFrames).toHaveBeenLastCalledWith(expect.objectContaining({keyword:'WK-001',pageNum:1}));w.unmount()
 })
 it('新增提交中重复点击只发送一次',async()=>{
  const w=render();await flushPromises();const s=(w.vm as any).$.setupState;s.openFrameCreate();s.frameForm.frameNo='WK-001'
  let resolve!:(v:any)=>void;api.createPrintFrame.mockReturnValueOnce(new Promise(done=>{resolve=done}))
  const first=s.submitFrameCreate();await s.submitFrameCreate();expect(api.createPrintFrame).toHaveBeenCalledOnce()
  resolve({data:{frameId:5}});await first;w.unmount()
 })
 it('从菲林进入带入V1，制版只列同版本可用实物片，保存使用当前网框revision',async()=>{
  routing.current.query={artworkId:'11',versionId:'21'}
  const w=render();await flushPromises();const s=(w.vm as any).$.setupState
  expect(s.carriedVersion).toMatchObject({artworkId:11,versionId:21,versionNo:'V1'})
  await s.openPlate(frame)
  expect(s.picked.versionId).toBe(21);expect(s.filmOptions.map((f:any)=>f.filmId)).toEqual([31]);expect(s.plateForm.filmId).toBe(31)
  await s.submitPlate()
  expect(api.createPrintPlate).toHaveBeenCalledWith(expect.objectContaining({frameId:5,stretchId:7,versionId:21,filmId:31,expectedRevision:2}))
  expect(api.releasePrintPlate).not.toHaveBeenCalled();expect(messages.success).toHaveBeenCalledWith(expect.stringContaining('待另一位'));w.unmount()
 })
 it('未绷网不能打开制版，绷网命令带实际数据及合格结论',async()=>{
  const w=render();await flushPromises();const s=(w.vm as any).$.setupState
  api.getPrintFrame.mockResolvedValueOnce({data:{...frame,stretchId:null}});await s.openPlate(frame);expect(s.plateVisible).toBe(false)
  await s.openStretch(frame);Object.assign(s.stretchForm,{mesh:300,tensionNcm:20,material:'聚酯',qualified:true});await s.submitStretch()
  expect(api.stretchPrintFrame).toHaveBeenCalledWith(5,expect.objectContaining({expectedRevision:2,mesh:300,tensionNcm:20,material:'聚酯',qualified:true}));w.unmount()
 })
 it('草稿及非所属版本不被带入，版本切换迟到响应不能覆盖新选择',async()=>{
  routing.current.query={artworkId:'11',versionId:'22'}
  const w=render();await flushPromises();const s=(w.vm as any).$.setupState;expect(s.carriedVersion).toBe(null);expect(s.contextError).toContain('尚未发布')
  let resolve!:(v:any)=>void;artworks.detail.mockReturnValueOnce(new Promise(done=>{resolve=done}))
  const stale=s.onPickVersion({artworkId:11,versionId:21});await s.onPickVersion({artworkId:11,versionId:22});resolve({data:artwork});await stale
  expect(s.picked).toBe(null);expect(s.filmOptions).toEqual([]);w.unmount()
 })
 it('检验放行沿用原权限，缺检验记录不提交，403失败保留输入和请求号',async()=>{
  const w=render();await flushPromises();const s=(w.vm as any).$.setupState
  expect(s.frameActions.find((a:any)=>a.key==='inspect').permission).toBe('engineering:screen-resource:release')
  expect(s.frameActions.find((a:any)=>a.key==='inspect').visible({row:{plateId:null}})).toBe(false)
  await s.openInspection({plateId:41});await s.submitInspection();expect(api.releasePrintPlate).not.toHaveBeenCalled()
  s.inspectionForm.inspection=' 对位、线条检验合格 '
  api.releasePrintPlate.mockRejectedValueOnce(new Error('制版人不能放行本人制版'));await s.submitInspection()
  expect(s.inspectionVisible).toBe(true);expect(s.inspectionError).toContain('不能放行');expect(s.inspectionForm.inspection).toContain('对位')
  const first=api.releasePrintPlate.mock.calls[0][1];await s.submitInspection()
  expect(api.releasePrintPlate).toHaveBeenLastCalledWith(41,first);expect(first).toMatchObject({expectedRevision:3,inspection:'对位、线条检验合格'});expect(s.inspectionVisible).toBe(false);w.unmount()
 })
 it('不合格走fail接口，已放行版不重复检验',async()=>{
  const w=render();await flushPromises();const s=(w.vm as any).$.setupState;await s.openInspection({plateId:41})
  Object.assign(s.inspectionForm,{passed:false,inspection:'图案缺损'});await s.submitInspection()
  expect(api.failPrintPlate).toHaveBeenCalledWith(41,expect.objectContaining({inspection:'图案缺损'}));expect(api.releasePrintPlate).not.toHaveBeenCalled()
  api.getPrintPlate.mockResolvedValueOnce({data:{plateId:41,status:ScreenPlateStatusEnum.RELEASED.value,revision:4}})
  await s.openInspection({plateId:41});expect(s.canInspect).toBe(false);await s.submitInspection();expect(api.failPrintPlate).toHaveBeenCalledOnce();w.unmount()
 })
})
