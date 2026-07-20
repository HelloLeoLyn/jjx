<!-- src/layout/components/Sidebar.vue -->
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
      background-color="#fff"
      text-color="#606266"
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

  // 折叠时隐藏标题
  :deep(.el-menu--collapse) ~ & .logo-container .title {
    display: none;
  }

  .sidebar-menu {
    flex: 1;
    overflow-y: auto;
    overflow-x: hidden;
    border-right: none;
    padding: 8px 0;

    &:not(.el-menu--collapse) {
      width: 100%;
    }

    // 展开状态样式
    &:not(.el-menu--collapse) {
      :deep(.el-menu-item) {
        margin: 2px 8px;
        width: calc(100% - 16px);
        border-radius: 8px;
        transition: all 0.2s;
        height: 40px;
        line-height: 40px;

        &:hover {
          background: #ecf5ff !important;
        }

        &.is-active {
          background: #ecf5ff !important;
          color: #409eff !important;
          font-weight: 600;

          &::before {
            content: '';
            position: absolute;
            left: 0;
            top: 50%;
            transform: translateY(-50%);
            width: 3px;
            height: 20px;
            background: #409eff;
            border-radius: 0 3px 3px 0;
          }
        }
      }

      :deep(.el-sub-menu__title) {
        margin: 2px 8px;
        width: calc(100% - 16px);
        border-radius: 8px;
        height: 40px;
        line-height: 40px;

        &:hover {
          background: #ecf5ff !important;
        }
      }

      :deep(.el-menu--inline) {
        .el-menu-item {
          margin: 1px 8px 1px 28px;
          width: calc(100% - 36px);
        }
      }
    }

    // 折叠状态 - 只保留悬浮效果和文字隐藏
    &.el-menu--collapse {
      :deep(.el-menu-item),
      :deep(.el-sub-menu__title) {
        &.is-active {
          background: #ecf5ff !important;
          color: #409eff !important;
        }

        &:hover {
          background: #ecf5ff !important;
        }
      }

      :deep(.el-menu-item) {
        &.is-active {
          &::before {
            display: none;
          }
        }
      }
    }
  }
}

.sidebar-container {
  &.collapsed {
    width: 64px !important;
  }
}

.sidebar-container::-webkit-scrollbar {
  width: 4px;
}

.sidebar-container::-webkit-scrollbar-thumb {
  background-color: #e8eaef;
  border-radius: 2px;
}
</style>
