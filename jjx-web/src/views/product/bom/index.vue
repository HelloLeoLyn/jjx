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
        <el-form-item label="BOM名称" prop="bomName">
          <el-input
            v-model="queryParams.bomName"
            placeholder="请输入BOM名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="BOM状态" prop="approveStatus">
          <el-select
            v-model="queryParams.approveStatus"
            placeholder="请选择BOM状态"
            clearable
            style="width: 200px"
          >
            <el-option
              v-for="dict in bomStatusOptions"
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
            @click="() => handleApprove()"
            >审核</el-button
          >
        </el-col>
      </el-row>
    </el-card>

    <!-- 数据表格 -->
    <el-card class="table-card" shadow="never">
      <el-table v-loading="loading" :data="bomList" @selection-change="handleSelectionChange">
        <el-table-column type="selection" width="55" align="center" />
        <el-table-column label="BOM编码" align="center" prop="bomCode" width="160">
          <template #default="scope">
            <el-button link type="primary" @click="handleView(scope.row)">{{
              scope.row.bomCode
            }}</el-button>
          </template>
        </el-table-column>
        <el-table-column label="BOM版本" align="center" prop="bomVersion" width="100" />
        <el-table-column label="产品编码" align="center" prop="productCode" width="170" />
        <el-table-column label="产品名称" align="center" prop="productName" width="170" />

        <el-table-column label="审核状态" prop="approveStatus" width="100">
          <template #default="scope">
            <el-tag :type="ProductEnum.bomStatus.getTagProps(scope.row.approveStatus)?.type">
              {{ ProductEnum.bomStatus.getLabel(scope.row.approveStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效日期" align="center" prop="effectiveDate" width="120">
          <template #default="scope">
            <span>{{ parseDate(scope.row.effectiveDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="失效日期" align="center" prop="expiryDate" width="120">
          <template #default="scope">
            <span>{{ parseDate(scope.row.expiryDate) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="创建时间" align="center" prop="createTime" width="180">
          <template #default="scope">
            <span>{{ parseTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="备注" align="center" prop="remark" min-width="180" />
        <el-table-column
          label="操作"
          align="center"
          class-name="small-padding fixed-width"
          min-width="300"
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

            <el-tooltip content="复制BOM" placement="top">
              <el-button
                link
                type="success"
                icon="CopyDocument"
                @click="handleCopyBom(scope.row)"
              ></el-button>
            </el-tooltip>
            <el-tooltip
              content="审核"
              placement="top"
              v-if="ProductEnum.bomStatus.canDo(scope.row.approveStatus, ProductActions.SUBMIT)"
            >
              <el-button
                link
                type="warning"
                icon="View"
                @click="handleApprove(scope.row)"
              ></el-button>
            </el-tooltip>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.pageNum"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>

    <!-- 添加或修改BOM对话框 -->
    <BomFormDialog v-model="open" :bom-id="selectedBomId" @success="getList" />
    <BomDetail :bom-id="selectedBomId" v-model="bomDetailOpen" />

    <!-- BOM审核对话框 -->
    <BomApproveDialog
      :bom-id="selectedBomForApprove"
      v-model="bomApproveOpen"
      @success="handleApproveSuccess"
      @close="handleApproveClose"
    />
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'EngineeringBom',
})

import { ref, reactive, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { productBomApi } from '@/api/product/bom'
import { parseTime, parseDate } from '@/utils/format'
import BomDetail from './components/BomDetail.vue'
import BomApproveDialog from './components/BomApproveDialog.vue'
import BomFormDialog from './components/BomFormDialog.vue'
import type { EngineeringBomQueryParams, EngineeringBom } from '@/types/product/bom'
import { ProductEnum, ProductActions } from '@/enums/product'
// 查询参数
const queryParams = reactive<EngineeringBomQueryParams>({
  pageNum: 1,
  pageSize: 10,
  bomCode: undefined,
  bomName: undefined,
  productCode: undefined,
  productName: undefined,
  approveStatus: undefined,
  startDate: undefined,
  endDate: undefined,
})

// 响应式数据
const loading = ref(false)
const ids = ref<number[]>([])
const single = ref(true)
const multiple = ref(true)
const total = ref(0)
const open = ref(false)
const bomDetailOpen = ref(false)
const bomApproveOpen = ref(false)
const selectedBomId = ref<number | undefined>(undefined)
const selectedBomForApprove = ref<number | undefined>(undefined)

// 表格数据
const bomList = ref<EngineeringBom[]>([])

// 字典选项
const bomStatusOptions = ref([
  { value: 'draft', label: '草稿' },
  { value: 'reviewing', label: '审核中' },
  { value: 'approved', label: '已审核' },
  { value: 'active', label: '生效中' },
  { value: 'inactive', label: '已失效' },
])

// 获取BOM列表
const getList = async () => {
  loading.value = true
  try {
    const response = await productBomApi.listEngineeringBom(queryParams)
    bomList.value = response.data?.records || []
    total.value = response.data?.total || 0
  } catch (error) {
    console.error('获取BOM列表失败:', error)
  } finally {
    loading.value = false
  }
}

// 搜索按钮操作
const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

// 重置按钮操作
const resetQuery = () => {
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    bomCode: undefined,
    productCode: undefined,
    productName: undefined,
    bomVersion: undefined,
    bomStatus: undefined,
    startDate: undefined,
    endDate: undefined,
    orderByColumn: undefined,
    isAsc: undefined,
  })
  getList()
}

// 多选框选中数据
const handleSelectionChange = (selection: EngineeringBom[]) => {
  ids.value = selection.map((item) => item.bomId)
  single.value = selection.length !== 1
  multiple.value = !selection.length
}

// 新增按钮操作
const handleAdd = () => {
  selectedBomId.value = undefined
  open.value = true
}

// 修改按钮操作
const handleUpdate = (row?: EngineeringBom) => {
  const bomId = row?.bomId || ids.value[0]
  selectedBomId.value = bomId
  open.value = true
}

// 删除按钮操作
const handleDelete = (row?: EngineeringBom) => {
  const bomIds = row?.bomId || ids.value[0]
  ElMessageBox.confirm('是否确认删除BOM编码为"' + bomIds + '"的数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      return productBomApi.removeEngineeringBom(bomIds)
    })
    .then(() => {
      getList()
      ElMessage.success('删除成功')
    })
    .catch(() => {})
}

// 导出按钮操作
const handleExport = () => {
  ElMessageBox.confirm('是否确认导出所有BOM数据项？', '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning',
  })
    .then(() => {
      return productBomApi.exportEngineeringBom(queryParams)
    })
    .then((res) => {
      const blob = new Blob([res as any], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      })
      const link = document.createElement('a')
      link.href = URL.createObjectURL(blob)
      link.download = `BOM列表_${new Date().toISOString().slice(0, 10)}.xlsx`
      link.click()
      URL.revokeObjectURL(link.href)
      ElMessage.success('导出成功')
    })
    .catch(() => {})
}

// 审核按钮操作
const handleApprove = (row?: EngineeringBom) => {
  const bomId = row?.bomId || ids.value[0]
  if (!bomId) {
    ElMessage.warning('请选择要审核的BOM')
    return
  }

  selectedBomForApprove.value = bomId
  bomApproveOpen.value = true
}

// 查看详情按钮操作
const handleView = (row: EngineeringBom) => {
  selectedBomId.value = row.bomId
  bomDetailOpen.value = true
}

// 复制BOM按钮操作
const handleCopyBom = (row: EngineeringBom) => {
  const bomId = row.bomId
  ElMessageBox.confirm('是否确认复制此BOM？', '提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'info',
  })
    .then(() => {
      // 由于没有copyEngineeringBom API，我们可以通过获取现有BOM信息然后创建新BOM来实现
      return productBomApi.getEngineeringBomInfo(bomId)
    })
    .then((response: any) => {
      const bomData = response.data
      // 移除ID并创建新BOM
      bomData.bomId = undefined
      bomData.bomCode = ''
      bomData.bomVersion = `${bomData.bomVersion}_copy`
      return productBomApi.addEngineeringBom(bomData)
    })
    .then(() => {
      getList()
      ElMessage.success('复制成功')
    })
    .catch(() => {})
}

// 审核成功处理
const handleApproveSuccess = (result: any) => {
  ElMessage.success(
    `BOM审核${result.approveResult === ProductActions.APPROVE ? '通过' : '驳回'}成功`
  )
  getList() // 刷新列表
}

// 审核对话框关闭处理
const handleApproveClose = () => {
  selectedBomForApprove.value = undefined
}

// 监听详情对话框关闭事件，重置selectedBomId
watch(
  () => bomDetailOpen.value,
  (newValue) => {
    if (!newValue) {
      // 对话框关闭时重置selectedBomId
      selectedBomId.value = undefined
    }
  }
)

// 监听审核对话框关闭事件
watch(
  () => bomApproveOpen.value,
  (newValue) => {
    if (!newValue) {
      // 对话框关闭时重置selectedBomForApprove
      selectedBomForApprove.value = undefined
    }
  }
)

// 组件挂载时获取数据
onMounted(() => {
  getList()
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
