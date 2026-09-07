<template>
  <WidgetCard
    :title="`🗒️ 我的待办 · 待办任务 ${data.todoTotal} · 未读通知 ${data.unreadNotice}`"
    tone="todo"
  >
    <div v-if="data.todos.length" class="todo-list">
      <button
        v-for="item in data.todos.slice(0, 8)"
        :key="item.taskId"
        class="todo-row"
        type="button"
        @click="openTodo(item)"
      >
        <span class="todo-name">{{ item.title }}</span
        ><span class="todo-time">{{ item.createTime || '-' }}</span>
      </button>
    </div>
    <el-empty v-else-if="!data.unreadNotice" description="暂无待办，去休息一下" :image-size="60" />
    <div v-else class="notice-entry" @click="router.push('/notification/index')">
      有 {{ data.unreadNotice }} 条未读通知，点击查看
    </div>
  </WidgetCard>
</template>
<script setup lang="ts">
import { onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import request from '@/utils/request'
import { resolveJump } from '@/utils/bizJump'
import WidgetCard from '../components/WidgetCard.vue'
interface TodoItem {
  taskId: number
  title: string
  taskType?: string
  bizType?: string
  bizId?: number | string
  sourceEvent?: string
  deadline?: string
  createTime?: string
}
const router = useRouter()
const data = reactive<{ unreadNotice: number; todoTotal: number; todos: TodoItem[] }>({
  unreadNotice: 0,
  todoTotal: 0,
  todos: [],
})
function openTodo(item: TodoItem) {
  const target = resolveJump(item.sourceEvent || '', item.bizId)
  router.push(target || '/kanban/index')
}
onMounted(async () => {
  try {
    const res = await request.get('/dashboard/my-todos')
    if (res?.data)
      Object.assign(data, res.data, { todos: Array.isArray(res.data.todos) ? res.data.todos : [] })
  } catch (error) {
    console.warn('我的待办加载失败', error)
  }
})
</script>
<style scoped>
.todo-list {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 16px;
}
.todo-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
  padding: 10px 12px;
  color: #303133;
  background: #f7f8fa;
  border: 1px solid transparent;
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
}
.todo-row:hover {
  color: #409eff;
  background: #f5f9ff;
  border-color: #c6e2ff;
}
.todo-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.todo-time {
  flex-shrink: 0;
  font-size: 12px;
  color: #909399;
}
.notice-entry {
  padding: 14px;
  color: #409eff;
  text-align: center;
  background: #f5f9ff;
  border-radius: 8px;
  cursor: pointer;
}
@media (max-width: 700px) {
  .todo-list {
    grid-template-columns: 1fr;
  }
  .todo-time {
    display: none;
  }
}
</style>
