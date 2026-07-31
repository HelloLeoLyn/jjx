<template>
  <div class="stocktake-list">
    <!-- 搜索栏 -->
    <el-card class="search-card">
      <el-form :model="queryParams" :inline="true">
        <el-form-item label="盘点单号">
          <el-input
            v-model="queryParams.stocktakeNo"
            placeholder="请输入盘点单号"
            clearable
            @keyup.enter="handleQuery"
          />
        </el-form-item>
        <el-form-item label="盘点类型">
          <el-select
            v-model="queryParams.stocktakeType"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="全盘" value="full" />
            <el-option label="抽盘" value="partial" />
            <el-option label="循环盘点" value="cycle" />
          </el-select>
        </el-form-item>
        <el-form-item label="仓库">
          <el-select
            v-model="queryParams.warehouseId"
            placeholder="请选择仓库"
            clearable
            style="width: 150px"
          >
            <el-option label="原材料仓库" value="1" />
            <el-option label="成品仓库" value="2" />
            <el-option label="半成品仓库" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select
            v-model="queryParams.status"
            placeholder="请选择"
            clearable
            style="width: 120px"
          >
            <el-option label="待盘点" :value="0" />
            <el-option label="盘点中" :value="4" />
            <el-option label="已完成" :value="11" />
            <el-option label="已取消" :value="9" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleQuery">搜索</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 操作栏 -->
    <el-card class="operation-card">
      <el-row :gutter="10">
        <el-col :span="1.5">
          <el-button type="primary" @click="handleCreate">
            <el-icon><Plus /></el-icon>新建盘点单
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button @click="handleExport">
            <el-icon><Download /></el-icon>导出
          </el-button>
        </el-col>
        <el-col :span="1.5">
          <el-button @click="handleRefresh">
            <el-icon><Refresh /></el-icon>刷新
          </el-button>
        </el-col>
      </el-row>
    </el-card>

    <!-- 表格 -->
    <el-card class="table-card">
      <el-table v-loading="loading" :data="stocktakeList" border style="width: 100%">
        <el-table-column label="盘点单号" prop="stocktakeNo" width="150" />
        <el-table-column label="盘点类型" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStocktakeTypeTag(row.stocktakeType)" size="small">
              {{ row.stocktakeTypeName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="仓库" prop="warehouseName" width="120" />
        <el-table-column label="盘点物料数" prop="materialCount" width="100" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.materialCount) }}
          </template>
        </el-table-column>
        <el-table-column label="差异物料数" prop="differenceCount" width="100" align="right">
          <template #default="{ row }">
            <span v-if="row.differenceCount > 0" style="color: #f56c6c">
              {{ formatNumber(row.differenceCount) }}
            </span>
            <span v-else>
              {{ formatNumber(row.differenceCount) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="差异金额" prop="differenceAmount" width="120" align="right">
          <template #default="{ row }">
            <span v-if="row.differenceAmount > 0" style="color: #f56c6c">
              ¥ {{ formatCurrency(row.differenceAmount) }}
            </span>
            <span v-else-if="row.differenceAmount < 0" style="color: #67c23a">
              ¥ {{ formatCurrency(row.differenceAmount) }}
            </span>
            <span v-else> ¥ {{ formatCurrency(row.differenceAmount) }} </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusTag(row.status)" size="small">
              {{ row.statusName }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="创建人" prop="createBy" width="100" />
        <el-table-column label="创建时间" prop="createTime" width="150" align="center" />
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="handleView(row)">详情</el-button>
            <el-button v-if="row.status === 0" link type="primary" @click="handleEdit(row)"
              >编辑</el-button
            >
            <el-button v-if="row.status === 0" link type="success" @click="handleStart(row)"
              >开始盘点</el-button
            >
            <el-button
              v-if="row.status === 4"
              link
              type="warning"
              @click="handleComplete(row)"
              >完成盘点</el-button
            >
            <el-button v-if="row.status === 11" link type="info" @click="handleAdjust(row)"
              >生成调整单</el-button
            >
          </template>
        </el-table-column>
      </el-table>

      <pagination
        v-show="total > 0"
        :total="total"
        v-model:page="queryParams.current"
        v-model:limit="queryParams.pageSize"
        @pagination="getList"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'StocktakeList',
})

import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Plus, Download, Refresh } from '@element-plus/icons-vue'
import { formatCurrency, formatNumber } from '@/utils/format'

const router = useRouter()

// 查询参数
const queryParams = reactive({
  current: 1,
  pageSize: 10,
  stocktakeNo: '',
  stocktakeType: '',
  warehouseId: '',
  status: '',
})

// 响应式数据
const loading = ref(false)
const stocktakeList = ref<any[]>([])
const total = ref(0)

// 模拟数据
const mockStocktakeData = [
  {
    stocktakeId: '1',
    stocktakeNo: 'ST20240328001',
    stocktakeType: 'full',
    stocktakeTypeName: '全盘',
    warehouseId: '1',
    warehouseName: '原材料仓库',
    materialCount: 50,
    differenceCount: 3,
    differenceAmount: 1500,
    status: 11,
    statusName: '已完成',
    createBy: '张三',
    createTime: '2024-03-28 10:00:00',
  },
  {
    stocktakeId: '2',
    stocktakeNo: 'ST20240328002',
    stocktakeType: 'partial',
    stocktakeTypeName: '抽盘',
    warehouseId: '2',
    warehouseName: '成品仓库',
    materialCount: 30,
    differenceCount: 0,
    differenceAmount: 0,
    status: 4,
    statusName: '盘点中',
    createBy: '李四',
    createTime: '2024-03-28 11:00:00',
  },
  {
    stocktakeId: '3',
    stocktakeNo: 'ST20240328003',
    stocktakeType: 'cycle',
    stocktakeTypeName: '循环盘点',
    warehouseId: '3',
    warehouseName: '半成品仓库',
    materialCount: 20,
    differenceCount: 1,
    differenceAmount: -500,
    status: 0,
    statusName: '待盘点',
    createBy: '王五',
    createTime: '2024-03-28 12:00:00',
  },
]

// 获取盘点单列表
const getList = async () => {
  loading.value = true
  try {
    // 模拟API调用
    await new Promise((resolve) => setTimeout(resolve, 500))

    // 过滤数据
    let filteredData = [...mockStocktakeData]

    if (queryParams.stocktakeNo) {
      filteredData = filteredData.filter((item) =>
        item.stocktakeNo.includes(queryParams.stocktakeNo)
      )
    }

    if (queryParams.stocktakeType) {
      filteredData = filteredData.filter((item) => item.stocktakeType === queryParams.stocktakeType)
    }

    if (queryParams.status) {
      filteredData = filteredData.filter((item) => item.status === queryParams.status)
    }

    // 模拟分页
    const start = (queryParams.current - 1) * queryParams.pageSize
    const end = start + queryParams.pageSize
    stocktakeList.value = filteredData.slice(start, end)
    total.value = filteredData.length
  } catch (error) {
    console.error('获取盘点单列表失败:', error)
    ElMessage.error('获取盘点单列表失败')
  } finally {
    loading.value = false
  }
}

// 搜索
const handleQuery = () => {
  queryParams.current = 1
  getList()
}

// 重置
const handleReset = () => {
  queryParams.current = 1
  queryParams.stocktakeNo = ''
  queryParams.stocktakeType = ''
  queryParams.warehouseId = ''
  queryParams.status = ''
  getList()
}

// 新建盘点单
const handleCreate = () => {
  router.push('/inventory/stocktake/create')
}

// 导出
const handleExport = () => {
  ElMessage.info('导出功能开发中')
}

// 刷新
const handleRefresh = () => {
  getList()
  ElMessage.success('数据已刷新')
}

// 查看详情
const handleView = (row: any) => {
  router.push(`/inventory/stocktake/detail/${row.stocktakeId}`)
}

// 编辑盘点单
const handleEdit = (row: any) => {
  router.push(`/inventory/stocktake/edit/${row.stocktakeId}`)
}

// 开始盘点
const handleStart = (row: any) => {
  ElMessage.success(`开始盘点: ${row.stocktakeNo}`)
  getList()
}

// 完成盘点
const handleComplete = (row: any) => {
  ElMessage.success(`完成盘点: ${row.stocktakeNo}`)
  getList()
}

// 生成调整单
const handleAdjust = (row: any) => {
  ElMessage.success(`生成调整单: ${row.stocktakeNo}`)
}

// 获取盘点类型标签样式
const getStocktakeTypeTag = (
  type: string
): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const typeMap: Record<string, 'success' | 'warning' | 'info' | 'danger' | undefined> = {
    full: 'success',
    partial: 'warning',
    cycle: 'info',
  }
  return typeMap[type]
}

// 获取状态标签样式
const getStatusTag = (status: number): 'success' | 'warning' | 'info' | 'danger' | undefined => {
  const statusMap: Record<number, 'success' | 'warning' | 'info' | 'danger' | undefined> = {
    0: 'info',    // draft
    4: 'warning', // processing
    5: 'success', // confirmed
    11: 'success', // processed
    9: 'danger',  // cancelled
  }
  return statusMap[status]
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.stocktake-list {
  padding: 20px;
}

.search-card,
.operation-card,
.table-card {
  margin-bottom: 16px;
}
</style>
