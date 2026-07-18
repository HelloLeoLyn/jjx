<template>
  <el-tree-select
    :model-value="modelValue"
    :data="deptOptions"
    :props="treeProps"
    :placeholder="placeholder"
    :clearable="clearable"
    :filterable="filterable"
    :multiple="multiple"
    :disabled="disabled"
    :size="size"
    :collapse-tags="collapseTags"
    :default-expand-all="defaultExpandAll"
    :check-strictly="checkStrictly"
    :default-expanded-keys="defaultExpandedKeys"
    @update:model-value="handleUpdateValue"
    @change="handleChange"
    @clear="handleClear"
    class="dept-tree-select"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'

// Props 定义
interface Props {
  modelValue?: number | number[] // 选中的部门ID
  deptOptions: any[] // 部门选项数据（父组件提供）
  placeholder?: string // 占位符
  clearable?: boolean // 是否可清空
  filterable?: boolean // 是否可搜索
  multiple?: boolean // 是否多选
  disabled?: boolean // 是否禁用
  size?: 'large' | 'default' | 'small' // 尺寸
  collapseTags?: boolean // 多选时是否折叠标签
  defaultExpandAll?: boolean // 是否默认展开所有节点
  checkStrictly?: boolean // 是否严格的遵守父子节点不互相关联
  defaultExpandedKeys?: number[] // 默认展开的节点
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: undefined,
  deptOptions: () => [],
  placeholder: '请选择部门',
  clearable: true,
  filterable: false,
  multiple: false,
  disabled: false,
  size: 'default',
  collapseTags: false,
  defaultExpandAll: false,
  checkStrictly: false,
  defaultExpandedKeys: () => [],
})

// Emits 定义
const emit = defineEmits<{
  'update:modelValue': [value: number | number[] | undefined]
  change: [value: number | number[] | undefined, data: any]
  clear: []
}>()

// 树形选择器配置
const treeProps = {
  value: 'id',
  label: 'deptName',
  children: 'children',
  disabled: 'disabled',
}

// 默认展开的键（合并父组件传入的）
const defaultExpandedKeys = computed(() => {
  if (props.defaultExpandAll) {
    // 如果默认展开所有，返回所有节点的key
    return getAllNodeKeys(props.deptOptions)
  }
  return props.defaultExpandedKeys
})

// 获取所有节点的key
const getAllNodeKeys = (nodes: any[]): number[] => {
  const keys: number[] = []
  const traverse = (nodeList: any[]) => {
    for (const node of nodeList) {
      keys.push(node.id)
      if (node.children && node.children.length) {
        traverse(node.children)
      }
    }
  }
  traverse(nodes)
  return keys
}

// 处理值更新
const handleUpdateValue = (value: number | number[] | undefined) => {
  emit('update:modelValue', value)
}

// 处理变更
const handleChange = (value: number | number[] | undefined, data: any) => {
  emit('change', value, data)
}

// 处理清空
const handleClear = () => {
  emit('clear')
}

// 暴露方法给父组件
defineExpose({
  // 清空选中
  clear: () => {
    emit('update:modelValue', undefined)
  },
  // 设置选中的部门
  setValue: (value: number | number[]) => {
    emit('update:modelValue', value)
  },
  // 获取当前选中的值
  getValue: () => props.modelValue,
})
</script>

<style scoped>
/* 关键：设置组件宽度为100% */
.dept-tree-select {
  width: 180px;
}

/* 确保内部输入框也是100%宽度 */
:deep(.el-input) {
  width: 100%;
}

/* 可选：设置下拉框宽度与输入框一致 */
:deep(.el-select-dropdown) {
  min-width: auto !important;
  width: auto;
  max-width: 400px;
}
</style>
