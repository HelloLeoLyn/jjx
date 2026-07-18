import { ref, reactive, computed, readonly } from 'vue'
import { productCategoryApi } from '@/api/product/category'
import type {
  ProductCategoryQueryParams,
  ProductCategoryFormData,
  ProductCategoryItem,
} from '@/types/product/category'
import type { R } from '@/types'

// ==================== 类型定义 ====================

interface CategoryTreeNode extends ProductCategoryItem {
  children?: CategoryTreeNode[]
}

interface CategoryState {
  list: ProductCategoryItem[]
  tree: CategoryTreeNode[]
  current: ProductCategoryItem | null
  loading: boolean
  treeLoading: boolean
}

interface CategoryActions {
  // 数据获取
  fetchList: (params?: ProductCategoryQueryParams) => Promise<void>
  fetchTree: (params?: ProductCategoryQueryParams) => Promise<void>
  fetchInfo: (categoryId: number) => Promise<void>

  // CRUD操作
  create: (data: ProductCategoryFormData) => Promise<void>
  update: (data: ProductCategoryFormData) => Promise<void>
  remove: (categoryId: number) => Promise<void>

  // 业务逻辑
  search: (keyword: string) => ProductCategoryItem[]
  filterByParent: (parentId: number | null) => ProductCategoryItem[]
  getCategoryPath: (categoryId: number) => ProductCategoryItem[]
  buildTree: (categories: ProductCategoryItem[]) => CategoryTreeNode[]
  flattenTree: (tree: CategoryTreeNode[]) => ProductCategoryItem[]

  // 表单处理
  resetForm: () => void
  validateForm: (data: ProductCategoryFormData) => boolean
}

// ==================== 常量定义 ====================

const DEFAULT_QUERY_PARAMS: ProductCategoryQueryParams = {
  pageNum: 1,
  pageSize: 100,
  status: '0', // 正常状态
}

const DEFAULT_FORM_DATA: ProductCategoryFormData = {
  parentId: 0,
  categoryCode: '',
  categoryName: '',
  categoryLevel: 1,
  sortOrder: 0,
  status: '0',
  remark: '',
}

// ==================== 工具函数 ====================

/**
 * 构建分类树形结构
 */
const buildCategoryTree = (categories: ProductCategoryItem[]): CategoryTreeNode[] => {
  const map = new Map<number, CategoryTreeNode>()
  const roots: CategoryTreeNode[] = []

  // 创建节点映射
  categories.forEach((category) => {
    map.set(category.categoryId, { ...category, children: [] })
  })

  // 构建树结构
  categories.forEach((category) => {
    const node = map.get(category.categoryId)!
    if (category.parentId === 0) {
      roots.push(node)
    } else {
      const parent = map.get(category.parentId)
      if (parent) {
        parent.children!.push(node)
      }
    }
  })

  // 排序
  const sortNodes = (nodes: CategoryTreeNode[]) => {
    nodes.sort((a, b) => a.sortOrder - b.sortOrder)
    nodes.forEach((node) => {
      if (node.children) {
        sortNodes(node.children)
      }
    })
  }

  sortNodes(roots)
  return roots
}

/**
 * 扁平化树结构
 */
const flattenCategoryTree = (tree: CategoryTreeNode[]): ProductCategoryItem[] => {
  const result: ProductCategoryItem[] = []

  const traverse = (nodes: CategoryTreeNode[]) => {
    nodes.forEach((node) => {
      const { children, ...category } = node
      result.push(category)
      if (children && children.length > 0) {
        traverse(children)
      }
    })
  }

  traverse(tree)
  return result
}

/**
 * 获取分类路径
 */
const getCategoryPath = (
  categoryId: number,
  categories: ProductCategoryItem[]
): ProductCategoryItem[] => {
  const path: ProductCategoryItem[] = []
  let current = categories.find((c) => c.categoryId === categoryId)

  while (current) {
    path.unshift(current)
    current = categories.find((c) => c.categoryId === current!.parentId)
  }

  return path
}

/**
 * 搜索分类
 */
const searchCategories = (
  categories: ProductCategoryItem[],
  keyword: string
): ProductCategoryItem[] => {
  if (!keyword.trim()) return categories

  const lowerKeyword = keyword.toLowerCase()
  return categories.filter(
    (category) =>
      category.categoryName.toLowerCase().includes(lowerKeyword) ||
      category.categoryCode.toLowerCase().includes(lowerKeyword)
  )
}

/**
 * 按父级ID过滤分类
 */
const filterCategoriesByParent = (
  categories: ProductCategoryItem[],
  parentId: number | null
): ProductCategoryItem[] => {
  return categories.filter((category) => category.parentId === (parentId || 0))
}

/**
 * 表单验证
 */
const validateCategoryForm = (data: ProductCategoryFormData): boolean => {
  return !!(
    data.categoryCode?.trim() &&
    data.categoryName?.trim() &&
    data.categoryLevel >= 1 &&
    data.sortOrder >= 0
  )
}

// ==================== 主函数 ====================

export function useProductCategory() {
  // ==================== 响应式状态 ====================

  const state = reactive<CategoryState>({
    list: [],
    tree: [],
    current: null,
    loading: false,
    treeLoading: false,
  })

  const formData = reactive<ProductCategoryFormData>({ ...DEFAULT_FORM_DATA })

  // ==================== 计算属性 ====================

  const categoryList = computed(() => readonly(state.list))
  const categoryTree = computed(() => readonly(state.tree))
  const currentCategory = computed(() => (state.current ? readonly(state.current) : null))
  const isLoading = computed(() => state.loading)
  const isTreeLoading = computed(() => state.treeLoading)

  // ==================== 数据获取方法 ====================

  /**
   * 获取分类列表
   */
  const fetchList = async (params: ProductCategoryQueryParams = DEFAULT_QUERY_PARAMS) => {
    try {
      state.loading = true
      const response = await productCategoryApi.list(params)
      if (response.code === 200) {
        state.list = response.data || []
      }
    } catch (error) {
      console.error('获取产品分类列表失败:', error)
      state.list = []
    } finally {
      state.loading = false
    }
  }

  /**
   * 获取分类树
   */
  const fetchTree = async (params: ProductCategoryQueryParams = DEFAULT_QUERY_PARAMS) => {
    try {
      state.treeLoading = true
      const response = await productCategoryApi.tree(params)
      if (response.code === 200) {
        state.tree = buildCategoryTree(response.data || [])
        // 同步更新列表
        state.list = flattenCategoryTree(state.tree)
      }
    } catch (error) {
      console.error('获取产品分类树失败:', error)
      state.tree = []
      state.list = []
    } finally {
      state.treeLoading = false
    }
  }

  /**
   * 获取分类详情
   */
  const fetchInfo = async (categoryId: number) => {
    try {
      state.loading = true
      const response = await productCategoryApi.getInfo(categoryId)
      if (response.code === 200) {
        state.current = response.data
        // 更新表单数据
        if (response.data) {
          Object.assign(formData, response.data)
        }
      }
    } catch (error) {
      console.error('获取产品分类详情失败:', error)
      state.current = null
    } finally {
      state.loading = false
    }
  }

  // ==================== CRUD操作方法 ====================

  /**
   * 创建分类
   */
  const create = async (data: ProductCategoryFormData) => {
    if (!validateCategoryForm(data)) {
      throw new Error('表单数据验证失败')
    }

    const response = await productCategoryApi.add(data)
    if (response.code === 200) {
      // 刷新数据
      await fetchList()
      await fetchTree()
      resetForm()
    } else {
      throw new Error(response.msg || '创建分类失败')
    }
  }

  /**
   * 更新分类
   */
  const update = async (data: ProductCategoryFormData) => {
    if (!validateCategoryForm(data)) {
      throw new Error('表单数据验证失败')
    }

    const response = await productCategoryApi.edit(data)
    if (response.code === 200) {
      // 刷新数据
      await fetchList()
      await fetchTree()
      resetForm()
    } else {
      throw new Error(response.msg || '更新分类失败')
    }
  }

  /**
   * 删除分类
   */
  const remove = async (categoryId: number) => {
    const response = await productCategoryApi.remove(categoryId)
    if (response.code === 200) {
      // 刷新数据
      await fetchList()
      await fetchTree()
    } else {
      throw new Error(response.msg || '删除分类失败')
    }
  }

  // ==================== 业务逻辑方法 ====================

  /**
   * 搜索分类
   */
  const search = (keyword: string): ProductCategoryItem[] => {
    return searchCategories(state.list, keyword)
  }

  /**
   * 按父级ID过滤分类
   */
  const filterByParent = (parentId: number | null): ProductCategoryItem[] => {
    return filterCategoriesByParent(state.list, parentId)
  }

  /**
   * 获取分类路径
   */
  const getPath = (categoryId: number): ProductCategoryItem[] => {
    return getCategoryPath(categoryId, state.list)
  }

  /**
   * 构建树结构
   */
  const buildTree = (categories: ProductCategoryItem[]): CategoryTreeNode[] => {
    return buildCategoryTree(categories)
  }

  /**
   * 扁平化树结构
   */
  const flattenTree = (tree: CategoryTreeNode[]): ProductCategoryItem[] => {
    return flattenCategoryTree(tree)
  }

  // ==================== 表单处理方法 ====================

  /**
   * 重置表单
   */
  const resetForm = () => {
    Object.assign(formData, DEFAULT_FORM_DATA)
  }

  /**
   * 验证表单
   */
  const validateForm = (data: ProductCategoryFormData): boolean => {
    return validateCategoryForm(data)
  }

  // ==================== 返回值 ====================

  const actions: CategoryActions = {
    fetchList,
    fetchTree,
    fetchInfo,
    create,
    update,
    remove,
    search,
    filterByParent,
    getCategoryPath: getPath,
    buildTree,
    flattenTree,
    resetForm,
    validateForm,
  }

  return {
    // 状态
    categoryList,
    categoryTree,
    currentCategory,
    isLoading,
    isTreeLoading,
    formData,

    // 方法
    ...actions,
  }
}
