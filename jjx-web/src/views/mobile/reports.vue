<template>
  <div class="m-reports">
    <header class="m-header">
      <el-button link @click="router.back()">← 返回</el-button>
      <span class="m-header-title">我的报工</span>
      <span class="m-header-spacer"></span>
    </header>

    <div class="m-reports-body">
      <el-tabs v-model="activeTab" class="m-reports-tabs">
        <el-tab-pane label="待审批" name="pending">
          <div v-loading="loading" class="m-reports-list">
            <div v-for="r in pendingList" :key="r.reportId" class="m-report-item">
              <div class="m-report-item-head">
                <span class="m-report-item-no">{{ r.reportNo || `报工#${r.reportId}` }}</span>
                <el-tag size="small" type="warning">{{ r.reportStatusLabel || '待审批' }}</el-tag>
              </div>
              <div class="m-report-item-meta">
                <div>{{ r.orderNo || '-' }}</div>
                <div>
                  合格 {{ fmtQty(r.qualifiedQuantity) }}
                  <span v-if="Number(r.defectiveQuantity || 0) > 0" class="m-defective">
                    · 不良 {{ fmtQty(r.defectiveQuantity) }}
                  </span>
                  · {{ fmtTime(r.reportTime) }}
                </div>
              </div>
              <div class="m-report-item-actions">
                <el-button
                  v-if="r.reportStatus === 'PENDING'"
                  size="small"
                  type="danger"
                  plain
                  @click="handleCancel(r)"
                >
                  撤销
                </el-button>
              </div>
            </div>
            <el-empty v-if="!loading && !pendingList.length" description="暂无待审批报工" />
          </div>
        </el-tab-pane>
        <el-tab-pane label="全部记录" name="all">
          <div v-loading="loading" class="m-reports-list">
            <div v-for="r in allList" :key="r.reportId" class="m-report-item">
              <div class="m-report-item-head">
                <span class="m-report-item-no">{{ r.reportNo || `报工#${r.reportId}` }}</span>
                <el-tag size="small" :type="statusTag(r.reportStatus)">
                  {{ r.reportStatusLabel || r.reportStatus }}
                </el-tag>
              </div>
              <div class="m-report-item-meta">
                <div>{{ r.orderNo || '-' }}</div>
                <div>
                  合格 {{ fmtQty(r.qualifiedQuantity) }}
                  <span v-if="Number(r.defectiveQuantity || 0) > 0" class="m-defective">
                    · 不良 {{ fmtQty(r.defectiveQuantity) }}
                  </span>
                  · {{ fmtTime(r.reportTime) }}
                </div>
              </div>
            </div>
            <el-empty v-if="!loading && !allList.length" description="暂无报工记录" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getMyWorkReports, cancelWorkReport } from '@/api/production/workReport'
import type { WorkReportVO, WorkReportStatus } from '@/api/production/workReport'

const router = useRouter()

const activeTab = ref<'pending' | 'all'>('pending')
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
}
.m-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  position: sticky;
  top: 0;
  z-index: 10;
}
.m-header-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}
.m-header-spacer {
  width: 48px;
}
.m-reports-body {
  padding: 8px 12px;
}
.m-reports-tabs :deep(.el-tabs__item) {
  font-size: 14px;
}
.m-report-item {
  background: #fff;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 10px;
  border: 1px solid #ebeef5;
}
.m-report-item-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
}
.m-report-item-no {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.m-report-item-meta {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
}
.m-defective {
  color: #f56c6c;
}
.m-report-item-actions {
  margin-top: 8px;
}
</style>
