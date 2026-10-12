<template>
  <section v-loading="busy || fetching" class="data-section">
    <div class="section-heading">
      <h3>作业流程</h3>
      <el-button
        v-if="editable"
        :disabled="busy || fetching || !currentRoutingId"
        @click="emit('edit')"
      >编辑</el-button>
    </div>
    <el-alert v-if="loadError" :title="loadError" type="error" :closable="false">
      <el-button link type="primary" @click="loadRouting">重新加载</el-button>
    </el-alert>
    <el-tabs v-model="workflowTab">
      <el-tab-pane
        v-for="group in workflowGroups"
        :key="group.value"
        :name="group.value"
        :label="group.label"
      >
        <el-table
          :data="group.rows"
          border
          size="small"
          :empty-text="`暂无${group.label}冲型组装工序`"
        >
          <el-table-column type="index" label="序号" width="70" />
          <el-table-column label="流程" min-width="220">
            <template #default="{ row }">
              <div class="workflow-operation">
                <div class="workflow-symbols">
                  <template v-for="(item, i) in workflowOperations(row)" :key="item.itemId || i">
                    <span v-if="i">＋</span>
                    <span class="workflow-symbol">
                      <SvgIcon v-if="item.icon" :name="item.icon" :size="24" />
                      <span v-else>{{ item.processName || '-' }}</span>
                      <sub v-if="workSpecStepSubscript(item)">{{ workSpecStepSubscript(item) }}</sub>
                    </span>
                  </template>
                </div>
                <span class="workflow-remark">{{ workSpecOperationRemark(row) }}</span>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="耗时(h)" width="100">
            <template #default="{ row }">{{ workflowLabor(row) }}</template>
          </el-table-column>
          <el-table-column label="工序名称" min-width="180">
            <template #default="{ row }">{{ workflowNames(row) }}</template>
          </el-table-column>
          <el-table-column label="备注" min-width="180">
            <template #default="{ row }">
              <span class="multiline">{{ workSpecOperationRemark(row) || '-' }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </section>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import SvgIcon from '@/components/SvgIcon/index.vue'
import { productWorkSpecApi } from '@/api/product/workSpec'
import { flowGroups, workSpecOperationRemark, workSpecStepSubscript } from './docset'
import {
  processRowsForGroup,
  workflowLabor,
  workflowNames,
  workflowOperations,
  type SpecProcessRow,
} from './workSpecProcessRows'

/** 传入 items 时直接展示；不传 items 时按 productId 查询作业规范来源中的路线。 */
const props = defineProps<{
  items?: SpecProcessRow[]
  productId?: number
  routingId?: number
  editable?: boolean
  busy?: boolean
}>()
const emit = defineEmits<{ edit: [] }>()
const workflowTab = ref(flowGroups[0]?.value || '')
const fetchedItems = ref<SpecProcessRow[]>([])
const fetchedRoutingId = ref<number>()
const fetching = ref(false)
const loadError = ref('')
let generation = 0

const currentItems = computed(() => props.items ?? fetchedItems.value)
const currentRoutingId = computed(() => props.routingId ?? fetchedRoutingId.value)
const workflowGroups = computed(() =>
  flowGroups.map((group) => ({
    ...group,
    rows: processRowsForGroup(currentItems.value, 'ASSEMBLY', group.value),
  }))
)

async function loadRouting() {
  const requestId = ++generation
  if (props.items !== undefined || !props.productId) {
    fetchedItems.value = []
    fetchedRoutingId.value = undefined
    loadError.value = ''
    fetching.value = false
    return
  }
  fetchedItems.value = []
  fetchedRoutingId.value = undefined
  fetching.value = true
  loadError.value = ''
  try {
    const response = await productWorkSpecApi.get(props.productId)
    if (requestId !== generation) return
    const routing = response.data?.sourceData?.routing
    fetchedItems.value = routing?.items || []
    fetchedRoutingId.value = routing?.routingId ? Number(routing.routingId) : undefined
  } catch (error) {
    if (requestId === generation) {
      loadError.value = error instanceof Error ? error.message : '作业流程加载失败，请重试'
    }
  } finally {
    if (requestId === generation) fetching.value = false
  }
}

watch(() => [props.items, props.productId], () => { void loadRouting() }, { immediate: true })
onBeforeUnmount(() => { generation++ })
</script>

<style scoped>
.data-section {
  margin-bottom: 20px;
  padding: 16px;
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
}
.section-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.section-heading h3 {
  margin: 0;
  font-size: 15px;
}
.workflow-operation {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.workflow-symbols {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
}
.workflow-symbol {
  display: inline-flex;
  align-items: flex-end;
}
.workflow-symbol sub {
  margin-left: 2px;
  white-space: pre-wrap;
}
.workflow-remark,
.multiline {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
</style>
