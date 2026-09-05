<template>
  <div class="m-reports">
    <div class="m-filter">
      <div class="m-chips">
        <span
          v-for="t in tabs"
          :key="t.value"
          class="m-chip"
          :class="{ active: activeTab === t.value }"
          @click="activeTab = t.value"
          >{{ t.label }}</span
        >
      </div>
    </div>

    <div v-loading="loading" class="m-list">
      <div v-for="r in currentList" :key="r.reportId" class="m-report-item">
        <div class="m-report-item-head">
          <span class="m-report-item-no">{{ r.reportNo || `报工#${r.reportId}` }}</span>
          <span class="m-tag" :class="'st-' + (r.reportStatus || 'PENDING')">{{
            r.reportStatusLabel || r.reportStatus || '待审批'
          }}</span>
        </div>
        <div class="m-report-item-meta">
          <div class="m-line">🏷 {{ r.orderNo || '-' }}</div>
          <div v-if="r.reportStatus === 'PENDING'" class="m-line reviewer">
            ✋ {{ r.pendingReviewerName ? '待 ' + r.pendingReviewerName + ' 审批' : '待生产管理审批' }}
          </div>
          <div class="m-line qty">
            合格 <b class="ok">{{ fmtQty(r.qualifiedQuantity) }}</b>
            <span v-if="Number(r.defectiveQuantity || 0) > 0" class="bad">
              · 不良 {{ fmtQty(r.defectiveQuantity) }}
            </span>
            <span v-if="Number(r.laborHours)" class="hours"> · ⏱ {{ r.laborHours }}h</span>
            <span class="time">{{ fmtTime(r.reportTime) }}</span>
          </div>
        </div>
        <div class="m-report-item-actions">
          <button
            v-if="r.reportStatus === 'PENDING'"
            class="m-act m-act-bad"
            @click="handleCancel(r)"
          >
            撤销
          </button>
          <button class="m-act m-act-primary" @click="printWorkReport(r)">🖨 打印</button>
        </div>
      </div>
      <div v-if="!loading && !currentList.length" class="m-empty">
        {{ activeTab === 'pending' ? '暂无待审批报工' : '暂无报工记录' }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyWorkReports, cancelWorkReport } from '@/api/production/workReport'
import type { WorkReportVO, WorkReportStatus } from '@/api/production/workReport'

const router = useRouter()

const activeTab = ref<'pending' | 'all'>('pending')

const tabs = [
  { value: 'pending', label: '待审批' },
  { value: 'all', label: '全部记录' },
] as const

const currentList = computed(() => (activeTab.value === 'pending' ? pendingList.value : allList.value))
const loading = ref(false)
const pendingList = ref<WorkReportVO[]>([])
const allList = ref<WorkReportVO[]>([])

function fmtQty(v?: number | string | null): string {
  const n = Number(v || 0)
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

function fmtTime(t?: string): string {
  if (!t) return '-'
  return t.replace('T', ' ').slice(5, 16)
}

function statusTag(s?: WorkReportStatus): any {
  const map: Record<string, string> = {
    PENDING: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    CANCELLED: 'info',
  }
  return map[s || ''] || 'info'
}

async function loadPending() {
  loading.value = true
  try {
    const res: any = await getMyWorkReports({ pageNum: 1, pageSize: 20, status: 'PENDING' })
    pendingList.value = res?.data?.records || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadAll() {
  loading.value = true
  try {
    const res: any = await getMyWorkReports({ pageNum: 1, pageSize: 20 })
    allList.value = res?.data?.records || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function handleCancel(r: WorkReportVO) {
  try {
    const { value } = await ElMessageBox.prompt('请输入撤销原因', '撤销报工', {
      confirmButtonText: '确认撤销',
      cancelButtonText: '取消',
      inputValidator: (v: string) => (v && v.trim() ? true : '撤销原因必填'),
    })
    await cancelWorkReport(r.reportId, { cancelReason: value.trim() })
    ElMessage.success('已撤销')
    loadPending()
  } catch (e: any) {
    if (e !== 'cancel' && e?.message) ElMessage.error(e.message)
  }
}

// 打印工票（DEV-1247）
function printWorkReport(r: WorkReportVO) {
  if (!r.reportId) return
  const { href } = router.resolve(`/production/report/print/${r.reportId}`)
  window.open(href, '_blank')
}

watch(activeTab, (tab) => {
  if (tab === 'pending') loadPending()
  else loadAll()
})

onMounted(() => loadPending())
</script>

<style scoped>
.m-reports {
  min-height: 100vh;
  background: #f5f7fa;
  padding: 12px 12px 70px;
}
.m-filter {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.m-chips {
  display: flex;
  gap: 8px;
  overflow-x: auto;
}
.m-chip {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: #fff;
  color: #606266;
  font-size: 13px;
  padding: 6px 12px;
  border-radius: 16px;
  cursor: pointer;
  box-shadow: 0 1px 4px rgba(43, 90, 167, 0.06);
}
.m-chip.active {
  background: #2b5aa7;
  color: #fff;
  font-weight: 600;
}
.m-chip-count {
  font-style: normal;
  font-size: 11px;
  background: rgba(0, 0, 0, 0.08);
  border-radius: 8px;
  padding: 0 5px;
  line-height: 15px;
}
.m-chip.active .m-chip-count {
  background: rgba(255, 255, 255, 0.25);
}
.m-report-item {
  background: #fff;
  border-radius: 14px;
  padding: 14px;
  margin-bottom: 12px;
  box-shadow: 0 2px 10px rgba(43, 90, 167, 0.05);
}
.m-report-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}
.m-report-item-no {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  font-family: ui-monospace, monospace;
}
.m-tag {
  flex-shrink: 0;
  font-size: 12px;
  padding: 3px 10px;
  border-radius: 10px;
  font-weight: 500;
}
.m-tag.st-PENDING {
  color: #e6a23c;
  background: #fdf6ec;
}
.m-tag.st-APPROVED {
  color: #67c23a;
  background: #f0f9eb;
}
.m-tag.st-REJECTED {
  color: #f56c6c;
  background: #fef0f0;
}
.m-tag.st-CANCELLED {
  color: #909399;
  background: #f4f4f5;
}
.m-report-item-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: #f7f9fc;
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 12px;
}
.m-line {
  font-size: 13px;
  color: #606266;
}
.m-line.reviewer {
  color: #b88230;
  font-size: 12px;
  background: rgba(184, 130, 48, 0.08);
  border-radius: 6px;
  padding: 2px 6px;
  align-self: flex-start;
}
.m-line .ok {
  color: #67c23a;
  font-size: 16px;
}
.m-line .bad {
  color: #f56c6c;
  font-size: 16px;
}
.m-line .hours {
  color: #2b5aa7;
}
.m-line .time {
  color: #c0c4cc;
  font-size: 11px;
  margin-left: 6px;
}
.m-report-item-actions {
  display: flex;
  gap: 10px;
}
.m-act {
  flex: 1;
  height: 40px;
  border: none;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
}
.m-act-primary {
  background: linear-gradient(135deg, #2b5aa7, #4a7fd4);
  color: #fff;
}
.m-act-bad {
  background: #fff;
  color: #f56c6c;
  border: 1px solid #f56c6c;
}
.m-empty {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 70px 0;
}
</style>
