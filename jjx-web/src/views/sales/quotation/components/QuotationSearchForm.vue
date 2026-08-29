<!-- views/sales/quotation/components/QuotationSearchForm.vue -->
<template>
  <el-card class="search-card" shadow="never">
    <el-form :model="localParams" :inline="true" label-width="80px">
      <el-form-item label="报价单号" prop="quotationNo">
        <el-input
          v-model="localParams.quotationNo"
          placeholder="请输入报价单号"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="询价单号" prop="inquiryNo">
        <el-input
          v-model="localParams.inquiryNo"
          placeholder="请输入来源询价单号"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="客户名称" prop="customerName">
        <el-input
          v-model="localParams.customerName"
          placeholder="请输入客户名称"
          clearable
          style="width: 200px"
          @keyup.enter="handleQuery"
        />
      </el-form-item>
      <el-form-item label="报价状态" prop="quotationStatus">
        <el-select
          v-model="localParams.quotationStatus"
          placeholder="请选择报价状态"
          clearable
          style="width: 200px"
        >
          <el-option
            v-for="item in statusOptions"
            :key="item.value"
            :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="报价日期">
        <el-date-picker
          v-model="localDateRange"
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
        <el-button icon="Refresh" @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { QuotationStatusEnum } from '@/enums/sales'

// ============================================================
// Props & Emits
// ============================================================
const props = withDefaults(
  defineProps<{
    queryParams?: any
    dateRange?: string[]
  }>(),
  {
    queryParams: () => ({}),
    dateRange: () => [],
  }
)

const emit = defineEmits<{
  (e: 'update:queryParams', value: any): void
  (e: 'update:dateRange', value: string[]): void
  (e: 'query'): void
  (e: 'reset'): void
}>()

// ============================================================
// 状态选项（内部定义，不依赖父组件）
// ============================================================
const statusOptions = QuotationStatusEnum.items.map((item) => ({
  value: item.value,
  label: item.label,
}))

// ============================================================
// 本地双向绑定
// ============================================================
const localParams = computed({
  get: () => props.queryParams || {},
  set: (val) => emit('update:queryParams', val),
})

const localDateRange = computed({
  get: () => props.dateRange || [],
  set: (val) => emit('update:dateRange', val),
})

// ============================================================
// 方法
// ============================================================
const handleQuery = () => {
  emit('query')
}

const handleReset = () => {
  // 清空查询参数
  const emptyParams: any = {}
  Object.keys(localParams.value).forEach((key) => {
    emptyParams[key] = undefined
  })
  emit('update:queryParams', emptyParams)
  emit('update:dateRange', [])
  emit('reset')
}
</script>

<style scoped>
.search-card {
  margin-bottom: 16px;
}
</style>
