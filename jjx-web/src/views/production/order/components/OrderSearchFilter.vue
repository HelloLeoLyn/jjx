<template>
  <div class="search-filter">
    <el-card shadow="never" class="filter-card">
      <el-form :model="searchForm" label-width="80px">
        <el-row :gutter="20">
          <el-col :span="6">
            <el-form-item label="订单编号">
              <el-input
                v-model="searchForm.orderNo"
                placeholder="请输入订单编号"
                clearable
                @keyup.enter="handleSearch"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="产品名称">
              <el-input
                v-model="searchForm.productName"
                placeholder="请输入产品名称"
                clearable
                @keyup.enter="handleSearch"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="订单状态">
              <el-select
                v-model="searchForm.orderStatus"
                placeholder="请选择状态"
                clearable
                :disabled="!statusOptions.length"
              >
                <el-option
                  v-for="status in statusOptions"
                  :key="status.value"
                  :label="status.label"
                  :value="status.value"
                />
              </el-select>
            </el-form-item>
          </el-col>

          <el-col :span="6">
            <el-form-item label="销售订单">
              <el-input
                v-model="searchForm.salesOrderNo"
                placeholder="请输入销售订单编号"
                clearable
                @keyup.enter="handleSearch"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="产品编码">
              <el-input
                v-model="searchForm.productCode"
                placeholder="请输入产品编码"
                clearable
                @keyup.enter="handleSearch"
              />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item>
              <el-button type="primary" icon="Search" @click="handleSearch" :loading="loading">
                搜索
              </el-button>
              <el-button icon="Refresh" @click="handleReset">重置</el-button>
              <el-button link @click="toggleAdvancedSearch">
                {{ showAdvancedSearch ? '收起' : '高级搜索' }}
                <el-icon>
                  <ArrowDown v-if="!showAdvancedSearch" />
                  <ArrowUp v-else />
                </el-icon>
              </el-button>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 高级搜索 -->
        <el-collapse-transition>
          <div v-show="showAdvancedSearch">
            <el-row :gutter="20">
              <el-col :span="6">
                <el-form-item label="审批状态" v-if="viewType === 'plan' || viewType === 'all'">
                  <el-select
                    v-model="searchForm.approvalStatus"
                    placeholder="请选择审批状态"
                    clearable
                  >
                    <el-option label="待审批" value="pending" />
                    <el-option label="已批准" value="approved" />
                    <el-option label="已拒绝" value="rejected" />
                    <el-option label="已取消" value="cancelled" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item
                  label="执行状态"
                  v-if="viewType === 'work_order' || viewType === 'all'"
                >
                  <el-select
                    v-model="searchForm.executionStatus"
                    placeholder="请选择执行状态"
                    clearable
                  >
                    <el-option label="未开始" value="not_started" />
                    <el-option label="进行中" value="in_progress" />
                    <el-option label="已完成" value="completed" />
                    <el-option label="已暂停" value="paused" />
                    <el-option label="已取消" value="cancelled" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="计划类型" v-if="viewType === 'plan' || viewType === 'all'">
                  <el-select v-model="searchForm.planType" placeholder="请选择计划类型" clearable>
                    <el-option label="月计划" value="monthly" />
                    <el-option label="周计划" value="weekly" />
                    <el-option label="日计划" value="daily" />
                    <el-option label="专项计划" value="special" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="计划开始">
                  <el-date-picker
                    v-model="searchForm.planDateStart"
                    type="date"
                    placeholder="选择开始日期"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
              <el-col :span="6">
                <el-form-item label="计划结束">
                  <el-date-picker
                    v-model="searchForm.planDateEnd"
                    type="date"
                    placeholder="选择结束日期"
                    value-format="YYYY-MM-DD"
                    style="width: 100%"
                  />
                </el-form-item>
              </el-col>
            </el-row>
          </div>
        </el-collapse-transition>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ArrowDown, ArrowUp } from '@element-plus/icons-vue'
import type { ProductionOrderQuery, OrderStatus } from '@/types/production/order'
import { getFilteredStatusOptions } from '../utils/orderConstants'

interface Props {
  searchForm: ProductionOrderQuery
  viewType: 'plan' | 'work_order' | 'all'
  loading?: boolean
}

interface Emits {
  (e: 'search'): void
  (e: 'reset'): void
  (e: 'update:searchForm', value: ProductionOrderQuery): void
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
})

const emit = defineEmits<Emits>()

// 状态
const showAdvancedSearch = ref(false)

// 计算属性
const statusOptions = computed(() => {
  return getFilteredStatusOptions(props.viewType)
})

// 方法
const handleSearch = () => {
  emit('search')
}

const handleReset = () => {
  emit('reset')
}

const toggleAdvancedSearch = () => {
  showAdvancedSearch.value = !showAdvancedSearch.value
}
</script>

<style scoped>
.search-filter {
  margin-bottom: 20px;
}

.filter-card {
  border: none;
}

.filter-card :deep(.el-card__body) {
  padding: 20px;
}
</style>
