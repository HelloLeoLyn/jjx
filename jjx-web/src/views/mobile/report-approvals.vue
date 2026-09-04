<template>
  <div class="m-approvals">
    <div v-loading="loading" class="m-list">
      <div v-for="r in list" :key="r.reportId" class="m-ap-item">
        <div class="m-ap-head">
          <span class="m-ap-no">{{ r.reportNo || `报工#${r.reportId}` }}</span>
          <span class="m-ap-status">待审核</span>
        </div>
        <div class="m-ap-meta">
          <div class="m-ap-row">
            <span class="k">工序</span>
            <span class="v">{{ r.processName || r.executionId || '-' }}</span>
          </div>
          <div class="m-ap-row">
            <span class="k">工单</span>
            <span class="v">{{ r.orderNo || '-' }}</span>
          </div>
          <div class="m-ap-row">
            <span class="k">报工人</span>
            <span class="v">{{ r.reporterName || '-' }}</span>
            <span class="k" style="margin-left: 12px">时间</span>
            <span class="v">{{ fmtTime(r.createTime || r.sendTime) }}</span>
          </div>
          <div class="m-ap-row">
            <span class="k">数量</span>
            <span class="v qty">
              <b class="ok">{{ fmtNum(r.qualifiedQuantity) }}</b> 合格
              <b class="bad" v-if="Number(r.defectiveQuantity || 0) > 0">{{ fmtNum(r.defectiveQuantity) }}</b>
              <span v-if="Number(r.defectiveQuantity || 0) > 0"> 不良</span>
              <span v-if="Number(r.laborHours)"> · ⏱ {{ r.laborHours }}h</span>
            </span>
          </div>
        </div>
        <div class="m-ap-actions">
          <button class="m-btn m-btn-ok" :disabled="actingId === r.reportId" @click="approve(r)">✓ 通过</button>
          <button class="m-btn m-btn-bad" :disabled="actingId === r.reportId" @click="reject(r)">✕ 驳回</button>
        </div>
      </div>
      <div v-if="!loading && !list.length" class="m-empty">没有待审核的报工</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPendingApprovalWorkReports, approveWorkReport, rejectWorkReport } from '@/api/production/workReport'

const loading = ref(false)
const list = ref<any[]>([])
const actingId = ref<number | null>(null)

function fmtNum(v?: number | string): string {
  return v == null ? '-' : String(v)
}

function fmtTime(t?: string): string {
  if (!t) return ''
  const d = new Date(t)
  const pad = (x: number) => String(x).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function load() {
  loading.value = true
  try {
    const res: any = await getPendingApprovalWorkReports({ pageNum: 1, pageSize: 50 })
    list.value = res?.data?.records || res?.data || []
  } catch (e: any) {
    ElMessage.error(e?.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function approve(r: any) {
  actingId.value = r.reportId
  try {
    await approveWorkReport(r.reportId, {})
    ElMessage.success('已通过')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    actingId.value = null
  }
}

async function reject(r: any) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', `驳回报工 ${r.reportNo || ''}`, {
      confirmButtonText: '确认驳回',
      cancelButtonText: '取消',
      inputPlaceholder: '驳回原因（必填）',
      inputValidator: (v: string) => (v && v.trim() ? true : '驳回原因必填'),
    })
    reason = value.trim()
  } catch {
    return // 取消
  }
  actingId.value = r.reportId
  try {
    await rejectWorkReport(r.reportId, { reviewRemark: reason })
    ElMessage.success('已驳回')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  } finally {
    actingId.value = null
  }
}

onMounted(load)
</script>

<style scoped>
.m-approvals {
  padding: 14px;
}
.m-list {
  min-height: 200px;
}
.m-ap-item {
  background: #fff;
  border-radius: 14px;
  padding: 14px;
  margin-bottom: 12px;
  box-shadow: 0 2px 10px rgba(43, 90, 167, 0.05);
}
.m-ap-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 10px;
}
.m-ap-no {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  font-family: ui-monospace, monospace;
}
.m-ap-status {
  font-size: 12px;
  color: #e6a23c;
  background: #fdf6ec;
  padding: 2px 8px;
  border-radius: 8px;
}
.m-ap-meta {
  display: flex;
  flex-direction: column;
  gap: 6px;
  background: #f7f9fc;
  border-radius: 10px;
  padding: 10px 12px;
  margin-bottom: 12px;
}
.m-ap-row {
  display: flex;
  align-items: baseline;
  font-size: 13px;
}
.m-ap-row .k {
  color: #909399;
  width: 52px;
  flex-shrink: 0;
}
.m-ap-row .v {
  color: #303133;
}
.m-ap-row .qty .ok {
  color: #67c23a;
  font-size: 16px;
}
.m-ap-row .qty .bad {
  color: #f56c6c;
  font-size: 16px;
}
.m-ap-actions {
  display: flex;
  gap: 10px;
}
.m-btn {
  flex: 1;
  height: 42px;
  border: none;
  border-radius: 10px;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
}
.m-btn-ok {
  background: #67c23a;
  color: #fff;
}
.m-btn-bad {
  background: #fff;
  color: #f56c6c;
  border: 1px solid #f56c6c;
}
.m-btn:disabled {
  opacity: 0.6;
}
.m-empty {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 60px 0;
}
</style>
