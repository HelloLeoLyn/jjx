<!-- src/layout/components/Sidebar.vue -->
<template>
  <div class="sidebar-container">
    <div class="logo-container">
      <img src="@/assets/logo.png" alt="logo" class="logo" />
      <span v-if="!isCollapse" class="title">JJX ERP系统</span>
    </div>
    <el-menu
      :default-active="activeMenu"
      :collapse="isCollapse"
      :unique-opened="true"
      :collapse-transition="false"
      background-color="#304156"
      text-color="#bfcbd9"
      active-text-color="#409EFF"
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
  background-color: #304156;

  .logo-container {
    height: 50px;
    display: flex;
    align-items: center;
    justify-content: center;
    background-color: #2b2f3a;
    padding: 0 16px;
    flex-shrink: 0;

    .logo {
      width: 32px;
      height: 32px;
    }

    .title {
      margin-left: 12px;
      color: #fff;
      font-size: 16px;
      font-weight: 600;
      white-space: nowrap;
    }
  }

  .sidebar-menu {
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
    border-right: none;

    &:not(.el-menu--collapse) {
      width: 210px;
    }
  }
}

.sidebar-container::-webkit-scrollbar {
  width: 6px;
}

.sidebar-container::-webkit-scrollbar-thumb {
  background-color: #304156;
  border-radius: 3px;
}
</style>
