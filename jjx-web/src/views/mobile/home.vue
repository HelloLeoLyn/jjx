<template>
  <div class="m-home">
    <!-- 问候 -->
    <div class="m-home-hello">
      <div class="m-hello-avatar">{{ avatarText }}</div>
      <div class="m-hello-text">
        <div class="m-hello-name">{{ helloWord }}，{{ nickName || userName || '' }}</div>
        <div class="m-hello-role">{{ roleText }}</div>
      </div>
      <div class="m-hello-date">{{ todayText }}</div>
    </div>

    <!-- 我的任务概览 -->
    <div v-if="loaded" class="m-home-card m-task-card" @click="goTask">
      <div class="m-card-title">
        <span>📋 我的任务</span>
        <span class="m-card-more">查看全部 ›</span>
      </div>
      <div class="m-task-stats">
        <div class="m-stat">
          <b class="num primary">{{ counts.todo }}</b>
          <span>待执行</span>
        </div>
        <div class="m-stat">
          <b class="num warn">{{ counts.doing }}</b>
          <span>执行中</span>
        </div>
        <div class="m-stat">
          <b class="num done">{{ counts.done }}</b>
          <span>已完成</span>
        </div>
      </div>
      <div v-if="noTask" class="m-no-task">当前没有分配给我的任务，去扫码看看有没有新工单</div>
    </div>

    <!-- 通知 -->
    <div class="m-home-card">
      <div class="m-card-title" @click="router.push('/m/notices')">
        <span>🔔 通知</span>
        <span v-if="unreadCount" class="m-unread-badge">{{ unreadCount }} 未读</span>
        <span v-else class="m-card-more">全部已读</span>
      </div>
      <div v-if="noticeList.length" class="m-notice-list">
        <div
          v-for="n in noticeList"
          :key="n.notificationId"
          class="m-notice-item"
          :class="{ unread: n.isRead === 0 }"
          @click="openNotice(n)"
        >
          <span class="m-notice-dot" v-if="n.isRead === 0"></span>
          <div class="m-notice-main">
            <div class="m-notice-title">{{ n.title }}</div>
            <div class="m-notice-time">{{ fmtTime(n.sendTime || n.createTime) }}</div>
          </div>
          <span class="m-notice-arrow">›</span>
        </div>
      </div>
      <div v-else class="m-home-empty">暂无通知</div>
    </div>

    <!-- 快捷宫格 -->
    <div class="m-home-card">
      <div class="m-card-title"><span>⚡ 快捷功能</span></div>
      <div class="m-grid">
        <div class="m-grid-item" @click="router.push('/m/scan')">
          <span class="m-grid-icon">📷</span>
          <span>扫码</span>
        </div>
        <div v-if="canReport" class="m-grid-item" @click="router.push('/m/reports')">
          <span class="m-grid-icon">🧾</span>
          <span>我的报工</span>
        </div>
        <div v-if="canQuality" class="m-grid-item" @click="router.push('/m/quality')">
          <span class="m-grid-icon">🔍</span>
          <span>质检判定</span>
        </div>
        <div v-if="canPick" class="m-grid-item" @click="router.push('/m/pick')">
          <span class="m-grid-icon">📦</span>
          <span>生产领料</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { getMyProductionExecutions } from '@/api/production/task'
import { ExecutionStatusEnum } from '@/enums/production'
import { getUnreadCount, getUnreadList, markAsRead } from '@/api/notification'
import { isWorkOrderNo } from '@/composables/useScanner'

const router = useRouter()
const userStore = useUserStore()

const userName = userStore.userName || ''
const nickName = userStore.nickName || ''
const roles: string[] = userStore.roles || []

const counts = ref({ todo: 0, doing: 0, done: 0 })
const loaded = ref(false)
const noTask = ref(false)

const unreadCount = ref(0)
const noticeList = ref<any[]>([])

const roleText = computed(() => {
  if (Array.isArray(roles) && roles.length) {
    return roles[0]
  }
  return ''
})

const avatarText = computed(() => (nickName || userName || '我').slice(0, 1))

const helloWord = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const todayText = new Date().toLocaleDateString('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' })

function fmtTime(t?: string): string {
  if (!t) return ''
  const d = new Date(t)
  const now = new Date()
  const same = d.toDateString() === now.toDateString()
  const pad = (x: number) => String(x).padStart(2, '0')
  const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`
  if (same) return hm
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${hm}`
}

// ===== 任务概览 =====
async function loadTasks() {
  loaded.value = false
  try {
    const res: any = await getMyProductionExecutions({ pageNum: 1, pageSize: 100 })
    const list: any[] = res?.data?.records || []
    const s = { todo: 0, doing: 0, done: 0 }
    list.forEach((ex) => {
      const v = Number(ex.executionStatus)
      if (v === ExecutionStatusEnum.PENDING.value) s.todo++
      else if (v === ExecutionStatusEnum.EXECUTING.value || v === ExecutionStatusEnum.PREPARING.value || v === ExecutionStatusEnum.PAUSED.value) s.doing++
      else if (v === ExecutionStatusEnum.COMPLETED.value) s.done++
    })
    counts.value = s
    noTask.value = list.length === 0
  } catch {
    counts.value = { todo: 0, doing: 0, done: 0 }
  } finally {
    loaded.value = true
  }
}

function goTask() {
  router.push('/m/order')
}

// ===== 通知（ERP 顶栏同源） =====
async function loadNotices() {
  const uid = userStore.userId
  if (!uid) return
  try {
    const c: any = await getUnreadCount(uid)
    unreadCount.value = Number(c?.data || 0)
    const r: any = await getUnreadList(uid)
    noticeList.value = (r?.data || []).slice(0, 5)
  } catch {
    // 通知加载失败不阻塞首页
  }
}

async function openNotice(n: any) {
  if (n.isRead === 0) {
    try {
      await markAsRead(n.notificationId)
      n.isRead = 1
      unreadCount.value = Math.max(0, unreadCount.value - 1)
    } catch {
      // 忽略
    }
  }
  // 深链：优先从标题/内容提取工单号 → 任务页；否则按类型映射
  const text = `${n.title || ''} ${n.content || ''}`
  const m = text.match(/WO-[\w-]+-\d{1,2}|WPO\d{8,}/)
  if (m && isWorkOrderNo(m[0])) {
    router.push({ path: '/m/order', query: { orderNo: m[0] } })
    return
  }
  const bt = String(n.bizType || '')
  if (bt.includes('work_report') || /报工|审批/.test(text)) {
    router.push('/m/report-approvals')
    return
  }
  if (bt.includes('quality') || text.includes('质检')) {
    router.push('/m/quality')
    return
  }
  if (bt.includes('pick') || bt.includes('outbound') || text.includes('领料')) {
    router.push('/m/pick')
    return
  }
  ElMessage.info('该通知请在 PC 端查看处理')
}

// ===== 宫格权限 =====
const canReport = computed(() => userStore.hasPermission('production:work-report:view') || userStore.hasPermission('production:work-report:add'))
const canQuality = computed(() => userStore.hasPermission('production:quality:judge'))
const canPick = computed(() => userStore.hasPermission('inventory:outbound:view') || userStore.hasPermission('inventory:outbound:add'))

onMounted(() => {
  loadTasks()
  loadNotices()
})
</script>

<style scoped>
.m-home {
  padding: 14px 14px 20px;
}
.m-home-hello {
  display: flex;
  align-items: center;
  gap: 12px;
  background: linear-gradient(135deg, #2b5aa7, #3f7bd6);
  color: #fff;
  border-radius: 14px;
  padding: 16px;
  margin-bottom: 12px;
}
.m-hello-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.25);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  font-weight: 700;
  flex-shrink: 0;
}
.m-hello-text {
  flex: 1;
  min-width: 0;
}
.m-hello-name {
  font-size: 17px;
  font-weight: 700;
}
.m-hello-role {
  font-size: 12px;
  opacity: 0.85;
  margin-top: 2px;
}
.m-hello-date {
  font-size: 12px;
  opacity: 0.9;
  align-self: flex-start;
}
.m-home-card {
  background: #fff;
  border-radius: 14px;
  padding: 14px;
  margin-bottom: 12px;
  box-shadow: 0 2px 10px rgba(43, 90, 167, 0.05);
}
.m-card-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
  cursor: pointer;
}
.m-card-more {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
}
.m-task-stats {
  display: flex;
}
.m-stat {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 6px 0;
}
.m-stat .num {
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
}
.m-stat span {
  font-size: 12px;
  color: #909399;
}
.num.primary { color: #2b5aa7; }
.num.warn { color: #e6a23c; }
.num.done { color: #67c23a; }
.m-no-task {
  font-size: 13px;
  color: #909399;
  padding: 6px 0 2px;
  text-align: center;
}
.m-unread-badge {
  font-size: 12px;
  color: #fff;
  background: #f56c6c;
  border-radius: 10px;
  padding: 2px 8px;
}
.m-notice-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 4px;
  border-bottom: 1px solid #f5f6f8;
  cursor: pointer;
}
.m-notice-item:last-child {
  border-bottom: none;
}
.m-notice-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  flex-shrink: 0;
}
.m-notice-main {
  flex: 1;
  min-width: 0;
}
.m-notice-title {
  font-size: 14px;
  color: #303133;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.m-notice-item.unread .m-notice-title {
  font-weight: 600;
}
.m-notice-time {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 2px;
}
.m-notice-arrow {
  color: #c0c4cc;
  font-size: 16px;
}
.m-home-empty {
  font-size: 13px;
  color: #c0c4cc;
  text-align: center;
  padding: 8px 0;
}
.m-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 10px;
}
.m-grid-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 12px 0;
  background: #f7f9fc;
  border-radius: 12px;
  font-size: 12px;
  color: #606266;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.m-grid-item:active {
  background: #edf2fa;
}
.m-grid-icon {
  font-size: 24px;
}
</style>
