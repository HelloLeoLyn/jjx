<!-- src/components/common/DataTable.vue -->
<template>
  <el-card class="table-card" shadow="never">
    <el-table
      v-loading="loading"
      :data="data"
      border
      stripe
      @selection-change="handleSelectionChange"
      @sort-change="handleSortChange"
    >
      <!-- 多选列 -->
      <el-table-column v-if="showSelection" type="selection" width="55" align="center" />

      <!-- 序号列 -->
      <el-table-column
        v-if="showIndex"
        label="序号"
        type="index"
        width="60"
        align="center"
        :index="indexMethod"
      />

      <!-- 动态列 -->
      <template v-for="column in visibleColumns" :key="column.prop || column.slot">
        <!-- 枚举列（必须有 prop） -->
        <el-table-column
          v-if="column.enumObj && column.prop"
          :label="column.label"
          :prop="column.prop"
          :width="column.width"
          :min-width="column.minWidth"
          :align="column.align || 'center'"
          :sortable="column.sortable"
          :fixed="column.fixed"
        >
          <template #default="{ row }"> </template>
        </el-table-column>

        <!-- 自定义插槽列 -->
        <el-table-column
          v-else-if="column.slot"
          :label="column.label"
          :prop="column.prop"
          :width="column.width"
          :min-width="column.minWidth"
          :align="column.align || 'left'"
          :fixed="column.fixed"
        >
          <template #default="{ row, $index }">
            <slot :name="column.slot" :row="row" :index="$index" />
          </template>
        </el-table-column>

        <!-- 普通列（必须有 prop） -->
        <el-table-column
          v-else-if="column.prop"
          :label="column.label"
          :prop="column.prop"
          :width="column.width"
          :min-width="column.minWidth"
          :align="column.align || 'left'"
          :sortable="column.sortable"
          :fixed="column.fixed"
          :formatter="column.formatter"
          show-overflow-tooltip
        />
      </template>

      <!-- 操作列（使用 action 插槽） -->
      <el-table-column
        v-if="hasActionSlot"
        label="操作"
        :width="actionMinWidth ? undefined : actionWidth"
        :min-width="actionMinWidth || undefined"
        :fixed="actionFixed"
        align="center"
      >
        <template #default="{ row, $index }">
          <slot name="action" :row="row" :index="$index" />
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrapper" v-if="pageable">
      <el-pagination
        :current-page="localCurrentPage"
        :page-size="localPageSize"
        :page-sizes="pageSizes"
        :total="total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed, useSlots, ref, watch } from 'vue'
import type { TableOptions } from '@/components/common-ui/type'

interface Props {
  data: any[]
  loading?: boolean
  total?: number
  columns: TableOptions[]
  showSelection?: boolean
  showIndex?: boolean
  actionWidth?: number
  actionMinWidth?: number
  actionFixed?: boolean | 'left' | 'right'
  pageSizes?: number[]
  modelValue?: { pageNum: number; pageSize: number }
  pageable?: boolean
}

interface Emits {
  (e: 'update:modelValue', value: { pageNum: number; pageSize: number }): void
  (e: 'selection-change', selection: any[]): void
  (e: 'sort-change', sort: { prop: string; order: string }): void
  (e: 'page-change', page: number): void
  (e: 'size-change', size: number): void
}

const props = withDefaults(defineProps<Props>(), {
  loading: false,
  total: 0,
  showSelection: true,
  showIndex: true,
  actionWidth: 150,
  actionMinWidth: undefined,
  actionFixed: 'right',
  pageSizes: () => [10, 20, 50, 100],
  modelValue: () => ({ pageNum: 1, pageSize: 10 }),
  pageable: true,
})

const emit = defineEmits<Emits>()
const slots = useSlots()

// 内部状态
const localCurrentPage = ref(props.modelValue?.pageNum || 1)
const localPageSize = ref(props.modelValue?.pageSize || 10)

// 监听外部 modelValue 变化
watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal) {
      if (newVal.pageNum !== localCurrentPage.value) {
        localCurrentPage.value = newVal.pageNum
      }
      if (newVal.pageSize !== localPageSize.value) {
        localPageSize.value = newVal.pageSize
      }
    }
  },
  { deep: true, immediate: true }
)

// 过滤隐藏列
const visibleColumns = computed(() => {
  return props.columns.filter((col) => !col.hidden)
})

// 检查是否有 action 插槽
const hasActionSlot = computed(() => !!slots.action)

const indexMethod = (index: number) => {
  return (localCurrentPage.value - 1) * localPageSize.value + index + 1
}

const handleSelectionChange = (selection: any[]) => {
  emit('selection-change', selection)
}

const handleSortChange = (sort: { prop: string; order: string }) => {
  emit('sort-change', sort)
}

const handleSizeChange = (size: number) => {
  localPageSize.value = size
  localCurrentPage.value = 1
  syncToParent(1, size)
  emit('size-change', size)
  emit('page-change', 1)
}

const handleCurrentChange = (page: number) => {
  localCurrentPage.value = page
  syncToParent(page, localPageSize.value)
  emit('page-change', page)
}

/**
 * 就地更新并回传父级查询对象（2026-09-21 修复）。
 * 背景：`<script setup>` 里 `const queryParams = reactive({...})` 会被编译器改写为 `let` +
 * getter/setter 暴露给渲染作用域，而父级模板的 `v-model="queryParams"` 编译为
 * `$setup.queryParams = $event` → 走 setter → **直接把父级那个查询对象整个换掉**。
 * 旧实现 emit 的是 `{pageNum, pageSize}` 这个不完整的新对象 → 翻页/改每页条数后
 * 父级 queryParams 里就只剩这两个字段，筛选条件（业务模块/关键字…）全丢。
 * 因此：**就地改父级对象，并回传同一个引用**（父级再赋值也还是它，条件不丢）。
 */
const syncToParent = (pageNum: number, pageSize: number) => {
  const target =
    props.modelValue && typeof props.modelValue === 'object' ? (props.modelValue as any) : ({} as any)
  target.pageNum = pageNum
  target.pageSize = pageSize
  emit('update:modelValue', target)
}
</script>

<style scoped>
.table-card {
  margin-bottom: 16px;
}

.pagination-wrapper {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
