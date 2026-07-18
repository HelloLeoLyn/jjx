<!-- src/layout/index.vue -->
<template>
  <div :class="classObj" class="app-wrapper">
    <div
      v-if="device === 'mobile' && !sidebar.opened"
      class="drawer-bg"
      @click="handleClickOutside"
    />
    <sidebar class="sidebar-container" />
    <div class="main-container">
      <navbar />
      <tags-view v-if="showTagsView" />
      <app-main />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useUserStore } from '@/store/modules/user'
import Sidebar from './components/Sidebar.vue'
import Navbar from './components/Navbar.vue'
import TagsView from './components/TagsView.vue'
import AppMain from './components/AppMain.vue'

const userStore = useUserStore()

const sidebar = computed(() => ({
  opened: !userStore.getSidebarCollapsed,
}))

const device = computed(() => 'desktop')
const showTagsView = computed(() => true)

const classObj = computed(() => ({
  hideSidebar: !sidebar.value.opened,
  openSidebar: sidebar.value.opened,
  mobile: device.value === 'mobile',
}))

const handleClickOutside = () => {
  // 移动端点击遮罩关闭侧边栏
}
</script>

<style lang="scss" scoped>
.app-wrapper {
  position: relative;
  height: 100vh;
  width: 100%;
  display: flex;

  .sidebar-container {
    flex-shrink: 0;
    width: 210px;
    transition: width 0.28s;
    background-color: #304156;
    overflow: hidden;
  }

  .main-container {
    flex: 1;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    background-color: #f0f2f6;
  }

  &.hideSidebar {
    .sidebar-container {
      width: 54px;
    }
  }
}

.drawer-bg {
  background: #000;
  opacity: 0.3;
  width: 100%;
  top: 0;
  height: 100%;
  position: absolute;
  z-index: 999;
}
</style>
