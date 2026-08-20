# BomItemEditor 组件使用说明

## 概述

`BomItemEditor` 是一个用于编辑 BOM（物料清单）明细的可复用 Vue 组件。它提供了完整的 BOM 明细管理功能，包括添加、编辑、删除物料项，以及自动计算金额和净数量。

## 功能特性

### 1. 核心功能

- **物料管理**：添加、编辑、删除 BOM 明细项
- **批量操作**：支持多选删除
- **自动计算**：自动计算净数量和总金额
- **排序功能**：支持手动调整物料顺序
- **物料搜索**：支持通过物料编码搜索物料信息

### 2. 数据验证

- 必填字段验证
- 数值范围验证
- 物料信息完整性检查

### 3. 用户界面

- 响应式表格布局
- 实时计算显示
- 操作按钮状态管理
- 总计金额显示

## 组件属性

### Props

| 属性名       | 类型                       | 默认值      | 说明                         |
| ------------ | -------------------------- | ----------- | ---------------------------- |
| `modelValue` | `EngineeringBomItemFormData[]` | `[]`        | BOM 明细数据数组（双向绑定） |
| `bomId`      | `number`                   | `undefined` | 关联的 BOM ID                |

### Emits

| 事件名              | 参数类型                   | 说明         |
| ------------------- | -------------------------- | ------------ |
| `update:modelValue` | `EngineeringBomItemFormData[]` | 数据更新事件 |
| `change`            | `EngineeringBomItemFormData[]` | 数据变化事件 |

## 使用方法

### 基本使用

```vue
<template>
  <BomItemEditor
    v-model="bomItems"
    :bom-id="currentBomId"
    @change="handleItemsChange"
  />
</template>

<script setup lang="ts">
import { ref } from 'vue'
import BomItemEditor from '@/components/BomItemEditor.vue'
import type { EngineeringBomItemFormData } from '@/types/product'

const currentBomId = ref(1)
const bomItems = ref<EngineeringBomItemFormData[]>([
  {
    materialId: 1,
    materialCode: 'MAT001',
    materialName: '钢材',
    materialSpec: 'Φ20mm',
    unit: 'kg',
    quantity: 10,
    lossRate: 5,
    unitPrice: 5.8,
    sortOrder: 1,
  },
])

const handleItemsChange = (items: EngineeringBomItemFormData[]) => {
  console.log('BOM明细已更新:', items)
}
</script>
```

### 在表单中使用

```vue
<template>
  <el-form :model="form" label-width="120px">
    <!-- 其他表单字段 -->

    <el-form-item label="BOM明细">
      <BomItemEditor v-model="form.items" :bom-id="form.bomId" />
    </el-form-item>

    <!-- 表单操作按钮 -->
  </el-form>
</template>
```

### 使用组件暴露的方法

```vue
<template>
  <BomItemEditor ref="bomEditorRef" v-model="bomItems" />

  <el-button @click="validateAndSubmit"> 提交 </el-button>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import BomItemEditor from '@/components/BomItemEditor.vue'
import type { EngineeringBomItemFormData } from '@/types/product'

const bomEditorRef = ref<InstanceType<typeof BomItemEditor>>()
const bomItems = ref<EngineeringBomItemFormData[]>([])

const validateAndSubmit = () => {
  if (bomEditorRef.value?.validateItems()) {
    // 验证通过，提交数据
    const items = bomEditorRef.value.getItems()
    console.log('提交的BOM明细:', items)
    // 调用API提交数据
  }
}
</script>
```

## 数据模型

### EngineeringBomItemFormData 接口

```typescript
interface EngineeringBomItemFormData {
  itemId?: number // 明细ID（编辑时使用）
  bomId?: number // BOM ID
  materialId: number // 物料ID
  materialCode: string // 物料编码
  materialName: string // 物料名称
  materialSpec: string // 物料规格
  unit: string // 单位
  quantity: number // 数量
  lossRate: number // 损耗率（%）
  netQuantity: number // 净数量（自动计算）
  unitPrice: number // 单价
  totalPrice: number // 总金额（自动计算）
  remark?: string // 备注
  sortOrder: number // 排序序号
}
```

### 自动计算规则

1. **净数量** = `quantity × (1 + lossRate / 100)`
2. **总金额** = `netQuantity × unitPrice`

## 组件方法

### 暴露的方法

通过 `ref` 可以调用以下方法：

| 方法名            | 参数 | 返回值                     | 说明                 |
| ----------------- | ---- | -------------------------- | -------------------- |
| `getItems()`      | 无   | `EngineeringBomItemFormData[]` | 获取当前所有明细项   |
| `clearItems()`    | 无   | `void`                     | 清空所有明细项       |
| `validateItems()` | 无   | `boolean`                  | 验证明细数据是否有效 |

### 使用示例

```typescript
// 获取当前数据
const items = bomEditorRef.value?.getItems()

// 清空数据
bomEditorRef.value?.clearItems()

// 验证数据
const isValid = bomEditorRef.value?.validateItems()
if (isValid) {
  // 数据有效，执行提交操作
}
```

## 样式定制

### CSS 类名

组件提供了以下 CSS 类名用于样式定制：

| 类名               | 说明         |
| ------------------ | ------------ |
| `.bom-item-editor` | 组件根容器   |
| `.editor-header`   | 操作按钮区域 |
| `.summary-info`    | 汇总信息区域 |
| `.total-price`     | 总金额显示   |
| `.bom-item-table`  | 明细表格     |

### 样式覆盖示例

```css
/* 自定义编辑器样式 */
.custom-bom-editor .editor-header {
  background-color: #f0f9ff;
  border: 1px solid #91d5ff;
}

.custom-bom-editor .total-price {
  color: #1890ff;
  font-size: 18px;
}

/* 自定义表格样式 */
.custom-bom-editor .bom-item-table :deep(.el-table__row:hover) {
  background-color: #e6f7ff;
}
```

## 集成到现有 BOM 页面

### 修改 BOM 编辑对话框

已自动集成到 `src/views/product/bom/index.vue` 中，新增了 "BOM明细" 标签页。

### 使用步骤

1. 打开 BOM 管理页面
2. 点击"新增"或"修改"按钮
3. 切换到"BOM明细"标签页
4. 使用组件功能管理物料明细

## 注意事项

### 1. 物料数据源

组件内置了示例物料数据，实际使用时需要：

- 连接物料管理 API
- 实现物料搜索功能
- 替换 `materialOptions` 数据源

### 2. 数据持久化

- 新增 BOM 时，明细数据随主表一起提交
- 修改 BOM 时，需要处理明细的增删改
- 建议使用批量操作 API 提高性能

### 3. 性能优化

- 大量数据时考虑分页或虚拟滚动
- 复杂计算考虑防抖处理
- 频繁操作时优化渲染性能

### 4. 浏览器兼容性

- 支持现代浏览器（Chrome 80+、Firefox 75+、Safari 13.1+）
- 依赖 Element Plus 组件库
- 使用 Vue 3 Composition API

## 故障排除

### 常见问题

#### 1. 组件不显示

- 检查是否正确导入组件
- 确认 `modelValue` 数据格式正确
- 查看浏览器控制台错误信息

#### 2. 数据不更新

- 确认使用 `v-model` 双向绑定
- 检查数据响应式处理
- 验证数据类型匹配

#### 3. 计算错误

- 检查数值字段类型
- 验证计算公式
- 确认单位一致性

#### 4. 样式问题

- 检查 CSS 作用域
- 确认样式加载顺序
- 验证类名是否正确

### 调试建议

1. 使用 Vue Devtools 检查组件状态
2. 查看控制台日志输出
3. 验证数据类型和格式
4. 逐步调试计算方法

## 更新日志

### v1.0.0 (2026-03-27)

- 初始版本发布
- 实现基本 BOM 明细编辑功能
- 集成到 BOM 管理页面
- 提供完整的使用文档

## 相关资源

- [Element Plus 文档](https://element-plus.org/)
- [Vue 3 文档](https://vuejs.org/)
- [TypeScript 手册](https://www.typescriptlang.org/docs/)
- [项目 API 文档](/api/product)
