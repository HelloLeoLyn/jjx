<template>
  <el-card class="search-card" shadow="never">
    <el-form ref="formRef" :model="queryParams" :inline="true" label-width="80px">
      <el-form-item label="路线编码" prop="routingCode">
        <el-input
          v-model="queryParams.routingCode"
          placeholder="请输入路线编码"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="路线名称" prop="routingName">
        <el-input
          v-model="queryParams.routingName"
          placeholder="请输入路线名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="产品名称" prop="productId">
        <el-input
          v-model="queryParams.productId"
          placeholder="请选择产品"
          clearable
          style="width: 200px"
        />
      </el-form-item>
      <el-form-item label="审核状态" prop="approveStatus">
        <el-select
          v-model="queryParams.approveStatus"
          placeholder="请选择审核状态"
          clearable
          style="width: 150px"
        >
          <el-option
            v-for="item in approveStatusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="当前版本" prop="isCurrent">
        <el-select
          v-model="queryParams.isCurrent"
          placeholder="请选择"
          clearable
          style="width: 120px"
        >
          <el-option label="是" :value="1" />
          <el-option label="否" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
        <el-button icon="Refresh" @click="resetQuery">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import type { FormInstance } from 'element-plus'
import type { ProductQueryParams } from '@/types/product'
// ProductRouteQueryParams 改为使用 ProductQueryParams

const emit = defineEmits<{
  search: [params: ProductQueryParams]
  reset: []
}>()

const formRef = ref<FormInstance>()

const approveStatusOptions = [
  { value: 1, label: '草稿' },
  { value: 2, label: '待审批' },
  { value: 3, label: '已批准' },
  { value: 4, label: '已拒绝' },
]

const queryParams = reactive<any>({
  pageNum: 1,
  pageSize: 10,
  routingCode: undefined,
  routingName: undefined,
  productId: undefined,
  productCode: undefined,
  approveStatus: undefined,
  isCurrent: undefined,
  orderByColumn: 'createTime',
  isAsc: 'desc',
})

const handleQuery = () => {
  queryParams.pageNum = 1
  emit('search', { ...queryParams })
}

const resetQuery = () => {
  if (formRef.value) {
    formRef.value.resetFields()
  }
  Object.assign(queryParams, {
    pageNum: 1,
    pageSize: 10,
    routingCode: undefined,
    routingName: undefined,
    productId: undefined,
    productCode: undefined,
    approveStatus: undefined,
    isCurrent: undefined,
    orderByColumn: 'createTime',
    isAsc: 'desc',
  })
  emit('reset')
}
</script>

<style scoped>
.search-card {
  margin-bottom: 20px;
}
</style>
