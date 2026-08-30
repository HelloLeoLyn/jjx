<template>
  <div class="operation-log-panel">
    <div v-if="loading" class="panel-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <el-empty v-else-if="!logs.length" description="暂无操作记录" :image-size="60" />

    <el-timeline v-else class="op-timeline">
      <el-timeline-item
        v-for="(log, idx) in logs"
        :key="idx"
        :timestamp="log.time"
        :type="log.status === 1 ? 'success' : 'danger'"
        placement="top"
      >
        <div class="op-card">
          <div class="op-card-head">
            <el-tag size="small" :type="log.status === 1 ? 'success' : 'danger'" effect="plain">
              {{ log.status === 1 ? '成功' : '失败' }}
            </el-tag>
            <span class="op-module">{{ log.module }}</span>
            <span class="op-biz-status" v-if="log.bizStatus">
              → {{ log.bizStatus }}
            </span>
          </div>
          <div class="op-action">{{ formatBusinessType(log.businessType) }}</div>
          <div class="op-meta" v-if="log.operator">
            <span v-if="log.operator">操作人：{{ log.operator }}</span>
          </div>
          <div class="op-remark" v-if="log.detail">{{ log.detail }}</div>
          <div class="op-error" v-if="log.errorMsg">{{ log.errorMsg }}</div>
        </div>
      </el-timeline-item>
    </el-timeline>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import request from '@/utils/request'
const props = defineProps<{
  bizType: string
  bizId: number
  /** 可选：traceId 直达链路 */
  traceId?: string
}>()

const loading = ref(false)
const logs = ref<any[]>([])

// bizStatus 由后端在写入流水时快照成状态文案（sys_oper_log.biz_status 为 varchar），前端不再做映射

function formatBusinessType(code: number): string {
  const map: Record<number, string> = {
    1: '新增', 2: '修改', 3: '删除', 4: '导出', 5: '导入',
    6: '审批', 7: '登录', 8: '登出', 9: '其他', 10: '重置密码', 11: '转换',
  }
  return map[code] ?? String(code ?? '')
}

// 操作日志查询：统一走 trace_id 事件流（只查主表）
async function loadLogs() {
  logs.value = []
  if (!props.traceId) return
  loading.value = true
  try {
    const res = await request.get('/api/trace/events', {
      params: { traceId: props.traceId, pageNum: 1, pageSize: 100 },
    })
    const records: any[] = (res as any)?.data?.records || []
    logs.value = records.map((record: any) => ({
      time: record.time,
      bizStatus: record.bizStatus,
      bizType: record.bizType,
      businessType: record.businessType,
      module: record.module,
      operator: record.operatorName,
      status: record.result,
      detail: record.detail,
    }))
  } catch {
    logs.value = []
  } finally {
    loading.value = false
  }
}

watch(() => [props.bizType, props.bizId, props.traceId], loadLogs, { immediate: true })

defineExpose({ loadLogs })
</script>

<style scoped>
.operation-log-panel {
  min-height: 120px;
}
.panel-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  justify-content: center;
  padding: 40px 0;
  color: #909399;
}
.op-timeline {
  padding: 8px 4px 0;
}
.op-card {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 8px 10px;
  background: #fff;
}
.op-card-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.op-module {
  font-size: 12px;
  color: #909399;
}
.op-biz-status {
  font-size: 12px;
  color: #409eff;
}
.op-action {
  font-size: 13px;
  color: #303133;
  margin-top: 4px;
}
.op-meta {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.op-remark {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
  background: #f8fafc;
  padding: 4px 8px;
  border-radius: 4px;
}
.op-error {
  font-size: 12px;
  color: #f56c6c;
  margin-top: 4px;
}
</style>
