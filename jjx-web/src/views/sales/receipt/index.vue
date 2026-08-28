<template>
  <div class="app-container">
    <el-card shadow="never">
      <el-form :inline="true" :model="query">
        <el-form-item label="单号"><el-input v-model="query.receiptNo" clearable placeholder="收款单号" /></el-form-item>
        <el-form-item label="客户"><el-input v-model="query.customerName" clearable placeholder="客户名称" /></el-form-item>
        <el-form-item label="日期"><el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" range-separator="至" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="query.status" clearable style="width: 120px"><el-option v-for="item in SalesFinanceDocumentStatusEnum.items" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
      <!-- 扩展位：后续新增/编辑收款单按钮放在此处。 -->
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="receiptNo" label="单号" min-width="150" /><el-table-column prop="customerName" label="客户" min-width="150" />
        <el-table-column prop="receiptDate" label="日期" width="120" /><el-table-column label="收款方式" width="120"><template #default="{ row }">{{ SalesReceiptPaymentMethodEnum.getLabel(row.paymentMethod) }}</template></el-table-column>
        <el-table-column label="金额" width="120" align="right"><template #default="{ row }">{{ money(row.receiptAmount) }}</template></el-table-column>
        <el-table-column label="实收" width="120" align="right"><template #default="{ row }">{{ money(row.actualAmount) }}</template></el-table-column>
        <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="SalesFinanceDocumentStatusEnum.getTagProps(row.status).type">{{ SalesFinanceDocumentStatusEnum.getLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="print(row.receiptId)">打印</el-button><el-button link @click="detail(row.receiptId)">详情</el-button></template></el-table-column>
      </el-table>
      <el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
    </el-card>
    <el-dialog v-model="detailVisible" title="收款单详情" width="620px"><el-descriptions v-if="current" :column="2" border><el-descriptions-item v-for="item in detailItems" :key="item.label" :label="item.label">{{ item.value }}</el-descriptions-item></el-descriptions></el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { salesReceiptApi, type SalesReceipt } from '@/api/sales/receipt'
import { SalesFinanceDocumentStatusEnum, SalesReceiptPaymentMethodEnum } from '@/enums/sales'
const loading=ref(false), rows=ref<SalesReceipt[]>([]), total=ref(0), dateRange=ref<string[]>([]), detailVisible=ref(false), current=ref<SalesReceipt>()
const query=reactive({pageNum:1,pageSize:10,receiptNo:'',customerName:'',status:undefined as number|undefined})
const money=(v?:number)=>v==null?'-':Number(v).toLocaleString('zh-CN',{minimumFractionDigits:2})
async function load(){loading.value=true;try{const r:any=await salesReceiptApi.page({...query,startDate:dateRange.value?.[0],endDate:dateRange.value?.[1]});rows.value=r.data?.records||[];total.value=r.data?.total||0}finally{loading.value=false}}
function search(){query.pageNum=1;load()} function reset(){query.receiptNo='';query.customerName='';query.status=undefined;dateRange.value=[];search()}
function print(id:number){window.open(`/sales/receipt/print/${id}`,'_blank')}
async function detail(id:number){const r:any=await salesReceiptApi.detail(id);current.value=r.data;detailVisible.value=true}
const detailItems=computed(()=>{const x=current.value;if(!x)return[];return [{label:'单号',value:x.receiptNo},{label:'客户',value:x.customerName||'-'},{label:'日期',value:x.receiptDate||'-'},{label:'收款方式',value:SalesReceiptPaymentMethodEnum.getLabel(x.paymentMethod!)},{label:'应收',value:money(x.receiptAmount)},{label:'实收',value:money(x.actualAmount)},{label:'状态',value:SalesFinanceDocumentStatusEnum.getLabel(x.status)},{label:'备注',value:x.remark||'-'}]})
onMounted(load)
</script>
