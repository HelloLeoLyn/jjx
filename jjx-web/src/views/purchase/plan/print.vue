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
        ><span>{{
          snapshotMode ? '统计范围：当前采购计划快照' : '统计范围：当前安全库存及订单缺料预警'
        }}</span>
      </div>
      <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" />
      <table>
        <thead>
          <tr>
            <th v-for="column in columns" :key="column">{{ column }}</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="(row, i) in rows" :key="i">
            <td v-for="column in columns" :key="column">
              {{ row[column] === '' || row[column] == null ? '—' : row[column] }}
            </td>
          </tr>
          <tr v-if="!rows.length">
            <td :colspan="columns.length" class="center">暂无计划数据</td>
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
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import A4Canvas from '@/components/A4Canvas/index.vue'
import PrintCompanyHeader from '@/components/PrintCompanyHeader.vue'
import { getPlanSuggestions, logPlanPrint } from '@/api/purchase/order'
const route = useRoute()
const snapshotMode = !!route.query.snapshot
const loadError = ref('')
const columns = [
  '物料编码',
  '物料名称',
  '规格',
  '原始需求',
  '基本单位',
  '计划采购量',
  '采购单位',
  '折合基本数量',
  '需求日期',
  '采购说明',
]
const router = useRouter(),
  rows = ref<any[]>([]),
  today = new Date().toLocaleDateString('zh-CN')
async function print() {
  try {
    if (loadError.value || !rows.value.length) {
      ElMessage.warning('没有可打印的计划数据')
      return
    }
    await logPlanPrint()
    window.print()
  } catch {
    ElMessage.error('打印留痕失败，请重试')
  }
}
onMounted(async () => {
  try {
    if (snapshotMode) {
      const key = String(route.query.snapshot)
      if (!key.startsWith('purchase-plan-print-')) throw new Error('无效的计划打印快照')
      const text = sessionStorage.getItem(key)
      if (!text) throw new Error('计划打印快照已失效，请回到采购计划重新打印')
      const data = JSON.parse(text)
      if (!Array.isArray(data)) throw new Error('计划打印快照格式错误')
      rows.value = data
      sessionStorage.removeItem(key)
    } else {
      const r: any = await getPlanSuggestions()
      rows.value = (r.data || []).map((x: any) => ({
        物料编码: x.materialCode,
        物料名称: x.materialName,
        规格: x.specification || x.materialSpec,
        原始需求: x.suggestQuantity,
        基本单位: x.unit,
        计划采购量: x.suggestQuantity,
        采购单位: x.unit,
        折合基本数量: x.suggestQuantity,
        采购说明: x.reason,
      }))
    }
  } catch (e: any) {
    loadError.value = e?.message || '计划读取失败，请重试'
  }
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
  border-bottom: 2px solid var(--doc-theme, #2b5aa7);
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
  background: var(--doc-theme, #2b5aa7);
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
