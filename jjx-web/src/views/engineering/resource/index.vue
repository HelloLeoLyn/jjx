<template>
  <div class="resource-page">
    <el-card shadow="never">
      <template #header>
        <div class="header"><span>{{ tab === 'SCREEN' ? '网版管理' : '刀模管理' }}</span><el-input v-model="keyword" clearable placeholder="编号/名称/内容" style="width:260px" @keyup.enter="load" @clear="load" /></div>
      </template>
      <template v-if="false">
        <el-table :data="films" v-loading="loading" border>
          <el-table-column prop="filmCode" label="菲林编码" width="180" />
          <el-table-column prop="filmName" label="名称" min-width="180" />
          <el-table-column prop="version" label="版本" width="90" />
          <el-table-column prop="productName" label="原主产品" min-width="160" />
          <el-table-column label="关联产品" min-width="260"><template #default="{row}">{{ productText(row._products) }}</template></el-table-column>
          <el-table-column label="操作" width="100"><template #default="{row}"><el-button link type="primary" v-hasPermi="['engineering:resource:edit']" @click="editProducts('FILM',row.filmId,row._products,row.filmName)">产品关联</el-button></template></el-table-column>
        </el-table>
      </template>

      <template v-else-if="tab === 'SCREEN'">
        <div class="toolbar"><el-button type="primary" v-hasPermi="['engineering:resource:edit']" @click="openFrame()">新增网框</el-button></div>
        <el-table :data="frames" v-loading="loading" border>
          <el-table-column prop="frame_no" label="网框编号" width="130" />
          <el-table-column prop="frame_type" label="型号" width="100" />
          <el-table-column prop="mesh" label="目数" width="80" />
          <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="ScreenFrameStatusEnum.getTagProps(row.status).type">{{ ScreenFrameStatusEnum.getLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column prop="current_plate_no" label="当前版面" width="150" />
          <el-table-column prop="plate_content" label="版面内容" min-width="180" show-overflow-tooltip />
          <el-table-column label="关联产品" min-width="220"><template #default="{row}">{{ refText(row.product_refs) }}</template></el-table-column>
          <el-table-column prop="location" label="位置" width="120" />
          <el-table-column label="操作" width="280" fixed="right"><template #default="{row}">
            <el-button link type="primary" v-hasPermi="['engineering:resource:edit']" @click="openFrame(row)">编辑</el-button>
            <el-button v-if="row.status === ScreenFrameStatusEnum.EMPTY.value" link type="success" v-hasPermi="['engineering:resource:edit']" @click="openPlate(row)">制版</el-button>
            <el-button v-if="row.status === ScreenFrameStatusEnum.PLATED.value" link type="warning" v-hasPermi="['engineering:resource:maintain']" @click="wash(row)">洗版</el-button>
            <el-dropdown v-if="row.status !== ScreenFrameStatusEnum.SCRAPPED.value" v-hasPermi="['engineering:resource:maintain']" @command="(command:string)=>frameAction(row,command)"><el-button link type="warning">维护</el-button><template #dropdown><el-dropdown-menu><el-dropdown-item command="REPAIR">送修</el-dropdown-item><el-dropdown-item command="ENABLE">恢复使用</el-dropdown-item><el-dropdown-item command="SCRAP">报废</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
            <el-button link v-hasPermi="['engineering:resource:view']" @click="showHistory('SCREEN_FRAME',row.frame_id)">履历</el-button>
          </template></el-table-column>
        </el-table>
      </template>

      <template v-else>
        <div class="toolbar"><el-button type="primary" v-hasPermi="['engineering:resource:edit']" @click="openDie()">新增刀模</el-button></div>
        <el-table :data="dies" v-loading="loading" border>
          <el-table-column prop="die_no" label="刀模编号" width="130" />
          <el-table-column prop="die_name" label="名称" min-width="150" />
          <el-table-column prop="purpose" label="用途" min-width="130" />
          <el-table-column prop="version" label="版本" width="80" />
          <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="DieStatusEnum.getTagProps(row.status).type">{{ DieStatusEnum.getLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="关联产品" min-width="220"><template #default="{row}">{{ refText(row.product_refs) }}</template></el-table-column>
          <el-table-column prop="location" label="位置" width="110" />
          <el-table-column label="操作" width="220" fixed="right"><template #default="{row}">
            <el-button link type="primary" v-hasPermi="['engineering:resource:edit']" @click="openDie(row)">编辑</el-button>
            <el-button v-if="row.status !== DieStatusEnum.SCRAPPED.value && row.status !== DieStatusEnum.REPLACED.value" link type="warning" v-hasPermi="['engineering:resource:maintain']" @click="openAction(row)">维护</el-button>
            <el-button link @click="showHistory('DIE',row.die_id)">履历</el-button>
          </template></el-table-column>
        </el-table>
      </template>
    </el-card>

    <el-dialog v-model="frameVisible" :title="frameForm.frameId ? '编辑网框' : '新增网框'" width="520px">
      <el-form :model="frameForm" label-width="90px"><el-form-item label="网框编号" required><el-input v-model="frameForm.frameNo" /></el-form-item><el-form-item label="型号"><el-input v-model="frameForm.frameType" /></el-form-item><el-form-item label="目数"><el-input v-model="frameForm.mesh" /></el-form-item><el-form-item label="位置"><el-input v-model="frameForm.location" /></el-form-item><el-form-item label="备注"><el-input v-model="frameForm.remark" type="textarea" /></el-form-item></el-form>
      <template #footer><el-button @click="frameVisible=false">取消</el-button><el-button type="primary" @click="saveFrame">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="plateVisible" title="制版" width="600px">
      <el-form :model="plateForm" label-width="90px"><el-form-item label="版面编号" required><el-input v-model="plateForm.plateNo" /></el-form-item><el-form-item label="来源菲林"><el-select v-model="plateForm.filmId" filterable clearable style="width:100%"><el-option v-for="f in films" :key="f.filmId" :value="f.filmId" :label="`${f.filmCode} ${f.filmName} ${f.version}`" /></el-select></el-form-item><el-form-item label="版面内容"><el-input v-model="plateForm.content" type="textarea" /></el-form-item><el-form-item label="关联产品"><el-select v-model="plateForm.productIds" multiple filterable style="width:100%"><el-option v-for="p in products" :key="p.productId" :value="p.productId" :label="`${p.productCode} ${p.productName}`" /></el-select></el-form-item><el-form-item label="用途"><el-input v-model="plateForm.purpose" /></el-form-item></el-form>
      <template #footer><el-button @click="plateVisible=false">取消</el-button><el-button type="primary" @click="createPlate">确认制版</el-button></template>
    </el-dialog>

    <el-dialog v-model="dieVisible" :title="dieForm.dieId ? '编辑刀模' : '新增刀模'" width="600px">
      <el-form :model="dieForm" label-width="90px"><el-form-item label="刀模编号" required><el-input v-model="dieForm.dieNo" /></el-form-item><el-form-item label="名称" required><el-input v-model="dieForm.dieName" /></el-form-item><el-form-item label="用途"><el-input v-model="dieForm.purpose" /></el-form-item><el-form-item label="规格"><el-input v-model="dieForm.specification" type="textarea" /></el-form-item><el-form-item label="版本"><el-input v-model="dieForm.version" /></el-form-item><el-form-item label="位置"><el-input v-model="dieForm.location" /></el-form-item><el-form-item label="关联产品"><el-select v-model="dieForm.productIds" multiple filterable style="width:100%"><el-option v-for="p in products" :key="p.productId" :value="p.productId" :label="`${p.productCode} ${p.productName}`" /></el-select></el-form-item></el-form>
      <template #footer><el-button @click="dieVisible=false">取消</el-button><el-button type="primary" @click="saveDie">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="productVisible" :title="`${productTitle} · 产品关联`" width="560px"><el-select v-model="selectedProductIds" multiple filterable style="width:100%"><el-option v-for="p in products" :key="p.productId" :value="p.productId" :label="`${p.productCode} ${p.productName}`" /></el-select><template #footer><el-button @click="productVisible=false">取消</el-button><el-button type="primary" @click="saveProducts">保存</el-button></template></el-dialog>
    <el-dialog v-model="actionVisible" title="刀模维护" width="520px"><el-form label-width="90px"><el-form-item label="动作"><el-select v-model="actionForm.actionType"><el-option v-for="a in DieActionEnum.items" :key="a.value" :value="a.value" :label="a.label" /></el-select></el-form-item><el-form-item v-if="actionForm.actionType === DieActionEnum.REMAKE.value" label="新刀模编号" required><el-input v-model="actionForm.newDieNo" /></el-form-item><el-form-item label="说明"><el-input v-model="actionForm.description" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="actionVisible=false">取消</el-button><el-button type="primary" @click="saveAction">确认</el-button></template></el-dialog>
    <el-dialog v-model="historyVisible" title="维护履历" width="760px"><el-table :data="history" border><el-table-column prop="operate_time" label="时间" width="170"/><el-table-column prop="action_type" label="动作" width="100"/><el-table-column prop="before_status" label="原状态" width="100"/><el-table-column prop="after_status" label="新状态" width="100"/><el-table-column prop="operator" label="操作人" width="100"/><el-table-column prop="description" label="说明" min-width="180"/></el-table></el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { engineeringResourceApi as api } from '@/api/engineering/resource'
import { filmApi } from '@/api/product/film'
import { listProductPage } from '@/api/product'
import { DieActionEnum, DieStatusEnum, ScreenFrameStatusEnum } from '@/enums/engineering/resource'

defineOptions({ name: 'EngineeringResource' })
const route=useRoute()
const tab=computed(()=>route.name === 'EngineeringDieResource' ? 'DIE' : 'SCREEN')
const keyword=ref(''),loading=ref(false),films=ref<any[]>([]),frames=ref<any[]>([]),dies=ref<any[]>([]),products=ref<any[]>([])
const frameVisible=ref(false),plateVisible=ref(false),dieVisible=ref(false),productVisible=ref(false),actionVisible=ref(false),historyVisible=ref(false)
const frameForm=reactive<any>({}),plateForm=reactive<any>({productIds:[]}),dieForm=reactive<any>({productIds:[]}),actionForm=reactive<any>({actionType:DieActionEnum.REPAIR.value,description:'',newDieNo:''})
const selectedProductIds=ref<number[]>([]),productType=ref(''),productId=ref(0),productTitle=ref(''),history=ref<any[]>([]),actionDieId=ref(0)
function dataOf(r:any){return r?.data ?? r ?? []}
function refs(s?:string){return (s||'').split('||').filter(Boolean).map(x=>{const [id,...rest]=x.split(':');return {product_id:Number(id),product_code_name:rest.join(':')}})}
function refText(s?:string){return refs(s).map(x=>x.product_code_name).join('；')||'-'}
function productText(list:any[]){return (list||[]).map(x=>`${x.product_code} ${x.product_name}`).join('；')||'-'}
async function load(){loading.value=true;try{if(tab.value==='SCREEN')frames.value=dataOf(await api.frames({keyword:keyword.value||undefined}));else dies.value=dataOf(await api.dies({keyword:keyword.value||undefined}))}finally{loading.value=false}}
async function loadBase(){const r:any=await listProductPage({pageNum:1,pageSize:1000} as any);products.value=r?.data?.records||r?.data?.list||[];films.value=dataOf(await filmApi.list({}))}
function openFrame(row?:any){Object.assign(frameForm,row?{frameId:row.frame_id,frameNo:row.frame_no,frameType:row.frame_type,mesh:row.mesh,location:row.location,remark:row.remark}:{frameId:null,frameNo:'',frameType:'',mesh:'',location:'',remark:''});frameVisible.value=true}
async function saveFrame(){await api.saveFrame(frameForm);ElMessage.success('已保存');frameVisible.value=false;load()}
function openPlate(row:any){Object.assign(plateForm,{frameId:row.frame_id,plateNo:'',filmId:null,content:'',productIds:[],purpose:''});plateVisible.value=true}
async function createPlate(){await api.createPlate(plateForm);ElMessage.success('制版完成');plateVisible.value=false;load()}
async function wash(row:any){const {value}=await ElMessageBox.prompt('请输入洗版原因','洗版确认',{inputType:'textarea'});await api.washFrame(row.frame_id,value);ElMessage.success('已洗版，网框恢复为空框');load()}
async function frameAction(row:any,actionType:string){const labels:Record<string,string>={REPAIR:'送修',ENABLE:'恢复使用',SCRAP:'报废'};const {value}=await ElMessageBox.prompt('请输入处理说明',`${labels[actionType]}确认`,{inputType:'textarea'});await api.frameAction(row.frame_id,{actionType,description:value});ElMessage.success(`网框已${labels[actionType]}`);load()}
async function openDie(row?:any){let ids:number[]=[];if(row)ids=dataOf(await api.products('DIE',row.die_id)).map((x:any)=>x.product_id);Object.assign(dieForm,row?{dieId:row.die_id,dieNo:row.die_no,dieName:row.die_name,purpose:row.purpose,specification:row.specification,version:row.version,location:row.location,productIds:ids}:{dieId:null,dieNo:'',dieName:'',purpose:'',specification:'',version:'',location:'',productIds:[]});dieVisible.value=true}
async function saveDie(){await api.saveDie(dieForm);ElMessage.success('已保存');dieVisible.value=false;load()}
function openAction(row:any){actionDieId.value=row.die_id;Object.assign(actionForm,{actionType:DieActionEnum.REPAIR.value,description:'',newDieNo:''});actionVisible.value=true}
async function saveAction(){await api.dieAction(actionDieId.value,actionForm);ElMessage.success('维护动作已记录');actionVisible.value=false;load()}
function editProducts(type:string,id:number,current:any[],title:string){productType.value=type;productId.value=id;productTitle.value=title;selectedProductIds.value=(current||[]).map(x=>x.product_id);productVisible.value=true}
async function saveProducts(){await api.replaceProducts(productType.value,productId.value,selectedProductIds.value);ElMessage.success('产品关联已保存');productVisible.value=false;load()}
async function showHistory(type:string,id:number){history.value=dataOf(await api.maintenance(type,id));historyVisible.value=true}
watch(()=>route.name,()=>{keyword.value='';load()})
onMounted(async()=>{await loadBase();await load()})
</script>

<style scoped>.resource-page{padding:16px}.header,.toolbar{display:flex;align-items:center;justify-content:space-between}.toolbar{margin-bottom:12px}</style>
