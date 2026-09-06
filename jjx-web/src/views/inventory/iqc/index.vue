<template>
  <div class="iqc-page">
    <el-card>
      <template #header>
        <div class="header"><span>IQC进料检测</span><el-button @click="load" :loading="loading">刷新</el-button></div>
      </template>
      <el-form inline>
        <el-form-item label="检验状态"><el-select v-model="query.result" clearable style="width: 130px" @change="load"><el-option v-for="item in InspectionResultEnum.items" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item label="审核状态"><el-select v-model="query.reviewStatus" clearable style="width: 130px" @change="load"><el-option v-for="item in QualityReviewStatusEnum.items" :key="item.value" :label="item.label" :value="item.value" /></el-select></el-form-item>
        <el-form-item><el-input v-model="query.inspectionNo" clearable placeholder="检验单号" @keyup.enter="load" /></el-form-item>
        <el-form-item><el-button type="primary" @click="load">查询</el-button></el-form-item>
      </el-form>
      <el-table v-loading="loading" :data="rows" border>
        <el-table-column prop="inspectionNo" label="检验单号" min-width="190" />
        <el-table-column label="入库单" width="130"><template #default="{ row }">{{ inboundNames[row.sourceId] || `入库#${row.sourceId || '-'}` }}</template></el-table-column>
        <el-table-column prop="materialName" label="材料" min-width="160" />
        <el-table-column label="版本" width="70"><template #default="{ row }">V{{ row.inspectionVersion || 1 }}</template></el-table-column>
        <el-table-column label="检验结果" width="100"><template #default="{ row }"><el-tag :type="InspectionResultEnum.getTagProps(row.result).type">{{ InspectionResultEnum.getLabel(row.result) }}</el-tag></template></el-table-column>
        <el-table-column label="审核状态" width="100"><template #default="{ row }"><el-tag :type="QualityReviewStatusEnum.getTagProps(row.reviewStatus).type">{{ QualityReviewStatusEnum.getLabel(row.reviewStatus) }}</el-tag></template></el-table-column>
        <el-table-column prop="inspectTime" label="检验时间" width="170" />
        <el-table-column label="操作" fixed="right" width="300">
          <template #default="{ row }">
            <el-button v-if="row.sourceId && (row.result === InspectionResult.PENDING || row.reviewStatus === QualityReviewStatus.REJECTED)" link type="primary" @click="openInspection(row)">检验</el-button>
            <el-button v-if="row.sourceId && row.reviewStatus === QualityReviewStatus.PENDING" link type="success" @click="openReview(row)">审核</el-button>
            <el-button v-if="row.sourceId && row.inspectionId" link type="primary" @click="print(row)">打印报告</el-button>
            <el-button v-if="row.sourceId" link type="warning" @click="openQuarantine(row)">隔离/处置</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager"><el-pagination v-model:current-page="query.pageNum" v-model:page-size="query.pageSize" :total="total" layout="total, sizes, prev, pager, next" @current-change="load" @size-change="load" /></div>
    </el-card>
    <InboundInspectionDialog v-model:visible="inspectionVisible" :inbound-id="activeInboundId" :item-id="activeItemId" @success="load" />
    <IqcReviewDialog v-model:visible="reviewVisible" :inbound-id="activeInboundId" :inbound-no="activeInboundNo" @success="load" />
    <IqcQuarantineDialog v-model:visible="quarantineVisible" :inbound-id="activeInboundId" :inbound-no="activeInboundNo" @success="load" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { qualityApi, type QualityVO } from '@/api/production/quality'
import { inboundApi } from '@/api/inventory/inbound'
import InboundInspectionDialog from '@/views/inventory/inbound/components/InboundInspectionDialog.vue'
import IqcReviewDialog from '@/views/inventory/inbound/components/IqcReviewDialog.vue'
import IqcQuarantineDialog from '@/views/inventory/inbound/components/IqcQuarantineDialog.vue'
import { InspectionResult, InspectionResultEnum, QualityReviewStatus, QualityReviewStatusEnum } from '@/enums/quality/InspectionEnum'

const router = useRouter()
const route = useRoute()
const rows = ref<QualityVO[]>([])
const total = ref(0)
const loading = ref(false)
const inboundNames = reactive<Record<string, string>>({})
const query = reactive({ pageNum: 1, pageSize: 20, inspectionType: 'IQC', inspectionNo: '', sourceId: undefined as number | undefined, result: '', reviewStatus: '' })
const inspectionVisible = ref(false)
const reviewVisible = ref(false)
const quarantineVisible = ref(false)
const activeInboundId = ref<number>()
const activeItemId = ref<number>()
const activeInboundNo = ref('')

async function load() {
  loading.value = true
  try {
    query.sourceId = route.query.inboundId ? Number(route.query.inboundId) : undefined
    const result = await qualityApi.page(query)
    rows.value = result.data?.records || []
    total.value = result.data?.total || 0
    await Promise.all([...new Set(rows.value.map(row => row.sourceId).filter(Boolean))].map(async id => {
      if (!inboundNames[String(id)]) {
        const inbound = await inboundApi.getById(String(id))
        inboundNames[String(id)] = inbound.data?.inboundNo || `入库#${id}`
      }
    }))
  } finally {
    loading.value = false
  }
}
function activate(row: QualityVO) {
  activeInboundId.value = row.sourceId
  activeItemId.value = row.sourceItemId
  activeInboundNo.value = inboundNames[String(row.sourceId)] || ''
}
function openInspection(row: QualityVO) { activate(row); inspectionVisible.value = true }
function openReview(row: QualityVO) { activate(row); reviewVisible.value = true }
function openQuarantine(row: QualityVO) { activate(row); quarantineVisible.value = true }
function print(row: QualityVO) {
  router.push({ path: '/production/quality-print/iqc-report', query: { inboundId: row.sourceId, inspectionId: row.inspectionId } })
}
onMounted(load)
</script>

<style scoped>
.iqc-page { padding: 20px; }
.header { display: flex; justify-content: space-between; align-items: center; font-weight: 600; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
</style>
