<!-- src/layout/components/Navbar.vue -->
<template>
  <div class="navbar">
    <div class="left-menu">
      <div class="hamburger-container" @click="toggleSidebar">
        <el-icon :size="18" class="hamburger-icon">
          <DArrowRight v-if="isCollapse" />
          <DArrowLeft v-else />
        </el-icon>
      </div>
      <breadcrumb />
    </div>

    <div class="right-menu">
      <!-- 消息通知铃铛 -->
      <NavNotification />
      <el-dropdown trigger="click" @command="handleCommand">
        <div class="avatar-wrapper">
          <el-avatar :size="32" :src="avatar" />
          <span class="username">{{ username }}</span>
          <el-icon><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>
              个人中心
            </el-dropdown-item>
            <el-dropdown-item command="password">
              <el-icon><Lock /></el-icon>
              修改密码
            </el-dropdown-item>
            <el-dropdown-item divided command="logout">
              <el-icon><SwitchButton /></el-icon>
              退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { DArrowLeft, DArrowRight, ArrowDown, User, Lock, SwitchButton } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import Breadcrumb from './Breadcrumb.vue'
import NavNotification from './NavNotification.vue'

const router = useRouter()
const userStore = useUserStore()
const isCollapse = computed(() => userStore.sidebarCollapsed)
const username = computed(() => userStore.nickName || userStore.userName)
const avatar = computed(
  () => userStore.avatar || 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'
)

const toggleSidebar = () => {
  userStore.toggleSidebar()
}

const handleCommand = (command: string) => {
  switch (command) {
    case 'profile':
      router.push('/profile')
      break
    case 'password':
      router.push('/password')
      break
    case 'logout':
      handleLogout()
      break
  }
}

const handleLogout = async () => {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })
    await userStore.logout()
    ElMessage.success('退出登录成功')
    router.push('/login')
  } catch (error) {
    // 用户取消退出
  }
}
</script>

<style scoped lang="scss">
.navbar {
  height: 50px;
  overflow: hidden;
  position: relative;
  background: #fff;
  border-bottom: 1px solid #e8eaef;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 20px;
  flex-shrink: 0;

  .left-menu {
    display: flex;
    align-items: center;
    gap: 16px;

    .hamburger-container {
      cursor: pointer;
      display: flex;
      align-items: center;
      color: #909399;
      width: 32px;
      height: 32px;
      border-radius: 8px;
      justify-content: center;
      transition: all 0.2s;

      &:hover {
        color: #409eff;
        background: #ecf5ff;
      }

      .hamburger-icon {
        transition: transform 0.2s ease;
      }
    }
  }

  .right-menu {
    display: flex;
    align-items: center;

    .avatar-wrapper {
      display: flex;
      align-items: center;
      gap: 8px;
      cursor: pointer;
      padding: 4px 12px;
      border-radius: 8px;
      transition: all 0.2s;

      &:hover {
        background: #f5f6fa;
      }

      .username {
        font-size: 14px;
        color: #303133;
        font-weight: 500;
      }
    }
  }
}
</style>
