<template>
  <el-dialog v-model="visible" title="产品详情" width="1200px" append-to-body>
    <ProductDetail ref="productDetailRef" :product-id="productId" />

    <template #footer>
      <span class="dialog-footer">
        <el-button @click="visible = false">关闭</el-button>
      </span>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import ProductDetail from './ProductDetail.vue'

// Props定义
interface Props {
  modelValue: boolean
  productId?: number
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  productId: undefined,
})

// Emits定义
interface Emits {
  (e: 'update:modelValue', value: boolean): void
}

const emit = defineEmits<Emits>()

// 响应式数据
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
</script>

<style scoped>
.dialog-footer {
  text-align: right;
}
</style>
