<!-- views/sales/quotation/components/QuotationCodeGenDialog.vue
  报价明细行编码生成弹窗（2026-09-02）
  2026-10-14 dev-20261014-011：内部改为通用 ProductCodeGenDialog 的薄封装，
  对外接口（modelValue / customerShort / initState / usedSerials + confirm(params) / cancel）保持不变，调用方无感。
  - 隐藏客户简称显示（编码拼接仍用已选客户简称）
  - 四选全选后自动取流水号生成编码，无需点按钮
  - 确定后回填产品编码（+ 静默携带编码参数，随报价保存建档写入 product.spec_json）
-->
<template>
  <ProductCodeGenDialog
    v-model="visible"
    :title="editing ? '修改产品编码' : '生成产品编码'"
    :customer-short="customerShort"
    :init-state="initState"
    :used-serials="usedSerials"
    @confirm="onConfirm"
    @cancel="emit('cancel')"
  />
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import ProductCodeGenDialog from '@/components/ProductCodeGenDialog/index.vue'
import type { ProductCodeState, ProductCodeResult } from '@/composables/useProductCode'

const props = defineProps<{
  modelValue: boolean
  customerShort: string
  /** 编辑回显：已有编码参数（从明细行带过来，或从编码反解） */
  initState?: Partial<ProductCodeState>
  /** 本单已占用的流水号（自动避让用，2026-09-02：不含当前编辑行自身） */
  usedSerials?: string[]
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm', params: ProductCodeResult): void
  (e: 'cancel'): void
}>()

const visible = ref(false)
watch(
  () => props.modelValue,
  (v) => {
    visible.value = v
  },
)
watch(visible, (v) => emit('update:modelValue', v))

const editing = computed(
  () => !!(props.initState && Object.values(props.initState).some(Boolean)),
)

const onConfirm = (payload: { params: ProductCodeResult }) => {
  emit('confirm', payload.params)
  visible.value = false
}
</script>
