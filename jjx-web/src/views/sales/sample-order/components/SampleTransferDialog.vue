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

        <div class="section-title">工序（{{ store.sampleProcessCount }} 个组合）</div>
        <div v-if="store.sampleProcessGroups.length" class="process-list">
          <div v-for="group in store.sampleProcessGroups" :key="group.groupOrder" class="process-card">
            <div class="group-heading">
              <span>{{ group.processName || `组合 ${group.groupOrder}` }}</span>
              <span class="group-meta">组合 · {{ group.itemCount }} 项</span>
              <el-tag v-if="group.hasCustomProcessParams" size="small" type="warning" effect="plain">印刷</el-tag>
            </div>
            <div v-for="process in group.items" :key="process.processId" class="process-row">
              <span>{{ process.processName }}</span>
              <el-tag
                v-if="process.matched"
                size="small"
                type="success"
                effect="plain"
              >→ {{ process.matchedStdProcessName }}</el-tag>
              <el-tag
                v-else-if="process.customProcessParams"
                size="small"
                type="warning"
                effect="plain"
              >自定义印刷·原样转入</el-tag>
              <el-tag v-else size="small" type="danger" effect="plain">未匹配到标准工序</el-tag>
            </div>
          </div>
        </div>
        <el-empty v-else description="暂无打样工序" :image-size="60" />

        <div class="section-title">物料（{{ store.sampleMaterials.length }} 项）</div>
        <el-table :data="store.sampleMaterials" size="small" border stripe max-height="280">
          <el-table-column prop="sourceProcessName" label="来源组合" min-width="120" />
          <el-table-column label="物料" min-width="180">
            <template #default="scope">
              <div>{{ scope.row.name || '-' }}</div>
              <div v-if="scope.row.spec" class="secondary">{{ scope.row.spec }}</div>
            </template>
          </el-table-column>
          <el-table-column label="数量" width="110" align="center">
            <template #default="scope">{{ scope.row.qty ?? '-' }} {{ scope.row.unit || '' }}</template>
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
    </div>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :disabled="!store.allMatched" :loading="store.confirming" @click="onConfirm">
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
