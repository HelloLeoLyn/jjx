import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, shallowMount } from '@vue/test-utils'
import { ArtworkVersionStatusEnum, FilmRequestProgressEnum } from '@/enums/product/film'
import FilmManagement from '../components/FilmManagement.vue'
import { newFilmRequestId, useFilmManagement } from '../composables/useFilmManagement'
const api=vi.hoisted(()=>({list:vi.fn(),detail:vi.fn(),listRequests:vi.fn(),applyFilmRequest:vi.fn(),receiveFilmRequest:vi.fn(),create:vi.fn(),createWithInitialVersion:vi.fn(),edit:vi.fn(),createVersion:vi.fn(),editVersion:vi.fn(),submit:vi.fn(),approve:vi.fn(),reject:vi.fn(),release:vi.fn(),activate:vi.fn(),disable:vi.fn(),impact:vi.fn(),archiveFilm:vi.fn(),destroyFilm:vi.fn()}))
vi.mock('@/api/product/film',()=>({printingArtworkApi:api}))
vi.mock('@/api/product',()=>({listProductPage:vi.fn().mockResolvedValue({data:{records:[]}}),searchProduct:vi.fn().mockResolvedValue({data:[]}),getProductInfo:vi.fn()}))
vi.mock('@/api/sales/sampleOrder',()=>({sampleOrderApi:{page:vi.fn().mockResolvedValue({data:{records:[]}})}}))
vi.mock('@/api/engineering/printingSearch',()=>({printingSearchApi:{search:vi.fn()}}))
vi.mock('vue-router',()=>({useRouter:()=>({push:vi.fn()})}))
vi.mock('@/components/attachment/AttachmentCenter.vue',()=>({default:{template:'<div/>'}}))
vi.mock('@/components/product/productFilePreviewUtils',()=>({fileKind:()=> 'other',renderFile:vi.fn().mockResolvedValue([]),downloadFile:vi.fn()}))
const messages=vi.hoisted(()=>({success:vi.fn(),warning:vi.fn(),error:vi.fn()}))
vi.mock('element-plus',async original=>({...await original<typeof import('element-plus')>(),ElMessage:messages,ElMessageBox:{confirm:vi.fn().mockResolvedValue('confirm'),prompt:vi.fn().mockResolvedValue({value:'核对后处置'})}}))
const master={artworkId:1,artworkNo:'PW-001',name:'主数据',family:'面板',versionCount:1,defaultVersionId:12,defaultVersionNo:'V2',copyCount:0,pendingFilmCount:2}
const detail=(id=1)=>({artworkId:id,artworkNo:`PW-00${id}`,name:`菲林${id}`,family:'面板',revision:3,productIds:[10],productsText:'P-10 产品10',defaultVersionId:id===1?12:21,versions:[{versionId:id===1?12:21,versionNo:'V2',status:ArtworkVersionStatusEnum.RELEASED.value,revision:4}],films:[]})
const request={requestId:7,requestNo:'CP-007',versionId:12,requestedQuantity:2,receivedQuantity:0,remainingQuantity:2,progress:FilmRequestProgressEnum.PENDING.value,material:'PET',thicknessMm:0.125,size:'A4',dueDate:'2026-12-01',revision:0}
function render(showForm=false) {return shallowMount(FilmManagement,{global:{renderStubDefaultSlot:true,directives:{hasPermi:()=>{},loading:()=>{}},stubs:{ElCard:{template:'<section><slot/></section>'},ElTable:{template:'<table/>',props:['data']},ElDrawer:{template:'<aside/>'},ElDialog:{template:showForm ? '<aside><slot/><slot name="footer"/></aside>' : '<aside/>'},ElButton:{template:'<button @click="$emit(\'click\')"><slot/></button>'}}}})}
beforeEach(()=>{
 vi.clearAllMocks();api.list.mockResolvedValue({data:{records:[master,{...master,artworkId:5,artworkNo:'PW-005',versionCount:0,defaultVersionId:null,defaultVersionNo:null,copyCount:0}],total:2}})
 api.detail.mockImplementation(async(id:number)=>({data:detail(id)}));api.listRequests.mockResolvedValue({data:[{...request}]});api.applyFilmRequest.mockResolvedValue({data:{requestId:7}});api.receiveFilmRequest.mockResolvedValue({data:{requestId:7}});api.createWithInitialVersion.mockResolvedValue({data:{artworkId:8,versionId:81}})
})
afterEach(()=>vi.unstubAllGlobals())
describe('正式菲林主数据加载与归属',()=>{
 it('主列表使用菲林档案接口，无版本主档也保留',async()=>{const model=useFilmManagement();await model.loadRows();expect(api.list).toHaveBeenCalledOnce();expect(model.rows.value.map(row=>row.artworkNo)).toEqual(['PW-001','PW-005']);expect(model.page.total).toBe(2);expect(api.listRequests).not.toHaveBeenCalled()})
 it('详情和申请的迟到响应不能串入另一条菲林',async()=>{
  const model=useFilmManagement();let resolveDetail!:(value:any)=>void;api.detail.mockReturnValueOnce(new Promise(resolve=>{resolveDetail=resolve}))
  const old=model.loadDetail(1);await model.loadDetail(2);resolveDetail({data:detail(1)});await old;expect(model.detail.value?.artworkId).toBe(2)
  await model.loadDetail(1);let resolveRequests!:(value:any)=>void;api.listRequests.mockReturnValueOnce(new Promise(resolve=>{resolveRequests=resolve}))
  const late=model.loadRequests(1);await model.loadDetail(2);api.listRequests.mockResolvedValueOnce({data:[]});await model.loadRequests(2);resolveRequests({data:[request]});await late;expect(model.requests.value).toEqual([]);expect(model.selectedVersionId.value).toBe(21)
 })
 it('接口错误明确显示，不伪装成正常空列表',async()=>{const model=useFilmManagement();api.list.mockRejectedValueOnce(new Error('列表连接失败'));await model.loadRows();expect(model.listError.value).toBe('列表连接失败');await model.loadDetail(1);api.listRequests.mockRejectedValueOnce(new Error('请先完成迁移399'));await model.loadRequests();expect(model.requestsError.value).toContain('迁移399')})
})
describe('正式页面写入真实API载荷',()=>{
 it('普通HTTP没有randomUUID时，新增按钮仍打开弹窗并可保存唯一请求号',async()=>{
  const getRandomValues=globalThis.crypto.getRandomValues.bind(globalThis.crypto)
  vi.stubGlobal('crypto',{getRandomValues})
  const ids=Array.from({length:100},()=>newFilmRequestId())
  expect(new Set(ids).size).toBe(ids.length)
  expect(ids.every(id=>/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/.test(id))).toBe(true)
  const w=render();await flushPromises()
  await w.findAll('button').find(button=>button.text().includes('新增菲林'))!.trigger('click');await flushPromises()
  const s=(w.vm as any).$.setupState
  expect(s.masterVisible).toBe(true);expect(api.create).not.toHaveBeenCalled()
  Object.assign(s.masterDraft,{no:'PW-HTTP',name:'HTTP主档',productIds:[10]});await s.saveMaster()
  expect(api.createWithInitialVersion).toHaveBeenCalledWith(expect.objectContaining({artworkNo:'PW-HTTP',requestId:expect.stringMatching(/^[0-9a-f-]{36}$/)}))
  w.unmount()
 })
 it('申请只提交申请接口，版本与revision来自该菲林详情',async()=>{
  const w=render();await flushPromises();const s=(w.vm as any).$.setupState;await s.openRequest(master);Object.assign(s.requestDraft,{quantity:2,size:'300x200mm',dueDate:'2026-12-01'});await s.saveRequest()
  expect(api.applyFilmRequest).toHaveBeenCalledWith(12,expect.objectContaining({expectedRevision:4,quantity:2,size:'300x200mm'}));expect(api.receiveFilmRequest).not.toHaveBeenCalled();expect(s.requestVisible).toBe(false);w.unmount()
 })
 it('写请求等待期间重复点击只发送一次',async()=>{
  const w=render();await flushPromises();const s=(w.vm as any).$.setupState;await s.openRequest(master);Object.assign(s.requestDraft,{size:'A4',dueDate:'2026-12-01'})
  let resolve!:(value:any)=>void;api.applyFilmRequest.mockReturnValueOnce(new Promise(done=>{resolve=done}));const first=s.saveRequest();await s.saveRequest();expect(api.applyFilmRequest).toHaveBeenCalledOnce();resolve({data:{requestId:7}});await first;w.unmount()
 })
 it('回片失败保留输入，固定申请ID与申请revision且不发送版本替换',async()=>{
  const w=render();await flushPromises();const s=(w.vm as any).$.setupState;await s.openCopies(master);s.openReceipt({...request,revision:4});Object.assign(s.receiptRows[0],{filmNo:' FL-009 ',location:' A架 '});api.receiveFilmRequest.mockRejectedValueOnce(new Error('申请已有新回片'))
  await s.saveReceipt();expect(api.receiveFilmRequest).toHaveBeenCalledWith(7,expect.objectContaining({expectedRevision:4,copies:[{filmNo:'FL-009',location:'A架'}]}));expect(api.receiveFilmRequest.mock.calls[0][1]).not.toHaveProperty('versionId');expect(s.receiptVisible).toBe(true);expect(s.receiptRows[0].filmNo).toBe(' FL-009 ');w.unmount()
 })
 it('新增菲林保留产品归属，成功后刷新主档列表',async()=>{
  const w=render();await flushPromises();const s=(w.vm as any).$.setupState;await s.openMaster();Object.assign(s.masterDraft,{no:'PW-NEW',name:'新增主档',productIds:[10]});await s.saveMaster();expect(api.createWithInitialVersion).toHaveBeenCalledWith(expect.objectContaining({artworkNo:'PW-NEW',name:'新增主档',productIds:[10],expectedRevision:0}));expect(s.masterVisible).toBe(false);w.unmount()
 })
 it('首次弹窗直接提供上传入口，使用独立草稿且不提前绑定',async()=>{
  const w=render(true);await flushPromises();const s=(w.vm as any).$.setupState;await s.openMaster();await flushPromises()
  const uploader=w.findComponent({name:'AttachmentCenter'})
  expect(uploader.exists()).toBe(true)
  expect(uploader.attributes('source-type')).toBe('print_artwork_version')
  expect(uploader.attributes('auto-bind')).toBe('false')
  const key=s.masterDraftKey;expect(key).toMatch(/^film-initial-/)
  expect(w.text()).toContain('保存时自动建立初始草稿版本')
  await s.openMaster();expect(s.masterDraftKey).not.toBe(key);w.unmount()
 })
 it('一次提交档案与上传图纸，失败保留输入和文件，重试成功后才清理草稿',async()=>{
  const w=render();await flushPromises();const s=(w.vm as any).$.setupState;await s.openMaster()
  Object.assign(s.masterDraft,{no:'PW-ONE',name:'一次录入',productIds:[10],technicalSpec:' 对位要求 '})
  const clearPending=vi.fn();const files=[{attachmentId:99,documentType:'FILM'}]
  s.masterDrawingCenter={uploading:false,getPendingEntries:()=>files,clearPending}
  api.createWithInitialVersion.mockRejectedValueOnce(new Error('图纸绑定失败'))
  await s.saveMaster()
  expect(s.masterVisible).toBe(true);expect(s.masterDraft.name).toBe('一次录入');expect(clearPending).not.toHaveBeenCalled()
  const first=api.createWithInitialVersion.mock.calls[0][0]
  expect(first).toEqual(expect.objectContaining({artworkNo:'PW-ONE',drawingFileId:99,drawingDocumentType:'FILM',technicalSpec:'对位要求',expectedRevision:0}))
  await s.saveMaster()
  expect(api.createWithInitialVersion.mock.calls[1][0]).toEqual(first)
  expect(clearPending).toHaveBeenCalledOnce();expect(s.masterVisible).toBe(false)
  expect(api.create).not.toHaveBeenCalled();expect(api.createVersion).not.toHaveBeenCalled();w.unmount()
 })
 it('上传中禁止保存，多份图纸不能静默丢弃，移除图纸后不发送旧附件ID',async()=>{
  const w=render();await flushPromises();const s=(w.vm as any).$.setupState;await s.openMaster()
  Object.assign(s.masterDraft,{name:'上传检查',productIds:[10]})
  const uploader={uploading:true,getPendingEntries:()=>[{attachmentId:91},{attachmentId:92}],clearPending:vi.fn()}
  s.masterDrawingCenter=uploader;await s.saveMaster();expect(api.createWithInitialVersion).not.toHaveBeenCalled()
  s.masterDrawingCenter.uploading=false;await s.saveMaster();expect(api.createWithInitialVersion).not.toHaveBeenCalled()
  expect(messages.warning).toHaveBeenCalledWith(expect.stringContaining('只保存一份图纸'))
  s.masterDrawingCenter.getPendingEntries=()=>[];await s.saveMaster()
  expect(api.createWithInitialVersion).toHaveBeenCalledWith(expect.objectContaining({drawingFileId:undefined}));w.unmount()
 })
 it('首次保存中重复点击不重复发送，编辑已有菲林不生成新版本',async()=>{
  const w=render();await flushPromises();const s=(w.vm as any).$.setupState;await s.openMaster()
  Object.assign(s.masterDraft,{name:'重复提交',productIds:[10]})
  let resolve!:(value:any)=>void;api.createWithInitialVersion.mockReturnValueOnce(new Promise(done=>{resolve=done}))
  const first=s.saveMaster();await s.saveMaster();expect(api.createWithInitialVersion).toHaveBeenCalledOnce()
  resolve({data:{artworkId:8,versionId:81}});await first
  api.edit.mockResolvedValueOnce({data:{artworkId:1}})
  await s.openMaster(master);await s.saveMaster()
  expect(api.edit).toHaveBeenCalledWith(1,expect.objectContaining({expectedRevision:3}))
  expect(api.edit.mock.calls[0][1]).not.toHaveProperty('drawingFileId')
  expect(api.createWithInitialVersion).toHaveBeenCalledOnce();expect(api.createVersion).not.toHaveBeenCalled();w.unmount()
 })
 it('版本图纸保存与封存继续使用原有受控接口',async()=>{
  const w=render();await flushPromises();const s=(w.vm as any).$.setupState;await s.openVersions(master);s.openVersionForm({versionId:12,versionNo:'V2',status:ArtworkVersionStatusEnum.DRAFT.value,revision:6,drawingFileId:100});s.drawingUploaded({attachmentId:101});s.versionDrawingCenter={uploading:false,getPendingEntries:()=>[{attachmentId:101,documentType:'FILM'}],clearPending:vi.fn()};api.editVersion.mockResolvedValueOnce({data:{versionId:12}});await s.saveVersion()
  expect(api.editVersion).toHaveBeenCalledWith(12,expect.objectContaining({expectedRevision:6,drawingFileId:101,drawingDocumentType:'FILM'}));api.archiveFilm.mockResolvedValueOnce({data:{}});await s.disposeCopy({filmId:9,filmNo:'FL-9',versionId:12,revision:3},'archive');expect(api.archiveFilm).toHaveBeenCalledWith(9,expect.objectContaining({expectedRevision:3,reason:'核对后处置'}));w.unmount()
 })
 it('新增图纸未选类型时拦截，选好后才传递二级类型',async()=>{
  const w=render();await flushPromises();const s=(w.vm as any).$.setupState;await s.openMaster()
  Object.assign(s.masterDraft,{name:'类型必选',productIds:[10]})
  const files:any[]=[{attachmentId:99,documentType:null}]
  s.masterDrawingCenter={uploading:false,getPendingEntries:()=>files,clearPending:vi.fn()}
  await s.saveMaster();expect(api.createWithInitialVersion).not.toHaveBeenCalled()
  expect(messages.warning).toHaveBeenCalledWith('请选择图纸资料类型（二级）')
  files[0].documentType='FILM';await s.saveMaster()
  expect(api.createWithInitialVersion).toHaveBeenCalledWith(expect.objectContaining({drawingFileId:99,drawingDocumentType:'FILM'}));w.unmount()
 })
 it('修改版本图纸类型失败保留待绑定队列，成功后才清理',async()=>{
  const w=render();await flushPromises();const s=(w.vm as any).$.setupState;await s.openVersions(master)
  s.openVersionForm({versionId:12,versionNo:'V2',status:ArtworkVersionStatusEnum.DRAFT.value,revision:6,drawingFileId:100})
  const files:any[]=[{attachmentId:101,documentType:null}];const clearPending=vi.fn()
  s.versionDrawingCenter={uploading:false,getPendingEntries:()=>files,clearPending}
  s.drawingUploaded({attachmentId:101});await s.saveVersion();expect(api.editVersion).not.toHaveBeenCalled()
  files[0].documentType='FILM';api.editVersion.mockRejectedValueOnce(new Error('保存失败'));await s.saveVersion()
  expect(s.versionFormVisible).toBe(true);expect(clearPending).not.toHaveBeenCalled()
  expect(api.editVersion).toHaveBeenCalledWith(12,expect.objectContaining({drawingFileId:101,drawingDocumentType:'FILM'}))
  api.editVersion.mockResolvedValueOnce({data:{versionId:12}});await s.saveVersion();expect(clearPending).toHaveBeenCalledOnce();w.unmount()
 })
 it('修改版本上传中不保存，移除待绑定图纸恢复原图纸，不发送旧的临时ID',async()=>{
  const w=render();await flushPromises();const s=(w.vm as any).$.setupState;await s.openVersions(master)
  s.openVersionForm({versionId:12,versionNo:'V2',status:ArtworkVersionStatusEnum.DRAFT.value,revision:6,drawingFileId:100})
  s.drawingUploaded({attachmentId:101})
  s.versionDrawingCenter={uploading:true,getPendingEntries:()=>[],clearPending:vi.fn()}
  await s.saveVersion();expect(api.editVersion).not.toHaveBeenCalled()
  s.versionDrawingCenter.uploading=false;api.editVersion.mockResolvedValueOnce({data:{versionId:12}});await s.saveVersion()
  expect(api.editVersion).toHaveBeenCalledWith(12,expect.objectContaining({drawingFileId:100,drawingDocumentType:undefined}));w.unmount()
 })
})
