<template>
  <div>
    <el-card>
      <template #header><div class="header"><span>CAPA 纠正预防措施</span><div><el-select v-model="query.status" clearable placeholder="状态" @change="load(1)"><el-option v-for="item in CapaStatusEnum.items" :key="item.value" :label="item.label" :value="item.value" /></el-select><el-button type="primary" @click="openCreate">新建</el-button><el-button @click="load()">刷新</el-button></div></div></template>
      <el-alert type="info" :closable="false" title="闭环：待分析 → 措施执行中 → 待验证 → 已关闭；存在未关闭 CAPA 时关联不良单不能结案。" />
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="capaNo" label="CAPA编号" width="190" />
        <el-table-column prop="ncrId" label="不良单ID" width="100" />
        <el-table-column prop="rootCauseCategory" label="根因分类" width="130" />
        <el-table-column prop="rootCause" label="根因" min-width="160" show-overflow-tooltip />
        <el-table-column prop="actionPlan" label="措施" min-width="180" show-overflow-tooltip />
        <el-table-column prop="ownerName" label="责任人" width="110" />
        <el-table-column prop="dueDate" label="期限" width="120" />
        <el-table-column label="状态" width="130"><template #default="{row}"><el-tag :type="CapaStatusEnum.getTagProps(row.status).type">{{ CapaStatusEnum.getLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="110"><template #default="{row}"><el-button v-if="row.status !== CapaStatus.CLOSED" link type="primary" @click="openAdvance(row)">推进</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" layout="total, prev, pager, next" @current-change="load()" />
    </el-card>
    <el-dialog v-model="createVisible" title="新建 CAPA" width="480px"><el-form label-width="100px"><el-form-item label="不良单ID" required><el-input-number v-model="createForm.ncrId" :min="1" /></el-form-item><el-form-item label="责任人"><el-input v-model="createForm.ownerName" /></el-form-item><el-form-item label="期限"><el-date-picker v-model="createForm.dueDate" value-format="YYYY-MM-DD" /></el-form-item></el-form><template #footer><el-button @click="createVisible=false">取消</el-button><el-button type="primary" @click="submitCreate">创建</el-button></template></el-dialog>
    <el-dialog v-model="visible" title="推进 CAPA" width="560px"><el-form label-width="100px">
      <template v-if="current?.status === CapaStatus.PENDING_ANALYSIS"><el-form-item label="根因分类"><el-input v-model="form.rootCauseCategory" /></el-form-item><el-form-item label="根因"><el-input v-model="form.rootCause" type="textarea" /></el-form-item><el-form-item label="措施计划"><el-input v-model="form.actionPlan" type="textarea" /></el-form-item><el-form-item label="责任人"><el-input v-model="form.ownerName" /></el-form-item><el-form-item label="期限"><el-date-picker v-model="form.dueDate" value-format="YYYY-MM-DD" /></el-form-item></template>
      <el-form-item v-if="current?.status === CapaStatus.PENDING_VERIFICATION" label="验证结论"><el-input v-model="form.verificationResult" type="textarea" /></el-form-item>
      <el-alert v-if="current?.status === CapaStatus.ACTION_IN_PROGRESS" type="warning" :closable="false" title="确认措施已经执行完成，将进入待验证。" />
    </el-form><template #footer><el-button @click="visible=false">取消</el-button><el-button type="primary" @click="submit">确认推进</el-button></template></el-dialog>
  </div>
</template>
<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { qualityCapaApi } from '@/api/quality/lot'
import { CapaStatus, CapaStatusEnum } from '@/enums/quality/CapaEnum'
const loading=ref(false), rows=ref<any[]>([]), total=ref(0), visible=ref(false), createVisible=ref(false), current=ref<any>()
const query=reactive({pageNum:1,pageSize:10,status:''})
const form=reactive({rootCauseCategory:'',rootCause:'',actionPlan:'',ownerName:'',dueDate:'',verificationResult:''})
const createForm=reactive({ncrId:undefined as number|undefined,ownerName:'',dueDate:''})
async function load(page?:number){if(page)query.pageNum=page;loading.value=true;try{const r:any=await qualityCapaApi.page({...query});rows.value=r?.data?.records||[];total.value=Number(r?.data?.total||0)}finally{loading.value=false}}
function openAdvance(row:any){current.value=row;Object.assign(form,{rootCauseCategory:row.rootCauseCategory||'',rootCause:row.rootCause||'',actionPlan:row.actionPlan||'',ownerName:row.ownerName||'',dueDate:row.dueDate||'',verificationResult:''});visible.value=true}
function openCreate(){Object.assign(createForm,{ncrId:undefined,ownerName:'',dueDate:''});createVisible.value=true}
async function submitCreate(){if(!createForm.ncrId)return ElMessage.warning('请输入不良单ID');await qualityCapaApi.create({...createForm});ElMessage.success('CAPA 已创建');createVisible.value=false;load(1)}
async function submit(){await qualityCapaApi.advance(current.value.capaId,{...form});ElMessage.success('CAPA 已推进');visible.value=false;load()}
onMounted(()=>load(1))
</script>
<style scoped>.header{display:flex;justify-content:space-between;align-items:center}.el-alert{margin-bottom:16px}</style>
