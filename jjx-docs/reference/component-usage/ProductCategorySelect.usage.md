# ProductCategorySelect 组件使用说明

基于 `/api/product/category/tree` 接口生成的产品分类选择组件，支持多级树形结构、单选、搜索过滤功能。

## 特性

1. **数据只加载一次**：每个页面实例只从服务器获取一次分类数据
2. **多级树形结构**：支持无限级分类树
3. **单选模式**：只能选择一个分类（使用 `check-strictly` 模式）
4. **搜索过滤**：支持按分类名称和分类编码搜索
5. **状态管理**：自动管理加载状态和错误处理
6. **暴露方法**：提供刷新、清空、获取选中项等方法

## 基本用法

```vue
<template>
  <div>
    <ProductCategorySelect
      v-model="selectedCategoryId"
      @change="handleCategoryChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ProductCategorySelect from '@/components/ProductCategorySelect.vue'

const selectedCategoryId = ref<number | null>(null)

const handleCategoryChange = (
  value: number | null,
  category?: ProductCategoryItem,
) => {
  console.log('选中的分类ID:', value)
  console.log('选中的分类详情:', category)
}
</script>
```

## Props

| 属性           | 类型             | 默认值             | 说明                 |
| -------------- | ---------------- | ------------------ | -------------------- |
| modelValue     | `number \| null` | `null`             | 选中的分类ID         |
| placeholder    | `string`         | `'请选择产品分类'` | 占位文本             |
| width          | `string`         | `'100%'`           | 组件宽度             |
| clearable      | `boolean`        | `true`             | 是否可清空           |
| disabled       | `boolean`        | `false`            | 是否禁用             |
| filterable     | `boolean`        | `true`             | 是否可搜索过滤       |
| showAllOption  | `boolean`        | `false`            | 是否显示"全部"选项   |
| allOptionLabel | `string`         | `'全部'`           | "全部"选项的标签文本 |

## Events

| 事件名            | 参数                                                      | 说明                               |
| ----------------- | --------------------------------------------------------- | ---------------------------------- |
| update:modelValue | `(value: number \| null)`                                 | 选中的分类ID变化时触发             |
| change            | `(value: number \| null, category?: ProductCategoryItem)` | 选择变化时触发，包含选中的分类详情 |

## 暴露的方法

通过组件引用可以调用以下方法：

```vue
<template>
  <ProductCategorySelect ref="categorySelectRef" />
  <button @click="handleRefresh">刷新数据</button>
  <button @click="handleClear">清空选择</button>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ProductCategorySelect from '@/components/ProductCategorySelect.vue'

const categorySelectRef = ref<InstanceType<typeof ProductCategorySelect>>()

const handleRefresh = async () => {
  await categorySelectRef.value?.refresh()
}

const handleClear = () => {
  categorySelectRef.value?.clearSelection()
}

const getCurrentSelection = () => {
  const category = categorySelectRef.value?.getSelectedCategory()
  console.log('当前选中的分类:', category)
}
</script>
```

### 可用方法

- `loadCategoryTree()`: 加载分类树数据
- `clearSelection()`: 清空当前选择
- `refresh()`: 刷新分类数据（重新从服务器加载）
- `getSelectedCategory()`: 获取当前选中的分类详情
- `categoryTree`: 获取当前加载的分类树数据（响应式引用）
- `isLoading`: 获取当前是否正在加载数据（响应式引用）

## 在表单中使用

```vue
<template>
  <el-form :model="form" label-width="100px">
    <el-form-item label="产品分类" prop="categoryId">
      <ProductCategorySelect
        v-model="form.categoryId"
        :placeholder="'请选择产品分类'"
        :width="'300px'"
        :show-all-option="true"
        @change="handleCategoryChange"
      />
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
import { reactive } from 'vue'

const form = reactive({
  categoryId: null as number | null,
  // 其他表单字段...
})

const handleCategoryChange = (
  value: number | null,
  category?: ProductCategoryItem,
) => {
  if (category) {
    console.log(`选择了: ${category.categoryName} (${category.categoryCode})`)
  }
}
</script>
```

## 在搜索过滤中使用

```vue
<template>
  <SearchContainer :items="searchItems" @search="handleSearch">
    <template #extra-actions>
      <ProductCategorySelect
        v-model="searchParams.categoryId"
        :placeholder="'按分类筛选'"
        :width="'200px'"
        :show-all-option="true"
      />
    </template>
  </SearchContainer>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import SearchContainer from '@/components/SearchContainer/index.vue'
import ProductCategorySelect from '@/components/ProductCategorySelect.vue'

const searchParams = reactive({
  categoryId: null as number | null,
  keyword: '',
  // 其他搜索参数...
})

const searchItems = [
  {
    prop: 'keyword',
    label: '关键词',
    type: 'input',
    placeholder: '请输入关键词',
  },
  // 其他搜索项...
]

const handleSearch = (params: any) => {
  console.log('搜索参数:', params)
  // 执行搜索逻辑...
}
</script>
```

## 注意事项

1. **数据缓存**：组件内部使用 `hasLoaded` 标志确保数据只加载一次，如果需要强制刷新可以调用 `refresh()` 方法
2. **"全部"选项**：当 `showAllOption` 为 `true` 时，会添加一个值为 `-1` 的"全部"选项
3. **禁用状态**：根据分类的 `status` 字段自动禁用状态为 `'0'` 的分类
4. **搜索过滤**：搜索时会同时匹配分类名称和分类编码
5. **类型安全**：使用 TypeScript 类型定义，确保类型安全

## 错误处理

组件内置错误处理，当 API 调用失败时会显示错误提示。可以通过监听控制台日志查看详细错误信息。

## 样式定制

组件使用 Element Plus 的 `el-tree-select` 组件，可以通过 CSS 覆盖来自定义样式：

```css
/* 自定义样式示例 */
.custom-category-select {
  :deep(.el-tree) {
    max-height: 300px;
    overflow-y: auto;
  }

  :deep(.el-tree-node__content) {
    height: 36px;
  }
}
```
