<!-- views/sales/quotation/components/QuotationStats.vue -->
<template>
  <el-card class="stat-card" shadow="never" v-if="stats">
    <el-row :gutter="16">
      <el-col :span="3">
        <el-statistic title="报价单总数" :value="stats.totalCount || 0" />
      </el-col>
      <el-col :span="5">
        <el-statistic title="报价总金额" :value="stats.totalAmount || 0" :precision="2" />
      </el-col>
      <el-col :span="3">
        <el-statistic title="草稿" :value="stats.draftCount || 0" />
      </el-col>
      <el-col :span="3">
        <el-statistic title="已发送" :value="stats.sentCount || 0" />
      </el-col>
      <el-col :span="3">
        <el-statistic title="已确认" :value="stats.acceptedCount || 0" />
      </el-col>
      <el-col :span="3">
        <el-statistic title="已拒绝" :value="stats.rejectedCount || 0" />
      </el-col>
      <el-col :span="3">
        <el-statistic title="已过期" :value="stats.expiredCount || 0" />
      </el-col>
    </el-row>
  </el-card>
</template>

<script setup lang="ts">
// ============================================================
// ✅ 声明 props
// ============================================================
defineProps<{
  stats: any
}>()
</script>

<style scoped>
.stat-card {
  margin-bottom: 16px;
}
</style>
