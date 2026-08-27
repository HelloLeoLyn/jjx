<template>
  <div v-if="frequentMaterials.length" class="freq-materials">
    <span class="freq-label">⭐ 常用物料</span>
    <el-tooltip
      v-for="fm in frequentMaterials" :key="fm.materialId || fm.name"
      :content="`点击添加到当前卡片材料（${fm.count}次）`" placement="top"
    >
      <el-tag
        class="freq-tag" size="small" effect="plain"
        @click="$emit('add', fm)"
      >{{ fm.name }}{{ fm.spec ? ' ' + fm.spec : '' }}</el-tag>
    </el-tooltip>
    <span class="desc">基于历史打样统计，点击加入当前卡片</span>
  </div>
</template>

<script setup lang="ts">
/**
 * 常用物料快捷区（dev-20260811-008 组件化）
 */
defineProps<{
  frequentMaterials: any[]
}>()

defineEmits<{
  (e: 'add', fm: any): void
}>()
</script>

<style scoped>
.freq-materials {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 12px;
  margin-bottom: 12px;
  background: #f8fbff;
  border: 1px dashed #b3d8ff;
  border-radius: 8px;
}
.freq-label {
  font-size: 12px;
  font-weight: 600;
  color: #e6a23c;
  margin-right: 2px;
}
.freq-tag {
  cursor: pointer;
  transition: all 0.15s;
}
.freq-tag:hover {
  border-color: #e6a23c;
  color: #e6a23c;
  background: #fdf6ec;
}
.desc {
  font-size: 12px;
  color: #909399;
  font-weight: 400;
  margin-left: 8px;
}
</style>
