import {afterEach,beforeEach,describe,it,expect,vi} from 'vitest'
import {shallowMount,mount,flushPromises} from '@vue/test-utils'
import Editor from '../../order/components/PurchaseProcessingEditor.vue'
import Summary from '../../order/components/PurchaseProcessingSummary.vue'
import Print from '../../order/components/PurchaseItemsPrintTable.vue'
import Receive from '../PurchaseProcessingReceive.vue'
const api=vi.hoisted(()=>({plan:vi.fn(),history:vi.fn(),receipt:vi.fn()}))
vi.mock('@/utils/request',()=>({default:{get:vi.fn(),post:vi.fn()}}))
vi.mock('@/api/purchase/orderEntry',async original=>({...await original<typeof import('@/api/purchase/orderEntry')>(),previewProcessingPlan:api.plan,getProcessingHistory:api.history,previewProcessingReceipt:api.receipt}))
vi.mock('@/api/inventory/material',()=>({materialApi:{search:vi.fn().mockResolvedValue({data:{records:[]}})}}))
const plan={groups:[{groupId:'g1',name:'母卷1',motherRolls:1,slits:[{widthMm:285,rolls:2},{widthMm:315,rolls:1},{widthMm:335,rolls:1}],outputs:[{outputId:'p1',materialId:501,materialCode:'CUT-285',materialName:'EBA180切片',unit:'PCS',widthMm:285,lengthMm:315,quantity:985},{outputId:'r1',materialId:502,materialCode:'ROLL-335',materialName:'EBA180余卷',unit:'ROLL',widthMm:335,rollLengthM:100,quantity:1}]}]}
const snapshot=JSON.stringify(plan)
const item={materialId:354,materialCode:'RM000350',materialName:'EBA180',unit:'㎡',purchaseUnit:'ROLL',priceUnit:'㎡',purchaseQuantity:3,quantity:366,unitPrice:85,taxRate:13,priceTaxInclusive:true,quotedAmount:31110,amount:27530.97,rollWidthMm:1220,rollLengthM:100,processingPlan:snapshot}
const wrappers:any[]=[]
function wrap(component:any,props:any){const w=shallowMount(component,{props});wrappers.push(w);return{w,s:(w.vm as any).$?.setupState}}
beforeEach(()=>{vi.clearAllMocks();api.history.mockResolvedValue({data:{processingPlan:snapshot,receipts:[]}});api.plan.mockResolvedValue({data:{...item,processingPlan:snapshot}});api.receipt.mockResolvedValue({data:{receivedQuantity:44.8875,items:[{materialCode:'CUT-285',quantity:500,unit:'PCS',amount:3376.82}]}})})
afterEach(()=>wrappers.splice(0).forEach(w=>w.unmount()))
describe('母卷加工要求和实际收货',()=>{
 it('只读方案显示所属母卷、分切及切片余卷，打印保留同一快照与含税金额',()=>{const w=mount(Summary,{props:{modelValue:snapshot}});wrappers.push(w);expect(w.text()).toContain('母卷1');expect(w.text()).toContain('985片');expect(w.text()).toContain('余卷交回');const p=mount(Print,{props:{items:[item]}});wrappers.push(p);expect(p.text()).toContain('31110.00');expect(p.text()).toContain('含税');expect(p.text()).toContain('母卷1');expect(p.text()).not.toContain('27530.97')})
 it('编辑在独立草稿中进行，取消不写回采购行',()=>{const {w,s}=wrap(Editor,{item,modelValue:snapshot});s.open();s.groups[0].outputs[0].quantity=20;s.visible=false;expect(w.emitted('update:modelValue')).toBeUndefined();expect(JSON.parse(item.processingPlan).groups[0].outputs[0].quantity).toBe(985)})
 it('校核成功只采用服务端规范化方案，不创建订单',async()=>{const {w,s}=wrap(Editor,{item,modelValue:snapshot});s.open();await s.apply();expect(api.plan).toHaveBeenCalledOnce();expect(w.emitted('update:modelValue')?.[0]).toEqual([snapshot]);expect(s.visible).toBe(false)})
 it('校核失败保留加工草稿和错误，不发采用事件',async()=>{api.plan.mockRejectedValue(new Error('分切宽度超母卷'));const {w,s}=wrap(Editor,{item,modelValue:snapshot});s.open();s.groups[0].slits[0].widthMm=2000;await s.apply();expect(s.error).toContain('超母卷');expect(s.groups[0].slits[0].widthMm).toBe(2000);expect(w.emitted('update:modelValue')).toBeUndefined()})
 it('加工收货打开时不默认填满计划，不虚构余卷长度',async()=>{const {s}=wrap(Receive,{itemId:99});await flushPromises();expect(s.inputs[0].outputs.map((o:any)=>o.quantity)).toEqual([0,0]);expect(s.inputs[0].outputs[1].rollLengthM).toBeUndefined();expect(s.hasInput).toBe(false);expect(api.receipt).not.toHaveBeenCalled()})
 it('只提交本次实际交付的规格，覆盖面积采用后端结果',async()=>{const {s}=wrap(Receive,{itemId:99});await flushPromises();s.inputs[0].outputs[0].quantity=500;s.changed();const result=await s.validate();expect(api.receipt).toHaveBeenCalledWith(99,[expect.objectContaining({outputs:[{outputId:'p1',quantity:500,rollLengthM:undefined}]})]);expect(result.receivedQuantity).toBe(44.8875);expect(result.groups[0].outputs).toHaveLength(1)})
 it('校核失败时保留实收供修正重试',async()=>{api.receipt.mockRejectedValue(new Error('超过计划未收'));const {s}=wrap(Receive,{itemId:99});await flushPromises();s.inputs[0].outputs[0].quantity=1000;s.changed();await expect(s.validate()).rejects.toThrow();expect(s.inputs[0].outputs[0].quantity).toBe(1000);expect(s.error).toContain('超过计划未收');expect(s.preview).toBeUndefined()})
 it('切换采购行后，迟到的试算不能显示为新行结果',async()=>{let done!:(r:any)=>void;api.receipt.mockReturnValueOnce(new Promise(r=>{done=r}));const {w,s}=wrap(Receive,{itemId:99});await flushPromises();s.inputs[0].outputs[0].quantity=500;s.changed();const old=s.validate();await w.setProps({itemId:100});await flushPromises();done({data:{receivedQuantity:999,items:[]}});await old;expect(s.preview).toBeUndefined();expect(s.inputs[0].outputs[0].quantity).toBe(0)})
 it('数量在失焦之前改变，也会使旧试算结果失效',async()=>{let done!:(r:any)=>void;api.receipt.mockReturnValueOnce(new Promise(r=>{done=r}));const {s}=wrap(Receive,{itemId:99});await flushPromises();s.inputs[0].outputs[0].quantity=500;s.changed();const old=s.validate();s.inputs[0].outputs[0].quantity=501;done({data:{receivedQuantity:999,items:[]}});await old;expect(s.preview).toBeUndefined()})
})
