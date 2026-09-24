<template>
  <div class="scrap-order-page">
    <el-alert type="info" :closable="false" show-icon class="scope-guide">
      <template #title>
        成品报废单：报废处置生效时自动出单（谁报废、报废多少、哪几件、谁批的）。
        报废是**实物凭据**，库存不受影响（口径A：不良品从未进良品库）；打印凭据版式后置。
      </template>
    </el-alert>
    <el-card>
      <template #header>
        <div class="header">
          <span>成品报废单</span>
          <div>
            <el-input
              v-model="keyword"
              clearable
              placeholder="报废单号 / 不良单号 / 工单号"
              style="width: 240px"
              @keyup.enter="load"
            />
            <el-button type="primary" @click="load">查询</el-button>
            <el-button @click="load">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table v-loading="loading" :data="rows" border size="small">
        <template #empty><el-empty description="暂无报废单" /></template>
        <el-table-column prop="scrapNo" label="报废单号" min-width="150" />
        <el-table-column prop="ncrNo" label="不良单号" min-width="140" />
        <el-table-column label="工单 / 批" min-width="170">
          <template #default="{ row }">
            <div>{{ row.orderNo || '-' }}</div>
            <div class="sub">{{ row.lotNo ? '批 ' + row.lotNo : '' }}</div>
          </template>
        </el-table-column>
        <el-table-column label="产品" min-width="160">
          <template #default="{ row }">
            {{ row.productCode || '-' }}
            <span class="sub">{{ row.productName || '' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" width="110" />
        <el-table-column label="数量" width="85" align="right">
          <template #default="{ row }">{{ num(row.quantity) }}</template>
        </el-table-column>
        <el-table-column label="主缺陷" min-width="140">
          <template #default="{ row }">
            <span v-if="row.defectItem">{{ row.defectItem }}</span>
            <el-tag v-if="row.defectLevel" :type="levelTag(row.defectLevel)" size="small" effect="plain" style="margin-left: 4px">
              {{ row.defectLevel }}
            </el-tag>
            <span v-if="!row.defectItem && !row.defectLevel">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="pieceRange" label="件号区间" min-width="170" />
        <el-table-column label="申请人 / 审批人" min-width="140">
          <template #default="{ row }">
            {{ row.applicant || '-' }}
            <span v-if="row.approver" class="sub">审批 {{ row.approver }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="row.status === 'VOID' ? 'info' : 'success'" size="small">
              {{ row.status === 'VOID' ? '已作废' : '已生效' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="{ row }">
            <el-button link size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="detailVisible" title="成品报废单详情" width="640px" append-to-body>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="报废单号">{{ detail?.scrapNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ detail?.status === 'VOID' ? '已作废' : '已生效' }}</el-descriptions-item>
        <el-descriptions-item label="不良单号">{{ detail?.ncrNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源处置单">#{{ detail?.actionId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="工单">{{ detail?.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="检验批">{{ detail?.lotNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="产品">{{ detail?.productCode || '-' }} {{ detail?.productName || '' }}</el-descriptions-item>
        <el-descriptions-item label="批次">{{ detail?.batchNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="报废数量">{{ num(detail?.quantity) }}</el-descriptions-item>
        <el-descriptions-item label="主缺陷">
          {{ detail?.defectItem || '-' }} {{ detail?.defectLevel || '' }}
        </el-descriptions-item>
        <el-descriptions-item label="件号区间" :span="2">{{ detail?.pieceRange || '-' }}</el-descriptions-item>
        <el-descriptions-item label="原因说明" :span="2">{{ detail?.reason || '-' }}</el-descriptions-item>
        <el-descriptions-item label="申请人">{{ detail?.applicant || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批人">{{ detail?.approver || '-' }}</el-descriptions-item>
        <el-descriptions-item label="生成时间" :span="2">{{ detail?.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detail?.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <div class="sub" style="margin-top: 8px">
        件级明细（哪几件）在「质量管理 → 产品不良台账 → 不良件」查看；本单为实物凭据，不影响库存数量。
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { qualityScrapOrderApi, type QualityScrapOrder } from '@/api/quality/scrapOrder'

defineOptions({ name: 'QualityScrapOrderList' })

const loading = ref(false)
const rows = ref<QualityScrapOrder[]>([])
const keyword = ref('')

const detailVisible = ref(false)
const detail = ref<QualityScrapOrder | null>(null)

const num = (value?: number | null) =>
  value == null ? '-' : Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 4 })
/** 分级标签色：CR 致命=红 / MA 严重=橙 / MI 轻微=灰 */
const levelTag = (level?: string) => (level === 'CR' ? 'danger' : level === 'MA' ? 'warning' : 'info')

const load = async () => {
  loading.value = true
  try {
    const res: any = await qualityScrapOrderApi.list(keyword.value || undefined)
    rows.value = res?.data || []
  } catch (e: any) {
    ElMessage.error(e?.message || '查询失败')
    rows.value = []
  } finally {
    loading.value = false
  }
}

const openDetail = async (row: QualityScrapOrder) => {
  try {
    const res: any = await qualityScrapOrderApi.detail(row.scrapId)
    detail.value = res?.data || row
  } catch {
    detail.value = row
  }
  detailVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.scope-guide {
  margin-bottom: 12px;
}
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.sub {
  color: #909399;
  font-size: 12px;
  margin-left: 6px;
}
</style>
