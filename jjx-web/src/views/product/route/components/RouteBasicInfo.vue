<template>
  <el-descriptions :column="2" border>
    <el-descriptions-item label="路线编码" :span="1">{{ detail.routingCode }}</el-descriptions-item>
    <el-descriptions-item label="路线名称" :span="1">{{ detail.routingName }}</el-descriptions-item>
    <el-descriptions-item label="产品名称" :span="1">{{ detail.productName }}</el-descriptions-item>
    <el-descriptions-item label="产品编码" :span="1">{{ detail.productCode }}</el-descriptions-item>
    <el-descriptions-item label="版本号" :span="1">{{
      detail.routingVersion
    }}</el-descriptions-item>
    <el-descriptions-item label="当前版本" :span="1">
      <el-tag :type="detail.isCurrent === 1 ? 'success' : 'info'" size="small">{{
        detail.isCurrentName
      }}</el-tag>
    </el-descriptions-item>
    <el-descriptions-item label="审核状态" :span="1">
      <el-tag :type="RouteStatusEnum.getTagProps(detail.approveStatus).type" size="small">{{
        RouteStatusEnum.getLabel(detail.approveStatus)
      }}</el-tag>
    </el-descriptions-item>
    <el-descriptions-item label="工序数量" :span="1">{{
      detail.processCount
    }}</el-descriptions-item>
    <el-descriptions-item label="总人工工时" :span="1">{{
      detail.totalLaborHours
    }}</el-descriptions-item>
    <el-descriptions-item label="总机器工时" :span="1">{{
      detail.totalMachineHours
    }}</el-descriptions-item>
    <el-descriptions-item label="创建人" :span="1">{{ detail.createBy }}</el-descriptions-item>
    <el-descriptions-item label="创建时间" :span="1">{{ detail.createTime }}</el-descriptions-item>
    <el-descriptions-item label="更新人" :span="1">{{ detail.updateBy }}</el-descriptions-item>
    <el-descriptions-item label="更新时间" :span="1">{{ detail.updateTime }}</el-descriptions-item>
    <el-descriptions-item label="路线说明" :span="2">{{
      detail.description || '-'
    }}</el-descriptions-item>
    <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
  </el-descriptions>
</template>
<script setup lang="ts">
import type { EngineeringRoutingVO } from '@/types/product/routing'
import { RouteStatusEnum } from '@/enums/product'
defineProps<{ detail: EngineeringRoutingVO }>()
</script>
