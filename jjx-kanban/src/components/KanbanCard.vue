<template>
  <div
    class="kanban-card"
    :class="[priorityClass, statusClass]"
    @click="onClick"
  >
    <div class="card-header">
      <el-tag
        :type="priorityTagType"
        size="small"
        effect="dark"
        class="priority-tag"
      >
        {{ priorityLabel }}
      </el-tag>
      <span class="card-id">{{ card.id }}</span>
    </div>

    <div class="card-title">{{ card.title }}</div>

    <div v-if="card.productName" class="card-field">
      <el-icon><Goods /></el-icon>
      {{ card.productName }}
    </div>

    <div v-if="card.quantity" class="card-field">
      <el-icon><Ticket /></el-icon>
      {{ card.quantity.toLocaleString() }} pcs
    </div>

    <div v-if="card.customer" class="card-field">
      <el-icon><UserFilled /></el-icon>
      {{ card.customer }}
    </div>

    <div v-if="card.reason" class="card-field card-reason">
      <el-icon><Warning /></el-icon>
      {{ card.reason }}
    </div>

    <div v-if="card.department" class="card-field">
      <el-icon><OfficeBuilding /></el-icon>
      {{ card.department }}
    </div>

    <div class="card-footer">
      <div class="card-assignee">
        <el-avatar :size="20" style="background: #409eff">{{ card.assignee?.slice(0, 1) }}</el-avatar>
        <span>{{ card.assignee }}</span>
      </div>
      <div class="card-deadline" :class="{ overdue: isOverdue }">
        <el-icon><Clock /></el-icon>
        {{ card.deadline }}
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { BoardCard } from '@/types/board'
import { Goods, Ticket, UserFilled, Warning, Clock, OfficeBuilding } from '@element-plus/icons-vue'

const props = defineProps<{
  card: BoardCard
}>()

const emit = defineEmits<{
  click: [cardId: string]
}>()

const priorityLabel = computed(() => {
  const map: Record<string, string> = { urgent: '紧急', high: '高', normal: '普通', low: '低' }
  return map[props.card.priority] ?? '普通'
})

const priorityTagType = computed(() => {
  const map: Record<string, string> = { urgent: 'danger', high: 'warning', normal: 'info', low: '' }
  return map[props.card.priority] ?? ''
})

const priorityClass = computed(() => `priority-${props.card.priority}`)

const statusClass = computed(() => `status-${props.card.status}`)

const isOverdue = computed(() => {
  if (!props.card.deadline) return false
  return props.card.deadline < new Date().toISOString().slice(0, 10)
})

function onClick() {
  emit('click', props.card.id)
}
</script>

<style scoped>
.kanban-card {
  background: #fff;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 8px;
  cursor: pointer;
  border: 1px solid #e4e7ed;
  transition: box-shadow 0.2s, transform 0.15s;
  font-size: 13px;
}

.kanban-card:hover {
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
  transform: translateY(-1px);
}

.kanban-card.priority-urgent {
  border-left: 3px solid #f56c6c;
}

.kanban-card.priority-high {
  border-left: 3px solid #e6a23c;
}

.kanban-card.priority-normal {
  border-left: 3px solid #409eff;
}

.kanban-card.priority-low {
  border-left: 3px solid #c0c4cc;
}

.kanban-card.status-blocked {
  opacity: 0.75;
  background: #f5f7fa;
}

.kanban-card.status-completed {
  background: #f0f9eb;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.priority-tag {
  flex-shrink: 0;
}

.card-id {
  color: #909399;
  font-size: 11px;
  font-family: monospace;
}

.card-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 6px;
  line-height: 1.4;
  color: #303133;
}

.card-field {
  display: flex;
  align-items: center;
  gap: 4px;
  color: #606266;
  font-size: 12px;
  margin-bottom: 3px;
  line-height: 1.4;
}

.card-field .el-icon {
  font-size: 12px;
  flex-shrink: 0;
}

.card-reason {
  color: #e6a23c;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
}

.card-assignee {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: #909399;
}

.card-deadline {
  display: flex;
  align-items: center;
  gap: 3px;
  font-size: 11px;
  color: #909399;
}

.card-deadline.overdue {
  color: #f56c6c;
  font-weight: 600;
}
</style>
