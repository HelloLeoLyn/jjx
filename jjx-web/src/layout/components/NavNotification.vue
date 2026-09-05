<template>
  <div class="nav-notification" v-loading="loading">
    <el-popover
      placement="bottom-end"
      :width="380"
      trigger="click"
      :visible="popoverVisible"
      @hide="handlePopoverHide"
      popper-class="notification-popover"
    >
      <template #reference>
        <div class="bell-wrapper" @click="togglePopover">
          <el-badge :value="unreadCount" :hidden="unreadCount === 0" :max="99" class="bell-badge">
            <el-icon :size="20" class="bell-icon">
              <Bell />
            </el-icon>
          </el-badge>
        </div>
      </template>

      <!-- 弹出内容 -->
      <div class="notification-dropdown">
        <div class="dropdown-header">
          <span class="dropdown-title">消息通知</span>
          <el-button
            link
            type="primary"
            size="small"
            :disabled="unreadCount === 0"
            @click="handleMarkAllRead"
          >
            全部标为已读
          </el-button>
        </div>

        <div class="dropdown-body" v-if="unreadList.length > 0">
          <div
            v-for="item in unreadList"
            :key="item.notificationId"
            class="notification-item"
            @click="handleRead(item)"
          >
            <div class="item-header">
              <el-tag :type="typeTag(item.notificationType)" size="small" effect="plain" class="item-type">
                {{ typeLabel(item.notificationType) }}
              </el-tag>
              <span class="item-time">{{ formatTime(item.sendTime || item.createTime) }}</span>
            </div>
            <div class="item-title">{{ item.title }}</div>
            <div class="item-content" v-if="item.content">{{ item.content }}</div>
          </div>
        </div>

        <el-empty v-else description="暂无未读消息" :image-size="60" />

        <div class="dropdown-footer">
          <el-button link type="primary" size="small" @click="goToNotificationPage">
            查看全部消息
          </el-button>
        </div>
      </div>
    </el-popover>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { Bell } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { getUnreadCount, getUnreadList, markAsRead, markAllAsRead } from '@/api/notification'
import { useUserStore } from '@/store/modules/user'
import type { NotificationVO } from '@/api/notification'
import { resolveJump } from '@/utils/bizJump'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const popoverVisible = ref(false)
const unreadCount = ref(0)
const unreadList = ref<NotificationVO[]>([])
let pollTimer: ReturnType<typeof setInterval> | null = null

// 获取当前用户ID
const receiverId = computed(() => userStore.userId)

// 加载未读数据
async function loadUnread() {
  if (!receiverId.value) return
  try {
    const countRes = await getUnreadCount(receiverId.value)
    if (countRes?.data !== undefined) {
      unreadCount.value = countRes.data
    }
  } catch {
    // 静默处理
  }
}

// 加载未读列表（弹窗打开时调用）
async function loadUnreadList() {
  if (!receiverId.value) return
  loading.value = true
  try {
    const res = await getUnreadList(receiverId.value)
    if (res?.data) {
      unreadList.value = res.data.slice(0, 10) // 最多显示10条
    }
  } catch {
    unreadList.value = []
  } finally {
    loading.value = false
  }
}

// 切换弹窗
function togglePopover() {
  popoverVisible.value = !popoverVisible.value
  if (popoverVisible.value) {
    loadUnreadList()
  }
}

// 关闭弹窗
function handlePopoverHide() {
  popoverVisible.value = false
}

// 标记单条已读
async function handleRead(item: NotificationVO) {
  try {
    await markAsRead(item.notificationId)
    item.isRead = 1
    unreadCount.value = Math.max(0, unreadCount.value - 1)
    unreadList.value = unreadList.value.filter(n => n.notificationId !== item.notificationId)
  } catch {
    // 静默处理
  }
  const target = resolveJump(item.bizType || '', item.bizId)
  if (target) {
    popoverVisible.value = false
    router.push(target)
  }
}

// 全部标为已读
async function handleMarkAllRead() {
  if (!receiverId.value) return
  try {
    await markAllAsRead(receiverId.value)
    unreadCount.value = 0
    unreadList.value = []
    ElMessage.success('已全部标为已读')
  } catch {
    // 静默处理
  }
}

// 跳转到通知管理页
function goToNotificationPage() {
  popoverVisible.value = false
  router.push('/notification/index')
}

// 类型标签颜色
function typeTag(type: string) {
  return type === 'SYSTEM' ? 'primary' : type === 'EMAIL' ? 'warning' : 'info'
}

// 类型标签文字
function typeLabel(type: string) {
  return type === 'SYSTEM' ? '系统' : type === 'EMAIL' ? '邮件' : type === 'APP' ? '应用' : type
}

// 格式化时间
function formatTime(timeStr: string) {
  if (!timeStr) return ''
  try {
    const d = new Date(timeStr)
    const now = new Date()
    const diff = now.getTime() - d.getTime()
    const minutes = Math.floor(diff / 60000)
    if (minutes < 1) return '刚刚'
    if (minutes < 60) return `${minutes}分钟前`
    const hours = Math.floor(minutes / 60)
    if (hours < 24) return `${hours}小时前`
    const days = Math.floor(hours / 24)
    if (days < 7) return `${days}天前`
    const month = String(d.getMonth() + 1).padStart(2, '0')
    const day = String(d.getDate()).padStart(2, '0')
    return `${month}-${day}`
  } catch {
    return timeStr
  }
}

onMounted(() => {
  loadUnread()
  // 每30秒轮询未读数
  pollTimer = setInterval(loadUnread, 30000)
})

onUnmounted(() => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
})
</script>

<style scoped>
.nav-notification {
  display: flex;
  align-items: center;
}

.bell-wrapper {
  position: relative;
  cursor: pointer;
  padding: 6px 10px;
  border-radius: 8px;
  transition: all 0.2s;
  display: flex;
  align-items: center;
}

.bell-wrapper:hover {
  background: #f5f6fa;
}

.bell-icon {
  color: #606266;
  transition: color 0.2s;
}

.bell-wrapper:hover .bell-icon {
  color: #409eff;
}

.bell-badge :deep(.el-badge__content) {
  border: 2px solid #fff;
}
</style>

<style>
/* 全局样式避免 scoped 限制 */
.notification-popover {
  padding: 0 !important;
}

.notification-popover .el-popover__title {
  display: none;
}

.notification-dropdown {
  display: flex;
  flex-direction: column;
  max-height: 480px;
}

.dropdown-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 16px 10px;
  border-bottom: 1px solid #ebeef5;
}

.dropdown-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.dropdown-body {
  flex: 1;
  overflow-y: auto;
  max-height: 360px;
}

.notification-item {
  padding: 12px 16px;
  cursor: pointer;
  transition: background 0.2s;
  border-bottom: 1px solid #f5f6fa;
}

.notification-item:hover {
  background: #f5f7fa;
}

.notification-item:last-child {
  border-bottom: none;
}

.item-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.item-type {
  flex-shrink: 0;
}

.item-time {
  font-size: 12px;
  color: #909399;
  margin-left: 8px;
  white-space: nowrap;
}

.item-title {
  font-size: 14px;
  color: #303133;
  line-height: 1.5;
  font-weight: 500;
}

.item-content {
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.dropdown-footer {
  padding: 10px 16px;
  border-top: 1px solid #ebeef5;
  text-align: center;
}
</style>
