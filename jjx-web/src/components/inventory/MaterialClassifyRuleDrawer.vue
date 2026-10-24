<template>
  <el-drawer v-model="visible" title="归类规则（关键词 → 分类）" size="620px" append-to-body>
    <el-alert
      type="info"
      :closable="false"
      title="规则只用于导入/归类的建议，命中后仍是「规则命中未确认」，需人工确认。优先级大者先匹配。"
      style="margin-bottom: 12px"
    />
    <div class="toolbar">
      <el-button size="small" :loading="loading" @click="load">刷新</el-button>
      <span class="count">共 {{ rules.length }} 条</span>
    </div>
    <el-table v-loading="loading" :data="rules" size="small" border>
      <el-table-column label="关键词" prop="keyword" min-width="140" show-overflow-tooltip />
      <el-table-column label="匹配字段" width="110">
        <template #default="{ row }">{{ matchLabel(row.matchField) }}</template>
      </el-table-column>
      <el-table-column label="目标分类" prop="categoryCode" width="170" show-overflow-tooltip />
      <el-table-column label="优先级" prop="priority" width="80" align="center" />
      <el-table-column label="启用" width="80" align="center">
        <template #default="{ row }">
          <el-switch
            :model-value="row.enabled !== false"
            :loading="busyId === row.id"
            @change="toggle(row)"
          />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="80" align="center">
        <template #default="{ row }">
          <el-button link type="danger" :loading="busyId === row.id" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rules.length" description="暂无规则" />
  </el-drawer>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { materialClassifyApi } from '@/api/inventory/classify'
import type { MaterialClassifyRule } from '@/types/inventory/classify'

const props = withDefaults(defineProps<{ modelValue: boolean }>(), { modelValue: false })
const emit = defineEmits<{ (e: 'update:modelValue', value: boolean): void }>()
const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})
const rules = ref<MaterialClassifyRule[]>([])
const loading = ref(false)
const busyId = ref<number>()

const matchLabel = (value?: string) =>
  value === 'NAME' ? '名称' : value === 'SPEC' ? '规格' : '名称或规格'

async function load() {
  loading.value = true
  try {
    const response = await materialClassifyApi.rules()
    rules.value = response.data || []
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '规则加载失败')
  } finally {
    loading.value = false
  }
}

async function toggle(rule: MaterialClassifyRule) {
  if (!rule.id) return
  busyId.value = rule.id
  try {
    await materialClassifyApi.updateRule({ ...rule, enabled: rule.enabled === false })
    ElMessage.success(rule.enabled === false ? '已启用' : '已停用')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '操作失败')
  } finally {
    busyId.value = undefined
  }
}

async function remove(rule: MaterialClassifyRule) {
  if (!rule.id) return
  try {
    await ElMessageBox.confirm(`删除规则「${rule.keyword}」？`, '提示', { type: 'warning' })
  } catch {
    return
  }
  busyId.value = rule.id
  try {
    await materialClassifyApi.deleteRule(rule.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '删除失败')
  } finally {
    busyId.value = undefined
  }
}

watch(
  () => props.modelValue,
  (open) => {
    if (open) void load()
  }
)
</script>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;
}
.count {
  font-size: 12px;
  color: #909399;
}
</style>
