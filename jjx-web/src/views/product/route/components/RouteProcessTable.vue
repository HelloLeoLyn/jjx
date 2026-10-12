<template>
  <div class="route-process-table">
    <el-divider content-position="left">工序明细</el-divider>
    <el-tabs v-model="majorCategoryTab">
      <el-tab-pane :label="`🛠 冲型组装（${assemblyCount}）`" name="ASSEMBLY">
        <WorkSpecWorkflow :items="routeItems" />
      </el-tab-pane>
      <el-tab-pane :label="`🖨️ 印刷（${printCount}）`" name="PRINT">
        <ProductPrintSpecPanel :routing-items="routeItems" read-only />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { EngineeringRoutingItemVO } from '@/types/product/routing'
import WorkSpecWorkflow from '@/views/engineering/product-spec/components/WorkSpecWorkflow.vue'
import ProductPrintSpecPanel from '@/views/engineering/product-spec/components/ProductPrintSpecPanel.vue'
import type { SpecProcessRow } from '@/views/engineering/product-spec/components/workSpecProcessRows'

const props = defineProps<{ items: EngineeringRoutingItemVO[] }>()
const routeItems = computed(() => props.items as unknown as SpecProcessRow[])
const assemblyCount = computed(() => {
  const rows = props.items.filter((item) => item.majorCategory !== 'PRINT')
  if (rows.some((item) => Array.isArray(item.children))) return rows.length
  return new Set(rows.map((item, index) => item.groupId ? `group_${item.groupId}` : `independent_${index}`)).size
})
const printCount = computed(() => props.items.filter((item) => item.majorCategory === 'PRINT').length)
const majorCategoryTab = ref<'ASSEMBLY' | 'PRINT'>('ASSEMBLY')

watch(
  () => props.items,
  () => { majorCategoryTab.value = assemblyCount.value || !printCount.value ? 'ASSEMBLY' : 'PRINT' },
  { immediate: true }
)
</script>
