<template>
  <div class="app-container">
    <!-- 搜索区域 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" ref="queryForm" :inline="true" label-width="80px">
        <el-form-item label="产品编码" prop="productCode">
          <el-input
            v-model="queryParams.productCode"
            placeholder="请输入产品编码"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="产品名称" prop="productName">
          <el-input
            v-model="queryParams.productName"
            placeholder="请输入产品名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="产品分类" prop="categoryId">
          <ProductCategorySelect
            v-model="queryParams.categoryId"
            :placeholder="'请选择产品分类'"
            :width="'200px'"
            :show-all-option="true"
            @change="handleCategoryFilterChange"
          />
        </el-form-item>
        <el-form-item label="产品状态" prop="productStatus">
          <el-select
            v-model="queryParams.productStatus"
            placeholder="请选择产品状态"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="dict in ProductEnum.status.items"
              :key="dict.value"
              :label="dict.label"
              :value="dict.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            value-format="YYYY-MM-DD"
            style="width: 240px"
          />
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
          <el-button
            type="success"
            plain
            icon="Edit"
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
            :disabled="multiple"
            @click="() => handleDelete()"
            >删除</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button type="warning" plain icon="Download" @click="handleExport">导出</el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="info"
            plain
            icon="Check"
            :disabled="single"
            @click="() => handlePublish()"
            >发布</el-button
          >
        </el-col>
        <el-col :span="1.5">
          <el-button
            type="warning"
            plain
            icon="Close"
            :disabled="single"
            @click="() => handleDisable()"
            >停用</el-button
          >
        </el-col>
      </el-row>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="productList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="产品编码" align="center" prop="productCode" width="120">
          <template #default="scope">
            <el-button link type="primary" @click="handleView(scope.row)">{{
              scope.row.productCode
            }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="产品名称" align="center" prop="productName" width="120" />
        <el-table-column label="产品分类" align="center" prop="categoryId" width="120">
          <template #default="scope">
            {{ getCategoryName(scope.row.categoryId) }}
          </template>
        </el-table-column>

        <el-table-column label="产品状态" prop="productStatus" width="100">
          <template #default="scope">
            <el-tag :type="ProductEnum.status.getTagProps(scope.row.productStatus).type">
              {{ ProductEnum.status.getLabel(scope.row.productStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="单位" align="center" prop="unit" width="80" />
        <el-table-column label="BOM" align="center" prop="bomCode">
          <template #default="scope">
            <el-button
              v-if="scope.row.bomCode"
              link
              type="warning"
              icon="Document"
              @click="handleViewBom(scope.row)"
              >{{ scope.row.bomCode }}_{{ scope.row.bomVersion }}</el-button
            >
            <el-button v-else link type="primary" icon="Plus" @click="handleConfigBom(scope.row)">
              配置BOM
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="工艺路线" align="center" prop="routeCode">
          <template #default="scope">
            <el-button
              v-if="scope.row.routeCode"
              link
              type="success"
              icon="SetUp"
              @click="handleViewRoute(scope.row)"
              >{{ scope.row.routeCode }}_{{ scope.row.routeVersion }}</el-button
            >
            <el-button v-else link type="primary" icon="Plus" @click="handleConfigRoute(scope.row)">
              配置工艺路线
            </el-button>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column
          label="操作"
          align="left"
          class-name="small-padding fixed-width"
          width="280"
          fixed="right"
        >
          <template #default="scope">
            <div class="action-buttons">
              <!-- 开发中：编辑、提交审核、删除 -->
              <template
                v-if="ProductEnum.status.canDo(scope.row.productStatus, ProductEnum.actions.EDIT)"
              >
                <el-button link type="primary" size="small" @click="handleUpdate(scope.row)"
                  >编辑</el-button
                >
              </template>
              <template
                v-if="ProductEnum.status.canDo(scope.row.productStatus, ProductEnum.actions.SUBMIT)"
              >
                <el-button link type="warning" size="small" @click="handleSubmit(scope.row)"
                  >提交审核</el-button
                >
              </template>
              <template
                v-if="ProductEnum.status.canDo(scope.row.productStatus, ProductEnum.actions.DELETE)"
              >
                <el-button link type="danger" size="small" @click="handleDelete(scope.row)"
                  >删除</el-button
                >
              </template>

              <!-- 待审核：审核通过、审核驳回、取消 -->
              <template
                v-if="
                  ProductEnum.status.canDo(scope.row.productStatus, ProductEnum.actions.APPROVE)
                "
              >
                <el-button link type="success" size="small" @click="handleApprove(scope.row)"
                  >通过</el-button
                >
              </template>
              <template
                v-if="ProductEnum.status.canDo(scope.row.productStatus, ProductEnum.actions.REJECT)"
              >
                <el-button link type="danger" size="small" @click="handleReject(scope.row)"
                  >驳回</el-button
                >
              </template>
              <template
                v-if="ProductEnum.status.canDo(scope.row.productStatus, ProductEnum.actions.CANCEL)"
              >
                <el-button link type="info" size="small" @click="handleCancel(scope.row)"
                  >取消</el-button
                >
              </template>

              <!-- 已通过：发布 -->
              <template
                v-if="
                  ProductEnum.status.canDo(scope.row.productStatus, ProductEnum.actions.PUBLISH)
                "
              >
                <el-button link type="success" size="small" @click="handlePublish(scope.row)"
                  >发布</el-button
                >
              </template>

              <!-- 已发布：停产 -->
              <template
                v-if="
                  ProductEnum.status.canDo(scope.row.productStatus, ProductEnum.actions.OBSOLETE)
                "
              >
                <el-button link type="danger" size="small" @click="handleObsolete(scope.row)"
                  >停产</el-button
                >
              </template>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页区域 -->
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="queryParams.pageNum"
          v-model:page-size="queryParams.pageSize"
          :page-sizes="[10, 20, 30, 50]"
          :total="total"
          layout="total, sizes, prev, pager, next, jumper"
          @size-change="handleSizeChange"
          @current-change="handleCurrentChange"
        />
      </div>
    </el-card>

    <!-- 添加或修改产品对话框组件 -->
    <ProductForm v-model="open" :product-id="selectedProductId" @success="() => {}" />
    <!-- 产品详情组件 -->
    <ProductDetailDialog v-model="detailOpen" :product-id="selectedProductId" />
    <RouteDetailDialog v-model="routeDetailDialogVisible" :routing-id="currentRoutingId" />
    <BomDetail :bom-id="currentBomId" v-model="bomDetailDialogVisible" />
    <!-- 配置验证对话框组件 -->
    <ProductConfigValidateDialog v-model="validateOpen" :product-id="selectedProductId" />
    <ApproveDialog v-model="approveOpen" :product-id="selectedProductId"></ApproveDialog>
    <!-- 配置BOM对话框 -->
    <BomConfigDialog v-model="bomConfigVisible" :product="bomConfigProduct" @success="getList" />
    <!-- 配置工艺路线对话框 -->
    <RouteConfigDialog
      v-model="routeConfigVisible"
      :product="routeConfigProduct"
      @success="getList"
    />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'ProductList',
})

import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { productApi } from '@/api/product'
import { parseTime } from '@/utils/format'
import ProductCategorySelect from '@/components/ProductCategorySelect.vue'
import ProductForm from './components/ProductForm.vue'
import ProductDetailDialog from './components/ProductDetailDialog.vue'
import BomDetail from '../bom/components/BomDetail.vue'
import RouteDetailDialog from '../route/components/RouteDetailDialog.vue'
import ProductConfigValidateDialog from './components/ProductConfigValidateDialog.vue'
import type { ProductQueryParams, ProductFormData, ProductVo } from '@/types/product'
import { ProductEnum } from '@/enums'
import { useProductCategory } from '@/composables/useProductCategory'
import type { ProductCategoryDictItem } from '@/types/product/category'
import ApproveDialog from './components/ApproveDialog.vue'
import BomConfigDialog from './components/BomConfigDialog.vue'
import RouteConfigDialog from './components/RouteConfigDialog.vue'
const router = useRouter()
const { categoryList, fetchList } = useProductCategory()
// 创建类别ID到名称的映射表（核心）
const categoryMap = ref<Map<number, ProductCategoryDictItem>>(new Map())

// 构建映射表
const buildCategoryMap = () => {
  const map = new Map<number, ProductCategoryDictItem>()
  categoryList.value.forEach((category: any) => {
    const dictItem: ProductCategoryDictItem = {
      categoryId: category.categoryId,
      parentId: category.parentId,
      categoryCode: category.categoryCode,
      categoryName: category.categoryName,
      categoryLevel: category.categoryLevel,
    }
    map.set(category.categoryId, dictItem)
  })
  categoryMap.value = map
}

// 根据ID获取类别名称
const getCategoryName = (categoryId: number): string => {
  return categoryMap.value.get(categoryId)?.categoryName || '未知类别'
}
const getCategoryCode = (categoryId: number): string => {
  return categoryMap.value.get(categoryId)?.categoryCode || '未知类别代码'
}
// 初始化：一次性加载所有类别数据
const initCategories = async () => {
  try {
    // 只调用一次，获取所有类别数据
    await fetchList()
    buildCategoryMap()
  } catch (error) {
    console.error('加载类别数据失败:', error)
  }
}

// 查询参数
const queryParams = reactive<ProductQueryParams>({
  pageNum: 1,
  pageSize: 10,
  productCode: undefined,
  productName: undefined,
  categoryId: undefined,
  productStatus: undefined,
  startDate: undefined,
  endDate: undefined,
})

// 响应式数据
const loading = ref(false)
const ids = ref<number[]>([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const title = ref('')
const open = ref(false)
const detailOpen = ref(false)
const validateOpen = ref(false)
const approveOpen = ref(false)
const currentRoutingId = ref<number | undefined>(undefined)
const routeDetailDialogVisible = ref(false)
const currentBomId = ref<number | undefined>(undefined)
const bomDetailDialogVisible = ref(false)
const dateRange = ref<string[]>([])
const categoryOptions = ref<
  Array<{ categoryId: number; categoryName: string; categoryCode: string }>
>([])
const selectedProductId = ref<number | undefined>(undefined)

// 配置BOM相关
const bomConfigVisible = ref(false)
const bomConfigProduct = ref<ProductVo | undefined>(undefined)

// 配置工艺路线相关
const routeConfigVisible = ref(false)
const routeConfigProduct = ref<ProductVo | undefined>(undefined)

// 表格数据
const productList = ref<ProductVo[]>([])

// 获取产品列表
const getList = async () => {
  loading.value = true
  try {
    // 处理日期范围
    if (dateRange.value && dateRange.value.length === 2) {
      queryParams.startDate = dateRange.value[0]
      queryParams.endDate = dateRange.value[1]
    } else {
      queryParams.startDate = undefined
      queryParams.endDate = undefined
    }

    const response = await productApi.page(queryParams)
    productList.value = response.data?.records || []
    total.value = response.data?.total || 0
  } catch (error) {
    console.error('获取产品列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 获取分类选项
const getCategoryOptions = async () => {
  try {
    const response = await productApi.category.getProductCategoryTree()
    categoryOptions.value = response.data || []
  } catch (error) {
    console.error('获取分类选项失败:', error)
  }
}

// 搜索按钮操作
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置按钮操作
const resetQuery = () => {
  dateRange.value = []
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    productCode: undefined,
    productName: undefined,
    categoryId: undefined,
    productStatus: undefined,
    startDate: undefined,
    endDate: undefined,
    isAsc: undefined,
  })
  getList()
}

// 多选框选中数据
const handleSelectionChange = (selection: ProductVo[]) => {
  ids.value = selection.map((item) => item.productId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

// 新增按钮操作
const handleAdd = () => {
  router.push('/product/list/create')
}

// 修改按钮操作
const handleUpdate = (row?: ProductVo) => {
  if (row?.productId) {
    router.push(`/product/list/edit/${row.productId}`)
  }
}

// 删除按钮操作
const handleDelete = (row?: ProductVo) => {
  const productIds = row?.productId || ids.value
  ElMessageBox.confirm('是否确认删除产品编码为"' + productIds + '"的数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      return productApi.remove(productIds)
    })
    .then(() => {
      getList()
      ElMessage.success('删除成功')
    })
    .catch(() => {})
}

// 导出按钮操作
const handleExport = () => {
  ElMessageBox.confirm('是否确认导出所有产品数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      return productApi.export(queryParams)
    })
    .then(() => {
      ElMessage.success('导出成功')
    })
    .catch(() => {})
}

// 分页大小改变
const handleSizeChange = (val: number) => {
  queryParams.pageSize = val
  getList()
}

// 当前页改变
const handleCurrentChange = (val: number) => {
  queryParams.pageNum = val
  getList()
}

// 发布按钮操作
const handlePublish = (row?: ProductVo) => {
  const productId = row?.productId || ids.value[0]
  ElMessageBox.confirm('是否确认发布产品？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
  })
    .then(() => {
      return productApi.publish(productId)
    })
    .then(() => {
      getList()
      ElMessage.success('发布成功')
    })
    .catch(() => {})
}

// 停用按钮操作
const handleDisable = (row?: ProductVo) => {
  const productId = row?.productId || ids.value[0]
  ElMessageBox.confirm('是否确认停用产品？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      return productApi.disable(productId)
    })
    .then(() => {
      getList()
      ElMessage.success('停用成功')
    })
    .catch(() => {})
}

// 提交审核
const handleSubmit = (row: ProductVo) => {
  approveOpen.value = true
  title.value = '提交审核'
  selectedProductId.value = row.productId
}

// 审核通过
const handleApprove = (row: ProductVo) => {
  ElMessageBox.confirm(`是否确认审核通过产品"${row.productName}"？`, '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
  })
    .then(() => {
      return productApi.approve(row.productId)
    })
    .then(() => {
      getList()
      ElMessage.success('审核通过成功')
    })
    .catch(() => {})
}

// 审核驳回
const handleReject = (row: ProductVo) => {
  ElMessageBox.prompt(`请输入驳回原因（产品"${row.productName}"）`, '驳回', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    inputType: 'textarea',
    inputPlaceholder: '请输入驳回原因',
    inputValidator: (value: string) => {
      if (!value) return '驳回原因不能为空'
      return true
    },
  })
    .then(({ value }) => {
      return productApi.reject(row.productId, value)
    })
    .then(() => {
      getList()
      ElMessage.success('已驳回')
    })
    .catch(() => {})
}

// 取消（取消审核/取消发布）
const handleCancel = (row: ProductVo) => {
  const statusLabel = ProductEnum.status.getLabel(row.productStatus)
  ElMessageBox.confirm(
    `是否确认取消当前操作（产品"${row.productName}"，当前状态：${statusLabel}）？`,
    '提示',
    {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning',
    }
  )
    .then(() => {
      return productApi.cancel(row.productId)
    })
    .then(() => {
      getList()
      ElMessage.success('取消成功')
    })
    .catch(() => {})
}

// 停产
const handleObsolete = (row: ProductVo) => {
  ElMessageBox.confirm(`是否确认将产品"${row.productName}"停产？停产后将无法恢复。`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      return productApi.obsolete(row.productId)
    })
    .then(() => {
      getList()
      ElMessage.success('已停产')
    })
    .catch(() => {})
}

// 配置BOM按钮操作
const handleConfigBom = (row: ProductVo) => {
  bomConfigProduct.value = row
  bomConfigVisible.value = true
}

// 配置工艺路线按钮操作
const handleConfigRoute = (row: ProductVo) => {
  routeConfigProduct.value = row
  routeConfigVisible.value = true
}

// 查看BOM按钮操作
const handleViewBom = (row: ProductVo) => {
  currentBomId.value = row.currentBomId
  bomDetailDialogVisible.value = true
}

// 查看工艺路线按钮操作
const handleViewRoute = (row: ProductVo) => {
  currentRoutingId.value = row.currentRouteId
  routeDetailDialogVisible.value = true
}

// 查看详情按钮操作
const handleView = (row: ProductVo) => {
  selectedProductId.value = row.productId
  detailOpen.value = true
}

// 处理分类过滤变化（搜索）
const handleCategoryFilterChange = (value: number | null, category?: any) => {
  // 当选择"全部"选项时，value为-1，我们将其设为undefined以清除过滤
  if (value === -1) {
    queryParams.categoryId = undefined
  } else {
    queryParams.categoryId = value || undefined
  }

  // 可以在这里添加额外的处理逻辑，比如记录选择的分类信息
  if (category) {
    console.log('选择了分类:', category.categoryName, 'ID:', category.categoryId)
  }
}

// 组件挂载时获取数据
onMounted(() => {
  getList()
  getCategoryOptions()
  initCategories()
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

/* 规格参数样式 */
.spec-item {
  margin-bottom: 8px;
  padding: 4px 8px;
  background-color: #f5f7fa;
  border-radius: 4px;
  border-left: 3px solid #409eff;
}

.spec-item:last-child {
  margin-bottom: 0;
}

.spec-item strong {
  color: #303133;
  margin-right: 8px;
}

.spec-item span {
  color: #606266;
}
</style>
