<template>
  <!-- 供应商选择器 -->
  <el-select
    :model-value="selectedSupplierId"
    :placeholder="placeholder"
    :disabled="disabledValue"
    :clearable="clearableValue"
    :filterable="filterableValue"
    :remote="remoteValue"
    :remote-method="remoteMethod"
    :loading="loadingValue"
    :style="width ? `width: ${width}` : ''"
    @change="handleChange"
  >
    <el-option
      v-for="supplier in supplierOptions"
      :key="supplier.supplierId"
      :label="supplier.supplierName"
      :value="String(supplier.supplierId)"
      :disabled="supplier.currentOnly"
    >
      <div class="supplier-option">
        <span class="supplier-name">{{ supplier.supplierName }}</span>
        <span class="supplier-code" v-if="showCode">({{ supplier.supplierCode }})</span>
      </div>
    </el-option>
  </el-select>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { getActiveSuppliers, listSupplier, getSupplier } from '@/api/purchase/supplier'
import type { PurchaseSupplier } from '@/types/purchase'

interface Props {
  /** 选中的供应商ID (v-model) */
  modelValue?: string | number
  /** 已有单据供应商名称，用于列表加载前或失败时回显 */
  selectedName?: string
  /** 占位文本 */
  placeholder?: string
  /** 是否禁用 */
  disabled?: boolean
  /** 是否可清空 */
  clearable?: boolean
  /** 是否可搜索过滤 */
  filterable?: boolean
  /** 是否启用远程搜索 */
  remote?: boolean
  /** 是否显示供应商编码 */
  showCode?: boolean
  /** 宽度 */
  width?: string
  /** 是否只加载活跃供应商 */
  activeOnly?: boolean
}

interface Emits {
  (e: 'update:modelValue', value: string | number | undefined): void
  (e: 'change', supplier: PurchaseSupplier | null): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  selectedName: '',
  placeholder: '请选择供应商',
  disabled: false,
  clearable: true,
  filterable: true,
  remote: false,
  showCode: false,
  width: '100%',
  activeOnly: true,
})

const emit = defineEmits<Emits>()

const loading = ref(false)
const supplierList = ref<PurchaseSupplier[]>([])
const normalizeId = (value: string | number | undefined) => value == null ? '' : String(value)
const selectedSupplierId = computed(() => normalizeId(props.modelValue))
const currentSupplier = ref<PurchaseSupplier | null>(null)
const suppliersLoaded = ref(false)
const supplierOptions = computed(() => {
  const options = supplierList.value.map((supplier) => ({ ...supplier, currentOnly: false }))
  const id = selectedSupplierId.value
  if (id && !options.some((supplier) => normalizeId(supplier.supplierId) === id)) {
    // 当前单据供应商可回显；不把列表之外的供应商开放为新的可选项。
    const supplier = currentSupplier.value && normalizeId(currentSupplier.value.supplierId) === id
      ? currentSupplier.value : null
    options.push({
      supplierCode: '',
      supplierType: '',
      ...(supplier || {}),
      supplierId: Number(id),
      supplierName: props.selectedName || supplier?.supplierName || id,
      currentOnly: true,
    })
  }
  return options
})

const ensureCurrentSupplier = async () => {
  const id = selectedSupplierId.value
  currentSupplier.value = null
  if (!id || supplierList.value.some((supplier) => normalizeId(supplier.supplierId) === id)) return
  try {
    const res = await getSupplier(Number(id))
    if (selectedSupplierId.value === id) currentSupplier.value = res.data || null
  } catch (error) {
    console.error('加载当前供应商失败:', error)
  }
}

// 使用 computed 确保布尔值类型，避免 undefined 传递给 el-select
const disabledValue = computed(() => props.disabled ?? false)
const clearableValue = computed(() => props.clearable ?? true)
const filterableValue = computed(() => props.filterable ?? true)
const remoteValue = computed(() => props.remote ?? false)
const loadingValue = computed(() => loading.value)

// 加载供应商列表
const loadSuppliers = async (keyword?: string) => {
  loading.value = true
  try {
    if (props.activeOnly) {
      const res = await getActiveSuppliers()
      supplierList.value = (res as any).data || []
    } else {
      const params: any = { pageNum: 1, pageSize: 200 }
      if (keyword) {
        params.supplierName = keyword
      }
      const res = await listSupplier(params)
      supplierList.value = (res as any).data?.records || (res as any).data || []
    }
  } catch (error) {
    console.error('加载供应商列表失败:', error)
    supplierList.value = []
  } finally {
    loading.value = false
    suppliersLoaded.value = true
    await ensureCurrentSupplier()
  }
}

// 远程搜索方法
const remoteMethod = (query: string) => {
  if (query) {
    loadSuppliers(query)
  } else {
    loadSuppliers()
  }
}

// 用户选择才发送 change；补加载不会清空订单明细或覆盖名称。
const handleChange = (value: string | number | undefined) => {
  const id = normalizeId(value)
  const supplier = supplierOptions.value.find((s) => normalizeId(s.supplierId) === id) || null
  emit('update:modelValue', id ? (supplier?.supplierId ?? value) : undefined)
  emit('change', supplier)
}

// 监听外部 modelValue 变化
watch(
  () => props.modelValue,
  () => {
    if (suppliersLoaded.value) ensureCurrentSupplier()
  }
)

// 初始化加载
onMounted(() => {
  loadSuppliers()
})

// 暴露方法
defineExpose({
  loadSuppliers,
  refresh: loadSuppliers,
})
</script>

<style scoped>
.supplier-option {
  display: flex;
  align-items: center;
  gap: 4px;
}

.supplier-name {
  font-weight: 500;
}

.supplier-code {
  color: #909399;
  font-size: 12px;
}
</style>
