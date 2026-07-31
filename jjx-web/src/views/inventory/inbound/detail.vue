<template>
  <div class="inbound-detail">
    <el-card class="header-card">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <h2>入库单详情 - {{ inboundData.inboundNo }}</h2>
            <enum-tag :enum-obj="InboundEnum.orderStatus" :value="inboundData.status" />
          </div>
          <div class="header-actions">
            <el-button @click="handleBack">返回</el-button>
            <el-button v-if="inboundData.status === 0" type="primary" @click="handleEdit">
              编辑
            </el-button>
            <el-button v-if="inboundData.status === 0" type="success" @click="handleSubmit">
              提交审批
            </el-button>
            <el-button
              v-if="inboundData.status === 1"
              type="success"
              @click="handleApprove"
            >
              审批通过
            </el-button>
            <el-button
              v-if="inboundData.status === 2"
              type="warning"
              @click="handleConfirm"
            >
              确认入库
            </el-button>
            <el-button
              v-if="inboundData.status === 0 || inboundData.status === 1"
              type="danger"
              @click="handleCancel"
            >
              取消
            </el-button>
          </div>
        </div>
      </template>

      <!-- 基本信息 -->
      <el-descriptions :column="3" border>
        <el-descriptions-item label="入库单号">
          {{ inboundData.inboundNo }}
        </el-descriptions-item>
        <el-descriptions-item label="入库类型">
          <enum-tag :enum-obj="InboundEnum.type" :value="inboundData.inboundType" />
        </el-descriptions-item>
        <el-descriptions-item label="仓库">
          {{ inboundData.warehouseName }}
        </el-descriptions-item>
        <el-descriptions-item label="供应商">
          {{ inboundData.supplierName || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="总数量">
          {{ formatNumber(inboundData.totalQuantity) }}
        </el-descriptions-item>
        <el-descriptions-item label="总金额">
          ¥ {{ formatCurrency(inboundData.totalAmount) }}
        </el-descriptions-item>
        <el-descriptions-item label="来源类型">
          {{ getSourceTypeName(inboundData.sourceType) || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="来源单号">
          {{ inboundData.sourceNo || inboundData.sourceId || '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="创建人">
          {{ inboundData.createBy }}
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ inboundData.createTime }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">
          {{ inboundData.updateTime }}
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="3">
          {{ inboundData.remark || '-' }}
        </el-descriptions-item>
      </el-descriptions>
    </el-card>

    <!-- 入库明细 -->
    <el-card class="detail-card">
      <template #header>
        <div class="detail-header">
          <h3>入库明细</h3>
          <div class="detail-summary">
            <span>物料种类：{{ inboundData.items?.length || 0 }}</span>
            <span>总数量：{{ formatNumber(inboundData.totalQuantity) }}</span>
            <span>总金额：¥ {{ formatCurrency(inboundData.totalAmount) }}</span>
          </div>
        </div>
      </template>

      <el-table :data="inboundData.items" border style="width: 100%">
        <el-table-column label="序号" width="60" align="center">
          <template #default="{ $index }">
            {{ $index + 1 }}
          </template>
        </el-table-column>
        <el-table-column label="物料编码" prop="materialCode" width="120" />
        <el-table-column label="物料名称" prop="materialName" width="150" show-overflow-tooltip />
        <el-table-column label="规格型号" prop="specification" width="120" show-overflow-tooltip />
        <el-table-column label="单位" prop="unit" width="80" align="center" />
        <el-table-column label="批次号" prop="batchNo" width="120" />
        <el-table-column label="数量" prop="quantity" width="100" align="right">
          <template #default="{ row }">
            {{ formatNumber(row.quantity) }}
          </template>
        </el-table-column>
        <el-table-column label="单价" prop="unitPrice" width="100" align="right">
          <template #default="{ row }"> ¥ {{ formatCurrency(row.unitPrice) }} </template>
        </el-table-column>
        <el-table-column label="金额" prop="amount" width="120" align="right">
          <template #default="{ row }"> ¥ {{ formatCurrency(row.amount) }} </template>
        </el-table-column>
        <el-table-column label="库位" prop="locationCode" width="100" />
        <el-table-column label="生产日期" prop="productionDate" width="110" align="center" />
        <el-table-column label="到期日期" prop="expiryDate" width="110" align="center" />
        <el-table-column label="备注" prop="remark" width="120" />
      </el-table>
    </el-card>

    <!-- 操作日志 -->
    <el-card class="log-card" v-if="operationLogs.length > 0">
      <template #header>
        <h3>操作日志</h3>
      </template>
      <el-timeline>
        <el-timeline-item
          v-for="log in operationLogs"
          :key="log.id"
          :timestamp="log.time"
          :type="getLogType(log.type)"
        >
          <div class="log-item">
            <div class="log-content">
              <span class="log-operator">{{ log.operator }}</span>
              <span class="log-action">{{ log.action }}</span>
              <span class="log-detail" v-if="log.detail">{{ log.detail }}</span>
            </div>
          </div>
        </el-timeline-item>
      </el-timeline>
    </el-card>
  </div>
</template>

<script setup lang="ts">
defineOptions({
  name: 'InboundDetail',
})

import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { inboundApi } from '@/api/inventory/inbound'
import { formatCurrency, formatNumber } from '@/utils/format'
import type { InboundVO } from '@/types/inventory/inbound'
import { InboundEnum } from '@/enums/inventory'

const route = useRoute()
const router = useRouter()

const inboundId = ref<string>('')
const inboundData = ref<InboundVO>({
  inboundId: '',
  inboundNo: '',
  inboundType: '',
  inboundTypeName: '',
  warehouseId: '',
  warehouseName: '',
  supplierId: '',
  supplierName: '',
  sourceType: '',
  sourceId: '',
  sourceNo: '',
  totalQuantity: 0,
  totalAmount: 0,
  status: '',
  statusName: '',
  remark: '',
  createBy: '',
  createTime: '',
  updateTime: '',
  items: [],
})

const operationLogs = ref<any[]>([
  {
    id: '1',
    time: '2026-03-30 10:30:00',
    operator: '张三',
    action: '创建入库单',
    type: 'create',
    detail: '创建入库单草稿',
  },
  {
    id: '2',
    time: '2026-03-30 11:15:00',
    operator: '张三',
    action: '提交审批',
    type: 'submit',
    detail: '提交入库单审批',
  },
])

// 获取入库单详情
const loadInboundDetail = async () => {
  if (!inboundId.value) return

  try {
    const res = await inboundApi.getById(inboundId.value)
    if (res.data) {
      inboundData.value = res.data
    } else {
      ElMessage.error('未找到入库单详情')
    }
  } catch (error) {
    console.error('获取入库单详情失败:', error)
    ElMessage.error('获取入库单详情失败')
  }
}

// 获取来源类型名称
const getSourceTypeName = (sourceType?: string): string => {
  if (!sourceType) return '-'
  return InboundEnum.sourceType.getLabel(sourceType)
}

// 获取日志类型
const getLogType = (type: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' => {
  const typeMap: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'info'> = {
    create: 'primary',
    submit: 'warning',
    approve: 'success',
    reject: 'danger',
    confirm: 'success',
    cancel: 'danger',
  }
  return typeMap[type] || 'info'
}

// 返回
const handleBack = () => {
  router.push('/inventory/inbound')
}

// 编辑
const handleEdit = () => {
  router.push(`/inventory/inbound/edit/${inboundId.value}`)
}

// 提交审批
const handleSubmit = async () => {
  try {
    await ElMessageBox.confirm('确认提交审批吗？', '提示', { type: 'warning' })
    const res = await inboundApi.submitApprove(inboundId.value)
    if (res.data) {
      ElMessage.success('提交审批成功')
      loadInboundDetail()
    } else {
      ElMessage.error('提交审批失败')
    }
  } catch (error) {
    console.error('提交审批失败:', error)
  }
}

// 审批通过
const handleApprove = async () => {
  try {
    const { value } = await ElMessageBox.prompt('请输入审批备注', '审批通过', {
      confirmButtonText: '通过',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入备注（可选）',
    })

    // 这里需要获取当前用户信息，暂时使用模拟数据
    const currentUser = {
      id: '1',
      name: '当前用户',
    }

    const res = await inboundApi.approve({
      inboundId: inboundId.value,
      approverId: currentUser.id,
      approverName: currentUser.name,
      remark: value || '',
    })

    if (res.data) {
      ElMessage.success('审批通过成功')
      loadInboundDetail()
    } else {
      ElMessage.error('审批通过失败')
    }
  } catch (error) {
    console.error('审批通过失败:', error)
  }
}

// 确认入库
const handleConfirm = async () => {
  try {
    await ElMessageBox.confirm('确认入库吗？', '提示', { type: 'warning' })

    // 这里需要获取当前用户信息，暂时使用模拟数据
    const currentUser = {
      id: '1',
      name: '当前用户',
    }

    const res = await inboundApi.confirm(inboundId.value, currentUser.id, currentUser.name)
    if (res.data) {
      ElMessage.success('确认入库成功')
      loadInboundDetail()
    } else {
      ElMessage.error('确认入库失败')
    }
  } catch (error) {
    console.error('确认入库失败:', error)
  }
}

// 取消入库单
const handleCancel = async () => {
  try {
    const { value } = await ElMessageBox.prompt('请输入取消原因', '取消入库单', {
      confirmButtonText: '确认取消',
      cancelButtonText: '取消',
      inputPlaceholder: '请输入取消原因',
      inputValidator: (value) => {
        if (!value) {
          return '取消原因不能为空'
        }
        return true
      },
    })

    const res = await inboundApi.cancel(inboundId.value, value)
    if (res.data) {
      ElMessage.success('取消成功')
      loadInboundDetail()
    } else {
      ElMessage.error('取消失败')
    }
  } catch (error) {
    console.error('取消失败:', error)
  }
}

onMounted(() => {
  inboundId.value = route.params.id as string
  loadInboundDetail()
})
</script>

<style scoped>
.inbound-detail {
  padding: 20px;
}

.header-card,
.detail-card,
.log-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 15px;
}

.header-actions {
  display: flex;
  gap: 10px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.detail-summary {
  display: flex;
  gap: 20px;
  color: #606266;
  font-size: 14px;
}

.log-item {
  padding: 5px 0;
}

.log-content {
  display: flex;
  align-items: center;
  gap: 8px;
}

.log-operator {
  color: #409eff;
  font-weight: bold;
}

.log-action {
  color: #303133;
}

.log-detail {
  color: #909399;
  font-style: italic;
}
</style>
