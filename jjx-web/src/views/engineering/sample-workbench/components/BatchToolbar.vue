<template>
  <div v-if="batchMode" class="batch-bar">
    <span class="batch-info">已选 <b>{{ batchSelected.size }}</b> 张卡片</span>
    <!-- 全选/取消全选（dev-20260811-008） -->
    <el-checkbox
      :model-value="allSelected"
      :indeterminate="indeterminate"
      @change="(v: boolean | string | number) => $emit('select-all', !!v)"
      class="batch-select-all"
    >全选</el-checkbox>
    <span class="batch-label">统一设置工序类别：</span>
    <el-select v-model="batchCategoryModel" size="small" placeholder="选择类别" clearable style="width:120px" @change="(v: any) => applyBatchCategory(v)">
      <el-option v-for="c in categoryOptions" :key="c.itemValue" :label="c.label" :value="c.itemValue" />
    </el-select>
    <el-button size="small" type="primary" plain icon="Plus" @click="$emit('batch-material')">批量添加材料</el-button>
    <el-button size="small" type="danger" plain icon="Delete" @click="$emit('batch-delete')">批量删除</el-button>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 批量操作栏（dev-20260811-008 组件化）
 * 含全选/取消全选 + 批量分类/加材料/删除
 */
const props = defineProps<{
  batchMode: boolean
  batchSelected: Set<string>
  batchCategory: string | null
  categoryOptions: any[]
  currentTabCards: any[]
}>()

const emit = defineEmits<{
  (e: 'select-all', v: boolean): void
  (e: 'batch-material'): void
  (e: 'batch-delete'): void
  (e: 'update:batchCategory', v: string | null): void
}>()

const batchCategoryModel = computed({
  get: () => props.batchCategory,
  set: (v: string | null) => emit('update:batchCategory', v),
})

/** 当前 tab 是否全部选中 */
const allSelected = computed(
  () => props.currentTabCards.length > 0 && props.currentTabCards.every((pc: any) => props.batchSelected.has(pc.uid))
)
/** 是否部分选中（半选状态） */
const indeterminate = computed(
  () => {
    const selected = props.currentTabCards.filter((pc: any) => props.batchSelected.has(pc.uid)).length
    return selected > 0 && selected < props.currentTabCards.length
  }
)

function applyBatchCategory(cat: string | undefined) {
  if (!cat) return
  emit('update:batchCategory', cat)
}
</script>

<style scoped>
.batch-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 10px;
  padding: 10px 14px;
  background: #fdf6ec;
  border: 1px solid #f5dab1;
  border-radius: 8px;
}
.batch-info {
  font-size: 13px;
  color: #b88230;
}
.batch-label {
  font-size: 12px;
  color: #606266;
}
.batch-select-all {
  margin-left: 4px;
}
</style>
