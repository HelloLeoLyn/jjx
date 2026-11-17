<template>
  <div class="processing-receive" v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="plan">
      <p>按最终节点登记本次实际交货或报废数量，未处理的保持为空。报废项只记录加工损耗，不生成该节点的库存。</p>
      <el-card v-for="group in plan.groups" :key="group.groupId" shadow="never" class="receipt-group">
        <strong>{{ group.name }} · 母卷 {{ group.motherRolls }}卷</strong>
        <div v-if="!plan.tree" class="slits">分切：{{ group.slits.map(s => `${s.widthMm}mm × ${s.rolls}卷`).join(' / ') }}</div>
        <el-table :data="group.outputs" border size="small">
          <el-table-column label="交付规格" min-width="220"><template #default="{row}"><template v-if="isScrap(row)"><strong class="scrap-label">报废不入库</strong><div>{{ row.disposalReason }}</div></template><template v-else>{{ row.materialCode || '未指定物料' }} · {{ row.materialName }}</template><small v-if="displayPath(row)" class="node-path">{{ displayPath(row) }}</small><div>{{ row.widthMm }}mm × {{ row.unit === 'ROLL' ? `${row.rollLengthM}m/卷` : `${row.lengthMm}mm` }}</div></template></el-table-column>
          <el-table-column label="计划 / 已确认" width="120"><template #default="{row}">{{ row.quantity }} / {{ received(row.outputId) }} {{ row.unit === 'ROLL' ? '卷' : '片' }}</template></el-table-column>
          <el-table-column label="本次交货 / 报废" width="150"><template #default="{row}"><el-input-number v-sync-disabled-aria v-model="entry(group.groupId,row.outputId).quantity" :disabled="disabled" :min="0" :precision="0" :controls="false" :aria-label="isScrap(row) ? '本次报废数量' : '实际交付片数卷数'" /></template></el-table-column>
          <el-table-column label="实际每卷长度(m)" width="170"><template #default="{row}"><el-input-number v-if="row.unit==='ROLL'" v-sync-disabled-aria v-model="entry(group.groupId,row.outputId).rollLengthM" :disabled="disabled" :min="0" :precision="4" :controls="false" aria-label="余卷实际长度" /><span v-else>—</span></template></el-table-column>
        </el-table>
        <p v-if="group.outputs.some(isScrap)">报废面积由后台按本次报废数量计算，无需重复填写损耗。</p>
        <div v-else class="loss-input"><span>本次加工损耗(㎡)</span><el-input-number v-sync-disabled-aria v-model="groupEntry(group.groupId).lossArea" :disabled="disabled" :min="0" :precision="4" :controls="false" aria-label="加工损耗面积" /><el-input v-model="groupEntry(group.groupId).lossReason" :disabled="disabled" maxlength="200" placeholder="有损耗时填写原因" /></div>
      </el-card>
      <el-button :disabled="disabled || !hasInput" :loading="previewing" @click="check">核对实际交付</el-button>
      <template v-if="preview">
        <p><strong>本次折合母卷 {{ preview.receivedQuantity }}㎡</strong>（含已确认的报废及加工损耗）。{{ preview.items.length ? '以下实际交货生成待检入库明细：' : '本次仅确认报废，不生成入库单。' }}</p>
        <el-table v-if="preview.items.length" :data="preview.items" border size="small"><el-table-column label="物料" prop="materialCode"/><el-table-column label="规格" prop="specification"/><el-table-column label="实际基本数量" prop="quantity"/><el-table-column label="基本单位" prop="unit"/><el-table-column label="未税成本" prop="amount"/></el-table>
        <el-table v-if="preview.disposals?.length" :data="preview.disposals" border size="small"><el-table-column label="报废节点" prop="outputId"/><el-table-column label="实际报废数量" prop="quantity"/><el-table-column label="报废面积(㎡)" prop="scrapArea"/><el-table-column label="原因" prop="lossReason"/></el-table>
      </template>
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed,ref,watch,onBeforeUnmount } from 'vue'
import { getProcessingHistory,readProcessingPlan,previewProcessingReceipt } from '@/api/purchase/orderEntry'
import type { ProcessingPlan,ProcessingReceiptGroup,ProcessingOutput } from '@/api/purchase/orderEntry'
import { processingTreeDisplayRows } from '../order/components/processingTreeDisplay'
import { PurchaseProcessingDeliveryEnum } from '@/enums/purchase/processing'
const isScrap=(output:ProcessingOutput)=>output.delivery===PurchaseProcessingDeliveryEnum.SCRAP.value
// Element Plus数字输入的aria-disabled只在挂载时设置；连续收货解锁后同步真实disabled。
function syncDisabledAria(el:HTMLElement){const input=el.querySelector('input');if(input)input.setAttribute('aria-disabled',String(input.disabled))}
const vSyncDisabledAria={mounted:syncDisabledAria,updated:syncDisabledAria}
const props=defineProps<{itemId:number;disabled?:boolean;modelValue?:ProcessingReceiptGroup[]}>()
const emit=defineEmits<{(e:'update:modelValue',value:ProcessingReceiptGroup[]):void}>()
const plan=ref<ProcessingPlan>(),loading=ref(false),previewing=ref(false),error=ref(''),inputs=ref<ProcessingReceiptGroup[]>([]),facts=ref<{outputId:string;quantity:number}[]>([])
const displayPaths=computed(()=>new Map(plan.value?.tree?processingTreeDisplayRows(plan.value.tree).filter(r=>!r.children.length).map(r=>[r.node.nodeId,r.path]):[]))
const displayPath=(output:ProcessingOutput)=>displayPaths.value.get(output.outputId)||output.nodePath
const preview=ref<Awaited<ReturnType<typeof previewProcessingReceipt>>['data']>()
let epoch=0,version=0,disposed=false
const hasInput=computed(()=>inputs.value.some(g=>g.outputs.some(o=>Number(o.quantity)>0)||Number(g.lossArea)>0))
const received=(id:string)=>facts.value.filter(f=>f.outputId===id).reduce((s,f)=>s+Number(f.quantity),0)
function groupEntry(id:string){return inputs.value.find(g=>g.groupId===id)!}
function entry(groupId:string,id:string){return groupEntry(groupId).outputs.find(o=>o.outputId===id)!}
function payload(){return inputs.value.map(g=>({...g,outputs:g.outputs.filter(o=>Number(o.quantity)>0).map(o=>({...o,rollLengthM:o.rollLengthM==null?undefined:o.rollLengthM}))})).filter(g=>g.outputs.length||Number(g.lossArea)>0)}
function changed(){++version;previewing.value=false;preview.value=undefined;error.value='';emit('update:modelValue',payload())}
async function validate(){const v=++version,e=epoch;preview.value=undefined;previewing.value=true;error.value='';try{const groups=payload();const r=await previewProcessingReceipt(props.itemId,groups);if(disposed||v!==version||e!==epoch)return;preview.value=r.data;emit('update:modelValue',groups);return {groups,receivedQuantity:r.data.receivedQuantity,inboundItemCount:r.data.items.length}}catch(ex:any){if(v===version&&e===epoch)error.value=ex?.message||'实际交付校核失败';throw ex}finally{if(v===version&&e===epoch)previewing.value=false}}
watch(inputs,changed,{deep:true,flush:'sync'})
async function check(){try{await validate()}catch(ex:any){if(!error.value)error.value=ex?.message||'校核失败'}}
watch(()=>props.itemId,async id=>{const e=++epoch;++version;loading.value=true;plan.value=undefined;preview.value=undefined;error.value='';inputs.value=[];emit('update:modelValue',[]);try{const r=await getProcessingHistory(id);if(disposed||e!==epoch)return;plan.value=readProcessingPlan(r.data.processingPlan);if(!plan.value)throw new Error('加工方案格式无效');facts.value=r.data.receipts;inputs.value=plan.value.groups.map(g=>({groupId:g.groupId,lossArea:0,lossReason:'',outputs:g.outputs.map(o=>({outputId:o.outputId,quantity:0}))}))}catch(ex:any){if(e===epoch)error.value=ex?.message||'加工要求读取失败'}finally{if(e===epoch)loading.value=false}},{immediate:true})
onBeforeUnmount(()=>{disposed=true;++epoch;++version})
defineExpose({validate,hasInput})
</script>
<style scoped>
.processing-receive{padding:10px 20px}.scrap-label{color:#f56c6c}.node-path{display:block;color:#909399;line-height:1.5}.receipt-group{margin:12px 0}.slits{margin:8px 0;color:#606266}.loss-input{display:flex;align-items:center;gap:10px;margin-top:12px}.loss-input>.el-input{flex:1}.processing-receive :deep(.el-input-number){width:140px}
</style>
