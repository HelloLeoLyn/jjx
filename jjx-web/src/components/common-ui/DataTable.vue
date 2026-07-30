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
  emit('update:modelValue', { pageNum: 1, pageSize: size })
  emit('size-change', size)
  emit('page-change', 1)
}

const handleCurrentChange = (page: number) => {
  localCurrentPage.value = page
  emit('update:modelValue', { pageNum: page, pageSize: localPageSize.value })
  emit('page-change', page)
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
