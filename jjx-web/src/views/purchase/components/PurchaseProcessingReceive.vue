<template>
  <div class="processing-receive" v-loading="loading">
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="plan">
      <p>母卷采购行 {{ itemId }}；本次只填写实际收到的切片与余卷。未收到的交货要求保持为空，母卷不重复入库。</p>
      <el-card v-for="group in plan.groups" :key="group.groupId" shadow="never" class="receipt-group">
        <strong>{{ group.name }} · 母卷 {{ group.motherRolls }}卷</strong>
        <div class="slits">分切：{{ group.slits.map(s => `${s.widthMm}mm × ${s.rolls}卷`).join(' / ') }}</div>
        <el-table :data="group.outputs" border size="small">
          <el-table-column label="交付规格" min-width="220"><template #default="{row}">{{ row.materialCode }} · {{ row.materialName }}<div>{{ row.widthMm }}mm × {{ row.unit === 'ROLL' ? `${row.rollLengthM}m/卷` : `${row.lengthMm}mm` }}</div></template></el-table-column>
          <el-table-column label="计划 / 已收" width="120"><template #default="{row}">{{ row.quantity }} / {{ received(row.outputId) }} {{ row.unit === 'ROLL' ? '卷' : '片' }}</template></el-table-column>
          <el-table-column label="本次实际交付" width="150"><template #default="{row}"><el-input-number v-model="entry(group.groupId,row.outputId).quantity" :disabled="disabled" :min="0" :precision="0" :controls="false" aria-label="实际交付片数卷数" @change="changed" /></template></el-table-column>
          <el-table-column label="实际每卷长度(m)" width="170"><template #default="{row}"><el-input-number v-if="row.unit==='ROLL'" v-model="entry(group.groupId,row.outputId).rollLengthM" :disabled="disabled" :min="0" :precision="4" :controls="false" aria-label="余卷实际长度" @change="changed" /><span v-else>—</span></template></el-table-column>
        </el-table>
        <div class="loss-input"><span>本次加工损耗(㎡)</span><el-input-number v-model="groupEntry(group.groupId).lossArea" :disabled="disabled" :min="0" :precision="4" :controls="false" aria-label="加工损耗面积" @change="changed" /><el-input v-model="groupEntry(group.groupId).lossReason" :disabled="disabled" maxlength="200" placeholder="有损耗时填写原因" @input="changed" /></div>
      </el-card>
      <el-button :disabled="disabled || !hasInput" :loading="previewing" @click="check">核对实际交付</el-button>
      <template v-if="preview">
        <p><strong>本次折合母卷 {{ preview.receivedQuantity }}㎡</strong>（含已填写损耗），确认后生成以下实际规格的待检入库明细：</p>
        <el-table :data="preview.items" border size="small"><el-table-column label="物料" prop="materialCode"/><el-table-column label="规格" prop="specification"/><el-table-column label="实际基本数量" prop="quantity"/><el-table-column label="基本单位" prop="unit"/><el-table-column label="未税成本" prop="amount"/></el-table>
      </template>
    </template>
  </div>
</template>
<script setup lang="ts">
import { computed,ref,watch,onBeforeUnmount } from 'vue'
import { getProcessingHistory,readProcessingPlan,previewProcessingReceipt } from '@/api/purchase/orderEntry'
import type { ProcessingPlan,ProcessingReceiptGroup } from '@/api/purchase/orderEntry'
const props=defineProps<{itemId:number;disabled?:boolean;modelValue?:ProcessingReceiptGroup[]}>()
const emit=defineEmits<{(e:'update:modelValue',value:ProcessingReceiptGroup[]):void}>()
const plan=ref<ProcessingPlan>(),loading=ref(false),previewing=ref(false),error=ref(''),inputs=ref<ProcessingReceiptGroup[]>([]),facts=ref<{outputId:string;quantity:number}[]>([])
const preview=ref<Awaited<ReturnType<typeof previewProcessingReceipt>>['data']>()
let epoch=0,version=0,disposed=false
const hasInput=computed(()=>inputs.value.some(g=>g.outputs.some(o=>Number(o.quantity)>0)||Number(g.lossArea)>0))
const received=(id:string)=>facts.value.filter(f=>f.outputId===id).reduce((s,f)=>s+Number(f.quantity),0)
function groupEntry(id:string){return inputs.value.find(g=>g.groupId===id)!}
function entry(groupId:string,id:string){return groupEntry(groupId).outputs.find(o=>o.outputId===id)!}
function payload(){return inputs.value.map(g=>({...g,outputs:g.outputs.filter(o=>Number(o.quantity)>0).map(o=>({...o,rollLengthM:o.rollLengthM==null?undefined:o.rollLengthM}))})).filter(g=>g.outputs.length||Number(g.lossArea)>0)}
function changed(){++version;previewing.value=false;preview.value=undefined;error.value='';emit('update:modelValue',payload())}
async function validate(){const v=++version,e=epoch;preview.value=undefined;previewing.value=true;error.value='';try{const groups=payload();const r=await previewProcessingReceipt(props.itemId,groups);if(disposed||v!==version||e!==epoch)return;preview.value=r.data;emit('update:modelValue',groups);return {groups,receivedQuantity:r.data.receivedQuantity}}catch(ex:any){if(v===version&&e===epoch)error.value=ex?.message||'实际交付校核失败';throw ex}finally{if(v===version&&e===epoch)previewing.value=false}}
async function check(){try{await validate()}catch(ex:any){if(!error.value)error.value=ex?.message||'校核失败'}}
watch(()=>props.itemId,async id=>{const e=++epoch;++version;loading.value=true;plan.value=undefined;preview.value=undefined;error.value='';inputs.value=[];emit('update:modelValue',[]);try{const r=await getProcessingHistory(id);if(disposed||e!==epoch)return;plan.value=readProcessingPlan(r.data.processingPlan);if(!plan.value)throw new Error('加工方案格式无效');facts.value=r.data.receipts;inputs.value=plan.value.groups.map(g=>({groupId:g.groupId,lossArea:0,lossReason:'',outputs:g.outputs.map(o=>({outputId:o.outputId,quantity:0}))}))}catch(ex:any){if(e===epoch)error.value=ex?.message||'加工要求读取失败'}finally{if(e===epoch)loading.value=false}},{immediate:true})
onBeforeUnmount(()=>{disposed=true;++epoch;++version})
defineExpose({validate,hasInput})
</script>
<style scoped>
.processing-receive{padding:10px 20px}.receipt-group{margin:12px 0}.slits{margin:8px 0;color:#606266}.loss-input{display:flex;align-items:center;gap:10px;margin-top:12px}.loss-input>.el-input{flex:1}.processing-receive :deep(.el-input-number){width:140px}
</style>
