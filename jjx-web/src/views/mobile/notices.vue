<template>
  <div class="m-notices">
    <div class="m-tabs">
      <span class="m-tab" :class="{ active: tab === 'unread' }" @click="switchTab('unread')">未读</span>
      <span class="m-tab" :class="{ active: tab === 'all' }" @click="switchTab('all')">全部</span>
      <span v-if="unreadCount > 0" class="m-tab-right" @click="readAll">全部已读</span>
    </div>
    <div v-loading="loading" class="m-list">
      <div
        v-for="n in list"
        :key="n.notificationId"
        class="m-item"
        :class="{ unread: n.isRead === 0 }"
        @click="openNotice(n)"
      >
        <span class="m-dot" v-if="n.isRead === 0"></span>
        <div class="m-main">
          <div class="m-title">{{ n.title }}</div>
          <div v-if="n.content" class="m-content">{{ n.content }}</div>
          <div class="m-time">{{ fmtTime(n.sendTime || n.createTime) }}</div>
        </div>
        <span class="m-arrow">›</span>
      </div>
      <div v-if="!loading && !list.length" class="m-empty">
        {{ tab === 'unread' ? '暂无未读通知' : '暂无通知' }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { getNotificationPage, getUnreadCount, markAsRead, markAllAsRead } from '@/api/notification'
import { isWorkOrderNo } from '@/composables/useScanner'

const router = useRouter()
const userStore = useUserStore()

const tab = ref<'unread' | 'all'>('unread')
const list = ref<any[]>([])
const loading = ref(false)
const unreadCount = ref(0)
const total = ref(0)
const pageNum = ref(1)

function fmtTime(t?: string): string {
  if (!t) return ''
  const d = new Date(t)
  const pad = (x: number) => String(x).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function load(reset = false) {
  const uid = userStore.userId
  if (!uid) return
  if (reset) pageNum.value = 1
  loading.value = true
  try {
    const params: any = {
      receiverId: uid,
      pageNum: pageNum.value,
      pageSize: 20,
    }
    if (tab.value === 'unread') params.isRead = 0
    const res: any = await getNotificationPage(params)
    const records = res?.data?.records || res?.data?.list || []
    list.value = reset ? records : [...list.value, ...records]
    total.value = Number(res?.data?.total || records.length)
  } catch {
    // 忽略
  } finally {
    loading.value = false
  }
}

async function loadUnreadCount() {
  const uid = userStore.userId
  if (!uid) return
  try {
    const c: any = await getUnreadCount(uid)
    unreadCount.value = Number(c?.data || 0)
  } catch {
    // 忽略
  }
}

function switchTab(t: 'unread' | 'all') {
  if (tab.value === t) return
  tab.value = t
  load(true)
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
  // 深链：工单号优先，其次业务类型映射（与 Home 一致）
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

async function readAll() {
  const uid = userStore.userId
  if (!uid) return
  try {
    await markAllAsRead(uid)
    unreadCount.value = 0
    list.value = []
    ElMessage.success('已全部标为已读')
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}

onMounted(() => {
  loadUnreadCount()
  load(true)
})
</script>

<style scoped>
.m-notices {
  min-height: 100vh;
  background: #f5f7fa;
}
.m-tabs {
  display: flex;
  align-items: center;
  gap: 18px;
  background: #fff;
  padding: 10px 16px;
  border-bottom: 1px solid #ebeef5;
  position: sticky;
  top: 0;
  z-index: 5;
}
.m-tab {
  font-size: 15px;
  color: #606266;
  cursor: pointer;
  padding-bottom: 4px;
}
.m-tab.active {
  color: #2b5aa7;
  font-weight: 600;
  border-bottom: 2px solid #2b5aa7;
}
.m-tab-right {
  margin-left: auto;
  font-size: 13px;
  color: #909399;
  cursor: pointer;
}
.m-list {
  padding: 10px 12px;
}
.m-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  background: #fff;
  border-radius: 12px;
  padding: 12px 14px;
  margin-bottom: 10px;
  cursor: pointer;
}
.m-item.unread {
  background: #f0f6ff;
}
.m-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
  flex-shrink: 0;
  margin-top: 6px;
}
.m-main {
  flex: 1;
  min-width: 0;
}
.m-title {
  font-size: 14px;
  color: #303133;
  font-weight: 500;
}
.m-item.unread .m-title {
  font-weight: 700;
}
.m-content {
  font-size: 13px;
  color: #606266;
  margin-top: 4px;
  word-break: break-all;
}
.m-time {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 6px;
}
.m-arrow {
  color: #c0c4cc;
  font-size: 16px;
  margin-top: 4px;
}
.m-empty {
  text-align: center;
  color: #c0c4cc;
  font-size: 13px;
  padding: 60px 0;
}
</style>
