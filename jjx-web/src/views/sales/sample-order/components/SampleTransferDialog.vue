<template>
  <el-dialog
    v-model="visible"
    title="打样转标准"
    width="900px"
    append-to-body
    destroy-on-close
    :close-on-click-modal="false"
    @open="onOpen"
  >
    <div v-loading="store.loading" class="preview-body">
      <template v-if="!store.loading && store.preview">
        <el-descriptions :column="1" border size="small" class="order-summary">
          <el-descriptions-item label="样品单号">{{ store.orderNo || '-' }}</el-descriptions-item>
        </el-descriptions>

        <el-alert
          v-if="transferredFlag"
          type="warning"
          show-icon
          :closable="false"
          title="该样品单已转移过，再次确认将生成新的 BOM/工艺路线版本"
          class="notice"
        />
        <el-alert
          v-if="!store.allMatched"
          type="error"
          show-icon
          :closable="false"
          :title="unmatchedMessage"
          class="notice"
        />
        <el-alert
          v-else
          type="success"
          show-icon
          :closable="false"
          title="已完成自动匹配，确认后将直接建档并生成新版本 BOM/工艺路线"
          class="notice"
        />

        <el-steps :active="activeStep" finish-status="success" simple class="transfer-steps">
          <el-step title="工艺路线" />
          <el-step title="物料" />
        </el-steps>

        <template v-if="activeStep === 0">
          <div class="section-title">工序（{{ store.sampleProcessCount }} 个组合）</div>
          <el-tabs
            v-if="store.sampleProcessGroups.length"
            v-model="activeProcessModule"
            type="border-card"
          >
            <el-tab-pane label="冲形组装" name="ASSEMBLY">
              <el-tabs v-model="activeAssemblyCategory">
                <el-tab-pane
                  v-for="tab in processCategoryTabs"
                  :key="tab.value"
                  :label="`${tab.label}（${processGroups('ASSEMBLY', tab.value).length}）`"
                  :name="tab.value"
                >
                  <div class="process-list">
                    <div
                      v-for="group in processGroups('ASSEMBLY', tab.value)"
                      :key="group.groupOrder"
                      class="process-card"
                    >
                      <div class="group-heading">
                        <span>{{ group.processName || `组合 ${group.groupOrder}` }}</span>
                        <span class="group-meta">组合 · {{ group.itemCount }} 项</span>
                      </div>
                      <div
                        v-for="process in group.items"
                        :key="process.processId"
                        class="process-row"
                      >
                        <span>{{ process.processName }}</span>
                        <el-tag v-if="process.matched" size="small" type="success" effect="plain">
                          → {{ process.matchedStdProcessName }}
                        </el-tag>
                        <el-tag v-else size="small" type="danger" effect="plain">
                          未匹配到标准工序
                        </el-tag>
                      </div>
                    </div>
                    <el-empty
                      v-if="!processGroups('ASSEMBLY', tab.value).length"
                      :description="`暂无${tab.label}冲形组装工序`"
                      :image-size="60"
                    />
                  </div>
                </el-tab-pane>
              </el-tabs>
            </el-tab-pane>
            <el-tab-pane label="印刷" name="PRINT">
              <el-tabs v-model="activePrintCategory">
                <el-tab-pane
                  v-for="tab in processCategoryTabs"
                  :key="tab.value"
                  :label="`${tab.label}（${processGroups('PRINT', tab.value).length}）`"
                  :name="tab.value"
                >
                  <div class="process-list">
                    <div
                      v-for="group in processGroups('PRINT', tab.value)"
                      :key="group.groupOrder"
                      class="process-card"
                    >
                      <div class="group-heading">
                        <span>{{ group.processName || `组合 ${group.groupOrder}` }}</span>
                        <span class="group-meta">组合 · {{ group.itemCount }} 项</span>
                      </div>
                      <div
                        v-for="process in group.items"
                        :key="process.processId"
                        class="process-row"
                      >
                        <span>{{ process.processName }}</span>
                        <el-tag v-if="process.matched" size="small" type="success" effect="plain">
                          → {{ process.matchedStdProcessName }}
                        </el-tag>
                        <el-tag v-else size="small" type="warning" effect="plain">
                          自定义印刷·原样转入
                        </el-tag>
                      </div>
                    </div>
                    <el-empty
                      v-if="!processGroups('PRINT', tab.value).length"
                      :description="`暂无${tab.label}印刷工序`"
                      :image-size="60"
                    />
                  </div>
                </el-tab-pane>
              </el-tabs>
            </el-tab-pane>
          </el-tabs>
          <el-empty v-else description="暂无打样工序" :image-size="60" />
        </template>

        <template v-else>
          <div class="section-title">物料（{{ store.sampleMaterials.length }} 项）</div>
          <el-table :data="store.sampleMaterials" size="small" border stripe max-height="420">
            <el-table-column prop="sourceProcessName" label="来源组合" min-width="120" />
            <el-table-column label="物料" min-width="180">
              <template #default="scope">
                <div>{{ scope.row.name || '-' }}</div>
                <div v-if="scope.row.spec" class="secondary">{{ scope.row.spec }}</div>
              </template>
            </el-table-column>
            <el-table-column label="数量" width="110" align="center">
              <template #default="scope"
                >{{ scope.row.qty ?? '-' }} {{ scope.row.unit || '' }}</template
              >
            </el-table-column>
            <el-table-column label="自动匹配" min-width="210">
              <template #default="scope">
                <el-tag v-if="scope.row.matched" size="small" type="success" effect="plain">
                  → {{ scope.row.matchedMaterialName }}
                </el-tag>
                <el-tag v-else size="small" type="danger" effect="plain">未匹配到标准物料</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </template>
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button v-if="activeStep === 1" @click="activeStep = 0">上一步</el-button>
      <el-button v-if="activeStep === 0" type="primary" @click="activeStep = 1"> 下一步 </el-button>
      <el-button
        v-else
        type="primary"
        :disabled="!store.allMatched"
        :loading="store.confirming"
        @click="onConfirm"
      >
        确认转移
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useSampleTransferStore } from '@/store/modules/sampleTransfer'

const props = defineProps<{
  modelValue: boolean
  orderId?: number | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', value: boolean): void
  (e: 'success'): void
}>()

const store = useSampleTransferStore()
const visible = ref(props.modelValue)
const transferredFlag = ref(false)
const TRANSFER_FLAG_KEY = 'sample_transferred_orders'
const activeStep = ref(0)
const activeProcessModule = ref<'ASSEMBLY' | 'PRINT'>('ASSEMBLY')
const activeAssemblyCategory = ref('PANEL')
const activePrintCategory = ref('PANEL')
const processCategoryTabs = [
  { value: 'PANEL', label: '面板' },
  { value: 'UP_LINE', label: '上线' },
  { value: 'DOWN_LINE', label: '下线' },
] as const

function processGroups(module: 'ASSEMBLY' | 'PRINT', category: string) {
  return store.sampleProcessGroups.filter((group) => {
    const isPrint = group.hasCustomProcessParams
    return (module === 'PRINT' ? isPrint : !isPrint) && group.items[0]?.processCategory === category
  })
}

const unmatchedMessage = computed(() => {
  const processes = store.unmatchedProcesses.map((item) => item.processName).filter(Boolean)
  const materials = store.unmatchedMaterials.map((item) => item.name).filter(Boolean)
  const details = [
    processes.length ? `工序：${processes.join('、')}` : '',
    materials.length ? `材料：${materials.join('、')}` : '',
  ].filter(Boolean)
  return `以下工序/材料未匹配到标准项，请先在标准工序/物料库建档后再转移：${details.join('；')}`
})

function getTransferredIds(): number[] {
  try {
    const value = JSON.parse(localStorage.getItem(TRANSFER_FLAG_KEY) || '[]')
    return Array.isArray(value) ? value : []
  } catch {
    return []
  }
}

function markTransferred(orderId: number) {
  const ids = getTransferredIds()
  if (!ids.includes(orderId)) {
    ids.push(orderId)
    localStorage.setItem(TRANSFER_FLAG_KEY, JSON.stringify(ids))
  }
}

watch(
  () => props.modelValue,
  (value) => {
    visible.value = value
  }
)
watch(visible, (value) => emit('update:modelValue', value))

async function onOpen() {
  if (!props.orderId) return
  activeStep.value = 0
  transferredFlag.value = getTransferredIds().includes(props.orderId)
  await store.loadPreview(props.orderId)
}

async function onConfirm() {
  const result = await store.confirmTransfer()
  if (!result) return
  if (props.orderId) markTransferred(props.orderId)
  ElMessage.success('建档/版本化完成，可去产品档案/BOM/工艺路线完善后提交审核')
  visible.value = false
  emit('success')
}
</script>

<style scoped>
.preview-body {
  min-height: 200px;
}

.order-summary,
.notice {
  margin-bottom: 14px;
}

.transfer-steps {
  margin: 18px 0;
}

.section-title {
  margin: 18px 0 8px;
  color: #303133;
  font-weight: 600;
}

.process-list {
  display: grid;
  gap: 10px;
}

.process-card {
  padding: 10px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.group-heading,
.process-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.group-heading {
  margin-bottom: 8px;
  font-weight: 600;
}

.group-meta,
.secondary {
  color: #909399;
  font-size: 12px;
}

.process-row {
  justify-content: space-between;
  padding: 5px 0;
  border-top: 1px dashed #ebeef5;
}
</style>
