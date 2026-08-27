<template>
  <div class="notification-page">
    <div class="page-header">
      <h1 class="page-title">消息通知</h1>
      <div class="header-actions">
        <el-button size="small" @click="handleRefresh" :loading="loading">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
        <el-button size="small" type="primary" @click="handleMarkAllRead" v-if="unreadCount > 0">
          全部标为已读
        </el-button>
      </div>
    </div>

    <!-- 统计 -->
    <el-row :gutter="16" class="stat-row">
      <el-col :span="6">
        <el-card shadow="never" class="stat-card">
          <div class="stat-value">{{ total }}</div>
          <div class="stat-label">全部</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card unread">
          <div class="stat-value">{{ unreadCount }}</div>
          <div class="stat-label">未读</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card system">
          <div class="stat-value">{{ systemCount }}</div>
          <div class="stat-label">系统通知</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="never" class="stat-card email">
          <div class="stat-value">{{ emailCount }}</div>
          <div class="stat-label">邮件通知</div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 筛选 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="query" size="small">
        <el-form-item label="类型">
          <el-select v-model="query.notificationType" placeholder="全部" clearable style="width:140px">
            <el-option label="系统通知" value="SYSTEM" />
            <el-option label="邮件通知" value="EMAIL" />
            <el-option label="应用通知" value="APP" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.isRead" placeholder="全部" clearable style="width:120px">
            <el-option label="未读" :value="0" />
            <el-option label="已读" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="loadData">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table :data="list" v-loading="loading" @row-click="handleRowClick" style="width:100%">
        <el-table-column width="50">
          <template #default="{ row }">
            <el-badge :hidden="row.isRead === 1" is-dot />
          </template>
        </el-table-column>
        <el-table-column prop="title" label="标题" min-width="200" />
        <el-table-column prop="notificationType" label="类型" width="100">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.notificationType)" size="small">
              {{ typeLabel(row.notificationType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="senderName" label="发送人" width="120" />
        <el-table-column prop="sendTime" label="时间" width="180" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" v-if="!row.isRead" @click.stop="handleRead(row)">
              标为已读
            </el-button>
            <el-button link type="danger" size="small" @click.stop="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:page-size="query.pageSize"
        v-model:current-page="query.pageNum"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @change="loadData"
        class="pagination"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { Refresh } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getNotificationPage, getUnreadCount, markAsRead, markAllAsRead, deleteNotification } from '@/api/notification'
import type { NotificationVO, NotificationQuery } from '@/api/notification'
import { useUserStore } from '@/store/modules/user'

const userStore = useUserStore()
const currentUserId = computed(() => userStore.userId)

const loading = ref(false)
const list = ref<NotificationVO[]>([])
const total = ref(0)
const unreadCount = ref(0)
const systemCount = ref(0)
const emailCount = ref(0)

const query = reactive<NotificationQuery>({
  receiverId: 1,
  notificationType: undefined as any,
  isRead: undefined as any,
  pageNum: 1,
  pageSize: 20,
})

function typeTag(type: string) {
  return type === 'SYSTEM' ? 'primary' : type === 'EMAIL' ? 'warning' : 'info'
}

function typeLabel(type: string) {
  return type === 'SYSTEM' ? '系统' : type === 'EMAIL' ? '邮件' : type === 'APP' ? '应用' : type
}

async function loadData() {
  loading.value = true
  try {
    query.receiverId = currentUserId.value || 1
    const res = await getNotificationPage(query)
    if (res?.data) {
      list.value = res.data.records || []
      total.value = res.data.total || 0
    }
    const cntRes = await getUnreadCount(currentUserId.value || 1)
    unreadCount.value = cntRes?.data || 0
    systemCount.value = list.value.filter((n: NotificationVO) => n.notificationType === 'SYSTEM').length
    emailCount.value = list.value.filter((n: NotificationVO) => n.notificationType === 'EMAIL').length
  } finally {
    loading.value = false
  }
}

function resetQuery() {
  query.notificationType = undefined as any
  query.isRead = undefined as any
  query.pageNum = 1
  loadData()
}

async function handleRead(row: NotificationVO) {
  await markAsRead(row.notificationId)
  ElMessage.success('已标为已读')
  loadData()
}

async function handleMarkAllRead() {
  await ElMessageBox.confirm('确定全部标为已读？')
  await markAllAsRead(currentUserId.value || 1)
  ElMessage.success('已全部标为已读')
  loadData()
}

async function handleDelete(row: NotificationVO) {
  await ElMessageBox.confirm('确定删除该通知？')
  await deleteNotification(row.notificationId)
  ElMessage.success('已删除')
  loadData()
}

function handleRowClick(row: NotificationVO) {
  if (!row.isRead) handleRead(row)
}

function handleRefresh() {
  loadData()
}

onMounted(loadData)
</script>

<style scoped>
.notification-page { padding: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px; }
.page-title { margin: 0; font-size: 24px; font-weight: 500; }
.header-actions { display: flex; gap: 8px; }
.stat-row { margin-bottom: 16px; }
.stat-card { text-align: center; }
.stat-value { font-size: 32px; font-weight: 700; color: #303133; }
.stat-label { font-size: 13px; color: #909399; margin-top: 4px; }
.stat-card.unread .stat-value { color: #409eff; }
.stat-card.system .stat-value { color: #67c23a; }
.stat-card.email .stat-value { color: #e6a23c; }
.filter-card { margin-bottom: 16px; }
.pagination { margin-top: 16px; justify-content: center; }
</style>
