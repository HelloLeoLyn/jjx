<template>
  <el-dialog v-model="visible" :title="card?.title ?? '卡片详情'" width="1080px" @close="onClose">
    <!-- ① dev/office 任务：完整 SysTask 全字段 -->
    <template v-if="taskDetail">
      <el-descriptions class="task-desc" :column="2" border size="small" label-width="120px">
        <el-descriptions-item label="任务ID">{{ taskDetail.taskId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="任务编码">{{
          taskDetail.taskCode || '-'
        }}</el-descriptions-item>

        <el-descriptions-item label="任务类型">{{
          taskDetail.taskType || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(taskDetail.status)" size="small">{{
            statusLabel(taskDetail.status)
          }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="优先级">
          <el-tag :type="priorityType(taskDetail.priority)" size="small" effect="dark">{{
            priorityLabel(taskDetail.priority)
          }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="负责人">
          {{ taskDetail.assigneeName || '-' }}
          <span v-if="taskDetail.assigneeId" style="color: #909399"
            >（#{{ taskDetail.assigneeId }}）</span
          >
        </el-descriptions-item>
        <el-descriptions-item label="指派角色">
          {{ taskDetail.assignRole ? '角色#' + taskDetail.assignRole : '-' }}
        </el-descriptions-item>
        <el-descriptions-item label="业务类型">{{
          taskDetail.bizType || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="业务ID">{{ taskDetail.bizId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="来源事件">{{
          taskDetail.sourceEvent || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="来源ID">{{ taskDetail.sourceId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="结果类型">{{
          taskDetail.resultType || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="结果ID">{{ taskDetail.resultId ?? '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建人">{{ taskDetail.createBy || '-' }}</el-descriptions-item>
        <el-descriptions-item label="更新人">{{ taskDetail.updateBy || '-' }}</el-descriptions-item>

        <el-descriptions-item label="开始时间">{{
          taskDetail.startTime || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="截止日期">
          <span :class="{ 'text-danger': isOverdue }">{{ taskDetail.deadline || '-' }}</span>
          <el-tag v-if="isOverdue" type="danger" size="small" effect="dark" style="margin-left: 8px"
            >已逾期</el-tag
          >
        </el-descriptions-item>
        <el-descriptions-item label="完成时间">{{
          taskDetail.completedTime || '-'
        }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{
          fmtTime(taskDetail.createTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="更新时间">{{
          fmtTime(taskDetail.updateTime)
        }}</el-descriptions-item>
        <el-descriptions-item label="验收用例" :span="2" v-if="tcList.length">
          <el-tag
            v-for="tc in tcList"
            :key="tc"
            size="small"
            type="success"
            class="tc-tag"
            @click="openTestBench(tc)"
            >{{ tc }}</el-tag
          >
          <span style="color: #909399; font-size: 12px; margin-left: 4px">（点标签去测试工作台）</span>
        </el-descriptions-item>
        <el-descriptions-item label="描述" :span="2">
          <div class="detail-text">{{ taskDetail.description || '-' }}</div>
        </el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">
          <div class="detail-text">{{ taskDetail.remark || '-' }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </template>

    <!-- ② 生产工单卡片：保留原字段展示 -->
    <template v-else-if="card">
      <el-descriptions class="task-desc" :column="2" border size="small" label-width="120px">
        <el-descriptions-item label="任务id">{{ card.id }}</el-descriptions-item>
        <el-descriptions-item label="任务编码">{{ card.taskCode }}</el-descriptions-item>
        <el-descriptions-item v-if="card.workOrderNo" label="工单号">{{
          card.workOrderNo
        }}</el-descriptions-item>
        <el-descriptions-item v-if="card.currentProcess" label="当前工序">
          <el-tag size="small">{{ card.currentProcess }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item v-if="card.productName" label="产品名称">{{
          card.productName
        }}</el-descriptions-item>
        <el-descriptions-item v-if="card.quantity" label="数量"
          >{{ card.quantity.toLocaleString() }} pcs</el-descriptions-item
        >
        <el-descriptions-item v-if="card.customer" label="客户">{{
          card.customer
        }}</el-descriptions-item>
        <el-descriptions-item label="优先级">
          <el-tag :type="priorityType(card.priority)" size="small" effect="dark">{{
            priorityLabel(card.priority)
          }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(card.status)" size="small">{{
            statusLabel(card.status)
          }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="负责人">{{ card.assignee }}</el-descriptions-item>
        <el-descriptions-item label="截止日期">{{ card.deadline }}</el-descriptions-item>
        <el-descriptions-item v-if="card.taskType" label="任务类型">{{
          card.taskType
        }}</el-descriptions-item>
        <el-descriptions-item v-if="card.department" label="部门">{{
          card.department
        }}</el-descriptions-item>
        <el-descriptions-item v-if="card.sourceOrderNo" label="来源单号">{{
          card.sourceOrderNo
        }}</el-descriptions-item>
        <el-descriptions-item v-if="card.reason" label="原因">{{
          card.reason
        }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ card.createdAt }}</el-descriptions-item>
        <el-descriptions-item v-if="card.remark" label="描述/备注" :span="2">
          <div class="detail-text">{{ card.remark }}</div>
        </el-descriptions-item>
      </el-descriptions>
    </template>

    <!-- 任务截图 -->
    <div v-if="screenshots.length > 0" class="detail-screenshots">
      <div class="screenshot-title">截图（{{ screenshots.length }}）</div>
      <div class="screenshot-list">
        <el-image
          v-for="(img, idx) in screenshots"
          :key="img.id"
          :src="img.url"
          :preview-src-list="screenshots.map((s) => s.url)"
          :initial-index="idx"
          fit="cover"
          class="screenshot-thumb"
          preview-teleported
        />
      </div>
    </div>
    <template #footer>
      <el-button @click="onClose">关闭</el-button>
      <el-button v-if="jumpTarget" type="primary" @click="goToBiz">去处理</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import type { TagType } from '@/types'
import type { BoardCard } from '@/views/kanban/types/board'
import http from '@/utils/request'
import { useRouter } from 'vue-router'
import { resolveJump, resolveModulePage } from '@/utils/bizJump'

const props = defineProps<{
  visible: boolean
  card: BoardCard | null
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
}>()
const router = useRouter()

/** 完整任务详情（dev/office 走 /kanban/board/{module}/tasks/{taskId}） */
const taskDetail = ref<any>(null)

/** 任务截图（复用通用附件：bizType=task, bizId=taskId） */
const screenshots = ref<{ id: number; url: string; name: string }[]>([])

function extractTaskId(cardId: string): string {
  return cardId.replace(/^TASK-/, '').replace(/^DEV-/, '')
}

function isSysTaskModule(templateType?: string): boolean {
  return templateType === 'dev' || templateType === 'office'
}

async function loadDetail(card: BoardCard | null) {
  taskDetail.value = null
  screenshots.value = []
  if (!card) return
  const taskId = Number(extractTaskId(card.id))
  if (!taskId) return
  // dev/office：sys_task 全字段详情
  if (card.templateType && isSysTaskModule(card.templateType)) {
    try {
      const res: any = await http.get(`/kanban/board/${card.templateType}/tasks/${taskId}`)
      if (res?.code === 200 && res.data) {
        taskDetail.value = res.data
      }
    } catch (e) {
      console.warn('任务详情加载失败', e)
    }
  }
  // 截图（dev/office 与 production 共用）
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

watch(
  () => props.card,
  (card) => {
    loadDetail(card)
  },
  { immediate: true }
)

const visible = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
})

const jumpTarget = computed(() => {
  // dev/emergency 是开发需求/紧急事项卡，无业务单据可跳（方案确认范围）
  if (props.card?.templateType === 'dev' || props.card?.templateType === 'emergency') return null
  if (taskDetail.value) {
    return (
      resolveJump(taskDetail.value.sourceEvent || '', taskDetail.value.bizId) ||
      (resolveModulePage(taskDetail.value.bizType || '')
        ? { path: resolveModulePage(taskDetail.value.bizType || '') as string }
        : null)
    )
  }
  return props.card?.templateType === 'production' ? { path: '/production/order' } : null
})

function goToBiz() {
  if (!jumpTarget.value) return
  visible.value = false
  router.push(jumpTarget.value)
}

function priorityLabel(p?: string): string {
  const map: Record<string, string> = { urgent: '紧急', high: '高', normal: '普通', low: '低' }
  return map[p ?? ''] ?? p ?? '-'
}

function priorityType(p?: string): TagType {
  const map: Record<string, TagType> = {
    urgent: 'danger',
    high: 'warning',
    normal: 'info',
    low: 'info',
  }
  return map[p ?? ''] ?? 'info'
}

function statusLabel(s?: number | string): string {
  const map: Record<string, string> = {
    '0': '待开始',
    '1': '进行中',
    '2': '待审核',
    '3': '阻塞',
    '4': '已废弃',
    '10': '已完成',
    pending: '待开始',
    in_progress: '进行中',
    review: '待审核',
    completed: '已完成',
    blocked: '阻塞',
    cancelled: '已取消',
  }
  return map[String(s ?? '')] ?? String(s ?? '-')
}

function statusType(s?: number | string): TagType {
  const map: Record<string, TagType> = {
    '0': 'info',
    '1': 'primary',
    '2': 'warning',
    '3': 'danger',
    '4': 'info',
    '10': 'success',
    pending: 'info',
    in_progress: 'primary',
    review: 'warning',
    completed: 'success',
    blocked: 'danger',
    cancelled: 'info',
  }
  return map[String(s ?? '')] ?? 'info'
}

const isOverdue = computed(() => {
  const d = taskDetail.value?.deadline || props.card?.deadline
  if (!d) return false
  return String(d) < new Date().toISOString().slice(0, 10)
})

/** 验收用例 TC 列表（sys_task.test_cases，逗号分隔） */
const tcList = computed<string[]>(() => {
  const raw = taskDetail.value?.testCases || ''
  return String(raw)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean)
})

function openTestBench(tc: string) {
  window.open(`http://localhost:8899/test/#tc-${tc.toLowerCase()}`, '_blank')
}

function fmtTime(v?: string | number): string {
  if (v === null || v === undefined || v === '') return '-'
  return String(v).replace('T', ' ').slice(0, 19)
}

function onClose() {
  visible.value = false
}
</script>

<style scoped>
/* 等宽布局：每行两列平均分配（label 固定宽，内容列等宽），描述/备注 span=2 独占整行 */
.task-desc :deep(.el-descriptions__table) {
  table-layout: fixed;
  width: 100%;
}
.task-desc :deep(.el-descriptions__label) {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.task-desc :deep(.el-descriptions__content) {
  word-break: break-all;
}

.tc-tag {
  cursor: pointer;
}

.detail-text {
  white-space: pre-wrap;
  color: #303133;
  max-height: 200px;
  overflow-y: auto;
  width: 100%;
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

.text-danger {
  color: #f56c6c;
}
</style>
