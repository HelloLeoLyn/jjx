<!-- views/sales/quotation/components/QuotationToolbar.vue -->
<template>
  <el-card class="operation-card" shadow="never">
    <el-row :gutter="10" class="mb8">
      <el-col :span="1.5">
        <el-button type="primary" plain icon="Plus" @click="emit('add')">新增</el-button>
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Edit"
          v-hasPermi="['sales:quotation:edit']"
          :disabled="single || !actions.canEdit"
          @click="emit('update')"
          >修改</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="Delete"
          v-hasPermi="['sales:quotation:delete']"
          :disabled="multiple || !actions.canDelete"
          @click="emit('delete')"
          >删除</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Download"
          v-hasPermi="['sales:quotation:export']"
          @click="emit('export')"
          >导出</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="Send"
          v-hasPermi="['sales:quotation:edit']"
          :disabled="single || !actions.canSend"
          @click="emit('send')"
          >发送报价</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="Switch"
          v-hasPermi="['sales:quotation:edit']"
          :disabled="single || !actions.canConvert"
          @click="emit('convert')"
          >转为订单</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="Collection"
          v-hasPermi="['sales:quotation:edit']"
          :disabled="single || !actions.canConvertToSample"
          @click="emit('convertToSample')"
          >转为样品单</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="CircleCheck"
          v-hasPermi="['sales:quotation:edit']"
          :disabled="single || !actions.canCustomerConfirm"
          @click="emit('customerConfirm', true)"
          >客户确认</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="CircleClose"
          v-hasPermi="['sales:quotation:edit']"
          :disabled="single || !actions.canCustomerConfirm"
          @click="emit('customerConfirm', false)"
          >客户拒绝</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button type="warning" plain icon="CopyDocument" :disabled="single" @click="emit('copy')"
          >复制报价</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Upload"
          v-hasPermi="['sales:quotation:edit']"
          :disabled="single || !actions.canSubmitReview"
          @click="emit('submitReview')"
          >提交审核</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="CircleCheck"
          v-hasPermi="['sales:quotation:approve']"
          :disabled="single || !actions.canApprove"
          @click="emit('review', true)"
          >审核通过</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="danger"
          plain
          icon="CircleClose"
          v-hasPermi="['sales:quotation:approve']"
          :disabled="single || !actions.canApprove"
          @click="emit('review', false)"
          >审核驳回</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="primary"
          plain
          icon="Document"
          :disabled="single"
          @click="emit('exportPdf')"
          >导出PDF</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="success"
          plain
          icon="DocumentCopy"
          :disabled="single"
          @click="emit('exportExcel')"
          >导出Excel</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="RefreshLeft"
          v-hasPermi="['sales:quotation:edit']"
          :disabled="single || !actions.canReQuote"
          @click="emit('reQuote')"
          >重新报价</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="warning"
          plain
          icon="EditPen"
          :disabled="single || !actions.canModify"
          @click="emit('modify')"
          >改单</el-button
        >
      </el-col>
      <el-col :span="1.5">
        <el-button
          type="info"
          plain
          icon="FolderOpened"
          :disabled="single"
          @click="emit('attachment')"
          >附件</el-button
        >
      </el-col>
    </el-row>
  </el-card>
</template>

<script setup lang="ts">
// ============================================================
// ✅ 声明所有 props
// ============================================================
defineProps<{
  single: boolean
  multiple: boolean
  actions: {
    canSend: boolean
    canSubmitReview: boolean
    canApprove: boolean
    canCustomerConfirm: boolean
    canConvert: boolean
    canConvertToSample: boolean
    canReQuote: boolean
    canDelete: boolean
    canEdit: boolean
    canModify: boolean
  }
}>()

// ============================================================
// ✅ 声明所有 emits
// ============================================================
const emit = defineEmits<{
  (e: 'add'): void
  (e: 'update'): void
  (e: 'delete'): void
  (e: 'export'): void
  (e: 'send'): void
  (e: 'convert'): void
  (e: 'convertToSample'): void
  (e: 'customerConfirm', confirmed: boolean): void
  (e: 'copy'): void
  (e: 'submitReview'): void
  (e: 'review', approved: boolean): void
  (e: 'exportPdf'): void
  (e: 'exportExcel'): void
  (e: 'reQuote'): void
  (e: 'modify'): void
  (e: 'attachment'): void
}>()
</script>

<style scoped>
.operation-card {
  margin-bottom: 16px;
}
</style>
