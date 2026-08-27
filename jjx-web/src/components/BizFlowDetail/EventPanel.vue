<template>
  <div class="event-panel">
    <div v-if="loading" class="panel-loading">
      <el-icon class="is-loading"><Loading /></el-icon>
      <span>加载中...</span>
    </div>

    <el-empty v-else-if="!events.length" description="暂无事件记录" :image-size="60" />

    <el-timeline v-else class="ev-timeline">
      <el-timeline-item
        v-for="(ev, idx) in events"
        :key="idx"
        :timestamp="formatTime(ev.sendTime || ev.createTime)"
        :type="ev.type === 'task' ? 'warning' : 'primary'"
        placement="top"
      >
        <div class="ev-card">
          <div class="ev-head">
            <el-tag size="small" :type="ev.type === 'task' ? 'warning' : 'primary'" effect="plain">
              {{ ev.type === 'task' ? '任务' : '通知' }}
            </el-tag>
            <span class="ev-title">{{ ev.title }}</span>
          </div>
          <div class="ev-content" v-if="ev.content">{{ ev.content }}</div>
          <div class="ev-meta">
            <span v-if="ev.receiverName">接收人：{{ ev.receiverName }}</span>
            <span v-if="ev.assigneeName">负责人：{{ ev.assigneeName }}</span>
            <span v-if="ev.eventCode" class="ev-code">{{ ev.eventCode }}</span>
            <el-tag v-if="ev.isRead === 1" size="small" type="info" effect="plain">已读</el-tag>
          </div>
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
}>()

const loading = ref(false)
const events = ref<any[]>([])

async function loadEvents() {
  events.value = []
  if (!props.bizId) return
  loading.value = true
  try {
    const list: any[] = []
    // 1. 通知：按 bizType 查，前端按 bizId 过滤（接口无 bizId 参数）
    const notifRes: any = await request({
      url: '/notification/page',
      method: 'get',
      params: { bizType: props.bizType, pageNum: 1, pageSize: 50 },
    })
    const notifList = (notifRes?.data?.list || notifRes?.data?.records || []) as any[]
    for (const n of notifList) {
      if (String(n.bizId) === String(props.bizId)) {
        list.push({ type: 'notification', ...n })
      }
    }
    // 2. 任务：看板任务接口按 bizType 查，前端按 bizId 过滤
    const taskRes: any = await request({
      url: `/kanban/board/${props.bizType}/tasks`,
      method: 'get',
      params: {},
    })
    const taskList = (taskRes?.data || []) as any[]
    for (const t of taskList) {
      if (t.bizType === props.bizType && String(t.bizId) === String(props.bizId)) {
        list.push({
          type: 'task',
          title: t.title,
          content: t.description,
          assigneeName: t.assigneeName,
          eventCode: t.sourceEvent,
          createTime: t.createTime,
          bizId: t.bizId,
        })
      }
    }
    // 按时间倒序（新的在前）
    list.sort((a, b) => String(b.sendTime || b.createTime || '').localeCompare(String(a.sendTime || a.createTime || '')))
    events.value = list
  } catch {
    events.value = []
  } finally {
    loading.value = false
  }
}

function formatTime(t: string | undefined): string {
  if (!t) return ''
  return String(t).replace('T', ' ').slice(0, 16)
}

watch(() => [props.bizType, props.bizId], loadEvents, { immediate: true })

defineExpose({ loadEvents })
</script>

<style scoped>
.event-panel {
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
.ev-timeline {
  padding: 8px 4px 0;
}
.ev-card {
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  padding: 8px 10px;
  background: #fff;
}
.ev-head {
  display: flex;
  align-items: center;
  gap: 8px;
}
.ev-title {
  font-size: 13px;
  font-weight: 500;
  color: #303133;
}
.ev-content {
  font-size: 12px;
  color: #606266;
  margin-top: 4px;
}
.ev-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}
.ev-code {
  font-family: monospace;
  font-size: 11px;
  color: #b0b3b8;
}
</style>
