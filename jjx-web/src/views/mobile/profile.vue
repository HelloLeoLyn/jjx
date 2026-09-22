<template>
  <div class="m-profile">
    <!-- 头部：头像 + 姓名/账号 -->
    <div class="m-pf-head">
      <div class="m-pf-avatar">{{ avatarText }}</div>
      <div class="m-pf-headtext">
        <div class="m-pf-name">{{ displayName }}</div>
        <div class="m-pf-sub">
          <span>账号 {{ userName || '-' }}</span>
          <span v-if="roleText"> · {{ roleText }}</span>
        </div>
      </div>
    </div>

    <!-- 信息行（只读） -->
    <div class="m-pf-card">
      <div v-for="row in infoRows" :key="row.label" class="m-pf-row">
        <span class="m-pf-label">{{ row.label }}</span>
        <span class="m-pf-value">{{ row.value }}</span>
      </div>
      <div v-if="loading" class="m-pf-loading">加载中…</div>
    </div>

    <!-- 操作 -->
    <div class="m-pf-actions">
      <el-button class="m-pf-btn" @click="openPwd">修改密码</el-button>
      <el-button class="m-pf-btn is-danger" type="danger" plain @click="doLogout">退出登录</el-button>
    </div>

    <!-- 修改密码（口径照抄 PC 个人页：旧密码必填 / 新密码 6-20 且禁 <>"|\ / 两次一致） -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="92%" append-to-body>
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="76px">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input
            v-model="pwdForm.oldPassword"
            type="password"
            show-password
            :maxlength="20"
            placeholder="请输入旧密码"
          />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="pwdForm.newPassword"
            type="password"
            show-password
            :maxlength="20"
            placeholder="6-20 位"
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="pwdForm.confirmPassword"
            type="password"
            show-password
            :maxlength="20"
            placeholder="请再次输入新密码"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitPwd">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { userApi } from '@/api/system/user'
import { useUserStore } from '@/store/modules/user'

/**
 * 手机端个人页（dev-20260922-010，基础版）：
 * 信息展示 + 修改密码 + 退出登录。口径与 PC 个人页一致（views/system/user/profile/index.vue），
 * 复用同一批接口：GET /system/user/current、PUT /system/user/profile/updatePwd。
 */
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const user = ref<any>({})

const userName = computed(() => user.value.userName || userStore.userName || '')
const nickName = computed(() => user.value.nickName || userStore.nickName || '')
const displayName = computed(() => nickName.value || userName.value || '我')
const avatarText = computed(() => displayName.value.slice(0, 1))
const roleText = computed(() => {
  const roles = (userStore.roles || []) as string[]
  return roles.length ? roles[0] : ''
})

const infoRows = computed(() => [
  { label: '用户名称', value: userName.value || '-' },
  { label: '用户昵称', value: nickName.value || '-' },
  { label: '手机号码', value: user.value.phone || '-' },
  { label: '邮箱', value: user.value.email || '-' },
  { label: '所属部门', value: user.value.deptName || '-' },
  { label: '所属角色', value: roleText.value || '-' },
])

async function load() {
  loading.value = true
  try {
    const res = await userApi.getCurrentInfo()
    if (res.code === 200 && res.data) {
      user.value = res.data
    }
  } catch (error: any) {
    // 失败不阻塞：下面各行会回退到 store 里的登录信息
    console.error('加载个人信息失败:', error)
    ElMessage.error(error?.message || '加载个人信息失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)

// ─────────── 修改密码 ───────────
const pwdVisible = ref(false)
const saving = ref(false)
const pwdFormRef = ref<FormInstance>()
const pwdForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })

const equalToPassword = (_rule: any, value: string, callback: (e?: Error) => void) => {
  if (value !== pwdForm.newPassword) {
    callback(new Error('两次输入的密码不一致'))
  } else {
    callback()
  }
}

const pwdRules: FormRules = {
  oldPassword: [{ required: true, message: '旧密码不能为空', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '新密码不能为空', trigger: 'blur' },
    { min: 6, max: 20, message: '长度在 6 到 20 个字符', trigger: 'blur' },
    { pattern: /^[^<>"'|\\]+$/, message: '不能包含非法字符：< > " \' \\ |', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '确认密码不能为空', trigger: 'blur' },
    { validator: equalToPassword, trigger: 'blur' },
  ],
}

function openPwd() {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdFormRef.value?.clearValidate()
  pwdVisible.value = true
}

async function submitPwd() {
  if (!pwdFormRef.value) return
  try {
    await pwdFormRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    const res = await userApi.updatePwd({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    if (res.code === 200) {
      ElMessage.success('密码修改成功，请重新登录')
      pwdVisible.value = false
      // 与 PC 口径一致：改密后强制重新登录
      setTimeout(() => {
        userStore.logout().finally(() => {
          router.replace('/m/login')
        })
      }, 800)
    } else {
      ElMessage.error(res.msg || '修改失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '修改失败')
  } finally {
    saving.value = false
  }
}

// ─────────── 退出登录 ───────────
async function doLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch {
    return
  }
  userStore.logout().finally(() => {
    router.replace('/m/login')
  })
}
</script>

<style scoped>
.m-profile {
  padding: 12px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.m-pf-head {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 10px;
  padding: 16px 14px;
}
.m-pf-avatar {
  width: 52px;
  height: 52px;
  border-radius: 50%;
  background: var(--doc-theme, #2b5aa7);
  color: #fff;
  font-size: 22px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.m-pf-headtext {
  min-width: 0;
}
.m-pf-name {
  font-size: 17px;
  font-weight: 600;
  color: #303133;
}
.m-pf-sub {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}
.m-pf-card {
  background: #fff;
  border-radius: 10px;
  padding: 4px 14px;
}
.m-pf-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 0;
  border-bottom: 1px solid #f2f3f5;
  font-size: 14px;
}
.m-pf-row:last-child {
  border-bottom: none;
}
.m-pf-label {
  color: #909399;
  flex-shrink: 0;
}
.m-pf-value {
  color: #303133;
  text-align: right;
  word-break: break-all;
}
.m-pf-loading {
  padding: 8px 0 12px;
  font-size: 12px;
  color: #c0c4cc;
  text-align: center;
}
.m-pf-actions {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.m-pf-btn {
  width: 100%;
  height: 42px;
  margin-left: 0;
}
</style>
