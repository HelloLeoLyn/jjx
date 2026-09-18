<template>
  <el-dialog
    v-model="visibleModel"
    :title="`新建${templateName}卡片`"
    width="880px"
    @close="onClose"
  >
    <el-form :model="form" label-width="80px" size="small">
      <el-form-item label="标题" required>
        <el-input v-model="form.title" placeholder="请输入卡片标题" />
      </el-form-item>

      <el-form-item label="负责人">
        <el-select v-model="form.assignee" style="width: 100%">
          
        </el-select>
      </el-form-item>

      <el-form-item label="截止日期">
        <el-date-picker v-model="form.deadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
      </el-form-item>

      <el-form-item label="优先级">
        <el-select v-model="form.priority" style="width: 100%">
          <el-option label="🔥 紧急" value="urgent" />
          <el-option label="⏫ 高" value="high" />
          <el-option label="➖ 普通" value="normal" />
          <el-option label="⬇️ 低" value="low" />
        </el-select>
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="5" />
      </el-form-item>

      <el-form-item label="截图">
        <div
          ref="chatRef"
          class="screenshot-chat"
          tabindex="0"
          @paste="handlePaste"
          @click="focusChat"
        >
          <div v-if="form.screenshots.length > 0" class="screenshot-bubbles">
            <div v-for="(img, idx) in form.screenshots" :key="idx" class="shot-bubble">
              <el-image
                :src="img.url"
                fit="cover"
                class="shot-thumb"
                preview-teleported
                :preview-src-list="form.screenshots.map((s) => s.url)"
                :initial-index="idx"
              />
              <el-icon class="shot-remove" @click="form.screenshots.splice(idx, 1)"><Close /></el-icon>
            </div>
          </div>
          <div class="chat-placeholder">
            <el-icon style="font-size: 18px"><Picture /></el-icon>
            <span>截图后直接 Ctrl+V 粘贴到这里（可贴多张）</span>
          </div>
        </div>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visibleModel = false">取消</el-button>
      <el-button type="primary" @click="onSubmit" :loading="submitting">创建</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture, Close } from '@element-plus/icons-vue'
import type { TemplateType, BoardCard, Priority } from '@/views/kanban/types/board'

const props = defineProps<{
  visible: boolean
  templateType: TemplateType
  targetColumnId: string
  targetColumnLabel: string
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  create: [card: Partial<BoardCard>, targetColumnId: string]
}>()

const submitting = ref(false)

const visibleModel = computed({
  get: () => props.visible,
  set: (val: boolean) => emit('update:visible', val),
})

const templateName = computed(() => {
  const map: Record<string, string> = { prod: '生产', biz: '业务', dev: '开发任务' }
  return map[props.templateType] ?? '卡片'
})

const form = reactive({
  title: '',
  assignee: '',
  deadline: '',
  priority: 'normal' as Priority,
  remark: '',
  screenshots: [] as { file: File; url: string }[],
})

function resetForm() {
  form.title = ''
  form.assignee = ''
  form.deadline = ''
  form.priority = 'normal'
  form.remark = ''
  form.screenshots = []
}

// 聚焦聊天区（点击时聚焦，便于直接粘贴）
const chatRef = ref<HTMLDivElement | null>(null)
function focusChat() {
  chatRef.value?.focus()
}

// 粘贴截图（Ctrl+V）
function handlePaste(e: ClipboardEvent) {
  const items = e.clipboardData?.items || []
  for (const item of items) {
    if (item.type.startsWith('image/')) {
      const file = item.getAsFile()
      if (file) {
        form.screenshots.push({ file, url: URL.createObjectURL(file) })
      }
    }
  }
  if (form.screenshots.length > 0) {
    ElMessage.success(`已添加 ${form.screenshots.length} 张截图`)
  }
}

function onClose() {
  visibleModel.value = false
}

async function onSubmit() {
  if (!form.title.trim()) {
    ElMessage.warning('请输入标题')
    return
  }

  submitting.value = true

  const today = new Date().toISOString().slice(0, 10)

  const newCard: Partial<BoardCard> = {
    id: `NEW-${Date.now()}`,
    title: form.title,
    templateType: props.templateType,
    priority: form.priority,
    status: 'pending',
    assignee: form.assignee || '未分配',
    deadline: form.deadline || today,
    remark: form.remark,
    createdAt: today,
    updatedAt: today,
    screenshots: form.screenshots.map((s) => ({ file: s.file })),
  }

  emit('create', newCard, props.targetColumnId)
  resetForm()

  // 延迟关闭，避免闪烁
  setTimeout(() => {
    submitting.value = false
    visibleModel.value = false
  }, 200)
}
</script>

<style scoped>
/* 对话式截图粘贴区 */
.screenshot-chat {
  width: 100%;
  min-height: 96px;
  border: 1px dashed #dcdfe6;
  border-radius: 8px;
  padding: 10px;
  background: #fafbfc;
  cursor: text;
  outline: none;
  transition: border-color 0.2s;
  box-sizing: border-box;
}

.screenshot-chat:focus {
  border-color: #409eff;
  background: #f5f9ff;
}

.screenshot-bubbles {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 8px;
}

.shot-bubble {
  position: relative;
}

.shot-thumb {
  width: 88px;
  height: 66px;
  border-radius: 6px;
  border: 1px solid #e4e7ed;
  cursor: zoom-in;
  display: block;
}

.shot-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  background: #f56c6c;
  color: #fff;
  border-radius: 50%;
  padding: 2px;
  cursor: pointer;
  font-size: 12px;
}

.chat-placeholder {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #a8abb2;
  font-size: 13px;
  min-height: 36px;
}
</style>
