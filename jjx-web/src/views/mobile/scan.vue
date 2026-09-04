<template>
  <div class="m-scan">
    <!-- 摄像头扫码 -->
    <div class="m-scan-card">
      <button v-if="!scanning" class="m-scan-cam-btn" @click="startCameraScan">
        <span class="cam-icon">📷</span>
        <span class="cam-text">
          <b>摄像头扫码</b>
          <small>对准工单二维码自动识别</small>
        </span>
      </button>

      <div v-if="scanning" class="m-cam-wrap">
        <div id="m-qr-reader" class="m-qr-reader"></div>
        <button class="m-scan-cancel" @click="stopCameraScan">取消扫码</button>
      </div>
    </div>

    <!-- 或手动输入 -->
    <div class="m-divider"><span>或手动输入工单号</span></div>

    <div class="m-scan-input-row">
      <input
        v-model="orderNo"
        class="m-scan-input"
        placeholder="如 WO-PL2609040002-01"
        enterkeyhint="go"
        @keyup.enter="handleGo"
      />
      <button class="m-scan-go-btn" @click="handleGo">定位</button>
    </div>

    <p class="m-scan-tip">💡 也支持 PDA 扫描键 / 扫码枪：光标在输入框时直接扫</p>

    <!-- 最近扫描 -->
    <div v-if="recent.length" class="m-recent">
      <div class="m-recent-title">最近扫描</div>
      <div v-for="no in recent" :key="no" class="m-recent-item" @click="goOrder(no)">
        <span class="m-recent-no">{{ no }}</span>
        <span class="m-recent-arrow">›</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onBeforeUnmount } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useScanner, isWorkOrderNo } from '@/composables/useScanner'
import { Html5Qrcode } from 'html5-qrcode'

const router = useRouter()

const orderNo = ref('')
const recent = ref<string[]>(JSON.parse(localStorage.getItem('m_recent_order') || '[]'))

// ===== 手机摄像头扫码 =====
const scanning = ref(false)
let cameraScanner: Html5Qrcode | null = null

async function startCameraScan() {
  if (scanning.value) return
  if (!window.isSecureContext) {
    ElMessage.warning('当前为非 HTTPS 环境，浏览器禁止调摄像头；请用 PDA 扫描键/扫码枪，或手机 Chrome 将该地址加入不安全源白名单后重试')
    return
  }
  try {
    scanning.value = true
    await new Promise((r) => setTimeout(r, 150))
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
        // 单帧解码失败忽略
      }
    )
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
      // ignore
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
</script>

<style scoped>
.m-scan {
  padding: 16px 16px 24px;
}
.m-scan-card {
  background: #fff;
  border-radius: 14px;
  padding: 14px;
  box-shadow: 0 2px 10px rgba(43, 90, 167, 0.06);
}
.m-scan-cam-btn {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  border: none;
  background: linear-gradient(135deg, #2b5aa7, #3f7bd6);
  color: #fff;
  border-radius: 12px;
  padding: 18px 20px;
  cursor: pointer;
  -webkit-tap-highlight-color: transparent;
}
.m-scan-cam-btn:active {
  opacity: 0.9;
}
.cam-icon {
  font-size: 30px;
}
.cam-text {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  text-align: left;
  gap: 3px;
}
.cam-text b {
  font-size: 17px;
}
.cam-text small {
  font-size: 12px;
  opacity: 0.85;
}
.m-cam-wrap {
  text-align: center;
}
.m-qr-reader {
  width: 100%;
  border-radius: 10px;
  overflow: hidden;
  background: #000;
  margin-bottom: 10px;
}
.m-qr-reader video {
  width: 100%;
  display: block;
}
.m-scan-cancel {
  border: 1px solid #dcdfe6;
  background: #fff;
  color: #606266;
  border-radius: 10px;
  padding: 10px 0;
  width: 100%;
  font-size: 15px;
  cursor: pointer;
}
.m-divider {
  display: flex;
  align-items: center;
  gap: 10px;
  color: #c0c4cc;
  font-size: 12px;
  margin: 18px 0;
}
.m-divider::before,
.m-divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #e4e7ed;
}
.m-scan-input-row {
  display: flex;
  gap: 10px;
}
.m-scan-input {
  flex: 1;
  height: 50px;
  border: 1px solid #dcdfe6;
  border-radius: 12px;
  padding: 0 16px;
  font-size: 16px;
  background: #fff;
  outline: none;
  min-width: 0;
}
.m-scan-input:focus {
  border-color: #2b5aa7;
}
.m-scan-go-btn {
  width: 88px;
  border: none;
  background: #fff;
  color: #2b5aa7;
  border: 1px solid #2b5aa7;
  border-radius: 12px;
  font-size: 16px;
  font-weight: 600;
  cursor: pointer;
}
.m-scan-go-btn:active {
  background: #ecf2fc;
}
.m-scan-tip {
  margin: 12px 2px 0;
  font-size: 12px;
  color: #909399;
}
.m-recent {
  margin-top: 22px;
}
.m-recent-title {
  font-size: 13px;
  color: #909399;
  margin-bottom: 8px;
  padding-left: 4px;
}
.m-recent-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-radius: 10px;
  padding: 13px 16px;
  margin-bottom: 8px;
  font-size: 14px;
  color: #303133;
  cursor: pointer;
  border: 1px solid #f0f2f5;
}
.m-recent-no {
  font-family: ui-monospace, monospace;
  color: #2b5aa7;
}
.m-recent-arrow {
  color: #c0c4cc;
  font-size: 18px;
}
</style>
