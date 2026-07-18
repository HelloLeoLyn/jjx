<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" :inline="true" label-width="80px">
        <el-form-item label="分类编码" prop="categoryCode">
          <el-input
            v-model="queryParams.categoryCode"
            placeholder="请输入分类编码"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="分类名称" prop="categoryName">
          <el-input
            v-model="queryParams.categoryName"
            placeholder="请输入分类名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select
            v-model="queryParams.status"
            placeholder="请选择状态"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="dict in statusOptions"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作按钮区域 -->
    <el-card class="operation-card" shadow="never">
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button type="info" plain icon="Refresh" @click="refreshTree">刷新</el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 分类树表格 -->
    <el-card class="table-card" shadow="never">
      <el-table
        v-loading="loading"
        :data="categoryTree"
        row-key="categoryId"
        :tree-props="{ children: 'children', hasChildren: 'hasChildren' }"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="分类编码" align="center" prop="categoryCode" width="260" />
        <el-table-column label="分类名称" align="center" prop="categoryName" width="180" />
        <el-table-column label="分类级别" align="center" prop="categoryLevel" width="100" />
        <el-table-column label="排序" align="center" prop="sortOrder" width="80" />
        <el-table-column label="状态" prop="status" width="100">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.status)">
              {{ getStatusLabel(scope.row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="280">
          <template #default="scope">
            <span>{{ parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          align="center"
          class-name="small-padding fixed-width"
          width="200"
        >
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button
                link
                type="primary"
                icon="Edit"
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button
                link
                type="danger"
                icon="Delete"
                @click="handleDelete(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="添加子分类" placement="top">
              <el-button
                link
                type="success"
                icon="Plus"
                @click="handleAddChild(scope.row)"
              ></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加或修改分类对话框 -->
    <el-dialog :title="title" v-model="open" width="600px" append-to-body>
      <el-form ref="categoryFormRef" :model="form" :rules="rules" label-width="120px">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="父级分类" prop="parentId">
              <el-tree-select
                v-model="form.parentId"
                :data="categoryTreeOptions"
                :props="treeProps"
                placeholder="请选择父级分类"
                check-strictly
                style="width: 100%"
                @change="handleParentChange"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="分类编码" prop="categoryCode">
              <el-input v-model="form.categoryCode" placeholder="请输入分类编码" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="分类名称" prop="categoryName">
              <el-input v-model="form.categoryName" placeholder="请输入分类名称" maxlength="100" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="分类级别" prop="categoryLevel">
              <el-input-number
                v-model="form.categoryLevel"
                :min="1"
                :max="5"
                :precision="0"
                placeholder="请输入分类级别"
                readonly
                style="width: 100%"
              />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" placeholder="请选择状态" style="width: 100%">
                <el-option
                  v-for="dict in statusOptions"
                  :key="dict.value"
                  :label="dict.label"
                  :value="dict.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="描述" prop="remark">
              <el-input
                v-model="form.remark"
                type="textarea"
                placeholder="请输入描述"
                :rows="3"
                maxlength="500"
                show-word-limit
              />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'ProductCategory',
})

import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { productCategoryApi } from '@/api/product/category'

import { parseTime } from '@/utils/format'
import type { ProductCategoryFormData, ProductCategoryItem } from '@/types/product/category'

// 查询参数
const queryParams = reactive({
  pageNum: 1,
  pageSize: 10,
  categoryCode: undefined as string | undefined,
  categoryName: undefined as string | undefined,
  parentId: undefined as number | undefined,
  status: undefined as string | undefined,
  orderByColumn: undefined as string | undefined,
  isAsc: undefined as 'asc' | 'desc' | undefined,
})

// 响应式数据
const loading = ref(false)
const showSearch = ref(true)
const ids = ref<number[]>([])
const single = ref(true)
const multiple = ref(true)
const title = ref('')
const open = ref(false)

// 表单数据
const form = reactive<ProductCategoryFormData>({
  categoryId: undefined,
  parentId: 0,
  categoryCode: '',
  categoryName: '',
  categoryLevel: 1,
  sortOrder: 0,
  status: '0',
  remark: '',
})

// 表单引用
const categoryFormRef = ref<FormInstance>()

// 表单验证规则
const rules = reactive<FormRules>({
  categoryCode: [{ required: true, message: '请输入分类编码', trigger: 'blur' }],
  categoryName: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  parentId: [{ required: true, message: '请选择父级分类', trigger: 'change' }],
  categoryLevel: [{ required: true, message: '请输入分类级别', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
})

// 分类树数据
const categoryTree = ref<ProductCategoryItem[]>([])
const categoryTreeOptions = ref<ProductCategoryItem[]>([])

// 树形选择器配置
const treeProps = {
  value: 'categoryId',
  label: 'categoryName',
  children: 'children',
}

// 字典选项
const statusOptions = ref([
  { value: '0', label: '启用' },
  { value: '1', label: '停用' },
])

// 获取分类树
const getCategoryTree = async () => {
  loading.value = true
  try {
    const response = await productCategoryApi.tree(queryParams)
    categoryTree.value = response.data || []
    categoryTreeOptions.value = response.data || []
  } catch (error) {
    console.error('获取分类树失败:', error)
  } finally {
    loading.value = false
  }
}

// 状态标签类型/文本
const getStatusTagType = (status: string) => {
  switch (status) {
    case '0':
      return 'success'
    case '1':
      return 'danger'
    default:
      return 'info'
  }
}

const getStatusLabel = (status: string) => {
  const map: Record<string, string> = {
    '0': '启用',
    '1': '停用',
  }
  return map[status] || '未知'
}

// 搜索按钮操作
const handleQuery = () => {
  getCategoryTree()
}

// 重置按钮操作
const resetQuery = () => {
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    categoryCode: undefined,
    categoryName: undefined,
    parentId: undefined,
    status: undefined,
    orderByColumn: undefined,
    isAsc: undefined,
  })
  getCategoryTree()
}

// 多选框选中数据
const handleSelectionChange = (selection: ProductCategoryItem[]) => {
  ids.value = selection.map((item) => item.categoryId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

// 新增按钮操作
const handleAdd = () => {
  resetForm()
  open.value = true
  title.value = '新增分类'
}

// 添加子分类按钮操作
const handleAddChild = (row: ProductCategoryItem) => {
  resetForm()
  form.parentId = row.categoryId
  form.categoryLevel = row.categoryLevel + 1
  open.value = true
  title.value = '添加子分类'
}

// 修改按钮操作
const handleUpdate = (row?: ProductCategoryItem) => {
  resetForm()
  const categoryId = row?.categoryId || ids.value[0]
  productCategoryApi.getInfo(categoryId).then((response: any) => {
    Object.assign(form, response.data)
    open.value = true
    title.value = '修改分类'
  })
}

// 删除按钮操作
const handleDelete = (row?: ProductCategoryItem) => {
  const categoryId = row?.categoryId || ids.value[0]
  ElMessageBox.confirm('是否确认删除分类编码为"' + categoryId + '"的数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      return productCategoryApi.remove(categoryId)
    })
    .then(() => {
      getCategoryTree()
      ElMessage.success('删除成功')
    })
    .catch(() => {})
}

// 刷新树按钮操作
const refreshTree = () => {
  getCategoryTree()
}

// 表单重置
const resetForm = () => {
  if (categoryFormRef.value) {
    categoryFormRef.value.resetFields()
  }
  Object.assign(form, {
    categoryId: undefined,
    parentId: 0,
    categoryCode: '',
    categoryName: '',
    categoryLevel: 1,
    sortOrder: 0,
    status: '0',
    remark: '',
  })
}

// 提交表单
const submitForm = () => {
  if (!categoryFormRef.value) return

  categoryFormRef.value.validate((valid) => {
    if (valid) {
      if (form.categoryId !== undefined) {
        productCategoryApi.edit(form as any).then(() => {
          ElMessage.success('修改成功')
          open.value = false
          getCategoryTree()
        })
      } else {
        productCategoryApi.add(form as any).then(() => {
          ElMessage.success('新增成功')
          open.value = false
          getCategoryTree()
        })
      }
    }
  })
}

// 取消按钮
const cancel = () => {
  open.value = false
  resetForm()
}
// 处理父级分类选择变化
const handleParentChange = (selectedParentId: number) => {
  console.log(selectedParentId)

  if (selectedParentId === 0) {
    // 选择根节点，分类级别为1
    form.categoryLevel = 1
    return
  }

  // 查找选中的父级分类
  const findCategory = (
    categories: ProductCategoryItem[],
    id: number
  ): ProductCategoryItem | null => {
    for (const category of categories) {
      if (category.categoryId === id) {
        return category
      }
      if (category.children && category.children.length > 0) {
        const found = findCategory(category.children, id)
        if (found) return found
      }
    }
    return null
  }

  const parentCategory = findCategory(categoryTreeOptions.value, selectedParentId)
  if (parentCategory) {
    // 分类级别 = 父级分类级别 + 1
    form.categoryLevel = parentCategory.categoryLevel + 1
  } else {
    // 未找到父级分类，设置为1级
    form.categoryLevel = 1
  }
}
// 组件挂载时获取数据
onMounted(() => {
  getCategoryTree()
})
</script>

<style scoped>
.search-card {
  margin-bottom: 20px;
}

.operation-card {
  margin-bottom: 20px;
}

.table-card {
  margin-bottom: 20px;
}
</style>
