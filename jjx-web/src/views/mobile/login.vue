<template>
  <div class="m-login">
    <div class="m-login-box">
      <h2 class="m-login-title">JJX 生产监工</h2>
      <p class="m-login-sub">移动端扫码操作</p>
      <el-input
        v-model="username"
        class="m-input"
        placeholder="账号"
        size="large"
        clearable
        autocomplete="username"
      />
      <el-input
        v-model="password"
        class="m-input"
        type="password"
        placeholder="密码"
        size="large"
        show-password
        autocomplete="current-password"
        @keyup.enter="handleLogin"
      />
      <el-button
        type="primary"
        size="large"
        class="m-login-btn"
        :loading="loading"
        @click="handleLogin"
      >
        登 录
      </el-button>
      <div class="m-login-tip">车间工人使用个人账号登录</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const username = ref('')
const password = ref('')
const loading = ref(false)

async function handleLogin() {
  if (!username.value.trim() || !password.value) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    await userStore.login({
      username: username.value.trim(),
      password: password.value,
    })
    ElMessage.success('登录成功')
    // 登录成功：优先回跳原目标页，否则进扫码入口
    const redirect = String(route.query.redirect || '')
    router.replace(redirect && redirect.startsWith('/m/') ? redirect : '/m/home')
  } catch (e: any) {
    ElMessage.error(e?.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.m-login {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(160deg, #1f2d3d 0%, #2c4a6b 100%);
  padding: 24px;
}
.m-login-box {
  width: 100%;
  max-width: 380px;
  background: #fff;
  border-radius: 16px;
  padding: 40px 28px 32px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.25);
  text-align: center;
}
.m-login-title {
  margin: 0;
  font-size: 24px;
  color: #1f2d3d;
}
.m-login-sub {
  margin: 8px 0 28px;
  font-size: 14px;
  color: #909399;
}
.m-input {
  margin-bottom: 16px;
}
.m-login-btn {
  width: 100%;
  height: 48px;
  font-size: 17px;
  margin-top: 8px;
}
.m-login-tip {
  margin-top: 20px;
  font-size: 12px;
  color: #c0c4cc;
}
</style>
