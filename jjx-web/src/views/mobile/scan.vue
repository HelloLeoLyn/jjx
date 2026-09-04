<template>
  <div class="m-scan">
    <header class="m-header">
      <span class="m-header-user">{{ nickName || userName }}</span>
      <el-button link type="primary" @click="handleLogout">退出登录</el-button>
    </header>

    <div class="m-scan-body">
      <div class="m-scan-icon">📷</div>
      <h3 class="m-scan-title">扫码定位工单</h3>
      <p class="m-scan-tip">扫码枪/PDA 扫描纸质工单二维码，手机可用摄像头扫，或手动输入工单号</p>

      <el-button
        v-if="!scanning"
        size="large"
        type="success"
        class="m-scan-btn m-scan-cam"
        plain
        @click="startCameraScan"
      >
        📷 摄像头扫码
      </el-button>

      <!-- 摄像头扫码区（2026-09-04：手机扫码；需 HTTPS/localhost 或浏览器允许不安全源摄像头） -->
      <div v-if="scanning" class="m-cam-wrap">
        <div id="m-qr-reader" class="m-qr-reader"></div>
        <el-button size="large" class="m-scan-btn" @click="stopCameraScan">取消扫码</el-button>
      </div>

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
import { ref, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { useScanner, isWorkOrderNo } from '@/composables/useScanner'
import { Html5Qrcode } from 'html5-qrcode'

const router = useRouter()
const userStore = useUserStore()

const orderNo = ref('')
const recent = ref<string[]>(JSON.parse(localStorage.getItem('m_recent_order') || '[]'))
const userName = userStore.userName || ''
const nickName = userStore.nickName || ''

// ===== 手机摄像头扫码（2026-09-04）=====
const scanning = ref(false)
let cameraScanner: Html5Qrcode | null = null

async function startCameraScan() {
  if (scanning.value) return
  // 摄像头需要安全上下文（HTTPS 或 localhost）；http://内网IP 会被浏览器拒绝，给明确提示
  if (!window.isSecureContext) {
    ElMessage.warning('当前为非 HTTPS 环境，浏览器禁止调摄像头；请用 PDA 扫描键/扫码枪，或手机 Chrome 将该地址加入不安全源白名单后重试')
    return
  }
  try {
    // 等 DOM 渲染出容器再启动
    await new Promise((r) => setTimeout(r, 100))
    cameraScanner = new Html5Qrcode('m-qr-reader')
    await cameraScanner.start(
      { facingMode: 'environment' },
      { fps: 10, qrbox: { width: 220, height: 220 } },
      (decodedText) => {
        stopCameraScan()
        if (isWorkOrderNo(decodedText)) {
          goOrder(decodedText)
        } else {
          ElMessage.warning(`识别到非工单号内容：${decodedText.slice(0, 30)}`)
        }
      },
      () => {
        // 单帧解码失败忽略（持续扫）
      }
    )
    scanning.value = true
  } catch (e: any) {
    scanning.value = false
    const name = e?.name || ''
    if (name === 'NotAllowedError') {
      ElMessage.error('摄像头权限被拒绝，请在浏览器设置中允许访问摄像头')
    } else if (name === 'NotFoundError') {
      ElMessage.error('未检测到摄像头设备')
    } else {
      ElMessage.error('摄像头启动失败：' + (e?.message || e))
    }
  }
}

async function stopCameraScan() {
  scanning.value = false
  if (cameraScanner) {
    try {
      await cameraScanner.stop()
      cameraScanner.clear()
    } catch {
      // 未启动/已停止时忽略
    }
    cameraScanner = null
  }
}

onBeforeUnmount(() => {
  stopCameraScan()
})

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
.m-scan-cam {
  margin-bottom: 14px;
}
.m-cam-wrap {
  max-width: 420px;
  margin: 0 auto 16px;
}
.m-qr-reader {
  width: 100%;
  border-radius: 10px;
  overflow: hidden;
  margin-bottom: 12px;
  background: #000;
}
.m-qr-reader video {
  width: 100%;
  display: block;
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
