<template>
  <table class="processing-tree-table">
    <thead>
      <tr>
        <th>序号</th>
        <th v-if="materialName">品名</th>
        <th>{{ deliveryOnly ? '来源 / 规格' : '加工关系 / 规格' }}</th>
        <th>数量</th>
        <th>交货物料</th>
        <th v-if="$slots.actions">操作</th>
      </tr>
    </thead>
    <tbody>
      <tr
        v-for="row in rows"
        :key="row.key"
        :data-node-id="row.key"
        :class="{ 'tree-leaf': !row.children.length, 'tree-group': row.group }"
      >
        <td>{{ row.number }}</td>
        <td v-if="materialName">{{ materialName }}</td>
        <td :style="{ paddingLeft: `${8 + (deliveryOnly ? 0 : row.depth * 22)}px` }">
          <small v-if="deliveryOnly">{{ row.path }}</small>
          <button
            v-else-if="collapsible && row.children.length"
            type="button"
            class="branch-toggle"
            :aria-expanded="!collapsed.has(row.key)"
            :aria-label="`${collapsed.has(row.key) ? '展开' : '折叠'} ${row.number} ${row.role}`"
            @click="toggle(row.key)"
          >
            {{ collapsed.has(row.key) ? '▸' : '▾' }}
          </button>
          <span v-else class="branch">{{ row.depth ? '└ ' : '' }}</span
          ><strong>{{ row.role }}</strong
          ><span v-if="row.node.operation && !row.sourceOnly">
            · {{ PurchaseProcessingOperationEnum.getLabel(row.node.operation.type) }}</span
          >
          <div>{{ processingTreeSpec(row.node) }}</div>
          <div
            v-if="row.node.operation?.type === PurchaseProcessingOperationEnum.CUT.value"
            class="cut-layout"
          >
            {{ processingCutLayout(row.node.operation) }}
          </div>
        </td>
        <td>{{ row.quantity }}{{ row.node.unit === 'ROLL' ? '卷' : '片' }}</td>
        <td>
          <template v-if="row.sourceOnly || (row.depth === 0 && row.children.length)"
            >来源原料</template
          ><template v-else-if="row.group">加工分组</template
          ><template v-else-if="row.children.length">加工中间料</template
          ><template v-else-if="row.node.delivery === PurchaseProcessingDeliveryEnum.SCRAP.value"
            ><strong class="scrap">报废不入库</strong
            ><small>{{ row.node.disposalReason }}</small></template
          ><template v-else
            ><span :class="{ unbound: !row.node.materialId }">{{
              row.node.materialCode || '待指定交货物料'
            }}</span
            ><small v-if="row.node.bindingMessage">{{ row.node.bindingMessage }}</small></template
          >
        </td>
        <td v-if="$slots.actions">
          <span v-if="row.sourceOnly">在下方加工分组维护</span>
          <slot
            v-else
            name="actions"
            :node="row.node"
            :source-label="row.sourceLabel"
            :slit-sources="row.slitSources"
          />
        </td>
      </tr>
    </tbody>
  </table>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import {
  PurchaseProcessingOperationEnum,
  PurchaseProcessingDeliveryEnum,
} from '@/enums/purchase/processing'
import type { ProcessingTreeNode } from '@/api/purchase/orderEntry'
import {
  processingTreeDisplayRows,
  processingTreeSpec,
  processingCutLayout,
} from './processingTreeDisplay'
const props = defineProps<{
  tree: ProcessingTreeNode
  materialName?: string
  collapsible?: boolean
  expandNodeId?: string
  expandRevision?: number
  deliveryOnly?: boolean
}>()
const collapsed = ref(new Set<string>())
const allRows = computed(() => processingTreeDisplayRows(props.tree))
function toggle(key: string) {
  if (collapsed.value.has(key)) collapsed.value.delete(key)
  else collapsed.value.add(key)
}
watch(
  () => props.expandRevision,
  () => {
    const targets = allRows.value.filter((row) => row.node.nodeId === props.expandNodeId)
    for (const row of allRows.value) {
      if (
        targets.some(
          (target) =>
            target.number === row.number ||
            target.number.startsWith(`${row.number}.`) ||
            row.number.startsWith(`${target.number}.`)
        )
      )
        collapsed.value.delete(row.key)
    }
  },
  { flush: 'post' }
)
const rows = computed(() => {
  const all = allRows.value
  return props.deliveryOnly
    ? all.filter(
        (row) =>
          !row.children.length &&
          !row.node.operation &&
          row.node.delivery !== PurchaseProcessingDeliveryEnum.SCRAP.value
      )
    : props.collapsible
      ? all.filter(
          (row) =>
            !all.some(
              (parent) =>
                collapsed.value.has(parent.key) && row.number.startsWith(`${parent.number}.`)
            )
        )
      : all
})
</script>
<style scoped>
.branch-toggle {
  border: 0;
  background: none;
  padding: 0 6px 0 0;
  cursor: pointer;
  color: #606266;
  font-size: 15px;
}

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
.tree-group {
  background: #f0f7ff;
}
.cut-layout {
  color: #606266;
  font-size: 11px;
}
.unbound {
  color: #b88230;
}
.scrap {
  color: #f56c6c;
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
