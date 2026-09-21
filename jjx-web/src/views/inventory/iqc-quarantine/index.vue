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
    <el-card class="card">
      <template #header>批次谱系</template>
      <el-table v-loading="batchLoading" :data="batchRows" border>
        <el-table-column prop="batchNo" label="批次" width="190" />
        <el-table-column prop="parentBatchNo" label="父批次" width="190" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ batchTypeLabel(row.batchType) }}</template>
        </el-table-column>
        <el-table-column prop="quantity" label="批次数量" width="100" />
        <el-table-column prop="acceptedQuantity" label="合格数量" width="100" />
        <el-table-column prop="rejectedQuantity" label="不良数量" width="100" />
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag :type="IqcBatchStatusEnum.getTagProps(row.status).type">{{
              IqcBatchStatusEnum.getLabel(row.status)
            }}</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-card class="card">
      <template #header>
        <div class="card-title">
          <span>隔离台账（待处置明细）</span>
          <span class="card-tip"
            >来料检验判定为不合格的物料会进这张台账；处置方式只有四种：释放入库 / 退货 /
            返工 / 报废。「剩余数量」减到 0 才算结清，状态才会变成已释放/已退货/已返工/已报废。</span
          >
        </div>
      </template>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="materialCode" label="物料编码" width="150" />
        <el-table-column prop="materialName" label="物料名称" min-width="170" />
        <el-table-column prop="batchNo" label="批次" width="160" />
        <el-table-column label="原始隔离" width="105" align="right">
          <template #default="{ row }">{{ num(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="已处置" width="100" align="right">
          <template #default="{ row }">{{ num(disposedQuantity(row)) }}</template>
        </el-table-column>
        <el-table-column label="剩余数量" width="105" align="right">
          <template #default="{ row }">
            <span :class="{ danger: Number(row.remainingQuantity) > 0 }">{{
              num(row.remainingQuantity)
            }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }">
            <el-tag :type="IqcQuarantineStatusEnum.getTagProps(row.status).type">{{
              IqcQuarantineStatusEnum.getLabel(row.status)
            }}</el-tag>
            <el-tag v-if="isPartial(row)" class="partial" size="small" type="warning" effect="plain"
              >部分处置</el-tag
            >
          </template>
        </el-table-column>
        <el-table-column label="IQC处置建议" width="140">
          <template #default="{ row }">{{ dispositionLabel(row.disposition) }}</template>
        </el-table-column>
        <el-table-column prop="createTime" label="建立时间" width="170" />
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button
              v-if="
                canDispose &&
                row.status === IqcQuarantineStatus.PENDING &&
                Number(row.remainingQuantity) > 0
              "
              type="primary"
              link
              @click="openDisposition(row)"
              >处置</el-button
            >
            <el-tooltip v-else-if="!canDispose" content="当前账号无「隔离处置」权限，请联系品质主管授权">
              <span class="no-perm">无处置权限</span>
            </el-tooltip>
            <span v-else>-</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-card class="card"
      ><template #header>供应商返工单</template
      ><el-table v-loading="reworkLoading" :data="reworkOrders" border
        ><el-table-column prop="reworkNo" label="返工单号" width="220" /><el-table-column
          prop="materialCode"
          label="物料"
          width="160"
        /><el-table-column prop="batchNo" label="父批次" width="180" /><el-table-column
          prop="childBatchNo"
          label="复检子批次"
          width="190"
        /><el-table-column prop="quantity" label="数量" width="100" /><el-table-column
          label="状态"
          width="120"
          ><template #default="{ row }"
            ><el-tag :type="IqcReworkStatusEnum.getTagProps(row.status).type">{{
              IqcReworkStatusEnum.getLabel(row.status)
            }}</el-tag></template
          ></el-table-column
        ><el-table-column label="操作" width="150"
          ><template #default="{ row }"
            ><el-button
              v-if="canDispose && row.status === IqcReworkStatus.CREATED"
              type="primary"
              link
              @click="completeRework(row)"
              >完成返工</el-button
            ><el-button
              v-if="canInspect && row.status === IqcReworkStatus.PENDING_REINSPECTION"
              type="primary"
              link
              @click="goReinspect(row)"
              >去复检</el-button
            ><span
              v-if="
                row.status !== IqcReworkStatus.CREATED &&
                row.status !== IqcReworkStatus.PENDING_REINSPECTION
              "
              >-</span
            ></template
          ></el-table-column
        ></el-table
      ></el-card
    >
    <el-card class="card">
      <template #header>处置单历史（本页已执行的每一次处置）</template>
      <el-table v-loading="ordersLoading" :data="orders" border>
        <el-table-column prop="dispositionNo" label="处置单号" width="210" />
        <el-table-column label="类型" width="120">
          <template #default="{ row }">{{ actionLabel(row.action) }}</template>
        </el-table-column>
        <el-table-column prop="materialCode" label="物料" width="160" />
        <el-table-column prop="quantity" label="数量" width="100" />
        <el-table-column prop="operatorName" label="操作人" width="110" />
        <el-table-column prop="createTime" label="时间" />
      </el-table>
    </el-card>
    <IqcQuarantineDialog
      v-model:visible="dispositionVisible"
      :inbound-id="activeInboundId"
      :inbound-no="activeInboundNo"
      :item-id="activeItemId"
      @success="load"
    />
  </div>
</template>
<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { inboundApi } from '@/api/inventory/inbound'
import { iqcApi } from '@/api/inventory/iqc'
import {
  IqcQuarantineActionEnum,
  IqcQuarantineStatus,
  IqcQuarantineStatusEnum,
} from '@/enums/inventory/IqcQuarantineEnum'
import { IqcReworkStatus, IqcReworkStatusEnum } from '@/enums/inventory/IqcReworkEnum'
import { IqcBatchStatusEnum, IqcBatchTypeEnum } from '@/enums/inventory/IqcBatchEnum'
import { IqcDispositionEnum } from '@/enums/inventory/InboundEnum'
import { hasPermi } from '@/directives'
import IqcQuarantineDialog from '@/views/inventory/inbound/components/IqcQuarantineDialog.vue'
const router = useRouter()
// 2026-09-21 用户定口径 B（隔离处置只给品质主管一侧，见迁移 167）：
// 收回 INVENTORY 业务操作(23)/审核员(24) 的处置入口后，本页与来料检验页的处置按钮
// 只认 quality:ncr:dispose；无权限时显示「无处置权限」提示而不是留白。
const canDispose = computed(() => hasPermi(['quality:ncr:dispose']))
const canInspect = computed(() => hasPermi(['quality:lot:inspect', 'inventory:inbound:edit']))
const status = ref<string>()
const rows = ref<any[]>([])
const orders = ref<any[]>([])
const reworkOrders = ref<any[]>([])
const batchRows = ref<any[]>([])
const loading = ref(false)
const ordersLoading = ref(false)
const reworkLoading = ref(false)
const batchLoading = ref(false)
const dispositionVisible = ref(false)
const activeInboundId = ref<number>()
const activeInboundNo = ref<string>()
const activeItemId = ref<string>()
const num = (value?: number | string | null) =>
  value == null || value === ''
    ? '-'
    : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
/** 已处置数量 = 原始隔离 − 剩余数量；后端部分处置时状态仍停在「待处置」，靠这两个数相减才能看出进度 */
const disposedQuantity = (row: any) =>
  Number(row.quantity || 0) - Number(row.remainingQuantity || 0)
const isPartial = (row: any) =>
  Number(row.remainingQuantity || 0) > 0 && disposedQuantity(row) > 0
const dispositionLabel = (value?: string) => (value ? IqcDispositionEnum.getLabel(value) : '-')
const batchTypeLabel = (value?: string) => (value ? IqcBatchTypeEnum.getLabel(value) : '-')
const actionLabel = (value?: string) => (value ? IqcQuarantineActionEnum.getLabel(value) : '-')

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
    batchLoading.value = true
    const inboundIds = [...new Set(rows.value.map((row) => row.inboundId).filter(Boolean))]
    const [reworkResults, batchResults] = await Promise.all([
      Promise.all(inboundIds.map((inboundId) => iqcApi.listReworkOrders(String(inboundId)))),
      Promise.all(inboundIds.map((inboundId) => iqcApi.listBatches(String(inboundId)))),
    ])
    reworkOrders.value = reworkResults.flatMap((result) => result.data || [])
    batchRows.value = batchResults.flatMap((result) => result.data || [])
  } finally {
    loading.value = false
    ordersLoading.value = false
    reworkLoading.value = false
    batchLoading.value = false
  }
}
async function openDisposition(row: any) {
  activeInboundId.value = Number(row.inboundId)
  activeItemId.value = String(row.inboundItemId)
  activeInboundNo.value = ''
  dispositionVisible.value = true
  try {
    // 弹窗标题要带来源入库单号：此前没传 inbound-no，标题一直是「IQC 隔离处置 - 」
    const { data } = await inboundApi.getById(String(row.inboundId))
    activeInboundNo.value = data?.inboundNo || ''
  } catch {
    activeInboundNo.value = ''
  }
}
async function completeRework(row: any) {
  await ElMessageBox.confirm('完成返工后将生成新的IQC复检记录，确认继续吗？', '确认完成返工', {
    type: 'warning',
  })
  await iqcApi.completeRework(String(row.reworkId))
  ElMessage.success('返工已完成，已生成待复检记录')
  await load()
}
async function goReinspect(row: any) {
  const { data } = await inboundApi.getById(String(row.inboundId))
  await router.push({
    path: '/inventory/iqc',
    query: {
      inboundNo: data?.inboundNo,
      itemId: String(row.inboundItemId),
    },
  })
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
.card-title {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.card-tip {
  color: #909399;
  font-size: 12px;
  font-weight: 400;
  line-height: 1.5;
}
.partial {
  margin-left: 6px;
}
.no-perm {
  color: #c0c4cc;
  font-size: 12px;
  cursor: help;
}
.danger {
  color: #f56c6c;
}
</style>
