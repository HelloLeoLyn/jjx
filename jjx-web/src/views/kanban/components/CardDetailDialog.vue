<template>
  <el-dialog
    v-model="visible"
    :title="card?.title ?? '卡片详情'"
    width="1200px"
    @close="onClose"
  >
    <template v-if="card">
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="编号" :span="2">
          {{ card.id }}
        </el-descriptions-item>
        <el-descriptions-item label="工单号" v-if="card.workOrderNo">
          {{ card.workOrderNo }}
        </el-descriptions-item>
        <el-descriptions-item label="当前工序" v-if="card.currentProcess">
          <el-tag size="small">{{ card.currentProcess }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="产品名称" v-if="card.productName">
          {{ card.productName }}
        </el-descriptions-item>
        <el-descriptions-item label="数量" v-if="card.quantity">
          {{ card.quantity.toLocaleString() }} pcs
        </el-descriptions-item>
        <el-descriptions-item label="客户" v-if="card.customer">
          {{ card.customer }}
        </el-descriptions-item>
        <el-descriptions-item label="优先级">
          <el-tag :type="priorityType" size="small" effect="dark">
            {{ priorityLabel }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType" size="small">
            {{ statusLabel }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="负责人">
          {{ card.assignee }}
        </el-descriptions-item>
        <el-descriptions-item label="截止日期" :class="{ 'text-danger': isOverdue }">
          {{ card.deadline }}
          <el-tag v-if="isOverdue" type="danger" size="small" effect="dark" style="margin-left: 8px">已逾期</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="任务类型" v-if="card.taskType">
          {{ card.taskType }}
        </el-descriptions-item>
        <el-descriptions-item label="部门" v-if="card.department">
          {{ card.department }}
        </el-descriptions-item>
        <el-descriptions-item label="紧急类型" v-if="card.urgencyType">
          <el-tag type="danger" size="small">{{ card.urgencyType }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="来源单号" v-if="card.sourceOrderNo">
          {{ card.sourceOrderNo }}
        </el-descriptions-item>
        <el-descriptions-item label="原因/备注" :span="2" v-if="card.remark">
          <div class="detail-remark">{{ card.remark }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="创建时间">
          {{ card.createdAt }}
        </el-descriptions-item>
        <el-descriptions-item label="更新时间">
          {{ card.updatedAt }}
        </el-descriptions-item>
        <el-descriptions-item label="物料状态" :span="2" v-if="card.extraData?.materialStatus">
          <el-tag
            :type="card.extraData.materialStatus === '齐料' ? 'success' : card.extraData.materialStatus === '待料' ? 'danger' : 'warning'"
            size="small"
          >
            {{ card.extraData.materialStatus }}
          </el-tag>
        </el-descriptions-item>
      </el-descriptions>

      <!-- 任务截图 -->
      <div v-if="screenshots.length > 0" class="detail-screenshots">
        <div class="screenshot-title">截图（{{ screenshots.length }}）</div>
        <div class="screenshot-list">
          <el-image
            v-for="(img, idx) in screenshots"
            :key="img.id"
            :src="img.url"
            :preview-src-list="screenshots.map(s => s.url)"
            :initial-index="idx"
            fit="cover"
            class="screenshot-thumb"
            preview-teleported
          />
        </div>
      </div>

      <div class="detail-actions">
        <el-input
          v-model="remarkEdit"
          type="textarea"
          :rows="2"
          placeholder="添加备注..."
          style="margin-top: 12px"
        />
        <div class="detail-buttons">
          <el-button type="primary" @click="onSaveRemark">保存备注</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { TagType } from '@/types'
import type { BoardCard } from '@/views/kanban/types/board'

const props = defineProps<{
  visible: boolean
  card: BoardCard | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  save: [cardId: string, updates: Partial<BoardCard>]
}>()

const remarkEdit = ref('')

// 任务截图（复用通用附件：bizType=task, bizId=taskId）
const screenshots = ref<{ id: number; url: string; name: string }[]>([])

function extractTaskId(cardId: string): string {
  return cardId.replace(/^TASK-/, '').replace(/^DEV-/, '')
}

async function loadScreenshots(card: BoardCard | null) {
  screenshots.value = []
  if (!card) return
  const taskId = Number(extractTaskId(card.id))
  if (!taskId) return
  try {
    const { attachmentApi } = await import('@/api/system/attachment')
    const res = await attachmentApi.list('task', taskId)
    const list = (res.data as any[]) || []
    screenshots.value = list
      .filter((a) => {
        const t = (a.fileType || a.fileName || '').toLowerCase()
        return t.includes('image') || /\.(png|jpe?g|gif|webp|bmp)$/.test(a.fileName || '')
      })
      .map((a) => ({
        id: a.id,
        url: attachmentApi.downloadUrl(a.id),
        name: a.fileName || '',
      }))
  } catch (e) {
    console.error('加载任务截图失败:', e)
  }
}

watch(() => props.card, (card) => {
  remarkEdit.value = card?.remark ?? ''
  loadScreenshots(card)
}, { immediate: true })

const visible = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
})

const priorityLabel = computed(() => {
  const map: Record<string, string> = { urgent: '紧急', high: '高', normal: '普通', low: '低' }
  return map[props.card?.priority ?? ''] ?? ''
})

const priorityType = computed<TagType>(() => {
  const map: Record<string, TagType> = { urgent: 'danger', high: 'warning', normal: 'info', low: 'info' }
  return map[props.card?.priority ?? ''] ?? 'info'
})

const statusLabel = computed(() => {
  const map: Record<string, string> = {
    pending: '待开始', in_progress: '进行中', review: '待审核',
    completed: '已完成', blocked: '阻塞', cancelled: '已取消',
  }
  return map[props.card?.status ?? ''] ?? ''
})

const statusType = computed<TagType>(() => {
  const map: Record<string, TagType> = {
    pending: 'info', in_progress: 'primary', review: 'warning',
    completed: 'success', blocked: 'danger', cancelled: 'info',
  }
  return map[props.card?.status ?? ''] ?? ''
})

const isOverdue = computed(() => {
  if (!props.card?.deadline) return false
  return props.card.deadline < new Date().toISOString().slice(0, 10)
})

function onClose() {
  visible.value = false
}

function onSaveRemark() {
  if (props.card) {
    emit('save', props.card.id, { remark: remarkEdit.value })
  }
}
</script>

<style scoped>
.detail-remark {
  white-space: pre-wrap;
  color: #606266;
}

.detail-actions {
  margin-top: 8px;
}

.detail-screenshots {
  margin-top: 12px;
}

.screenshot-title {
  font-size: 13px;
  font-weight: 600;
  color: #606266;
  margin-bottom: 8px;
}

.screenshot-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.screenshot-thumb {
  width: 96px;
  height: 72px;
  border-radius: 6px;
  border: 1px solid #e4e7ed;
  cursor: zoom-in;
}

.detail-buttons {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}

.text-danger {
  color: #f56c6c;
}
</style>
