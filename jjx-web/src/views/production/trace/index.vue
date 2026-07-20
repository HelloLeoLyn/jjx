<template>
  <div class="app-container">
    <!-- 搜索表单 -->
    <el-card class="search-card" shadow="never">
      <el-form :model="queryParams" :inline="true" label-width="100px">
        <el-form-item label="追溯编码">
          <el-input v-model="queryParams.traceCode" placeholder="物料编码/工单号/产品编码" clearable style="width:200px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="追溯类型">
          <el-select v-model="queryParams.traceType" placeholder="请选择" clearable style="width:150px">
            <el-option label="原料追溯" value="MATERIAL" />
            <el-option label="工单追溯" value="ORDER" />
            <el-option label="产品追溯" value="PRODUCT" />
          </el-select>
        </el-form-item>
        <el-form-item label="批次号">
          <el-input v-model="queryParams.batchNo" placeholder="请输入批次号" clearable style="width:200px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">查询</el-button>
          <el-button icon="Refresh" @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 追溯操作 -->
    <el-card class="mt-16" shadow="never">
      <template #header>
        <div class="card-header">
          <span>🔍 追溯查询</span>
        </div>
      </template>
      <el-form :inline="true">
        <el-form-item label="输入追溯码">
          <el-input v-model="traceCode" placeholder="输入物料编码/工单号/产品编码" style="width:300px" />
        </el-form-item>
        <el-form-item>
          <el-button type="success" icon="Top" @click="doTraceForward">正追溯 →</el-button>
          <el-button type="warning" icon="Bottom" @click="doTraceBackward">反追溯 ←</el-button>
        </el-form-item>
      </el-form>

      <!-- 追溯结果 -->
      <el-timeline v-if="traceResults.length > 0">
        <el-timeline-item
          v-for="item in traceResults"
          :key="item.traceId"
          :timestamp="item.operateTime"
          :color="timelineColor(item.operation)"
          placement="top"
        >
          <div class="trace-item">
            <div class="trace-item-header">
              <el-tag :type="tagType(item.operation)" size="small">{{ item.operationName }}</el-tag>
              <span class="trace-item-type">{{ item.traceTypeName }}</span>
            </div>
            <div class="trace-item-body">
              <span class="trace-item-code">编码: {{ item.traceCode }}</span>
              <span v-if="item.batchNo" class="trace-item-batch">批次: {{ item.batchNo }}</span>
              <span v-if="item.operator" class="trace-item-operator">操作人: {{ item.operator }}</span>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else-if="traced" description="未找到追溯记录" :image-size="60" />
    </el-card>

    <!-- 数据表格 -->
    <el-card class="mt-16" shadow="never">
      <template #header>
        <div class="card-header">
          <span>📋 追溯日志</span>
        </div>
      </template>
      <el-table v-loading="loading" :data="logList" stripe>
        <el-table-column label="追溯编码" prop="traceCode" width="180" />
        <el-table-column label="类型" prop="traceTypeName" width="100" />
        <el-table-column label="操作" prop="operationName" width="80" />
        <el-table-column label="批次号" prop="batchNo" width="150" />
        <el-table-column label="工单ID" prop="orderId" width="80" />
        <el-table-column label="操作人" prop="operator" width="100" />
        <el-table-column label="操作时间" prop="operateTime" width="180" />
        <el-table-column label="详情" prop="detail" min-width="200" show-overflow-tooltip />
      </el-table>
      <el-pagination
        v-model:current-page="queryParams.pageNum"
        v-model:page-size="queryParams.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="getList"
        @current-change="getList"
      />
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { traceApi, type TraceQuery, type TraceVO } from '@/api/production/trace'
import { Search, Refresh, Top, Bottom } from '@element-plus/icons-vue'

const loading = ref(false)
const total = ref(0)
const logList = ref<TraceVO[]>([])
const traceResults = ref<TraceVO[]>([])
const traceCode = ref('')
const traced = ref(false)

const queryParams = reactive<TraceQuery>({
  pageNum: 1,
  pageSize: 10,
})

const getList = async () => {
  loading.value = true
  try {
    const res = await traceApi.page(queryParams)
    if (res.data) {
      logList.value = res.data.records || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    console.error('查询追溯日志失败', e)
  } finally {
    loading.value = false
  }
}

const handleQuery = () => {
  queryParams.pageNum = 1
  getList()
}

const handleReset = () => {
  queryParams.traceCode = ''
  queryParams.traceType = ''
  queryParams.batchNo = ''
  handleQuery()
}

const doTraceForward = async () => {
  if (!traceCode.value) {
    ElMessage.warning('请输入追溯编码')
    return
  }
  traced.value = true
  try {
    const res = await traceApi.traceForward(traceCode.value)
    traceResults.value = res.data || []
    if (traceResults.value.length === 0) {
      ElMessage.info('未找到追溯记录')
    }
  } catch (e) {
    console.error('追溯查询失败', e)
  }
}

const doTraceBackward = async () => {
  if (!traceCode.value) {
    ElMessage.warning('请输入追溯编码')
    return
  }
  traced.value = true
  try {
    const res = await traceApi.traceBackward(traceCode.value)
    traceResults.value = res.data || []
    if (traceResults.value.length === 0) {
      ElMessage.info('未找到追溯记录')
    }
  } catch (e) {
    console.error('追溯查询失败', e)
  }
}

const timelineColor = (op: string) => {
  const colors: Record<string, string> = {
    inbound: '#67c23a',
    outbound: '#f56c6c',
    start: '#409eff',
    complete: '#67c23a',
    inspect: '#e6a23c',
  }
  return colors[op] || '#909399'
}

const tagType = (op: string) => {
  const types: Record<string, string> = {
    inbound: 'success',
    outbound: 'danger',
    start: 'primary',
    complete: 'success',
    inspect: 'warning',
  }
  return (types[op] || 'info') as any
}

onMounted(() => {
  getList()
})
</script>

<style scoped>
.app-container { padding: 0; }
.mt-16 { margin-top: 16px; }
.search-card { border-radius: 12px; border: 1px solid #e8eaef; }
.card-header {
  display: flex; align-items: center; justify-content: space-between;
  font-size: 14px; font-weight: 600;
}
.trace-item { padding: 8px 0; }
.trace-item-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.trace-item-type { font-size: 12px; color: #909399; }
.trace-item-body { font-size: 13px; color: #606266; display: flex; gap: 16px; flex-wrap: wrap; }
</style>
</template>
