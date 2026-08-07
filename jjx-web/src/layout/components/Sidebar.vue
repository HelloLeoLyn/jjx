<!-- src/layout/components/Sidebar.vue -->
<!-- 官方标准写法（DEV-663 重做）：el-menu 原生样式，不做自定义菜单项覆盖 -->
<template>
  <div class="sidebar-container" :class="{ collapsed: isCollapse }">
    <div class="logo-container">
      <img src="@/assets/logo.png" alt="logo" class="logo" />
      <span v-if="!isCollapse" class="title">JJX ERP系统</span>
    </div>
    <el-menu
      :default-active="activeMenu"
      :collapse="isCollapse"
      :unique-opened="true"
      :collapse-transition="false"
      router
      class="sidebar-menu"
    >
      <sidebar-item v-for="item in menuList" :key="item.path" :item="item" />
    </el-menu>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/store/modules/user'
import { usePermissionStore } from '@/store/modules/permission'
import SidebarItem from './SidebarItem.vue'

const route = useRoute()
const userStore = useUserStore()
const permissionStore = usePermissionStore()

// 系统名称（登录页加载配置后写入 localStorage）
const systemName = computed(() => localStorage.getItem('system_name') || 'JJX ERP系统')

const isCollapse = computed(() => userStore.sidebarCollapsed)
const menuList = computed(() => permissionStore.getMenus)

const activeMenu = computed(() => {
  const { meta, path } = route
  if (meta.activeMenu) {
    return meta.activeMenu as string
  }
  return path
})
</script>

<style scoped lang="scss">
.sidebar-container {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: #fff;

  .logo-container {
    height: 56px;
    display: flex;
    align-items: center;
    padding: 0 20px;
    flex-shrink: 0;
    border-bottom: 1px solid #e8eaef;

    .logo {
      width: 30px;
      height: 30px;
      border-radius: 6px;
    }

    .title {
      margin-left: 12px;
      color: #1a73e8;
      font-size: 18px;
      font-weight: 700;
      white-space: nowrap;
      letter-spacing: 0.5px;
    }
  }

  .sidebar-menu {
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
    border-right: none;
    padding: 8px 0;
  }

  // 折叠时隐藏标题
  .logo-container .title {
    display: none;
  }

  &.collapsed {
    .logo-container .title {
      display: none;
    }
  }
}
</style>
