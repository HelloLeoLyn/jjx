<template>
  <div class="dashboard-container">
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><Goods /></el-icon>
              <span>产品总数</span>
            </div>
          </template>
          <div class="stat-value">{{ stats.productCount }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><Tickets /></el-icon>
              <span>工单总数</span>
            </div>
          </template>
          <div class="stat-value">{{ stats.orderCount }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><User /></el-icon>
              <span>用户总数</span>
            </div>
          </template>
          <div class="stat-value">{{ stats.userCount }}</div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card class="stat-card" shadow="hover">
          <template #header>
            <div class="card-header">
              <el-icon><OfficeBuilding /></el-icon>
              <span>部门总数</span>
            </div>
          </template>
          <div class="stat-value">{{ stats.deptCount }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20" class="mt-20">
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>快捷操作</span>
            </div>
          </template>
          <div class="quick-actions">
            <el-button type="primary" @click="$router.push('/dashboard/product/list')">
              <el-icon><Goods /></el-icon>
              产品管理
            </el-button>
            <el-button type="success" @click="$router.push('/dashboard/production/order')">
              <el-icon><Tickets /></el-icon>
              生产工单
            </el-button>
            <el-button type="warning" @click="$router.push('/dashboard/system/user')">
              <el-icon><User /></el-icon>
              用户管理
            </el-button>
            <el-button type="info" @click="$router.push('/dashboard/system/role')">
              <el-icon><UserFilled /></el-icon>
              角色管理
            </el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <template #header>
            <div class="card-header">
              <span>系统信息</span>
            </div>
          </template>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="系统名称">JJX ERP系统</el-descriptions-item>
            <el-descriptions-item label="系统版本">v1.0.0</el-descriptions-item>
            <el-descriptions-item label="当前用户">{{ userInfo.username || '-' }}</el-descriptions-item>
            <el-descriptions-item label="登录时间">{{ loginTime }}</el-descriptions-item>
          </el-descriptions>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useUserStore } from '@/store/modules/user'
import { Goods, Tickets, User, UserFilled, OfficeBuilding } from '@element-plus/icons-vue'

const userStore = useUserStore()
const userInfo = ref<Record<string, any>>(userStore.userInfo || {})

const stats = reactive({
  productCount: 0,
  orderCount: 0,
  userCount: 0,
  deptCount: 0,
})

const loginTime = ref(new Date().toLocaleString())

onMounted(() => {
  // 这里可以调用API获取统计数据
  // 暂时使用模拟数据
  stats.productCount = 128
  stats.orderCount = 256
  stats.userCount = 32
  stats.deptCount = 8
})
</script>

<style scoped lang="scss">
.dashboard-container {
  padding: 20px;
}

.stat-card {
  .card-header {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 14px;
    color: #606266;
  }

  .stat-value {
    font-size: 32px;
    font-weight: bold;
    color: #409eff;
    text-align: center;
    padding: 10px 0;
  }
}

.mt-20 {
  margin-top: 20px;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
</style>
