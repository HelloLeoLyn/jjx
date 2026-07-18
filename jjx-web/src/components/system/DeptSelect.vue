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
    <el-option v-for="dept in deptList" :key="dept.id" :label="dept.deptName" :value="dept.id!" />
  </el-select>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { deptApi } from '@/api/system/dept'
import type { SysDept } from '@/types/system'

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
  (e: 'change', value: number | number[] | null, depts?: SysDept[]): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  placeholder: '请选择部门',
  width: '100%',
  clearable: true,
  disabled: false,
  filterable: true,
  multiple: false,
})

const emit = defineEmits<Emits>()

// Refs
const deptList = ref<SysDept[]>([])
const isLoading = ref(false)
const hasLoaded = ref(false)

// Computed
const selectedValue = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

// Methods
const loadDeptList = async () => {
  // If already loaded, don't load again
  if (hasLoaded.value) {
    return
  }

  isLoading.value = true
  try {
    const res = await deptApi.treeselect({})
    if (res.code === 200 && res.data) {
      deptList.value = res.data
      hasLoaded.value = true
    } else {
      ElMessage.error(res.msg || '获取部门列表失败')
    }
  } catch (error) {
    console.error('加载部门列表失败:', error)
    ElMessage.error('加载部门列表失败')
  } finally {
    isLoading.value = false
  }
}

const handleChange = (value: number | number[] | null) => {
  if (value === null || value === undefined) {
    emit('change', null)
    return
  }

  // Find the selected dept(s)
  if (Array.isArray(value)) {
    const selectedDepts = deptList.value.filter((dept) => value.includes(dept.id!))
    emit('change', value, selectedDepts)
  } else {
    const selectedDept = deptList.value.find((dept) => dept.id === value)
    emit('change', value, selectedDept ? [selectedDept] : [])
  }
}

const getSelectedDept = (): SysDept | undefined => {
  const value = selectedValue.value
  if (value === null || value === undefined) return undefined
  if (Array.isArray(value)) {
    return undefined // For multiple selection, use getSelectedDepts instead
  }
  return deptList.value.find((dept) => dept.id === value)
}

const getSelectedDepts = (): SysDept[] => {
  const value = selectedValue.value
  if (value === null || value === undefined) return []
  if (Array.isArray(value)) {
    return deptList.value.filter((dept) => value.includes(dept.id!))
  } else {
    const dept = deptList.value.find((dept) => dept.id === value)
    return dept ? [dept] : []
  }
}

const refresh = async () => {
  hasLoaded.value = false
  await loadDeptList()
}

// Expose methods
defineExpose({
  loadDeptList,
  refresh,
  getSelectedDept,
  getSelectedDepts,
  deptList,
  isLoading,
})

// Lifecycle
onMounted(() => {
  loadDeptList()
})
</script>

<style scoped>
/* Add any custom styles here if needed */
</style>
