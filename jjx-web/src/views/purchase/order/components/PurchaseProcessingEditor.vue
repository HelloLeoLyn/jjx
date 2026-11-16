<template>
  <div class="processing-editor">
    <div class="processing-toolbar">
      <strong>加工要求</strong>
      <el-button link type="primary" :disabled="disabled" @click="open">{{ modelValue ? '维护加工要求' : '添加分切 / 切片要求' }}</el-button>
      <el-button v-if="modelValue" link type="danger" :disabled="disabled" @click="remove">移除加工要求</el-button>
    </div>
    <PurchaseProcessingSummary :model-value="modelValue" />
    <el-dialog v-model="visible" title="母卷加工要求" width="min(1080px, calc(100vw - 24px))" append-to-body :close-on-click-modal="false" :close-on-press-escape="!saving" :show-close="!saving">
      <p>采购行：{{ item.materialCode }} · {{ item.materialName }}，{{ item.rollWidthMm || '—' }}mm × {{ item.rollLengthM || '—' }}m × {{ item.purchaseQuantity || '—' }}卷。金额在采购主行结算。</p>
      <el-alert v-if="item.purchaseUnit !== 'ROLL'" title="请先在采购主行选择按卷采购，并确认每卷长度。" type="warning" :closable="false" />
      <el-form :disabled="saving" label-width="90px">
        <el-card v-for="(group, index) in groups" :key="group.groupId" shadow="never" class="group-card">
          <div class="group-title">
            <el-input v-model="group.name" maxlength="80" aria-label="母卷分组名称" style="width:220px" />
            <span>使用母卷</span><el-input-number v-model="group.motherRolls" :min="1" :max="10000" :precision="0" :controls="false" style="width:90px" /><span>卷</span>
            <el-button link type="danger" @click="groups.splice(index,1)">移除此组</el-button>
          </div>
          <div class="slit-line">
            <strong>分切</strong>
            <div v-for="(slit,i) in group.slits" :key="i" class="slit-entry">
              <el-input-number v-model="slit.widthMm" :min="0" :precision="2" :controls="false" aria-label="分切宽度" style="width:105px" />mm ×
              <el-input-number v-model="slit.rolls" :min="1" :max="10000" :precision="0" :controls="false" aria-label="分切卷数" style="width:70px" />卷
              <el-button link type="danger" @click="group.slits.splice(i,1)">移除</el-button>
            </div>
            <el-button link type="primary" @click="group.slits.push({widthMm:0,rolls:1})">添加宽度</el-button>
          </div>
          <div v-for="(output,i) in group.outputs" :key="output.outputId" class="output-entry">
            <el-select v-model="output.unit" aria-label="交付形态" style="width:115px"><el-option label="切片" value="PCS"/><el-option label="余卷交回" value="ROLL"/></el-select>
            <el-select v-model="output.materialId" filterable remote :remote-method="search" :loading="searching" placeholder="选择交付规格物料" style="width:310px" @change="id=>selectOutput(output,id)">
              <el-option v-for="m in optionsFor(output)" :key="m.materialId" :value="m.materialId" :label="`${m.materialCode} · ${m.materialName} · ${m.specification || ''}`"/>
            </el-select>
            <el-input-number v-model="output.quantity" :min="0" :precision="0" :controls="false" aria-label="计划交货数量" style="width:110px"/><span>{{ output.unit === 'ROLL' ? '卷' : '片' }}</span>
            <template v-if="output.unit==='ROLL'"><el-input-number v-model="output.rollLengthM" :min="0" :precision="4" :controls="false" aria-label="余卷预计每卷长度" style="width:115px"/><span>m/卷</span></template>
            <span v-else>{{ output.widthMm || '—' }} × {{ output.lengthMm || '—' }}mm</span>
            <el-button link type="danger" @click="group.outputs.splice(i,1)">移除</el-button>
            <el-input v-model="output.remark" maxlength="300" placeholder="加工或交货说明（可选）" />
          </div>
          <el-button plain @click="addOutput(group)">添加切片 / 余卷</el-button>
        </el-card>
        <el-button plain type="primary" @click="addGroup">添加母卷分组</el-button>
      </el-form>
      <el-alert v-if="error" :title="error" type="error" :closable="false" class="error-alert" />
      <p class="hint">交付规格绑定物料档案；系统校核宽度、面积和数量，不自动推算切片产能。要求随采购订单保存及审核，收货时录入实际片数、卷数和余卷长度。</p>
      <template #footer><el-button :disabled="saving" @click="visible=false">取消</el-button><el-button type="primary" :loading="saving" :disabled="item.purchaseUnit!=='ROLL'" @click="apply">采用加工要求</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { ref,onBeforeUnmount } from 'vue'
import { ElMessageBox } from 'element-plus'
import { materialApi } from '@/api/inventory/material'
import { previewProcessingPlan,readProcessingPlan } from '@/api/purchase/orderEntry'
import type { PurchaseEntryItem,ProcessingGroup,ProcessingOutput } from '@/api/purchase/orderEntry'
import type { InventoryMaterial } from '@/types/inventory/material'
import PurchaseProcessingSummary from './PurchaseProcessingSummary.vue'
const props=defineProps<{modelValue?:string|null;item:PurchaseEntryItem;disabled?:boolean}>()
const emit=defineEmits<{(e:'update:modelValue',value:string|null):void;(e:'change'):void}>()
const visible=ref(false),saving=ref(false),error=ref(''),groups=ref<ProcessingGroup[]>([]),options=ref<InventoryMaterial[]>([]),searching=ref(false)
let sequence=0,disposed=false
const key=()=>globalThis.crypto?.randomUUID?.()||`processing-${Date.now()}-${++sequence}`
function open(){if(props.disabled)return;groups.value=JSON.parse(JSON.stringify(readProcessingPlan(props.modelValue)?.groups||[]));error.value='';visible.value=true;if(!groups.value.length)addGroup()}
function addGroup(){groups.value.push({groupId:key(),name:`母卷组 ${groups.value.length+1}`,motherRolls:1,slits:[{widthMm:0,rolls:1}],outputs:[]})}
function addOutput(group:ProcessingGroup){group.outputs.push({outputId:key(),unit:'PCS',quantity:0})}
function optionsFor(output:ProcessingOutput){return output.materialId&&!options.value.some(m=>m.materialId===output.materialId)?[{materialId:output.materialId,materialCode:output.materialCode,materialName:output.materialName,specification:output.materialSpec} as InventoryMaterial,...options.value]:options.value}
let searchVersion=0
async function search(keyword:string){const v=++searchVersion;searching.value=true;try{const r=await materialApi.search({keyword,pageNum:1,pageSize:30});if(v===searchVersion&&!disposed)options.value=r.data?.records||[]}catch(ex:any){if(v===searchVersion)error.value=ex?.message||'物料搜索失败'}finally{if(v===searchVersion)searching.value=false}}
function selectOutput(output:ProcessingOutput,id:number){const m=options.value.find(m=>m.materialId===id);if(m)Object.assign(output,{materialCode:m.materialCode,materialName:m.materialName,materialSpec:m.specification,widthMm:m.widthMm,lengthMm:m.lengthMm})}
async function apply(){if(saving.value)return;saving.value=true;error.value='';try{const r=await previewProcessingPlan({...props.item,processingPlan:JSON.stringify({groups:groups.value})});if(disposed)return;emit('update:modelValue',r.data.processingPlan!);emit('change');visible.value=false}catch(e:any){error.value=e?.message||'加工方案校核失败，输入已保留'}finally{saving.value=false}}
async function remove(){try{await ElMessageBox.confirm('移除本采购行的加工要求？保存订单后生效。','移除加工要求');emit('update:modelValue',null);emit('change')}catch{/* 取消保留原要求。 */}}
onBeforeUnmount(()=>{disposed=true;++searchVersion})
</script>
<style scoped>
.processing-toolbar,.group-title,.slit-line,.slit-entry,.output-entry{display:flex;align-items:center;gap:10px;flex-wrap:wrap}.processing-toolbar{margin:12px 0}.group-card{margin:14px 0}.group-title,.slit-line{margin-bottom:14px}.output-entry{padding:12px;background:#f5f7fa;margin-bottom:10px}.hint{font-size:12px;color:#606266;line-height:1.8}.error-alert{margin-top:12px}
</style>
