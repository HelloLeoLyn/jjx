<template>
  <div class="iqc-ledger">
    <el-card>
      <el-form inline>
        <el-form-item label="隔离状态"
          ><el-select v-model="status" clearable style="width: 150px" @change="load"
            ><el-option
              v-for="item in IqcQuarantineStatusEnum.items"
              :key="item.value"
              :label="item.label"
              :value="item.value" /></el-select
        ></el-form-item>
        <el-form-item><el-button type="primary" @click="load">刷新</el-button></el-form-item>
      </el-form>
    </el-card>
    <el-card class="card"
      ><el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="materialCode" label="物料编码" width="160" />
        <el-table-column prop="materialName" label="物料名称" min-width="180" />
        <el-table-column prop="batchNo" label="批次" width="160" />
        <el-table-column prop="quantity" label="原始隔离" width="110" />
        <el-table-column prop="remainingQuantity" label="剩余数量" width="110" />
        <el-table-column label="状态" width="100"
          ><template #default="{ row }"
            ><el-tag :type="IqcQuarantineStatusEnum.getTagProps(row.status).type">{{
              IqcQuarantineStatusEnum.getLabel(row.status)
            }}</el-tag></template
          ></el-table-column
        >
        <el-table-column prop="disposition" label="IQC处置建议" width="130" />
        <el-table-column prop="createTime" label="建立时间" width="180" /> </el-table
    ></el-card>
    <el-card class="card"
      ><template #header>供应商返工单</template
      ><el-table v-loading="reworkLoading" :data="reworkOrders" border
        ><el-table-column prop="reworkNo" label="返工单号" width="220" /><el-table-column
          prop="materialCode"
          label="物料"
          width="160" /><el-table-column prop="quantity" label="数量" width="100" /><el-table-column
          label="状态"
          width="120"
          ><template #default="{ row }"
            ><el-tag :type="IqcReworkStatusEnum.getTagProps(row.status).type">{{
              IqcReworkStatusEnum.getLabel(row.status)
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="操作" width="130"
          ><template #default="{ row }"
            ><el-button
              v-if="row.status === IqcReworkStatus.CREATED"
              type="primary"
              link
              @click="completeRework(row)"
              >完成返工</el-button
            ></template
          ></el-table-column
      ></el-table
    ></el-card>
    <el-card class="card"
      ><template #header>处置单历史</template
      ><el-table v-loading="ordersLoading" :data="orders" border
        ><el-table-column prop="dispositionNo" label="处置单号" width="210" /><el-table-column
          prop="action"
          label="类型"
          width="110" /><el-table-column
          prop="materialCode"
          label="物料"
          width="160" /><el-table-column prop="quantity" label="数量" width="100" /><el-table-column
          prop="operatorName"
          label="操作人"
          width="110" /><el-table-column prop="createTime" label="时间" /></el-table
    ></el-card>
  </div>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { inboundApi } from '@/api/inventory/inbound'
import { iqcApi } from '@/api/inventory/iqc'
import { IqcQuarantineStatusEnum } from '@/enums/inventory/IqcQuarantineEnum'
import { IqcReworkStatus, IqcReworkStatusEnum } from '@/enums/inventory/IqcReworkEnum'
const status = ref<string>()
const rows = ref<any[]>([])
const orders = ref<any[]>([])
const reworkOrders = ref<any[]>([])
const loading = ref(false)
const ordersLoading = ref(false)
const reworkLoading = ref(false)
async function load() {
  loading.value = true
  ordersLoading.value = true
  try {
    const [q, o] = await Promise.all([
      inboundApi.listAllQuarantine(status.value),
      inboundApi.listAllDispositionOrders(),
    ])
    rows.value = q.data || []
    orders.value = o.data || []
    reworkLoading.value = true
    const inboundIds = [...new Set(rows.value.map(row => row.inboundId).filter(Boolean))]
    const reworkResults = await Promise.all(inboundIds.map(inboundId => iqcApi.listReworkOrders(String(inboundId))))
    reworkOrders.value = reworkResults.flatMap(result => result.data || [])
  } finally {
    loading.value = false
    ordersLoading.value = false
    reworkLoading.value = false
  }
}
async function completeRework(row: any) {
  await ElMessageBox.confirm('完成返工后将生成新的IQC复检记录，确认继续吗？', '确认完成返工', { type: 'warning' })
  await iqcApi.completeRework(String(row.reworkId))
  ElMessage.success('返工已完成，已生成待复检记录')
  await load()
}
onMounted(load)
</script>
<style scoped>
.iqc-ledger {
  padding: 20px;
}
.card {
  margin-top: 16px;
}
</style>
