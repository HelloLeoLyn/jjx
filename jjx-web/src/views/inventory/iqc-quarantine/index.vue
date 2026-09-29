<template>
  <div class="iqc-pending">
    <div class="scope-tip">
      <el-tooltip placement="bottom-start">
        <template #content>
          <div class="scope-tip__pop">
            跨批次视角：全厂还没处置完的来料隔离品（剩余可处置量 &gt; 0）。<br />
            处置方式四种：让步接收（特采） / 退货 / 返工 / 报废；剩余可处置量减到 0 即结清。<br />
            某个批次要看成「处置单历史 / 批次溯源 / 材料检验」，到「来料检验」工作台选中该批次。
          </div>
        </template>
        <span class="scope-tip__text"
          >❓ 本页只列「待处置」的隔离品（跨批次）；处置后在对应批次的处置单历史里留痕</span
        >
      </el-tooltip>
      <el-button link type="primary" @click="router.push('/inventory/iqc')">返回来料检验</el-button>
      <el-button link type="primary" @click="router.push('/quality/ncr')">查看产品不良台账</el-button>
    </div>

    <div class="filter-bar">
      <el-form inline @submit.prevent>
        <el-form-item label="来料批次">
          <el-input v-model="query.inboundNo" clearable placeholder="输入来料批次" />
        </el-form-item>
        <el-form-item label="采购单号">
          <el-input v-model="query.sourceNo" clearable placeholder="输入采购单号" />
        </el-form-item>
        <el-form-item label="物料">
          <el-input v-model="query.materialKeyword" clearable placeholder="编码或名称" />
        </el-form-item>
        <el-form-item label="批次">
          <el-input v-model="query.batchNo" clearable placeholder="输入批次号" />
        </el-form-item>
        <el-form-item label="供应商">
          <el-input v-model="query.supplierName" clearable placeholder="输入供应商名称" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-card class="card">
      <template #header>
        <div class="card-title">
          <span>待处理明细（跨批次）</span>
          <span class="card-tip"
            >剩余可处置量 &gt; 0 才是待处置；结清后该行不再出现，构成见对应批次的处置单历史</span
          >
        </div>
      </template>
      <DispositionPendingTable
        :rows="rows"
        :can-dispose="canDispose"
        :loading="loading"
        @dispose="openDisposition"
      />
      <el-empty v-if="!loading && !rows.length" description="当前没有待处置的来料隔离品" />
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        class="pagination"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="total"
        @size-change="search"
        @current-change="load"
      />
    </el-card>

    <IqcQuarantineDialog
      v-model:visible="dispositionVisible"
      :inbound-id="activeInboundId"
      :inbound-no="activeInboundNo"
      :quarantine-row="activeQuarantine"
      @success="load"
    />
  </div>
</template>

<script setup lang="ts">
/**
 * 跨批次待处置工作台（dev-20260929-007）
 *
 * 定位（history/iqc-disposition-truth-rootfix-dev-20260929-003.md §6）：
 *   按批次的完整工作区在「来料检验」工作台；本页只回答"现在全厂有哪些不合格品等我处置"。
 * 数据源：/iqc-quarantine/page?pendingOnly=true（一次分页请求 + 关联行）；
 *   表格复用 DispositionPendingTable（与工作台同一份实现，不写第二份）。
 * 处置：行内「处置」带着该行完整上下文打开弹窗（来料批次/供应商/采购单号/检验批号/不合格原因）。
 */
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { inboundApi } from '@/api/inventory/inbound'
import { hasPermi } from '@/directives'
import DispositionPendingTable from '@/views/inventory/iqc/components/DispositionPendingTable.vue'
import IqcQuarantineDialog from '@/views/inventory/inbound/components/IqcQuarantineDialog.vue'

const router = useRouter()
/** 口径 B（2026-09-21 用户定，迁移 167）：隔离处置只给品质一侧 */
const canDispose = computed(() => hasPermi(['quality:ncr:dispose']))
const query = ref({
  pageNum: 1,
  pageSize: 20,
  materialKeyword: '',
  batchNo: '',
  inboundNo: '',
  sourceNo: '',
  supplierName: '',
})
const rows = ref<any[]>([])
const total = ref(0)
const loading = ref(false)
const dispositionVisible = ref(false)
const activeInboundId = ref<number>()
const activeInboundNo = ref<string>()
const activeQuarantine = ref<any>()

async function load() {
  loading.value = true
  try {
    const { data } = await inboundApi.pageIqcQuarantine({
      ...query.value,
      pendingOnly: true,
    })
    rows.value = data?.page?.records || []
    total.value = data?.page?.total || 0
  } finally {
    loading.value = false
  }
}
function search() {
  query.value.pageNum = 1
  load()
}
function resetQuery() {
  query.value = {
    pageNum: 1,
    pageSize: query.value.pageSize,
    materialKeyword: '',
    batchNo: '',
    inboundNo: '',
    sourceNo: '',
    supplierName: '',
  }
  load()
}
function openDisposition(row: any) {
  activeInboundId.value = Number(row.inboundId)
  activeInboundNo.value = row.inboundNo || ''
  activeQuarantine.value = row
  dispositionVisible.value = true
}
onMounted(load)
</script>

<style scoped>
.iqc-pending {
  padding: 20px;
}
.scope-tip {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 10px;
  color: #909399;
  font-size: 12px;
}
.scope-tip__text {
  cursor: help;
}
.scope-tip__pop {
  line-height: 1.7;
}
.filter-bar {
  margin-bottom: 12px;
}
.card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-weight: 600;
}
.card-tip {
  color: #909399;
  font-size: 12px;
  font-weight: 400;
}
.pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
