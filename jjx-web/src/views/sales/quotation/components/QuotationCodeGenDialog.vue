<!-- views/sales/quotation/components/QuotationCodeGenDialog.vue
  报价明细行编码生成弹窗（2026-09-02）：针对单行明细生成产品编码
  - 隐藏客户简称显示（编码拼接仍用已选客户简称）
  - 四选（面板结构/面板特征/线路类型/线路特征）全选后自动取流水号生成编码，无需点按钮
  - 确定后回填产品编码（+ 静默携带编码参数，随报价保存建档写入 product.spec_json）
-->
<template>
  <el-dialog
    :title="editing ? '修改产品编码' : '生成产品编码'"
    v-model="visible"
    width="560px"
    append-to-body
    destroy-on-close
    @close="handleClose"
  >
    <el-alert
      v-if="!customerShort"
      type="warning"
      :closable="false"
      show-icon
      title="请先在报价单中选择客户，再生成产品编码"
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
import { ElMessage } from 'element-plus'
import ProductCodeGenerator from '@/components/ProductCodeGenerator/index.vue'
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
const editing = ref(false)
const genRef = ref()
const codeState = reactive<ProductCodeState>({
  serialNo: '',
  panelType: '',
  panelFeature: '',
  circuitType: '',
  circuitFeature: '',
})
const codeParams = ref<ProductCodeResult | null>(null)

const previewCode = computed(() => codeParams.value?.productCode || '')

watch(
  () => props.modelValue,
  (val) => {
    visible.value = val
    if (val) {
      editing.value = !!(props.initState && Object.values(props.initState).some(Boolean))
      // 重置为编辑态参数（有则回显，无则全新）
      codeState.serialNo = props.initState?.serialNo || ''
      codeState.panelType = props.initState?.panelType || ''
      codeState.panelFeature = props.initState?.panelFeature || ''
      codeState.circuitType = props.initState?.circuitType || ''
      codeState.circuitFeature = props.initState?.circuitFeature || ''
      codeParams.value = null
    }
  }
)

watch(visible, (val) => {
  emit('update:modelValue', val)
})

// 四选全选 + 无流水号 → 自动取号生成编码（2026-09-02，无需点生成按钮）
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

// 同号自动避让（2026-09-02）：生成的流水号与本单其他明细重复时，自动 +1 重试
// 依赖：serialNo 改变 → ProductCodeGenerator 内部 watch → 重新 emitResult → codeParams 更新 → 本 watch 再查
watch(
  () => codeParams.value?.productCode,
  () => {
    const code = codeParams.value?.productCode
    if (!code || !codeState.serialNo) return
    const used = (props.usedSerials || []).filter(Boolean)
    if (!used.includes(codeState.serialNo)) return
    // 撞号：自动 +1 直到不冲突（3位流水号，超出 999 回绕提示）
    let next = Number(codeState.serialNo) + 1
    let guard = 0
    while (used.includes(String(next).padStart(3, '0')) && guard < 100) {
      next++
      guard++
    }
    if (next > 999) {
      codeParams.value = null
      ElMessage.warning('流水号已用尽（001~999），请换客户或联系管理员')
      return
    }
    codeState.serialNo = String(next).padStart(3, '0')
  }
)

const handleConfirm = () => {
  if (!codeParams.value) return
  emit('confirm', codeParams.value)
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
