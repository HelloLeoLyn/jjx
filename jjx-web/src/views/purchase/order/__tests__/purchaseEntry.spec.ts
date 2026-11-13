import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, shallowMount } from '@vue/test-utils'
import { nextTick } from 'vue'
import ElementPlus from 'element-plus'
import OrderFormDialog from '../components/OrderFormDialog.vue'
import PurchaseItemTable from '../components/PurchaseItemTable.vue'
import PurchaseItemsPrintTable from '../components/PurchaseItemsPrintTable.vue'

const api = vi.hoisted(() => ({ addOrder: vi.fn(),updateOrder: vi.fn(),generateOrderNo: vi.fn(),getOrder: vi.fn(),previewOrderPricing: vi.fn() }))
const defaults = vi.hoisted(() => ({ getPurchaseMaterialDefault: vi.fn(),getPurchaseUnitOptions: vi.fn(),savePurchaseMaterialDefault: vi.fn() }))
vi.mock('@/api/purchase/order', () => api)
vi.mock('@/api/purchase/orderEntry', async original => ({ ...(await original<typeof import('@/api/purchase/orderEntry')>()),...defaults }))
vi.mock('@/api/inventory/material', () => ({ materialApi: { search: vi.fn().mockResolvedValue({code:200,data:{records:[]}}) } }))
const units = [{value:'㎡',label:'平方米'},{value:'ROLL',label:'卷'},{value:'PCS',label:'件'},{value:'KG',label:'千克'}]
const material = { materialId:354,materialCode:'RM000350',materialName:'PET188',supplierMaterialName:'EBA180',specification:'1220*100000mm',unit:'㎡',widthMm:1220,lengthMm:100000 }
const row = () => ({materialId:354,materialCode:'RM000350',materialName:'PET188',supplierMaterialName:'EBA180',unit:'㎡',purchaseUnit:'ROLL',priceUnit:'㎡',purchaseQuantity:3,quantity:366,unitPrice:85,taxRate:13,amount:31110,priceQuantity:366,purchaseRemark:'切片985PCS，余卷交回'})
const wrappers: any[]=[]
const form = () => {const w=shallowMount(OrderFormDialog,{props:{visible:false}});wrappers.push(w);return {w,vm:w.vm as any}}
beforeEach(() => {
  Object.values(api).forEach(f=>f.mockReset());Object.values(defaults).forEach(f=>f.mockReset())
  api.addOrder.mockResolvedValue({});api.updateOrder.mockResolvedValue({});api.generateOrderNo.mockResolvedValue({data:'PO-TEST'})
  api.previewOrderPricing.mockResolvedValue({data:{orderAmount:31110,orderTax:4044.3,orderTotalAmount:35154.3,items:[{...row(),taxAmount:4044.3}]}})
  defaults.getPurchaseUnitOptions.mockResolvedValue({data:units})
  defaults.getPurchaseMaterialDefault.mockResolvedValue({data:{configured:true,unit:'㎡',purchaseUnit:'ROLL',priceUnit:'㎡',rollWidthMm:1220,rollLengthM:100,pieceWidthMm:1220,pieceLengthMm:100000,allowedUnits:['㎡','ROLL']}})
})
afterEach(()=>{wrappers.splice(0).forEach(w=>w.unmount());vi.restoreAllMocks()})
describe('精简采购主表与后端换算',()=>{
  it('首次直接打开弹窗也初始化日期、单位和编号',async()=>{
    const w=shallowMount(OrderFormDialog,{props:{visible:true}});wrappers.push(w)
    await flushPromises()
    expect((w.vm as any).form.orderDate).toMatch(/^\d{4}-\d{2}-\d{2}$/)
    expect((w.vm as any).form.orderNo).toBe('PO-TEST')
    expect((w.vm as any).loadFailed).toBe(false)
  })
  it('选择供应商物料自动带采购约定，不依据长宽猜卷材',async()=>{
    const {vm}=form();vm.form.supplierId='16';vm.supplierMaterials=[material]
    vm.handleMaterialChange(354,0);await flushPromises()
    expect(defaults.getPurchaseMaterialDefault).toHaveBeenCalledWith(16,354)
    expect(vm.form.items[0]).toMatchObject({purchaseUnit:'ROLL',priceUnit:'㎡',rollLengthM:100,rollWidthMm:1220,defaultConfigured:true})
  })
  it('采购约定读取期间的价格事件不会取消自动带出单位',async()=>{
    let resolve!:(v:any)=>void;defaults.getPurchaseMaterialDefault.mockReturnValueOnce(new Promise(done=>resolve=done))
    const {vm}=form();vm.form.supplierId='16';vm.supplierMaterials=[material];vm.handleMaterialChange(354,0)
    vm.form.items[0].unitPrice=85;vm.touchPricing(0);expect(vm.form.items[0].defaultLoading).toBe(true)
    resolve({data:{configured:true,unit:'㎡',purchaseUnit:'ROLL',priceUnit:'㎡',rollLengthM:100,rollWidthMm:1220}});await flushPromises()
    expect(vm.form.items[0]).toMatchObject({purchaseUnit:'ROLL',unitPrice:85,defaultLoading:false})
  })
  it('迟到的采购约定不能覆盖已经重新选择的物料',async()=>{
    let resolve!:(v:any)=>void;defaults.getPurchaseMaterialDefault.mockReturnValueOnce(new Promise(done=>resolve=done))
    const {vm}=form();vm.form.supplierId='16';vm.supplierMaterials=[material,{...material,materialId:110,materialCode:'AUX000106'}]
    vm.handleMaterialChange(354,0);vm.handleMaterialChange(110,0);await flushPromises()
    resolve({data:{configured:true,unit:'PCS',purchaseUnit:'PCS',priceUnit:'PCS'}});await flushPromises()
    expect(vm.form.items[0]).toMatchObject({materialId:110,purchaseUnit:'ROLL',unit:'㎡'})
  })
  it('采购数量和计价数量分开，计价数量不作为客户端权威提交',async()=>{
    const {vm}=form();vm.form.supplierId='16';vm.form.items=[row()];vm.formRef={validate:vi.fn().mockResolvedValue(true)}
    await vm.handleSubmit()
    const sent=api.addOrder.mock.calls[0][0]
    expect(sent.items[0]).toMatchObject({purchaseQuantity:3,quantity:366,purchaseUnit:'ROLL',priceUnit:'㎡',purchaseRemark:'切片985PCS，余卷交回'})
    expect(sent.items[0]).not.toHaveProperty('priceQuantity')
    expect(sent.orderTax).toBe(4044.3)
  })
  it('换算失败清掉过期计价结果并保留输入，不允许保存',async()=>{
    api.previewOrderPricing.mockRejectedValue(new Error('请补齐每片长度'))
    const {vm}=form();vm.form.items=[row()];vm.formRef={validate:vi.fn().mockResolvedValue(true)}
    await vm.handleSubmit();expect(api.addOrder).not.toHaveBeenCalled()
    expect(vm.form.items[0]).toMatchObject({purchaseQuantity:3,unitPrice:85,priceQuantity:null,pricingError:'请补齐每片长度'})
  })
  it('删除行后，迟到的预览不覆盖其他行',async()=>{
    let resolve!:(v:any)=>void;api.previewOrderPricing.mockReturnValueOnce(new Promise(done=>resolve=done))
    const {vm}=form();const first=row();vm.form.items=[first,{...row(),materialId:110,materialCode:'AUX000106',amount:99}]
    const pending=vm.calculateRowTotal(0);vm.removeItem(0);resolve({data:{items:[{...row(),amount:999}]}});await pending
    expect(vm.form.items[0].amount).toBe(99)
  })
  it('合计使用整单后端结果，不把逐行舍入税额相加',async()=>{
    api.previewOrderPricing.mockImplementation(async (body:any)=>({data:{orderAmount:body.items.length===2?0.1:0.05,orderTax:0.01,orderTotalAmount:body.items.length===2?0.11:0.06,items:body.items.map((i:any)=>({...i,priceQuantity:1,quantity:1,amount:0.05,taxAmount:0.0065}))}}))
    const {vm}=form();vm.form.items=[{...row(),unitPrice:0.05},{...row(),materialId:110,materialCode:'AUX000106',unitPrice:0.05}]
    await Promise.all([vm.calculateRowTotal(0),vm.calculateRowTotal(1)]);await flushPromises()
    expect(vm.totalAmount).toBe(0.1);expect(vm.totalTax).toBe(0.01);expect(vm.totalWithTax).toBe(0.11)
  })
  it('默认约定读取失败不会静默按基本单位保存，重试保留数量单价',async()=>{
    defaults.getPurchaseMaterialDefault.mockRejectedValueOnce(new Error('network'))
    const {vm}=form();vm.form.supplierId='16';vm.supplierMaterials=[material];vm.formRef={validate:vi.fn().mockResolvedValue(true)}
    vm.handleMaterialChange(354,0);await flushPromises()
    vm.form.items[0].purchaseQuantity=3;vm.form.items[0].unitPrice=85
    await vm.handleSubmit();expect(api.addOrder).not.toHaveBeenCalled();expect(vm.form.items[0].defaultError).toBe(true)
    vm.retryDefaults(0);await flushPromises()
    expect(vm.form.items[0]).toMatchObject({defaultError:false,purchaseQuantity:3,unitPrice:85,purchaseUnit:'ROLL'})
  })
  it('关闭后迟到的价格响应不修改已关闭草稿',async()=>{
    let resolve!:(v:any)=>void;api.previewOrderPricing.mockReturnValueOnce(new Promise(done=>resolve=done))
    const {vm}=form();vm.form.items=[row()];const pending=vm.calculateRowTotal(0)
    vm.handleClose();resolve({data:{items:[{...row(),amount:999}]}});await pending
    expect(vm.form.items[0].amount).not.toBe(999)
  })
  it('保存默认单位是独立明确动作，不随保存订单自动改采购约定',async()=>{
    defaults.savePurchaseMaterialDefault.mockResolvedValue({})
    const {vm}=form();vm.form.supplierId='16';vm.form.items=[row()]
    await vm.calculateRowTotal(0);expect(defaults.savePurchaseMaterialDefault).not.toHaveBeenCalled()
    await vm.handleSaveDefault(0);expect(defaults.savePurchaseMaterialDefault).toHaveBeenCalledWith({supplierId:16,materialId:354,purchaseUnit:'ROLL',priceUnit:'㎡'})
  })
  it('重新打开订单保留本单单位尺寸、备注税率，不用当前默认覆写',async()=>{
    api.getOrder.mockResolvedValue({data:{orderId:2,orderNo:'PO261113005',supplierId:16,supplierName:'供应商',orderDate:'2026-11-13',expectedDeliveryDate:'2026-11-20',orderType:0,currency:'CNY',orderAmount:31110,orderTax:4044.3,orderTotalAmount:35154.3,remark:'订单备注',items:[{...row(),rollLengthM:98,taxAmount:4044.3}]}})
    const {w,vm}=form();await w.setProps({orderId:2,visible:true});await flushPromises()
    expect(vm.form.items[0]).toMatchObject({rollLengthM:98,purchaseUnit:'ROLL',purchaseQuantity:3,taxRate:13})
    expect(vm.form.remark).toBe('订单备注');expect(vm.totalTax).toBe(4044.3)
  })
})
describe('采购计划预填正式订单',()=>{
  it('保留计划数量单位和尺寸，只带供应商报价单位；初始化不下单',async()=>{
    const seed={supplierId:16,supplierName:'供应商',expectedDeliveryDate:'2026-12-01',urgentFlag:true,
      items:[{...row(),purchaseUnit:'㎡',purchaseQuantity:366,rollLengthM:98,purchaseRemark:'原始需求 300㎡',unitPrice:0}]}
    // 约定采购单位为卷，计划明确填㎡时不能将366变为366卷。
    api.previewOrderPricing.mockImplementation(async body=>({data:{orderAmount:0,orderTax:0,orderTotalAmount:0,items:body.items.map((i:any)=>({...i,quantity:366,priceQuantity:366,amount:0}))}}))
    const w=shallowMount(OrderFormDialog,{props:{visible:true,planSeed:seed}});wrappers.push(w);await flushPromises();const vm=w.vm as any
    expect(vm.form.items[0]).toMatchObject({purchaseUnit:'㎡',purchaseQuantity:366,priceUnit:'㎡',rollLengthM:98,purchaseRemark:'原始需求 300㎡'})
    expect(vm.form.expectedDeliveryDate).toBe('2026-12-01');expect(vm.form.urgentFlag).toBe(true);expect(api.addOrder).not.toHaveBeenCalled()
    expect(seed.items[0].unitPrice).toBe(0)
    vm.addItem();vm.removeItem(0);vm.handleMaterialChange(110,0);vm.handleSupplierChange({supplierId:17,supplierName:'另一个供应商'})
    expect(vm.form.items).toHaveLength(1);expect(vm.form.items[0].materialId).toBe(354);expect(vm.form.supplierId).toBe('16')
  })
  it('保存成功发出实际数量与订单号；保存失败保留核对窗口，不发成功事件',async()=>{
    const {w,vm}=form();vm.form.items=[row()];vm.form.orderNo='PO-PLAN';vm.formRef={validate:vi.fn().mockResolvedValue(true)}
    api.addOrder.mockRejectedValueOnce(new Error('保存失败'));await vm.handleSubmit()
    expect(w.emitted('success')).toBeUndefined();expect(vm.form.items[0].purchaseQuantity).toBe(3)
    await vm.handleSubmit();expect(w.emitted('success')?.[0]?.[0]).toMatchObject({orderNo:'PO-PLAN',items:[expect.objectContaining({materialId:354,quantity:366})]})
  })
})
describe('实际表格和打印',()=>{
  it('两个单位下拉独立常显，计价数量没有输入框',async()=>{
    const w=mount(PurchaseItemTable,{props:{items:[row()],materials:[material],supplierId:'16',materialLoading:false,units},global:{plugins:[ElementPlus],directives:{hasPermi:{mounted(){}}}}});wrappers.push(w)
    await nextTick();await flushPromises()
    expect(w.text()).toContain('采购单位');expect(w.text()).toContain('报价单位');expect(w.text()).toContain('计价数量')
    const headers=w.findAll('th');const pricingIndex=headers.findIndex(h=>h.text()==='计价数量')
    const cells=w.findAll('tbody tr')[0].findAll('td')
    expect(cells[pricingIndex].text()).toContain('366');expect(cells[pricingIndex].find('input').exists()).toBe(false)
    expect(w.findAll('.el-select').length).toBeGreaterThanOrEqual(3)
  })
  it('打印承接采购与计价两组数量单位、四位单价和多行采购说明',()=>{
    const w=mount(PurchaseItemsPrintTable,{props:{items:[{...row(),unitPrice:3.564,purchaseQuantity:4,priceQuantity:240,amount:855.36,purchaseRemark:'第一卷分切\n余料交回'}]}});wrappers.push(w)
    expect(w.text()).toContain('3.564');expect(w.text()).toContain('855.36');expect(w.text()).toContain('240');expect(w.text()).toContain('余料交回')
    expect(w.findAll('tbody tr')).toHaveLength(2)
  })
})
