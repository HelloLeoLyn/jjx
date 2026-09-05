<template>
  <div v-loading="loading" class="inbound-detail">
    <template v-if="inbound">
      <!-- 基本信息 -->
      <el-descriptions :column="3" border>
        <el-descriptions-item label="入库单号">{{ inbound.inboundNo }}</el-descriptions-item>
        <el-descriptions-item label="入库类型">{{ inbound.inboundTypeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="仓库">{{ inbound.warehouseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="供应商">{{ inbound.supplierName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="总数量">{{ formatNumber(inbound.totalQuantity) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="getStatusTag(inbound.status)" size="small">
            {{ inbound.statusName || inboundStatusText(inbound.status) }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="创建人">{{ inbound.createBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ inbound.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">{{ inbound.remark || '-' }}</el-descriptions-item>
      </el-descriptions>

      <template v-if="isPurchaseSource && inbound.inspectionResult">
        <el-divider content-position="left">来料检验</el-divider>
        <el-descriptions :column="3" border>
          <el-descriptions-item label="检验员">{{ inbound.inspectorName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="检验时间">{{ inbound.inspectionTime || '-' }}</el-descriptions-item>
          <el-descriptions-item label="判定">{{ inspectionResultLabel(inbound.inspectionResult) }}</el-descriptions-item>
          <el-descriptions-item label="检验备注" :span="3">{{ inbound.inspectionRemark || '-' }}</el-descriptions-item>
        </el-descriptions>
      </template>

      <!-- 入库明细 -->
      <el-divider content-position="left">入库明细</el-divider>
      <el-table :data="inbound.items || []" border style="width: 100%">
        <el-table-column label="物料编码" prop="materialCode" width="130" />
        <el-table-column label="物料名称" prop="materialName" min-width="150" show-overflow-tooltip />
        <el-table-column label="规格型号" prop="specification" width="120" show-overflow-tooltip />
        <el-table-column label="单位" prop="unit" width="70" align="center" />
        <el-table-column label="批次号" prop="batchNo" width="150" />
        <el-table-column label="数量" prop="quantity" width="100" align="right">
          <template #default="{ row }">{{ formatNumber(row.quantity) }}</template>
        </el-table-column>
        <el-table-column v-if="isPurchaseSource" label="合格" prop="qualifiedQuantity" width="90" align="right" />
        <el-table-column v-if="isPurchaseSource" label="不良" prop="rejectedQuantity" width="90" align="right" />
        <el-table-column v-if="isPurchaseSource" label="不良/备注原因" prop="rejectReason" min-width="140" show-overflow-tooltip />
        <el-table-column label="库位" prop="locationCode" width="100" />
        <el-table-column label="生产日期" prop="productionDate" width="110" align="center" />
        <el-table-column label="到期日期" prop="expiryDate" width="110" align="center" />
      </el-table>

      <!-- 采购票据图片（采购来源：收货时上传的票据，按采购订单号目录展示） -->
      <template v-if="isPurchaseSource">
        <el-divider content-position="left">采购票据</el-divider>
        <div v-if="images.length" class="img-grid">
          <el-image
            v-for="(img, idx) in images"
            :key="idx"
            :src="img.fileUrl"
            :preview-src-list="previewList"
            :initial-index="idx"
            fit="cover"
            class="img-item"
          />
        </div>
        <el-empty v-else description="暂无采购票据图片" :image-size="60" />
      </template>
    </template>
    <el-empty v-else-if="!loading" description="未找到入库单数据" :image-size="80" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { inboundApi } from '@/api/inventory/inbound'
import { getDiskReceiptFiles } from '@/api/purchase/order'
import { formatNumber } from '@/utils/format'
import type { InboundVO } from '@/types/inventory/inbound'
import { InspectionResultEnum } from '@/enums/inventory/InboundEnum'

const props = defineProps<{
  inboundId: number | string
}>()

const loading = ref(false)
const inbound = ref<InboundVO | null>(null)
const images = ref<Array<{ fileName: string; storageName: string; fileUrl: string; fileSize: number }>>([])

const isPurchaseSource = computed(() => !!inbound.value?.sourceId && (
  ['PURCHASE', 'PURCHASE_ORDER'].includes(inbound.value.sourceType?.toUpperCase() || '')
  || inbound.value.inboundType?.toUpperCase() === 'PURCHASE'
))
const previewList = computed(() => images.value.map((i) => i.fileUrl))
const inspectionResultLabel = (result: string) => {
  const normalized = result.toUpperCase()
  return InspectionResultEnum.canDo(normalized) ? InspectionResultEnum.getLabel(normalized) : result
}

// 状态文本（后端 statusName 缺失时兜底）
const inboundStatusTextMap: Record<number, string> = {
  0: '草稿', 1: '待审批', 2: '已批准', 3: '已驳回', 4: '处理中', 5: '已确认',
  6: '已出库', 7: '已入库', 8: '已关闭', 9: '已取消', 10: '已完成', 11: '已处理', 12: '调拨中',
}
const inboundStatusText = (status?: number) =>
  status === undefined || status === null ? '-' : inboundStatusTextMap[status] || String(status)

const getStatusTag = (status?: number): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const map: Record<number, 'success' | 'warning' | 'info' | 'danger' | undefined> = {
    0: 'info', 1: 'warning', 2: 'success', 3: 'danger', 4: 'warning', 5: 'success',
    6: 'success', 7: 'success', 8: 'info', 9: 'danger', 10: 'success', 11: 'success', 12: 'warning',
  }
  return status === undefined || status === null ? undefined : map[status]
}

const loadDetail = async () => {
  if (!props.inboundId) return
  loading.value = true
  try {
    const res = await inboundApi.getById(String(props.inboundId))
    inbound.value = res.data
    await loadImages()
  } catch (error) {
    console.error('加载入库单详情失败:', error)
    ElMessage.error('加载入库单详情失败')
  } finally {
    loading.value = false
  }
}

// 采购来源：按采购订单ID查磁盘票据图片
const loadImages = async () => {
  images.value = []
  if (!isPurchaseSource.value || !inbound.value?.sourceId) return
  try {
    const res = await getDiskReceiptFiles(Number(inbound.value.sourceId))
    images.value = res.data || []
  } catch (error) {
    console.error('加载采购票据失败:', error)
  }
}

watch(
  () => props.inboundId,
  () => {
    if (props.inboundId) loadDetail()
  },
  { immediate: true },
)
</script>

<style scoped>
.img-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.img-item {
  width: 120px;
  height: 120px;
  border-radius: 6px;
  border: 1px solid #e4e7ed;
  cursor: pointer;
}
</style>
