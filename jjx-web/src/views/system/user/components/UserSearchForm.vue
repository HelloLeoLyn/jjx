<template>
  <el-card class="search-card" shadow="never">
    <el-form :model="formData" ref="formRef" :inline="true" label-width="80px">
      <!-- 用户名称 -->
      <el-form-item label="用户名称" prop="userName">
        <el-input
          v-model="formData.userName"
          placeholder="请输入用户名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>

      <!-- 部门选择 -->
      <el-form-item label="部门" prop="deptId">
        <DeptTreeSelect
          v-model="formData.deptId"
          :dept-options="deptOptions"
          placeholder="请选择部门"
          clearable
          style="width: 200px"
        />
      </el-form-item>

      <!-- 手机号码 -->
      <el-form-item label="手机号码" prop="phoneNumber">
        <el-input
          v-model="formData.phoneNumber"
          placeholder="请输入手机号码"
          clearable
          style="width: 200px"
          @keyup.enter="handleSearch"
        />
      </el-form-item>

      <!-- 用户状态 -->
      <el-form-item label="用户状态" prop="status">
        <el-select
          v-model="formData.status"
          placeholder="请选择用户状态"
          clearable
          style="width: 200px"
        >
          <el-option label="正常" value="0" />
          <el-option label="停用" value="1" />
        </el-select>
      </el-form-item>

      <!-- 操作按钮 -->
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleSearch">搜索</el-button>
        <el-button icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { ref, watch, reactive } from 'vue'
import type { FormInstance } from 'element-plus'
import DeptTreeSelect from '../../dept/components/DeptTreeSelect.vue'
import type { SysDept } from '@/types/system'

interface Props {
  modelValue: {
    userName?: string
    deptId?: number
    phoneNumber?: string
    status?: string
  }
  deptOptions?: SysDept[]
}

interface Emits {
  (e: 'update:modelValue', value: Props['modelValue']): void
  (e: 'search'): void
  (e: 'reset'): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: () => ({
    userName: '',
    deptId: undefined,
    phoneNumber: '',
    status: '',
  }),
  deptOptions: () => [],
})

const emit = defineEmits<Emits>()

// 表单引用
const formRef = ref<FormInstance>()

// 表单数据 - 使用 reactive 创建响应式副本
const formData = reactive({ ...props.modelValue })

// 监听父组件传递的 modelValue 变化，更新本地表单数据
watch(
  () => props.modelValue,
  (newValue) => {
    Object.assign(formData, newValue)
  },
  { deep: true }
)

// 监听表单数据变化，同步到父组件
watch(
  () => formData,
  (newValue) => {
    emit('update:modelValue', { ...newValue })
  },
  { deep: true }
)

// 搜索事件
const handleSearch = () => {
  emit('search')
}

// 重置事件
const handleReset = () => {
  // 重置表单数据
  Object.assign(formData, {
    userName: '',
    deptId: undefined,
    phoneNumber: '',
    status: '',
  })

  // 触发重置事件
  emit('reset')
}
</script>

<style scoped>
.search-card {
  margin-bottom: 16px;
}
</style>
