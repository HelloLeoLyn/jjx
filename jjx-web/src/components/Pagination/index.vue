<template>
  <div class="pagination-container">
    <el-pagination
      :current-page="currentPage"
      :page-size="pageSize"
      :page-sizes="pageSizes"
      :total="total"
      :layout="layout"
      :background="background"
      @size-change="handleSizeChange"
      @current-change="handleCurrentChange"
    />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps({
  // 当前页码
  page: {
    type: Number,
    default: 1,
  },
  // 每页条数
  limit: {
    type: Number,
    default: 10,
  },
  // 总条数
  total: {
    type: Number,
    required: true,
    default: 0,
  },
  // 每页条数选项
  pageSizes: {
    type: Array as PropType<number[]>,
    default: () => [10, 20, 50, 100],
  },
  // 布局
  layout: {
    type: String,
    default: 'total, sizes, prev, pager, next, jumper',
  },
  // 背景色
  background: {
    type: Boolean,
    default: true,
  },
  // 是否自动滚动到顶部
  autoScroll: {
    type: Boolean,
    default: true,
  },
})

const emit = defineEmits(['update:page', 'update:limit', 'pagination'])

// 计算属性
const currentPage = computed({
  get: () => props.page,
  set: (val) => emit('update:page', val),
})

const pageSize = computed({
  get: () => props.limit,
  set: (val) => emit('update:limit', val),
})

// 每页条数变化
const handleSizeChange = (val: number) => {
  pageSize.value = val
  emit('pagination', { page: currentPage.value, limit: val })
  if (props.autoScroll) {
    scrollToTop()
  }
}

// 页码变化
const handleCurrentChange = (val: number) => {
  currentPage.value = val
  emit('pagination', { page: val, limit: pageSize.value })
  if (props.autoScroll) {
    scrollToTop()
  }
}

// 滚动到顶部
const scrollToTop = () => {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}
</script>

<style scoped>
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
