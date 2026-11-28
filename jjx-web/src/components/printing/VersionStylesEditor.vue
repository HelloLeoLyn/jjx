<template>
  <div>
    <el-table :data="modelValue" size="small" border>
      <el-table-column label="样式编号" min-width="130"><template #default="{ row }">{{ row.styleNo || '保存时自动编号' }}</template></el-table-column>
      <el-table-column label="样式名称" min-width="130"><template #default="{ row, $index }"><el-input :model-value="row.name" maxlength="100" @update:model-value="v => update($index, 'name', v)" /></template></el-table-column>
      <el-table-column label="油墨色号" min-width="120"><template #default="{ row, $index }"><el-input :model-value="row.inkColor" maxlength="100" @update:model-value="v => update($index, 'inkColor', v)" /></template></el-table-column>
      <el-table-column width="65"><template #default="{ $index }"><el-button link type="danger" @click="emit('update:modelValue', modelValue.filter((_, i) => i !== $index))">删除</el-button></template></el-table-column>
    </el-table>
    <el-button :disabled="modelValue.length >= 100" link type="primary" @click="emit('update:modelValue', [...modelValue, { name: '', inkColor: '' }])">添加样式</el-button>
  </div>
</template>
<script setup lang="ts">
export interface FilmStyle { styleNo?: string; name: string; inkColor: string }
const props = defineProps<{ modelValue: FilmStyle[] }>()
const emit = defineEmits<{ 'update:modelValue': [value: FilmStyle[]] }>()
function update(index: number, key: 'name' | 'inkColor', value: string) {
  emit('update:modelValue', props.modelValue.map((row, i) => i === index ? { ...row, [key]: value } : row))
}
</script>
