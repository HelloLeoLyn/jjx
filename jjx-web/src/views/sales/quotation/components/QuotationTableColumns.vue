<!-- views/sales/quotation/components/QuotationTableColumns.vue -->
<template>
  <el-table-column type="selection" width="55" align="center" />

  <el-table-column label="报价单号" align="center" width="160">
    <template #default="{ row }">
      <el-link type="primary" underline="never" @click="emit('view', row)">
        {{ row.quotationNo }}
      </el-link>
    </template>
  </el-table-column>

  <el-table-column label="来源询价单" align="center" width="140">
    <template #default="{ row }">
      <el-link
        v-if="row.sourceInquiryNo"
        type="primary"
        underline="never"
        @click="emit('gotoInquiry', row)"
      >
        {{ row.sourceInquiryNo }}
      </el-link>
      <span v-else>-</span>
    </template>
  </el-table-column>

  <el-table-column label="客户名称" align="center" prop="customerName" width="180" />

  <el-table-column label="报价日期" align="center" prop="quotationDate" width="120">
    <template #default="{ row }">
      <span>{{ parseTime(row.quotationDate, 'yyyy-MM-dd') }}</span>
    </template>
  </el-table-column>

  <el-table-column label="有效期至" prop="validUntil" width="120">
    <template #default="{ row }">
      <span v-if="row.validUntil">{{ parseTime(row.validUntil, 'yyyy-MM-dd') }}</span>
      <span v-else>-</span>
    </template>
  </el-table-column>

  <el-table-column label="报价状态" prop="quotationStatus" width="100">
    <template #default="{ row }">
      <el-tag :type="getStatusTagType(row.quotationStatus)">
        {{ getStatusLabel(row.quotationStatus) }}
      </el-tag>
    </template>
  </el-table-column>

  <el-table-column label="订单类型" align="center" width="120">
    <template #default="{ row }">
      <el-tag v-if="row.convertedOrderType === 2" type="warning" size="small">样品单</el-tag>
      <el-tag v-else-if="row.convertedOrderType === 1" type="success" size="small">销售订单</el-tag>
      <span v-else style="color: #c0c4cc">未转单</span>
    </template>
  </el-table-column>

  <el-table-column label="币种" align="center" prop="currency" width="80" />

  <el-table-column label="总金额" align="center" prop="totalAmount" width="120">
    <template #default="{ row }">
      <span>{{ formatCurrency(row.totalAmount) }}</span>
    </template>
  </el-table-column>

  <el-table-column label="销售员" align="center" prop="salesPersonName" width="100" />

  <el-table-column label="创建时间" align="center" prop="createTime" width="180">
    <template #default="{ row }">
      <span>{{ parseTime(row.createTime) }}</span>
    </template>
  </el-table-column>

  <!-- ===== 操作列 ===== -->
  <el-table-column
    label="操作"
    align="center"
    class-name="small-padding fixed-width"
    min-width="250"
  >
    <template #default="{ row }">
      <SkeletonAction>
        <el-tooltip content="修改" placement="top" v-if="canEdit(row)">
          <el-button link type="primary" icon="Edit" @click="emit('update', row)" />
        </el-tooltip>
        <el-tooltip content="查看流水" placement="top">
          <el-button link type="info" icon="Connection" @click="emit('showTrace', row)" />
        </el-tooltip>
        <el-tooltip content="删除" placement="top" v-if="canDelete(row)">
          <el-button link type="danger" icon="Delete" @click="emit('delete', row)" />
        </el-tooltip>
        <el-tooltip content="发送报价" placement="top" v-if="row.quotationStatus === 6">
          <el-button
            link
            type="warning"
            icon="Promotion"
            v-hasPermi="['sales:quotation:edit']"
            @click="emit('send', row)"
          />
        </el-tooltip>
        <el-tooltip content="重新报价" placement="top" v-if="canReQuote(row)">
          <el-button
            link
            type="warning"
            icon="RefreshLeft"
            v-hasPermi="['sales:quotation:edit']"
            @click="emit('reQuote', row)"
          />
        </el-tooltip>
        <el-tooltip content="转为订单" placement="top" v-if="canConvert(row)">
          <el-button
            link
            type="success"
            icon="Switch"
            v-hasPermi="['sales:quotation:edit']"
            @click="emit('convert', row)"
          />
        </el-tooltip>
        <el-tooltip content="转为样品单" placement="top" v-if="canConvertToSample(row)">
          <el-button
            link
            type="warning"
            icon="Collection"
            v-hasPermi="['sales:quotation:edit']"
            @click="emit('convertToSample', row)"
          />
        </el-tooltip>
        <el-tooltip content="改单" placement="top" v-if="row.quotationStatus === 9">
          <el-button
            link
            type="warning"
            icon="EditPen"
            v-hasPermi="['sales:quotation:edit']"
            @click="emit('modify', row)"
          />
        </el-tooltip>
        <el-tooltip content="提交审核" placement="top" v-if="canSubmitReview(row)">
          <el-button
            link
            type="primary"
            icon="Upload"
            v-hasPermi="['sales:quotation:edit']"
            @click="emit('submitReview', row)"
          />
        </el-tooltip>
        <el-tooltip content="客户确认" placement="top" v-if="row.quotationStatus === 1">
          <el-button
            link
            type="success"
            icon="CircleCheck"
            v-hasPermi="['sales:quotation:edit']"
            @click="emit('customerConfirm', true, row)"
          />
        </el-tooltip>
        <el-tooltip content="客户拒绝" placement="top" v-if="row.quotationStatus === 1">
          <el-button
            link
            type="danger"
            icon="CircleClose"
            v-hasPermi="['sales:quotation:edit']"
            @click="emit('customerConfirm', false, row)"
          />
        </el-tooltip>
        <el-tooltip content="审核通过" placement="top" v-if="row.quotationStatus === 5">
          <el-button
            link
            type="success"
            icon="CircleCheck"
            v-hasPermi="['sales:quotation:approve']"
            @click="emit('review', true, row)"
          />
        </el-tooltip>
        <el-tooltip content="审核驳回" placement="top" v-if="row.quotationStatus === 5">
          <el-button
            link
            type="danger"
            icon="CircleClose"
            v-hasPermi="['sales:quotation:approve']"
            @click="emit('review', false, row)"
          />
        </el-tooltip>
      </SkeletonAction>
    </template>
  </el-table-column>
</template>

<script setup lang="ts">
import { QuotationStatusEnum } from '@/enums/sales'
import { parseTime, formatCurrency } from '@/utils/format'

// ============================================================
// ✅ 阻止属性透传
// ============================================================
defineOptions({
  inheritAttrs: false,
})

// ============================================================
// 状态工具
// ============================================================
const getStatusTagType = (status: number) => {
  return QuotationStatusEnum.getTagProps(status).type || 'info'
}

const getStatusLabel = (status: number) => {
  const label = QuotationStatusEnum.getLabel(status)
  return label && label !== '未知' ? label : '未知状态'
}

// ============================================================
// 行内操作权限判断
// ============================================================
const canEdit = (row: any) => {
  return ![1, 2, 3, 4].includes(row.quotationStatus) && row.quotationStatus !== 9
}

const canDelete = (row: any) => {
  return ![1, 2, 5, 6, 8, 9].includes(row.quotationStatus)
}

const canReQuote = (row: any) => {
  return [QuotationStatusEnum.REJECTED.value, QuotationStatusEnum.EXPIRED.value].includes(
    row.quotationStatus
  )
}

const canConvert = (row: any) => {
  return row.quotationStatus === QuotationStatusEnum.ACCEPTED.value && row.quotationType !== 2
}

const canConvertToSample = (row: any) => {
  return row.quotationType !== 1 && row.quotationStatus === QuotationStatusEnum.ACCEPTED.value
}

const canSubmitReview = (row: any) => {
  return [QuotationStatusEnum.DRAFT.value, QuotationStatusEnum.MODIFYING.value].includes(
    row.quotationStatus
  )
}

// ============================================================
// ✅ 声明所有 emits
// ============================================================
const emit = defineEmits<{
  (e: 'view', row: any): void
  (e: 'update', row: any): void
  (e: 'delete', row: any): void
  (e: 'send', row: any): void
  (e: 'convert', row: any): void
  (e: 'convertToSample', row: any): void
  (e: 'submitReview', row: any): void
  (e: 'review', approved: boolean, row: any): void
  (e: 'customerConfirm', confirmed: boolean, row: any): void
  (e: 'reQuote', row: any): void
  (e: 'modify', row: any): void
  (e: 'showTrace', row: any): void
  (e: 'gotoInquiry', row: any): void
}>()
</script>
