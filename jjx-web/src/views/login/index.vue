<template>
  <div class="login-container">
    <div class="login-box">
      <!-- 左侧logo区域 -->
      <div class="login-left">
        <div class="logo-container">
          <div class="logo">
            <!-- Logo image commented out since file doesn't exist -->
            <!-- <img src="@/assets/logo.png" alt="Logo" /> -->
            <div class="logo-text">JJX ERP</div>
          </div>
          <div class="slogan">智能制造管理系统</div>
        </div>
      </div>

      <!-- 右侧登录表单区域 -->
      <div class="login-right">
        <div class="login-form">
          <div class="form-header">
            <h2>用户登录</h2>
            <p>欢迎使用JJX ERP系统</p>
          </div>

          <el-form
            ref="loginFormRef"
            :model="loginForm"
            :rules="loginRules"
            class="login-form-content"
            @keyup.enter="handleLogin"
          >
            <el-form-item prop="username">
              <el-input
                v-model="loginForm.username"
                placeholder="请输入用户名"
                size="large"
                :prefix-icon="User"
              />
            </el-form-item>

            <el-form-item prop="password">
              <el-input
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                size="large"
                :prefix-icon="Lock"
                show-password
              />
            </el-form-item>

            <el-form-item prop="captcha" v-if="captchaEnabled">
              <div class="captcha-container">
                <el-input
                  v-model="loginForm.captcha"
                  placeholder="请输入验证码"
                  size="large"
                  :prefix-icon="Picture"
                  @keyup.enter="handleLogin"
                />
                <div class="captcha-image" @click="refreshCaptcha">
                  <img :src="captchaImage" alt="验证码" v-if="captchaImage" />
                  <div v-else class="captcha-loading">加载中...</div>
                </div>
              </div>
            </el-form-item>

            <el-form-item>
              <el-checkbox v-model="loginForm.rememberMe">记住密码</el-checkbox>
            </el-form-item>

            <el-form-item>
              <el-button
                type="primary"
                size="large"
                :loading="loading"
                @click="handleLogin"
                class="login-button"
              >
                {{ loading ? '登录中...' : '登录' }}
              </el-button>
            </el-form-item>
          </el-form>

          <div class="form-footer">
            <div class="links">
              <a href="javascript:void(0);" @click="handleForgetPassword">忘记密码?</a>
              <span class="divider">|</span>
              <a href="javascript:void(0);" @click="handleRegister">注册账号</a>
            </div>
            <div class="copyright">© 2024 JJX ERP 版权所有</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { User, Lock, Picture } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/modules/user'
import { captchaManager } from '@/utils/captcha'

// 路由
const router = useRouter()

// 用户store
const userStore = useUserStore()

// 登录表单引用
const loginFormRef = ref<FormInstance>()

// 加载状态
const loading = ref(false)

// 验证码是否启用
const captchaEnabled = ref(true)

// 验证码图片
const captchaImage = ref('')

// 登录表单数据
const loginForm = reactive({
  username: '',
  password: '',
  captcha: '',
  rememberMe: false,
})

// 登录表单验证规则
const loginRules = reactive<FormRules>({
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度在2到20个字符', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 5, max: 20, message: '密码长度在5到20个字符', trigger: 'blur' },
  ],
  captcha: [
    { required: true, message: '请输入验证码', trigger: 'blur' },
    { min: 4, max: 4, message: '验证码长度为4个字符', trigger: 'blur' },
  ],
})

// 获取验证码
const getCaptcha = async () => {
  if (!captchaEnabled.value) return

  try {
    // 使用前端生成的验证码
    const result = captchaManager.generate({
      width: 120,
      height: 40,
      length: 4,
      type: 'mixed',
      fontSize: 24,
      backgroundColor: '#f5f7fa',
    })
    captchaImage.value = result.image
  } catch (error) {
    console.error('获取验证码失败:', error)
    // 失败时使用备用验证码
    captchaImage.value =
      'data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTIwIiBoZWlnaHQ9IjQwIiB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciPjxyZWN0IHdpZHRoPSIxMjAiIGhlaWdodD0iNDAiIGZpbGw9IiNmNWY1ZjUiLz48dGV4dCB4PSIxMCIgeT0iMjUiIGZvbnQtZmFtaWx5PSJBcmlhbCIgZm9udC1zaXplPSIxNiIgZmlsbD0iIzMzMyI+MTIzNDwvdGV4dD48L3N2Zz4='
  }
}

// 刷新验证码
const refreshCaptcha = () => {
  getCaptcha()
}

// 处理登录
const handleLogin = async () => {
  if (!loginFormRef.value) return

  // 防止重复提交
  if (loading.value) {
    return
  }

  await loginFormRef.value.validate(async (valid) => {
    if (valid) {
      // 验证验证码
      if (captchaEnabled.value) {
        const isValid = captchaManager.validate(loginForm.captcha, false)
        if (!isValid) {
          ElMessage.error('验证码错误，请重新输入')
          refreshCaptcha()
          loginForm.captcha = ''
          return
        }
      }

      loading.value = true
      try {
        await userStore.login(loginForm)
        ElMessage.success('登录成功')

        // 优雅的跳转方案：添加短暂延迟确保路由已更新
        await new Promise((resolve) => setTimeout(resolve, 100))

        // 跳转到仪表板首页
        router.push('/dashboard/index')
      } catch (error: any) {
        ElMessage.error(error.message || '登录失败')
        // 登录失败刷新验证码
        refreshCaptcha()
      } finally {
        loading.value = false
      }
    }
  })
}

// 处理忘记密码
const handleForgetPassword = () => {
  ElMessage.info('忘记密码功能待实现')
}

// 处理注册
const handleRegister = () => {
  ElMessage.info('注册功能待实现')
}

// 页面加载时
onMounted(() => {
  // 获取验证码
  getCaptcha()

  // 检查是否有记住的密码
  const rememberMe = localStorage.getItem('rememberMe')
  if (rememberMe === 'true') {
    const savedUsername = localStorage.getItem('savedUsername')
    const savedPassword = localStorage.getItem('savedPassword')

    if (savedUsername) {
      loginForm.username = savedUsername
    }
    if (savedPassword) {
      loginForm.password = savedPassword
    }
    loginForm.rememberMe = true
  }
})
</script>

<style scoped>
.login-container {
  width: 100%;
  height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: hidden;
}

.login-box {
  width: 900px;
  height: 500px;
  background: white;
  border-radius: 10px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  display: flex;
  overflow: hidden;
}

.login-left {
  flex: 1;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 40px;
}

.logo-container {
  text-align: center;
}

.logo {
  margin-bottom: 30px;
}

.logo img {
  width: 80px;
  height: 80px;
  margin-bottom: 20px;
}

.logo-text {
  font-size: 36px;
  font-weight: bold;
  letter-spacing: 2px;
}

.slogan {
  font-size: 18px;
  opacity: 0.9;
  margin-top: 10px;
}

.login-right {
  flex: 1;
  padding: 60px 50px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.login-form {
  width: 100%;
}

.form-header {
  text-align: center;
  margin-bottom: 40px;
}

.form-header h2 {
  font-size: 28px;
  color: #333;
  margin-bottom: 10px;
}

.form-header p {
  color: #666;
  font-size: 14px;
}

.login-form-content {
  width: 100%;
}

.captcha-container {
  display: flex;
  gap: 10px;
}

.captcha-image {
  width: 120px;
  height: 40px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  display: flex;
  justify-content: center;
  align-items: center;
  overflow: hidden;
  background-color: #f5f7fa;
}

.captcha-image img {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.captcha-loading {
  color: #999;
  font-size: 12px;
}

.login-button {
  width: 100%;
  height: 45px;
  font-size: 16px;
}

.form-footer {
  margin-top: 30px;
  text-align: center;
}

.links {
  margin-bottom: 20px;
}

.links a {
  color: #666;
  text-decoration: none;
  font-size: 14px;
  transition: color 0.3s;
}

.links a:hover {
  color: #409eff;
}

.divider {
  margin: 0 10px;
  color: #ccc;
}

.copyright {
  color: #999;
  font-size: 12px;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .login-box {
    width: 90%;
    height: auto;
    flex-direction: column;
  }

  .login-left {
    padding: 30px;
  }

  .login-right {
    padding: 40px 30px;
  }
}
</style>
