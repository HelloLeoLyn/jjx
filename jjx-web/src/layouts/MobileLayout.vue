<template>
  <div class="m-layout">
    <!-- 顶部标题栏 -->
    <header class="m-topbar">
      <div class="m-topbar-left">
        <el-button
          v-if="showBack"
          link
          @click="goBack"
          style="font-size: 16px; color: #303133"
          >← 返回</el-button
        >
        <span v-else class="m-topbar-brand">JJX 生产</span>
      </div>
      <span class="m-topbar-title">{{ pageTitle }}</span>
      <div class="m-topbar-right">
        <el-dropdown trigger="click" @command="onCommand">
          <span class="m-topbar-user">{{ nickName || userName || '我' }}</span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <!-- 内容区 -->
    <main class="m-content">
      <router-view />
    </main>

    <!-- 底部 TabBar -->
    <nav class="m-tabbar">
      <div
        v-for="tab in tabs"
        :key="tab.path"
        class="m-tab"
        :class="{ active: isActive(tab) }"
        @click="goTab(tab)"
      >
        <span class="m-tab-icon">{{ tab.icon }}</span>
        <span class="m-tab-label">{{ tab.label }}</span>
      </div>
    </nav>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/store/modules/user'

/**
 * 移动端主框架（2026-09-04 H5 应用化）：
 * 顶部标题栏 + 内容区 + 底部 TabBar；/m/login 独立无壳
 */
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const userName = userStore.userName || ''
const nickName = userStore.nickName || ''

const tabs = [
  { path: '/m/order', label: '任务', icon: '📋', match: /^\/m\/order/ },
  { path: '/m/home', label: '首页', icon: '🏠', match: /^\/m\/home/ },
  { path: '/m/scan', label: '扫码', icon: '📷', match: /^\/m\/scan/ },
]

const pageTitle = computed(() => String((route.meta.title as string) || '移动端'))
const showBack = computed(() => {
  // Tab 首页不显示返回（没有上一级）；子流程页（report 带参进入等）显示
  const p = route.path
  if (p === '/m/order' || p === '/m/home' || p === '/m/scan') {
    return false
  }
  return true
})

function isActive(tab: { match: RegExp }) {
  return tab.match.test(route.path)
}

function goTab(tab: { path: string }) {
  if (route.path !== tab.path) {
    router.push(tab.path)
  }
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.replace('/m/order')
  }
}

function onCommand(cmd: string) {
  if (cmd === 'logout') {
    userStore.resetToken()
    router.replace('/m/login')
  }
}
</script>

<style scoped>
.m-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f7fa;
  max-width: 560px;
  margin: 0 auto;
}
.m-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 48px;
  padding: 0 12px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  flex-shrink: 0;
  position: sticky;
  top: 0;
  z-index: 10;
}
.m-topbar-left {
  width: 90px;
  text-align: left;
}
.m-topbar-brand {
  font-size: 16px;
  font-weight: 700;
  color: #2b5aa7;
}
.m-topbar-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  flex: 1;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.m-topbar-right {
  width: 90px;
  text-align: right;
}
.m-topbar-user {
  font-size: 14px;
  color: #606266;
  cursor: pointer;
}
.m-content {
  flex: 1;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
  padding-bottom: calc(60px + env(safe-area-inset-bottom));
}
.m-tabbar {
  display: flex;
  height: calc(56px + env(safe-area-inset-bottom));
  padding-bottom: env(safe-area-inset-bottom);
  background: #fff;
  border-top: 1px solid #ebeef5;
  flex-shrink: 0;
  position: sticky;
  bottom: 0;
  z-index: 10;
}
.m-tab {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: #909399;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
  user-select: none;
}
.m-tab.active {
  color: #2b5aa7;
}
.m-tab-icon {
  font-size: 20px;
  line-height: 1;
}
.m-tab-label {
  font-size: 11px;
}
</style>
