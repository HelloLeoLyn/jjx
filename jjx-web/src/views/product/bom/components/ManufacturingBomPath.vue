<template>
  <div class="manufacturing-bom-path">
    <el-breadcrumb separator="›">
      <el-breadcrumb-item v-for="(node, index) in nodes" :key="nodeKey(node, index)">
        <el-button
          v-if="index < nodes.length - 1"
          link
          type="primary"
          class="path-link"
          @click="emit('navigate', index)"
        >
          {{ labelOf(node) }}
        </el-button>
        <span v-else class="path-current">{{ labelOf(node) }}</span>
      </el-breadcrumb-item>
    </el-breadcrumb>
    <div class="path-actions">
      <el-button
        v-if="nodes.length > 1"
        link
        type="primary"
        class="path-back"
        @click="emit('navigate', nodes.length - 2)"
        >返回上层</el-button
      >
      <span class="path-hint">逐层查看：上层未保存的修改不会因查看下层而丢失</span>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * 制造BOM 层路径（dev-20261111-005）。
 * 只负责展示当前窗口的层级路径与返回，不判断制造关系、不读写数据：
 * 已选下层只来自 engineering_bom_item.sub_bom_id；未选方案的已建档物料进入候选时按物料身份展示，
 * 不由分组/规格/模数推断上下层关系。
 */
interface ManufacturingBomPathNode {
  /** 投入物料ID（= 该层制造方案的产出物料） */
  materialId?: number | null
  /** 该层使用的制造方案BOM ID */
  bomId?: number | null
  materialCode?: string
  materialName?: string
  /** 显式标签优先（如“当前产品”），否则用编码+名称 */
  label?: string
}

const props = withDefaults(defineProps<{ nodes?: ManufacturingBomPathNode[] }>(), {
  nodes: () => [],
})

const emit = defineEmits<{ (e: 'navigate', index: number): void }>()

const labelOf = (node: ManufacturingBomPathNode): string => {
  if (node.label) return node.label
  const text = [node.materialCode, node.materialName].filter((v) => !!String(v ?? '').trim()).join(' ')
  if (text) return text
  // 不把数据库自增ID当标题；档案未读到时就事论事
  return node.materialId != null ? '产出物料（档案未读取）' : '当前层'
}

const nodeKey = (node: ManufacturingBomPathNode, index: number): string =>
  `${index}-${node.bomId ?? 'draft'}-${node.materialId ?? 'none'}`

defineExpose({ labelOf })
</script>

<style scoped>
.manufacturing-bom-path {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 6px 10px;
  background: var(--el-fill-color-lighter, #f5f7fa);
  border-radius: 6px;
}

.manufacturing-bom-path :deep(.el-breadcrumb) {
  flex: 1;
  min-width: 0;
}

.path-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  white-space: nowrap;
}

.path-current {
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}

.path-hint {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}
</style>
