<template>
  <div class="m-scan">
    <header class="m-header">
      <span class="m-header-user">{{ nickName || userName }}</span>
      <el-button link type="primary" @click="handleLogout">退出登录</el-button>
    </header>

    <div class="m-scan-body">
      <div class="m-scan-icon">📷</div>
      <h3 class="m-scan-title">扫码定位工单</h3>
      <p class="m-scan-tip">扫码枪扫描纸质工单二维码，或手动输入工单号</p>

      <el-input
        v-model="orderNo"
        class="m-scan-input"
        placeholder="工单号（如 WPO2608120001）"
        size="large"
        clearable
        @keyup.enter="handleGo"
      />
      <el-button type="primary" size="large" class="m-scan-btn" @click="handleGo">
        定位工单
      </el-button>

      <el-button size="large" class="m-scan-btn m-scan-btn-secondary" @click="router.push('/m/reports')">
        我的报工记录
      </el-button>

      <el-button size="large" class="m-scan-btn m-scan-btn-secondary" @click="router.push('/m/quality')">
        质检判定
      </el-button>

      <el-button size="large" class="m-scan-btn m-scan-btn-secondary" @click="router.push('/m/pick')">
        生产领料
      </el-button>

      <div class="m-scan-history" v-if="recent.length">
        <div class="m-scan-history-title">最近扫描</div>
        <div
          v-for="no in recent"
          :key="no"
          class="m-scan-history-item"
          @click="goOrder(no)"
        >
          {{ no }}
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { useScanner, isWorkOrderNo } from '@/composables/useScanner'

const router = useRouter()
const userStore = useUserStore()

const orderNo = ref('')
const recent = ref<string[]>(JSON.parse(localStorage.getItem('m_recent_order') || '[]'))
const userName = userStore.userName || ''
const nickName = userStore.nickName || ''

// 扫码枪监听（仅本页启用）
useScanner({
  enabled: () => true,
  onScan: (code) => {
    orderNo.value = code
    goOrder(code)
  },
})

function pushRecent(no: string) {
  recent.value = [no, ...recent.value.filter((x) => x !== no)].slice(0, 5)
  localStorage.setItem('m_recent_order', JSON.stringify(recent.value))
}

function goOrder(no: string) {
  if (!isWorkOrderNo(no)) {
    ElMessage.warning('工单号格式不正确')
    return
  }
  pushRecent(no)
  router.push({ path: '/m/order', query: { orderNo: no } })
}

function handleGo() {
  const no = orderNo.value.trim()
  if (!no) {
    ElMessage.warning('请输入或扫描工单号')
    return
  }
  goOrder(no)
}

function handleLogout() {
  userStore.resetToken()
  router.replace('/m/login')
}
</script>

<style scoped>
.m-scan {
  min-height: 100vh;
  background: #f5f7fa;
}
.m-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: #fff;
  border-bottom: 1px solid #ebeef5;
}
.m-header-user {
  font-size: 15px;
  color: #303133;
}
.m-scan-body {
  padding: 48px 24px;
  text-align: center;
}
.m-scan-icon {
  font-size: 56px;
}
.m-scan-title {
  margin: 16px 0 8px;
  font-size: 20px;
  color: #303133;
}
.m-scan-tip {
  margin: 0 0 32px;
  font-size: 13px;
  color: #909399;
}
.m-scan-input {
  max-width: 420px;
  margin: 0 auto 16px;
}
.m-scan-btn {
  max-width: 420px;
  width: 100%;
  height: 48px;
  font-size: 16px;
}
.m-scan-btn-secondary {
  margin-top: 12px;
  background: #fff;
  border: 1px solid #dcdfe6;
  color: #606266;
}
.m-scan-history {
  margin-top: 40px;
  text-align: left;
  max-width: 420px;
  margin-left: auto;
  margin-right: auto;
}
.m-scan-history-title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
}
.m-scan-history-item {
  padding: 10px 14px;
  background: #fff;
  border-radius: 8px;
  margin-bottom: 8px;
  font-size: 14px;
  color: #409eff;
  cursor: pointer;
  border: 1px solid #ebeef5;
}
</style>
