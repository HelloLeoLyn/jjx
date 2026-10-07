<template>
  <el-card class="bom-info-card" shadow="never">
    <template #header>
      <div class="card-header">
        <span>BOM基本信息</span>
      </div>
    </template>
    <el-descriptions :column="2" border>
      <el-descriptions-item label="BOM编码">{{ detail.bomCode }}</el-descriptions-item>
      <el-descriptions-item label="产品编码">{{ detail.productCode }}</el-descriptions-item>
      <el-descriptions-item label="产品名称">{{ detail.productName }}</el-descriptions-item>
      <el-descriptions-item label="BOM版本">{{ detail.bomVersion }}</el-descriptions-item>
      <el-descriptions-item label="当前状态">
        <el-tag :type="BomStatusEnum.getTagProps(detail.approveStatus)?.type">
          {{ BomStatusEnum.getLabel(detail.approveStatus) }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="生效日期">
        {{ parseDate(detail.effectiveDate) }}
      </el-descriptions-item>
      <el-descriptions-item label="失效日期">
        {{ parseDate(detail.expiryDate) }}
      </el-descriptions-item>
      <el-descriptions-item label="创建时间">{{
        parseTime(detail.createTime)
      }}</el-descriptions-item>
      <el-descriptions-item label="创建人">{{ detail.createBy || '-' }}</el-descriptions-item>
      <el-descriptions-item label="批注" :span="2">{{
        detail.approveRemark || '-'
      }}</el-descriptions-item>
      <el-descriptions-item label="备注" :span="2">{{ detail.remark || '-' }}</el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>
<script setup lang="ts">
import type { EngineeringBom } from '@/types/product/bom'
import { BomStatusEnum } from '@/enums/product'
import { parseDate, parseTime } from '@/utils/format'
defineProps<{ detail: EngineeringBom }>()
</script>
