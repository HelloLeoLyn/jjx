<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <SearchForm
      v-model="queryParams"
      :fields="searchFields"
      @search="handleQuery"
      @reset="resetQuery"
    >
    </SearchForm>
    <!-- 操作按钮区域 -->
    <el-card class="operation-card" shadow="never">
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Plus" @click="handleAdd">新增</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="success"
            plain
            icon="Edit"
            v-hasPermi="['inventory:category:edit']"
            :disabled="single"
            @click="() => handleUpdate()"
            >修改</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="danger"
            plain
            icon="Delete"
            v-hasPermi="['inventory:category:remove']"
            :disabled="multiple"
            @click="() => handleDelete()"
            >删除</el-button
          >
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
        <el-table-column label="分类编码" align="center" prop="categoryCode" width="160" />
        <el-table-column label="分类名称" align="center" prop="categoryName" width="180" />
        <el-table-column label="分类级别" align="center" prop="categoryLevel" width="100" />
        <el-table-column label="排序" align="center" prop="sortOrder" width="80" />
        <el-table-column label="状态" prop="status" width="100">
          <template #default="{ row }"> </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          align="center"
          class-name="small-padding fixed-width"
          min-width="200"
        >
          <template #default="scope">
            <el-tooltip content="修改" placement="top">
              <el-button
                link
                type="primary"
                icon="Edit"
                v-hasPermi="['inventory:category:edit']"
                @click="handleUpdate(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip content="删除" placement="top">
              <el-button
                link
                type="danger"
                icon="Delete"
                v-hasPermi="['inventory:category:remove']"
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
            <el-form-item label="排序" prop="sortOrder">
              <el-input-number
                v-model="form.sortOrder"
                :min="0"
                :precision="0"
                placeholder="请输入排序"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
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
          <el-col :span="12">
            <el-form-item label="备注" prop="remark">
              <el-input v-model="form.remark" placeholder="请输入备注" maxlength="200" />
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
  name: 'MaterialCategory',
})
import { ref, reactive, onMounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import SearchForm from '@/components/common-ui/SearchForm.vue'
import type { SearchOptions } from '@/components/common-ui/type'
import { materialCategoryApi } from '@/api/inventory/materialCategory'
import type {
  MaterialCategoryItem,
  MaterialCategoryFormData,
  MaterialCategoryQueryParams,
} from '@/types/inventory/material'
import { parseTime } from '@/utils/format'
import { searchConfig } from './category.config'
import { MaterialEnum } from '@/enums/inventory'
// ==================== 配置 ====================

// 搜索表单配置
const searchFields: SearchOptions[] = searchConfig

// 状态管理
const loading = ref(false)
const showSearch = ref(true)
const open = ref(false)
const title = ref('')

// 表格数据
const categoryTree = ref<MaterialCategoryItem[]>([])
const categoryTreeOptions = ref<MaterialCategoryItem[]>([])

// 表单数据
const form = reactive<MaterialCategoryFormData>({
  parentId: 0,
  categoryCode: '',
  categoryName: '',
  categoryLevel: 1,
  sortOrder: 0,
  status: '1',
  remark: '',
})

// 查询参数
const queryParams = reactive({
  current: 1,
  pageSize: 100,
  categoryCode: '',
  categoryName: '',
  status: '',
})

// 表单验证规则
const rules = {
  parentId: [{ required: true, message: '请选择父级分类', trigger: 'change' }],
  categoryCode: [
    { required: true, message: '请输入分类编码', trigger: 'blur' },
    { min: 1, max: 50, message: '长度在 1 到 50 个字符', trigger: 'blur' },
  ],
  categoryName: [
    { required: true, message: '请输入分类名称', trigger: 'blur' },
    { min: 1, max: 100, message: '长度在 1 到 100 个字符', trigger: 'blur' },
  ],
  categoryLevel: [{ required: true, message: '请输入分类级别', trigger: 'blur' }],
  sortOrder: [{ required: true, message: '请输入排序', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
}

// 表单引用
const categoryFormRef = ref()

// 状态选项
const statusOptions = [
  { label: '正常', value: '1' },
  { label: '停用', value: '1' },
]

// 树形选择器配置
const treeProps = {
  value: 'categoryId',
  label: 'categoryName',
  children: 'children',
}

// 计算属性
const single = computed(() => ids.value.length !== 1)
const multiple = computed(() => ids.value.length < 1)

// 选中的ID数组
const ids = ref<number[]>([])

// 获取分类树
const getCategoryTree = async () => {
  try {
    loading.value = true
    const response = await materialCategoryApi.getTree(queryParams as any)
    if (response.code === 200) {
      categoryTree.value = response.data || []
      if (response.data) {
        categoryTreeOptions.value = [
          { categoryId: 0, categoryName: '顶级分类', children: [] },
          ...(response.data as any),
        ]
      }
    }
  } catch (error) {
    console.error('获取分类树失败:', error)
    ElMessage.error('获取分类树失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleQuery = () => {
  getCategoryTree()
}

// 重置查询
const resetQuery = () => {
  queryParams.categoryCode = ''
  queryParams.categoryName = ''
  queryParams.status = ''
  handleQuery()
}

// 刷新树
const refreshTree = () => {
  getCategoryTree()
}

// 表格选择变化
const handleSelectionChange = (selection: MaterialCategoryItem[]) => {
  ids.value = selection.map((item) => item.categoryId)
}

// 父级分类变化处理
const handleParentChange = (value: number) => {
  // 根据父级分类计算当前分类级别
  if (value === 0) {
    form.categoryLevel = 1
  } else {
    const parentCategory = findCategoryById(categoryTreeOptions.value, value)
    form.categoryLevel = (parentCategory?.categoryLevel || 0) + 1
  }
}

// 递归查找分类
const findCategoryById = (
  tree: MaterialCategoryItem[],
  id: number
): MaterialCategoryItem | null => {
  for (const item of tree) {
    if (item.categoryId === id) {
      return item
    }
    if (item.children && item.children.length > 0) {
      const found = findCategoryById(item.children, id)
      if (found) return found
    }
  }
  return null
}

// 新增分类
const handleAdd = () => {
  resetForm()
  open.value = true
  title.value = '添加材料分类'
}

// 添加子分类
const handleAddChild = (row: MaterialCategoryItem) => {
  resetForm()
  form.parentId = row.categoryId
  form.categoryLevel = row.categoryLevel + 1
  open.value = true
  title.value = '添加子分类'
}

// 修改分类
const handleUpdate = (row?: MaterialCategoryItem) => {
  resetForm()
  const categoryId = row?.categoryId || ids.value[0]
  if (categoryId) {
    const category = findCategoryById(categoryTree.value, categoryId)
    if (category) {
      Object.assign(form, category)
      open.value = true
      title.value = '修改材料分类'
    }
  }
}

// 删除分类
const handleDelete = async (row?: MaterialCategoryItem) => {
  const categoryIds = row ? [row.categoryId] : ids.value
  if (categoryIds.length === 0) {
    ElMessage.warning('请选择要删除的分类')
    return
  }

  try {
    await ElMessageBox.confirm('是否确认删除选中的分类？', '警告', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    })

    for (const id of categoryIds) {
      await materialCategoryApi.delete(id)
    }

    ElMessage.success('删除成功')
    getCategoryTree()
  } catch (error) {
    if (error !== 'cancel') {
      console.error('删除失败:', error)
      ElMessage.error('删除失败')
    }
  }
}

// 提交表单
const submitForm = async () => {
  if (!categoryFormRef.value) return

  try {
    await categoryFormRef.value.validate()

    if (form.categoryId) {
      await materialCategoryApi.update(form)
      ElMessage.success('修改成功')
    } else {
      await materialCategoryApi.add(form)
      ElMessage.success('新增成功')
    }

    open.value = false
    getCategoryTree()
  } catch (error) {
    console.error('提交失败:', error)
    ElMessage.error('提交失败')
  }
}

// 取消
const cancel = () => {
  open.value = false
  resetForm()
}

// 重置表单
const resetForm = () => {
  if (categoryFormRef.value) {
    categoryFormRef.value.resetFields()
  }
  Object.assign(form, {
    parentId: 0,
    categoryCode: '',
    categoryName: '',
    categoryLevel: 1,
    sortOrder: 0,
    status: '1',
    remark: '',
  })
}

// 页面加载时获取数据
onMounted(() => {
  getCategoryTree()
})
</script>

<style scoped>
.app-container {
  padding: 20px;
}

.search-card,
.operation-card,
.table-card {
  margin-bottom: 20px;
}

.mb8 {
  margin-bottom: 8px;
}

.small-padding :deep(.el-table .cell) {
  padding: 0 8px;
}

.fixed-width {
  width: 200px;
}

.dialog-footer {
  text-align: right;
}
</style>
