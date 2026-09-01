<template>
  <div class="app-container">
    <el-card shadow="never" class="search-card">
      <el-form :model="query" inline>
        <el-form-item label="订单号"><el-input v-model="query.orderNo" clearable placeholder="订单号" style="width: 180px" /></el-form-item>
        <el-form-item label="供应商"><el-input v-model="query.supplierName" clearable placeholder="供应商名称" style="width: 180px" /></el-form-item>
        <el-form-item><el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button></el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never">
      <el-table v-loading="loading" :data="filteredRows" border row-key="orderId">
        <el-table-column type="expand">
          <template #default="{ row }">
            <el-table :data="row.items || []" border size="small" class="detail-table">
              <el-table-column prop="materialCode" label="物料编码" min-width="120" />
              <el-table-column prop="materialName" label="物料名称" min-width="140" />
              <el-table-column prop="materialSpec" label="规格" min-width="100" />
              <el-table-column prop="unit" label="单位" width="60" />
              <el-table-column prop="quantity" label="订购数量" width="90" align="right" />
              <el-table-column prop="receivedQuantity" label="已收数量" width="90" align="right" />
              <el-table-column label="收货状态" width="100">
                <template #default="{ row: item }"><el-tag :type="ReceiptStatusEnum.getTagProps(item.receiptStatus).type">{{ ReceiptStatusEnum.getLabel(item.receiptStatus) }}</el-tag></template>
              </el-table-column>
              <el-table-column label="检验结果" width="90">
                <template #default="{ row: item }">
                  <el-tag v-if="item.inspectionResult" :type="InspectionResultEnum.getTagProps(item.inspectionResult).type">{{ InspectionResultEnum.getLabel(item.inspectionResult) }}</el-tag>
                  <span v-else>-</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="180" fixed="right">
                <template #default="{ row: item }">
                  <el-button v-if="item.receiptStatus !== ReceiptStatusEnum.RECEIVED.value" v-hasPermi="['purchase:receipt:edit']" link type="primary" @click="openConfirm(row, item)">收货</el-button>
                  <el-button v-hasPermi="['purchase:receipt:edit']" link type="warning" @click="openInspect(row, item)">检验</el-button>
                </template>
              </el-table-column>
            </el-table>
          </template>
        </el-table-column>
        <el-table-column prop="orderNo" label="订单号" min-width="160" />
        <el-table-column prop="supplierName" label="供应商" min-width="150" />
        <el-table-column prop="orderDate" label="订单日期" width="110" />
        <el-table-column prop="orderTotalAmount" label="订单金额" width="120" align="right">
          <template #default="{ row }">{{ money(row.orderTotalAmount) }}</template>
        </el-table-column>
        <el-table-column label="收货进度" width="120">
          <template #default="{ row }">
            <el-tag :type="ReceiptStatusEnum.getTagProps(row.receiptStatus).type">{{ ReceiptStatusEnum.getLabel(row.receiptStatus) }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="confirmVisible" title="确认收货" width="480px">
      <el-form :model="confirmForm" label-width="90px">
        <el-form-item label="物料">{{ confirmForm.materialName || '-' }}</el-form-item>
        <el-form-item label="订购数量">{{ confirmForm.quantity ?? '-' }}</el-form-item>
        <el-form-item label="已收数量">{{ confirmForm.receivedQuantity ?? 0 }}</el-form-item>
        <el-form-item label="本次收货" required>
          <el-input-number v-model="confirmForm.receivedQuantity" :min="0.01" :precision="2" :controls="false" style="width: 100%" />
        </el-form-item>
        <el-form-item label="收货人"><el-input v-model="confirmForm.receiverName" placeholder="收货人姓名" /></el-form-item>
        <el-form-item label="收货日期"><el-date-picker v-model="confirmForm.receiptDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="confirmForm.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="confirmVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitConfirm">确认收货</el-button></template>
    </el-dialog>

    <el-dialog v-model="inspectVisible" title="来料检验" width="480px">
      <el-form :model="inspectForm" label-width="90px">
        <el-form-item label="物料">{{ inspectForm.materialName || '-' }}</el-form-item>
        <el-form-item label="检验结果" required>
          <el-radio-group v-model="inspectForm.inspectionResult">
            <el-radio v-for="item in InspectionResultEnum.items" :key="item.value" :value="item.value">{{ item.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="检验人"><el-input v-model="inspectForm.inspectorName" placeholder="检验人姓名" /></el-form-item>
        <el-form-item label="检验日期"><el-date-picker v-model="inspectForm.inspectionDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="inspectForm.inspectionRemark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer><el-button @click="inspectVisible = false">取消</el-button><el-button type="primary" :loading="submitting" @click="submitInspect">保存检验结果</el-button></template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { listReceipt, confirmReceipt, inspectReceipt } from '@/api/purchase/receipt'
import { ReceiptStatusEnum, InspectionResultEnum } from '@/enums/purchase/receipt'

defineOptions({ name: 'PurchaseReceipt' })

const loading = ref(false)
const submitting = ref(false)
const rows = ref<any[]>([])
const confirmVisible = ref(false)
const inspectVisible = ref(false)
const confirmTarget = ref<any>()
const inspectTarget = ref<any>()
const query = reactive({ orderNo: '', supplierName: '' })

const confirmForm = reactive<any>({ receivedQuantity: 0, receiverName: '', receiptDate: '', remark: '' })
const inspectForm = reactive<any>({ inspectionResult: 'passed', inspectorName: '', inspectionDate: '', inspectionRemark: '' })

const money = (v?: number) => v == null ? '-' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

function today() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60_000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

const filteredRows = computed(() => {
  const q = query
  if (!q.orderNo && !q.supplierName) return rows.value
  return rows.value.filter((r) =>
    (!q.orderNo || (r.orderNo || '').includes(q.orderNo)) &&
    (!q.supplierName || (r.supplierName || '').includes(q.supplierName)),
  )
})

async function load() {
  loading.value = true
  try {
    const res: any = await listReceipt()
    rows.value = res.data || []
  } finally {
    loading.value = false
  }
}
function search() { /* 前端过滤由 computed 响应 query 变化，无需额外处理 */ }
function reset() { Object.assign(query, { orderNo: '', supplierName: '' }) }

function openConfirm(order: any, item: any) {
  confirmTarget.value = item
  Object.assign(confirmForm, {
    materialName: item.materialName,
    quantity: item.quantity,
    receivedQuantity: item.receivedQuantity || 0,
    receiverName: '',
    receiptDate: today(),
    remark: '',
  })
  confirmVisible.value = true
}
async function submitConfirm() {
  if (!confirmTarget.value) return
  const item = confirmTarget.value
  const amount = Number(confirmForm.receivedQuantity)
  if (!amount || amount <= 0) { ElMessage.warning('请输入收货数量'); return }
  submitting.value = true
  try {
    await confirmReceipt(item.itemId, amount, confirmForm.receiverName || '系统', confirmForm.receiptDate, confirmForm.remark)
    ElMessage.success('收货成功')
    confirmVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '收货失败')
  } finally {
    submitting.value = false
  }
}

function openInspect(order: any, item: any) {
  inspectTarget.value = item
  Object.assign(inspectForm, {
    materialName: item.materialName,
    inspectionResult: item.inspectionResult || 'passed',
    inspectorName: '',
    inspectionDate: today(),
    inspectionRemark: '',
  })
  inspectVisible.value = true
}
async function submitInspect() {
  if (!inspectTarget.value) return
  if (!inspectForm.inspectorName) { ElMessage.warning('请填写检验人'); return }
  submitting.value = true
  try {
    await inspectReceipt(inspectTarget.value.itemId, inspectForm.inspectionResult, inspectForm.inspectorName, inspectForm.inspectionDate, inspectForm.inspectionRemark)
    ElMessage.success('检验结果已保存')
    inspectVisible.value = false
    load()
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    submitting.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.search-card { margin-bottom: 16px; }
.detail-table { margin: 8px 16px; }
</style>
