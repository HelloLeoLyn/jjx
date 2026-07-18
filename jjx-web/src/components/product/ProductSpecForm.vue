<template>
  <div class="product-spec-form">
    <!-- 使用默认规格参数复选框 -->
    <div class="spec-header">
      <el-checkbox
        v-model="useDefaultSpec"
        :indeterminate="isIndeterminate"
        @change="handleDefaultSpecChange"
      >
        使用默认规格参数模板
      </el-checkbox>
      <el-tooltip content="勾选后将自动填充常用的规格参数模板，您可以在填充后修改" placement="top">
        <el-icon class="info-icon"><InfoFilled /></el-icon>
      </el-tooltip>
      <el-button
        v-if="!useDefaultSpec && specItems.length === 0"
        type="primary"
        link
        size="small"
        @click="showDefaultPreview"
      >
        查看默认模板
      </el-button>
    </div>

    <!-- 规格参数表格 -->
    <div class="spec-table-container" v-if="specItems.length > 0">
      <el-table :data="specItems" border size="small" class="spec-table">
        <el-table-column label="序号" type="index" width="60" align="center" />
        <el-table-column label="参数名称" prop="name" min-width="120">
          <template #default="{ row, $index }">
            <el-input
              v-model="row.name"
              placeholder="请输入参数名称"
              size="small"
              @change="handleSpecChange($index)"
            />
          </template>
        </el-table-column>
        <el-table-column label="参数值" prop="value" min-width="150">
          <template #default="{ row, $index }">
            <el-input
              v-model="row.value"
              placeholder="请输入参数值"
              size="small"
              @change="handleSpecChange($index)"
            />
          </template>
        </el-table-column>
        <el-table-column label="单位" prop="unit" width="100">
          <template #default="{ row, $index }">
            <el-input
              v-model="row.unit"
              placeholder="单位"
              size="small"
              @change="handleSpecChange($index)"
            />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" align="center">
          <template #default="{ $index }">
            <el-button
              type="danger"
              link
              size="small"
              icon="Delete"
              @click="removeSpecItem($index)"
            />
          </template>
        </el-table-column>
      </el-table>

      <!-- 添加按钮 -->
      <div class="spec-actions">
        <el-button type="primary" size="small" @click="addSpecItem">
          <el-icon><Plus /></el-icon>添加参数
        </el-button>
        <el-button v-if="useDefaultSpec" type="warning" size="small" @click="resetToDefault">
          重置为默认值
        </el-button>
      </div>
    </div>

    <!-- 空状态提示 -->
    <div v-else class="spec-empty">
      <el-empty description="暂无规格参数" :image-size="80">
        <template #description>
          <p>当前没有规格参数</p>
          <p v-if="!useDefaultSpec" class="empty-hint">
            您可以勾选"使用默认规格参数模板"或手动添加参数
          </p>
        </template>
        <el-button type="primary" size="small" @click="addSpecItem"> 添加第一个参数 </el-button>
      </el-empty>
    </div>

    <!-- 默认值预览对话框 -->
    <el-dialog title="默认规格参数模板" v-model="previewVisible" width="600px" append-to-body>
      <el-table :data="DEFAULT_SPEC_ITEMS" border size="small">
        <el-table-column label="参数名称" prop="name" width="120" />
        <el-table-column label="参数值" prop="value" width="180" />
        <el-table-column label="单位" prop="unit" width="80" />
      </el-table>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="previewVisible = false">关闭</el-button>
          <el-button type="primary" @click="applyDefaultSpec"> 应用此模板 </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { ProductSpecItem } from '@/types/product'

// 默认规格参数
const DEFAULT_SPEC_ITEMS: ProductSpecItem[] = [
  { name: '颜色', value: '黑色', unit: '-' },
  { name: '材质', value: '金属', unit: '-' },
  { name: '尺寸', value: '10cm x 5cm x 2cm', unit: 'cm' },
  { name: '重量', value: '200g', unit: 'g' },
  { name: '品牌', value: '某品牌', unit: '-' },
  { name: '型号', value: 'XYZ123', unit: '-' },
]

// Props定义
interface Props {
  modelValue?: string // JSON字符串
  useDefault?: boolean // 是否使用默认值
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: '',
  useDefault: false,
})

// Emits定义
interface Emits {
  (e: 'update:modelValue', value: string): void
  (e: 'update:useDefault', value: boolean): void
  (e: 'change', value: ProductSpecItem[]): void
}

const emit = defineEmits<Emits>()

// 响应式数据
const specItems = ref<ProductSpecItem[]>([])
const useDefaultSpec = ref(props.useDefault)
const isIndeterminate = ref(false)
const previewVisible = ref(false)

// 更新不确定状态
const updateIndeterminateState = () => {
  if (specItems.value.length === 0) {
    isIndeterminate.value = false
    return
  }

  // 检查是否有任何项与默认值匹配
  const hasMatchingItems = specItems.value.some((item) =>
    DEFAULT_SPEC_ITEMS.some(
      (defaultItem) =>
        item.name === defaultItem.name &&
        item.value === defaultItem.value &&
        item.unit === defaultItem.unit
    )
  )

  const hasNonMatchingItems = specItems.value.some(
    (item) =>
      !DEFAULT_SPEC_ITEMS.some(
        (defaultItem) =>
          item.name === defaultItem.name &&
          item.value === defaultItem.value &&
          item.unit === defaultItem.unit
      )
  )

  isIndeterminate.value = hasMatchingItems && hasNonMatchingItems
}

// 监听props变化
watch(
  () => props.modelValue,
  (newValue) => {
    if (newValue) {
      try {
        const parsed = JSON.parse(newValue)
        if (Array.isArray(parsed)) {
          specItems.value = parsed
        } else if (parsed.specifications && Array.isArray(parsed.specifications)) {
          specItems.value = parsed.specifications
        } else if (typeof parsed === 'object') {
          // 转换为数组格式
          specItems.value = Object.entries(parsed).map(([name, value]) => ({
            name,
            value: String(value),
            unit: '-',
          }))
        } else {
          specItems.value = []
        }
      } catch (error) {
        console.error('解析规格参数失败:', error)
        specItems.value = []
      }
    } else {
      specItems.value = []
    }
    updateIndeterminateState()
  },
  { immediate: true }
)

watch(
  () => props.useDefault,
  (newValue) => {
    useDefaultSpec.value = newValue
    if (newValue && specItems.value.length === 0) {
      applyDefaultSpec()
    }
  }
)

// 计算是否与默认值完全匹配
const isExactMatchWithDefault = computed(() => {
  if (specItems.value.length !== DEFAULT_SPEC_ITEMS.length) return false
  return specItems.value.every((item, index) => {
    const defaultItem = DEFAULT_SPEC_ITEMS[index]
    return (
      item.name === defaultItem.name &&
      item.value === defaultItem.value &&
      item.unit === defaultItem.unit
    )
  })
})

// 处理默认规格参数变化
const handleDefaultSpecChange = (checked: boolean | string | number) => {
  const isChecked = Boolean(checked)
  emit('update:useDefault', isChecked)

  if (isChecked && specItems.value.length === 0) {
    // 如果当前没有数据，应用默认值
    applyDefaultSpec()
  } else if (!isChecked) {
    // 如果取消勾选，不清空数据，只更新状态
    updateIndeterminateState()
  }
}

// 应用默认规格参数
const applyDefaultSpec = () => {
  specItems.value = JSON.parse(JSON.stringify(DEFAULT_SPEC_ITEMS))
  emitSpecChange()
  useDefaultSpec.value = true
  emit('update:useDefault', true)
  previewVisible.value = false
  ElMessage.success('已应用默认规格参数模板')
}

// 重置为默认值
const resetToDefault = () => {
  specItems.value = JSON.parse(JSON.stringify(DEFAULT_SPEC_ITEMS))
  emitSpecChange()
  ElMessage.success('已重置为默认值')
}

// 显示默认值预览
const showDefaultPreview = () => {
  previewVisible.value = true
}

// 添加规格参数项
const addSpecItem = () => {
  specItems.value.push({
    name: '',
    value: '',
    unit: '-',
  })
  emitSpecChange()
  updateIndeterminateState()
}

// 移除规格参数项
const removeSpecItem = (index: number) => {
  specItems.value.splice(index, 1)
  emitSpecChange()
  updateIndeterminateState()

  // 如果删除了所有项，取消勾选默认值
  if (specItems.value.length === 0 && useDefaultSpec.value) {
    useDefaultSpec.value = false
    emit('update:useDefault', false)
  }
}

// 处理规格参数变化
const handleSpecChange = (index: number) => {
  emitSpecChange()
  updateIndeterminateState()
}

// 触发变化事件
const emitSpecChange = () => {
  const jsonString = JSON.stringify(specItems.value)
  emit('update:modelValue', jsonString)
  emit('change', specItems.value)
}

// 暴露方法给父组件
defineExpose({
  getSpecItems: () => specItems.value,
  clearSpecItems: () => {
    specItems.value = []
    emitSpecChange()
    useDefaultSpec.value = false
    emit('update:useDefault', false)
  },
  applyDefaultSpec,
})
</script>

<style scoped>
.product-spec-form {
  width: 100%;
}

.spec-header {
  display: flex;
  align-items: center;
  margin-bottom: 16px;
  padding: 8px 12px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.spec-header .info-icon {
  margin-left: 8px;
  color: #909399;
  cursor: help;
}

.spec-table-container {
  margin-top: 12px;
}

.spec-table {
  margin-bottom: 12px;
}

.spec-table :deep(.el-table__cell) {
  padding: 4px 0;
}

.spec-table :deep(.el-input) {
  width: 100%;
}

.spec-actions {
  display: flex;
  gap: 12px;
  margin-top: 12px;
}

.spec-empty {
  margin: 20px 0;
  padding: 40px 20px;
  background-color: #fafafa;
  border-radius: 4px;
  border: 1px dashed #dcdfe6;
  text-align: center;
}

.empty-hint {
  color: #909399;
  font-size: 12px;
  margin-top: 8px;
}

.dialog-footer {
  text-align: right;
}
</style>
