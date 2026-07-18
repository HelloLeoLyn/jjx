<template>
  <el-select
    v-model="selectedValue"
    :placeholder="placeholder"
    :clearable="clearable"
    :filterable="filterable"
    :disabled="disabled"
    :multiple="multiple"
    :style="{ width: width }"
    @change="handleChange"
  >
    <el-option
      v-for="role in roleList"
      :key="role.roleId"
      :label="role.roleName"
      :value="role.roleId!"
    />
  </el-select>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { roleApi } from '@/api/system/role'
import type { SysRole } from '@/types/system'

interface Props {
  modelValue?: number | number[] | null
  placeholder?: string
  width?: string
  clearable?: boolean
  disabled?: boolean
  filterable?: boolean
  multiple?: boolean
}

interface Emits {
  (e: 'update:modelValue', value: number | number[] | null): void
  (e: 'change', value: number | number[] | null, roles?: SysRole[]): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  placeholder: '请选择角色',
  width: '100%',
  clearable: true,
  disabled: false,
  filterable: true,
  multiple: false,
})

const emit = defineEmits<Emits>()

// Refs
const roleList = ref<SysRole[]>([])
const isLoading = ref(false)
const hasLoaded = ref(false)

// Computed
const selectedValue = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

// Methods
const loadRoleList = async () => {
  // If already loaded, don't load again
  if (hasLoaded.value) {
    return
  }

  isLoading.value = true
  try {
    const res = await roleApi.optionselect()
    if (res.code === 200 && res.data) {
      roleList.value = res.data
      hasLoaded.value = true
    } else {
      ElMessage.error(res.msg || '获取角色列表失败')
    }
  } catch (error) {
    console.error('加载角色列表失败:', error)
    ElMessage.error('加载角色列表失败')
  } finally {
    isLoading.value = false
  }
}

const handleChange = (value: number | number[] | null) => {
  if (value === null || value === undefined) {
    emit('change', null)
    return
  }

  // Find the selected role(s)
  if (Array.isArray(value)) {
    const selectedRoles = roleList.value.filter((role) => value.includes(role.roleId!))
    emit('change', value, selectedRoles)
  } else {
    const selectedRole = roleList.value.find((role) => role.roleId === value)
    emit('change', value, selectedRole ? [selectedRole] : [])
  }
}

const getSelectedRole = (): SysRole | undefined => {
  const value = selectedValue.value
  if (value === null || value === undefined) return undefined
  if (Array.isArray(value)) {
    return undefined // For multiple selection, use getSelectedRoles instead
  }
  return roleList.value.find((role) => role.roleId === value)
}

const getSelectedRoles = (): SysRole[] => {
  const value = selectedValue.value
  if (value === null || value === undefined) return []
  if (Array.isArray(value)) {
    return roleList.value.filter((role) => value.includes(role.roleId!))
  } else {
    const role = roleList.value.find((role) => role.roleId === value)
    return role ? [role] : []
  }
}

const refresh = async () => {
  hasLoaded.value = false
  await loadRoleList()
}

// Expose methods
defineExpose({
  loadRoleList,
  refresh,
  getSelectedRole,
  getSelectedRoles,
  roleList,
  isLoading,
})

// Lifecycle
onMounted(() => {
  loadRoleList()
})
</script>

<style scoped>
/* Add any custom styles here if needed */
</style>
