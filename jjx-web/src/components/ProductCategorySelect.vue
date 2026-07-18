<template>
  <!-- <el-tree-select
    ref="treeSelectRef"
    v-model="selectedValue"
    :data="categoryTree"
    :props="treeProps"
    :placeholder="placeholder"
    :filterable="filterable"
    :filter-method="filterMethod"
    show-checkbox
    check-strictly
    check-on-click-node
    :clearable="clearable"
    :style="{ width: width }"
    @change="handleChange"
  /> -->
  <el-tree-select
    ref="treeSelectRef"
    :props="treeProps"
    v-model="selectedValue"
    :data="categoryTree"
    check-strictly
    :render-after-expand="false"
    clearable
    style="width: 240px"
  />
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { ElTreeSelect, ElMessage } from 'element-plus'
import { getProductCategoryTree } from '@/api/product/category'
import type { ProductCategoryItem } from '@/types/product/category'

interface Props {
  modelValue?: number | null
  placeholder?: string
  width?: string
  clearable?: boolean
  disabled?: boolean
  filterable?: boolean
}

interface Emits {
  (e: 'update:modelValue', value: number | null): void
  (e: 'change', value: number | null, category?: ProductCategoryItem): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: null,
  placeholder: '请选择产品分类',
  width: '100%',
  clearable: true,
  disabled: false,
  filterable: true,
  showAllOption: false,
})

const emit = defineEmits<Emits>()

// Refs
const treeSelectRef = ref<InstanceType<typeof ElTreeSelect>>()
const categoryTree = ref<ProductCategoryItem[]>([])
const isLoading = ref(false)
const hasLoaded = ref(false)

// Tree props configuration
const treeProps = {
  value: 'categoryId',
  label: 'categoryName',
  children: 'children',
  // disabled: (data: any, node: any) => {
  //   // Check if data has status property and if it's '1' (disabled)
  //   // '0' = enabled, '1' = disabled
  //   // Special handling for "全部" option (categoryId = -1) - always enabled
  //   if (data?.categoryId === -1) {
  //     return false
  //   }
  //   return data?.status === '1'
  // },
}

// Computed
const selectedValue = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

// Methods
const loadCategoryTree = async () => {
  // If already loaded, don't load again
  if (hasLoaded.value) {
    return
  }

  isLoading.value = true
  try {
    const res = await getProductCategoryTree()
    if (res.code === 200 && res.data) {
      let treeData = res.data as ProductCategoryItem[]

      categoryTree.value = treeData
      hasLoaded.value = true
    } else {
      ElMessage.error(res.msg || '获取产品分类失败')
    }
  } catch (error) {
    console.error('加载产品分类树失败:', error)
    ElMessage.error('加载产品分类失败')
  } finally {
    isLoading.value = false
  }
}

const filterMethod = (query: string) => {
  // Custom filter method to search in category code and name
  const searchText = query.toLowerCase()

  // The filter-method prop for el-tree-select expects a function that returns a boolean
  // based on whether the node matches the query
  return (node: any) => {
    // Handle different node structures
    const data = node.data || node
    const categoryName = data.categoryName?.toLowerCase() || ''
    const categoryCode = data.categoryCode?.toLowerCase() || ''

    return categoryName.includes(searchText) || categoryCode.includes(searchText)
  }
}

const handleChange = (value: number | null) => {
  if (value === null || value === undefined) {
    emit('change', null)
    return
  }

  // Find the selected category
  const findCategory = (
    tree: ProductCategoryItem[],
    id: number
  ): ProductCategoryItem | undefined => {
    for (const item of tree) {
      if (item.categoryId === id) {
        return item
      }
      if (item.children && item.children.length > 0) {
        const found = findCategory(item.children, id)
        if (found) return found
      }
    }
    return undefined
  }

  const selectedCategory = findCategory(categoryTree.value, value)
  emit('change', value, selectedCategory)
}

const getSelectedCategory = (): ProductCategoryItem | undefined => {
  if (!selectedValue.value) return undefined

  const findCategory = (
    tree: ProductCategoryItem[],
    id: number
  ): ProductCategoryItem | undefined => {
    for (const item of tree) {
      if (item.categoryId === id) {
        return item
      }
      if (item.children && item.children.length > 0) {
        const found = findCategory(item.children, id)
        if (found) return found
      }
    }
    return undefined
  }

  return findCategory(categoryTree.value, selectedValue.value)
}

const clearSelection = () => {
  selectedValue.value = null
  if (treeSelectRef.value) {
    // Clear the tree select internal state
    nextTick(() => {
      // Force clear by triggering change
      emit('change', null)
    })
  }
}

const refresh = async () => {
  hasLoaded.value = false
  await loadCategoryTree()
}

// Expose methods
defineExpose({
  loadCategoryTree,
  clearSelection,
  refresh,
  getSelectedCategory,
  categoryTree,
  isLoading,
})

// Lifecycle
onMounted(() => {
  loadCategoryTree()
})

// Watch for disabled changes to ensure proper state
watch(
  () => props.disabled,
  (newVal) => {
    if (newVal && treeSelectRef.value) {
      // Handle disabled state if needed
    }
  }
)
</script>

<style scoped>
/* Add any custom styles here if needed */
</style>
