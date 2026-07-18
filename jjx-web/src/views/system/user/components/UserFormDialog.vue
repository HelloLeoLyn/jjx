<template>
  <el-dialog
    :title="title"
    v-model="dialogVisible"
    :width="width"
    :close-on-click-modal="false"
    append-to-body
    @close="handleClose"
  >
    <el-form ref="formRef" :model="formData" :rules="rules" label-width="80px">
      <el-row :gutter="20">
        <!-- 用户名称 -->
        <el-col :span="12">
          <el-form-item label="用户名称" prop="userName">
            <el-input
              v-model="formData.userName"
              placeholder="请输入用户名称"
              :maxlength="30"
              clearable
            />
          </el-form-item>
        </el-col>

        <!-- 用户昵称 -->
        <el-col :span="12">
          <el-form-item label="用户昵称" prop="nickName">
            <el-input
              v-model="formData.nickName"
              placeholder="请输入用户昵称"
              :maxlength="30"
              clearable
            />
          </el-form-item>
        </el-col>

        <!-- 手机号码 -->
        <el-col :span="12">
          <el-form-item label="手机号码" prop="phone">
            <el-input
              v-model="formData.phone"
              placeholder="请输入手机号码"
              :maxlength="11"
              clearable
            />
          </el-form-item>
        </el-col>

        <!-- 邮箱 -->
        <el-col :span="12">
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="formData.email" placeholder="请输入邮箱" :maxlength="50" clearable />
          </el-form-item>
        </el-col>

        <!-- 用户密码 -->
        <el-col :span="12" v-if="!formData.userId">
          <el-form-item label="用户密码" prop="password">
            <el-input
              v-model="formData.password"
              placeholder="请输入用户密码"
              type="password"
              :maxlength="20"
              show-password
            />
          </el-form-item>
        </el-col>

        <!-- 确认密码 -->
        <el-col :span="12" v-if="!formData.userId">
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="formData.confirmPassword"
              placeholder="请确认密码"
              type="password"
              :maxlength="20"
              show-password
            />
          </el-form-item>
        </el-col>

        <!-- 用户性别 -->
        <el-col :span="12">
          <el-form-item label="用户性别" prop="sex">
            <el-select
              v-model="formData.sex"
              placeholder="请选择用户性别"
              clearable
              style="width: 100%"
            >
              <el-option label="男" value="0" />
              <el-option label="女" value="1" />
              <el-option label="未知" value="2" />
            </el-select>
          </el-form-item>
        </el-col>

        <!-- 状态 -->
        <el-col :span="12">
          <el-form-item label="状态" prop="status">
            <el-radio-group v-model="formData.status">
              <el-radio value="0">正常</el-radio>
              <el-radio value="1">停用</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-col>

        <!-- 角色 -->
        <el-col :span="24">
          <el-form-item label="角色" prop="roleIds">
            <el-select
              v-model="formData.roleIds"
              placeholder="请选择角色"
              multiple
              clearable
              style="width: 100%"
            >
              <el-option
                v-for="item in roleOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </el-col>

        <!-- 部门 -->
        <el-col :span="24">
          <el-form-item label="部门" prop="deptId">
            <el-tree-select
              v-model="formData.deptId"
              :data="deptOptions"
              :props="treeProps"
              placeholder="请选择部门"
              clearable
              filterable
              style="width: 100%"
            />
          </el-form-item>
        </el-col>

        <!-- 备注 -->
        <el-col :span="24">
          <el-form-item label="备注" prop="remark">
            <el-input
              v-model="formData.remark"
              type="textarea"
              :rows="3"
              placeholder="请输入备注"
              :maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </el-col>
      </el-row>
    </el-form>

    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import type { FormInstance, FormRules } from 'element-plus'
import type { SysUserDTO } from '@/types/system'

interface Props {
  visible: boolean
  title?: string
  width?: string
  formData: SysUserDTO
  rules?: FormRules
  submitLoading?: boolean
  roleOptions?: { value: number; label: string }[]
  deptOptions?: any[]
}

interface Emits {
  (e: 'update:visible', value: boolean): void
  (e: 'submit'): void
  (e: 'cancel'): void
}

const props = withDefaults(defineProps<Props>(), {
  visible: false,
  title: '用户表单',
  width: '600px',
  submitLoading: false,
  roleOptions: () => [],
  deptOptions: () => [],
})

const emit = defineEmits<Emits>()

const formRef = ref<FormInstance>()

const dialogVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

// 树形选择器配置
const treeProps = {
  value: 'id',
  label: 'deptName',
  children: 'children',
}

const handleSubmit = async () => {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
    emit('submit')
  } catch {
    // 验证失败
  }
}

const handleCancel = () => {
  emit('cancel')
  dialogVisible.value = false
}

const handleClose = () => {
  formRef.value?.resetFields()
}
</script>
