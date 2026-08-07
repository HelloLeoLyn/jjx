<template>
  <el-select
    :model-value="selectedWarehouseId"
    @update:model-value="handleUpdateModelValue"
    :placeholder="placeholder"
    :disabled="disabledValue"
    :clearable="clearableValue"
    :filterable="filterableValue"
    :style="width ? `width: ${width}` : ''"
    @change="handleChange"
    @clear="handleClear"
  >
    <el-option
      v-for="warehouse in filteredWarehouseList"
      :key="warehouse.warehouseId"
      :label="warehouse.warehouseName"
      :value="warehouse.warehouseId"
    >
      <div class="warehouse-option">
        <span class="warehouse-name">{{ warehouse.warehouseName }}</span>
        <span class="warehouse-code" v-if="showCode">({{ warehouse.warehouseCode }})</span>
      </div>
    </el-option>
  </el-select>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { WAREHOUSE_LIST } from '@/enums/inventory/WarehouseEnum'

interface Props {
  /** 选中的仓库ID (v-model) */
  modelValue?: string | number
  /** 占位文本 */
  placeholder?: string
  /** 是否禁用 */
  disabled?: boolean
  /** 是否可清空 */
  clearable?: boolean
  /** 是否可搜索过滤 */
  filterable?: boolean
  /** 是否显示仓库编码 */
  showCode?: boolean
  /** 宽度 */
  width?: string
  /** 是否只显示启用状态的仓库 */
  activeOnly?: boolean
  /** 按仓库类型过滤 */
  warehouseType?: string
}

interface Emits {
  (e: 'update:modelValue', value: string | number | undefined): void
  (e: 'change', warehouse: (typeof WAREHOUSE_LIST)[number] | null): void
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  placeholder: '请选择仓库',
  disabled: false,
  clearable: true,
  filterable: true,
  showCode: false,
  width: '100%',
  activeOnly: true,
  warehouseType: '',
})

const emit = defineEmits<Emits>()

const selectedWarehouseId = ref<string | number>(props.modelValue)

// 使用 computed 确保布尔值类型，避免 undefined 传递给 el-select
const disabledValue = computed(() => props.disabled ?? false)
const clearableValue = computed(() => props.clearable ?? true)
const filterableValue = computed(() => props.filterable ?? true)

// 过滤后的仓库列表
const filteredWarehouseList = computed(() => {
  let list = WAREHOUSE_LIST

  // 只显示启用状态的仓库（StatusEnum: 1=正常/0=停用，2026-08-07 对齐）
  if (props.activeOnly) {
    list = list.filter((w) => w.status === 1)
  }

  // 按仓库类型过滤（通过 warehouseCode 匹配）
  if (props.warehouseType) {
    list = list.filter((w) => w.warehouseCode === props.warehouseType)
  }

  return list
})

// 更新 modelValue
const handleUpdateModelValue = (value: any) => {
  selectedWarehouseId.value = value ?? ''
  handleChange(value)
}

// 选择变化
const handleChange = (value: any) => {
  const warehouse = filteredWarehouseList.value.find((w) => w.warehouseId === value) || null
  emit('update:modelValue', value)
  emit('change', warehouse)
}

// 清空
const handleClear = () => {
  emit('update:modelValue', undefined)
  emit('change', null)
}

// 监听外部 modelValue 变化
watch(
  () => props.modelValue,
  (newVal) => {
    selectedWarehouseId.value = newVal ?? ''
  }
)
</script>

<style scoped>
.warehouse-option {
  display: flex;
  align-items: center;
  gap: 4px;
}

.warehouse-name {
  font-weight: 500;
}

.warehouse-code {
  color: #909399;
  font-size: 12px;
}
</style>
