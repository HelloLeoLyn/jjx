<template>
  <div class="order-export">
    <el-card>
      <template #header>
        <span>订购单导出</span>
      </template>

      <el-form :model="orderForm" label-width="120px" size="default">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="订单号码">
              <el-input v-model="orderForm.orderNo" placeholder="请输入订单号码" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="订货时间">
              <el-date-picker
                v-model="orderForm.orderDate"
                type="date"
                placeholder="选择日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="交货时间">
              <el-date-picker
                v-model="orderForm.deliveryDate"
                type="date"
                placeholder="选择日期"
                format="YYYY-MM-DD"
                value-format="YYYY-MM-DD"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="交易方式">
              <el-radio-group v-model="orderForm.tradeType">
                <el-radio value="RMB">RMB 现结</el-radio>
                <el-radio value="monthly">月结</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="厂商">
          <el-input v-model="orderForm.supplierName" placeholder="请输入厂商名称" />
        </el-form-item>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="联系人">
              <el-input v-model="orderForm.supplierContact" placeholder="请输入联系人" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="电话">
              <el-input v-model="orderForm.supplierTel" placeholder="请输入电话" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 订单明细表格 -->
        <el-form-item label="订单明细">
          <el-table :data="orderForm.items" border stripe>
            <el-table-column type="index" label="项次" width="60" />
            <el-table-column prop="materialName" label="品名" min-width="150">
              <template #default="{ row }">
                <el-input v-model="row.materialName" size="small" />
              </template>
            </el-table-column>
            <el-table-column prop="specification" label="规格" min-width="150">
              <template #default="{ row }">
                <el-input v-model="row.specification" size="small" />
              </template>
            </el-table-column>
            <el-table-column prop="unit" label="单位" width="80">
              <template #default="{ row }">
                <el-input v-model="row.unit" size="small" />
              </template>
            </el-table-column>
            <el-table-column prop="quantity" label="数量" width="100">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.quantity"
                  :min="0"
                  size="small"
                  controls-position="right"
                />
              </template>
            </el-table-column>
            <el-table-column prop="unitPrice" label="单价" width="120">
              <template #default="{ row }">
                <el-input-number
                  v-model="row.unitPrice"
                  :min="0"
                  :precision="2"
                  size="small"
                  controls-position="right"
                />
              </template>
            </el-table-column>
            <el-table-column prop="amount" label="金额" width="120">
              <template #default="{ row }">
                {{ (row.quantity * row.unitPrice).toFixed(2) }}
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="120">
              <template #default="{ row }">
                <el-input v-model="row.remark" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="60">
              <template #default="{ $index }">
                <el-button type="danger" size="small" text @click="removeItem($index)"
                  >删除</el-button
                >
              </template>
            </el-table-column>
          </el-table>
          <el-button type="primary" text style="margin-top: 10px" @click="addItem">
            <el-icon><Plus /></el-icon>
            添加明细
          </el-button>
        </el-form-item>

        <!-- 合计 -->
        <el-form-item label="合计金额">
          <el-input v-model="totalAmount" disabled />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :loading="exporting" @click="handleExport">
            <el-icon><Download /></el-icon>
            导出Excel
          </el-button>
          <el-button @click="resetForm">清空</el-button>
          <el-button @click="loadMockData">加载示例数据</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Download } from '@element-plus/icons-vue'
import orderApi from '../index'
import type { OrderData, OrderItem } from '../index'

// 默认 mock 数据
const mockData: OrderData = {
  orderNo: 'PO-2026-0001',
  orderDate: '2026-04-30',
  deliveryDate: '2026-05-15',
  supplierName: '深圳市华强电子科技有限公司',
  supplierContact: '张经理',
  supplierTel: '0755-88886666',
  tradeType: 'RMB',
  items: [
    {
      itemNo: 1,
      materialName: '贴片电阻 0805 10KΩ ±1%',
      specification: '0805 10KΩ 1%',
      unit: '个',
      quantity: 5000,
      unitPrice: 0.05,
      amount: 250,
      remark: 'Samsung',
    },
    {
      itemNo: 2,
      materialName: '贴片电容 0805 100nF ±10% 50V',
      specification: '0805 104K 50V',
      unit: '个',
      quantity: 3000,
      unitPrice: 0.08,
      amount: 240,
      remark: 'Murata',
    },
    {
      itemNo: 3,
      materialName: 'STM32F103C8T6 微控制器',
      specification: 'LQFP-48',
      unit: '片',
      quantity: 200,
      unitPrice: 8.5,
      amount: 1700,
      remark: 'ST',
    },
    {
      itemNo: 4,
      materialName: 'AMS1117-3.3 稳压器',
      specification: 'SOT-223',
      unit: '片',
      quantity: 500,
      unitPrice: 0.35,
      amount: 175,
      remark: '',
    },
  ],
  totalAmount: 2365,
}

// 表单数据
const orderForm = ref<OrderData>(JSON.parse(JSON.stringify(mockData)))

const exporting = ref(false)

// 计算合计金额
const totalAmount = computed(() => {
  const sum = orderForm.value.items.reduce((total, item) => {
    return total + item.quantity * item.unitPrice
  }, 0)
  orderForm.value.totalAmount = sum
  return sum.toFixed(2)
})

// 添加明细
const addItem = () => {
  orderForm.value.items.push({
    itemNo: orderForm.value.items.length + 1,
    materialName: '',
    specification: '',
    unit: '',
    quantity: 0,
    unitPrice: 0,
    amount: 0,
    remark: '',
  })
}

// 删除明细
const removeItem = (index: number) => {
  orderForm.value.items.splice(index, 1)
}

// 清空表单
const resetForm = () => {
  orderForm.value = {
    orderNo: '',
    orderDate: '',
    deliveryDate: '',
    supplierName: '',
    supplierContact: '',
    supplierTel: '',
    tradeType: 'RMB',
    items: [],
    totalAmount: 0,
  }
}

// 加载示例数据
const loadMockData = () => {
  orderForm.value = JSON.parse(JSON.stringify(mockData))
}

// 导出Excel
const handleExport = async () => {
  if (!orderForm.value.orderNo) {
    ElMessage.warning('请输入订单号码')
    return
  }
  if (!orderForm.value.supplierName) {
    ElMessage.warning('请输入厂商名称')
    return
  }
  if (orderForm.value.items.length === 0) {
    ElMessage.warning('请添加订单明细')
    return
  }

  exporting.value = true
  try {
    const res = await orderApi.exportExcel(orderForm.value)

    // 创建Blob并下载
    const blob = new Blob([res as BlobPart], {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })
    const link = document.createElement('a')
    const url = URL.createObjectURL(blob)
    link.href = url
    link.download = `订购单_${orderForm.value.orderNo}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(url)

    ElMessage.success('导出成功')
  } catch (error) {
    console.error('导出失败:', error)
    ElMessage.error('导出失败')
  } finally {
    exporting.value = false
  }
}
</script>

<style scoped>
.order-export {
  padding: 20px;
}

:deep(.el-input-number) {
  width: 100%;
}
</style>
