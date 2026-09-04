<template>
  <div class="print-page">
    <div class="toolbar no-print">
      <el-button @click="router.back()">返回</el-button
      ><el-button type="primary" icon="Printer" @click="print">打印</el-button>
    </div>
    <A4Canvas :padding-mm="12"
      ><PrintCompanyHeader variant="center" />
      <h1>采购计划建议表</h1>
      <div class="meta">
        <span>日期：{{ today }}</span
        ><span>统计范围：当前安全库存及订单缺料预警</span>
      </div>
      <table>
        <thead>
          <tr>
            <th>物料编码</th>
            <th>名称</th>
            <th>规格</th>
            <th>建议数量</th>
            <th>供应商</th>
            <th>在途</th>
            <th>缺口</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(x, i) in rows" :key="i">
            <td>{{ x.materialCode }}</td>
            <td>{{ x.materialName }}</td>
            <td>{{ x.specification || x.materialSpec || '-' }}</td>
            <td class="right">{{ x.suggestQuantity || 0 }}</td>
            <td>{{ x.supplierName || '-' }}</td>
            <td class="right">{{ x.inTransit || x.inTransitQuantity || 0 }}</td>
            <td class="right">{{ x.shortageQuantity || x.suggestQuantity || 0 }}</td>
          </tr>
          <tr v-if="!rows.length">
            <td colspan="7" class="center">当前无采购建议</td>
          </tr>
        </tbody>
      </table>
      <div class="sign">
        <span>计划人：________________</span><span>确认人：________________</span>
      </div></A4Canvas
    >
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'
import { getPlanSuggestions, logPlanPrint } from '@/api/purchase/order'
const router = useRouter(),
  rows = ref<any[]>([]),
  today = new Date().toLocaleDateString('zh-CN')
async function print() {
  try {
    await logPlanPrint()
    window.print()
  } catch {
    ElMessage.error('打印留痕失败，请重试')
  }
}
onMounted(async () => {
  const r: any = await getPlanSuggestions()
  rows.value = r.data || []
})
</script>
<style scoped>
.print-page {
  min-height: 100vh;
  background: #eef0f3;
  padding: 20px;
}
.toolbar {
  max-width: 794px;
  margin: 0 auto 16px;
  display: flex;
  justify-content: space-between;
}
h1 {
  text-align: center;
  font-size: 20px;
  letter-spacing: 4px;
  border-bottom: 2px solid #2b5aa7;
  padding-bottom: 10px;
}
.meta {
  display: flex;
  justify-content: space-between;
  margin: 12px 0;
  font-size: 11px;
}
table {
  width: 100%;
  border-collapse: collapse;
  font-size: 10px;
}
th,
td {
  border: 1px solid #bbb;
  padding: 6px;
}
th {
  background: #2b5aa7;
  color: white;
}
.right {
  text-align: right;
}
.center {
  text-align: center;
}
.sign {
  display: flex;
  justify-content: space-between;
  margin-top: 60px;
  font-size: 12px;
}
@media print {
  .print-page {
    padding: 0;
    background: white;
  }
}
</style>
