<template>
  <div class="resource-page">
    <el-card shadow="never">
      <template #header>
        <div class="header"><span>{{ tab === 'SCREEN' ? '历史网版台账' : '刀模管理' }}</span><el-input v-if="tab !== 'SCREEN'" v-model="keyword" clearable placeholder="编号/名称/内容" style="width:260px" @keyup.enter="search" @clear="search" /></div>
      </template>
      <template v-if="tab === 'SCREEN'">
        <el-form :inline="true" class="frame-search" @submit.prevent>
          <el-form-item label="网框编号"><el-input v-model="frameQuery.frameNo" clearable style="width:130px" @keyup.enter="searchFrames" /></el-form-item>
          <el-form-item label="型号"><el-input v-model="frameQuery.frameType" clearable style="width:110px" @keyup.enter="searchFrames" /></el-form-item>
          <el-form-item label="目数"><el-input v-model="frameQuery.mesh" clearable style="width:90px" @keyup.enter="searchFrames" /></el-form-item>
          <el-form-item label="状态"><el-select v-model="frameQuery.status" clearable placeholder="全部" style="width:120px"><el-option v-for="o in ScreenFrameStatusEnum.items" :key="o.value" :label="o.label" :value="o.value" /></el-select></el-form-item>
          <el-form-item label="位置"><el-input v-model="frameQuery.location" clearable style="width:120px" @keyup.enter="searchFrames" /></el-form-item>
          <el-form-item label="当前版面"><el-input v-model="frameQuery.plateNo" clearable style="width:130px" @keyup.enter="searchFrames" /></el-form-item>
          <el-form-item label="版面内容"><el-input v-model="frameQuery.content" clearable style="width:140px" @keyup.enter="searchFrames" /></el-form-item>
          <el-form-item label="关联产品"><el-input v-model="frameQuery.productKeyword" clearable placeholder="编码/名称" style="width:150px" @keyup.enter="searchFrames" /></el-form-item>
          <el-form-item>
            <el-button type="primary" @click="searchFrames">查询</el-button>
            <el-button @click="resetFrameQuery">重置</el-button>
          </el-form-item>
        </el-form>
        <el-table :data="frames" v-loading="loading" border>
          <el-table-column prop="frame_no" label="网框编号" width="130" />
          <el-table-column prop="frame_type" label="型号" width="100" />
          <el-table-column prop="mesh" label="目数" width="80" />
          <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="ScreenFrameStatusEnum.getTagProps(row.status).type">{{ ScreenFrameStatusEnum.getLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column prop="current_plate_no" label="当前版面" width="150" />
          <el-table-column prop="plate_content" label="版面内容" min-width="180" show-overflow-tooltip />
          <el-table-column label="关联产品" min-width="220"><template #default="{row}">{{ refText(row.product_refs) }}</template></el-table-column>
          <el-table-column prop="location" label="位置" width="120" />
          <TableActionColumn :actions="frameActions" width="80" display="text" @action="handleFrameAction" />
        </el-table>
        <pagination v-show="framePage.total > 0" v-model:page="framePage.pageNum" v-model:limit="framePage.pageSize" :total="framePage.total" @pagination="load" />
      </template>

      <template v-else>
        <div class="toolbar">
          <el-button type="primary" v-hasPermi="['engineering:resource:edit']" @click="openDie()">新增刀模</el-button>
          <el-button v-hasPermi="['engineering:die:import']" @click="downloadDieTemplate">下载导入模板</el-button>
          <el-upload
            v-hasPermi="['engineering:die:import']"
            :show-file-list="false"
            accept=".xlsx,.xls"
            :before-upload="onDieFile"
            style="display:inline-block;margin-left:12px"
          >
            <el-button type="warning" :loading="dieImporting">导入刀模</el-button>
          </el-upload>
          <span class="muted">支持 Excel 批量导入，按刀模编号自动新增/更新</span>
        </div>
        <el-table :data="dies" v-loading="loading" border>
          <el-table-column prop="die_no" label="刀模编号" width="130" />
          <el-table-column prop="die_name" label="名称" min-width="150" />
          <el-table-column prop="purpose" label="用途" min-width="130" />
          <el-table-column prop="version" label="版本" width="80" />
          <el-table-column label="状态" width="100"><template #default="{row}"><el-tag :type="DieStatusEnum.getTagProps(row.status).type">{{ DieStatusEnum.getLabel(row.status) }}</el-tag></template></el-table-column>
          <el-table-column label="关联产品" min-width="220"><template #default="{row}">{{ refText(row.product_refs) }}</template></el-table-column>
          <el-table-column prop="quantity" label="数量" width="80" />
          <el-table-column prop="stock_in_date" label="入库日期" width="120" />
          <el-table-column prop="location" label="位置" min-width="140" show-overflow-tooltip />
          <TableActionColumn :actions="dieActions" width="220" display="text" @action="handleDieAction" />
        </el-table>
        <pagination v-show="diePage.total > 0" v-model:page="diePage.pageNum" v-model:limit="diePage.pageSize" :total="diePage.total" @pagination="load" />
      </template>
    </el-card>

    <el-dialog v-model="dieVisible" :title="dieForm.dieId ? '编辑刀模' : '新增刀模'" width="600px">
      <el-form :model="dieForm" label-width="90px"><el-form-item label="刀模编号" required><el-input v-model="dieForm.dieNo" /></el-form-item><el-form-item label="名称" required><el-input v-model="dieForm.dieName" /></el-form-item><el-form-item label="用途"><el-input v-model="dieForm.purpose" /></el-form-item><el-form-item label="规格"><el-input v-model="dieForm.specification" type="textarea" /></el-form-item><el-form-item label="版本"><el-input v-model="dieForm.version" /></el-form-item><el-form-item label="位置"><el-input v-model="dieForm.location" /></el-form-item><el-form-item label="关联产品"><el-select v-model="dieForm.productIds" multiple filterable remote :remote-method="searchProducts" :loading="productsLoading" placeholder="输入至少 2 个字符搜索产品" style="width:100%"><el-option v-for="p in products" :key="p.productId" :value="p.productId" :label="productLabel(p)" /></el-select></el-form-item></el-form>
      <template #footer><el-button @click="dieVisible=false">取消</el-button><el-button type="primary" @click="saveDie">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="actionVisible" title="刀模维护" width="520px"><el-form label-width="90px"><el-form-item label="动作"><el-select v-model="actionForm.actionType"><el-option v-for="a in DieActionEnum.items" :key="a.value" :value="a.value" :label="a.label" /></el-select></el-form-item><el-form-item v-if="actionForm.actionType === DieActionEnum.REMAKE.value" label="新刀模编号" required><el-input v-model="actionForm.newDieNo" /></el-form-item><el-form-item label="说明"><el-input v-model="actionForm.description" type="textarea" /></el-form-item></el-form><template #footer><el-button @click="actionVisible=false">取消</el-button><el-button type="primary" @click="saveAction">确认</el-button></template></el-dialog>
    <el-dialog v-model="importVisible" :title="importTitle" width="560px">
      <el-alert
        v-if="importResult"
        :type="importResult.failed > 0 ? 'warning' : 'success'"
        :closable="false"
        show-icon
        :title="`共 ${importResult.total} 行：新增 ${importResult.inserted} / 更新 ${importResult.updated}${importResult.plates != null ? ` / 版面 ${importResult.plates}` : ''} / 失败 ${importResult.failed}`"
        style="margin-bottom: 12px"
      />
      <div v-if="importResult?.errors?.length">
        <p class="muted">失败明细（最多 20 条）：</p>
        <ul class="err-list"><li v-for="(e, i) in importResult.errors" :key="i">{{ e }}</li></ul>
      </div>
      <template #footer><el-button type="primary" @click="importVisible = false">知道了</el-button></template>
    </el-dialog>

    <el-dialog v-model="historyVisible" title="维护履历" width="760px"><el-table :data="history" border><el-table-column prop="operate_time" label="时间" width="170"/><el-table-column prop="action_type" label="动作" width="100"/><el-table-column prop="before_status" label="原状态" width="100"/><el-table-column prop="after_status" label="新状态" width="100"/><el-table-column prop="operator" label="操作人" width="100"/><el-table-column prop="description" label="说明" min-width="180"/></el-table></el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { download } from '@/utils/format'
import { engineeringResourceApi as api } from '@/api/engineering/resource'
import { searchProduct } from '@/api/product'
import { DieActionEnum, DieStatusEnum, ScreenFrameStatusEnum } from '@/enums/engineering/resource'
import type { TableAction } from '@/components/common-ui/TableActionColumn/types'

defineOptions({ name: 'EngineeringResource' })
const route=useRoute()
const tab=computed(()=>route.name === 'EngineeringDieResource' ? 'DIE' : 'SCREEN')
const keyword=ref(''),loading=ref(false),frames=ref<any[]>([]),dies=ref<any[]>([]),products=ref<any[]>([])
const productsLoading=ref(false)
const productSearchCache=new Map<string,any[]>()
let productSearchSeq=0
const dieVisible=ref(false),actionVisible=ref(false),historyVisible=ref(false)
const dieForm=reactive<any>({productIds:[]}),actionForm=reactive<any>({actionType:DieActionEnum.REPAIR.value,description:'',newDieNo:''})
const history=ref<any[]>([]),actionDieId=ref(0)
const frameActions:TableAction<any>[]=[
  {key:'history',label:'履历',permission:'engineering:screen-resource:view'},
]
const dieActions:TableAction<any>[]=[
  {key:'edit',label:'编辑',permission:'engineering:resource:edit'},
  {key:'maintain',label:'维护',type:'warning',permission:'engineering:resource:maintain',visible:({row})=>row.status!==DieStatusEnum.SCRAPPED.value&&row.status!==DieStatusEnum.REPLACED.value},
  {key:'history',label:'履历'},
]
/** 刀模导入（2026-09-21）：模板下载 + Excel 上传 + 结果回执 */
const importVisible=ref(false),dieImporting=ref(false),importResult=ref<any>(null),importTitle=ref('导入结果')
async function downloadDieTemplate(){
  try{
    const res:any=await api.dieImportTemplate()
    download(res.data,'刀模导入模板.xlsx')
  }catch(e:any){ElMessage.error(e?.message||'模板下载失败')}
}
async function onDieFile(file:File){
  dieImporting.value=true
  try{
    const res:any=await api.importDies(file)
    importResult.value=res?.data||null
    importTitle.value='刀模导入结果'
    importVisible.value=true
    if(importResult.value?.failed>0) ElMessage.warning(`导入完成：新增 ${importResult.value.inserted}，更新 ${importResult.value.updated}，失败 ${importResult.value.failed}`)
    else ElMessage.success(`导入完成：新增 ${importResult.value?.inserted||0}，更新 ${importResult.value?.updated||0}`)
    await load()
  }catch(e:any){ElMessage.error(e?.message||'导入失败')}
  finally{dieImporting.value=false}
  return false
}
function dataOf(r:any){return r?.data ?? r ?? []}
function refs(s?:string){return (s||'').split('||').filter(Boolean).map(x=>{const [id,...rest]=x.split(':');return {product_id:Number(id),product_code_name:rest.join(':')}})}
function refText(s?:string){return refs(s).map(x=>x.product_code_name).join('；')||'-'}
/** 分页（2026-09-21 性能改造）：老台账导入后网框 7,291 / 刀模 12,134，全表返 2.8~4.8MB 太慢 */
const framePage=reactive({pageNum:1,pageSize:20,total:0})
const diePage=reactive({pageNum:1,pageSize:20,total:0})
/** 网版管理搜索表单（2026-10-09：编号/型号/目数/状态/位置/当前版面/版面内容/关联产品） */
const frameQuery=reactive({frameNo:'',frameType:'',mesh:'',location:'',plateNo:'',content:'',productKeyword:'',status:''})
async function load(){
  loading.value=true
  try{
    if(tab.value==='SCREEN'){
      const r:any=await api.frames({frameNo:frameQuery.frameNo||undefined,frameType:frameQuery.frameType||undefined,mesh:frameQuery.mesh||undefined,location:frameQuery.location||undefined,plateNo:frameQuery.plateNo||undefined,content:frameQuery.content||undefined,productKeyword:frameQuery.productKeyword||undefined,status:frameQuery.status||undefined,pageNum:framePage.pageNum,pageSize:framePage.pageSize})
      frames.value=r?.data?.records||[]
      framePage.total=r?.data?.total||0
    }else{
      const r:any=await api.dies({keyword:keyword.value||undefined,pageNum:diePage.pageNum,pageSize:diePage.pageSize})
      dies.value=r?.data?.records||[]
      diePage.total=r?.data?.total||0
    }
  }finally{loading.value=false}
}
/** 搜索/切 tab 回到第一页 */
function search(){if(tab.value==='SCREEN')framePage.pageNum=1;else diePage.pageNum=1;load()}
/** 网版管理搜索/重置 */
function searchFrames(){framePage.pageNum=1;load()}
function resetFrameQuery(){Object.assign(frameQuery,{frameNo:'',frameType:'',mesh:'',location:'',plateNo:'',content:'',productKeyword:'',status:''});framePage.pageNum=1;load()}
function productLabel(p:any){return p.productCodeName||`${p.productCode||''} ${p.productName||''}`.trim()}
function normalizeProduct(p:any){return {productId:Number(p.productId??p.product_id),productCode:p.productCode??p.product_code,productName:p.productName??p.product_name,productCodeName:p.productCodeName??p.product_code_name}}
function mergeProducts(items:any[]){const merged=new Map(products.value.map(p=>[Number(p.productId),p]));items.map(normalizeProduct).filter(p=>Number.isFinite(p.productId)).forEach(p=>merged.set(p.productId,p));products.value=[...merged.values()]}
async function searchProducts(query:string){
  const q=query.trim()
  const seq=++productSearchSeq
  if(q.length<2){productsLoading.value=false;return}
  const cached=productSearchCache.get(q)
  if(cached){productsLoading.value=false;mergeProducts(cached);return}
  productsLoading.value=true
  try{
    const items=dataOf(await searchProduct(q,undefined,'active'))
    productSearchCache.set(q,items)
    if(seq===productSearchSeq)mergeProducts(items)
  }finally{if(seq===productSearchSeq)productsLoading.value=false}
}
async function openDie(row?:any){let linked:any[]=[];if(row){linked=dataOf(await api.products('DIE',row.die_id));mergeProducts(linked)}const ids=linked.map((x:any)=>Number(x.product_id??x.productId));Object.assign(dieForm,row?{dieId:row.die_id,dieNo:row.die_no,dieName:row.die_name,purpose:row.purpose,specification:row.specification,version:row.version,location:row.location,productIds:ids}:{dieId:null,dieNo:'',dieName:'',purpose:'',specification:'',version:'',location:'',productIds:[]});dieVisible.value=true}
async function saveDie(){await api.saveDie(dieForm);ElMessage.success('已保存');dieVisible.value=false;load()}
function openAction(row:any){actionDieId.value=row.die_id;Object.assign(actionForm,{actionType:DieActionEnum.REPAIR.value,description:'',newDieNo:''});actionVisible.value=true}
async function saveAction(){await api.dieAction(actionDieId.value,actionForm);ElMessage.success('维护动作已记录');actionVisible.value=false;load()}
async function showHistory(type:string,id:number){history.value=dataOf(await api.maintenance(type,id));historyVisible.value=true}
function handleFrameAction(key:string,row:any){if(key==='history')void showHistory('SCREEN_FRAME',row.frame_id)}
function handleDieAction(key:string,row:any){if(key==='edit')void openDie(row);if(key==='maintain')openAction(row);if(key==='history')void showHistory('DIE',row.die_id)}
watch(()=>route.name,()=>{keyword.value='';framePage.pageNum=1;diePage.pageNum=1;load()})
onMounted(load)
</script>

<style scoped>.resource-page{padding:16px}.header,.toolbar{display:flex;align-items:center}.toolbar{margin-bottom:12px}.muted{color:#909399;font-size:12px;margin-left:8px}.err-list{max-height:240px;overflow:auto;color:#e6a23c;font-size:12px;line-height:20px}</style>
