<!-- src/views/system/user/index.vue -->
<template>
  <div class="user-page">
    <!-- 搜索表单 -->
    <UserSearchForm
      v-model="queryParams"
      :dept-options="deptOptions"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- 工具栏 -->
    <Toolbar
      :buttons="uiConfig.toolbarOptions"
      :selected-count="selectedRows.length"
      :show-batch-bar="true"
      @click="handleToolbarClick"
      @refresh="handleRefresh"
    >
      <template #batch-actions>
        <el-button
          :disabled="selectedRows.length === 0"
          type="danger"
          size="small"
          @click="handleBatchDelete"
          v-hasPermi="['system:user:remove']"
        >
          批量删除
        </el-button>
      </template>
    </Toolbar>
    <!-- 数据表格 -->
    <DataTable
      :data="userList"
      :loading="loading"
      :total="total"
      :columns="uiConfig.tableOptions"
      :show-action="false"
      :action-width="400"
      @selection-change="handleSelectionChange"
      @page-change="handlePageChange"
      @size-change="handleSizeChange"
    >
      <!-- 状态列自定义渲染 -->
      <template #deptId="{ row }">
        <span>{{ deptMap.get(row.deptId) || '未知部门' }}</span>
      </template>
      <!-- 状态列自定义渲染 -->
      <template #status="{ row }">
        <el-tag :type="row.status === 0 ? 'success' : 'danger'" size="small">
          {{ row.status === 0 ? '正常' : '停用' }}
        </el-tag>
      </template>
      <!-- 创建时间列自定义渲染 -->
      <template #createTime="{ row }">
        <span>{{ parseTime(row.createTime) }}</span>
      </template>

      <!-- 操作列 -->
      <template #action="{ row }">
        <el-button link type="primary" @click="handleEdit(row)" v-hasPermi="['system:user:edit']">
          修改
        </el-button>
        <el-button
          link
          type="danger"
          @click="handleDelete(row)"
          v-hasPermi="['system:user:remove']"
        >
          删除
        </el-button>
        <el-button
          link
          type="warning"
          @click="handleResetPwd(row)"
          v-hasPermi="['system:user:resetPwd']"
        >
          重置密码
        </el-button>
        <el-button
          link
          type="success"
          @click="handleAssignRole(row)"
          v-hasPermi="['system:user:edit']"
        >
          分配角色
        </el-button>
      </template>
    </DataTable>

    <!-- 用户表单对话框 -->
    <UserFormDialog
      v-model:visible="dialogVisible"
      :title="dialogTitle"
      :form-data="formData"
      :rules="formRules"
      :submit-loading="submitLoading"
      :role-options="roleSelectOptions"
      :dept-options="deptOptions"
      width="600px"
      @submit="handleSubmit"
      @cancel="handleCancel"
    />

    <!-- 重置密码对话框 -->
    <el-dialog title="重置密码" v-model="resetPwdVisible" width="400px" append-to-body>
      <el-form
        ref="resetPwdFormRef"
        :model="resetPwdForm"
        :rules="resetPwdRules"
        label-width="80px"
      >
        <el-form-item label="新密码" prop="newPassword">
          <el-input
            v-model="resetPwdForm.newPassword"
            placeholder="请输入新密码"
            type="password"
            maxlength="20"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="resetPwdForm.confirmPassword"
            placeholder="请确认新密码"
            type="password"
            maxlength="20"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetPwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetPwdLoading" @click="submitResetPwd">
          确定
        </el-button>
      </template>
    </el-dialog>

    <!-- 分配角色对话框 -->
    <AssignRoleDialog
      v-if="currentUser.userId !== undefined"
      v-model:visible="roleDialogVisible"
      :user-id="currentUser.userId"
      :user-name="currentUser.userName"
      @success="handleAssignSuccess"
    ></AssignRoleDialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'User',
})

import { ref, reactive, onMounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Toolbar, DataTable } from '@/components/common-ui/index'
import { userApi } from '@/api/system/user'
import { roleApi } from '@/api/system/role'
import { deptApi } from '@/api/system/dept'
import type { SysUser, SysRole, SysDept, SysUserDTO, SecurityUser } from '@/types/system'
import * as uiConfig from './index'
import { parseTime } from '@/utils/format'
import { assignExisting } from '@/utils/object'
import { formRules, resetPwdRules, formDataPassword, resetPwdNewPassword } from './user.rules'
import AssignRoleDialog from './components/AssignRoleDialog.vue'
import UserSearchForm from './components/UserSearchForm.vue'
import UserFormDialog from './components/UserFormDialog.vue'

// ==================== 响应式数据 ====================
const loading = ref(false)
const userList = ref<SysUser[]>([])
const total = ref(0)
const selectedRows = ref<SysUser[]>([])
const roleOptions = ref<SysRole[]>([])
const deptOptions = ref<SysDept[]>([]) // 部门选项，结构根据接口返回调整
const deptMap = ref<Map<number, string>>(new Map()) // 部门ID到名称的映射
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  deptId: undefined,
  userName: '',
  phone: '',
  status: '',
})

// 角色选择选项（用于UserFormDialog）
const roleSelectOptions = ref<{ value: number; label: string }[]>([])

// 表单对话框
const dialogVisible = ref(false)
const dialogTitle = ref('')
const formData = ref<SysUserDTO>({
  userId: undefined,
  userName: '',
  nickName: '',
  password: '',
  confirmPassword: '',
  email: '',
  phone: '',
  sex: '0',
  status: '0',
  roleIds: [],
  deptId: undefined,
  remark: '',
})
const submitLoading = ref(false)

// 重置密码
const resetPwdVisible = ref(false)
const resetPwdLoading = ref(false)
const resetPwdFormRef = ref<FormInstance>()
const resetPwdForm = reactive<SecurityUser>({
  userId: undefined as number | undefined,
  newPassword: '',
  confirmPassword: '',
})

// 同步密码值到验证规则（用于跨字段校验）
watch(
  () => formData.value.password,
  (val) => {
    formDataPassword.value = val || ''
  }
)
watch(
  () => resetPwdForm.newPassword,
  (val) => {
    resetPwdNewPassword.value = val || ''
  }
)

// 分配角色
const roleDialogVisible = ref(false)
const currentUser = ref<SysUser>({
  userId: undefined,
  userName: '',
  nickName: '',
})

// 更新角色选项
const updateRoleOptions = () => {
  roleSelectOptions.value = roleOptions.value.map((role) => ({
    value: role.roleId!,
    label: role.roleName,
  }))
}

// 将部门数据转换为 Map<id, deptName>
const convertDeptToMap = (depts: any[]): Map<number, string> => {
  const map = new Map<number, string>()

  const traverse = (items: any[]) => {
    for (const item of items) {
      if (item.id && item.deptName) {
        map.set(item.id, item.deptName)
      }
      if (item.children && item.children.length > 0) {
        traverse(item.children)
      }
    }
  }

  traverse(depts)
  return map
}
// ==================== API 请求 ====================
const getList = async () => {
  loading.value = true
  try {
    const res = await userApi.list({
      ...queryParams,
    })
    userList.value = res.data?.records || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
}

const getRoleOptions = async () => {
  try {
    const res = await roleApi.optionselect()
    roleOptions.value = res.data || []
    updateRoleOptions()
  } catch (error) {
    console.error('获取角色选项失败:', error)
  }
}

const getDeptOptions = async () => {
  try {
    const res = await deptApi.treeselect({})
    deptOptions.value = res.data || []
    deptMap.value = convertDeptToMap(res.data || [])
  } catch (error) {
    console.error('获取部门选项失败:', error)
  }
}

// ==================== 事件处理 ====================
const handleSearch = () => {
  queryParams.pageNum = 1
  getList()
}

const handleReset = () => {
  queryParams.userName = ''
  queryParams.phone = ''
  queryParams.status = ''
  queryParams.deptId = undefined
  getList()
}

const handleRefresh = () => {
  getList()
}

const handleSelectionChange = (selection: SysUser[]) => {
  selectedRows.value = selection
}

const handlePageChange = (page: number) => {
  queryParams.pageNum = page
  getList()
}

const handleSizeChange = (size: number) => {
  queryParams.pageSize = size
  queryParams.pageNum = 1
  getList()
}

const handleToolbarClick = (key: string) => {
  if (key === 'add') handleAdd()
  if (key === 'export') handleExport()
}

const handleAdd = () => {
  dialogTitle.value = '添加用户'
  formData.value = {
    userId: undefined,
    userName: '',
    nickName: '',
    password: '',
    confirmPassword: '',
    email: '',
    phone: '',
    sex: '0',
    status: '0',
    roleIds: [],
    remark: '',
  }
  getRoleOptions()

  dialogVisible.value = true
}
const copyFormData = (data: SysUser) => {
  assignExisting(formData.value, data)
  formData.value.roleIds = data.roles ? data.roles.map((role) => role.roleId!) : []
  formData.value.password = ''
}

const handleEdit = async (row: SysUser) => {
  try {
    const res = await userApi.getInfo(row.userId!)
    // 将接口返回的数据复制到 formData 中
    if (res.data) {
      copyFormData(res.data)
    }
    dialogTitle.value = '修改用户'
    getRoleOptions()
    dialogVisible.value = true
  } catch (error) {
    console.error('获取用户信息失败:', error)
  }
}

const handleDelete = async (row: SysUser) => {
  await ElMessageBox.confirm(`是否确认删除用户"${row.userName}"？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await userApi.remove([row.userId!])
  ElMessage.success('删除成功')
  getList()
}

const handleBatchDelete = async () => {
  const ids = selectedRows.value.map((item) => item.userId!)
  const names = selectedRows.value.map((item) => item.userName).join(',')
  await ElMessageBox.confirm(`是否确认删除用户"${names}"？`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
  await userApi.remove(ids)
  ElMessage.success('删除成功')
  getList()
}

const handleExport = () => {
  ElMessage.info('导出功能待实现')
}

const handleSubmit = async () => {
  submitLoading.value = true
  try {
    if (formData.value.userId) {
      formData.value.password = undefined
      await userApi.edit(formData.value)
      ElMessage.success('修改成功')
    } else {
      await userApi.add(formData.value)
      ElMessage.success('新增成功')
    }
    dialogVisible.value = false
    getList()
  } finally {
    submitLoading.value = false
  }
}

const handleCancel = () => {
  dialogVisible.value = false
}

const handleResetPwd = (row: SysUser) => {
  resetPwdForm.userId = row.userId
  resetPwdForm.newPassword = ''
  resetPwdForm.confirmPassword = ''
  resetPwdVisible.value = true
}

const submitResetPwd = async () => {
  if (!resetPwdFormRef.value) return
  await resetPwdFormRef.value.validate(async (valid) => {
    if (valid && resetPwdForm.userId) {
      resetPwdLoading.value = true
      try {
        await userApi.resetPwd({
          userId: resetPwdForm.userId,
          password: resetPwdForm.newPassword,
        })
        ElMessage.success('重置密码成功')
        resetPwdVisible.value = false
      } finally {
        resetPwdLoading.value = false
      }
    }
  })
}

const handleAssignRole = (row: SysUser) => {
  // 先设置 userId 和 userName
  currentUser.value = { ...row }
  // 使用 nextTick 确保数据更新后再打开弹窗
  nextTick(() => {
    roleDialogVisible.value = true
  })
}

const handleAssignSuccess = () => {
  ElMessage.success('角色分配成功')
  getList()
}

onMounted(() => {
  getList()
  getDeptOptions()
})
</script>

<style scoped>
.user-page {
  padding: 20px;
}
</style>
