<template>
  <div class="sample-review-preview" v-loading="loading">
    <template v-if="order">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="样品单号">{{ order.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="客户">{{ order.customerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源报价单">
          <template v-if="order.quotationNo">
            {{ order.quotationNo }}
            <el-button
              v-if="order.quotationId"
              link
              type="primary"
              size="small"
              @click="$emit('viewQuotation')"
            >查看</el-button>
          </template>
          <span v-else>-</span>
        </el-descriptions-item>
        <el-descriptions-item label="业务负责人">{{ order.salesManagerName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="本次申请打样数量" :span="2">
          {{ order.sampleQty != null ? `${order.sampleQty} PCS` : '-' }}
        </el-descriptions-item>
      </el-descriptions>

      <!-- 产品明细：单产品紧凑摘要 / 多产品表格（不拼长文本） -->
      <div class="srp-products">
        <div class="srp-title">转打样产品</div>
        <template v-if="products.length === 1">
          <div class="srp-product-line">
            {{ products[0].productName || '-' }}（{{ products[0].productCode || '-' }}）
            <template v-if="products[0].specification"><span class="srp-divider">｜</span>{{ products[0].specification }}</template>
            <template v-if="products[0].quantity != null"><span class="srp-divider">｜</span>打样 {{ products[0].quantity }}{{ products[0].unit || '' }}</template>
          </div>
        </template>
        <el-table v-else-if="products.length > 1" :data="products" size="small" border style="width: 100%">
          <el-table-column prop="productCode" label="产品编码" min-width="110" />
          <el-table-column prop="productName" label="产品名称" min-width="130" />
          <el-table-column prop="specification" label="规格" min-width="120" show-overflow-tooltip />
          <el-table-column prop="quantity" label="打样数量" width="90" align="center" />
          <el-table-column prop="unit" label="单位" width="70" align="center" />
        </el-table>
        <div v-else style="color:#909399;font-size:12px;padding:4px 0">暂无产品明细</div>
      </div>
    </template>
    <div v-else-if="!loading" style="color:#909399;font-size:13px;padding:8px 0">暂无样品单数据</div>
  </div>
</template>

<script setup lang="ts">
/**
 * 报价转打样 · 提交审核业务预览（V1）
 *
 * 职责：仅展示被操作对象业务摘要（样品单/客户/来源报价/产品明细/打样数量）。
 * 不负责：弹窗开关、接口调用、loading 控制、成功提示、列表刷新、审核意见表单、权限判断。
 *
 * mode="submit" 提交审核摘要（本轮接入）；mode="audit" 审核摘要（阶段二复用，暂未实现 UI）
 */
const props = withDefaults(
  defineProps<{
    order: any
    products?: any[]
    loading?: boolean
    mode?: 'submit' | 'audit'
  }>(),
  {
    products: () => [],
    loading: false,
    mode: 'submit',
  },
)

// 当前仅 submit 模式有 UI（audit 留待阶段二）
void props.mode

defineEmits<{
  (e: 'viewQuotation'): void
}>()
</script>

<style scoped>
.sample-review-preview {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 12px 14px;
}
.srp-products {
  margin-top: 10px;
}
.srp-title {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}
.srp-product-line {
  font-size: 13px;
  color: #303133;
  line-height: 1.7;
}
.srp-divider {
  color: #c0c4cc;
  margin: 0 4px;
}
</style>
