<template>
  <el-card class="col-bom" shadow="never">
    <template #header
      ><span style="font-weight: 600">BOM 物料清单</span
      ><span class="desc">各工序材料自动聚合</span></template
    >
    <el-table v-if="bomList.length > 0" :data="bomList" size="small" border style="width: 100%">
      <el-table-column prop="process" label="来源工序" width="100" />
      <el-table-column prop="name" label="材料" min-width="120" />
      <el-table-column prop="spec" label="规格" min-width="100" />
      <el-table-column prop="qty" label="用量" width="80" />
      <el-table-column prop="unit" label="单位" width="60" />
    </el-table>
    <div v-else style="color: #999; font-size: 13px">暂无材料（在工序中添加材料后自动汇总）</div>
    <div class="transfer-zone">
      <el-button type="success" size="small" :disabled="readonly" @click="$emit('transfer')"
        >📦 资料转移（建档产品/BOM/工艺路线）</el-button
      >
      <div class="desc">
        打样确认后，把本轮工序计划+材料建档为产品/BOM/工艺路线（可预览匹配/人工调整）
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
/**
 * BOM 物料清单卡（dev-20260811-008 组件化）
 */
defineProps<{
  bomList: any[]
  readonly?: boolean
}>()

defineEmits<{
  (e: 'transfer'): void
}>()
</script>

<style scoped>
.col-bom {
  flex: 1;
  min-width: 0;
}
.desc {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
  margin-left: 8px;
}
.transfer-zone {
  margin-top: 12px;
  border-top: 1px dashed #e4e7ed;
  padding-top: 10px;
}
.transfer-zone .desc {
  display: block;
  margin-left: 0;
  margin-top: 6px;
  line-height: 1.6;
}
</style>
