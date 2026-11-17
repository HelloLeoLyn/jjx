<template>
  <table class="processing-tree-table">
    <thead>
      <tr>
        <th>序号</th>
        <th v-if="materialName">品名</th>
        <th>加工关系 / 规格</th>
        <th>数量</th>
        <th>交货物料</th>
        <th v-if="$slots.actions">操作</th>
      </tr>
    </thead>
    <tbody>
      <tr
        v-for="row in rows"
        :key="row.node.nodeId"
        :class="{ 'tree-leaf': !row.node.children?.length }"
      >
        <td>{{ row.number }}</td>
        <td v-if="materialName">{{ materialName }}</td>
        <td :style="{ paddingLeft: `${8 + row.depth * 22}px` }">
          <span class="branch">{{ row.depth ? '└ ' : '' }}</span
          ><strong>{{ row.node.role }}</strong
          ><span v-if="row.node.operation">
            · {{ PurchaseProcessingOperationEnum.getLabel(row.node.operation.type) }}</span
          >
          <div>{{ spec(row.node) }}</div>
        </td>
        <td>{{ row.node.quantity }}{{ row.node.unit === 'ROLL' ? '卷' : '片' }}</td>
        <td>
          <template v-if="row.node.children?.length">加工中间料</template
          ><template v-else
            ><span :class="{ unbound: !row.node.materialId }">{{
              row.node.materialCode || '待指定交货物料'
            }}</span
            ><small v-if="row.node.bindingMessage">{{ row.node.bindingMessage }}</small></template
          >
        </td>
        <td v-if="$slots.actions"><slot name="actions" :node="row.node" /></td>
      </tr>
    </tbody>
  </table>
</template>
<script setup lang="ts">
import { computed } from 'vue'
import { PurchaseProcessingOperationEnum } from '@/enums/purchase/processing'
import { entryNumber } from '@/api/purchase/orderEntry'
import type { ProcessingTreeNode } from '@/api/purchase/orderEntry'
const props = defineProps<{ tree: ProcessingTreeNode; materialName?: string }>()
const rows = computed(() => {
  const all: { node: ProcessingTreeNode; depth: number; number: string }[] = []
  function visit(n: ProcessingTreeNode, depth: number, number: string) {
    all.push({ node: n, depth, number })
    n.children?.forEach((c, i) => visit(c, depth + 1, `${number}.${i + 1}`))
  }
  visit(props.tree, 0, '1')
  return all
})
const spec = (n: ProcessingTreeNode) =>
  `${entryNumber(n.widthMm)}mm × ${entryNumber(n.unit === 'ROLL' ? Number(n.lengthMm) / 1000 : n.lengthMm)}${n.unit === 'ROLL' ? 'm' : 'mm'}`
</script>
<style scoped>
.processing-tree-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.processing-tree-table th,
.processing-tree-table td {
  border: 1px solid #dcdfe6;
  padding: 8px;
  vertical-align: top;
  text-align: left;
  overflow-wrap: anywhere;
}
.processing-tree-table th {
  background: #f5f7fa;
}
.processing-tree-table td:first-child {
  white-space: nowrap;
}
.processing-tree-table strong {
  font-weight: 500;
}
.processing-tree-table small {
  display: block;
  color: #b88230;
}
.tree-leaf {
  background: #fbfdf9;
}
.unbound {
  color: #b88230;
}
.branch {
  color: #909399;
}
tr {
  break-inside: avoid;
}
@media print {
  .processing-tree-table th,
  .processing-tree-table td {
    border-color: #333;
    padding: 4px;
  }
}
</style>
