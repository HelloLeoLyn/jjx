<!-- components/ProductCodeGenDialog/index.vue
  通用「产品编码生成」弹窗（2026-10-14 dev-20261014-011）：由报价明细行弹窗抽公共而来
  - 客户简称 + 四选（面板结构/面板特征/线路类型/线路特征）全选后自动取流水号生成编码，无需点按钮
  - 可选「产品名称」输入（show-product-name，样品单现场建档用）：默认预填产品编码，可改
  - 本单已占用流水号自动避让（used-serials）
  - confirm 回传 { params, productName }；取消回传 cancel
-->
<template>
  <el-dialog
    :title="title"
    v-model="visible"
    width="800px"
    append-to-body
    destroy-on-close
    @close="handleClose"
  >
    <el-alert
      v-if="!customerShort"
      type="warning"
      :closable="false"
      show-icon
      title="请先选择客户，再生成产品编码"
      style="margin-bottom: 12px"
    />
    <ProductCodeGenerator
      ref="genRef"
      :customer-short="customerShort"
      v-model:state="codeState"
      :emit-params="true"
      v-model:params="codeParams"
      hide-short-name
      hide-generate
    />
    <el-form-item v-if="showProductName" label="产品名称" required style="margin-top: 12px">
      <el-input
        v-model="productName"
        placeholder="默认取产品编码，可修改"
        maxlength="200"
        @input="nameTouched = true"
      />
    </el-form-item>
    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" :disabled="!previewCode" @click="handleConfirm">确 定</el-button>
        <el-button @click="handleClose">取 消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed, watch } from 'vue'
import ProductCodeGenerator from '@/components/ProductCodeGenerator/index.vue'
import type { ProductCodeState, ProductCodeResult } from '@/composables/useProductCode'
import { normalizeSerial } from '@/composables/useProductCode'

const props = withDefaults(
  defineProps<{
    modelValue: boolean
    /** 客户简称（编码前缀，页面选客户后带出） */
    customerShort: string
    /** 弹窗标题 */
    title?: string
    /** 编辑回显：已有编码参数 */
    initState?: Partial<ProductCodeState>
    /** 本单已占用流水号（自动避让） */
    usedSerials?: string[]
    /** 是否显示「产品名称」输入（样品单现场建档用） */
    showProductName?: boolean
    /** 产品名称初值 */
    initProductName?: string
  }>(),
  {
    title: '生成产品编码',
    showProductName: false,
    initProductName: '',
  }
)

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'confirm', payload: { params: ProductCodeResult; productName: string }): void
  (e: 'cancel'): void
}>()

const visible = ref(false)
const genRef = ref()
const codeState = reactive<ProductCodeState>({
  serialNo: '',
  panelType: '',
  panelFeature: '',
  circuitType: '',
  circuitFeature: '',
})
const codeParams = ref<ProductCodeResult | null>(null)
const productName = ref('')
const nameTouched = ref(false)

const previewCode = computed(() => codeParams.value?.productCode || '')

watch(
  () => props.modelValue,
  (val) => {
    visible.value = val
    if (val) {
      codeState.serialNo = props.initState?.serialNo || ''
      codeState.panelType = props.initState?.panelType || ''
      codeState.panelFeature = props.initState?.panelFeature || ''
      codeState.circuitType = props.initState?.circuitType || ''
      codeState.circuitFeature = props.initState?.circuitFeature || ''
      codeParams.value = null
      nameTouched.value = false
      productName.value = props.initProductName || ''
    }
  }
)

watch(visible, (val) => emit('update:modelValue', val))

// 四选全选 + 无流水号 → 自动取号生成编码（无需点生成按钮）
watch(
  () => [
    codeState.panelType,
    codeState.panelFeature,
    codeState.circuitType,
    codeState.circuitFeature,
  ],
  () => {
    if (
      codeState.panelType &&
      codeState.panelFeature &&
      codeState.circuitType &&
      codeState.circuitFeature &&
      !codeState.serialNo
    ) {
      genRef.value?.generate()
    }
  }
)

// 产品名称未手改时跟随编码
watch(
  () => codeParams.value?.productCode,
  (code) => {
    if (props.showProductName && code && !nameTouched.value) productName.value = code
  }
)

// 同号自动避让：生成的流水号与本单其他已用号重复时，自动 +1 重试
watch(
  () => codeParams.value?.productCode,
  () => {
    const code = codeParams.value?.productCode
    if (!code || !codeState.serialNo) return
    const used = (props.usedSerials || []).filter(Boolean)
    if (!used.includes(codeState.serialNo)) return
    let next = Number(codeState.serialNo) + 1
    let guard = 0
    while (used.includes(normalizeSerial(String(next))) && guard < 200) {
      next++
      guard++
    }
    codeState.serialNo = normalizeSerial(String(next))
  }
)

const handleConfirm = () => {
  if (!codeParams.value) return
  emit('confirm', {
    params: codeParams.value,
    productName: (productName.value || codeParams.value.productCode).trim(),
  })
  visible.value = false
}

const handleClose = () => {
  visible.value = false
  emit('cancel')
}
</script>

<style scoped>
.dialog-footer {
  text-align: right;
}
</style>
