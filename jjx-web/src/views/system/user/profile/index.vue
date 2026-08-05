<template>
  <div class="profile-container">
    <!-- 加载骨架 -->
    <template v-if="loading">
      <el-card shadow="never">
        <el-skeleton :rows="3" animated />
      </el-card>
    </template>

    <template v-else>
      <!-- ══════════ 顶部用户横幅 ══════════ -->
      <el-card shadow="never" class="profile-banner">
        <div class="banner-content">
          <!-- 头像 -->
          <div class="avatar-wrap" @click="openAvatarDialog">
            <el-avatar :size="88" :src="avatarSrc" class="user-avatar" />
            <div class="avatar-mask">
              <el-icon :size="18"><Camera /></el-icon>
              <span>更换头像</span>
            </div>
          </div>

          <!-- 用户信息 -->
          <div class="user-main">
            <div class="user-name">{{ user.nickName || user.userName }}</div>
            <div class="user-account">账号：{{ user.userName }}</div>
            <div class="user-tags">
              <el-tag v-if="deptName" type="info" effect="plain" size="small">
                <el-icon style="margin-right: 4px; vertical-align: -2px"><OfficeBuilding /></el-icon>
                {{ deptName }}
              </el-tag>
              <el-tag
                v-for="role in roleNames"
                :key="role"
                size="small"
                effect="plain"
                style="margin-left: 6px"
              >
                {{ role }}
              </el-tag>
            </div>
          </div>

          <!-- 状态徽章 -->
          <div class="banner-status">
            <el-tag :type="user.status === 0 ? 'success' : 'danger'" effect="dark" round>
              {{ user.status === 0 ? '正常' : '停用' }}
            </el-tag>
          </div>
        </div>
      </el-card>

      <!-- ══════════ 主体区域 ══════════ -->
      <el-row :gutter="16" class="profile-body">
        <!-- 左：个人信息 -->
        <el-col :xs="24" :sm="24" :md="8">
          <el-card shadow="never" class="info-card">
            <template #header>
              <div class="card-header">
                <el-icon color="#409eff"><User /></el-icon>
                <span>个人信息</span>
              </div>
            </template>
            <el-descriptions :column="1" border>
              <el-descriptions-item label="用户名称">
                {{ user.userName }}
              </el-descriptions-item>
              <el-descriptions-item label="用户昵称">
                {{ user.nickName || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="手机号码">
                {{ user.phone || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="邮箱">
                {{ user.email || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="所属部门">
                {{ deptName || '-' }}
              </el-descriptions-item>
              <el-descriptions-item label="所属角色">
                <template v-if="roleNames.length > 0">
                  <el-tag
                    v-for="role in roleNames"
                    :key="role"
                    size="small"
                    style="margin: 2px 4px 2px 0"
                  >
                    {{ role }}
                  </el-tag>
                </template>
                <span v-else>-</span>
              </el-descriptions-item>
              <el-descriptions-item label="性别">
                {{ sexText }}
              </el-descriptions-item>
              <el-descriptions-item label="账号状态">
                <el-tag :type="user.status === 0 ? 'success' : 'danger'" size="small">
                  {{ user.status === 0 ? '正常' : '停用' }}
                </el-tag>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>

        <!-- 右：基本资料 / 修改密码 -->
        <el-col :xs="24" :sm="24" :md="16">
          <el-card shadow="never" class="form-card">
            <el-tabs v-model="activeTab">
              <!-- ── 基本资料 ── -->
              <el-tab-pane label="基本资料" name="info">
                <el-form
                  ref="infoFormRef"
                  :model="infoForm"
                  :rules="infoRules"
                  label-width="90px"
                  class="profile-form"
                >
                  <el-form-item label="用户昵称" prop="nickName">
                    <el-input
                      v-model="infoForm.nickName"
                      placeholder="请输入昵称"
                      :maxlength="30"
                      clearable
                    />
                  </el-form-item>
                  <el-form-item label="手机号码" prop="phone">
                    <el-input
                      v-model="infoForm.phone"
                      placeholder="请输入手机号码"
                      :maxlength="11"
                      clearable
                    />
                  </el-form-item>
                  <el-form-item label="邮箱" prop="email">
                    <el-input
                      v-model="infoForm.email"
                      placeholder="请输入邮箱"
                      :maxlength="50"
                      clearable
                    />
                  </el-form-item>
                  <el-form-item label="性别">
                    <el-radio-group v-model="infoForm.sex">
                      <el-radio value="0">男</el-radio>
                      <el-radio value="1">女</el-radio>
                      <el-radio value="2">未知</el-radio>
                    </el-radio-group>
                  </el-form-item>
                  <el-form-item label="备注">
                    <el-input
                      v-model="infoForm.remark"
                      type="textarea"
                      :rows="3"
                      placeholder="请输入备注"
                      :maxlength="500"
                      show-word-limit
                    />
                  </el-form-item>
                  <el-form-item>
                    <el-button type="primary" :loading="infoSaving" @click="saveInfo">
                      保存修改
                    </el-button>
                    <el-button @click="resetInfoForm">重置</el-button>
                  </el-form-item>
                </el-form>
              </el-tab-pane>

              <!-- ── 修改密码 ── -->
              <el-tab-pane label="修改密码" name="pwd">
                <el-form
                  ref="pwdFormRef"
                  :model="pwdForm"
                  :rules="pwdRules"
                  label-width="90px"
                  class="profile-form"
                >
                  <el-form-item label="旧密码" prop="oldPassword">
                    <el-input
                      v-model="pwdForm.oldPassword"
                      placeholder="请输入旧密码"
                      type="password"
                      show-password
                      :maxlength="20"
                    />
                  </el-form-item>
                  <el-form-item label="新密码" prop="newPassword">
                    <el-input
                      v-model="pwdForm.newPassword"
                      placeholder="请输入新密码"
                      type="password"
                      show-password
                      :maxlength="20"
                    />
                  </el-form-item>
                  <el-form-item label="确认密码" prop="confirmPassword">
                    <el-input
                      v-model="pwdForm.confirmPassword"
                      placeholder="请再次输入新密码"
                      type="password"
                      show-password
                      :maxlength="20"
                    />
                  </el-form-item>
                  <el-form-item>
                    <el-button type="primary" :loading="pwdSaving" @click="savePwd">
                      修改密码
                    </el-button>
                    <el-button @click="resetPwdFormData">重置</el-button>
                  </el-form-item>
                </el-form>
              </el-tab-pane>
            </el-tabs>
          </el-card>
        </el-col>
      </el-row>
    </template>

    <!-- ══════════ 头像裁剪弹窗 ══════════ -->
    <el-dialog
      title="修改头像"
      v-model="avatarDialogVisible"
      width="760px"
      append-to-body
      destroy-on-close
      @closed="closeAvatarDialog"
    >
      <el-row :gutter="16">
        <el-col :xs="24" :md="13" :style="{ height: '340px' }">
          <div class="cropper-box" v-loading="avatarUploading">
            <vue-cropper
              v-if="avatarDialogVisible"
              ref="cropperRef"
              :img="avatarOptions.img"
              :auto-crop="true"
              :auto-crop-width="200"
              :auto-crop-height="200"
              :fixed-box="true"
              :output-type="'png'"
              @real-time="avatarRealTime"
            />
          </div>
        </el-col>
        <el-col :xs="24" :md="11" :style="{ height: '340px' }">
          <div class="avatar-preview-box">
            <div class="preview-title">预览</div>
            <div class="preview-img-wrap">
              <img :src="avatarOptions.previews.url || avatarSrc" class="preview-img" />
            </div>
          </div>
        </el-col>
      </el-row>
      <template #footer>
        <div class="avatar-footer">
          <el-upload
            action="#"
            :show-file-list="false"
            :http-request="avatarSelect"
            accept="image/*"
          >
            <el-button size="small">选择图片</el-button>
          </el-upload>
          <div class="avatar-ops">
            <el-button size="small" circle @click="avatarChangeScale(1)">
              <el-icon><ZoomIn /></el-icon>
            </el-button>
            <el-button size="small" circle @click="avatarChangeScale(-1)">
              <el-icon><ZoomOut /></el-icon>
            </el-button>
            <el-button size="small" circle @click="avatarRotate(-90)">
              <el-icon><RefreshLeft /></el-icon>
            </el-button>
            <el-button size="small" circle @click="avatarRotate(90)">
              <el-icon><RefreshRight /></el-icon>
            </el-button>
          </div>
          <el-button type="primary" :loading="avatarUploading" @click="avatarSubmit">
            保存头像
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import {
  User,
  Camera,
  OfficeBuilding,
  ZoomIn,
  ZoomOut,
  RefreshLeft,
  RefreshRight,
} from '@element-plus/icons-vue'
import 'vue-cropper/dist/index.css'
import { VueCropper } from 'vue-cropper'
import { userApi } from '@/api/system/user'
import { deptApi } from '@/api/system/dept'
import { roleApi } from '@/api/system/role'
import { useUserStore } from '@/store/modules/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// ─────────── 基础数据 ───────────
const loading = ref(true)
const user = ref<any>({})
const deptMap = ref<Map<number, string>>(new Map())
const roleMap = ref<Map<number, string>>(new Map())

const defaultAvatar = 'https://cube.elemecdn.com/3/7c/3ea6beec64369c2642b92c6726f1epng.png'
const avatarSrc = computed(() => user.value.avatar || defaultAvatar)

const deptName = computed(() => {
  if (!user.value.deptId) return ''
  return deptMap.value.get(user.value.deptId) || ''
})

const roleNames = computed(() => {
  if (!Array.isArray(user.value.roleIds)) return []
  return user.value.roleIds
    .map((id: number) => roleMap.value.get(id))
    .filter((name: string | undefined): name is string => !!name)
})

const sexText = computed(() => {
  const map: Record<string, string> = { '0': '男', '1': '女', '2': '未知' }
  return map[String(user.value.sex)] || '未知'
})

// 部门树 → Map
const convertDeptToMap = (depts: any[]): Map<number, string> => {
  const map = new Map<number, string>()
  const traverse = (items: any[]) => {
    for (const item of items) {
      if (item.id && item.deptName) map.set(item.id, item.deptName)
      if (item.children?.length) traverse(item.children)
    }
  }
  traverse(depts)
  return map
}

// ─────────── 数据加载 ───────────
const loadData = async () => {
  loading.value = true
  try {
    const [userRes, deptRes, roleRes] = await Promise.all([
      userApi.getCurrentInfo(),
      deptApi.treeselect({}),
      roleApi.optionselect(),
    ])
    if (userRes.code === 200 && userRes.data) {
      user.value = userRes.data
      fillInfoForm()
    }
    if (deptRes.code === 200) {
      deptMap.value = convertDeptToMap(deptRes.data || [])
    }
    if (roleRes.code === 200) {
      roleMap.value = new Map(
        (roleRes.data || []).map((role: any) => [role.roleId, role.roleName])
      )
    }
  } catch (error) {
    console.error('加载个人信息失败:', error)
    ElMessage.error('加载个人信息失败')
  } finally {
    loading.value = false
  }
}

// ─────────── 基本资料 ───────────
const activeTab = ref(route.query.tab === 'pwd' ? 'pwd' : 'info')
const infoFormRef = ref<FormInstance>()
const infoSaving = ref(false)
const infoForm = reactive({
  nickName: '',
  phone: '',
  email: '',
  sex: '0',
  remark: '',
})

const validatePhone = (_rule: any, value: string, callback: (err?: Error) => void) => {
  if (!value) return callback()
  if (!/^1[3-9]\d{9}$/.test(value)) {
    callback(new Error('请输入正确的手机号码'))
  } else {
    callback()
  }
}

const infoRules: FormRules = {
  nickName: [
    { required: true, message: '昵称不能为空', trigger: 'blur' },
    { min: 2, max: 30, message: '昵称长度必须在 2-30 位之间', trigger: 'blur' },
  ],
  phone: [{ validator: validatePhone, trigger: 'blur' }],
  email: [
    { type: 'email', message: '请输入正确的邮箱地址', trigger: ['blur', 'change'] },
  ],
}

const fillInfoForm = () => {
  infoForm.nickName = user.value.nickName || ''
  infoForm.phone = user.value.phone || ''
  infoForm.email = user.value.email || ''
  infoForm.sex = String(user.value.sex ?? '0')
  infoForm.remark = user.value.remark || ''
}

const resetInfoForm = () => {
  fillInfoForm()
  infoFormRef.value?.clearValidate()
}

const saveInfo = async () => {
  if (!infoFormRef.value) return
  try {
    await infoFormRef.value.validate()
  } catch {
    return
  }
  infoSaving.value = true
  try {
    const payload: any = {
      userId: user.value.userId,
      nickName: infoForm.nickName,
      sex: infoForm.sex,
      remark: infoForm.remark,
    }
    // 空值不提交（后端 @Pattern 对空字符串会校验失败）
    if (infoForm.phone) payload.phone = infoForm.phone
    if (infoForm.email) payload.email = infoForm.email

    const res = await userApi.profile(payload)
    if (res.code === 200) {
      ElMessage.success('保存成功')
      // 同步更新本地与 store（顶栏昵称/头像实时生效）
      user.value.nickName = infoForm.nickName
      user.value.phone = infoForm.phone
      user.value.email = infoForm.email
      user.value.sex = infoForm.sex
      user.value.remark = infoForm.remark
      userStore.setUserInfo({
        ...(userStore.userInfo || { userId: 0, userName: '', roles: [], permissions: [] }),
        nickName: infoForm.nickName,
      })
    } else {
      ElMessage.error(res.msg || '保存失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '保存失败')
  } finally {
    infoSaving.value = false
  }
}

// ─────────── 修改密码 ───────────
const pwdFormRef = ref<FormInstance>()
const pwdSaving = ref(false)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const equalToPassword = (_rule: any, value: string, callback: (err?: Error) => void) => {
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

const resetPwdFormData = () => {
  pwdForm.oldPassword = ''
  pwdForm.newPassword = ''
  pwdForm.confirmPassword = ''
  pwdFormRef.value?.clearValidate()
}

const savePwd = async () => {
  if (!pwdFormRef.value) return
  try {
    await pwdFormRef.value.validate()
  } catch {
    return
  }
  pwdSaving.value = true
  try {
    const res = await userApi.updatePwd({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    })
    if (res.code === 200) {
      ElMessage.success('密码修改成功，请重新登录')
      resetPwdFormData()
      // 修改密码后强制重新登录
      setTimeout(() => {
        userStore.logout().finally(() => {
          router.push('/login')
        })
      }, 800)
    } else {
      ElMessage.error(res.msg || '修改失败')
    }
  } catch (error: any) {
    ElMessage.error(error?.message || '修改失败')
  } finally {
    pwdSaving.value = false
  }
}

// ─────────── 头像修改 ───────────
const avatarDialogVisible = ref(false)
const avatarUploading = ref(false)
const cropperRef = ref<any>(null)
const avatarOptions = reactive<{
  img: string
  previews: { url?: string; img?: Record<string, string> }
}>({
  img: '',
  previews: {},
})

const openAvatarDialog = () => {
  avatarOptions.img = avatarSrc.value
  avatarOptions.previews = {}
  avatarDialogVisible.value = true
}

const closeAvatarDialog = () => {
  avatarOptions.previews = {}
}

// 选择图片（覆盖默认上传行为）
const avatarSelect: any = (options: any) => {
  const file = options.file
  if (!file.type.startsWith('image/')) {
    ElMessage.error('请选择图片文件')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('图片不能超过 5MB')
    return
  }
  const reader = new FileReader()
  reader.onload = () => {
    avatarOptions.img = reader.result as string
    avatarOptions.previews = {}
  }
  reader.readAsDataURL(file)
}

const avatarRealTime = (data: any) => {
  avatarOptions.previews = data
}

const avatarChangeScale = (num: number) => {
  cropperRef.value?.changeScale(num)
}

const avatarRotate = (deg: number) => {
  cropperRef.value?.rotateRight(deg)
}

// 裁剪结果压缩为 256x256 JPEG，控制 base64 体积
const compressAvatar = (dataUrl: string): Promise<string> => {
  return new Promise((resolve, reject) => {
    const img = new Image()
    img.onload = () => {
      const canvas = document.createElement('canvas')
      const size = 256
      canvas.width = size
      canvas.height = size
      const ctx = canvas.getContext('2d')
      if (!ctx) return reject(new Error('canvas 不可用'))
      ctx.drawImage(img, 0, 0, size, size)
      resolve(canvas.toDataURL('image/jpeg', 0.85))
    }
    img.onerror = () => reject(new Error('图片解析失败'))
    img.src = dataUrl
  })
}

const avatarSubmit = async () => {
  if (!cropperRef.value) return
  avatarUploading.value = true
  try {
    const blob = await new Promise<Blob>((resolve, reject) => {
      cropperRef.value.getCropBlob((data: Blob) => resolve(data))
    })
    const reader = new FileReader()
    const dataUrl = await new Promise<string>((resolve, reject) => {
      reader.onload = () => resolve(reader.result as string)
      reader.onerror = () => reject(new Error('图片读取失败'))
      reader.readAsDataURL(blob)
    })
    const compressed = await compressAvatar(dataUrl)

    const res = await userApi.avatar(compressed)
    if (res.code === 200) {
      ElMessage.success('头像修改成功')
      user.value.avatar = compressed
      userStore.setUserInfo({
        ...(userStore.userInfo || { userId: 0, userName: '', roles: [], permissions: [] }),
        avatar: compressed,
      })
      avatarDialogVisible.value = false
    } else {
      ElMessage.error(res.msg || '头像修改失败')
    }
  } catch (error: any) {
    console.error('头像上传失败:', error)
    ElMessage.error(error?.message || '头像修改失败')
  } finally {
    avatarUploading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.profile-container {
  padding: 16px;

  // ═══ 顶部横幅 ═══
  .profile-banner {
    border: none;
    border-radius: 10px;
    overflow: hidden;
    background: linear-gradient(135deg, #2b5fd9 0%, #409eff 55%, #5eb1ff 100%);
    margin-bottom: 16px;

    :deep(.el-card__body) {
      padding: 28px 32px;
    }

    .banner-content {
      display: flex;
      align-items: center;
      gap: 24px;
      color: #fff;

      .avatar-wrap {
        position: relative;
        flex-shrink: 0;
        cursor: pointer;
        border-radius: 50%;

        .user-avatar {
          border: 3px solid rgba(255, 255, 255, 0.85);
          box-shadow: 0 4px 16px rgba(0, 0, 0, 0.25);
          display: block;
        }

        .avatar-mask {
          position: absolute;
          inset: 3px;
          border-radius: 50%;
          background: rgba(0, 0, 0, 0.5);
          display: flex;
          flex-direction: column;
          align-items: center;
          justify-content: center;
          gap: 2px;
          color: #fff;
          font-size: 12px;
          opacity: 0;
          transition: opacity 0.25s;
        }

        &:hover .avatar-mask {
          opacity: 1;
        }
      }

      .user-main {
        flex: 1;
        min-width: 0;

        .user-name {
          font-size: 22px;
          font-weight: 600;
          line-height: 1.3;
          text-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
        }

        .user-account {
          margin-top: 4px;
          font-size: 13px;
          opacity: 0.85;
        }

        .user-tags {
          margin-top: 10px;
          display: flex;
          flex-wrap: wrap;

          :deep(.el-tag) {
            border-color: rgba(255, 255, 255, 0.5);
            color: #fff;
            background: rgba(255, 255, 255, 0.15);
          }
        }
      }

      .banner-status {
        flex-shrink: 0;
        align-self: flex-start;
      }
    }
  }

  // ═══ 主体 ═══
  .profile-body {
    .info-card {
      margin-bottom: 16px;

      .card-header {
        display: flex;
        align-items: center;
        gap: 6px;
        font-weight: 600;
      }

      :deep(.el-descriptions__label) {
        width: 96px;
        background-color: #f5f7fa;
      }
    }

    .form-card {
      margin-bottom: 16px;

      .profile-form {
        max-width: 480px;
        padding-top: 12px;
      }
    }
  }
}

// ═══ 头像裁剪 ═══
.cropper-box {
  height: 340px;
  background: #f5f7fa;
  border-radius: 6px;
  overflow: hidden;
}

.avatar-preview-box {
  height: 340px;
  background: #f5f7fa;
  border-radius: 6px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;

  .preview-title {
    font-size: 13px;
    color: #909399;
  }

  .preview-img-wrap {
    width: 160px;
    height: 160px;
    border-radius: 50%;
    overflow: hidden;
    border: 3px solid #fff;
    box-shadow: 0 2px 12px rgba(0, 0, 0, 0.12);
    display: flex;
    align-items: center;
    justify-content: center;
    background: #fff;

    .preview-img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
  }
}

.avatar-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;

  .avatar-ops {
    display: flex;
    gap: 8px;
  }
}
</style>
